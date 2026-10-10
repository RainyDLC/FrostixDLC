package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ПКМ по ватермарке модуля Interface открывает выбор её позиции.
 * Клик глотается, чтобы игра не реагировала (не ела еду, не ставила блоки).
 */
@Mixin(MouseHandler.class)
public class InterfaceMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hatek$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (buttonInfo.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
            if (InterfaceHud.handleRightClick()) {
                ci.cancel();
            }
        }
    }
}
