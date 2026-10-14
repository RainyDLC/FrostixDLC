package dev.hatek.mixin;
import dev.hatek.client.module.impl.render.interf.quick.QuickItemOpacity;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
@Mixin(GuiRenderer.class)
public abstract class QuickItemOpacityRenderMixin {
    /** Vanilla uses a premultiplied-alpha atlas: dim RGB as well as alpha, not alpha alone. */
    @ModifyConstant(method="submitBlitFromItemAtlas",constant=@Constant(intValue=-1))
    private int quick$opacity(int original,GuiItemRenderState item,GuiItemAtlas.SlotView slot) {
        return QuickItemOpacity.color(item,original);
    }
}
