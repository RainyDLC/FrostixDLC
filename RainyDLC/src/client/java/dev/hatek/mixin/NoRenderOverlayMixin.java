package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class NoRenderOverlayMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                           Sprite sprite, CallbackInfo ci) {
        if (NoRender.hideFire()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderUnderwater(MinecraftClient client, MatrixStack matrices,
                                                 VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (NoRender.hideUnderwater()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void hatek$noRenderInWall(Sprite sprite, MatrixStack matrices,
                                             VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (NoRender.hideInWall()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderTotemPop(MatrixStack matrices, float tickProgress,
                                        OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (NoRender.hideTotemPop()) {
            ci.cancel();
        }
    }
}
