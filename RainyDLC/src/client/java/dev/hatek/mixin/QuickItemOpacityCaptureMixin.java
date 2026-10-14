package dev.hatek.mixin;
import dev.hatek.client.module.impl.render.interf.quick.QuickItemOpacity;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderState.class)
public abstract class QuickItemOpacityCaptureMixin {
    @Inject(method="addItem",at=@At("HEAD"))
    private void quick$capture(GuiItemRenderState item,CallbackInfo ci) { QuickItemOpacity.capture(item); }
}
