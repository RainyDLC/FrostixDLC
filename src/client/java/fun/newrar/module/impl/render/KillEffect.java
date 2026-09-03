package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.manager.event_impl.AttackEvent;
import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.preview.ModulePreview;
import fun.newrar.module.api.preview.PreviewContext;
import fun.newrar.module.api.preview.PreviewSettings;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleInfo(
        name = "Kill Effect",
        desc = "Эффект ниспадающего светового луча на месте ликвидации противника",
        category = Category.RENDER
)
public class KillEffect extends Module implements ModulePreview {
    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting effect = new ModeSetting(this, "Effect", "Beam", "Vortex", "Supernova");
    public ModeSetting typeColor = new ModeSetting(this, "Color mode", "Theme", "Custom");
    public ColorSetting tintColor = new ColorSetting(this, "Color", 0xFF00FFFF).setVisible(() -> typeColor.is("Custom"));

    public SliderSetting duration = new SliderSetting(this, "Duration", 2.2F, 1.0F, 5.0F, 0.1F);
    public SliderSetting beamHeight = new SliderSetting(this, "Beam height", 5.2F, 1.5F, 12.0F, 0.5F);
    public SliderSetting beamWidth = new SliderSetting(this, "Beam width", 0.42F, 0.08F, 1.2F, 0.02F);

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 2.5F);

    private static final byte DEATH_STATUS = 3;
    private static final long KILL_WINDOW_MS = 6500L;
    private static final long DUPLICATE_WINDOW_MS = 750L;

    private final List<KillInstance> instances = new ArrayList<>();
    private final BufferAllocator allocator = new BufferAllocator(1 << 16);

    private LivingEntity lastTarget;
    private KillPoint lastPoint;
    private int lastTargetId = -1;
    private long lastAttackTime;
    private int lastSpawnedId = -1;
    private long lastSpawnTime;

    public int getColor() {
        if (typeColor.is("Theme")) {
            return ColorUtil.getClientColor1(1);
        }
        return tintColor.getValue();
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        clearState();
    }

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public boolean previewNeedsDummy() {
        return true;
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        LivingEntity target = ctx.dummy();
        KillPoint point = target != null
                ? point(target)
                : new KillPoint(ctx.anchor(), 0.65F);
        instances.add(new KillInstance(point, effect.getValue()));
    }

    @Override
    public void previewStop() {
        clearState();
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (!(e.getTarget() instanceof LivingEntity living) || living == mc.player) return;

        lastTarget = living;
        lastTargetId = living.getId();
        lastAttackTime = System.currentTimeMillis();
        lastPoint = point(living);
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (!(e.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != DEATH_STATUS || mc.world == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof LivingEntity living)) return;
        trySpawn(living, false);
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (lastTarget == null) return;
        if (System.currentTimeMillis() - lastAttackTime > KILL_WINDOW_MS) {
            lastTarget = null;
            lastPoint = null;
            return;
        }

        if (!lastTarget.isAlive() || lastTarget.isRemoved() || lastTarget.getHealth() <= 0.0F) {
            trySpawn(lastTarget, true);
        }
    }

    private void trySpawn(LivingEntity entity, boolean allowCachedPoint) {
        long now = System.currentTimeMillis();
        if (entity.getId() != lastTargetId || now - lastAttackTime > KILL_WINDOW_MS) return;
        if (lastSpawnedId == entity.getId() && now - lastSpawnTime < DUPLICATE_WINDOW_MS) return;

        KillPoint p = allowCachedPoint && lastPoint != null ? lastPoint : point(entity);
        instances.add(new KillInstance(p, effect.getValue()));

        lastSpawnedId = entity.getId();
        lastSpawnTime = now;
        lastTarget = null;
        lastPoint = null;
    }

    private KillPoint point(LivingEntity entity) {
        return new KillPoint(entity.getEntityPos(), Math.max(0.65F, entity.getWidth()));
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        if (instances.isEmpty() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long durMs = (long) (duration.getValue() * 1000F);

        int color = getColor();
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        MatrixStack matrices = e.getMatrixStack();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        Iterator<KillInstance> it = instances.iterator();
        while (it.hasNext()) {
            KillInstance inst = it.next();
            float progress = (now - inst.start) / (float) durMs;
            if (progress >= 1F) {
                it.remove();
                continue;
            }

            float fadeIn = MathHelper.clamp(progress * 5.5F, 0F, 1F);
            float fadeOut = progress > 0.5F ? 1F - (progress - 0.5F) / 0.5F : 1F;
            inst.alpha = smooth(fadeIn) * smooth(fadeOut);
            inst.progress = progress;
        }

        if (instances.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buf = immediate.getBuffer(BEAM_LAYER);

        float pulse = 0.9F + 0.1F * MathHelper.sin(now % 1600L / 1600F * MathHelper.TAU);
        for (KillInstance inst : instances) {
            if ("Vortex".equalsIgnoreCase(inst.effectType)) {
                drawVortex(buf, mat, cam, inst, cr, cg, cb, pulse);
            } else if ("Supernova".equalsIgnoreCase(inst.effectType)) {
                drawSupernova(buf, mat, cam, inst, cr, cg, cb, pulse);
            } else {
                drawBeam(buf, mat, cam, inst, cr, cg, cb, pulse);
            }
        }

        immediate.draw();
    }

    private void drawBeam(VertexConsumer buf, Matrix4f mat, Vec3d cam, KillInstance beam,
                          int r, int g, int b, float pulse) {
        if (beam.alpha <= 0.01F) return;

        float grow = smooth(Math.min(beam.progress * 3.5F, 1F));
        float h = beamHeight.getValue() * grow;
        float base = beamWidth.getValue() * beam.radius;
        float x = (float) (beam.x - cam.x);
        float y = (float) (beam.y - cam.y + 0.035F);
        float z = (float) (beam.z - cam.z);

        float spin = (System.currentTimeMillis() - beam.start) / 1000F * 0.85F;
        float coreAlpha = 150F * beam.alpha * pulse;
        float softAlpha = 54F * beam.alpha;

        drawVerticalPetals(buf, mat, x, y, z, h, base * 0.72F, spin, r, g, b, (int) coreAlpha, 6);
        drawVerticalPetals(buf, mat, x, y, z, h * 0.82F, base * 1.28F, -spin * 0.55F, r, g, b, (int) softAlpha, 8);

        float bloom = 0.55F + 0.45F * grow;
        drawSoftDisc(buf, mat, x, y + 0.01F, z, base * 2.25F * bloom, r, g, b, (int) (42F * beam.alpha));
        drawSoftDisc(buf, mat, x, y + h * 0.98F, z, base * 0.92F * beam.alpha, r, g, b, (int) (28F * beam.alpha));

        for (int i = 0; i < 3; i++) {
            float wave = (beam.progress + i * 0.22F) % 1F;
            float radius = base * (1.05F + wave * 2.25F);
            int alpha = (int) (52F * beam.alpha * (1F - wave));
            drawRing(buf, mat, x, y + i * 0.025F, z, radius, 0.035F + wave * 0.045F, r, g, b, alpha);
        }
    }

    private void drawVortex(VertexConsumer buf, Matrix4f mat, Vec3d cam, KillInstance inst,
                            int r, int g, int b, float pulse) {
        if (inst.alpha <= 0.01F) return;

        float grow = smooth(Math.min(inst.progress * 2.8F, 1F));
        float h = beamHeight.getValue() * grow;
        float base = beamWidth.getValue() * inst.radius * 1.35F;
        float x = (float) (inst.x - cam.x);
        float y = (float) (inst.y - cam.y + 0.035F);
        float z = (float) (inst.z - cam.z);

        long elapsed = System.currentTimeMillis() - inst.start;
        float spin = elapsed / 1000F * 2.2F;

        drawHelicalRibbons(buf, mat, x, y, z, h, base, spin, r, g, b, (int) (130F * inst.alpha), 3, 28);

        drawVerticalPetals(buf, mat, x, y, z, h * 0.9F, base * 0.35F, spin * 2.4F, r, g, b, (int) (160F * inst.alpha * pulse), 4);
        drawVerticalPetals(buf, mat, x, y, z, h * 0.7F, base * 0.65F, -spin * 1.5F, r, g, b, (int) (65F * inst.alpha), 6);

        float midY = y + h * 0.42F;
        drawTiltedRing(buf, mat, x, midY, z, base * 1.4F, 0.045F, 35F, 0F, spin * 1.4F, r, g, b, (int) (85F * inst.alpha));
        drawTiltedRing(buf, mat, x, midY, z, base * 1.75F, 0.035F, -40F, 25F, -spin * 1.1F, r, g, b, (int) (65F * inst.alpha));

        drawSoftDisc(buf, mat, x, y + 0.01F, z, base * 2.6F * grow, r, g, b, (int) (55F * inst.alpha));

        for (int i = 0; i < 3; i++) {
            float wave = (inst.progress * 1.3F + i * 0.33F) % 1F;
            float radius = base * (0.8F + wave * 2.8F);
            int alpha = (int) (60F * inst.alpha * (1F - wave));
            drawRing(buf, mat, x, y + i * 0.02F, z, radius, 0.04F + wave * 0.03F, r, g, b, alpha);
        }

        drawVortexSparks(buf, mat, x, y, z, h, base, spin, inst.progress, inst.alpha, r, g, b);
    }

    private void drawSupernova(VertexConsumer buf, Matrix4f mat, Vec3d cam, KillInstance inst,
                               int r, int g, int b, float pulse) {
        if (inst.alpha <= 0.01F) return;

        float h = beamHeight.getValue() * 0.75F;
        float base = beamWidth.getValue() * inst.radius * 1.5F;
        float x = (float) (inst.x - cam.x);
        float y = (float) (inst.y - cam.y + 0.035F);
        float z = (float) (inst.z - cam.z);
        float centerY = y + Math.max(1.0F, h * 0.35F);

        long elapsed = System.currentTimeMillis() - inst.start;
        float spin = elapsed / 1000F * 1.4F;

        if (inst.progress < 0.22F) {
            float t = inst.progress / 0.22F;
            float shrink = 1.0F - t;
            float inRadius = base * (3.6F * shrink);
            int gatherAlpha = (int) (140F * inst.alpha * t);

            drawRing(buf, mat, x, centerY, z, inRadius, 0.05F, r, g, b, gatherAlpha);
            drawSoftDisc(buf, mat, x, centerY, z, base * (0.4F + 0.8F * t), 255, 255, 255, (int) (180F * inst.alpha * t));
            drawSoftDisc(buf, mat, x, y + 0.01F, z, inRadius * 0.7F, r, g, b, gatherAlpha / 2);
        } else {
            float burstProgress = (inst.progress - 0.22F) / 0.78F;
            float burstEase = smooth(Math.min(burstProgress * 2.2F, 1.0F));
            float burstFade = 1.0F - burstProgress;

            float coreR = base * (0.8F + 1.6F * (1.0F - burstEase));
            drawSoftDisc(buf, mat, x, centerY, z, coreR, 255, 255, 255, (int) (190F * inst.alpha * burstFade));
            drawSoftDisc(buf, mat, x, centerY, z, coreR * 1.8F, r, g, b, (int) (90F * inst.alpha * burstFade));

            float rayLen = base * (2.8F + 6.0F * burstEase);
            int rayAlpha = (int) (170F * inst.alpha * burstFade);
            drawStarburstRays(buf, mat, x, centerY, z, rayLen, base * 0.18F, spin, r, g, b, rayAlpha);

            float waveR = base * (0.6F + 7.5F * burstEase);
            int waveAlpha = (int) (140F * inst.alpha * burstFade * (1.0F - burstEase * 0.6F));
            drawRing(buf, mat, x, y + 0.01F, z, waveR, 0.08F + burstEase * 0.06F, r, g, b, waveAlpha);
            drawRing(buf, mat, x, centerY, z, waveR * 0.85F, 0.06F, r, g, b, waveAlpha / 2);

            drawTiltedRing(buf, mat, x, centerY, z, waveR * 0.65F, 0.05F, 28F, -18F, spin * 0.8F, r, g, b, (int) (waveAlpha * 0.8F));

            drawSupernovaSparks(buf, mat, x, centerY, z, base, burstProgress, inst.alpha, r, g, b);
        }
    }

    private static void drawHelicalRibbons(VertexConsumer buf, Matrix4f mat, float cx, float cy, float cz,
                                           float h, float baseR, float spin, int r, int g, int b,
                                           int alpha, int strands, int segments) {
        if (alpha <= 0 || h <= 0.02F) return;

        for (int s = 0; s < strands; s++) {
            float strandAngle = s * (MathHelper.TAU / strands);

            for (int i = 0; i < segments; i++) {
                float t0 = i / (float) segments;
                float t1 = (i + 1) / (float) segments;

                float y0 = cy + h * t0;
                float y1 = cy + h * t1;

                float r0 = baseR * (0.9F + 1.1F * t0 * t0 + 0.3F * MathHelper.sin(t0 * (float) Math.PI));
                float r1 = baseR * (0.9F + 1.1F * t1 * t1 + 0.3F * MathHelper.sin(t1 * (float) Math.PI));

                float a0 = spin + strandAngle + t0 * 3.4F * (float) Math.PI;
                float a1 = spin + strandAngle + t1 * 3.4F * (float) Math.PI;

                float x0 = MathHelper.cos(a0) * r0;
                float z0 = MathHelper.sin(a0) * r0;
                float x1 = MathHelper.cos(a1) * r1;
                float z1 = MathHelper.sin(a1) * r1;

                float ribbonW = baseR * 0.12F * (1F - t0 * 0.45F);
                float wx0 = -MathHelper.sin(a0) * ribbonW;
                float wz0 = MathHelper.cos(a0) * ribbonW;
                float wx1 = -MathHelper.sin(a1) * ribbonW;
                float wz1 = MathHelper.cos(a1) * ribbonW;

                int segAlpha0 = (int) (alpha * (1F - t0 * 0.7F));
                int segAlpha1 = (int) (alpha * (1F - t1 * 0.7F));

                buf.vertex(mat, cx + x0 - wx0, y0, cz + z0 - wz0).color(r, g, b, segAlpha0);
                buf.vertex(mat, cx + x0 + wx0, y0, cz + z0 + wz0).color(r, g, b, segAlpha0);
                buf.vertex(mat, cx + x1 + wx1, y1, cz + z1 + wz1).color(r, g, b, segAlpha1);
                buf.vertex(mat, cx + x1 - wx1, y1, cz + z1 - wz1).color(r, g, b, segAlpha1);
            }
        }
    }

    private static void drawTiltedRing(VertexConsumer buf, Matrix4f mat, float cx, float cy, float cz,
                                       float radius, float width, float pitchDeg, float rollDeg,
                                       float yawRad, int r, int g, int b, int alpha) {
        if (alpha <= 0 || radius <= width) return;

        float pRad = (float) Math.toRadians(pitchDeg);
        float rRad = (float) Math.toRadians(rollDeg);
        float cosP = MathHelper.cos(pRad);
        float sinP = MathHelper.sin(pRad);
        float cosR = MathHelper.cos(rRad);
        float sinR = MathHelper.sin(rRad);

        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a0 = yawRad + i / (float) segments * MathHelper.TAU;
            float a1 = yawRad + (i + 1) / (float) segments * MathHelper.TAU;

            float lx0_in = MathHelper.cos(a0) * (radius - width);
            float lz0_in = MathHelper.sin(a0) * (radius - width);
            float lx0_out = MathHelper.cos(a0) * radius;
            float lz0_out = MathHelper.sin(a0) * radius;

            float lx1_in = MathHelper.cos(a1) * (radius - width);
            float lz1_in = MathHelper.sin(a1) * (radius - width);
            float lx1_out = MathHelper.cos(a1) * radius;
            float lz1_out = MathHelper.sin(a1) * radius;

            float x0_in = lx0_in * cosR;
            float y0_in = lx0_in * sinR * sinP + lz0_in * sinP;
            float z0_in = lz0_in * cosP;

            float x0_out = lx0_out * cosR;
            float y0_out = lx0_out * sinR * sinP + lz0_out * sinP;
            float z0_out = lz0_out * cosP;

            float x1_in = lx1_in * cosR;
            float y1_in = lx1_in * sinR * sinP + lz1_in * sinP;
            float z1_in = lz1_in * cosP;

            float x1_out = lx1_out * cosR;
            float y1_out = lx1_out * sinR * sinP + lz1_out * sinP;
            float z1_out = lz1_out * cosP;

            buf.vertex(mat, cx + x0_in, cy + y0_in, cz + z0_in).color(r, g, b, 0);
            buf.vertex(mat, cx + x0_out, cy + y0_out, cz + z0_out).color(r, g, b, alpha);
            buf.vertex(mat, cx + x1_out, cy + y1_out, cz + z1_out).color(r, g, b, alpha);
            buf.vertex(mat, cx + x1_in, cy + y1_in, cz + z1_in).color(r, g, b, 0);
        }
    }

    private static void drawVortexSparks(VertexConsumer buf, Matrix4f mat, float cx, float cy, float cz,
                                         float h, float baseR, float spin, float progress, float beamAlpha,
                                         int r, int g, int b) {
        int sparkCount = 14;
        for (int p = 0; p < sparkCount; p++) {
            float pProg = (progress * 2.0F + p * (1.0F / sparkCount)) % 1.0F;
            float pY = cy + h * pProg;
            float pAngle = spin * 2.8F + p * (MathHelper.TAU / sparkCount) + pProg * 4.0F * (float) Math.PI;
            float pR = baseR * (0.65F + 1.25F * pProg);

            float px = cx + MathHelper.cos(pAngle) * pR;
            float pz = cz + MathHelper.sin(pAngle) * pR;

            float sparkSize = 0.04F * (1.0F - pProg * 0.6F) * beamAlpha;
            int sparkAlpha = (int) (190F * beamAlpha * (1.0F - pProg));
            drawDiamondSpark(buf, mat, px, pY, pz, sparkSize, r, g, b, sparkAlpha);
        }
    }

    private static void drawStarburstRays(VertexConsumer buf, Matrix4f mat, float cx, float cy, float cz,
                                          float len, float width, float spin, int r, int g, int b, int alpha) {
        if (alpha <= 0 || len <= 0.01F) return;

        float[][] DIRS = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
                {0.707F, 0.707F, 0}, {-0.707F, 0.707F, 0}, {0.707F, -0.707F, 0}, {-0.707F, -0.707F, 0},
                {0, 0.707F, 0.707F}, {0, 0.707F, -0.707F}, {0.707F, 0, 0.707F}, {-0.707F, 0, 0.707F}
        };

        float cosS = MathHelper.cos(spin);
        float sinS = MathHelper.sin(spin);

        for (float[] dir : DIRS) {
            float dx = dir[0] * cosS - dir[2] * sinS;
            float dy = dir[1];
            float dz = dir[0] * sinS + dir[2] * cosS;

            float tipX = cx + dx * len;
            float tipY = cy + dy * len;
            float tipZ = cz + dz * len;

            float px = -dz * width;
            float py = width * 0.5F;
            float pz = dx * width;

            buf.vertex(mat, cx - px, cy - py, cz - pz).color(r, g, b, alpha);
            buf.vertex(mat, tipX, tipY, tipZ).color(255, 255, 255, 0);
            buf.vertex(mat, tipX, tipY, tipZ).color(255, 255, 255, 0);
            buf.vertex(mat, cx + px, cy + py, cz + pz).color(r, g, b, alpha);
        }
    }

    private static void drawSupernovaSparks(VertexConsumer buf, Matrix4f mat, float cx, float cy, float cz,
                                            float baseR, float progress, float beamAlpha,
                                            int r, int g, int b) {
        int count = 16;
        float expand = smooth(Math.min(progress * 1.8F, 1.0F));
        float fade = 1.0F - progress;

        for (int i = 0; i < count; i++) {
            float theta = i / (float) count * MathHelper.TAU;
            float phi = (float) Math.sin(i * 1.7F) * 0.8F;

            float dist = baseR * (1.2F + 5.5F * expand);
            float sx = cx + MathHelper.cos(theta) * MathHelper.cos(phi) * dist;
            float sy = cy + MathHelper.sin(phi) * dist + expand * 0.8F;
            float sz = cz + MathHelper.sin(theta) * MathHelper.cos(phi) * dist;

            float size = 0.05F * fade * beamAlpha;
            int sparkAlpha = (int) (180F * beamAlpha * fade);
            drawDiamondSpark(buf, mat, sx, sy, sz, size, r, g, b, sparkAlpha);
        }
    }

    private static void drawDiamondSpark(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                         float s, int r, int g, int b, int alpha) {
        if (alpha <= 0 || s <= 0.001F) return;
        buf.vertex(mat, x, y - s, z).color(r, g, b, alpha);
        buf.vertex(mat, x + s, y, z + s).color(r, g, b, alpha);
        buf.vertex(mat, x, y + s, z).color(r, g, b, alpha);
        buf.vertex(mat, x - s, y, z - s).color(r, g, b, alpha);
    }

    private static void drawVerticalPetals(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                           float h, float halfWidth, float spin, int r, int g, int b,
                                           int alpha, int petals) {
        if (alpha <= 0 || h <= 0.01F) return;

        for (int i = 0; i < petals; i++) {
            float ang = spin + i / (float) petals * MathHelper.TAU;
            float dx = MathHelper.cos(ang) * halfWidth;
            float dz = MathHelper.sin(ang) * halfWidth;
            int edgeAlpha = Math.max(0, alpha / 3);

            buf.vertex(mat, x - dx, y, z - dz).color(r, g, b, edgeAlpha);
            buf.vertex(mat, x, y + h * 0.08F, z).color(r, g, b, alpha);
            buf.vertex(mat, x, y + h, z).color(r, g, b, 0);
            buf.vertex(mat, x + dx, y, z + dz).color(r, g, b, edgeAlpha);
        }
    }

    private static void drawSoftDisc(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                     float radius, int r, int g, int b, int alpha) {
        if (alpha <= 0 || radius <= 0.01F) return;

        int segments = 56;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * MathHelper.TAU;
            float a1 = (i + 1) / (float) segments * MathHelper.TAU;
            buf.vertex(mat, x, y, z).color(r, g, b, alpha);
            buf.vertex(mat, x + MathHelper.cos(a0) * radius, y, z + MathHelper.sin(a0) * radius).color(r, g, b, 0);
            buf.vertex(mat, x + MathHelper.cos(a1) * radius, y, z + MathHelper.sin(a1) * radius).color(r, g, b, 0);
            buf.vertex(mat, x, y, z).color(r, g, b, alpha);
        }
    }

    private static void drawRing(VertexConsumer buf, Matrix4f mat, float x, float y, float z,
                                 float radius, float width, int r, int g, int b, int alpha) {
        if (alpha <= 0 || radius <= width) return;

        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * MathHelper.TAU;
            float a1 = (i + 1) / (float) segments * MathHelper.TAU;

            float x0 = MathHelper.cos(a0);
            float z0 = MathHelper.sin(a0);
            float x1 = MathHelper.cos(a1);
            float z1 = MathHelper.sin(a1);

            buf.vertex(mat, x + x0 * (radius - width), y, z + z0 * (radius - width)).color(r, g, b, 0);
            buf.vertex(mat, x + x0 * radius, y, z + z0 * radius).color(r, g, b, alpha);
            buf.vertex(mat, x + x1 * radius, y, z + z1 * radius).color(r, g, b, alpha);
            buf.vertex(mat, x + x1 * (radius - width), y, z + z1 * (radius - width)).color(r, g, b, 0);
        }
    }

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private void clearState() {
        instances.clear();
        lastTarget = null;
        lastPoint = null;
        lastTargetId = -1;
        lastSpawnedId = -1;
    }

    private record KillPoint(Vec3d pos, float radius) {
    }

    private static class KillInstance {
        final double x, y, z;
        final float radius;
        final long start;
        final String effectType;
        float alpha;
        float progress;

        KillInstance(KillPoint point, String effectType) {
            this.x = point.pos.x;
            this.y = point.pos.y;
            this.z = point.pos.z;
            this.radius = point.radius;
            this.start = System.currentTimeMillis();
            this.effectType = effectType;
        }
    }

    private static final RenderPipeline BEAM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "kill_effect_beam"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BEAM_LAYER = RenderLayer.of(
            "kill_effect_beam",
            RenderSetup.builder(BEAM_PIPELINE).expectedBufferSize(1 << 15).build()
    );
}
