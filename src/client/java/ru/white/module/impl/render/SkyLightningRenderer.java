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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.utils.render.LightningPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Атмосферные молнии для World Tweaks: периодические разряды с неба вокруг игрока.
 * Каждый разряд — рваный канал из {@link LightningPath} с ветвлениями, рисуется
 * тремя последовательными слоями: свечение-биллборды, внешняя лента, яркое ядро,
 * плюс расширяющееся кольцо вспышки в точке удара.
 */
public final class SkyLightningRenderer {

    private static final Identifier GLOW_TEX =
            Identifier.of("client", "textures/particles/glow.png");
    private static final long STRIKE_LIFE = 950L;
    private static final float FLASH_TIME = 450F;

    private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/skystorm_color"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
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
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer OUTER_LAYER = RenderLayer.of("skystorm_outer",
            RenderSetup.builder(COLOR_PIPELINE).translucent().expectedBufferSize(1 << 15).build());
    private static final RenderLayer CORE_LAYER = RenderLayer.of("skystorm_core",
            RenderSetup.builder(COLOR_PIPELINE).translucent().expectedBufferSize(1 << 15).build());
    private static final RenderLayer GLOW_LAYER = RenderLayer.of("skystorm_glow",
            RenderSetup.builder(GLOW_PIPELINE)
                    .texture("Sampler0", GLOW_TEX)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build());

    private static class Strike {
        List<Vec3d> pts = new ArrayList<>();
        List<List<Vec3d>> branches = new ArrayList<>();
        Vec3d impact;
        long born;
        float seed;
    }

    private static final List<Strike> strikes = new ArrayList<>();
    private static long nextStrikeAt;
    private static final Random random = new Random();

    private SkyLightningRenderer() {
    }

    /** Вызывается из WorldTweaks на тике: чистит умершие разряды и спавнит новые. */
    public static void update(boolean enabled, float intervalSec, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        strikes.removeIf(s -> now - s.born > STRIKE_LIFE);

        if (!enabled || mc.player == null || mc.world == null) return;
        if (now < nextStrikeAt) return;

        nextStrikeAt = now + (long) (Math.max(0.3f, intervalSec) * 1000f * (0.7f + random.nextFloat() * 0.6f));
        spawn(radius);
        if (random.nextFloat() < 0.22f) spawn(radius); // иногда сдвоенный разряд
    }

    public static void clear() {
        strikes.clear();
    }

    private static void spawn(float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();

        double ang = random.nextDouble() * Math.PI * 2;
        double dist = radius * (0.35f + 0.65f * random.nextFloat());
        double x = mc.player.getX() + Math.cos(ang) * dist;
        double z = mc.player.getZ() + Math.sin(ang) * dist;

        int groundY = findGround((int) Math.floor(x), (int) Math.floor(z),
                mc.player.getBlockY());
        if (groundY == Integer.MIN_VALUE) return;

        Vec3d impact = new Vec3d(x, groundY + 1.01, z);
        double skyH = 24 + random.nextInt(12);
        Vec3d start = new Vec3d(
                x + (random.nextDouble() - 0.5) * 10.0,
                impact.y + skyH,
                z + (random.nextDouble() - 0.5) * 10.0);
        Vec3d end = new Vec3d(
                x + (random.nextDouble() - 0.5) * 1.6,
                impact.y,
                z + (random.nextDouble() - 0.5) * 1.6);

        Strike s = new Strike();
        s.born = System.currentTimeMillis();
        s.seed = random.nextFloat();
        s.impact = impact;
        s.pts.addAll(LightningPath.generate(start, end, 3, skyH * 0.09, random));

        // ветвления от случайных точек основного канала
        for (int b = 0; b < 3; b++) {
            if (s.pts.size() < 6) break;
            int idx = 2 + random.nextInt(Math.max(1, s.pts.size() - 4));
            Vec3d from = s.pts.get(idx);
            double blen = 2.5 + random.nextDouble() * 3.5;
            double ba = random.nextDouble() * Math.PI * 2;
            Vec3d bend = new Vec3d(
                    from.x + Math.cos(ba) * blen,
                    from.y - blen * (0.7 + random.nextDouble() * 0.6),
                    from.z + Math.sin(ba) * blen);
            s.branches.add(LightningPath.generate(from, bend, 2, blen * 0.35, random));
        }

        strikes.add(s);
    }

