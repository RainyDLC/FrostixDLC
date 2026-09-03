package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
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
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import fun.newrar.manager.event_impl.EventRender3D;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class SkyRainRenderer {
    private static final int CROWN_CAP = 140;
    private static final long CROWN_DURATION = 320L;
    private static final int PARTICLE_CAP = 300;
    private static final long PARTICLE_DURATION = 380L;

    private static final Identifier MIST_TEXTURE =
            Identifier.of("client", "textures/particles/glow.png");

    private static final RenderPipeline STREAK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/storm_streaks"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer STREAK_LAYER = RenderLayer.of("storm_streaks",
            RenderSetup.builder(STREAK_PIPELINE).translucent().expectedBufferSize(1 << 16).build());

    private static final RenderPipeline MIST_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/storm_mist"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer MIST_LAYER = RenderLayer.of("storm_mist",
            RenderSetup.builder(MIST_PIPELINE)
                    .texture("Sampler0", MIST_TEXTURE)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build());

    private static class RainStreak {
        double posX, posY, posZ;
        double velX, velY, velZ;
        double mass;
        float phase;
        float luminance;
        int groundElevation;
    }

    private static class SplashCrown {
        final double worldX, worldY, worldZ;
        final long spawnTime;
        final float size;

        SplashCrown(double x, double y, double z, long time, float size) {
            this.worldX = x;
            this.worldY = y;
            this.worldZ = z;
            this.spawnTime = time;
            this.size = size;
        }
    }

    private static class BounceShard {
        double worldX, worldY, worldZ;
        double velX, velY, velZ;
        final long spawnTime;

        BounceShard(double x, double y, double z, double vx, double vy, double vz, long time) {
            this.worldX = x;
            this.worldY = y;
            this.worldZ = z;
            this.velX = vx;
            this.velY = vy;
            this.velZ = vz;
            this.spawnTime = time;
        }
    }

    private static class GroundFog {
        double orbitAngle, orbitDist;
        float seed;
        double groundY = Double.NaN;
        long nextProbeTime;
    }

    private static final List<RainStreak> activeDrops = new ArrayList<>();
    private static final List<SplashCrown> splashCrowns = new ArrayList<>();
    private static final List<BounceShard> bounceShards = new ArrayList<>();
    private static GroundFog[] fogParticles;

    private static boolean optSplashes = true;
    private static boolean optMist = true;
    private static boolean optWind = true;
    private static float optWindForce = 40f;
    private static float optAreaRadius = 22f;
    private static int optTargetDrops = 500;
    private static float optRainAlpha = 0.75f;

    private static final float windTheta = (float) (Math.random() * Math.PI * 2.0);
    private static float windVecX, windVecZ;
    private static long lastRenderNanos = 0L;
    private static final Random rng = new Random();
    private static final BlockPos.Mutable probePos = new BlockPos.Mutable();

    private SkyRainRenderer() {
    }

    public static void update(boolean enabled, int count, float radius,
                              boolean windOn, float windStrength,
                              boolean splashesOn, boolean mistOn,
                              float dropAlpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.world == null) {
            clear();
            return;
        }

        optSplashes = splashesOn;
        optMist = mistOn;
        optWind = windOn;
        optWindForce = windStrength;
        optAreaRadius = radius;
        optTargetDrops = Math.max(10, Math.min(count, 4000));
        optRainAlpha = MathHelper.clamp(dropAlpha, 0.05f, 1.0f);

        float windScale = windOn ? (windStrength / 100f) * 1.6f : 0f;
        windVecX = (float) Math.cos(windTheta) * windScale;
        windVecZ = (float) Math.sin(windTheta) * windScale;
    }

    private static void advanceSimulation(MinecraftClient mc, float dt) {
        long now = System.currentTimeMillis();
        float timeSec = (now % 600_000L) / 1000f;

        float gust = 0.75f + 0.25f * (float) Math.sin(timeSec * 0.7f);
        float currentWindX = windVecX * gust;
        float currentWindZ = windVecZ * gust;

        int targetCount = optTargetDrops;

        if (activeDrops.isEmpty()) {
            for (int i = 0; i < targetCount; i++) {
                RainStreak drop = new RainStreak();
                spawnDrop(mc, drop, optAreaRadius, false);
                activeDrops.add(drop);
            }
        } else if (activeDrops.size() > targetCount) {
            while (activeDrops.size() > targetCount) {
                activeDrops.remove(activeDrops.size() - 1);
            }
        } else {
            while (activeDrops.size() < targetCount) {
                RainStreak drop = new RainStreak();
                spawnDrop(mc, drop, optAreaRadius, true);
                activeDrops.add(drop);
            }
        }

        Iterator<RainStreak> it = activeDrops.iterator();
        while (it.hasNext()) {
            RainStreak d = it.next();

            double drag = 1.0 - Math.min(0.88, Math.abs(d.velY) / (32.0 * d.mass));
            d.velY -= 21.0 * d.mass * drag * dt;
            if (d.velY < -34.0 * d.mass) d.velY = -34.0 * d.mass;

            double localGust = 0.7 + 0.3 * Math.sin(timeSec * 0.9 + d.phase);
            d.velX += (currentWindX * localGust - d.velX) * Math.min(1.0, dt * 3.5);
            d.velZ += (currentWindZ * localGust - d.velZ) * Math.min(1.0, dt * 3.5);

            d.posX += d.velX * dt;
            d.posY += d.velY * dt;
            d.posZ += d.velZ * dt;

            double dx = d.posX - mc.player.getX();
            double dz = d.posZ - mc.player.getZ();
            double boundary = optAreaRadius * 1.45;

            boolean hitSurface = false;
            if (d.posY <= d.groundElevation + 1.2) {
                if (checkBlockImpact(mc, d.posX, d.posY, d.posZ)) {
                    hitSurface = true;
                } else if (d.posY <= d.groundElevation - 2.5) {
                    hitSurface = true;
                }
            }

            if (dx * dx + dz * dz > boundary * boundary || hitSurface) {
                if (hitSurface && d.posY >= d.groundElevation - 3.0) {
                    double impactGroundY = Math.floor(d.posY) + 1.01;
                    if (optSplashes) {
                        spawnSplashCrown(d.posX, impactGroundY, d.posZ, now, (float) (0.26 + 0.16 * d.mass));
                        spawnBounceShards(d.posX, impactGroundY, d.posZ, d.velX, d.velZ, d.mass, now);
                    }
                }
                if (activeDrops.size() > targetCount) {
                    it.remove();
                } else {
                    spawnDrop(mc, d, optAreaRadius, true);
                }
            }
        }

        splashCrowns.removeIf(c -> now - c.spawnTime > CROWN_DURATION);

        for (int i = bounceShards.size() - 1; i >= 0; i--) {
            BounceShard s = bounceShards.get(i);
            if (now - s.spawnTime > PARTICLE_DURATION) {
                bounceShards.remove(i);
                continue;
            }
            s.velY -= 13.0 * dt;
            s.worldX += s.velX * dt;
            s.worldY += s.velY * dt;
            s.worldZ += s.velZ * dt;
        }

        advanceFog(mc, now, optAreaRadius, dt);
    }

    private static void spawnDrop(MinecraftClient mc, RainStreak d, float radius, boolean fromSkyTop) {
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(rng.nextDouble()) * radius;

        d.posX = mc.player.getX() + Math.cos(angle) * dist;
        d.posZ = mc.player.getZ() + Math.sin(angle) * dist;
        d.posY = fromSkyTop
                ? mc.player.getY() + 18.0 + rng.nextDouble() * 8.0
                : mc.player.getY() + 2.0 + rng.nextDouble() * 22.0;

        d.mass = 0.65 + rng.nextDouble() * 0.75;
        d.velY = -(14.0 + rng.nextDouble() * 10.0) * d.mass;
        d.velX = windVecX;
        d.velZ = windVecZ;
        d.phase = (float) (rng.nextDouble() * 62.8);
        d.luminance = (float) (0.65 + rng.nextDouble() * 0.35);
        d.groundElevation = sampleTerrainElevation(mc, MathHelper.floor(d.posX), MathHelper.floor(d.posZ));
    }

    private static void spawnSplashCrown(double x, double y, double z, long now, float scale) {
        if (splashCrowns.size() >= CROWN_CAP) splashCrowns.remove(0);
        splashCrowns.add(new SplashCrown(x, y, z, now, scale));
    }

    private static void spawnBounceShards(double x, double y, double z, double vx, double vz, double mass, long now) {
        if (bounceShards.size() >= PARTICLE_CAP) return;
        int count = 3 + rng.nextInt(2);
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2.0;
            double sp = (1.3 + rng.nextDouble() * 1.6) * mass;
            double svx = Math.cos(a) * sp * 0.45 + vx * 0.08;
            double svz = Math.sin(a) * sp * 0.45 + vz * 0.08;
            double svy = 1.9 + rng.nextDouble() * 2.2;
            bounceShards.add(new BounceShard(x, y + 0.02, z, svx, svy, svz, now));
        }
    }

    private static void advanceFog(MinecraftClient mc, long now, float radius, float dt) {
        if (!optMist) return;
        if (fogParticles == null || fogParticles.length != 14) {
            fogParticles = new GroundFog[14];
            for (int i = 0; i < 14; i++) {
                GroundFog f = new GroundFog();
                f.orbitAngle = Math.random() * Math.PI * 2.0;
                f.orbitDist = 3.5 + Math.random() * (radius - 3.5);
                f.seed = (float) (Math.random() * 100.0);
                fogParticles[i] = f;
            }
        }

        for (GroundFog f : fogParticles) {
            f.orbitAngle += 0.08 * dt * (0.7 + 0.3 * Math.sin(f.seed));
            if (now >= f.nextProbeTime) {
                f.nextProbeTime = now + 500 + (long) (Math.random() * 500);
                double fx = mc.player.getX() + Math.cos(f.orbitAngle) * f.orbitDist;
                double fz = mc.player.getZ() + Math.sin(f.orbitAngle) * f.orbitDist;
                f.groundY = sampleTerrainElevation(mc, MathHelper.floor(fx), MathHelper.floor(fz));
            }
        }
    }

    public static void clear() {
        activeDrops.clear();
        splashCrowns.clear();
        bounceShards.clear();
        lastRenderNanos = 0L;
    }

    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        long nowNanos = System.nanoTime();
        if (lastRenderNanos == 0L) lastRenderNanos = nowNanos;
        float dt = (float) ((nowNanos - lastRenderNanos) / 1_000_000_000.0);
        lastRenderNanos = nowNanos;
        dt = MathHelper.clamp(dt, 0.0f, 0.05f);

        advanceSimulation(mc, dt);

        if (activeDrops.isEmpty() && splashCrowns.isEmpty() && bounceShards.isEmpty()) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();

        float thunder = SkyLightningRenderer.flashLevel();
        float brightness = 0.88f * (1.0f + thunder * 0.65f);

        Vector3fc look = camera.getHorizontalPlane();
        float billX = -look.z();
        float billZ = look.x();

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        if (!activeDrops.isEmpty()) {
            VertexConsumer streakBuf = consumers.getBuffer(STREAK_LAYER);
            for (RainStreak d : activeDrops) {
                double dx = d.posX - camPos.x;
                double dy = d.posY - camPos.y;
                double dz = d.posZ - camPos.z;

                double spd = Math.sqrt(d.velX * d.velX + d.velY * d.velY + d.velZ * d.velZ);
                if (spd < 0.1) continue;

                float streakLen = (float) MathHelper.clamp(spd * 0.046, 0.24, 1.45);
                float radiusW = (float) (0.011 + 0.013 * d.mass);

                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float distFade = 1.0f - MathHelper.clamp((dist - optAreaRadius * 0.72f) / (optAreaRadius * 0.28f + 1f), 0f, 1f);
                if (distFade <= 0.01f) continue;

                int headAlpha = (int) (115 * d.luminance * distFade * brightness * optRainAlpha);
                int coreAlpha = (int) (200 * d.luminance * distFade * brightness * optRainAlpha);

                int colorHead = packRgba(lerp(185, 240, thunder), lerp(210, 245, thunder), 255, headAlpha);
                int colorTail = packRgba(lerp(185, 240, thunder), lerp(210, 245, thunder), 255, 0);

                float bx = (float) dx;
                float by = (float) dy;
                float bz = (float) dz;
                float tx = (float) (dx - d.velX * (streakLen / spd));
                float ty = (float) (dy - d.velY * (streakLen / spd));
                float tz = (float) (dz - d.velZ * (streakLen / spd));

                pushQuad(streakBuf, matrix,
                        bx - billX * radiusW, by, bz - billZ * radiusW, colorHead,
                        bx + billX * radiusW, by, bz + billZ * radiusW, colorHead,
                        tx + billX * radiusW * 0.35f, ty, tz + billZ * radiusW * 0.35f, colorTail,
                        tx - billX * radiusW * 0.35f, ty, tz - billZ * radiusW * 0.35f, colorTail);

                float innerW = radiusW * 0.35f;
                int colorCore = packRgba(245, 250, 255, coreAlpha);
                int colorCoreTail = packRgba(245, 250, 255, 0);
                pushQuad(streakBuf, matrix,
                        bx - billX * innerW, by, bz - billZ * innerW, colorCore,
                        bx + billX * innerW, by, bz + billZ * innerW, colorCore,
                        tx + billX * innerW * 0.2f, ty, tz + billZ * innerW * 0.2f, colorCoreTail,
                        tx - billX * innerW * 0.2f, ty, tz - billZ * innerW * 0.2f, colorCoreTail);
            }
            consumers.draw(STREAK_LAYER);
        }

        long currentMillis = System.currentTimeMillis();

        if (optSplashes && !splashCrowns.isEmpty()) {
            VertexConsumer crownBuf = consumers.getBuffer(STREAK_LAYER);
            for (SplashCrown c : splashCrowns) {
                float life = (currentMillis - c.spawnTime) / (float) CROWN_DURATION;
                if (life >= 1f) continue;

                float lift = (float) Math.sin(life * Math.PI);
                float crownH = c.size * 0.44f * lift;
                float crownR = c.size * (0.14f + 0.46f * life);
                int alpha = (int) ((1f - life) * 135 * brightness * optRainAlpha);
                int col = packRgba(lerp(200, 248, thunder), lerp(220, 252, thunder), 255, alpha);

                float cx = (float) (c.worldX - camPos.x);
                float cy = (float) (c.worldY - camPos.y) + 0.015f;
                float cz = (float) (c.worldZ - camPos.z);

                drawCrown(crownBuf, matrix, cx, cy, cz, crownR, crownH, col, 10);
            }
            consumers.draw(STREAK_LAYER);
        }

        if (optSplashes && !bounceShards.isEmpty()) {
            VertexConsumer shardBuf = consumers.getBuffer(STREAK_LAYER);
            for (BounceShard s : bounceShards) {
                float life = (currentMillis - s.spawnTime) / (float) PARTICLE_DURATION;
                if (life >= 1f) continue;

                float fade = 1f - life;
                int alpha = (int) (fade * 145 * brightness * optRainAlpha);
                int col = packRgba(lerp(210, 250, thunder), lerp(225, 252, thunder), 255, alpha);

                float sx = (float) (s.worldX - camPos.x);
                float sy = (float) (s.worldY - camPos.y);
                float sz = (float) (s.worldZ - camPos.z);
                float shardW = 0.015f * fade;
                float shardH = 0.055f * fade;

                pushQuad(shardBuf, matrix,
                        sx - billX * shardW, sy, sz - billZ * shardW, col,
                        sx + billX * shardW, sy, sz + billZ * shardW, col,
                        sx + billX * shardW * 0.4f, sy + shardH, sz + billZ * shardW * 0.4f, withAlpha(col, 20),
                        sx - billX * shardW * 0.4f, sy + shardH, sz - billZ * shardW * 0.4f, withAlpha(col, 20));
            }
            consumers.draw(STREAK_LAYER);
        }

        if (optMist && fogParticles != null) {
            VertexConsumer mistBuf = consumers.getBuffer(MIST_LAYER);
            for (GroundFog f : fogParticles) {
                if (Double.isNaN(f.groundY)) continue;
                double fx = mc.player.getX() + Math.cos(f.orbitAngle) * f.orbitDist;
                double fz = mc.player.getZ() + Math.sin(f.orbitAngle) * f.orbitDist;

                float cx = (float) (fx - camPos.x);
                float cy = (float) (f.groundY + 0.45 - camPos.y);
                float cz = (float) (fz - camPos.z);

                float pulse = 0.7f + 0.3f * (float) Math.sin((currentMillis % 100_000L) / 750f + f.seed);
                float radius = 1.9f + 0.5f * pulse;
                int alpha = (int) (24 * pulse * brightness);
                int col = packRgba(lerp(185, 235, thunder), lerp(205, 242, thunder), 255, alpha);

                mistBuf.vertex(matrix, cx - billX * radius, cy - radius * 0.4f, cz - billZ * radius).texture(0f, 1f).color(col);
                mistBuf.vertex(matrix, cx + billX * radius, cy - radius * 0.4f, cz + billZ * radius).texture(1f, 1f).color(col);
                mistBuf.vertex(matrix, cx + billX * radius, cy + radius * 0.4f, cz + billZ * radius).texture(1f, 0f).color(col);
                mistBuf.vertex(matrix, cx - billX * radius, cy + radius * 0.4f, cz - billZ * radius).texture(0f, 0f).color(col);
            }
            consumers.draw(MIST_LAYER);
        }
    }

    private static void drawCrown(VertexConsumer buf, Matrix4f m, float cx, float cy, float cz,
                                  float r, float h, int color, int segs) {
        int zeroAlpha = withAlpha(color, 0);
        float baseR = r * 0.35f;

        for (int i = 0; i < segs; i++) {
            double a0 = (Math.PI * 2.0 / segs) * i;
            double a1 = (Math.PI * 2.0 / segs) * (i + 1);

            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            pushQuad(buf, m,
                    cx + c0 * baseR, cy, cz + s0 * baseR, color,
                    cx + c1 * baseR, cy, cz + s1 * baseR, color,
                    cx + c1 * r, cy + h, cz + s1 * r, zeroAlpha,
                    cx + c0 * r, cy + h, cz + s0 * r, zeroAlpha);
        }
    }

    private static void pushQuad(VertexConsumer buf, Matrix4f m,
                                 float x0, float y0, float z0, int c0,
                                 float x1, float y1, float z1, int c1,
                                 float x2, float y2, float z2, int c2,
                                 float x3, float y3, float z3, int c3) {
        buf.vertex(m, x0, y0, z0).color(c0);
        buf.vertex(m, x1, y1, z1).color(c1);
        buf.vertex(m, x2, y2, z2).color(c2);
        buf.vertex(m, x3, y3, z3).color(c3);
    }

    private static boolean checkBlockImpact(MinecraftClient mc, double x, double y, double z) {
        if (mc.world == null) return false;
        probePos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        if (!mc.world.getChunkManager().isChunkLoaded(probePos.getX() >> 4, probePos.getZ() >> 4)) {
            return false;
        }
        BlockState bs = mc.world.getBlockState(probePos);
        if (!bs.getFluidState().isEmpty()) return true;
        if (bs.isAir()) return false;
        try {
            return !bs.getCollisionShape(mc.world, probePos).isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int sampleTerrainElevation(MinecraftClient mc, int x, int z) {
        if (mc.world == null) return 0;
        try {
            return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        } catch (Throwable ignored) {
            return mc.world.getBottomY();
        }
    }

    private static int packRgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int withAlpha(int rgba, int a) {
        return (rgba & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a)) & 0xFF) << 24);
    }

    private static int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * Math.max(0f, Math.min(1f, t)));
    }
}
