package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderGameMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void hatek$noHurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NoRender.noHurtCam()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void hatek$noViewBobbing(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NoRender.noViewBobbing()) {
            ci.cancel();
        }
    }
}
