package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Редактор размера HUD модуля Interface.
 * Открывается кнопкой "HUD Editor" ("Размер худа") в настройках модуля:
 * ClickGUI закрывается, игра за экраном затемняется.
 * Уголок справа-снизу элемента — размер перетаскиванием,
 * колесо мыши над элементом — точный масштаб.
 * Перемещение элементов — прямо в игре: ALT + ЛКМ и тянуть.
 * ESC / "Готово" — выйти.
 */
public final class HudEditScreen extends Screen {
    private static final float HANDLE = 13.0f;
    private static final int DIM = 0x66000000;

    /** Цель для ресайза: элемент из HudLayout либо 2D-медиа. */
    private static final class Target {
        final String key;
        final String label;
        final HudLayout.Element element; // null = 2D-медиа (настройки модуля)
        float[] bounds; // {x, y, w, h}, обновляется каждый кадр

        Target(String key, String label, HudLayout.Element element) {
            this.key = key;
            this.label = label;
            this.element = element;
        }

        boolean isMedia() {
            return this.element == null;
        }

        float scaleBase() {
            if (isMedia()) {
                return (float) Interface.mediaSize();
            }
            return HudLayout.get(this.element).scale;
        }

        void scaleBy(float factor) {
            if (isMedia()) {
                double size = Interface.mediaSize() * factor;
                Interface.setMediaGeometry(size, Interface.mediaX(), Interface.mediaY());
            } else {
                HudLayout.setScale(this.element, HudLayout.get(this.element).scale * factor);
            }
        }

        void moveTo(float x, float y, int sw, int sh) {
            float w = this.bounds[2];
            float h = this.bounds[3];
            x = Math.max(-w + 24.0f, Math.min(sw - 24.0f, x));
            y = Math.max(-h + 24.0f, Math.min(sh - 24.0f, y));
            if (isMedia()) {
                float cx = x + w / 2.0f;
                float cy = y + h / 2.0f;
                Interface.setMediaGeometry(Interface.mediaSize(),
                        cx / sw * 100.0, cy / sh * 100.0);
            } else {
                HudLayout.setPos(this.element, x, y);
            }
        }

        void resizeTo(float base, float factor) {
            factor = Math.max(0.1f, factor);
            if (isMedia()) {
                Interface.setMediaGeometry(base * factor,
                        Interface.mediaX(), Interface.mediaY());
            } else {
                HudLayout.setScale(this.element, base * factor);
            }
        }
    }

    /**
     * Цели живут между кадрами (иначе drag умирал бы на следующем кадре,
     * т.к. contains сравнивал бы разные объекты).
     */
    private final Map<String, Target> targets = new LinkedHashMap<>();
    private Target dragTarget;
    private boolean dragMove;
    private float grabDX;
    private float grabDY;
    private float resizeStartDist;
    private float resizeBase;
    private Target hoverTarget;

