package ru.white.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.white.module.impl.display.Emotions;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.ScreenBlur;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.List;

/**
 * Колесо эмоций в стиле референса: заблюренный фон, сверху табы
 * "Эмоции | Скины", крупное имя выбранной эмоции, вокруг центра —
 * полупрозрачные скруглённые карточки (имя + номер слота) в шахматном
 * порядке по двум радиусам, в центре крутится модель игрока.
 *
 * Наведение на карточку — живое превью позы на модели. Клик — играть.
 * Hold-эмоции играют, пока зажата ЛКМ. Цифры 1-9 — быстрый выбор.
 */
public final class EmoteWheelScreen extends Screen {

    private static final float CARD_W = 118;
    private static final float CARD_H = 46;
    private static final float CARD_R = 14;
    private static final float TAB_W = 176;
    private static final float TAB_H = 26;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<Emote> emotes = Emotes.ALL;
    private final float[] hover;

    private float scaleFix = 1f;
    private int tab = 0;            // 0 — Эмоции, 1 — Скины
    private int selected = -1;
    private boolean holding = false;

    // hit-зоны в клиентских координатах
    private float tabX, tabY;
    private float cx, cy, radius;

    public EmoteWheelScreen() {
        super(Text.literal("Emotions"));
        this.hover = new float[emotes.size()];
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        scaleFix = 2F / (float) mc.getWindow().getScaleFactor();
        float w = mc.getWindow().getScaledWidth() / scaleFix;
        float h = mc.getWindow().getScaledHeight() / scaleFix;
        float mx = (float) mouseX / scaleFix;
        float my = (float) mouseY / scaleFix;
        cx = w / 2f;
        cy = h / 2f;

        // радиус колеса: от меньшей стороны экрана, но не залезает на табы и заголовок
        radius = Math.min(w, h) * 0.34f;
        radius = Math.min(195f, Math.max(112f, radius));
        radius = Math.min(radius, cy - 96f);

        Render2D.beginOverlay();

        // заблюренный мир + затемнение
        ScreenBlur.capture(2);
        RenderUtil.Blur.blur(0, 0, w, h, 1f, ColorUtil.getColor(9, 9, 16, 130));

        if (tab == 0) {
            renderEmotes(context, mouseX, mouseY, mx, my, h);
        } else {
            renderSkins(context, mouseX, mouseY);
        }

        renderTabs(mx, my);

        Render2D.endOverlay();
        super.render(context, mouseX, mouseY, delta);
    }

    // ── вкладка эмоций ───────────────────────────────────────────────────

    private void renderEmotes(DrawContext context, int guiMX, int guiMY, float mx, float my, float h) {
        int n = emotes.size();

        // выбор карточки + живое превью позы на модели
        int sel = holding ? selected : pickCard(mx, my);
        if (sel != selected) {
            selected = sel;
            if (sel >= 0) {
                EmoteManager.beginPreview(emotes.get(sel));
            } else {
                EmoteManager.endPreview();
            }
        }
        for (int i = 0; i < n; i++) {
            hover[i] += ((i == selected ? 1f : 0f) - hover[i]) * 0.14f;
        }

        // подложка под модель + мягкое фиолетовое свечение
        Draw.glow(cx - 62f, cy - 78f, 124f, 156f, ColorUtil.getColorRaw(123, 77, 255, 255),
                60f, 46f, 0.38f, 0.85f);
        RenderUtil.Render2D.rect(cx - 60f, cy - 76f, 120f, 152f,
                ColorUtil.getColor(8, 8, 15, 70), 55f);

        // модель игрока в центре (ванильный рендер в GUI-координатах, следит за курсором)
        if (mc.player != null) {
            int gx1 = Math.round((cx - 45f) * scaleFix);
            int gy1 = Math.round((cy - 78f) * scaleFix);
            int gx2 = Math.round((cx + 45f) * scaleFix);
            int gy2 = Math.round((cy + 62f) * scaleFix);
            InventoryScreen.drawEntity(context, gx1, gy1, gx2, gy2,
                    Math.round(44f * scaleFix), 0.0625f, (float) guiMX, (float) guiMY, mc.player);
        }

        // карточки по кругу: шахматный порядок по двум радиусам
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2.0 + (Math.PI * 2.0 * i / n);
            float rad = (i % 2 == 0) ? radius : radius * 0.74f;
            float px = cx + (float) Math.cos(a) * rad - CARD_W / 2f;
            float py = cy + (float) Math.sin(a) * rad - CARD_H / 2f;

            float p = hover[i];
            float inflate = p * 3f;
            float x = px - inflate;
            float y = py - inflate;
            float cw = CARD_W + inflate * 2f;
            float ch = CARD_H + inflate * 2f;

            Emote emote = emotes.get(i);
            boolean isActive = EmoteManager.active() == emote;

            if (p > 0.02f) {
                Draw.glow(x, y, cw, ch, ColorUtil.getColorRaw(123, 77, 255, 255),
                        CARD_R, 16f, 0.5f * p, 0.8f);
            }

            // фон: тёмная полупрозрачная плашка, при ховере — плотнее
            int bg = mix(ColorUtil.getColor(16, 17, 26, 150),
                    ColorUtil.getColor(24, 25, 40, 232), p);
            RenderUtil.Render2D.rect(x, y, cw, ch, bg, CARD_R);

            // рамка: едва заметная, при ховере — акцентная
            RenderUtil.Render2D.outline(x, y, cw, ch, 0.75f + p * 0.75f,
                    mix(ColorUtil.getColor(255, 255, 255, 34),
                            ColorUtil.getColor(139, 100, 255, 255), p), CARD_R);

            Font font = Fonts.sf_regular;
            int nameColor = isActive
                    ? ColorUtil.getColorRaw(176, 146, 255, 255)
                    : mix(ColorUtil.getColor(232, 232, 242, 235),
                    ColorUtil.getColor(255, 255, 255, 255), p);
            font.drawCentered(emote.name(), x + cw / 2f, y + 12f, 7f, nameColor);
            font.drawCentered(String.valueOf(i + 1), x + cw / 2f, y + 27f, 6f,
                    ColorUtil.getColor(150, 152, 172, 200));
        }

