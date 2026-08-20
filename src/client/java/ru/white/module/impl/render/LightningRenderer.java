package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventRender3D;
import ru.white.utils.annotation.IMinecraft;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

/**
 * Рендерер молний вокруг таргета. Адаптирован под пайплайны Nightix
 * (текстурное свечение как у ROMB_ESP + 3D-линии как у кольца).
 * Матричные преобразования повторяют родной код проекта (translate(мир-камера) + локальные
 * вершины для линий; multiply(camera.getRotation()) для билборда свечения), чтобы не было
 * "полос" из-за рассинхрона матричного стека.
 */
public class LightningRenderer implements IMinecraft {

    private static final int SPAWN_PER_TICK = 2;
    private static final int LIGHTNING_DEPTH = 3;

    private static final int WHITE = 0xFFFFFF;
    private static final int RED = 0xFF3B30;
    private static final long HIT_FLASH_DURATION_MS = 350;

    private static final Identifier GLOW = Identifier.of("client", "textures/visuals/particles_2.png");

    public boolean redOnHit = true;

    /** Настраивается из TargetEsp / AttackAura (слайдеры). */
    public int maxBolts = 16;
    public long spawnIntervalMs = 42;

    private final Random random = new Random();
    private final Bolt[] bolts = new Bolt[64];
    private int boltCount = 0;
    private long lastSpawn = 0;
    private long lastHitTime = -HIT_FLASH_DURATION_MS;
    private int lastHurtTime = 0;

    // ── пайплайны рендера (регистрируются один раз) ──

