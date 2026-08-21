package ru.white.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import ru.white.utils.annotation.IMinecraft;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

/**
 * Электрические разряды вокруг кисти от первого лица.
 *
 * Структура повторяет {@link ru.white.module.impl.render.LightningRenderer}
 * (пул разрядов + двухфазный рендер), но с двумя принципиальными отличиями:
 *
 * <ol>
 *   <li>Точки разряда хранятся НЕ в мировых координатах, а в локальном базисе руки
 *       (right / up / forward). Каждый кадр они пересчитываются от текущего анкера,
 *       поэтому разряды приклеены к руке и не отстают при движении и повороте камеры.</li>
 *   <li>Свои пайплайны с уникальными Identifier — переиспользовать пайплайны
 *       LightningRenderer нельзя, повторная регистрация того же location конфликтует.</li>
 * </ol>
 *
 * Экземпляр держит состояние одной руки, поэтому на две руки нужно два экземпляра.
 */
public class HandLightningRenderer implements IMinecraft {

    private static final int SPAWN_PER_FRAME = 2;
    private static final int LIGHTNING_DEPTH = 3;
    private static final int MAX_BOLTS = 64;

    private static final Identifier GLOW = Identifier.of("client", "textures/visuals/particles_2.png");

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/hand_lightning_glow")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final Function<Identifier, RenderLayer> GLOW_LAYER = Util.memoize(texture -> {
        RenderSetup setup = RenderSetup.builder(GLOW_PIPELINE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1536)
                .build();
        return RenderLayer.of("hand_lightning_glow", setup);
    });

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "hand_lightning_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of("hand_lightning_line",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1 << 13).build());

    private final Random random = new Random();
    private final Bolt[] bolts = new Bolt[MAX_BOLTS];
    private int boltCount = 0;
    private long lastSpawn = 0;

    /** Сбросить все разряды (смена мира, выключение эффекта, пустая рука). */
    public void reset() {
        boltCount = 0;
    }

    /**
     * @param anchor     позиция кисти в мире
     * @param right      единичный вектор «вправо» от камеры
     * @param up         единичный вектор «вверх» от камеры
     * @param forward    единичный вектор взгляда
     * @param radius     радиус, на котором зарождаются разряды
     * @param boltLength базовая длина разряда
     * @param rgb        цвет без альфы
     * @param intensity  0..1 — общая видимость (альфа настройки × реакция на свинг)
     */
    public void render(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                       Vec3d anchor, Vec3d right, Vec3d up, Vec3d forward,
                       float radius, float boltLength, int rgb, float intensity,
                       int maxBolts, long spawnIntervalMs) {

        if (intensity <= 0.01f) {
            reset();
            return;
        }
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        int cap = Math.min(maxBolts, MAX_BOLTS);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();

        long now = System.currentTimeMillis();
        if (now - lastSpawn > spawnIntervalMs) {
            for (int s = 0; s < SPAWN_PER_FRAME && boltCount < cap; s++) {
                bolts[boltCount++] = spawnBolt(radius, boltLength);
            }
            lastSpawn = now;
        }
        // настройку количества могли уменьшить — лишние разряды убираем сразу
        if (boltCount > cap) boltCount = cap;

        // чистка истёкших + предрасчёт цвета/альфы на кадр
        int visible = 0;
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (now - bolt.spawnTime > bolt.lifetimeMs) {
                bolts[i] = bolts[--boltCount];
                i--;
                continue;
            }

            float life = (now - bolt.spawnTime) / (float) bolt.lifetimeMs;
            float fade = 1.0f - life;
            float flicker = 0.65f + random.nextFloat() * 0.35f;
            float boltAlpha = intensity * fade * flicker;
            bolt.drawAlpha = boltAlpha;
            if (boltAlpha <= 0.02f) continue;
            visible++;

            bolt.glowColor = withAlpha(rgb, (int) (boltAlpha * 150));
            bolt.coreColor = withAlpha(rgb, (int) (boltAlpha * 255));
        }
        if (visible == 0) return;

        // ВАЖНО: у Immediate вызов getBuffer() для нового слоя сбрасывает текущий,
        // поэтому свечение и линии нельзя чередовать — только двумя фазами.

        // ФАЗА 1: свечение — билборд в каждой точке разряда
        VertexConsumer glowBuf = immediate.getBuffer(GLOW_LAYER.apply(GLOW));
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            for (Vec3d local : bolt.localPoints) {
                Vec3d world = toWorld(anchor, right, up, forward, local);
                float h = (radius * (0.45f + random.nextFloat() * 0.30f)) / 2f;

                matrices.push();
                matrices.translate(world.x - cameraPos.x, world.y - cameraPos.y, world.z - cameraPos.z);
                matrices.multiply(cameraRotation);
                Matrix4f m = matrices.peek().getPositionMatrix();

                glowBuf.vertex(m, -h, -h, 0.0f).color(bolt.glowColor).texture(0.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h, -h, 0.0f).color(bolt.glowColor).texture(1.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h,  h, 0.0f).color(bolt.glowColor).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, -h,  h, 0.0f).color(bolt.glowColor).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);

                matrices.pop();
            }
        }

        // ФАЗА 2: ядро разряда — линии в локальных координатах относительно анкера
        VertexConsumer lineBuf = immediate.getBuffer(LINE_LAYER);
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            matrices.push();
            matrices.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
            Matrix4f lm = matrices.peek().getPositionMatrix();

            List<Vec3d> points = bolt.localPoints;
            for (int s = 0; s < points.size() - 1; s++) {
                Vec3d a = offset(right, up, forward, points.get(s));
                Vec3d b = offset(right, up, forward, points.get(s + 1));
                lineBuf.vertex(lm, (float) a.x, (float) a.y, (float) a.z).color(bolt.coreColor);
                lineBuf.vertex(lm, (float) b.x, (float) b.y, (float) b.z).color(bolt.coreColor);
            }

            matrices.pop();
        }
    }

    /** Локальные коэффициенты (right, up, forward) -> смещение в мировых осях. */
    private Vec3d offset(Vec3d right, Vec3d up, Vec3d forward, Vec3d local) {
        return right.multiply(local.x).add(up.multiply(local.y)).add(forward.multiply(local.z));
    }

    private Vec3d toWorld(Vec3d anchor, Vec3d right, Vec3d up, Vec3d forward, Vec3d local) {
        return anchor.add(offset(right, up, forward, local));
    }

    /**
     * Разряд зарождается на сфере радиуса {@code radius} вокруг кисти и бьёт наружу.
     * Всё — в локальном базисе руки, поэтому от положения игрока не зависит.
     */
    private Bolt spawnBolt(float radius, float boltLength) {
        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);

        double sx = Math.sin(phi) * Math.cos(theta);
        double sy = Math.cos(phi);
        double sz = Math.sin(phi) * Math.sin(theta);

        Vec3d start = new Vec3d(sx, sy, sz).multiply(radius);

        // направление наружу с разбросом, чтобы разряды не были строго радиальными
        Vec3d outward = new Vec3d(sx, sy, sz);
        Vec3d scatter = LightningPath.randomPerpendicular(outward, random).multiply(0.6);
        Vec3d dir = outward.add(scatter).normalize();

        double spikeLength = boltLength * (0.7 + random.nextDouble() * 0.6);
        Vec3d end = start.add(dir.multiply(spikeLength));

        Bolt bolt = new Bolt();
        bolt.localPoints = LightningPath.generate(start, end, LIGHTNING_DEPTH, spikeLength * 0.4, random);
        bolt.spawnTime = System.currentTimeMillis();
        bolt.lifetimeMs = 110 + random.nextInt(130);
        return bolt;
    }

    private int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    private static class Bolt {
        /** Точки в базисе руки: x=right, y=up, z=forward. */
        List<Vec3d> localPoints;
        long spawnTime;
        long lifetimeMs;
        /** Пересчитывается каждый кадр в render(). */
        float drawAlpha = 0f;
        int glowColor;
        int coreColor;
    }
}