        // крупное имя выбранной эмоции под табами
        String title;
        if (selected >= 0) {
            title = emotes.get(selected).name();
        } else {
            Emote act = EmoteManager.active();
            title = act != null ? act.name() : "Эмоции";
        }
        Fonts.sf_medium.drawCentered(title, cx, tabY + TAB_H + 14f, 10.5f,
                ColorUtil.getColor(244, 244, 250, 235));

        // подсказка внизу
        Fonts.sf_regular.drawCentered(
                holding ? "отпусти ЛКМ, чтобы остановить"
                        : "ЛКМ — играть · удерживай для (Hold) · 1-9 — слоты · ESC — закрыть",
                cx, h - 18f, 7f, ColorUtil.getColor(255, 255, 255, 80));
    }

    // ── вкладка скинов (заглушка) ────────────────────────────────────────

    private void renderSkins(DrawContext context, int guiMX, int guiMY) {
        if (mc.player != null) {
            int gx1 = Math.round((cx - 45f) * scaleFix);
            int gy1 = Math.round((cy - 78f) * scaleFix);
            int gx2 = Math.round((cx + 45f) * scaleFix);
            int gy2 = Math.round((cy + 62f) * scaleFix);
            InventoryScreen.drawEntity(context, gx1, gy1, gx2, gy2,
                    Math.round(44f * scaleFix), 0.0625f, (float) guiMX, (float) guiMY, mc.player);
        }
        Fonts.sf_medium.drawCentered("Скины", cx, cy - 92f, 11f,
                ColorUtil.getColor(244, 244, 250, 235));
        Fonts.sf_regular.drawCentered("Редактор скинов скоро", cx, cy - 74f, 7f,
                ColorUtil.getColor(255, 255, 255, 90));
    }

    // ── табы сверху ──────────────────────────────────────────────────────

    private void renderTabs(float mx, float my) {
        tabX = cx - TAB_W / 2f;
        tabY = 14f;

        float p0 = tabHover(tab == 0, mx, my, 0);
        float p1 = tabHover(tab == 1, mx, my, 1);

        // контейнер
        RenderUtil.Render2D.rect(tabX, tabY, TAB_W, TAB_H,
                ColorUtil.getColor(13, 14, 22, 185), TAB_H / 2f);
        RenderUtil.Render2D.outline(tabX, tabY, TAB_W, TAB_H, 0.75f,
                ColorUtil.getColor(255, 255, 255, 26), TAB_H / 2f);

        // активная половина — фиолетовая пилюля со свечением
        float segX = tabX + (tab == 0 ? 2f : TAB_W / 2f);
        float segW = TAB_W / 2f - 2f;
        float segP = tab == 0 ? p0 : p1;
        if (segP > 0.02f) {
            Draw.glow(segX, tabY + 2f, segW, TAB_H - 4f,
                    ColorUtil.getColorRaw(123, 77, 255, 255), (TAB_H - 4f) / 2f, 10f, 0.35f * segP, 0.8f);
        }
        RenderUtil.Render2D.rect(segX, tabY + 2f, segW, TAB_H - 4f,
                mix(ColorUtil.getColor(123, 77, 255, 200),
                        ColorUtil.getColor(139, 100, 255, 235), segP), (TAB_H - 4f) / 2f);

        Font font = Fonts.sf_regular;
        font.drawCentered("Эмоции", tabX + TAB_W * 0.25f, tabY + 9.5f, 7.5f,
                tab == 0 ? ColorUtil.getColor(255, 255, 255, 245)
                        : ColorUtil.getColor(190, 192, 210, 200));
        font.drawCentered("Скины", tabX + TAB_W * 0.75f, tabY + 9.5f, 7.5f,
                tab == 1 ? ColorUtil.getColor(255, 255, 255, 245)
                        : ColorUtil.getColor(190, 192, 210, 200));
    }

    private float tabHover0, tabHover1;

    private float tabHover(boolean active, float mx, float my, int idx) {
        boolean hov = isHovered(mx, my, tabX + (idx == 0 ? 2f : TAB_W / 2f),
                tabY + 2f, TAB_W / 2f - 2f, TAB_H - 4f);
        float target = active ? 1f : (hov ? 0.35f : 0f);
        float cur = idx == 0 ? tabHover0 : tabHover1;
        cur += (target - cur) * 0.14f;
        if (idx == 0) tabHover0 = cur; else tabHover1 = cur;
        return cur;
    }

    // ── геометрия / ввод ─────────────────────────────────────────────────

    private int pickCard(float mx, float my) {
        int n = emotes.size();
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2.0 + (Math.PI * 2.0 * i / n);
            float rad = (i % 2 == 0) ? radius : radius * 0.74f;
            float px = cx + (float) Math.cos(a) * rad - CARD_W / 2f;
            float py = cy + (float) Math.sin(a) * rad - CARD_H / 2f;
            if (isHovered(mx, my, px, py, CARD_W, CARD_H)) return i;
        }
        return -1;
    }

    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Линейное смешение ARGB-цветов. */
    private static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float mouseClientX(Click click) {
        return (float) click.x() / scaleFix;
    }

    private float mouseClientY(Click click) {
        return (float) click.y() / scaleFix;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = mouseClientX(click);
        float my = mouseClientY(click);

        if (click.button() == 0) {
            // переключение табов
            if (isHovered(mx, my, tabX, tabY, TAB_W, TAB_H)) {
                int newTab = mx < tabX + TAB_W / 2f ? 0 : 1;
                if (newTab != tab) {
                    tab = newTab;
                    selected = -1;
                    EmoteManager.endPreview();
                }
                return true;
            }

            if (tab == 0) {
                // клик по центру — выключить текущую эмоцию
                double dist = Math.hypot(mx - cx, my - cy);
                if (dist < 52f) {
                    if (!holding) EmoteManager.stop();
                    return true;
                }

                int idx = pickCard(mx, my);
                if (idx >= 0) {
                    Emote emote = emotes.get(idx);
                    if (emote.hold()) {
                        // hold-эмоции играют, пока зажата ЛКМ
                        EmoteManager.startHold(emote);
                        holding = true;
                    } else {
                        EmoteManager.endPreview();
                        EmoteManager.play(emote);
                        close();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (holding && click.button() == 0) {
            holding = false;
            EmoteManager.stop();
            close();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // цифры 1-9 — быстрый выбор слота
        if (tab == 0 && input.key() >= GLFW.GLFW_KEY_1 && input.key() <= GLFW.GLFW_KEY_9) {
            int idx = input.key() - GLFW.GLFW_KEY_1;
            if (idx < emotes.size()) {
                Emote emote = emotes.get(idx);
                if (emote.hold()) {
                    EmoteManager.startHold(emote);
                    close();
                } else {
                    EmoteManager.play(emote);
                    close();
                }
                return true;
            }
        }

        // закрытие ванильным биндом или клиентским
        if (Emotions.vanillaKey != null && Emotions.vanillaKey.matchesKey(input)) {
            close();
            return true;
        }
        Emotions module = Instance.get(Emotions.class);
        if (module != null && input.key() == module.wheelKey.get()) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        cleanup();
        super.close();
    }

    @Override
    public void removed() {
        cleanup();
        super.removed();
    }

    private void cleanup() {
        EmoteManager.endPreview();
        if (holding) {
            holding = false;
            EmoteManager.stop();
        }
    }
}
