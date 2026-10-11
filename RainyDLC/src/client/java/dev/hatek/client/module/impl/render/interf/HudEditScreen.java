package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Отдельный экран настройки HUD.
 * HUD рисуется явно каждый кадр (как в ClickGUI), перетаскивание —
 * тем же способом, что и панель ClickGUI: ЛКМ хватает элемент,
 * колесо мыши меняет размер. Без подсказок. ESC — выйти.
 */
public final class HudEditScreen extends Screen {
    private static final int DIM = 0x66000000;

    private boolean draggingHud;
    private InterfaceHud.Hit dragHudHit;
    private float hudGrabX;
    private float hudGrabY;

    public HudEditScreen() {
        super(Component.literal("HUD"));
    }

    @Override
    protected void init() {
        InterfaceHud.setScreenOpen(true);
    }

    @Override
    public void removed() {
        InterfaceHud.setScreenOpen(false);
        HudLayout.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY,
                                   float partialTick) {
        gg.fill(0, 0, this.width, this.height, DIM);
        InterfaceHud.renderExplicit(gg);
        super.extractRenderState(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        InterfaceHud.Hit hit = InterfaceHud.hitTest(mx, my);
        if (hit == null) {
            return super.mouseClicked(event, doubleClick);
        }
        this.draggingHud = true;
        this.dragHudHit = hit;
        this.hudGrabX = (float) mx - hit.bounds[0];
        this.hudGrabY = (float) my - hit.bounds[1];
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingHud && this.dragHudHit != null) {
            double mx = Render2D.mouseX();
            double my = Render2D.mouseY();
            InterfaceHud.moveHit(this.dragHudHit,
                    (float) mx - this.hudGrabX, (float) my - this.hudGrabY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingHud) {
            this.draggingHud = false;
            this.dragHudHit = null;
            HudLayout.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            double mx = Render2D.mouseX();
            double my = Render2D.mouseY();
            InterfaceHud.Hit hit = InterfaceHud.hitTest(mx, my);
            if (hit != null) {
                InterfaceHud.resizeHit(hit, scrollY > 0 ? 1.1f : 1.0f / 1.1f);
                HudLayout.save();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
