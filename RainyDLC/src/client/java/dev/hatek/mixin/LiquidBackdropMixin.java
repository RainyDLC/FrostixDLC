package dev.hatek.mixin;

import dev.hatek.client.ui.render.LiquidBackdrop;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Liquid Glass: снимок мира до того, как GUI начнёт рисоваться в main target. */
@Mixin(GuiRenderer.class)
public abstract class LiquidBackdropMixin {
    // require = 0: если в новой версии метод переименуют — стекло просто упадёт на обычный blur.
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void hatek$liquidBackdrop(CallbackInfo ci) {
        LiquidBackdrop.capture();
    }
}