    /** Ищет поверхность земли сверху вниз; не нашли — MIN_VALUE (разряд отменяется). */
    private static int findGround(int bx, int bz, int playerY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int top = playerY + 40;
        int bottom = playerY - 28;
        for (int y = top; y >= bottom; y--) {
            var state = mc.world.getBlockState(BlockPos.ofFloored(bx, y, bz));
            if (!state.isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Рендер активных разрядов. Слои идут строго последовательно. */
    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (strikes.isEmpty() || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        Vector3f right = mc.gameRenderer.getCamera().getRotation().transform(new Vector3f(1, 0, 0));
        Vector3f up = mc.gameRenderer.getCamera().getRotation().transform(new Vector3f(0, 1, 0));

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();

        // ── Pass 1: свечение вдоль канала и вспышка удара ──
        VertexConsumer glowBuf = consumers.getBuffer(GLOW_LAYER);
        for (Strike s : strikes) {
            float env = envelope(now - s.born);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int glowCol = argb(150, 195, 255, (int) (env * flicker * 90));

            for (int i = 0; i < s.pts.size(); i += 2) {
                sprite(glowBuf, matrix, s.pts.get(i), camPos, right, up,
                        0.55f + 0.35f * flicker, glowCol);
            }
            for (List<Vec3d> br : s.branches) {
                for (int i = 0; i < br.size(); i += 2) {
                    sprite(glowBuf, matrix, br.get(i), camPos, right, up,
                            0.35f + 0.25f * flicker, glowCol);
                }
            }

            // вспышка в точке удара
            float flash = flashEnv(now - s.born);
            if (flash > 0.01f) {
                sprite(glowBuf, matrix, s.impact.add(0, 0.8, 0), camPos, right, up,
                        2.6f + 2.2f * (1f - flash), argb(200, 225, 255, (int) (flash * env * 200)));
            }
        }
        consumers.draw(GLOW_LAYER);

        // ── Pass 2: внешняя лента канала ──
        VertexConsumer outerBuf = consumers.getBuffer(OUTER_LAYER);
        for (Strike s : strikes) {
            float env = envelope(now - s.born);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int col = argb(120, 175, 255, (int) (env * flicker * 130));
            ribbonPolyline(outerBuf, matrix, s.pts, camPos, 0.085f, col);
            for (List<Vec3d> br : s.branches) {
                ribbonPolyline(outerBuf, matrix, br, camPos, 0.055f, col);
            }
        }
        consumers.draw(OUTER_LAYER);

        // ── Pass 3: яркое ядро + кольцо вспышки на земле ──
        VertexConsumer coreBuf = consumers.getBuffer(CORE_LAYER);
        for (Strike s : strikes) {
            float env = envelope(now - s.born);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int core = argb(235, 245, 255, (int) (env * flicker * 235));
            ribbonPolyline(coreBuf, matrix, s.pts, camPos, 0.034f, core);
            for (List<Vec3d> br : s.branches) {
                ribbonPolyline(coreBuf, matrix, br, camPos, 0.02f, core);
            }

            // кольцо-волна от удара
            float tR = (now - s.born) / FLASH_TIME;
            if (tR < 1f) {
                float ringR = 0.4f + 3.2f * (1f - (1f - tR) * (1f - tR));
                int ringCol = argb(190, 220, 255, (int) ((1f - tR) * env * 150));
                groundRing(coreBuf, matrix, s.impact, ringR, 0.14f, ringCol);
            }
        }
        consumers.draw(CORE_LAYER);
    }

    private static float envelope(long ageMs) {
        float t = ageMs / (float) STRIKE_LIFE;
        if (t >= 1f) return 0f;
        if (t < 0.06f) return t / 0.06f;
        if (t > 0.62f) return 1f - (t - 0.62f) / 0.38f;
        return 1f;
    }

    private static float flashEnv(long ageMs) {
        return Math.max(0f, 1f - ageMs / FLASH_TIME);
    }

    private static float flicker(long now, float seed) {
        return 0.72f + 0.28f * (float) Math.sin(now * 0.11 + seed * 97.0);
    }

    private static int argb(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Билборд-спрайт свечения в точке pos. */
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

    /** Лента-полилиния, повёрнутая к камере (перпендикуляр = dir × toCam). */
    private static void ribbonPolyline(VertexConsumer buf, Matrix4f matrix,
                                       List<Vec3d> pts, Vec3d camPos, float halfW, int color) {
        for (int i = 0; i < pts.size() - 1; i++) {
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

    /** Плоское кольцо на земле в точке удара. */
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