    private static final RenderPipeline LIGHTNING_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/lightning_glow")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final Function<Identifier, RenderLayer> LIGHTNING_GLOW = Util.memoize(texture -> {
        RenderSetup setup = RenderSetup.builder(LIGHTNING_GLOW_PIPELINE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1536)
                .build();
        return RenderLayer.of("lightning_glow", setup);
    });

    private static final RenderPipeline LIGHTNING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "lightning_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LIGHTNING_LINE_LAYER = RenderLayer.of("lightning_line",
            RenderSetup.builder(LIGHTNING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());

    public void notifyHit() {
        lastHitTime = System.currentTimeMillis();
    }

    public void render(EventRender3D e, VertexConsumerProvider.Immediate immediate, LivingEntity target, float anim) {
        if (target == null || anim <= 0.01f) {
            boltCount = 0;
            return;
        }

        // авто-вспышка красным при получении урона (если включено redOnHit)
        if (target.hurtTime > lastHurtTime) {
            notifyHit();
        }
        lastHurtTime = target.hurtTime;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d basePos = target.getLerpedPos(e.getTickDelta());
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();

        long now = System.currentTimeMillis();
        if (now - lastSpawn > spawnIntervalMs && boltCount < maxBolts) {
            for (int s = 0; s < SPAWN_PER_TICK && boltCount < maxBolts; s++) {
                bolts[boltCount++] = spawnBolt(target, basePos);
            }
            lastSpawn = now;
        }

        float hitT = 0f;
        if (redOnHit) {
            long sinceHit = now - lastHitTime;
            if (sinceHit < HIT_FLASH_DURATION_MS) {
                hitT = 1.0f - (sinceHit / (float) HIT_FLASH_DURATION_MS);
            }
        }

        int customColor = WHITE;

        VertexConsumer glowBuf = immediate.getBuffer(LIGHTNING_GLOW.apply(GLOW));
        VertexConsumer lineBuf = immediate.getBuffer(LIGHTNING_LINE_LAYER);

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
            float boltAlpha = anim * fade * flicker;
            if (boltAlpha <= 0.02f) continue;

            int glowRgb = lerpRgb(customColor, RED, hitT);
            int coreRgb = lerpRgb(WHITE, RED, hitT);

            int glowColor = withAlpha(glowRgb, (int) (boltAlpha * 150));
            int coreColor = withAlpha(coreRgb, (int) (boltAlpha * 255));

            // свечение: билборд у каждой точки (как drawCubeGlowSprite)
            for (Vec3d point : bolt.points) {
                float blobScale = 0.16f + random.nextFloat() * 0.10f;
                float h = blobScale / 2f;

                matrices.push();
                matrices.translate(point.x - cameraPos.x, point.y - cameraPos.y, point.z - cameraPos.z);
                matrices.multiply(cameraRotation);
                Matrix4f m = matrices.peek().getPositionMatrix();

                glowBuf.vertex(m, -h, -h, 0.0f).color(glowColor).texture(0.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h, -h, 0.0f).color(glowColor).texture(1.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h,  h, 0.0f).color(glowColor).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, -h,  h, 0.0f).color(glowColor).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);

                matrices.pop();
            }

            // линии: translate(база-камера) + локальные вершины (как режим "Кольцо")
            matrices.push();
            matrices.translate(basePos.x - cameraPos.x, basePos.y - cameraPos.y, basePos.z - cameraPos.z);
            Matrix4f lm = matrices.peek().getPositionMatrix();

            List<Vec3d> points = bolt.points;
            for (int s = 0; s < points.size() - 1; s++) {
                Vec3d a = points.get(s).subtract(basePos);
                Vec3d b = points.get(s + 1).subtract(basePos);
                lineBuf.vertex(lm, (float) a.x, (float) a.y, (float) a.z).color(coreColor);
                lineBuf.vertex(lm, (float) b.x, (float) b.y, (float) b.z).color(coreColor);
            }

            matrices.pop();
        }
    }

    private Bolt spawnBolt(LivingEntity target, Vec3d basePos) {
        double width = target.getWidth();
        double height = target.getHeight();
        double surfaceRadius = width * 0.5 + 0.05;

        double angle = random.nextDouble() * Math.PI * 2;
        double h = random.nextDouble() * height;

        Vec3d start = basePos.add(Math.cos(angle) * surfaceRadius, h, Math.sin(angle) * surfaceRadius);

        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.toRadians((random.nextDouble() - 0.5) * 140.0);
        double dx = Math.cos(phi) * Math.cos(theta);
        double dy = Math.sin(phi);
        double dz = Math.cos(phi) * Math.sin(theta);

        double spikeLength = 0.25 + random.nextDouble() * 0.3;
        Vec3d end = start.add(dx * spikeLength, dy * spikeLength, dz * spikeLength);

        List<Vec3d> points = generateLightningPath(start, end, LIGHTNING_DEPTH, spikeLength * 0.4);

        Bolt bolt = new Bolt();
        bolt.points = points;
        bolt.spawnTime = System.currentTimeMillis();
        bolt.lifetimeMs = 130 + random.nextInt(140);
        return bolt;
    }

    private List<Vec3d> generateLightningPath(Vec3d start, Vec3d end, int depth, double maxOffset) {
        if (depth <= 0) {
            List<Vec3d> result = new ArrayList<>(2);
            result.add(start);
            result.add(end);
            return result;
        }

        Vec3d dir = end.subtract(start);
        Vec3d mid = start.add(dir.multiply(0.5));
        Vec3d perp = randomPerpendicular(dir);
        double offset = (random.nextDouble() - 0.5) * 2.0 * maxOffset;
        Vec3d displacedMid = mid.add(perp.multiply(offset));

        List<Vec3d> left = generateLightningPath(start, displacedMid, depth - 1, maxOffset * 0.5);
        List<Vec3d> right = generateLightningPath(displacedMid, end, depth - 1, maxOffset * 0.5);
        left.remove(left.size() - 1);
        left.addAll(right);
        return left;
    }

    private Vec3d randomPerpendicular(Vec3d dir) {
        Vec3d normDir = dir.normalize();
        Vec3d arbitrary = Math.abs(normDir.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d perp1 = normDir.crossProduct(arbitrary).normalize();
        Vec3d perp2 = normDir.crossProduct(perp1).normalize();
        double angle = random.nextDouble() * Math.PI * 2;
        return perp1.multiply(Math.cos(angle)).add(perp2.multiply(Math.sin(angle)));
    }

    private int lerpRgb(int rgbA, int rgbB, float t) {
        if (t <= 0f) return rgbA;
        if (t >= 1f) return rgbB;
        int rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
        int rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static class Bolt {
        List<Vec3d> points;
        long spawnTime;
        long lifetimeMs;
    }
}
