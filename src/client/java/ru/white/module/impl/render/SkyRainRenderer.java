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

/**
 * Шейдерный дождь для World Tweaks: светящиеся струи-капли вместо ванильных,
 * плюс расходящиеся круги на воде от падающих капель.
 * Ванильный рендер осадков отменяется миксином {@code WeatherRenderingMixin}.
 */
public final class SkyRainRenderer {

    private static final int MAX_RIPPLES = 90;
    private static final long RIPPLE_LIFE = 650L;

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

    private static class Drop {
        double x, y, z;
        double speed = 13.0;
        double floorTop;
        float len = 0.6f;
        float slantX, slantZ;
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

    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Ripple> ripples = new ArrayList<>();
    private static long nextAmbientRipple;

    private SkyRainRenderer() {
    }

    /** Вызывается из WorldTweaks на тике. */
    public static void update(boolean enabled, int count, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.world == null) {
            if (!drops.isEmpty()) drops.clear();
            if (!ripples.isEmpty()) ripples.clear();
            return;
        }

        // синхронизация пула капель
        while (drops.size() < count) {
            Drop d = new Drop();
            respawn(mc, d, radius);
            d.y = mc.player.getY() + 8.0 + Math.random() * 18.0;
            drops.add(d);
        }
        while (drops.size() > count) {
            drops.remove(drops.size() - 1);
        }

        long now = System.currentTimeMillis();

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.y -= d.speed * 0.05; // тик 50 мс

            double dx = d.x - mc.player.getX();
            double dz = d.z - mc.player.getZ();
            double maxDist = radius * 1.6;
            if (dx * dx + dz * dz > maxDist * maxDist || d.y <= d.floorTop) {
                if (d.y <= d.floorTop && d.water) {
                    addRipple(d.x, d.floorTop, d.z, now);
                }
                if (drops.size() > count) {
                    it.remove();
                } else {
                    respawn(mc, d, radius);
                }
            }
        }

        ripples.removeIf(r -> now - r.born > RIPPLE_LIFE);

        // фоновые круги на ближайшей воде — дождь «идёт везде»
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
    }

    public static void clear() {
        drops.clear();
        ripples.clear();
    }

    /** Рендер: светящиеся струи + круги на воде. Слои строго последовательные. */
    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((drops.isEmpty() && ripples.isEmpty()) || mc.player == null || mc.world == null) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Vector3f rv = mc.gameRenderer.getCamera().getRotation().transform(new Vector3f(1, 0, 0));
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
        float cullSq = 70f * 70f;

        // ── Pass 1: струи дождя ──
        VertexConsumer buf = consumers.getBuffer(RAIN_LAYER);
        int bottomCol = argb(170, 205, 255, 110);
        int topCol = argb(170, 205, 255, 16);

        for (Drop d : drops) {
            float px = (float) (d.x - camPos.x);
            float py = (float) (d.y - camPos.y);
            float pz = (float) (d.z - camPos.z);
            if (px * px + pz * pz > cullSq) continue;

            float tx = px + d.slantX * d.len;
            float ty = py + d.len;
            float tz = pz + d.slantZ * d.len;
            float wx = rx * 0.016f;
            float wz = rz * 0.016f;

            vertex(buf, matrix, px - wx, py, pz - wz, bottomCol);
            vertex(buf, matrix, px + wx, py, pz + wz, bottomCol);
            vertex(buf, matrix, tx + wx, ty, tz + wz, topCol);
            vertex(buf, matrix, tx - wx, ty, tz - wz, topCol);
        }
        consumers.draw(RAIN_LAYER);

        // ── Pass 2: круги на воде ──
        VertexConsumer rippleBuf = consumers.getBuffer(RAIN_LAYER);
        long now = System.currentTimeMillis();
        for (Ripple r : ripples) {
            float t = (now - r.born) / (float) RIPPLE_LIFE;
            if (t >= 1f) continue;
            float ease = 1f - (1f - t) * (1f - t);
            float rad = 0.055f + 0.55f * ease;
            int alpha = (int) ((1f - t) * (1f - t) * 140);
            int col = argb(195, 225, 255, alpha);

            ringQuads(rippleBuf, matrix,
                    (float) (r.x - camPos.x),
                    (float) (r.y - camPos.y) + 0.03f,
                    (float) (r.z - camPos.z),
                    rad, 0.05f, col);

            // второе кольцо появляется чуть позже — красивая рябь
            if (t > 0.2f) {
                float t2 = (t - 0.2f) / 0.8f;
                float rad2 = 0.04f + 0.32f * (1f - (1f - t2) * (1f - t2));
                ringQuads(rippleBuf, matrix,
                        (float) (r.x - camPos.x),
                        (float) (r.y - camPos.y) + 0.03f,
                        (float) (r.z - camPos.z),
                        rad2, 0.04f, argb(195, 225, 255, (int) (alpha * 0.6f)));
            }
        }
        consumers.draw(RAIN_LAYER);
    }

    private static void addRipple(double x, double y, double z, long now) {
        if (ripples.size() < MAX_RIPPLES) {
            ripples.add(new Ripple(x, y, z, now));
        }
    }

    /** Переносит каплю в новую точку около игрока и пересчитывает ей уровень пола/воды. */
    private static void respawn(MinecraftClient mc, Drop d, float radius) {
        double ang = Math.random() * Math.PI * 2;
        double dist = (0.2 + Math.random() * 0.8) * radius;
        d.x = mc.player.getX() + Math.cos(ang) * dist;
        d.z = mc.player.getZ() + Math.sin(ang) * dist;
        d.speed = 12.0 + Math.random() * 7.0;
        d.len = 0.45f + (float) Math.random() * 0.35f;
        d.slantX = (float) (Math.random() - 0.5) * 0.22f;
        d.slantZ = (float) (Math.random() - 0.5) * 0.22f;
        d.y = mc.player.getY() + 10.0 + Math.random() * 16.0;

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

    /** Верхний уровень воды в колонке рядом с игроком, иначе -inf. */
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

    /** Плоское кольцо-рябь на поверхности воды. */
    private static void ringQuads(VertexConsumer buf, Matrix4f matrix,
                                  float cx, float cy, float cz,
                                  float radius, float width, int color) {
        int segs = 28;
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
