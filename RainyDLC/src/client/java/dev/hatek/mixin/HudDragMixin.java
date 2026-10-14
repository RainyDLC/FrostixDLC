package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.interf.HudLayout;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.screen.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import dev.hatek.client.module.impl.render.interf.quick.QuickElementMenu;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перетаскивание Quick HUD только в открытом чате: ЛКМ по элементу хватает его,
 * колесо мыши меняет размер. Клик поглощается, чтобы экран под ним
 * не реагировал. Без подсказок, без кнопок, без ALT.
 */
@Mixin(MouseHandler.class)
public class HudDragMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hatek$hudDragButton(long window, MouseButtonInfo buttonInfo, int action,
                                     CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) InterfaceHud.stopDrag();
        if (!shouldHandle()) { QuickElementMenu.close(); return; }
        if (QuickElementMenu.mouseButton(Render2D.mouseX(),Render2D.mouseY(),buttonInfo.button(),action)) {
            ci.cancel(); return;
        }
        if (buttonInfo.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (InterfaceHud.startDrag(Render2D.mouseX(), Render2D.mouseY())) {
                ci.cancel();
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            InterfaceHud.stopDrag();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hatek$hudDragScroll(long window, double xOffset, double yOffset,
                                     CallbackInfo ci) {
        if (yOffset == 0.0 || !shouldHandle()) {
            return;
        }
        if (QuickElementMenu.open()) { ci.cancel(); return; }
        InterfaceHud.Hit hit = InterfaceHud.hitTest(Render2D.mouseX(), Render2D.mouseY());
        if (hit != null) {
            InterfaceHud.resizeHit(hit, yOffset > 0 ? 1.1f : 1.0f / 1.1f);
            HudLayout.save();
            ci.cancel();
        }
    }

    /**
     * Открыт чат, модуль включён,
     * в игре. Наш ClickGUI исключён — там HUD не трогаем.
     */
    private static boolean shouldHandle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        Interface module = Interface.instance();
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (mc.gui.screen() instanceof ClickGuiScreen) {
            return false;
        }
        return mc.gui.screen() instanceof ChatScreen;
    }
}
