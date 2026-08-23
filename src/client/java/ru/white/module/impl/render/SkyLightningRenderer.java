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
    private static final long EMBER_LIFE = 2400L;
    private static final float FLASH_TIME = 450F;

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
        /** Время «бега» лидера от облака до земли. */
        long propagateMs;
        final int[] branchAttach = new int[4];
        int branchCount;
    }

    private static final List<Strike> strikes = new ArrayList<>();
    private static long nextStrikeAt;
    private static final Random random = new Random();

    // переиспользуемые векторы базиса камеры (без аллокаций в кадре)
    private static final Vector3f CAM_RIGHT = new Vector3f();
    private static final Vector3f CAM_UP = new Vector3f();

    private SkyLightningRenderer() {
    }

    /** Вызывается из WorldTweaks на тике: чистит умершие разряды и спавнит новые. */
    public static void update(boolean enabled, float intervalSec, float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        strikes.removeIf(s -> now - s.born > STRIKE_LIFE + EMBER_LIFE);

        if (!enabled || mc.player == null || mc.world == null) return;
        if (now < nextStrikeAt) return;

        nextStrikeAt = now + (long) (Math.max(0.3f, intervalSec) * 1000f * (0.7f + random.nextFloat() * 0.6f));
        spawn(radius);
        if (random.nextFloat() < 0.22f) spawn(radius); // иногда сдвоенный разряд
    }

    public static void clear() {
        strikes.clear();
    }

    /**
     * Текущий уровень засветки мира от недавних разрядов (0..1).
     * Используется дождём (подсветка капель) и WorldTweaks (вспышка тумана).
     */
    public static float flashLevel() {
        long now = System.currentTimeMillis();
        float f = 0f;
        for (Strike s : strikes) {
            long ms = now - s.born - s.propagateMs;
            if (ms < 0 || ms > FLASH_TIME + 420L) continue;
            double e = Math.exp(-ms / 150.0)
                    + 0.75 * Math.exp(-sq((ms - 280.0) / 80.0))
                    + 0.5 * Math.exp(-sq((ms - 470.0) / 70.0));
            f += (float) Math.min(1.2, e);
        }
        return Math.min(1f, f);
    }

    private static void spawn(float radius) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // спавним в конусе перед камерой, а не за спиной:
        // forward для yaw θ — это угол θ+90° в параметризации (cos a, sin a)
        float yaw = mc.gameRenderer.getCamera().getYaw();
        double baseAng = Math.toRadians(yaw) + Math.PI * 0.5;
        double spread = Math.toRadians(110.0); // ±55° от направления взгляда
        double ang = baseAng + (random.nextDouble() - 0.5) * spread;

        double dist = radius * (0.4f + 0.6f * random.nextFloat());
        double x = mc.player.getX() + Math.cos(ang) * dist;
        double z = mc.player.getZ() + Math.sin(ang) * dist;

        int groundY = findGround((int) Math.floor(x), (int) Math.floor(z),
                mc.player.getBlockY());
        if (groundY == Integer.MIN_VALUE) return;

        Vec3d impact = new Vec3d(x, groundY + 1.01, z);
        double skyH = 55 + random.nextInt(30); // разряд идёт от самых облаков
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
        s.propagateMs = 130 + random.nextInt(60);
        s.pts.addAll(LightningPath.generate(start, end, 3, skyH * 0.09, random));

        // канал не должен нырять под землю
        float minY = (float) impact.y - 0.05f;
        for (int i = 0; i < s.pts.size(); i++) {
            Vec3d p = s.pts.get(i);
            if (p.y < minY) s.pts.set(i, new Vec3d(p.x, minY, p.z));
        }

        // ветвления от случайных точек основного канала
        for (int b = 0; b < 3; b++) {
            if (s.pts.size() < 6 || s.branchCount >= 4) break;
            int idx = 2 + random.nextInt(Math.max(1, s.pts.size() - 4));
            Vec3d from = s.pts.get(idx);
            double blen = 2.5 + random.nextDouble() * 3.5;
            double ba = random.nextDouble() * Math.PI * 2;
            Vec3d bend = new Vec3d(
                    from.x + Math.cos(ba) * blen,
                    from.y - blen * (0.7 + random.nextDouble() * 0.6),
                    from.z + Math.sin(ba) * blen);
            s.branchAttach[s.branchCount++] = idx;
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
        var camRot = mc.gameRenderer.getCamera().getRotation();
        Vector3f right = camRot.transform(CAM_RIGHT.set(1, 0, 0));
        Vector3f up = camRot.transform(CAM_UP.set(0, 1, 0));

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();

        // ── Pass 1: свечение вдоль канала и вспышка удара ──
        VertexConsumer glowBuf = consumers.getBuffer(GLOW_LAYER);
        for (Strike s : strikes) {
            long age = now - s.born;
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));

            // тлеющие угли в точке удара — канал «дышит» ещё пару секунд после разряда
            float tE = (age - s.propagateMs) / (float) EMBER_LIFE;
            if (tE >= 0f && tE < 1f) {
                float eA = (1f - tE) * (1f - tE) * flicker((long) (now * 0.6f), s.seed);
                sprite(glowBuf, matrix, s.impact.add(0, 0.35 + 0.85 * tE, 0), camPos, right, up,
                        0.75f + 0.85f * tE, argb(190, 218, 255, (int) (eA * 90)));
            }

            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int glowCol = argb(150, 195, 255, (int) (Math.min(1f, env) * flicker * 90));

            // ступенчатый лидер: канал проявляется сверху вниз
            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);

            // вспышка в «облаках» у старта канала
            float head = 1f - Math.min(1f, age / 240f);
            if (head > 0.01f) {
                sprite(glowBuf, matrix, s.pts.get(0), camPos, right, up,
                        7.5f + 3.5f * (1f - head),
                        argb(205, 228, 255, (int) (head * env * 170)));
                // широкая засветка облачной базы — небо «включается» на мгновение
                sprite(glowBuf, matrix, s.pts.get(0), camPos, right, up,
                        21f + 8f * (1f - head),
                        argb(185, 212, 255, (int) (head * env * 78)));
            }

            for (int i = 0; i < mainLim; i += 2) {
                sprite(glowBuf, matrix, s.pts.get(i), camPos, right, up,
                        1.0f + 0.7f * flicker, glowCol);
            }
            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = (age - s.propagateMs * s.branchAttach[bi] / (float) Math.max(1, s.pts.size() - 1))
                        / (float) Math.max(1L, s.propagateMs);
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                int lim = Math.max(2, (int) Math.ceil(Math.min(1f, bp) * (br.size() - 1)) + 1);
                for (int i = 0; i < lim; i += 2) {
                    sprite(glowBuf, matrix, br.get(i), camPos, right, up,
                            0.6f + 0.45f * flicker, glowCol);
                }
            }

            // удар и вспышка в точке падения — когда лидер дошёл до земли
            if (prog >= 1f) {
                float flash = flashEnv(age - s.propagateMs);
                if (flash > 0.01f) {
                    sprite(glowBuf, matrix, s.impact.add(0, 0.8, 0), camPos, right, up,
                            3.4f + 2.8f * (1f - flash), argb(200, 225, 255, (int) (flash * env * 200)));
                }
            }
        }
        consumers.draw(GLOW_LAYER);

        // ── Pass 2: внешняя лента канала ──
        VertexConsumer outerBuf = consumers.getBuffer(OUTER_LAYER);
        for (Strike s : strikes) {
            long age = now - s.born;
            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int col = argb(120, 175, 255, (int) (Math.min(1f, env) * flicker * 130));
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));
            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);
            ribbonPolyline(outerBuf, matrix, s.pts, camPos, 0.17f, col, mainLim);
            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = Math.min(1f, (age - s.propagateMs * s.branchAttach[bi]
                        / (float) Math.max(1, s.pts.size() - 1)) / (float) Math.max(1L, s.propagateMs));
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                ribbonPolyline(outerBuf, matrix, br, camPos, 0.115f, col,
                        Math.max(2, (int) Math.ceil(bp * (br.size() - 1)) + 1));
            }
        }
        consumers.draw(OUTER_LAYER);

        // ── Pass 3: яркое ядро + кольцо вспышки на земле ──
        VertexConsumer coreBuf = consumers.getBuffer(CORE_LAYER);
        for (Strike s : strikes) {
            long age = now - s.born;
            float env = envelope(age);
            if (env <= 0.01f) continue;
            float flicker = flicker(now, s.seed);
            int core = argb(235, 245, 255, (int) (Math.min(1f, env) * flicker * 235));
            float prog = Math.min(1f, age / (float) Math.max(1L, s.propagateMs));
            int mainLim = Math.max(2, (int) Math.ceil(prog * (s.pts.size() - 1)) + 1);
            ribbonPolyline(coreBuf, matrix, s.pts, camPos, 0.072f, core, mainLim);
            for (int bi = 0; bi < s.branches.size(); bi++) {
                float bp = Math.min(1f, (age - s.propagateMs * s.branchAttach[bi]
                        / (float) Math.max(1, s.pts.size() - 1)) / (float) Math.max(1L, s.propagateMs));
                if (bp <= 0f) continue;
                List<Vec3d> br = s.branches.get(bi);
                ribbonPolyline(coreBuf, matrix, br, camPos, 0.046f, core,
                        Math.max(2, (int) Math.ceil(bp * (br.size() - 1)) + 1));
            }

            // кольцо-волна от удара — после прихода лидера на землю
            if (prog >= 1f) {
                float tR = (age - s.propagateMs) / FLASH_TIME;
                if (tR >= 0f && tR < 1f) {
                    float ringR = 0.5f + 4.2f * (1f - (1f - tR) * (1f - tR));
                    int ringCol = argb(190, 220, 255, (int) ((1f - tR) * Math.min(1f, env) * 150));
                    groundRing(coreBuf, matrix, s.impact, ringR, 0.24f, ringCol);
                }
            }
        }
        consumers.draw(CORE_LAYER);
    }

    /**
     * Реалистичная огибающая: основной разряд + два повторных импульса
     * по тому же каналу (настоящие молнии бьют 2–3 раза подряд).
     */
    private static float envelope(long ageMs) {
        if (ageMs < 0 || ageMs >= STRIKE_LIFE) return 0f;
        double e = Math.exp(-ageMs / 260.0)
                + 0.85 * Math.exp(-sq((ageMs - 290.0) / 95.0))
                + 0.6 * Math.exp(-sq((ageMs - 480.0) / 85.0));
        float t = ageMs / (float) STRIKE_LIFE;
        float endFade = t > 0.82f ? (1f - t) / 0.18f : 1f;
        return (float) Math.min(1.25, e) * endFade;
    }

    private static double sq(double v) {
        return v * v;
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
        ribbonPolyline(buf, matrix, pts, camPos, halfW, color, pts.size());
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
