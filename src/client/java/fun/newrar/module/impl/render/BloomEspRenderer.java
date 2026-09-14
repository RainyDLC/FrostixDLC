package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public class BloomEspRenderer implements IMinecraft {
    public static final int MAX_BLOOM_PARTICLES = 64;
    private static final BloomParticleSeed[] BLOOM_SEEDS = new BloomParticleSeed[MAX_BLOOM_PARTICLES];

    public static final Identifier DEFAULT_BLOOM_TEXTURE = Identifier.of("punch", "textures/targetesp/bloom.png");

    static {
        for (int i = 0; i < MAX_BLOOM_PARTICLES; i++) {
            long seed = (long) i * 2654435761L + 987654321L;
            Random rnd = new Random(seed);
            float h = (float) i / (float) MAX_BLOOM_PARTICLES + (rnd.nextFloat() - 0.5f) * 0.06f;
            float r = 0.45f + rnd.nextFloat() * 0.55f;
            float ang = rnd.nextFloat() * (float) (Math.PI * 2.0);
            float dir = (i % 2 == 0) ? 1.0f : -1.0f;
            float oSpeed = dir * (0.8f + rnd.nextFloat() * 1.2f);
            float vSpeed = 0.25f + rnd.nextFloat() * 0.45f;
            float pSpeed = 2.0f + rnd.nextFloat() * 3.5f;
            float pPhase = rnd.nextFloat() * (float) (Math.PI * 2.0);
            float sSpeed = (rnd.nextFloat() - 0.5f) * 90.0f;
            float sSeed = rnd.nextFloat() * 360.0f;
            float szFactor = 0.75f + rnd.nextFloat() * 0.5f;
            float radFreq = 1.2f + rnd.nextFloat() * 2.0f;
            float radAmp = 0.04f + rnd.nextFloat() * 0.09f;
            BLOOM_SEEDS[i] = new BloomParticleSeed(h, r, ang, oSpeed, vSpeed, pSpeed, pPhase, sSpeed, sSeed, szFactor, radFreq, radAmp);
        }
    }

    private static final RenderPipeline BLOOM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/bloom_esp")
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

    public static final Function<Identifier, RenderLayer> BLOOM_LAYER =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(BLOOM_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(4096)
                        .build();
                return RenderLayer.of("bloom_esp", setup);
            });

    private Vec3d lastTargetPos;
    private float lastTargetHeight = 1.8f;
    private float lastTargetWidth = 0.6f;

    public void render(EventRender3D event, VertexConsumerProvider.Immediate immediate, LivingEntity target, float alphaPC,
                       int particleCount, float userBaseSize, float speedFactor) {
        render(event, immediate, target, alphaPC, particleCount, userBaseSize, speedFactor, DEFAULT_BLOOM_TEXTURE);
    }

    public void render(EventRender3D event, VertexConsumerProvider.Immediate immediate, LivingEntity target, float alphaPC,
                       int particleCount, float userBaseSize, float speedFactor, Identifier texture) {
        if (alphaPC <= 0.001f) return;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        float td = event.getTickDelta();
        if (target != null && target.isAlive()) {
            lastTargetPos = target.getLerpedPos(td);
            lastTargetHeight = target.getHeight();
            lastTargetWidth = target.getWidth();
        }

        Vec3d center = (target != null && target.isAlive()) ? target.getLerpedPos(td) : lastTargetPos;
        if (center == null) return;

        float entityHeight = (target != null && target.isAlive()) ? target.getHeight() : lastTargetHeight;
        float entityWidth = (target != null && target.isAlive()) ? target.getWidth() : lastTargetWidth;

        float hurtPC = 0f;
        if (target != null) {
            int hurtTicks = target.hurtTime;
            hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
        }
        hurtPC = MathHelper.clamp(hurtPC, 0f, 1f);

        int baseColor = ColorUtil.fade(1);
        int redColor = ColorUtil.getColor(255, 85, 105, (int) (255.0f * alphaPC));
        int particleThemeColor = ColorUtil.overCol(ColorUtil.multAlpha(baseColor, alphaPC), redColor, hurtPC);

        double time = (System.currentTimeMillis() % 36000000L) / 1000.0 * speedFactor;
        int count = Math.min(MAX_BLOOM_PARTICLES, Math.max(8, particleCount));

        Vec3d cam = camera.getCameraPos();
        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();

        MatrixStack ms = event.getMatrixStack();
        VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER.apply(texture != null ? texture : DEFAULT_BLOOM_TEXTURE));

        for (int i = 0; i < count; i++) {
            BloomParticleSeed seed = BLOOM_SEEDS[i];

            // 1. Плавный подъем вдоль высоты тела с циклическим переходом
            float rawH = (float) ((seed.heightBase + time * seed.verticalSpeed * 0.22) % 1.15);
            if (rawH < 0f) rawH += 1.15f;
            double worldY = center.y + rawH * entityHeight;

            // Затухание альфы в самом низу и вверху
            float hAlphaFade = 1.0f;
            if (rawH < 0.12f) {
                hAlphaFade = rawH / 0.12f;
            } else if (rawH > 0.92f) {
                hAlphaFade = Math.max(0.0f, (1.15f - rawH) / 0.23f);
            }

            // 2. Орбитальное покачивание и спиральное вращение вокруг вертикальной оси
            double curAngle = seed.angleOffset + time * seed.orbitSpeed + Math.sin(time * seed.radialFreq + seed.pulsePhase) * 0.35;
            double baseRadius = entityWidth * 0.65f * seed.radiusFactor + Math.sin(time * seed.radialFreq * 1.4 + seed.pulsePhase) * seed.radialAmp;
            double worldX = center.x + Math.cos(curAngle) * baseRadius;
            double worldZ = center.z + Math.sin(curAngle) * baseRadius;

            // 3. Анимация пульсации размера и альфы ("дыхание" / "сияние")
            float pulse = 0.78f + 0.32f * (float) Math.sin(time * seed.pulseSpeed + seed.pulsePhase);
            float currentSize = userBaseSize * seed.sizeFactor * pulse * alphaPC;
            float currentAlphaFactor = alphaPC * hAlphaFade * (0.6f + 0.4f * (float) Math.cos(time * seed.pulseSpeed + seed.pulsePhase));
            if (currentAlphaFactor <= 0.01f || currentSize <= 0.01f) continue;

            // 4. Поворот билборда
            float rotation = seed.spinSeed + (float) (time * seed.spinSpeed);

            // Отрисовка двух слоев: внешнее мягкое свечение + яркое компактное ядро
            ms.push();
            ms.translate(worldX - cam.x, worldY - cam.y, worldZ - cam.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
            if (rotation != 0f) {
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
            }
            Matrix4f matrix = ms.peek().getPositionMatrix();

            // Слой 1: Внешний ореол (Halo)
            float glowSize = currentSize * 1.55f;
            float glowHalf = glowSize * 0.5f;
            int glowAlpha = (int) (currentAlphaFactor * 110.0f);
            int glowCol = ColorUtil.replAlpha(particleThemeColor, glowAlpha);
            if (glowAlpha > 0) {
                drawBloomQuad(buffer, matrix, glowHalf, glowCol);
            }

            // Слой 2: Яркое ядро (Core)
            float coreHalf = currentSize * 0.5f;
            int coreAlpha = (int) (currentAlphaFactor * 220.0f);
            int coreCol = ColorUtil.replAlpha(particleThemeColor, coreAlpha);
            if (coreAlpha > 0) {
                drawBloomQuad(buffer, matrix, coreHalf, coreCol);
            }

            ms.pop();
        }
    }

    private static void drawBloomQuad(VertexConsumer buffer, Matrix4f matrix, float half, int color) {
        buffer.vertex(matrix, -half, -half, 0.0f).color(color).texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix,  half, -half, 0.0f).color(color).texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix,  half,  half, 0.0f).color(color).texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -half,  half, 0.0f).color(color).texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    public static class BloomParticleSeed {
        final float heightBase;
        final float radiusFactor;
        final float angleOffset;
        final float orbitSpeed;
        final float verticalSpeed;
        final float pulseSpeed;
        final float pulsePhase;
        final float spinSpeed;
        final float spinSeed;
        final float sizeFactor;
        final float radialFreq;
        final float radialAmp;

        BloomParticleSeed(float heightBase, float radiusFactor, float angleOffset, float orbitSpeed,
                          float verticalSpeed, float pulseSpeed, float pulsePhase, float spinSpeed,
                          float spinSeed, float sizeFactor, float radialFreq, float radialAmp) {
            this.heightBase = heightBase;
            this.radiusFactor = radiusFactor;
            this.angleOffset = angleOffset;
            this.orbitSpeed = orbitSpeed;
            this.verticalSpeed = verticalSpeed;
            this.pulseSpeed = pulseSpeed;
            this.pulsePhase = pulsePhase;
            this.spinSpeed = spinSpeed;
            this.spinSeed = spinSeed;
            this.sizeFactor = sizeFactor;
            this.radialFreq = radialFreq;
            this.radialAmp = radialAmp;
        }
    }
}
