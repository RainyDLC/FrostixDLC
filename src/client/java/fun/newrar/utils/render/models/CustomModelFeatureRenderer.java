package fun.newrar.utils.render.models;

import fun.newrar.module.impl.render.Models;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public class CustomModelFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CustomModelFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrixStack, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Models models = Models.getInstance();
        if (models == null || !models.isEnabled()) return;
        if (!models.isTarget(state)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state.id == mc.player.getId() && mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        try {
            float time = (float) ((System.currentTimeMillis() % 1000000L) / 1000.0);
            float wheelRotation = limbAngle * (180.0f / (float) Math.PI) * 0.70f;
            float scale = models.scale.getValue();

            VertexConsumerProvider.Immediate immediate = ModelRenderer3D.getImmediate();
            VertexConsumer quads = immediate.getBuffer(ModelRenderer3D.MODEL_LAYER);
            VertexConsumer lines = immediate.getBuffer(ModelRenderer3D.LINE_LAYER);

            // ================= HEAD PARTS =================
            matrixStack.push();
            this.getContextModel().head.applyTransform(matrixStack);
            if (scale != 1.0f) {
                matrixStack.scale(scale, scale, scale);
            }

            if (models.isEarsActive()) {
                ModelRenderer3D.renderEars(matrixStack, quads, models, models.getEarsType(), time);
            }

            if (models.isHornsActive()) {
                ModelRenderer3D.renderDemonHorns(matrixStack, quads, models.getPrimaryColor());
            }

            if (models.isSkirtActive() || models.modelPreset.is("Тянка")) {
                ModelRenderer3D.renderHeadpiece(matrixStack, quads, models.getPrimaryColor(), models.getSecondaryColor());
            }

            if (models.isMuzzleActive()) {
                ModelRenderer3D.renderMuzzle(matrixStack, quads, models.getPrimaryColor());
            }

            matrixStack.pop();

            // ================= BODY PARTS =================
            matrixStack.push();
            this.getContextModel().body.applyTransform(matrixStack);
            if (scale != 1.0f) {
                matrixStack.scale(scale, scale, scale);
            }

            if (models.isWheelchairActive()) {
                ModelRenderer3D.renderWheelchair(matrixStack, quads, lines, models, wheelRotation);
            }

            if (models.isSkirtActive() && !models.isWheelchairActive()) {
                ModelRenderer3D.renderPleatedSkirt(matrixStack, quads, models, limbAngle, limbDistance);
            }

            if (models.isTailActive()) {
                ModelRenderer3D.renderTail(matrixStack, quads, models, models.getTailType(), time, limbAngle, limbDistance);
            }

            if (models.isWingsActive()) {
                ModelRenderer3D.renderDemonWings(matrixStack, quads, models, time);
            }

            if (models.isBowsActive() && !models.isWheelchairActive()) {
                ModelRenderer3D.renderChestBow(matrixStack, quads, models.getSecondaryColor());
            }

            matrixStack.pop();

            immediate.draw();
        } catch (Throwable ignored) {
        }
    }
}
