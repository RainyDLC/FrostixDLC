package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.render.LightningPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SkyLightningRenderer {
    private static final Identifier GLOW_TEX =
            Identifier.of("client", "textures/particles/glow.png");
    private static final long STRIKE_LIFE = 1050L;
    private static final long EMBER_LIFE = 2600L;
    private static final float FLASH_TIME = 480F;

    private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/skystorm_color"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/skystorm_glow"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer OUTER_LAYER = RenderLayer.of("skystorm_outer",
            RenderSetup.builder(COLOR_PIPELINE).translucent().expectedBufferSize(1 << 16).build());
    private static final RenderLayer CORE_LAYER = RenderLayer.of("skystorm_core",
            RenderSetup.builder(COLOR_PIPELINE).translucent().expectedBufferSize(1 << 16).build());
    private static final RenderLayer GLOW_LAYER = RenderLayer.of("skystorm_glow",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEX)
                    .translucent()
                    .expectedBufferSize(1 << 14)
                    .build());

    private static class Spark {
        double px, py, pz;
        double vx, vy, vz;
        final long born;
        final float life;
        final float size;
        final int color;

        Spark(double x, double y, double z, double vx, double vy, double vz, long born, float life, float size, int col) {
            this.px = x;
            this.py = y;
            this.pz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.born = born;
            this.life = life;
            this.size = size;
            this.color = col;
        }
    }

    private static class Strike {
        boolean isCloudCrawler;
        List<Vec3d> pts = new ArrayList<>();
        List<List<Vec3d>> branches = new ArrayList<>();
        Vec3d impact;
        long born;
        float seed;
        long propagateMs;
        final int[] branchAttach = new int[8];
        int branchCount;
        double distanceToPlayer;
    }

    private static final List<Strike> strikes = new ArrayList<>();
    private static final List<Spark> sparks = new ArrayList<>();

    private static long nextStrikeAt;
    private static final Random random = new Random();
    private static float cameraShake = 0.0f;
    private static long lastShakeTime = 0L;

    private static final Vector3f CAM_RIGHT = new Vector3f();
    private static final Vector3f CAM_UP = new Vector3f();

    private SkyLightningRenderer() {
    }

    public static void update(boolean enabled, float intervalSec, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        strikes.removeIf(s -> now - s.born > STRIKE_LIFE + EMBER_LIFE);

        if (!enabled || mc.player == null || mc.world == null) {
            clear();
            return;
        }

        if (now < nextStrikeAt) return;

        nextStrikeAt = now + (long) (Math.max(0.3f, intervalSec) * 1000f * (0.65f + random.nextFloat() * 0.7f));
        spawn(radius);
        if (random.nextFloat() < 0.28f) spawn(radius * 1.25f);
    }

    public static void clear() {
        strikes.clear();
        sparks.clear();
        cameraShake = 0.0f;
    }

    public static float flashLevel() {
        long now = System.currentTimeMillis();
        float f = 0f;
        for (Strike s : strikes) {
            long ms = now - s.born - s.propagateMs;
            if (ms < 0 || ms > FLASH_TIME + 520L) continue;

            double pulse1 = Math.exp(-ms / 130.0);
            double pulse2 = 0.78 * Math.exp(-sq((ms - 110.0) / 65.0));
            double pulse3 = 0.54 * Math.exp(-sq((ms - 230.0) / 75.0));
            double pulse4 = 0.32 * Math.exp(-sq((ms - 410.0) / 80.0));

            double e = pulse1 + pulse2 + pulse3 + pulse4;
            f += (float) Math.min(1.35, e);
        }
        return Math.min(1f, f);
    }

    private static void spawn(float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        boolean isCrawler = random.nextFloat() < 0.28f;

        float yaw = mc.gameRenderer.getCamera().getYaw();
        double baseAng = Math.toRadians(yaw) + Math.PI * 0.5;
        double spread = Math.toRadians(120.0);
        double ang = baseAng + (random.nextDouble() - 0.5) * spread;

        double dist = radius * (0.35f + 0.65f * random.nextFloat());
        double x = mc.player.getX() + Math.cos(ang) * dist;
        double z = mc.player.getZ() + Math.sin(ang) * dist;

        Strike s = new Strike();
        s.isCloudCrawler = isCrawler;
        s.born = System.currentTimeMillis();
        s.seed = random.nextFloat();
        s.distanceToPlayer = dist;

        if (isCrawler) {
            double skyY = mc.player.getY() + 65.0 + random.nextInt(25);
            double crawlLen = 50.0 + random.nextDouble() * 55.0;
            double crawlAngle = ang + (random.nextDouble() - 0.5) * 1.5;
            Vec3d start = new Vec3d(x, skyY, z);
            Vec3d end = new Vec3d(
                    x + Math.cos(crawlAngle) * crawlLen,
                    skyY + (random.nextDouble() - 0.5) * 12.0,
                    z + Math.sin(crawlAngle) * crawlLen
            );

            s.impact = end;
            s.propagateMs = 180 + random.nextInt(90);
            s.pts.addAll(LightningPath.generate(start, end, 5, crawlLen * 0.16, 0.62, random));

            int branchCount = 4 + random.nextInt(4);
            for (int b = 0; b < branchCount; b++) {
                if (s.pts.size() < 8 || s.branchCount >= 8) break;
                int idx = 3 + random.nextInt(Math.max(1, s.pts.size() - 6));
                Vec3d from = s.pts.get(idx);
                double blen = 8.0 + random.nextDouble() * 16.0;
                double ba = crawlAngle + (random.nextBoolean() ? 0.6 : -0.6) + (random.nextDouble() - 0.5) * 0.4;
                Vec3d bend = new Vec3d(
                        from.x + Math.cos(ba) * blen,
                        from.y + (random.nextDouble() - 0.5) * 8.0,
                        from.z + Math.sin(ba) * blen
                );
                s.branchAttach[s.branchCount++] = idx;
                s.branches.add(LightningPath.generate(from, bend, 3, blen * 0.32, 0.58, random));
            }
        } else {
            int groundY = findGround((int) Math.floor(x), (int) Math.floor(z), mc.player.getBlockY());
            if (groundY == Integer.MIN_VALUE) return;

            Vec3d impact = new Vec3d(x, groundY + 1.01, z);
            double skyH = 60 + random.nextInt(35);
            Vec3d start = new Vec3d(
                    x + (random.nextDouble() - 0.5) * 14.0,
                    impact.y + skyH,
                    z + (random.nextDouble() - 0.5) * 14.0
            );
            Vec3d end = new Vec3d(
                    x + (random.nextDouble() - 0.5) * 1.5,
                    impact.y,
                    z + (random.nextDouble() - 0.5) * 1.5
            );

            s.impact = impact;
            s.propagateMs = 130 + random.nextInt(60);
            s.pts.addAll(LightningPath.generate(start, end, 5, skyH * 0.12, 0.64, random));

            float minY = (float) impact.y - 0.05f;
            for (int i = 0; i < s.pts.size(); i++) {
                Vec3d p = s.pts.get(i);
                if (p.y < minY) s.pts.set(i, new Vec3d(p.x, minY, p.z));
            }

            int branchCount = 4 + random.nextInt(4);
            for (int b = 0; b < branchCount; b++) {
                if (s.pts.size() < 6 || s.branchCount >= 8) break;
                int idx = 3 + random.nextInt(Math.max(1, s.pts.size() - 5));
                Vec3d from = s.pts.get(idx);
                double blen = 5.0 + random.nextDouble() * 12.0;
                double ba = random.nextDouble() * Math.PI * 2.0;
                Vec3d bend = new Vec3d(
                        from.x + Math.cos(ba) * blen,
                        from.y - blen * (0.65 + random.nextDouble() * 0.55),
                        from.z + Math.sin(ba) * blen
                );
                s.branchAttach[s.branchCount++] = idx;
                s.branches.add(LightningPath.generate(from, bend, 3, blen * 0.36, 0.6, random));
            }

            spawnImpactSparks(impact, s.born);

            if (dist < 32.0) {
                cameraShake = Math.max(cameraShake, (float) (1.0 - dist / 32.0));
            }
        }

        strikes.add(s);
    }

    private static void spawnImpactSparks(Vec3d impact, long now) {
        int count = 18 + random.nextInt(10);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizSpeed = 4.0 + random.nextDouble() * 9.0;
            double vx = Math.cos(angle) * horizSpeed;
            double vz = Math.sin(angle) * horizSpeed;
            double vy = 3.5 + random.nextDouble() * 8.0;

            float life = 0.7f + random.nextFloat() * 0.9f;
            float size = 0.08f + random.nextFloat() * 0.12f;
            int col = argb(210 + random.nextInt(45), 225 + random.nextInt(30), 255, 240);

            sparks.add(new Spark(impact.x, impact.y + 0.1, impact.z, vx, vy, vz, now, life, size, col));
        }
    }

    private static int findGround(int bx, int bz, int playerY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int top = playerY + 45;
        int bottom = playerY - 30;
        for (int y = top; y >= bottom; y--) {
            var state = mc.world.getBlockState(BlockPos.ofFloored(bx, y, bz));
            if (!state.isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (strikes.isEmpty() && sparks.isEmpty() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        float dt = lastShakeTime == 0L ? 0.016f : (float) ((now - lastShakeTime) / 1000.0);
        lastShakeTime = now;
        dt = MathHelper.clamp(dt, 0.001f, 0.05f);

        if (cameraShake > 0.005f) {
            cameraShake = Math.max(0.0f, cameraShake - dt * 2.8f);
        }

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        var camRot = mc.gameRenderer.getCamera().getRotation();
        Vector3f right = camRot.transform(CAM_RIGHT.set(1, 0, 0));
        Vector3f up = camRot.transform(CAM_UP.set(0, 1, 0));

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = new Matrix4f(e.getMatrixStack().peek().getPositionMatrix());

        if (cameraShake > 0.01f) {
            float shakePhase = (now % 1000L) * 0.045f;
            float offX = (float) Math.sin(shakePhase * 1.7) * cameraShake * 0.045f;
            float offY = (float) Math.cos(shakePhase * 2.1) * cameraShake * 0.035f;
            matrix.translate(offX, offY, 0.0f);
        }

        VertexConsumer glowBuf = consumers.getBuffer(GLOW_LAYER);

        for (Strike s : strikes) {
            long age = now - s.born;
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));

            if (!s.isCloudCrawler && s.impact != null) {
                float tE = (age - s.propagateMs) / (float) EMBER_LIFE;
                if (tE >= 0f && tE < 1f) {
                    float eA = (1f - tE) * (1f - tE) * flicker((long) (now * 0.6f), s.seed);
                    sprite(glowBuf, matrix, s.impact.add(0, 0.35 + 0.85 * tE, 0), camPos, right, up,
                            0.95f + 1.2f * tE, argb(195, 225, 255, (int) (eA * 105)));
                }
            }

            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int glowCol = argb(160, 205, 255, (int) (Math.min(1f, env) * flicker * 95));

            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);

            float head = 1f - Math.min(1f, age / 260f);
            if (head > 0.01f && !s.pts.isEmpty()) {
                sprite(glowBuf, matrix, s.pts.get(0), camPos, right, up,
                        9.0f + 4.0f * (1f - head),
                        argb(210, 235, 255, (int) (head * env * 185)));

                sprite(glowBuf, matrix, s.pts.get(0), camPos, right, up,
                        26f + 10f * (1f - head),
                        argb(185, 215, 255, (int) (head * env * 90)));
            }

            for (int i = 0; i < mainLim; i += 2) {
                sprite(glowBuf, matrix, s.pts.get(i), camPos, right, up,
                        1.2f + 0.85f * flicker, glowCol);
            }

            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = (age - s.propagateMs * s.branchAttach[bi] / (float) Math.max(1, s.pts.size() - 1))
                        / (float) Math.max(1L, s.propagateMs);
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                int lim = Math.max(2, (int) Math.ceil(Math.min(1f, bp) * (br.size() - 1)) + 1);
                for (int i = 0; i < lim; i += 2) {
                    sprite(glowBuf, matrix, br.get(i), camPos, right, up,
                            0.75f + 0.5f * flicker, glowCol);
                }
            }

            if (!s.isCloudCrawler && s.impact != null && prog >= 1f) {
                float flash = flashEnv(age - s.propagateMs);
                if (flash > 0.01f) {
                    sprite(glowBuf, matrix, s.impact.add(0, 0.9, 0), camPos, right, up,
                            4.5f + 3.5f * (1f - flash), argb(215, 235, 255, (int) (flash * env * 220)));
                }
            }
        }

        float skyFl = flashLevel();
        WorldTweaks wt = WorldTweaks.get();
        float flashSetting = (wt != null && wt.isEnabled()) ? wt.skyFlash.getValue() : 45.0f;
        if (skyFl > 0.005f && flashSetting > 0.5f) {
            float alpha = Math.min(1.0f, skyFl * (flashSetting / 100.0f) * 0.42f);
            int flashSkyCol = argb(215, 235, 255, (int) (alpha * 255));
            for (float dy = 25.0f; dy <= 75.0f; dy += 25.0f) {
                sprite(glowBuf, matrix, new Vec3d(camPos.x, camPos.y + dy, camPos.z), camPos, right, up, 160.0f, flashSkyCol);
            }
        }

        consumers.draw(GLOW_LAYER);

        VertexConsumer outerBuf = consumers.getBuffer(OUTER_LAYER);
        for (Strike s : strikes) {
            long age = now - s.born;
            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int col = argb(130, 185, 255, (int) (Math.min(1f, env) * flicker * 145));
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));
            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);

            float wMain = s.isCloudCrawler ? 0.22f : 0.19f;
            ribbonPolyline(outerBuf, matrix, s.pts, camPos, wMain, col, mainLim);

            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = Math.min(1f, (age - s.propagateMs * s.branchAttach[bi]
                        / (float) Math.max(1, s.pts.size() - 1)) / (float) Math.max(1L, s.propagateMs));
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                ribbonPolyline(outerBuf, matrix, br, camPos, 0.125f, col,
                        Math.max(2, (int) Math.ceil(bp * (br.size() - 1)) + 1));
            }
        }
        consumers.draw(OUTER_LAYER);

        VertexConsumer coreBuf = consumers.getBuffer(CORE_LAYER);
        for (Strike s : strikes) {
            long age = now - s.born;
            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int core = argb(245, 250, 255, (int) (Math.min(1f, env) * flicker * 245));
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));
            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);

            float wCore = s.isCloudCrawler ? 0.09f : 0.078f;
            ribbonPolyline(coreBuf, matrix, s.pts, camPos, wCore, core, mainLim);

            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = Math.min(1f, (age - s.propagateMs * s.branchAttach[bi]
                        / (float) Math.max(1, s.pts.size() - 1)) / (float) Math.max(1L, s.propagateMs));
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                ribbonPolyline(coreBuf, matrix, br, camPos, 0.052f, core,
                        Math.max(2, (int) Math.ceil(bp * (br.size() - 1)) + 1));
            }

            if (!s.isCloudCrawler && s.impact != null && prog >= 1f) {
                float tR = (age - s.propagateMs) / FLASH_TIME;
                if (tR >= 0f && tR < 1f) {
                    float ringR = 0.5f + 5.2f * (1f - (1f - tR) * (1f - tR));
                    int ringCol = argb(195, 225, 255, (int) ((1f - tR) * Math.min(1f, env) * 160));
                    groundRing(coreBuf, matrix, s.impact, ringR, 0.28f, ringCol);
                }
            }
        }

        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark sp = sparks.get(i);
            float ageSec = (now - sp.born) / 1000.0f;
            if (ageSec > sp.life) {
                sparks.remove(i);
                continue;
            }

            sp.vy -= 14.0 * dt;
            sp.px += sp.vx * dt;
            sp.py += sp.vy * dt;
            sp.pz += sp.vz * dt;

            float lifeRatio = 1.0f - (ageSec / sp.life);
            int alpha = (int) (lifeRatio * 230);
            int sparkColor = withAlpha(sp.color, alpha);

            float sx = (float) (sp.px - camPos.x);
            float sy = (float) (sp.py - camPos.y);
            float sz = (float) (sp.pz - camPos.z);
            float szRadius = sp.size * lifeRatio;

            coreBuf.vertex(matrix, sx - right.x * szRadius, sy - right.y * szRadius, sz - right.z * szRadius).color(sparkColor);
            coreBuf.vertex(matrix, sx + right.x * szRadius, sy + right.y * szRadius, sz + right.z * szRadius).color(sparkColor);
            coreBuf.vertex(matrix, sx + up.x * szRadius, sy + up.y * szRadius, sz + up.z * szRadius).color(sparkColor);
            coreBuf.vertex(matrix, sx - up.x * szRadius, sy - up.y * szRadius, sz - up.z * szRadius).color(sparkColor);
        }

        consumers.draw(CORE_LAYER);
    }

    private static float envelope(long ageMs) {
        if (ageMs < 0 || ageMs >= STRIKE_LIFE) return 0f;
        double pulse1 = Math.exp(-ageMs / 220.0);
        double pulse2 = 0.88 * Math.exp(-sq((ageMs - 240.0) / 85.0));
        double pulse3 = 0.65 * Math.exp(-sq((ageMs - 450.0) / 80.0));
        float t = ageMs / (float) STRIKE_LIFE;
        float endFade = t > 0.84f ? (1f - t) / 0.16f : 1f;
        return (float) Math.min(1.3, pulse1 + pulse2 + pulse3) * endFade;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static float flashEnv(long ageMs) {
        return Math.max(0f, 1f - ageMs / FLASH_TIME);
    }

    private static float flicker(long now, float seed) {
        return 0.74f + 0.26f * (float) Math.sin(now * 0.13 + seed * 97.0);
    }

    private static int argb(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgba, int a) {
        return (rgba & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a)) & 0xFF) << 24);
    }

    private static void sprite(VertexConsumer buf, Matrix4f matrix, Vec3d pos, Vec3d camPos,
                               Vector3f right, Vector3f up, float half, int color) {
        float px = (float) (pos.x - camPos.x);
        float py = (float) (pos.y - camPos.y);
        float pz = (float) (pos.z - camPos.z);
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;

        buf.vertex(matrix, px - rx - ux, py - ry - uy, pz - rz - uz).texture(0f, 0f).color(r, g, b, a);
        buf.vertex(matrix, px - rx + ux, py - ry + uy, pz - rz + uz).texture(0f, 1f).color(r, g, b, a);
        buf.vertex(matrix, px + rx + ux, py + ry + uy, pz + rz + uz).texture(1f, 1f).color(r, g, b, a);
        buf.vertex(matrix, px + rx - ux, py + ry - uy, pz - rz - uz).texture(1f, 0f).color(r, g, b, a);
    }

    private static void ribbonPolyline(VertexConsumer buf, Matrix4f matrix,
                                       List<Vec3d> pts, Vec3d camPos, float halfW, int color, int maxVerts) {
        int segs = Math.min(pts.size(), maxVerts) - 1;
        for (int i = 0; i < segs; i++) {
            Vec3d a = pts.get(i);
            Vec3d b = pts.get(i + 1);
            float ax = (float) (a.x - camPos.x), ay = (float) (a.y - camPos.y), az = (float) (a.z - camPos.z);
            float bx = (float) (b.x - camPos.x), by = (float) (b.y - camPos.y), bz = (float) (b.z - camPos.z);

            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float cx = ay * dz - az * dy;
            float cy = az * dx - ax * dz;
            float cz = ax * dy - ay * dx;
            float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1e-6f) continue;
            cx = cx / len * halfW;
            cy = cy / len * halfW;
            cz = cz / len * halfW;

            vertex(buf, matrix, ax - cx, ay - cy, az - cz, color);
            vertex(buf, matrix, ax + cx, ay + cy, az + cz, color);
            vertex(buf, matrix, bx + cx, by + cy, bz + cz, color);
            vertex(buf, matrix, bx - cx, by - cy, bz - cz, color);
        }
    }

    private static void groundRing(VertexConsumer buf, Matrix4f matrix,
                                   Vec3d impact, float radius, float width, int color) {
        float cx = (float) (impact.x - MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos().x);
        float cy = (float) (impact.y + 0.05 - MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos().y);
        float cz = (float) (impact.z - MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos().z);

        int segs = 36;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;
            vertex(buf, matrix, cx + (float) Math.cos(a0) * radius, cy, cz + (float) Math.sin(a0) * radius, color);
            vertex(buf, matrix, cx + (float) Math.cos(a1) * radius, cy, cz + (float) Math.sin(a1) * radius, color);
            vertex(buf, matrix, cx + (float) Math.cos(a1) * (radius - width), cy, cz + (float) Math.sin(a1) * (radius - width), color);
            vertex(buf, matrix, cx + (float) Math.cos(a0) * (radius - width), cy, cz + (float) Math.sin(a0) * (radius - width), color);
        }
    }

    private static void vertex(VertexConsumer buf, Matrix4f matrix, float x, float y, float z, int color) {
        buf.vertex(matrix, x, y, z).color(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f
        );
    }
}
