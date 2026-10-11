package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.ui.render.Render2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Прямое перетаскивание HUD модуля Interface в ClickGUI:
 * ЛКМ по элементу и тянуть — двигать, колесо над элементом — размер.
 * Никакого отдельного редактора и подсказок.
 */
public final class HudMover {
    private static boolean dragging;
    /** null + dragMedia = 2D-медиа, иначе элемент HudLayout. */
    private static HudLayout.Element dragElement;
    private static boolean dragMedia;
    private static float grabDX;
    private static float grabDY;

    private HudMover() {
    }

    public static boolean isDragging() {
        return dragging;
    }

    private static boolean canInteract() {
        Interface module = Interface.instance();
        return module != null && module.isEnabled();
    }

    /**
     * Хит-тест и старт перетаскивания. Вызывается из ClickGuiScreen.
     */
    public static boolean tryStart(double mx, double my) {
        if (dragging || !canInteract()) {
            return false;
        }
        // Медиа рисуется последним = верхнее.
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

    /** Движение за курсором. Вызывается из ClickGuiScreen.mouseDragged. */
    public static void dragTo(double mx, double my) {
        if (!dragging) {
            return;
        }
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();
        if (dragMedia) {
            float[] b = InterfaceHud.media2DBounds();
            if (b == null) {
                stop();
                return;
            }
            moveMedia((float) mx - grabDX, (float) my - grabDY, b[2], b[3], sw, sh);
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

    private static void moveMedia(float x, float y, float w, float h, int sw, int sh) {
        x = clamp(x, -w + 24.0f, sw - 24.0f);
        y = clamp(y, -h + 24.0f, sh - 24.0f);
        Interface.setMediaGeometry(Interface.mediaSize(),
                (x + w / 2.0f) / sw * 100.0, (y + h / 2.0f) / sh * 100.0);
    }

    /**
     * Колесо над элементом — размер. Возвращает true, если обработано.
     */
    public static boolean onScroll(double mx, double my, double scrollY) {
        if (!canInteract() || scrollY == 0) {
            return false;
        }
        float factor = scrollY > 0 ? 1.1f : 1.0f / 1.1f;
        if (Interface.showMedia2D()) {
            float[] b = InterfaceHud.media2DBounds();
            if (hit(b, mx, my)) {
                double size = Interface.mediaSize() * factor;
                Interface.setMediaGeometry(size, Interface.mediaX(), Interface.mediaY());
                return true;
            }
        }
        Map<HudLayout.Element, float[]> bounds = InterfaceHud.boundsSnapshot();
        List<HudLayout.Element> order = new ArrayList<>(bounds.keySet());
        Collections.reverse(order);
        for (HudLayout.Element el : order) {
            if (hit(bounds.get(el), mx, my)) {
                HudLayout.setScale(el, HudLayout.get(el).scale * factor);
                return true;
            }
        }
        return false;
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
