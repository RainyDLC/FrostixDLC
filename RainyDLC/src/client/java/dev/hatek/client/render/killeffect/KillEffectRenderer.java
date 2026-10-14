package dev.hatek.client.render.killeffect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.module.impl.render.KillEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Owns snapshots, never retains dead entities. All geometry is world-depth tested. */
public final class KillEffectRenderer {
    private static final int MAX_EFFECTS = 8;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double RANGE_SQUARED = 48 * 48;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("hatek_client", "killeffect/atlas_v2");

    // 8-tile procedural HD atlas (4x2 grid on 512x256):
    private static final int TILE_ABYSS = 0;
    private static final int TILE_NEBULA = 1;
    private static final int TILE_STAR = 2;
    private static final int TILE_RING = 3;
    private static final int TILE_RUNE = 4;
    private static final int TILE_SWIRL = 5;
    private static final int TILE_CRYSTAL = 6;
    private static final int TILE_RIBBON = 7;

    private static final List<Effect> EFFECTS = new ArrayList<>();
    // A death latch survives animation expiry, but not respawn/removal/world changes.
    private static final KillEffectDeathLatch DEATHS = new KillEffectDeathLatch();
    private static ClientLevel lastLevel;
    private static boolean atlasReady;
    private static boolean atlasFailed;

    private record Effect(
            UUID uuid,
            Vec3 position,
            float yaw,
            float gripYaw,
            PlayerSkin skin,
            long born,
            double speed,
            double size,
            String mode,
            double initialHeight
    ) {
        double age(long now) {
            return Math.max(0.0, (now - born) / 1e9 * speed);
        }

        double duration() {
            return KillEffectTimeline.duration(mode);
        }

        boolean isDemon() {
            return KillEffect.MODE_DEMON.equals(mode);
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
        Vertex lerp(Vertex b, float t) {
            return new Vertex(
                    x + (b.x - x) * t,
                    y + (b.y - y) * t,
                    z + (b.z - z) * t,
                    u + (b.u - u) * t,
                    v + (b.v - v) * t
            );
        }
    }

    private KillEffectRenderer() { }

    public static void clear() {
        EFFECTS.clear();
        DEATHS.clear();
        lastLevel = null;
    }

    public static boolean hides(int id) {
        return !atlasFailed && DEATHS.contains(id);
    }

    // Legacy boolean overloads preserved for backward compatibility
    public static void tick(boolean vortex, double speed, double size) {
        tick(vortex ? KillEffect.MODE_VORTEX : KillEffect.MODE_DEMON, speed, size);
    }

    public static void onDeath(AbstractClientPlayer player, boolean vortex, double speed, double size) {
        onDeath(player, vortex ? KillEffect.MODE_VORTEX : KillEffect.MODE_DEMON, speed, size);
    }

    public static void preview(boolean vortex, double speed, double size) {
        preview(vortex ? KillEffect.MODE_VORTEX : KillEffect.MODE_DEMON, speed, size);
    }

    public static void tick(String mode, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (lastLevel != mc.level) {
            clear();
            lastLevel = mc.level;
        }
        long now = System.nanoTime();
        EFFECTS.removeIf(e -> e.age(now) >= e.duration());
        Set<Integer> loaded = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            loaded.add(player.getId());
            // Health polling is only a fallback. Status 3 is the authoritative remote death event.
            if (player != mc.player && (player.isDeadOrDying() || player.deathTime > 0)) {
                onDeath(player, mode, speed, size);
            }
        }
        DEATHS.retain(loaded);
    }

