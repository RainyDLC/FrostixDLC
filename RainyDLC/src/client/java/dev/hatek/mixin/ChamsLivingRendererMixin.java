package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.Chams;
import dev.hatek.client.render.ChamsRenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class ChamsLivingRendererMixin {
    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void hatek$chamsRenderType(LivingEntityRenderState state, boolean bodyVisible,
                                       boolean translucent, boolean showOutline,
                                       CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof AvatarRenderState avatar && Chams.activeFor(avatar)
                && (bodyVisible || translucent)) {
            cir.setReturnValue(ChamsRenderType.forSkin(getTextureLocation(state), Chams.throughWalls()));
        }
    }
}
