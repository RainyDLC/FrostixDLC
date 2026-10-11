package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Свободное перемещение элементов HUD прямо в игре: зажать ALT,
 * нажать ЛКМ по элементу и тянуть. Клик при этом поглощается игрой.
 * Движение применяется каждый тик, пока зажат ALT.
 */
public final class HudMover {
    private static boolean dragging;
    /** null = 2D-медиа, иначе элемент HudLayout. */
    private static HudLayout.Element dragElement;
    private static boolean dragMedia;
    private static float grabDX;
    private static float grabDY;

    private HudMover() {
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean altDown() {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
    }

    /**
     * Пробует начать перемещение. Вызывается из миксина при нажатии ЛКМ.
     * Возвращает true, если клик нужно поглотить (начали двигать HUD).
     */
    public static boolean tryStart() {
        if (dragging) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui == null) {
            return false;
        }
        if (!altDown()) {
            return false;
        }
        Interface module = Interface.instance();
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (mc.gui.screen() != null) {
            return false;
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        // Порядок хит-теста: сверху вниз (медиа рисуется последним).
        if (Interface.showMedia2D()) {
            float[] b = InterfaceHud.media2DBounds();
            if (hit(b, mx, my)) {
                start(null, true, b, mx, my);
                return true;
            }
        }
        Map<HudLayout.Element, float[]> bounds = InterfaceHud.boundsSnapshot();
        List<HudLayout.Element> order = new ArrayList<>(bounds.keySet());
        Collections.reverse(order);
        for (HudLayout.Element el : order) {
            float[] b = bounds.get(el);
            if (hit(b, mx, my)) {
                start(el, false, b, mx, my);
                return true;
            }
        }
        return false;
    }

    private static void start(HudLayout.Element el, boolean media,
                              float[] b, double mx, double my) {
        dragging = true;
        dragElement = el;
        dragMedia = media;
        grabDX = (float) mx - b[0];
        grabDY = (float) my - b[1];
    }

    /** Движение за курсором. Вызывается каждый клиентский тик. */
    public static void tick() {
        if (!dragging) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !altDown() || mc.gui.screen() != null) {
            stop();
            return;
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();

        if (dragMedia) {
            float[] b = InterfaceHud.media2DBounds();
            if (b == null) {
                stop();
                return;
            }
            float w = b[2];
            float h = b[3];
            float x = clamp((float) mx - grabDX, -w + 24.0f, sw - 24.0f);
            float y = clamp((float) my - grabDY, -h + 24.0f, sh - 24.0f);
            Interface.setMediaGeometry(Interface.mediaSize(),
                    (x + w / 2.0f) / sw * 100.0, (y + h / 2.0f) / sh * 100.0);
        } else {
            float[] b = InterfaceHud.boundsSnapshot().get(dragElement);
            if (b == null) {
                stop();
                return;
            }
            float x = clamp((float) mx - grabDX, -b[2] + 24.0f, sw - 24.0f);
            float y = clamp((float) my - grabDY, -b[3] + 24.0f, sh - 24.0f);
            HudLayout.setPos(dragElement, x, y);
        }
    }

    public static void stop() {
        if (dragging) {
            dragging = false;
            dragElement = null;
            dragMedia = false;
            HudLayout.save();
        }
    }

    private static boolean hit(float[] b, double mx, double my) {
        return b != null && mx >= b[0] && mx <= b[0] + b[2]
                && my >= b[1] && my <= b[1] + b[3];
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
