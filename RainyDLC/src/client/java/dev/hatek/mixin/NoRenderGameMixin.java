package dev.hatek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderGameMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void hatek$noHurtCam(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (NoRender.noHurtCam()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void hatek$noViewBobbing(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (NoRender.noViewBobbing()) {
            ci.cancel();
        }
    }
}
