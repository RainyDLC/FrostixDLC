package relake.client.utils.render.world.targetesp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import relake.client.utils.render.color.ColorUtil;
import relake.client.utils.render.pipeline.ClientPipelines;

public final class ChainTargetEspRenderer {
    private static final Identifier CHAIN_TEXTURE =
            Identifier.fromNamespaceAndPath("relake", "textures/features/targetesp/chain.png");

    private static final int TOTAL_ANGLE = 360 * 2;
    private static final int LINKS_STEP = 18;
    private static final int MODIF = LINKS_STEP / 2;
    private static final float CHAIN_SIZE = 4.0F;
    private static final float DOWN = 1.0F;
    private static final int RED_COLOR = 0xFFFF0000;

    private static long startTimeMs = -1L;

    private ChainTargetEspRenderer() {
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource provider, TargetEspRenderContext context) {
        LivingEntity target = context.target();
        if (target == null) {
            return;
        }

        int alphaVal = Mth.clamp((int) (context.alpha() * 255.0F), 0, 255);
        if (alphaVal <= 0) {
            return;
        }

        float movingValue = getMovingValue(target);

        float hurtProgress = Mth.clamp(context.hurtProgress(), 0.0F, 1.0F);
        int baseColor = context.primaryColor();
        int blendedColor = hurtProgress > 0.0F
                ? ColorUtil.lerpColor(baseColor, RED_COLOR, hurtProgress)
                : baseColor;

        float width = target.getBbWidth() * 1.5F;
        float gradusX = 20.0F * (float) Math.min(1.0 + Math.sin(Math.toRadians(movingValue)), 1.0);
        float gradusZ = 20.0F * (float) (Math.min(1.0 + Math.sin(Math.toRadians(movingValue)), 2.0) - 1.0);

        float anchorY = target.getBbHeight() / 2.0F - 0.5F;

        VertexConsumer chainConsumer = provider.getBuffer(ClientPipelines.CHAIN_ESP.apply(CHAIN_TEXTURE));
        stack.pushPose();
        stack.translate(0.0F, anchorY, 0.0F);
        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2F - 0.5F * (chain == 0 ? 1.0F : 0.9F);
            float tiltZ = chain == 0 ? gradusX : -gradusX;
            float tiltX = chain == 0 ? gradusZ : -gradusZ;
            stack.pushPose();
            stack.mulPose(Axis.ZP.rotationDegrees(tiltZ));
            stack.mulPose(Axis.XP.rotationDegrees(tiltX));
            int color = ColorUtil.withAlpha(blendedColor, alphaVal);
            renderChainBand(chainConsumer, stack.last(), width, val, movingValue, tiltX, tiltZ, chain, color);
            stack.popPose();
        }
        stack.popPose();
    }

    public static void endBatch(MultiBufferSource.BufferSource provider) {
        provider.endBatch(ClientPipelines.CHAIN_ESP.apply(CHAIN_TEXTURE));
    }

    private static float getMovingValue(LivingEntity target) {

        long now = System.currentTimeMillis();
        if (startTimeMs < 0L) {
            startTimeMs = now;
        }

        float base = (float) ((now - startTimeMs) * 0.08) % 360.0F;
        if (target != null) {
            double dx = target.getX() - target.xo;
            double dy = target.getY() - target.yo;
            double dz = target.getZ() - target.zo;
            double motion = Math.sqrt(dx * dx + dy * dy + dz * dz);
            base += (float) (motion * 360.0);
        } else {
            base += (float) (Math.sin(now / 1000.0) * 5.0);
        }
        return base;
    }

    private static void renderChainBand(
            VertexConsumer consumer,
            PoseStack.Pose pose,
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

            consumer.addVertex(pose, prevSin, 0.0F, prevCos).setUv(uPrev, 0.0F).setColor(r, g, b, a);
            consumer.addVertex(pose, sin, 0.0F, cos).setUv(uCurrent, 0.0F).setColor(r, g, b, a);
            consumer.addVertex(pose, sin, DOWN, cos).setUv(uCurrent, 1.0F - 0.01F).setColor(r, g, b, a);
            consumer.addVertex(pose, prevSin, DOWN, prevCos).setUv(uPrev, 1.0F - 0.01F).setColor(r, g, b, a);
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}