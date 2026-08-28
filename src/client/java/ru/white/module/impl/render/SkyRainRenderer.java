package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventRender3D;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SkyRainRenderer {
    private static final int MAX_RIPPLES = 90;
    private static final long RIPPLE_LIFE = 650L;
    private static final int MAX_SPLASHES = 64;
    private static final long SPLASH_LIFE = 340L;
    private static final int MIST_COUNT = 12;
    private static final Identifier GLOW_TEX =
            Identifier.of("client", "textures/particles/glow.png");

    private static final RenderPipeline RAIN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/skystorm_rain"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer RAIN_LAYER = RenderLayer.of("skystorm_rain",
            RenderSetup.builder(RAIN_PIPELINE).translucent().expectedBufferSize(1 << 16).build());

    private static final RenderPipeline MIST_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/skystorm_mist"))
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

    private static final RenderLayer MIST_LAYER = RenderLayer.of("skystorm_mist",
            RenderSetup.builder(MIST_PIPELINE)
                    .texture("Sampler0", GLOW_TEX)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build());

    private static class Drop {
        double x, y, prevY, z;
        double vx, vz;
        double speed;
        double floorTop;
        float len;
        boolean water;
    }

    private static class Ripple {
        final double x, y, z;
        final long born;

        Ripple(double x, double y, double z, long born) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.born = born;
        }
    }

    private static class Splash {
        final double x, y, z;
        final long born;
        final float rot;

        Splash(double x, double y, double z, long born, float rot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.born = born;
            this.rot = rot;
        }
    }

    private static class Mist {
        double ang, dist;
        float seed;
        double y = Double.NaN;
        long nextSample;
    }

    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Ripple> ripples = new ArrayList<>();
    private static final List<Splash> splashes = new ArrayList<>();
    private static Mist[] mists;
    private static long nextAmbientRipple;

    private static boolean cfgSplashes = true;
    private static boolean cfgMist = true;
    private static boolean cfgWind = true;
    private static float cfgWindStrength = 40f;
    private static float cfgRadius = 18f;

    private static final float windBaseAngle = (float) (Math.random() * Math.PI * 2);
    private static float windX, windZ;

    private SkyRainRenderer() {
    }

    public static void update(boolean enabled, int count, float radius,
                              boolean windOn, float windStrength,
                              boolean splashesOn, boolean mistOn) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.world == null) {
            drops.clear();
            ripples.clear();
            splashes.clear();
            mists = null;
            return;
        }
        cfgSplashes = splashesOn;
        cfgMist = mistOn;
        cfgWind = windOn && windStrength > 0.5f;
        cfgWindStrength = windStrength;
        cfgRadius = radius;

        updateWind(now());

        while (drops.size() < count) {
            Drop d = new Drop();
            respawn(mc, d, radius);
            d.y = mc.player.getY() + 8.0 + Math.random() * 18.0;
            drops.add(d);
        }
        while (drops.size() > count) {
            drops.remove(drops.size() - 1);
        }

        long now = now();

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.prevY = d.y;
            d.y -= d.speed * 0.05;

            double dx = d.x - mc.player.getX();
            double dz = d.z - mc.player.getZ();
            double maxDist = radius * 1.6;
            if (dx * dx + dz * dz > maxDist * maxDist || d.y <= d.floorTop) {
                if (d.y <= d.floorTop) {
                    if (d.water) {
                        addRipple(d.x, d.floorTop, d.z, now);
                    } else if (cfgSplashes) {
                        addSplash(d.x, d.floorTop, d.z, now);
                    }
                }
                if (drops.size() > count) {
                    it.remove();
                } else {
                    respawn(mc, d, radius);
                }
            }
        }

        ripples.removeIf(r -> now - r.born > RIPPLE_LIFE);
        splashes.removeIf(s -> now - s.born > SPLASH_LIFE);

        if (now >= nextAmbientRipple) {
            nextAmbientRipple = now + 40;
            for (int a = 0; a < 3; a++) {
                double ang = Math.random() * Math.PI * 2;
                double dist = Math.random() * radius;
                double x = mc.player.getX() + Math.cos(ang) * dist;
                double z = mc.player.getZ() + Math.sin(ang) * dist;
                double wy = findWaterTop(mc, x, z, mc.player.getBlockY());
                if (wy != Double.NEGATIVE_INFINITY) {
                    addRipple(x, wy, z, now);
                }
            }
        }

        updateMist(mc, now, radius);
    }

    public static void clear() {
        drops.clear();
        ripples.clear();
        splashes.clear();
        mists = null;
    }

    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((drops.isEmpty() && ripples.isEmpty() && splashes.isEmpty() && mists == null)
                || mc.player == null || mc.world == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        var camRot = mc.gameRenderer.getCamera().getRotation();

        Vector3f rv = camRot.transform(new Vector3f(1, 0, 0));
        float rx = rv.x, rz = rv.z;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
        } else {
            rx /= rl;
            rz /= rl;
        }

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();
        float pTicks = e.getTickDelta();
        float cullSq = 70f * 70f;
        long now = now();

        float fl = SkyLightningRenderer.flashLevel();
        int rainR = lerp(170, 240, fl), rainG = lerp(205, 248, fl);
        float alphaMul = 1f + fl * 1.2f;

        if (cfgMist && mists != null) {
            Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
            Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
            VertexConsumer mistBuf = consumers.getBuffer(MIST_LAYER);
            for (Mist m : mists) {
                double mx = mc.player.getX() + Math.cos(m.ang) * m.dist;
                double mz = mc.player.getZ() + Math.sin(m.ang) * m.dist;
                float px = (float) (mx - camPos.x);
                float pz = (float) (mz - camPos.z);
                if (px * px + pz * pz > cullSq * 2.25f || Double.isNaN(m.y)) continue;
                float py = (float) (m.y - camPos.y)
                        + 0.55f * (float) Math.sin(now * 0.00037 + m.seed * 9f);
                float half = 3.4f + 1.8f * (float) Math.sin(now * 0.00023 + m.seed * 6.28f);
                sprite(mistBuf, matrix, px, py, pz, right, up, half,
                        argb(208, 228, 255, (int) (13 + fl * 46)));
            }
            consumers.draw(MIST_LAYER);
        }

        VertexConsumer buf = consumers.getBuffer(RAIN_LAYER);

        for (Drop d : drops) {
            float px = (float) (d.x - camPos.x);

            double ry = d.prevY + (d.y - d.prevY) * pTicks;
            float py = (float) (ry - camPos.y);
            float pz = (float) (d.z - camPos.z);
            float distSq = px * px + py * py + pz * pz;
            if (distSq > cullSq || py < -8f) continue;

            float fogFade = 1f - distSq / cullSq;
            fogFade = 0.3f + 0.7f * fogFade * fogFade;

            float len = d.len * (1f + fl * 0.15f);
            float sx = cfgWind ? (float) (d.vx / d.speed) * len : 0f;
            float sz = cfgWind ? (float) (d.vz / d.speed) * len : 0f;
            float tx = px + sx, ty = py + len, tz = pz + sz;

            float wB = 0.020f;
            float wT = wB * 0.32f;
            int bottomCol = argb(rainR, rainG, 255, (int) (145 * fogFade * alphaMul));
            int topCol = argb(rainR, rainG, 255, (int) (18 * fogFade));

            vertex(buf, matrix, px - rx * wB, py, pz - rz * wB, bottomCol);
            vertex(buf, matrix, px + rx * wB, py, pz + rz * wB, bottomCol);
            vertex(buf, matrix, tx + rx * wT, ty, tz + rz * wT, topCol);
            vertex(buf, matrix, tx - rx * wT, ty, tz - rz * wT, topCol);
        }
        consumers.draw(RAIN_LAYER);

        VertexConsumer rippleBuf = consumers.getBuffer(RAIN_LAYER);
        for (Ripple r : ripples) {
            float t = (now - r.born) / (float) RIPPLE_LIFE;
            if (t >= 1f) continue;
            float ease = 1f - (1f - t) * (1f - t);
            float rad = 0.055f + 0.55f * ease;
            int col = argb(lerp(195, 235, fl), lerp(225, 246, fl), 255,
                    (int) ((1f - t) * (1f - t) * 140 * alphaMul));

            ringQuads(rippleBuf, matrix,
                    (float) (r.x - camPos.x),
                    (float) (r.y - camPos.y) + 0.03f,
                    (float) (r.z - camPos.z),
                    rad, 0.05f, col, 28);

            if (t > 0.2f) {
                float t2 = (t - 0.2f) / 0.8f;
                float rad2 = 0.04f + 0.32f * (1f - (1f - t2) * (1f - t2));
                ringQuads(rippleBuf, matrix,
                        (float) (r.x - camPos.x),
                        (float) (r.y - camPos.y) + 0.03f,
                        (float) (r.z - camPos.z),
                        rad2, 0.04f,
                        withAlpha(col, (int) ((1f - t) * (1f - t) * 84 * alphaMul)), 28);
            }
        }
        consumers.draw(RAIN_LAYER);

        if (cfgSplashes && !splashes.isEmpty()) {
            VertexConsumer splashBuf = consumers.getBuffer(RAIN_LAYER);
            for (Splash s : splashes) {
                float t = (now - s.born) / (float) SPLASH_LIFE;
                if (t >= 1f) continue;
                float cx = (float) (s.x - camPos.x);
                float cy = (float) (s.y - camPos.y) + 0.02f;
                float cz = (float) (s.z - camPos.z);
                float fade = (1f - t) * (1f - t);

                float rad = 0.07f + 0.26f * (1f - fade);
                ringQuads(splashBuf, matrix, cx, cy, cz, rad, 0.03f,
                        argb(rainR, rainG, 255, (int) (115 * fade * alphaMul)), 12);

                float h = 0.30f * (float) Math.sin(Math.min(1f, t * 1.2f) * Math.PI);
                if (h < 0.01f) continue;
                int spokes = 5;
                for (int k = 0; k < spokes; k++) {
                    double a = s.rot + Math.PI * 2.0 * k / spokes;
                    float ox = cx + (float) Math.cos(a) * rad * 0.72f;
                    float oz = cz + (float) Math.sin(a) * rad * 0.72f;
                    float w = 0.016f * (1f - t * 0.55f);
                    int col = argb(rainR, rainG, 255, (int) (130 * fade * alphaMul));
                    int tipCol = withAlpha(col, (int) (130 * fade * alphaMul * 0.15f));
                    vertex(splashBuf, matrix, ox - rx * w, cy, oz - rz * w, col);
                    vertex(splashBuf, matrix, ox + rx * w, cy, oz + rz * w, col);
                    vertex(splashBuf, matrix, ox + rx * w * 0.4f, cy + h, oz + rz * w * 0.4f, tipCol);
                    vertex(splashBuf, matrix, ox - rx * w * 0.4f, cy + h, oz - rz * w * 0.4f, tipCol);
                }
            }
            consumers.draw(RAIN_LAYER);
        }
    }

    private static void updateWind(long now) {
        float t = now * 0.001f;
        double sway = Math.sin(t * 0.11) * 0.38 + Math.sin(t * 0.043 + 1.7) * 0.62;
        double ang = windBaseAngle + sway;
        double gust = 0.72 + 0.28 * Math.sin(t * 0.23 + Math.sin(t * 0.071) * 2.0);
        double spd = (cfgWindStrength / 100.0) * 9.5 * gust;
        windX = (float) (Math.cos(ang) * spd);
        windZ = (float) (Math.sin(ang) * spd);
    }

    private static void addRipple(double x, double y, double z, long now) {
        if (ripples.size() < MAX_RIPPLES) {
            ripples.add(new Ripple(x, y, z, now));
        }
    }

    private static void addSplash(double x, double y, double z, long now) {
        if (splashes.size() < MAX_SPLASHES) {
            splashes.add(new Splash(x, y, z, now, (float) (Math.random() * Math.PI * 2)));
        }
    }

    private static void respawn(MinecraftClient mc, Drop d, float radius) {
        double ang = Math.random() * Math.PI * 2;
        double dist = (0.2 + Math.random() * 0.8) * radius;
        d.x = mc.player.getX() + Math.cos(ang) * dist;
        d.z = mc.player.getZ() + Math.sin(ang) * dist;
        d.speed = 12.0 + Math.random() * 7.0;
        d.len = 0.45f + (float) Math.random() * 0.35f;

        double jitter = (Math.random() - 0.5) * 0.18;
        double ca = Math.cos(jitter), sa = Math.sin(jitter);
        d.vx = windX * ca - windZ * sa;
        d.vz = windX * sa + windZ * ca;
        d.y = mc.player.getY() + 10.0 + Math.random() * 16.0;
        d.prevY = d.y;

        int py = mc.player.getBlockY();
        d.water = false;
        d.floorTop = py - 24;
        for (int y = py + 22; y >= py - 14; y--) {
            var state = mc.world.getBlockState(BlockPos.ofFloored(d.x, y, d.z));
            if (state.isAir()) continue;
            boolean water = !state.getFluidState().isEmpty()
                    && state.getFluidState().isIn(FluidTags.WATER);
            d.water = water;
            d.floorTop = y + (water ? 0.9 : 1.02);
            break;
        }
    }

    private static double findWaterTop(MinecraftClient mc, double x, double z, int playerY) {
        for (int y = playerY + 6; y >= playerY - 8; y--) {
            var state = mc.world.getBlockState(BlockPos.ofFloored(x, y, z));
            if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.WATER)) {
                return y + 0.9;
            }
            if (!state.isAir()) return Double.NEGATIVE_INFINITY;
        }
        return Double.NEGATIVE_INFINITY;
    }

    private static double findSurfaceTop(MinecraftClient mc, double x, double z, int playerY) {
        for (int y = playerY + 10; y >= playerY - 14; y--) {
            var state = mc.world.getBlockState(BlockPos.ofFloored(x, y, z));
            if (state.isAir() || state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN)) continue;
            boolean water = !state.getFluidState().isEmpty()
                    && state.getFluidState().isIn(FluidTags.WATER);
            return y + (water ? 0.95 : 1.02);
        }
        return Double.NaN;
    }

    private static void updateMist(MinecraftClient mc, long now, float radius) {
        if (!cfgMist) {
            mists = null;
            return;
        }
        if (mists == null) {
            mists = new Mist[MIST_COUNT];
            for (int i = 0; i < mists.length; i++) {
                Mist m = new Mist();
                m.ang = Math.random() * Math.PI * 2;
                m.dist = radius * (0.25 + Math.random() * 0.75);
                m.seed = (float) Math.random();
                m.nextSample = 0L;
                mists[i] = m;
            }
        }

        for (Mist m : mists) {
            m.ang += 0.0045;
            m.dist += Math.sin(now * 0.0003 + m.seed * 12f) * 0.002;
            m.dist = Math.max(radius * 0.15, Math.min(radius * 1.35, m.dist));
            if (Double.isNaN(m.y) || now >= m.nextSample) {
                m.y = findSurfaceTop(mc,
                        mc.player.getX() + Math.cos(m.ang) * m.dist,
                        mc.player.getZ() + Math.sin(m.ang) * m.dist,
                        mc.player.getBlockY());
                m.nextSample = now + (Double.isNaN(m.y) ? 400L : 1600L + (long) (Math.random() * 1200L));
            }
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int argb(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    private static void vertex(VertexConsumer buf, Matrix4f matrix, float x, float y, float z, int color) {
        buf.vertex(matrix, x, y, z).color(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f
        );
    }

    private static void sprite(VertexConsumer buf, Matrix4f matrix, float px, float py, float pz,
                               Vector3f right, Vector3f up, float half, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;
        float rx2 = right.x * half, ry = right.y * half, rz2 = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;

        buf.vertex(matrix, px - rx2 - ux, py - ry - uy, pz - rz2 - uz).texture(0f, 0f).color(r, g, b, a);
        buf.vertex(matrix, px - rx2 + ux, py - ry + uy, pz - rz2 + uz).texture(0f, 1f).color(r, g, b, a);
        buf.vertex(matrix, px + rx2 + ux, py + ry + uy, pz + rz2 + uz).texture(1f, 1f).color(r, g, b, a);
        buf.vertex(matrix, px + rx2 - ux, py + ry - uy, pz - rz2 - uz).texture(1f, 0f).color(r, g, b, a);
    }

    private static void ringQuads(VertexConsumer buf, Matrix4f matrix,
                                  float cx, float cy, float cz,
                                  float radius, float width, int color, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;
            vertex(buf, matrix, cx + (float) Math.cos(a0) * radius, cy, cz + (float) Math.sin(a0) * radius, color);
            vertex(buf, matrix, cx + (float) Math.cos(a1) * radius, cy, cz + (float) Math.sin(a1) * radius, color);
            vertex(buf, matrix, cx + (float) Math.cos(a1) * (radius - width), cy, cz + (float) Math.sin(a1) * (radius - width), color);
            vertex(buf, matrix, cx + (float) Math.cos(a0) * (radius - width), cy, cz + (float) Math.sin(a0) * (radius - width), color);
        }
    }
}
