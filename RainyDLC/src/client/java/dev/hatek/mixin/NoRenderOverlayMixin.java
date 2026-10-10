package dev.hatek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderOverlayMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderFire(PoseStack poseStack, SubmitNodeCollector collector,
                                           TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoRender.hideFire()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderUnderwater(Minecraft minecraft, PoseStack poseStack,
                                                 SubmitNodeCollector collector, CallbackInfo ci) {
        if (NoRender.hideUnderwater()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderInWall(TextureAtlasSprite sprite, PoseStack poseStack,
                                             SubmitNodeCollector collector, int color, CallbackInfo ci) {
        if (NoRender.hideInWall()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderTotemPop(PoseStack poseStack, float progress,
                                        SubmitNodeCollector collector, CallbackInfo ci) {
        if (NoRender.hideTotemPop()) {
            ci.cancel();
        }
    }
}
