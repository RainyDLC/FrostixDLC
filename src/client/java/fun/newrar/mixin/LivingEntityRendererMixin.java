package fun.newrar.mixin;

import fun.newrar.interfaces.TargetScanRenderState;
import fun.newrar.module.impl.render.TargetEsp;
import fun.newrar.utils.render.TargetScanRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<
        T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    @Shadow
    public abstract Identifier getTexture(S state);

    @Shadow
    protected M model;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void nightix$extractTargetScan(T entity, S state, float tickDelta, CallbackInfo ci) {
        TargetEsp targetESP = TargetEsp.getInstance();
        ((TargetScanRenderState) state).nightix$setTargetScanTarget(
                targetESP != null && targetESP.isEnabled() && targetESP.isRenderedTarget(entity));
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V",
                    shift = At.Shift.BEFORE))
    private void nightix$submitTargetScan(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!((TargetScanRenderState) state).nightix$isTargetScanTarget()) {
            return;
        }

        TargetEsp targetESP = TargetEsp.getInstance();
        if (targetESP == null || !targetESP.isEnabled() || !targetESP.type.is("Переливание")) {
            return;
        }

        TargetScanRenderer.submit(
                this.model,
                state,
                matrices,
                queue,
                this.getTexture(state),
                targetESP.scanColor(),
                targetESP.scanSecondColor(),
                targetESP.saturation(),
                targetESP.renderAnimation(),
                targetESP.scanSpeed(),
                targetESP.scanGlow());
    }
}