    public HudEditScreen() {
        super(Component.literal("Редактор HUD"));
        InterfaceHud.setEditMode(true);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 38;
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> close())
                .bounds(cx - 108, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сбросить"), b -> HudLayout.reset())
                .bounds(cx + 8, by, 100, 20).build());
    }

    @Override
    public void removed() {
        InterfaceHud.setEditMode(false);
        HudLayout.save();
    }

    private void close() {
        Minecraft.getInstance().setScreenAndShow(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------- отрисовка ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY,
                                   float partialTick) {
        // Затемнение игры под редактором.
        gg.fill(0, 0, this.width, this.height, DIM);

        InterfaceHud.renderEditor(gg);
        refreshTargets();

        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        this.hoverTarget = targetAt(mx, my);

        // Рамки и уголки.
        for (Target t : this.targets.values()) {
            if (t.bounds == null) {
                continue;
            }
            float x = t.bounds[0];
            float y = t.bounds[1];
            float w = t.bounds[2];
            float h = t.bounds[3];
            boolean active = t == this.hoverTarget || t == this.dragTarget;
            int frame = active ? Style.accent() : 0x3DFFFFFF;

            // Тонкая рамка.
            Render2D.rect(gg, x, y, w, 1.5f, frame);
            Render2D.rect(gg, x, y + h - 1.5f, w, 1.5f, frame);
            Render2D.rect(gg, x, y, 1.5f, h, frame);
            Render2D.rect(gg, x + w - 1.5f, y, 1.5f, h, frame);

            // Уголок-ресайз.
            float hx = x + w - HANDLE;
            float hy = y + h - HANDLE;
            Render2D.round(gg, hx, hy, HANDLE, HANDLE, 3.0f,
                    active ? Style.accent() : 0x99FFFFFF);
            Render2D.rect(gg, hx + 3.0f, hy + HANDLE - 5.0f, HANDLE - 6.0f, 1.5f,
                    0xFF141414);
            Render2D.rect(gg, hx + HANDLE - 5.0f, hy + 3.0f, 1.5f, HANDLE - 6.0f,
                    0xFF141414);

            // Подпись — только при наведении.
            if (active) {
                HFont label = Fonts.label();
                float tw = label.width(t.label);
                float tx = Math.max(2.0f, x);
                float ty = y - 14.0f;
                if (ty < 2.0f) {
                    ty = y + h + 3.0f;
                }
                gg.fill((int) tx - 3, (int) ty - 2, (int) (tx + tw + 3),
                        (int) (ty + 10), 0xB0141414);
                label.draw(gg, t.label, tx, ty, Style.WHITE);
            }
        }

        // Подсказка.
        HFont hint = Fonts.label();
        String text = moduleOn()
                ? "Тянуть — двигать   •   уголок — размер   •   колесо — масштаб   •   ALT + ЛКМ в игре — двигать   •   ESC — готово"
                : "Включите модуль Interface, чтобы настроить HUD";
        float tw = hint.width(text);
        hint.draw(gg, text, (this.width - tw) / 2.0f, this.height - 58.0f, Style.WHITE);

        super.extractRenderState(gg, mouseX, mouseY, partialTick);
    }

    private boolean moduleOn() {
        Interface module = Interface.instance();
        return module != null && module.isEnabled();
    }

    private void refreshTargets() {
        if (!moduleOn()) {
            this.targets.clear();
            this.dragTarget = null;
            return;
        }
        Map<HudLayout.Element, float[]> bounds = InterfaceHud.boundsSnapshot();
        java.util.Set<String> active = new java.util.HashSet<>();
        for (HudLayout.Element el : HudLayout.Element.values()) {
            float[] b = bounds.get(el);
            if (b == null) {
                continue;
            }
            Target t = this.targets.get(el.name());
            if (t == null) {
                t = new Target(el.name(), el.label(), el);
                this.targets.put(el.name(), t);
            }
            t.bounds = b;
            active.add(el.name());
        }
        if (Interface.showMedia2D()) {
            float[] b = InterfaceHud.media2DBounds();
            if (b != null) {
                Target t = this.targets.get("MEDIA2D");
                if (t == null) {
                    t = new Target("MEDIA2D", "Media 2D", null);
                    this.targets.put("MEDIA2D", t);
                }
                t.bounds = b;
                active.add("MEDIA2D");
            }
        }
        // Убираем пропавшие.
        this.targets.keySet().removeIf(key -> !active.contains(key));
        if (this.dragTarget != null && !this.targets.containsValue(this.dragTarget)) {
            this.dragTarget = null;
        }
    }

    /** Поиск цели сверху вниз (последняя отрисованная — верхняя). */
    private Target targetAt(double mx, double my) {
        Target[] arr = this.targets.values().toArray(new Target[0]);
        for (int i = arr.length - 1; i >= 0; i--) {
            Target t = arr[i];
            float[] b = t.bounds;
            if (b != null && mx >= b[0] && mx <= b[0] + b[2]
                    && my >= b[1] && my <= b[1] + b[3]) {
                return t;
            }
        }
        return null;
    }

    private boolean onHandle(Target t, double mx, double my) {
        float[] b = t.bounds;
        return mx >= b[0] + b[2] - HANDLE && mx <= b[0] + b[2]
                && my >= b[1] + b[3] - HANDLE && my <= b[1] + b[3];
    }

    // ---------------- мышь ----------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        Target t = targetAt(mx, my);
        if (t == null) {
            return super.mouseClicked(event, doubleClick);
        }
        this.dragTarget = t;
        if (onHandle(t, mx, my)) {
            // Ресайз с уголка.
            this.dragMove = false;
            float dx = (float) mx - t.bounds[0];
            float dy = (float) my - t.bounds[1];
            this.resizeStartDist = Math.max(1.0f, (float) Math.hypot(dx, dy));
            this.resizeBase = t.scaleBase();
        } else {
            // Свободное перемещение.
            this.dragMove = true;
            this.grabDX = (float) mx - t.bounds[0];
            this.grabDY = (float) my - t.bounds[1];
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragTarget == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        if (this.dragMove) {
            this.dragTarget.moveTo((float) mx - this.grabDX, (float) my - this.grabDY,
                    Render2D.screenWidth(), Render2D.screenHeight());
        } else {
            float dx = (float) mx - this.dragTarget.bounds[0];
            float dy = (float) my - this.dragTarget.bounds[1];
            float dist = Math.max(1.0f, (float) Math.hypot(dx, dy));
            this.dragTarget.resizeTo(this.resizeBase, dist / this.resizeStartDist);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragTarget != null) {
            this.dragTarget = null;
            this.dragMove = false;
            HudLayout.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Target t = targetAt(Render2D.mouseX(), Render2D.mouseY());
        if (t == null || scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        t.scaleBy(scrollY > 0 ? 1.1f : 1.0f / 1.1f);
        return true;
    }
}
