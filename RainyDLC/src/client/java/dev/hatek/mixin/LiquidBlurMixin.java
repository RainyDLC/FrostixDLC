package dev.hatek.mixin;

import dev.hatek.client.ui.render.LiquidBackdrop;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Liquid Glass: снимки до и после ванильного blur GUI — чёткий фон и матовое стекло. */
@Mixin(GameRenderer.class)
public abstract class LiquidBlurMixin {
    @Inject(method = "processBlurEffect", at = @At("HEAD"), require = 0)
    private void hatek$liquidBeforeBlur(CallbackInfo ci) {
        LiquidBackdrop.onBlurStart();
    }

    @Inject(method = "processBlurEffect", at = @At("TAIL"), require = 0)
    private void hatek$liquidAfterBlur(CallbackInfo ci) {
        LiquidBackdrop.onBlurEnd();
    }
}
