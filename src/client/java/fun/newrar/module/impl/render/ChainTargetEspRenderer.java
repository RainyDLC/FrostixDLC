package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.utils.colors.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public final class ChainTargetEspRenderer {
    public static final Identifier CHAIN_TEXTURE =
            Identifier.of("client", "textures/targetesp/chain.png");

    private static final int TOTAL_ANGLE = 360 * 2;
    private static final int LINKS_STEP = 18;
    private static final int MODIF = LINKS_STEP / 2;
    private static final float CHAIN_SIZE = 4.0F;
    private static final float DOWN = 1.0F;
    private static final int RED_COLOR = 0xFFFF0000;

    private static long startTimeMs = -1L;

    public static final RenderPipeline CHAIN_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "chain_esp"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer CHAIN_LAYER = RenderLayer.of(
            "chain_esp",
            RenderSetup.builder(CHAIN_ESP_PIPELINE)
                    .texture("Sampler0", CHAIN_TEXTURE)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> CHAIN_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(CHAIN_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("chain_esp", setup);
            });

    private ChainTargetEspRenderer() {
    }

    public static void render(MatrixStack stack, VertexConsumerProvider provider, TargetEspRenderContext context) {
        LivingEntity target = context.target();
        if (target == null) {
            return;
        }

        int alphaVal = MathHelper.clamp((int) (context.alpha() * 255.0F), 0, 255);
        if (alphaVal <= 0) {
            return;
        }

        float movingValue = getMovingValue(target);

        float hurtProgress = MathHelper.clamp(context.hurtProgress(), 0.0F, 1.0F);
        int baseColor = context.primaryColor();
        int blendedColor = hurtProgress > 0.0F
                ? ColorUtil.interpolateColor2(baseColor, RED_COLOR, hurtProgress)
                : baseColor;

        float width = target.getWidth() * 1.5F;
        float gradusX = 20.0F * (float) Math.min(1.0 + Math.sin(Math.toRadians(movingValue)), 1.0);
        float gradusZ = 20.0F * (float) (Math.min(1.0 + Math.sin(Math.toRadians(movingValue)), 2.0) - 1.0);

        float anchorY = target.getHeight() / 2.0F - 0.5F;

        VertexConsumer chainConsumer = provider.getBuffer(CHAIN_LAYER);
        stack.push();
        stack.translate(0.0F, anchorY, 0.0F);
        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2F - 0.5F * (chain == 0 ? 1.0F : 0.9F);
            float tiltZ = chain == 0 ? gradusX : -gradusX;
            float tiltX = chain == 0 ? gradusZ : -gradusZ;
            stack.push();
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltZ));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tiltX));
            int color = ColorUtil.replAlpha(blendedColor, alphaVal);
            Matrix4f matrix = stack.peek().getPositionMatrix();
            renderChainBand(chainConsumer, matrix, width, val, movingValue, tiltX, tiltZ, chain, color);
            stack.pop();
        }
        stack.pop();
    }

    public static void endBatch(VertexConsumerProvider provider) {
        if (provider instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(CHAIN_LAYER);
        }
    }

    private static float getMovingValue(LivingEntity target) {
        long now = System.currentTimeMillis();
        if (startTimeMs < 0L) {
            startTimeMs = now;
        }

        float base = (float) ((now - startTimeMs) * 0.08) % 360.0F;
        if (target != null) {
            double dx = target.getX() - target.lastRenderX;
            double dy = target.getY() - target.lastRenderY;
            double dz = target.getZ() - target.lastRenderZ;
            double motion = Math.sqrt(dx * dx + dy * dy + dz * dz);
            base += (float) (motion * 360.0);
        } else {
            base += (float) (Math.sin(now / 1000.0) * 5.0);
        }
        return base;
    }

    private static void renderChainBand(
            VertexConsumer consumer,
            Matrix4f matrix,
            float width,
            float val,
            float moving,
            float tiltX,
            float tiltZ,
            int chain,
            int color
    ) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        for (int i = 0; i < TOTAL_ANGLE; i += MODIF) {
            float prevSin = tiltZ / 100.0F
                    + (float) Math.sin(Math.toRadians(i - MODIF + moving * 0.5F)) * width * val;
            float prevCos = -tiltX / 100.0F
                    + (float) Math.cos(Math.toRadians(i - MODIF + moving * 0.5F)) * width * val;
            float sin = tiltZ / 100.0F
                    + (float) Math.sin(Math.toRadians(i + moving * 0.5F)) * width * val;
            float cos = -tiltX / 100.0F
                    + (float) Math.cos(Math.toRadians(i + moving * 0.5F)) * width * val;

            float uPrev = fract(1.0F / 360.0F * (float) (i - MODIF) * CHAIN_SIZE);
            float uCurrent = fract(1.0F / 360.0F * (float) i * CHAIN_SIZE);

            consumer.vertex(matrix, prevSin, 0.0F, prevCos).texture(uPrev, 0.0F).color(r, g, b, a);
            consumer.vertex(matrix, sin, 0.0F, cos).texture(uCurrent, 0.0F).color(r, g, b, a);
            consumer.vertex(matrix, sin, DOWN, cos).texture(uCurrent, 1.0F - 0.01F).color(r, g, b, a);
            consumer.vertex(matrix, prevSin, DOWN, prevCos).texture(uPrev, 1.0F - 0.01F).color(r, g, b, a);
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
