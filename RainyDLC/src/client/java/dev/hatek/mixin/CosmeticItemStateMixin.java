package dev.hatek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.cosmetic.SwordRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рисует кастомный 3D-меч вместо ванильной модели. */
@Mixin(ItemStackRenderState.class)
public class CosmeticItemStateMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void hatek$submitCosmeticSword(PoseStack poseStack, SubmitNodeCollector collector,
                                           int light, int overlay, int outline, CallbackInfo ci) {
        if (SwordRenderer.submit((ItemStackRenderState) (Object) this, poseStack, collector, light, overlay)) {
            ci.cancel();
        }
    }
}
