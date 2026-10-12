package dev.hatek.mixin;

import dev.hatek.client.ui.render.LiquidBackdrop;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Liquid Glass: чёткий снимок мира до того, как GUI начнёт рисоваться в main target. */
@Mixin(GuiRenderer.class)
public abstract class LiquidBackdropMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void hatek$liquidBackdrop(CallbackInfo ci) {
        LiquidBackdrop.onGuiRender();
    }
}