    public static void onDeath(AbstractClientPlayer player, String mode, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || player == mc.player || player.level() != mc.level
                || player.distanceToSqr(mc.player) > RANGE_SQUARED) return;
        if (lastLevel != mc.level) {
            clear();
            lastLevel = mc.level;
        }
        if (DEATHS.mark(player.getId(), player.getUUID())) {
            add(capture(player, mode, speed, size, System.nanoTime()));
        }
    }

    /** Removal/respawn clears suppression, never the independent cinematic snapshot. */
    public static void removed(int entityId) {
        DEATHS.remove(entityId);
    }

    private static Effect capture(AbstractClientPlayer player, String mode, double speed, double size, long now) {
        Vec3 feet = player.position();
        HitResult ground = player.level().clip(new ClipContext(
                feet.add(0, 0.25, 0),
                feet.add(0, -8, 0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        Vec3 origin = ground.getType() == HitResult.Type.MISS ? feet : ground.getLocation();
        return new Effect(
                player.getUUID(),
                origin,
                player.getYRot(),
                gripFacing(origin),
                player.getSkin(),
                now,
                speed,
                size,
                mode,
                Math.max(0.0, feet.y - origin.y)
        );
    }

    private static float gripFacing(Vec3 origin) {
        Vec3 observer = Minecraft.getInstance().gameRenderer.mainCamera().position();
        return (float) Math.atan2(observer.z - origin.z, -(observer.x - origin.x));
    }

    private static void add(Effect effect) {
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.removeFirst();
        EFFECTS.add(effect);
    }

    public static void preview(String mode, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (lastLevel != mc.level) {
            clear();
            lastLevel = mc.level;
        }
        Effect src = capture(mc.player, mode, speed, size, System.nanoTime());
        Vec3 offset = mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(3);
        Vec3 point = mc.player.position().add(offset);
        HitResult ground = mc.level.clip(new ClipContext(
                point.add(0, 2, 0),
                point.add(0, -8, 0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (ground.getType() != HitResult.Type.MISS) point = ground.getLocation();
        add(new Effect(src.uuid, point, src.yaw, gripFacing(point), src.skin, src.born, speed, size, mode, 0));
    }

    private static boolean ensureAtlas() {
        if (atlasReady) return true;
        if (atlasFailed) return false;
        try {
            byte[] png = buildProceduralAtlasPng();
            NativeImage image = NativeImage.read(png);
            DynamicTexture texture;
            try {
                texture = new DynamicTexture(() -> "RainyDLC KillEffect HD Atlas", image);
            } catch (Throwable error) {
                image.close();
                throw error;
            }
            try {
                Minecraft.getInstance().getTextureManager().register(ATLAS, texture);
            } catch (Throwable error) {
                texture.close();
                throw error;
            }
            atlasReady = true;
            return true;
        } catch (Exception error) {
            atlasFailed = true;
            LoggerFactory.getLogger("RainyDLC-KillEffect")
                    .error("Cannot initialize KillEffect HD texture atlas; leaving vanilla corpses visible", error);
            return false;
        }
    }

    public static void render(PoseStack stack, LevelRenderState state, SubmitNodeCollector collector) {
        if (!KillEffect.active() || EFFECTS.isEmpty() || !(collector instanceof OrderedSubmitNodeCollector ordered)) return;
        if (Minecraft.getInstance().level != lastLevel || !ensureAtlas()) return;
        long now = System.nanoTime();
        Vec3 camera = state.cameraRenderState.pos;
        for (Effect effect : List.copyOf(EFFECTS)) {
            double t = effect.age(now);
            if (t >= effect.duration() || effect.position.distanceToSqr(camera) > RANGE_SQUARED) continue;
            KillEffectTimeline.Pose frame = KillEffectTimeline.sample(effect.mode, t);
            stack.pushPose();
            stack.translate(effect.position.x - camera.x, effect.position.y - camera.y, effect.position.z - camera.z);
            stack.mulPose(new Quaternionf().rotationY(
                    effect.isDemon() ? effect.gripYaw : (float) Math.toRadians(180.0 - effect.yaw)
            ));
            ordered.submitCustomGeometry(
                    stack,
                    RenderTypes.entityTranslucentEmissive(effect.skin.body().texturePath()),
                    (pose, out) -> body(pose, out, effect, frame, t)
            );
            ordered.submitCustomGeometry(
                    stack,
                    RenderTypes.entityTranslucentEmissive(ATLAS),
                    (pose, out) -> renderEffectGeometry(pose, out, effect, frame, t)
            );
            stack.popPose();
        }
    }

    private static void renderEffectGeometry(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        switch (e.mode) {
            case KillEffect.MODE_VORTEX -> vortex(pose, out, e, f, t);
            case KillEffect.MODE_SOUL -> soul(pose, out, e, f, t);
            case KillEffect.MODE_BLACK_HOLE -> blackHole(pose, out, e, f, t);
            case KillEffect.MODE_LIGHTNING -> lightning(pose, out, e, f, t);
            case KillEffect.MODE_CRYSTAL -> crystal(pose, out, e, f, t);
            default -> demon(pose, out, e, f, t);
        }
    }

    private static void body(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        float alpha = (float) f.bodyAlpha();
        if (alpha <= 0.005f) return;

        boolean isDemon = e.isDemon();
        float fall = isDemon ? (float) KillEffectTimeline.smooth(0.84, 1.18, t) : 0.0f;
        float bodyY = (float) (f.bodyY() + e.initialHeight * (isDemon ? (1.0f - fall) : 1.0f));
        float relativeYaw = (float) Math.atan2(
                Math.sin(Math.toRadians(180.0 - e.yaw) - e.gripYaw),
                Math.cos(Math.toRadians(180.0 - e.yaw) - e.gripYaw)
        );

        float pivotY = 0.9f;
        Matrix4f root = new Matrix4f()
                .rotateY((float) f.bodySpin() + (isDemon ? relativeYaw * (1.0f - fall) : 0.0f))
                .translate(0.0f, bodyY + pivotY, 0.0f)
                .rotateX((float) f.bodyTilt())
                .scale((float) f.scaleXZ(), (float) f.scaleY(), (float) f.scaleXZ())
                .translate(0.0f, -pivotY, 0.0f);

        float minY = (!isDemon || t < 0.84) ? -100.0f : 0.035f;
        int color = argb(alpha, f.tintR(), f.tintG(), f.tintB());
        boolean slim = e.skin.model() == PlayerModelType.SLIM;
        float aw = slim ? 3.0f / 16.0f : 4.0f / 16.0f;
        float sh = (float) f.shatter();

        // Torso
        Matrix4f torso = limbBurst(root, sh, 0.0f, 0.25f, -0.18f, sh * 1.4f, sh * 0.8f);
        part(pose, out, torso, -0.25f, 0.75f, -0.125f, 0.5f, 0.75f, 0.25f, 16, 16, 8, 12, 4, 0.0f, minY, color);
        part(pose, out, torso, -0.25f, 0.75f, -0.125f, 0.5f, 0.75f, 0.25f, 16, 32, 8, 12, 4, 0.008f, minY, color);

        // Head
        Matrix4f head = new Matrix4f(limbBurst(root, sh, 0.0f, 1.15f, 0.22f, -sh * 2.4f, sh * 1.9f))
                .translate(0.0f, 1.5f, 0.0f)
                .rotateX((float) (-0.32 * f.armSpread()));
        part(pose, out, head, -0.25f, 0.0f, -0.25f, 0.5f, 0.5f, 0.5f, 0, 0, 8, 8, 8, 0.0f, minY, color);
        part(pose, out, head, -0.25f, 0.0f, -0.25f, 0.5f, 0.5f, 0.5f, 32, 0, 8, 8, 8, 0.012f, minY, color);

        for (int side : new int[]{-1, 1}) {
            float spread = (float) f.armSpread();
            float grip = (float) f.grip();
            if (isDemon && side == -1 && grip > 0.0f) {
                grippingArm(pose, out, e, f, root, aw, slim, minY, color);
            } else {
                Matrix4f armBase = limbBurst(root, sh, side * 1.15f, 0.65f, side * 0.35f, side * sh * 3.2f, sh * 2.6f);
                Matrix4f arm = new Matrix4f(armBase)
                        .translate(side * (0.25f + aw / 2.0f), 1.45f, 0.0f)
                        .rotateZ(side * spread * 2.12f)
                        .rotateX(isDemon ? -0.18f * spread : 0.14f * spread);
                int u = side == -1 ? 40 : 32;
                int v = side == -1 ? 16 : 48;
                part(pose, out, arm, -aw / 2.0f, -0.75f, -0.125f, aw, 0.75f, 0.25f, u, v, slim ? 3 : 4, 12, 4, 0.0f, minY, color);
                part(pose, out, arm, -aw / 2.0f, -0.75f, -0.125f, aw, 0.75f, 0.25f, side == -1 ? 40 : 48, side == -1 ? 32 : 48, slim ? 3 : 4, 12, 4, 0.006f, minY, color);
            }
            Matrix4f legBase = limbBurst(root, sh, side * 0.75f, -0.35f, -side * 0.4f, -side * sh * 2.5f, sh * 2.1f);
            Matrix4f leg = new Matrix4f(legBase)
                    .translate(side * 0.125f, 0.75f, 0.0f)
                    .rotateZ(side * spread * 0.16f);
            part(pose, out, leg, -0.125f, -0.75f, -0.125f, 0.25f, 0.75f, 0.25f, side == -1 ? 0 : 16, side == -1 ? 16 : 48, 4, 12, 4, 0.0f, minY, color);
            part(pose, out, leg, -0.125f, -0.75f, -0.125f, 0.25f, 0.75f, 0.25f, 0, side == -1 ? 32 : 48, 4, 12, 4, 0.006f, minY, color);
        }
    }

    private static Matrix4f limbBurst(Matrix4f root, float shatter, float vx, float vy, float vz, float rx, float rz) {
        if (shatter <= 0.001f) return root;
        float gravity = -1.35f * shatter * shatter;
        return new Matrix4f(root)
                .translate(vx * shatter, vy * shatter + gravity, vz * shatter)
                .rotateX(rx)
                .rotateZ(rz);
    }

    private static void grippingArm(PoseStack.Pose pose, VertexConsumer out, Effect e,
                                    KillEffectTimeline.Pose f, Matrix4f root, float aw,
                                    boolean slim, float minY, int color) {
        float release = (float) f.slip();
        float reach = (float) f.grip();
        float x = (float) (-e.size * 0.82 + release * e.size * 0.42);
        Matrix4f raised = new Matrix4f(root)
                .translate(-0.25f - aw / 2.0f, 1.45f, 0.0f)
                .rotateZ((float) (-f.armSpread() * 2.12))
                .rotateX((float) (-0.18 * f.armSpread()));
        Vector3f shoulder = raised.transformPosition(new Vector3f());
        Vector3f elbow = raised.transformPosition(new Vector3f(0.0f, -0.375f, 0.0f))
                .lerp(new Vector3f(x + 0.28f, 0.22f - release * 0.85f, 0.035f), reach);
        Vector3f wrist = raised.transformPosition(new Vector3f(0.0f, -0.75f, 0.0f))
                .lerp(new Vector3f(x, 0.105f - release * 1.15f, 0.0f), reach);
        limb(pose, out, shoulder, elbow, aw, 40, 16, slim, minY, color);
        limb(pose, out, elbow, wrist, aw, 40, 22, slim, minY, color);
    }

    private static void limb(PoseStack.Pose pose, VertexConsumer out, Vector3f start, Vector3f end,
                             float width, int u, int v, boolean slim, float minY, int color) {
        Vector3f delta = new Vector3f(end).sub(start);
        float length = delta.length();
        if (length < 0.001f) return;
        Matrix4f matrix = new Matrix4f()
                .translate(start)
                .rotate(new Quaternionf().rotationTo(new Vector3f(0.0f, -1.0f, 0.0f), delta.div(length)))
                .scale(1.0f, length / 0.375f, 1.0f);
        part(pose, out, matrix, -width / 2.0f, -0.375f, -0.125f, width, 0.375f, 0.25f, u, v, slim ? 3 : 4, 6, 4, 0.0f, minY, color);
        part(pose, out, matrix, -width / 2.0f, -0.375f, -0.125f, width, 0.375f, 0.25f, u, v + 16, slim ? 3 : 4, 6, 4, 0.006f, minY, color);
    }

    private static void part(PoseStack.Pose pose, VertexConsumer out, Matrix4f m,
                             float x, float y, float z, float w, float h, float d,
                             int u, int v, int pw, int ph, int pd, float inflate, float minY, int color) {
        float a = x - inflate, b = y - inflate, c = z - inflate;
        float X = x + w + inflate, Y = y + h + inflate, Z = z + d + inflate;
        skinFace(pose, out, m, new float[]{a, b, c, X, b, c, X, Y, c, a, Y, c}, u + pd, v + pd, pw, ph, minY, color);
        skinFace(pose, out, m, new float[]{X, b, Z, a, b, Z, a, Y, Z, X, Y, Z}, u + pd + pw + pd, v + pd, pw, ph, minY, color);
        skinFace(pose, out, m, new float[]{a, b, Z, a, b, c, a, Y, c, a, Y, Z}, u, v + pd, pd, ph, minY, color);
        skinFace(pose, out, m, new float[]{X, b, c, X, b, Z, X, Y, Z, X, Y, c}, u + pd + pw, v + pd, pd, ph, minY, color);
        skinFace(pose, out, m, new float[]{a, Y, c, X, Y, c, X, Y, Z, a, Y, Z}, u + pd, v, pw, pd, minY, color);
        skinFace(pose, out, m, new float[]{a, b, Z, X, b, Z, X, b, c, a, b, c}, u + pd + pw, v, pw, pd, minY, color);
    }

    private static void skinFace(PoseStack.Pose pose, VertexConsumer out, Matrix4f m,
                                 float[] p, int u, int v, int w, int h, float minY, int color) {
        List<Vertex> poly = new ArrayList<>(4);
        float[] uv = {
                u / 64.0f, (v + h) / 64.0f,
                (u + w) / 64.0f, (v + h) / 64.0f,
                (u + w) / 64.0f, v / 64.0f,
                u / 64.0f, v / 64.0f
        };
        for (int i = 0; i < 4; i++) {
            Vector3f pt = m.transformPosition(new Vector3f(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]));
            poly.add(new Vertex(pt.x, pt.y, pt.z, uv[i * 2], uv[i * 2 + 1]));
        }
        List<Vertex> clipped = new ArrayList<>(6);
        for (int i = 0; i < poly.size(); i++) {
            Vertex a = poly.get(i), b = poly.get((i + 1) % poly.size());
            if (a.y >= minY) clipped.add(a);
            if ((a.y >= minY) != (b.y >= minY)) clipped.add(a.lerp(b, (minY - a.y) / (b.y - a.y)));
        }
        if (clipped.size() == 4) {
            quad(pose, out, clipped.get(0), clipped.get(1), clipped.get(2), clipped.get(3), color);
        } else {
            for (int i = 1; i + 1 < clipped.size(); i++) {
                quad(pose, out, clipped.get(0), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1), color);
            }
        }
    }

    // =========================================================================
    // 1. UPGRADED "Демоническая сила" (Abyssal Runic Portal & Shadow Tendrils)
    // =========================================================================
    private static void demon(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double radius = 1.38 * e.size * f.pitScale();
        if (radius < 0.005) return;
        double energy = f.energy();
        int seed = e.uuid.hashCode();

        // Layer 1: Deep abyssal ground crack & void stain
        ground(pose, out, 0.0f, 0.0f, 0.022f, (float) (radius * 1.08), TILE_ABYSS, argb(0.95 * energy, 1.0, 1.0, 1.0), 0.0f);

        // Layer 2: Swirling magma vortex inside the abyss
        ground(pose, out, 0.0f, 0.0f, 0.026f, (float) (radius * 0.92), TILE_SWIRL,
                argb(0.72 * energy, 0.95, 0.18, 0.08), (float) (-t * 2.2));

        // Layer 3: Counter-rotating outer & inner Demonic Runic Sigils
        double pulse = 0.78 + 0.22 * Math.sin(t * 6.5);
        ground(pose, out, 0.0f, 0.0f, 0.031f, (float) (radius * 1.18), TILE_RUNE,
                argb(0.88 * energy * pulse, 1.0, 0.22, 0.12), (float) (t * 0.85));
        ground(pose, out, 0.0f, 0.0f, 0.034f, (float) (radius * 0.76), TILE_RUNE,
                argb(0.75 * energy, 1.0, 0.55, 0.14), (float) (-t * 1.45));

        // Layer 4: 5 Writhing 3D Demonic Shadow-Magma Tendrils erupting from the pit to grab the victim
        double grabEnvelope = KillEffectTimeline.smooth(0.15, 0.72, t) * (1.0 - KillEffectTimeline.smooth(2.05, 2.55, t));
        if (grabEnvelope > 0.01) {
            int tendrils = 5;
            int segs = 9;
            for (int k = 0; k < tendrils; k++) {
                float[] pts = new float[segs * 3];
                double baseAngle = k * (Math.PI * 2.0 / tendrils) + 0.35 * Math.sin(t * 2.0 + k);
                double maxH = (1.65 + 0.35 * Math.sin(k * 1.7 + t * 3.0)) * grabEnvelope * e.size;
                for (int s = 0; s < segs; s++) {
                    double frac = s / (double) (segs - 1);
                    // Arch inward from the portal rim toward the victim's torso
                    double rCurve = radius * (0.68 * (1.0 - 0.65 * frac) + 0.14 * Math.sin(frac * Math.PI));
                    double twist = baseAngle + frac * 1.35 + 0.22 * Math.sin(t * 5.0 + frac * 4.0 + k);
                    pts[s * 3] = (float) (Math.cos(twist) * rCurve);
                    pts[s * 3 + 1] = (float) (0.03 + frac * maxH);
                    pts[s * 3 + 2] = (float) (Math.sin(twist) * rCurve);
                }
                int cBase = argb(0.92 * grabEnvelope, 0.95, 0.12, 0.06);
                int cTip = argb(0.85 * grabEnvelope, 1.0, 0.65, 0.15);
                ribbon(pose, out, pts, (float) (0.16 * e.size), (float) (0.03 * e.size), cBase, cTip);
            }
        }

        // Layer 5: 2 Spiraling Hellfire Energy Streams around the abyss
        for (int stream = 0; stream < 2; stream++) {
            int segs = 12;
            float[] pts = new float[segs * 3];
            double offset = stream * Math.PI;
            for (int s = 0; s < segs; s++) {
                double frac = s / (double) (segs - 1);
                double ang = offset - t * 4.2 + frac * 5.2;
                double rad = radius * (0.85 - 0.35 * frac);
                pts[s * 3] = (float) (Math.cos(ang) * rad);
                pts[s * 3 + 1] = (float) (0.04 + frac * 2.1 * energy);
                pts[s * 3 + 2] = (float) (Math.sin(ang) * rad);
            }
            ribbon(pose, out, pts, (float) (0.12 * e.size), 0.01f,
                    argb(0.65 * energy, 1.0, 0.25, 0.08),
                    argb(0.0, 1.0, 0.75, 0.2));
        }

        // Layer 6: Rising glowing 4-point star embers & crimson nebula wisps
        for (int i = 0; i < 44; i++) {
            double phase = fract(t * 0.72 + i * 0.618 + seed * 0.000001);
            double a = i * 2.399 + t * 2.1;
            double r = radius * (0.20 + 0.82 * noise(i, seed)) * (1.0 - 0.35 * phase);
            float x = (float) (Math.cos(a) * r);
            float z = (float) (Math.sin(a) * r);
            float y = (float) (0.06 + phase * 2.25 * e.size);
            float sz = (float) ((0.055 + 0.055 * noise(i + 87, seed)) * e.size);
            double alpha = Math.sin(phase * Math.PI) * energy;
            crossRot(pose, out, x, y, z, sz, TILE_STAR, (float) (t * 2.5 + i),
                    argb(alpha, 1.0, 0.35 + 0.45 * (1.0 - phase), 0.10));
        }

        // Layer 7: Final Plunge Crimson Shockwave Ring when victim slips into the pit
        if (f.burst() > 0.005) {
            double b = f.burst();
            float waveR = (float) (radius * (0.45 + 1.65 * b));
            ground(pose, out, 0.0f, 0.0f, 0.042f, waveR, TILE_RING,
                    argb((1.0 - b) * 0.95, 1.0, 0.32, 0.12), (float) (-t * 3.0));
        }
    }

    // =========================================================================
    // 2. UPGRADED "Вихрь" (Arcane Storm Cyclone with 3D Ribbons & Wind Blades)
    // =========================================================================
    private static void vortex(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        if (f.dustAlpha() < 0.005) return;
        int seed = e.uuid.hashCode();
        double fade = f.dustAlpha();
        double dissolve = KillEffectTimeline.smooth(1.85, KillEffectTimeline.VORTEX_SECONDS, t);
        float base = (float) (0.04 + 0.35 * f.dustY());

        // Layer 1: High-speed rotating storm swirl & runic ring on the ground
        ground(pose, out, 0.0f, 0.0f, 0.032f, (float) (1.35 * e.size * (1.0 + 0.4 * dissolve)), TILE_SWIRL,
                argb(fade * 0.82, 0.22, 0.85, 1.0), (float) (-t * 6.5));
        ground(pose, out, 0.0f, 0.0f, 0.036f, (float) (0.95 * e.size), TILE_RING,
                argb(fade * 0.68, 0.55, 0.42, 1.0), (float) (t * 4.0));

        // Layer 2: 4 Continuous 3D Helical Wind Ribbons forming a sleek funnel
        int helices = 4;
        int segs = 18;
        for (int hIdx = 0; hIdx < helices; hIdx++) {
            float[] pts = new float[segs * 3];
            double baseOffset = hIdx * (Math.PI * 2.0 / helices);
            for (int s = 0; s < segs; s++) {
                double h = s / (double) (segs - 1);
                double height = h * 3.2 * e.size;
                double rad = (0.28 + 0.88 * Math.pow(h, 0.85)) * e.size * (1.0 + 0.95 * dissolve);
                double ang = baseOffset + t * 8.5 - h * 7.2;
                pts[s * 3] = (float) (Math.cos(ang) * rad);
                pts[s * 3 + 1] = base + (float) height;
                pts[s * 3 + 2] = (float) (Math.sin(ang) * rad);
            }
            boolean cyan = (hIdx % 2 == 0);
            int cStart = argb(fade * 0.88, cyan ? 0.20 : 0.58, cyan ? 0.92 : 0.38, 1.0);
            int cEnd = argb(fade * 0.15, 0.85, 0.98, 1.0);
            ribbon(pose, out, pts, (float) (0.18 * e.size), (float) (0.06 * e.size), cStart, cEnd);
        }

        // Layer 3: Tilted Orbiting Wind-Slash Rings along the cyclone column
        for (int ring = 0; ring < 5; ring++) {
            double h = (ring + 0.5) / 5.0;
            float ry = base + (float) (h * 2.75 * e.size);
            float rRad = (float) ((0.42 + h * 0.72) * e.size * (1.0 + 0.75 * dissolve));
            float tiltX = (float) (0.22 * Math.sin(t * 5.0 + ring * 1.4));
            float tiltZ = (float) (0.22 * Math.cos(t * 5.0 + ring * 1.4));
            tiltedDisc(pose, out, 0.0f, ry, 0.0f, rRad, TILE_SWIRL,
                    argb(fade * 0.46 * (1.0 - 0.3 * h), 0.35, 0.82, 1.0),
                    (float) (-t * (7.5 - ring * 0.6)), tiltX, tiltZ);
        }

        // Layer 4: Volumetric Nebula Clouds & Sparkling Storm Motes
        for (int i = 0; i < 64; i++) {
            double phase = fract(i * 0.618 + t * 0.42);
            double angle = i * 2.399 + t * (6.2 + noise(i, seed) * 2.5);
            double radius = e.size * (0.30 + phase * 1.05) * (1.0 + dissolve * 1.45);
            float x = (float) (Math.cos(angle) * radius);
            float z = (float) (Math.sin(angle) * radius);
            float y = base + (float) (phase * 3.25 * e.size);
            boolean isStar = (i % 3 == 0);
            float sz = (float) ((isStar ? 0.075 : 0.24) * (0.7 + 0.6 * noise(i + 71, seed)) * e.size);
            double a = fade * Math.sin(phase * Math.PI) * (isStar ? 0.92 : 0.42);
            crossRot(pose, out, x, y, z, sz, isStar ? TILE_STAR : TILE_NEBULA, (float) (angle * 0.5),
                    argb(a, isStar ? 0.75 : 0.28, isStar ? 0.96 : 0.75, 1.0));
        }

        // Layer 5: Collapse Shockwave Burst
        if (f.burst() > 0.005) {
            double b = f.burst();
            tiltedDisc(pose, out, 0.0f, (float) (1.25 * e.size), 0.0f,
                    (float) (e.size * (0.35 + 1.85 * b)), TILE_RING,
                    argb((1.0 - b) * 0.90, 0.55, 0.95, 1.0), (float) (t * 5.0), 0.12f, -0.10f);
        }
    }

    // =========================================================================
    // 3. NEW MODE "Вознесение души" (Celestial Soul Ascension & Double Helix)
    // =========================================================================
    private static void soul(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double energy = f.energy();
        if (energy < 0.005) return;
        int seed = e.uuid.hashCode();
        double s = e.size;

        // Layer 1: Sacred Golden-Aqua Ground Halo & Counter-Rotating Rune Circle
        ground(pose, out, 0.0f, 0.0f, 0.028f, (float) (1.35 * s * energy), TILE_RUNE,
                argb(0.88 * energy, 1.0, 0.86, 0.38), (float) (t * 0.95));
        ground(pose, out, 0.0f, 0.0f, 0.032f, (float) (0.95 * s * energy), TILE_RING,
                argb(0.75 * energy, 0.38, 0.98, 0.92), (float) (-t * 1.4));

        // Layer 2: Ascending Crown Halo above the rising spirit
        float crownY = (float) (f.bodyY() + 2.15 * s);
        tiltedDisc(pose, out, 0.0f, crownY, 0.0f, (float) (0.62 * s * energy), TILE_RUNE,
                argb(0.82 * energy, 1.0, 0.92, 0.52), (float) (-t * 2.2), 0.0f, 0.0f);

        // Layer 3: 2 Intertwined Celestial DNA Double-Helix Ribbons spiraling up to the heavens
        int segs = 20;
        for (int strand = 0; strand < 2; strand++) {
            float[] pts = new float[segs * 3];
            double phaseOffset = strand * Math.PI;
            for (int i = 0; i < segs; i++) {
                double frac = i / (double) (segs - 1);
                double y = 0.05 + frac * (3.8 * s);
                double env = Math.sin(frac * Math.PI);
                double rad = (0.58 + 0.18 * env) * s * (1.0 - 0.35 * frac);
                double ang = phaseOffset + frac * 8.5 - t * 4.5;
                pts[i * 3] = (float) (Math.cos(ang) * rad);
                pts[i * 3 + 1] = (float) y;
                pts[i * 3 + 2] = (float) (Math.sin(ang) * rad);
            }
            int cBottom = strand == 0
                    ? argb(0.85 * energy, 0.35, 0.98, 0.90)
                    : argb(0.85 * energy, 1.0, 0.85, 0.35);
            int cTop = argb(0.15 * energy, 1.0, 1.0, 0.95);
            ribbon(pose, out, pts, (float) (0.14 * s), (float) (0.04 * s), cBottom, cTop);
        }

        // Layer 4: 4 Vertical Heavenly Light Pillars surrounding the spirit
        for (int p = 0; p < 4; p++) {
            double ang = p * (Math.PI * 0.5) + t * 0.45;
            float px = (float) (Math.cos(ang) * 0.78 * s);
            float pz = (float) (Math.sin(ang) * 0.78 * s);
            float[] beam = {
                    px, 0.03f, pz,
                    px, (float) (1.8 * s), pz,
                    px, (float) (3.9 * s), pz
            };
            ribbon(pose, out, beam, (float) (0.16 * s * energy), (float) (0.02 * s),
                    argb(0.48 * energy, 1.0, 0.92, 0.62),
                    argb(0.0, 0.45, 1.0, 0.95));
        }

        // Layer 5: Ascending Starlight Flares & Soul Wisps
        for (int i = 0; i < 52; i++) {
            double phase = fract(t * 0.48 + i * 0.618 + seed * 0.00001);
            double ang = i * 2.399 + t * 1.8;
            double rad = (0.18 + 0.85 * noise(i, seed)) * s * (1.0 + 0.5 * f.burst());
            float x = (float) (Math.cos(ang) * rad);
            float z = (float) (Math.sin(ang) * rad);
            float y = (float) (0.12 + phase * 3.95 * s);
            float sz = (float) ((0.065 + 0.075 * noise(i + 41, seed)) * s);
            double a = Math.sin(phase * Math.PI) * energy;
            boolean gold = (i % 2 == 0);
            crossRot(pose, out, x, y, z, sz, TILE_STAR, (float) (t * 3.0 + i),
                    argb(a, gold ? 1.0 : 0.45, gold ? 0.90 : 1.0, gold ? 0.45 : 0.95));
        }

        // Layer 6: Final Ascension Starburst Halo at the apex
        if (f.burst() > 0.005) {
            double b = f.burst();
            float burstY = (float) (2.35 * s);
            tiltedDisc(pose, out, 0.0f, burstY, 0.0f, (float) (s * (0.35 + 1.95 * b)), TILE_RING,
                    argb((1.0 - b) * 0.92, 1.0, 0.95, 0.65), (float) (t * 3.0), 0.0f, 0.0f);
        }
    }

    // =========================================================================
    // 4. NEW MODE "Черная дыра" (Singularity, Accretion Disk & Supernova Jets)
    // =========================================================================
    private static void blackHole(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double energy = f.energy();
        if (energy < 0.005) return;
        int seed = e.uuid.hashCode();
        double s = e.size;
        double implode = f.slip();
        float centerY = (float) (1.22 * s);
        float coreScale = (float) (energy * (1.0 - 0.65 * implode));

        // Layer 1: Gravitational distortion shadow on the ground
        ground(pose, out, 0.0f, 0.0f, 0.025f, (float) (1.25 * s * energy), TILE_ABYSS,
                argb(0.80 * energy, 0.35, 0.12, 0.55), (float) (-t * 2.0));
        ground(pose, out, 0.0f, 0.0f, 0.030f, (float) (1.05 * s * energy), TILE_RING,
                argb(0.65 * energy, 0.68, 0.25, 1.0), (float) (t * 3.5));

        // Layer 2: Tilted Relativistic Accretion Disk (outer violet-magenta + inner blazing gold-orange)
        float diskTiltX = 0.42f;
        float diskTiltZ = -0.24f;
        if (coreScale > 0.02f) {
            tiltedDisc(pose, out, 0.0f, centerY, 0.0f, (float) (1.58 * s * coreScale), TILE_SWIRL,
                    argb(0.88 * energy, 0.62, 0.18, 1.0), (float) (-t * 8.5), diskTiltX, diskTiltZ);
            tiltedDisc(pose, out, 0.0f, centerY, 0.0f, (float) (1.08 * s * coreScale), TILE_SWIRL,
                    argb(0.95 * energy, 1.0, 0.58, 0.18), (float) (-t * 12.0), diskTiltX, diskTiltZ);
            tiltedDisc(pose, out, 0.0f, centerY, 0.0f, (float) (0.72 * s * coreScale), TILE_RING,
                    argb(0.98 * energy, 1.0, 0.92, 0.72), (float) (t * 6.0), diskTiltX, diskTiltZ);

            // Gravitational Lensing Halo (orthogonal ring wrapping over the event horizon)
            tiltedDisc(pose, out, 0.0f, centerY, 0.0f, (float) (0.76 * s * coreScale), TILE_RING,
                    argb(0.85 * energy, 0.82, 0.48, 1.0), (float) (-t * 5.0), diskTiltX + 1.57f, diskTiltZ);

            // Dark Event Horizon Core
            crossRot(pose, out, 0.0f, centerY, 0.0f, (float) (0.46 * s * coreScale), TILE_ABYSS,
                    (float) (t * 2.0), argb(0.98 * energy, 0.08, 0.02, 0.14));
        }

        // Layer 3: 3 Spiraling Matter Streams being pulled into the Singularity
        int streams = 3;
        int segs = 14;
        for (int st = 0; st < streams; st++) {
            float[] pts = new float[segs * 3];
            double offset = st * (Math.PI * 2.0 / streams);
            for (int i = 0; i < segs; i++) {
                double frac = i / (double) (segs - 1); // 0 = outer, 1 = event horizon
                double rad = (1.65 * (1.0 - frac) + 0.08) * s * coreScale;
                double ang = offset - t * 9.0 - frac * 6.5;
                double yOff = Math.sin(ang) * 0.38 * rad;
                pts[i * 3] = (float) (Math.cos(ang) * rad);
                pts[i * 3 + 1] = centerY + (float) yOff;
                pts[i * 3 + 2] = (float) (Math.sin(ang) * rad);
            }
            ribbon(pose, out, pts, (float) (0.04 * s), (float) (0.14 * s),
                    argb(0.15 * energy, 0.55, 0.20, 1.0),
                    argb(0.92 * energy, 1.0, 0.78, 0.32));
        }

        // Layer 4: Infalling Star Particles (before implosion) & Supernova Shockwave + Polar Jets (after implosion)
        for (int i = 0; i < 48; i++) {
            double phase = 1.0 - fract(t * 0.85 + i * 0.618 + seed * 0.00001);
            double ang = i * 2.399 - t * (5.0 + 4.0 * (1.0 - phase));
            double rad = (0.12 + 1.55 * phase) * s * coreScale;
            float x = (float) (Math.cos(ang) * rad);
            float z = (float) (Math.sin(ang) * rad);
            float y = centerY + (float) ((noise(i + 19, seed) - 0.5) * 1.4 * phase * s);
            float sz = (float) ((0.05 + 0.06 * phase) * s);
            crossRot(pose, out, x, y, z, sz, TILE_STAR, (float) ang,
                    argb(Math.sin(phase * Math.PI) * energy, 0.85, 0.52, 1.0));
        }

        if (f.burst() > 0.005) {
            double b = f.burst();
            // Expanding Supernova Shockwave Ring
            tiltedDisc(pose, out, 0.0f, centerY, 0.0f, (float) (s * (0.25 + 2.45 * b)), TILE_RING,
                    argb((1.0 - b) * 0.96, 0.88, 0.55, 1.0), (float) (t * 8.0), diskTiltX, diskTiltZ);
            // Relativistic Polar Plasma Jets shooting up and down
            float jetLen = (float) (2.6 * s * Math.sin(b * Math.PI));
            float[] jetPts = {
                    0.0f, Math.max(0.03f, centerY - jetLen * 0.45f), 0.0f,
                    0.0f, centerY, 0.0f,
                    0.0f, centerY + jetLen, 0.0f
            };
            ribbon(pose, out, jetPts, (float) (0.22 * s * (1.0 - b)), (float) (0.04 * s),
                    argb((1.0 - b) * 0.95, 1.0, 0.85, 1.0),
                    argb((1.0 - b) * 0.85, 0.65, 0.25, 1.0));
        }
    }

    // =========================================================================
    // 5. NEW MODE "Грозовой разряд" (3D Sky Lightning Bolt & Plasma Shockwave)
    // =========================================================================
    private static void lightning(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double energy = f.energy();
        if (energy < 0.005) return;
        int seed = e.uuid.hashCode();
        double s = e.size;

        // Layer 1: Scorched electric crater & expanding ground shockwave rings
        ground(pose, out, 0.0f, 0.0f, 0.025f, (float) (1.15 * s * energy), TILE_ABYSS,
                argb(0.75 * energy, 0.25, 0.65, 1.0), 0.0f);
        for (int w = 0; w < 3; w++) {
            double wavePhase = fract(t * 1.35 + w * 0.333);
            float waveR = (float) ((0.25 + 1.95 * wavePhase) * s);
            ground(pose, out, 0.0f, 0.0f, 0.032f + w * 0.003f, waveR, TILE_RING,
                    argb((1.0 - wavePhase) * energy * 0.85, 0.42, 0.88, 1.0), (float) (t * 4.0 + w));
        }

        // Layer 2: Main Jagged 3D Sky Lightning Bolt + 3 Forked Branches (active during strike & re-strikes)
        double boltFade = (1.0 - KillEffectTimeline.smooth(0.95, 1.55, t));
        if (boltFade > 0.01) {
            int boltSeed = seed ^ ((int) (t * 18.0) * 7919);
            int segs = 13;
            float[] mainBolt = new float[segs * 3];
            for (int i = 0; i < segs; i++) {
                double frac = i / (double) (segs - 1); // 0 = ground, 1 = sky
                double jitterAmp = (frac > 0.05 && frac < 0.95) ? 0.38 * s : 0.0;
                mainBolt[i * 3] = (float) ((noise(i * 13, boltSeed) - 0.5) * jitterAmp);
                mainBolt[i * 3 + 1] = (float) (0.05 + frac * 6.5 * s);
                mainBolt[i * 3 + 2] = (float) ((noise(i * 29 + 7, boltSeed) - 0.5) * jitterAmp);

                // Secondary forked branch from mid-bolt nodes
                if (i == 4 || i == 7 || i == 9) {
                    float bx = mainBolt[i * 3], by = mainBolt[i * 3 + 1], bz = mainBolt[i * 3 + 2];
                    float dirX = (float) ((noise(i + 101, boltSeed) - 0.5) * 1.6 * s);
                    float dirZ = (float) ((noise(i + 211, boltSeed) - 0.5) * 1.6 * s);
                    float[] branch = {
                            bx, by, bz,
                            bx + dirX * 0.55f, by - (float) (0.65 * s), bz + dirZ * 0.55f,
                            bx + dirX, by - (float) (1.35 * s), bz + dirZ
                    };
                    ribbon(pose, out, branch, (float) (0.11 * s), 0.015f,
                            argb(0.90 * boltFade, 0.55, 0.92, 1.0),
                            argb(0.0, 0.25, 0.65, 1.0));
                }
            }
            // Bright white-cyan core + wider electric-blue plasma aura
            ribbon(pose, out, mainBolt, (float) (0.28 * s), (float) (0.34 * s),
                    argb(0.65 * boltFade, 0.22, 0.68, 1.0),
                    argb(0.65 * boltFade, 0.35, 0.78, 1.0));
            ribbon(pose, out, mainBolt, (float) (0.12 * s), (float) (0.15 * s),
                    argb(0.98 * boltFade, 0.92, 0.99, 1.0),
                    argb(0.98 * boltFade, 1.0, 1.0, 1.0));
        }

        // Layer 3: Crackling Plasma Arcs & Flying Electric Sparks
        for (int i = 0; i < 48; i++) {
            double phase = fract(t * 1.15 + i * 0.618 + seed * 0.00001);
            double ang = i * 2.399 + t * 5.5;
            double rad = (0.22 + 1.35 * phase) * s;
            float x = (float) (Math.cos(ang) * rad);
            float z = (float) (Math.sin(ang) * rad);
            float y = (float) (0.12 + (noise(i + 33, seed) * 2.2 + phase * 1.2 * (1.0 - phase)) * s);
            float sz = (float) ((0.055 + 0.075 * noise(i + 61, seed)) * s);
            double a = Math.sin(phase * Math.PI) * energy;
            crossRot(pose, out, x, y, z, sz, TILE_STAR, (float) (t * 8.0 + i),
                    argb(a, 0.65, 0.95, 1.0));
        }
    }

    // =========================================================================
    // 6. NEW MODE "Кристальный распад" (Frostix Crystal Prison & Shatter Nova)
    // =========================================================================
    private static void crystal(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double energy = f.energy();
        if (energy < 0.005) return;
        int seed = e.uuid.hashCode();
        double s = e.size;
        double shatter = f.shatter();

        // Layer 1: Frost Rune Sigil & Crystalline Ground Ring
        ground(pose, out, 0.0f, 0.0f, 0.028f, (float) (1.28 * s * energy), TILE_RUNE,
                argb(0.85 * energy * (1.0 - 0.5 * shatter), 0.38, 0.88, 1.0), (float) (t * 0.45));

        // Layer 2: 8 Erupting 3D Crystal Spikes encasing the target (before shatter)
        double spikeEnvelope = KillEffectTimeline.easeOutBack(0.0, 0.48, t) * (1.0 - KillEffectTimeline.smooth(0.80, 1.05, t));
        if (spikeEnvelope > 0.01) {
            for (int k = 0; k < 8; k++) {
                double ang = k * (Math.PI * 0.25) + 0.15 * (k % 2);
                double baseR = (k % 2 == 0 ? 0.45 : 0.72) * s;
                double tipR = (k % 2 == 0 ? 0.18 : 0.95) * s;
                double spikeH = (k % 2 == 0 ? 1.95 : 1.35) * s * spikeEnvelope;
                float[] spikePts = {
                        (float) (Math.cos(ang) * baseR), 0.02f, (float) (Math.sin(ang) * baseR),
                        (float) (Math.cos(ang) * ((baseR + tipR) * 0.5)), (float) (spikeH * 0.55), (float) (Math.sin(ang) * ((baseR + tipR) * 0.5)),
                        (float) (Math.cos(ang) * tipR), (float) spikeH, (float) (Math.sin(ang) * tipR)
                };
                ribbon(pose, out, spikePts, (float) (0.24 * s), 0.01f,
                        argb(0.88 * energy, 0.28, 0.82, 1.0),
                        argb(0.95 * energy, 0.88, 0.98, 1.0));
            }
        }

        // Layer 3: Explosive 3D Faceted Crystal Shards & Frost Nova Shockwave (during shatter)
        if (shatter > 0.001) {
            float novaR = (float) ((0.35 + 2.25 * shatter) * s);
            ground(pose, out, 0.0f, 0.0f, 0.036f, novaR, TILE_RING,
                    argb((1.0 - shatter) * 0.92, 0.55, 0.95, 1.0), (float) (-t * 2.5));

            for (int i = 0; i < 46; i++) {
                double ang = i * 2.399 + (noise(i, seed) - 0.5) * 0.4;
                double speed = (0.75 + 1.65 * noise(i + 17, seed)) * s;
                double dist = speed * shatter;
                double vy = (0.85 + 1.55 * noise(i + 53, seed)) * s;
                float x = (float) (Math.cos(ang) * dist);
                float z = (float) (Math.sin(ang) * dist);
                float y = (float) Math.max(0.06, 0.95 * s + vy * shatter - 1.85 * s * shatter * shatter);
                float sz = (float) ((0.09 + 0.11 * noise(i + 91, seed)) * s * (1.0 - 0.35 * shatter));
                boolean isShard = (i % 3 != 0);
                double a = (1.0 - shatter * 0.85) * energy;
                crossRot(pose, out, x, y, z, sz, isShard ? TILE_CRYSTAL : TILE_STAR,
                        (float) (t * 6.5 + i * 1.3),
                        argb(a, isShard ? 0.48 : 0.88, 0.94, 1.0));
            }
        } else {
            // Cold mist & glints while frozen
            for (int i = 0; i < 20; i++) {
                double phase = fract(t * 0.65 + i * 0.618);
                double ang = i * 2.399 + t * 1.2;
                float r = (float) ((0.25 + 0.55 * noise(i, seed)) * s);
                float x = (float) (Math.cos(ang) * r);
                float z = (float) (Math.sin(ang) * r);
                float y = (float) (0.2 + phase * 1.65 * s);
                crossRot(pose, out, x, y, z, (float) (0.07 * s), TILE_STAR, (float) (t * 2.0 + i),
                        argb(Math.sin(phase * Math.PI) * energy, 0.72, 0.96, 1.0));
            }
        }
    }

    // =========================================================================
    // 3D Geometry & Atlas Helpers
    // =========================================================================
    private static double fract(double n) {
        return n - Math.floor(n);
    }

    private static double noise(int i, int seed) {
        long x = (i * 0x9E3779B9L) ^ seed;
        x ^= x >>> 16;
        x *= 0x85ebca6bL;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static int argb(double a, double r, double g, double b) {
        return ((int) (Math.clamp(a, 0.0, 1.0) * 255.0) << 24)
                | ((int) (Math.clamp(r, 0.0, 1.0) * 255.0) << 16)
                | ((int) (Math.clamp(g, 0.0, 1.0) * 255.0) << 8)
                | (int) (Math.clamp(b, 0.0, 1.0) * 255.0);
    }

    private static int lerpColor(int c0, int c1, float t) {
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a = (int) (a0 + (a1 - a0) * t);
        int r = (int) (r0 + (r1 - r0) * t);
        int g = (int) (g0 + (g1 - g0) * t);
        int b = (int) (b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void ground(PoseStack.Pose pose, VertexConsumer out, float x, float z, float y, float r, int tile, int color, float angle) {
        float[] points = new float[12];
        float[] xs = {-r, r, r, -r}, zs = {-r, -r, r, r};
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        for (int i = 0; i < 4; i++) {
            points[i * 3] = x + xs[i] * cos - zs[i] * sin;
            points[i * 3 + 1] = y;
            points[i * 3 + 2] = z + xs[i] * sin + zs[i] * cos;
        }
        atlasQuadDouble(pose, out, tile, points, color);
    }

    private static void tiltedDisc(PoseStack.Pose pose, VertexConsumer out, float cx, float cy, float cz,
                                   float r, int tile, int color, float spinAngle, float tiltX, float tiltZ) {
        Matrix4f m = new Matrix4f()
                .translate(cx, cy, cz)
                .rotateX(tiltX)
                .rotateZ(tiltZ)
                .rotateY(spinAngle);
        float[] xs = {-r, r, r, -r}, zs = {-r, -r, r, r};
        float[] points = new float[12];
        for (int i = 0; i < 4; i++) {
            Vector3f v = m.transformPosition(new Vector3f(xs[i], 0.0f, zs[i]));
            points[i * 3] = v.x;
            points[i * 3 + 1] = v.y;
            points[i * 3 + 2] = v.z;
        }
        atlasQuadDouble(pose, out, tile, points, color);
    }

    private static void cross(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, float s, int tile, int color) {
        crossRot(pose, out, x, y, z, s, tile, 0.0f, color);
    }

    private static void crossRot(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, float s, int tile, float angle, int color) {
        float c = (float) Math.cos(angle) * s;
        float sn = (float) Math.sin(angle) * s;
        atlasQuadDouble(pose, out, tile, new float[]{
                x - c, y - s, z - sn,
                x + c, y - s, z + sn,
                x + c, y + s, z + sn,
                x - c, y + s, z - sn
        }, color);
        atlasQuadDouble(pose, out, tile, new float[]{
                x + sn, y - s, z - c,
                x - sn, y - s, z + c,
                x - sn, y + s, z + c,
                x + sn, y + s, z - c
        }, color);
    }

    /**
     * Draws two orthogonal intersecting 3D quad strips along a polyline `pts` (x,y,z triples)
     * using TILE_RIBBON so continuous beams, tendrils, spirals, and lightning look volumetric from all angles.
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, float[] pts,
                               float widthStart, float widthEnd, int colorStart, int colorEnd) {
        int count = pts.length / 3;
        if (count < 2) return;
        float u0 = (TILE_RIBBON % 4) * 0.25f + 0.5f / 512.0f;
        float v0 = (TILE_RIBBON / 4) * 0.5f + 0.5f / 256.0f;
        float uw = 0.25f - 1.0f / 512.0f;
        float vh = 0.5f - 1.0f / 256.0f;

        for (int i = 0; i < count - 1; i++) {
            float t0 = i / (float) (count - 1);
            float t1 = (i + 1) / (float) (count - 1);
            float w0 = widthStart + (widthEnd - widthStart) * t0;
            float w1 = widthStart + (widthEnd - widthStart) * t1;
            int c0 = lerpColor(colorStart, colorEnd, t0);
            int c1 = lerpColor(colorStart, colorEnd, t1);

            Vector3f p0 = new Vector3f(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]);
            Vector3f p1 = new Vector3f(pts[(i + 1) * 3], pts[(i + 1) * 3 + 1], pts[(i + 1) * 3 + 2]);
            Vector3f dir = new Vector3f(p1).sub(p0);
            if (dir.lengthSquared() < 1e-7f) continue;
            dir.normalize();

            Vector3f upRef = Math.abs(dir.y) > 0.92f ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
            Vector3f sideA = new Vector3f(dir).cross(upRef).normalize();
            Vector3f sideB = new Vector3f(dir).cross(sideA).normalize();

            float su0 = u0 + uw * t0;
            float su1 = u0 + uw * t1;

            for (Vector3f side : new Vector3f[]{sideA, sideB}) {
                Vertex a = new Vertex(p0.x - side.x * w0, p0.y - side.y * w0, p0.z - side.z * w0, su0, v0 + vh);
                Vertex b = new Vertex(p1.x - side.x * w1, p1.y - side.y * w1, p1.z - side.z * w1, su1, v0 + vh);
                Vertex c = new Vertex(p1.x + side.x * w1, p1.y + side.y * w1, p1.z + side.z * w1, su1, v0);
                Vertex d = new Vertex(p0.x + side.x * w0, p0.y + side.y * w0, p0.z + side.z * w0, su0, v0);
                quadColored(pose, out, a, b, c, d, c0, c1, c1, c0);
                quadColored(pose, out, d, c, b, a, c0, c1, c1, c0);
            }
        }
    }

    private static void atlasQuadDouble(PoseStack.Pose pose, VertexConsumer out, int tile, float[] p, int color) {
        float u = (tile % 4) * 0.25f + 0.5f / 512.0f;
        float v = (tile / 4) * 0.5f + 0.5f / 256.0f;
        float uw = 0.25f - 1.0f / 512.0f;
        float vh = 0.5f - 1.0f / 256.0f;
        Vertex v0 = new Vertex(p[0], p[1], p[2], u, v + vh);
        Vertex v1 = new Vertex(p[3], p[4], p[5], u + uw, v + vh);
        Vertex v2 = new Vertex(p[6], p[7], p[8], u + uw, v);
        Vertex v3 = new Vertex(p[9], p[10], p[11], u, v);
        quad(pose, out, v0, v1, v2, v3, color);
        quad(pose, out, v3, v2, v1, v0, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, Vertex a, Vertex b, Vertex c, Vertex d, int color) {
        quadColored(pose, out, a, b, c, d, color, color, color, color);
    }

    private static void quadColored(PoseStack.Pose pose, VertexConsumer out,
                                    Vertex a, Vertex b, Vertex c, Vertex d,
                                    int ca, int cb, int cc, int cd) {
        Vector3f normal = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z)
                .cross(c.x - a.x, c.y - a.y, c.z - a.z);
        if (normal.lengthSquared() < 1e-10f) normal.set(0.0f, 1.0f, 0.0f);
        else normal.normalize();
        Vertex[] vs = {a, b, c, d};
        int[] cs = {ca, cb, cc, cd};
        for (int i = 0; i < 4; i++) {
            Vertex p = vs[i];
            out.addVertex(pose.pose(), p.x, p.y, p.z)
                    .setColor(cs[i])
                    .setUv(p.u, p.v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, normal.x, normal.y, normal.z);
        }
    }

    // =========================================================================
    // Procedural 512x256 (8-Tile) HD RGBA Texture Atlas Generator
    // =========================================================================
    private static byte[] buildProceduralAtlasPng() throws IOException {
        final int width = 512;
        final int height = 256;
        final int tileSize = 128;
        byte[] rawScanlines = new byte[height * (1 + width * 4)];

        for (int tile = 0; tile < 8; tile++) {
            int tx = (tile % 4) * tileSize;
            int ty = (tile / 4) * tileSize;
            for (int y = 0; y < tileSize; y++) {
                int rowOffset = (ty + y) * (1 + width * 4) + 1;
                for (int x = 0; x < tileSize; x++) {
                    double u = (x + 0.5) / tileSize * 2.0 - 1.0;
                    double v = (y + 0.5) / tileSize * 2.0 - 1.0;
                    double r = Math.hypot(u, v);
                    double ang = Math.atan2(v, u);
                    double R = 0.0, G = 0.0, B = 0.0, A = 0.0;

                    switch (tile) {
                        case TILE_ABYSS -> {
                            double n = fbm(u * 3.5 + 4.0, v * 3.5 + 4.0, 101);
                            double jag = r + (n - 0.5) * 0.28;
                            double mask = 1.0 - KillEffectTimeline.smooth(0.58, 0.95, jag);
                            double rim = Math.exp(-Math.pow((jag - 0.56) / 0.11, 2.0)) * mask;
                            double cracks = Math.clamp(1.0 - Math.abs(Math.sin(ang * 7.0 + n * 5.0)) * 2.8, 0.0, 1.0)
                                    * KillEffectTimeline.smooth(0.15, 0.75, r) * mask;
                            double glow = Math.clamp(rim * 0.9 + cracks * 0.75, 0.0, 1.0);
                            R = Math.clamp(0.08 + glow * 0.92, 0.0, 1.0);
                            G = Math.clamp(0.02 + glow * 0.28, 0.0, 1.0);
                            B = Math.clamp(0.05 + glow * 0.18, 0.0, 1.0);
                            A = Math.clamp(mask * 0.96, 0.0, 1.0);
                        }
                        case TILE_NEBULA -> {
                            double n = fbm(u * 2.8 + 7.0, v * 2.8 + 7.0, 202);
                            double d = r + (n - 0.5) * 0.26;
                            double falloff = 1.0 - KillEffectTimeline.smooth(0.1, 0.92, d);
                            double core = 1.0 - KillEffectTimeline.smooth(0.0, 0.55, r);
                            double lum = Math.clamp(0.65 + 0.35 * n + 0.2 * core, 0.0, 1.0);
                            R = G = B = lum;
                            A = Math.clamp(falloff * falloff * (0.75 + 0.25 * n), 0.0, 1.0);
                        }
                        case TILE_STAR -> {
                            double core = Math.exp(-r * r * 14.0);
                            double spikeX = Math.exp(-Math.abs(v) * 16.0) * Math.exp(-Math.abs(u) * 2.6);
                            double spikeY = Math.exp(-Math.abs(u) * 16.0) * Math.exp(-Math.abs(v) * 2.6);
                            double diag1 = Math.exp(-Math.abs(u - v) * 18.0) * Math.exp(-r * 4.2) * 0.55;
                            double diag2 = Math.exp(-Math.abs(u + v) * 18.0) * Math.exp(-r * 4.2) * 0.55;
                            double halo = Math.pow(1.0 - KillEffectTimeline.smooth(0.0, 0.95, r), 2.0) * 0.35;
                            double val = Math.clamp(core + spikeX + spikeY + diag1 + diag2 + halo, 0.0, 1.0);
                            R = G = B = Math.clamp(0.85 + 0.15 * core, 0.0, 1.0);
                            A = val * (1.0 - KillEffectTimeline.smooth(0.85, 1.0, r));
                        }
                        case TILE_RING -> {
                            double ring1 = Math.exp(-Math.pow((r - 0.72) / 0.085, 2.0));
                            double ring2 = Math.exp(-Math.pow((r - 0.52) / 0.045, 2.0)) * 0.45;
                            double ripples = 0.75 + 0.25 * Math.sin(ang * 12.0 + r * 18.0);
                            double innerGlow = KillEffectTimeline.smooth(0.0, 0.72, r)
                                    * (1.0 - KillEffectTimeline.smooth(0.72, 0.95, r)) * 0.28;
                            double val = Math.clamp((ring1 * ripples + ring2 + innerGlow)
                                    * (1.0 - KillEffectTimeline.smooth(0.88, 0.99, r)), 0.0, 1.0);
                            R = G = B = Math.clamp(0.8 + 0.2 * ring1, 0.0, 1.0);
                            A = val;
                        }
                        case TILE_RUNE -> {
                            double outerRing = Math.exp(-Math.pow((r - 0.84) / 0.032, 2.0));
                            double midRing = Math.exp(-Math.pow((r - 0.64) / 0.028, 2.0));
                            double innerRing = Math.exp(-Math.pow((r - 0.34) / 0.025, 2.0));
                            double star = 0.0;
                            double maskIn = 1.0 - KillEffectTimeline.smooth(0.62, 0.66, r);
                            for (int k = 0; k < 4; k++) {
                                double ak = k * Math.PI / 4.0;
                                double rx = u * Math.cos(ak) + v * Math.sin(ak);
                                double ry = -u * Math.sin(ak) + v * Math.cos(ak);
                                double l1 = Math.exp(-Math.pow((Math.abs(rx) - 0.26) / 0.022, 2.0)) * maskIn;
                                double l2 = Math.exp(-Math.pow((Math.abs(ry) - 0.26) / 0.022, 2.0)) * maskIn;
                                star = Math.max(star, Math.max(l1, l2));
                            }
                            double inBand = KillEffectTimeline.smooth(0.66, 0.69, r)
                                    * (1.0 - KillEffectTimeline.smooth(0.79, 0.82, r));
                            double sector = (ang + Math.PI) / (2.0 * Math.PI) * 16.0;
                            int secId = (int) sector;
                            double secF = sector - secId;
                            double runeGlyph = inBand
                                    * KillEffectTimeline.smooth(0.15, 0.25, secF)
                                    * (1.0 - KillEffectTimeline.smooth(0.75, 0.85, secF))
                                    * (0.5 + 0.5 * Math.sin(r * 48.0 + secId * 2.1));
                            double baseGlow = (1.0 - KillEffectTimeline.smooth(0.0, 0.92, r)) * 0.18;
                            double val = Math.clamp(outerRing + midRing + innerRing + star * 0.85 + runeGlyph * 0.9 + baseGlow, 0.0, 1.0)
                                    * (1.0 - KillEffectTimeline.smooth(0.90, 0.98, r));
                            R = G = B = Math.clamp(0.85 + 0.15 * val, 0.0, 1.0);
                            A = val;
                        }
                        case TILE_SWIRL -> {
                            double n = fbm(u * 3.0 - 2.0, v * 3.0 + 2.0, 505);
                            double spiralPhase = ang * 3.0 - r * 9.5 + n * 1.8;
                            double arms = Math.pow(0.5 + 0.5 * Math.sin(spiralPhase), 2.0);
                            double arms2 = Math.pow(0.5 + 0.5 * Math.cos(ang * 2.0 - r * 12.0 - n * 1.5), 3.0);
                            double envelope = KillEffectTimeline.smooth(0.08, 0.28, r)
                                    * (1.0 - KillEffectTimeline.smooth(0.65, 0.96, r));
                            double val = Math.clamp((arms * 0.75 + arms2 * 0.55) * envelope, 0.0, 1.0);
                            R = G = B = Math.clamp(0.75 + 0.25 * arms, 0.0, 1.0);
                            A = val;
                        }
                        case TILE_CRYSTAL -> {
                            double dDiam = Math.abs(u) * 1.75 + Math.abs(v) * 0.98;
                            double inside = 1.0 - KillEffectTimeline.smooth(0.78, 0.86, dDiam);
                            double edge = Math.exp(-Math.pow((dDiam - 0.76) / 0.06, 2.0));
                            double ridgeV = Math.exp(-Math.pow(Math.abs(u) / 0.045, 2.0)) * inside;
                            double ridgeH = Math.exp(-Math.pow(Math.abs(v) / 0.055, 2.0)) * inside * 0.7;
                            double facetShade = 0.65 + 0.25 * (u * v > 0 ? 1.0 : -0.3);
                            double lum = Math.clamp(facetShade * 0.75 + edge * 0.9 + ridgeV * 0.85 + ridgeH * 0.6, 0.0, 1.0);
                            R = G = B = lum;
                            A = Math.clamp(inside * 0.92 + edge * 0.5, 0.0, 1.0);
                        }
                        default -> {
                            double n = fbm(u * 4.0 + 1.0, v * 4.0 + 1.0, 707);
                            double core = Math.exp(-Math.pow(v * 3.6, 2.0));
                            double glow = Math.exp(-Math.pow(v * 1.65, 2.0)) * (0.7 + 0.3 * n);
                            double endTaper = 1.0 - KillEffectTimeline.smooth(0.75, 0.98, Math.abs(u));
                            double val = Math.clamp((core * 0.95 + glow * 0.65) * endTaper, 0.0, 1.0);
                            R = G = B = Math.clamp(0.8 + 0.2 * core, 0.0, 1.0);
                            A = val;
                        }
                    }

                    int idx = rowOffset + (tx + x) * 4;
                    rawScanlines[idx] = (byte) (int) (R * 255.0);
                    rawScanlines[idx + 1] = (byte) (int) (G * 255.0);
                    rawScanlines[idx + 2] = (byte) (int) (B * 255.0);
                    rawScanlines[idx + 3] = (byte) (int) (A * 255.0);
                }
            }
        }

        return encodeRgbaPng(width, height, rawScanlines);
    }

    private static double hashNoise(int x, int y, int seed) {
        int n = x * 374761393 + y * 668265263 ^ seed * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double valueNoise(double x, double y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double n00 = hashNoise(ix, iy, seed);
        double n10 = hashNoise(ix + 1, iy, seed);
        double n01 = hashNoise(ix, iy + 1, seed);
        double n11 = hashNoise(ix + 1, iy + 1, seed);
        return (n00 * (1.0 - ux) + n10 * ux) * (1.0 - uy) + (n01 * (1.0 - ux) + n11 * ux) * uy;
    }

    private static double fbm(double x, double y, int seed) {
        double v = 0.0;
        double amp = 0.5;
        for (int i = 0; i < 4; i++) {
            v += amp * valueNoise(x, y, seed + i * 97);
            x *= 2.02;
            y *= 2.02;
            amp *= 0.5;
        }
        return v;
    }

    private static byte[] encodeRgbaPng(int width, int height, byte[] rawScanlines) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        out.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
        DataOutputStream ihdrData = new DataOutputStream(ihdr);
        ihdrData.writeInt(width);
        ihdrData.writeInt(height);
        ihdrData.writeByte(8); // bit depth
        ihdrData.writeByte(6); // RGBA color type
        ihdrData.writeByte(0); // compression
        ihdrData.writeByte(0); // filter
        ihdrData.writeByte(0); // interlace
        writePngChunk(out, "IHDR", ihdr.toByteArray());

        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        ByteArrayOutputStream idat = new ByteArrayOutputStream(rawScanlines.length / 2);
        try {
            deflater.setInput(rawScanlines);
            deflater.finish();
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                if (count > 0) idat.write(buf, 0, count);
            }
        } finally {
            deflater.end();
        }
        writePngChunk(out, "IDAT", idat.toByteArray());
        writePngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        dos.write(typeBytes);
        dos.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }
}
