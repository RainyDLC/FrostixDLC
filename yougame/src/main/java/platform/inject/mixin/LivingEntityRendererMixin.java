package platform.inject.mixin;

import aethereal.core.Delta;
import aethereal.module.render.TargetESP;
import aethereal.render.TargetScanRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import platform.interfaces.TargetScanRenderState;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<
        T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Shadow
    protected M model;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void delta$extractTargetScan(T entity, S state, float partialTick, CallbackInfo ci) {
        TargetESP targetESP = Delta.h() == null ? null : Delta.h().d().t().targetESP();
        ((TargetScanRenderState) state).delta$setTargetScanTarget(
                targetESP != null && targetESP.m() && targetESP.isRenderedTarget(entity));
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    shift = At.Shift.BEFORE))
    private void delta$submitTargetScan(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!((TargetScanRenderState) state).delta$isTargetScanTarget()) {
            return;
        }

        TargetESP targetESP = Delta.h() == null ? null : Delta.h().d().t().targetESP();
        if (targetESP == null || !targetESP.m()) {
            return;
        }

        TargetScanRenderer.submit(
                this.model,
                state,
                poseStack,
                collector,
                this.getTextureLocation(state),
                targetESP.scanColor(),
                targetESP.scanSecondColor(),
                targetESP.saturation(),
                targetESP.renderAnimation(),
                targetESP.scanSpeed(),
                targetESP.scanGlow());
    }
}
