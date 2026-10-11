package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.interf.HudMover;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перемещение (зажать ЛКМ) и масштабирование (колесико мыши) элементов HUD
 * прямо во время игры без открытия редактора и без лишних подсказок.
 */
@Mixin(MouseHandler.class)
public class HudMoveMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hatek$hudMove(long window, MouseButtonInfo buttonInfo, int action,
                                CallbackInfo ci) {
        if (buttonInfo.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (HudMover.tryStartInGame()) {
                ci.cancel();
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            if (HudMover.isDragging()) {
                HudMover.stop();
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hatek$hudScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
        if (HudMover.onScrollInGame(yoffset)) {
            ci.cancel();
        }
    }
}
