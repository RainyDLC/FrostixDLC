package rtx.kimiko.api.ui.mainmenu;

import mods.acountswiher.ru.vidtu.ias.screen.AccountScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.ui.BaseScreen;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.render2d.Render2D;

import java.util.List;

/** Reference-inspired, directly customizable main menu. */
public final class MainMenuScreen extends BaseScreen {
    /** Kept for callers compiled against the former Kotlin companion API. */
    public static final Companion Companion = new Companion();
    public static final class Companion {
        public MainMenuScreen instance() { return MainMenuScreen.instance(); }
        public boolean isOpen() { return MainMenuScreen.isOpen(); }
    }
    private static MainMenuScreen instance;
    private static final String[] ACTIONS = {
        "Одиночная игра", "Сетевая игра", "Сменить аккаунт", "Настройки", "Выйти из игры"
    };
    private MainMenuTab tab = MainMenuTab.PLAY;
    private boolean editing;
    private int dragging = -1;
    private float offsetX, offsetY;
    private float width, height, buttonW, buttonH, gearX, gearY;
    private long lastFrame;
    private float appear;

    private MainMenuScreen() { super(Text.literal("RainyDLC")); }
    public static MainMenuScreen instance() {
        if (instance == null) instance = new MainMenuScreen();
        return instance;
    }
    public static boolean isOpen() { return MinecraftClient.getInstance().currentScreen instanceof MainMenuScreen; }
    public MainMenuTab tab() { return tab; }
    public void select(MainMenuTab next) { if (next != null) tab = next; }

    @Override protected void init() {
        MenuLayout.load();
        lastFrame = System.nanoTime();
        appear = 0;
        editing = false;
        dragging = -1;
    }

    private void metrics() {
        width = Position.Companion.screenWidth();
        height = Position.Companion.screenHeight();
        MenuTheme.updateMetrics(height);
        buttonW = Math.min(185, Math.max(120, width * 0.22f));
        buttonH = Math.max(26, Math.min(34, height * 0.065f));
        gearX = width - 47;
        gearY = 17;
    }

    private float bx(int i) { return 25 + MenuLayout.x(i) * Math.max(0, width - buttonW - 50); }
    private float by(int i) { return 65 + MenuLayout.y(i) * Math.max(0, height - buttonH - 95); }
    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override protected void renderScreen(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        metrics();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        appear = Math.min(1, appear + dt * 2.8f);
        float mx = Position.Companion.mouseX();
        float my = Position.Companion.mouseY();
        MenuBackdrop.render(width, height, mx, my, dt, 1, appear);

        // Header positioned cleanly right above the buttons
        float headerX = bx(0);
        float headerY = Math.max(22, by(0) - 36);
        Fonts.BOLD.draw("RainyDLC", headerX, headerY, 13f, 0xFFFFFFFF);
        Fonts.MEDIUM.draw("Твоя игра. Твои правила.", headerX, headerY + 16, 5.2f, 0xCCA6BEDA);

        for (int i = 0; i < ACTIONS.length; i++) drawAction(i, mx, my);

        // Settings gear button with frosted glass
        boolean gearHover = hit(mx, my, gearX, gearY, 32, 32);
        Render2D.blur(gearX, gearY, 32, 32, 9, 14f);
        Render2D.rect(gearX, gearY, 32, 32, 9, editing ? 0x993364B0 : (gearHover ? 0x55253850 : 0x33121F2F));
        Render2D.outline(gearX, gearY, 32, 32, 9, 0.8f, gearHover ? 0x996FA8E8 : 0x446FA8E8);
        Fonts.I2.msdf(MainMenuTab.SETTINGS.glyph(), gearX + 8, gearY + 8, 16, 0xFFFFFFFF);

        Fonts.MEDIUM.draw("RAINYDLC  /  1.21.11", 24, height - 18, 4.8f, 0x88AAB9CB);
        if (editing) drawEditor(mx, my);
    }

    private void drawAction(int i, float mx, float my) {
        float x = bx(i), y = by(i);
        boolean hover = hit(mx, my, x, y, buttonW, buttonH);

        // Frosted matte translucent glass
        Render2D.blur(x, y, buttonW, buttonH, 8f, 15f);
        int glassFill = hover ? 0x4D2A4060 : 0x2A152336;
        if (i == 0 && !hover) glassFill = 0x3F244570; // subtle hint for play button
        else if (i == 0 && hover) glassFill = 0x663360A0;

        Render2D.rect(x, y, buttonW, buttonH, 8f, glassFill);

        int outlineColor = (editing && dragging == i) ? 0xFF99CCFF
            : (hover ? 0x997EAEED : 0x3B628CA8);
        Render2D.outline(x, y, buttonW, buttonH, 8f, 0.85f, outlineColor);

        // Soft left accent pill on hover
        if (hover) {
            Render2D.rect(x + 2, y + 5, 2.5f, buttonH - 10, 1.25f, 0xCC68B1FF);
        }

        float textX = x + (hover ? 16 : 14);
        float textY = y + buttonH * 0.5f - 3.2f;
        Fonts.SEMIBOLD.draw(ACTIONS[i], textX, textY, 6.2f, hover ? 0xFFFFFFFF : 0xEEF3F8FF);

        if (editing) {
            Render2D.rect(x + buttonW - 14, y + buttonH * .5f - 4, 2, 8, 1, 0xFFC2D6F3);
            Render2D.rect(x + buttonW - 10, y + buttonH * .5f - 4, 2, 8, 1, 0xFFC2D6F3);
        }
    }

    private void drawEditor(float mx, float my) {
        float pw = Math.min(235, width * .48f), px = width - pw - 16, py = 58;
        float ph = 191;
        Render2D.blur(px, py, pw, ph, 10, 16f);
        Render2D.rect(px, py, pw, ph, 10, 0x4D142032);
        Render2D.outline(px, py, pw, ph, 10, .8f, 0x556F98C8);
        Fonts.BOLD.draw("Настроить главное меню", px + 13, py + 15, 8, 0xFFF7FAFF);
        Fonts.MEDIUM.draw("Перетаскивай кнопки мышью", px + 13, py + 35, 5.4f, 0xFFADC0DB);
        MenuBackground selected = MenuBackgrounds.selected();
        String name = selected == null ? "Нет фона" : selected.name();
        editorRow(px, py + 55, pw, "Фон: " + truncate(name, 25), mx, my);
        editorRow(px, py + 88, pw, "Папка фото / GIF-видео", mx, my);
        editorRow(px, py + 121, pw, "Обновить фоны", mx, my);
        editorRow(px, py + 154, pw, "Сбросить кнопки", mx, my);
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
    private void editorRow(float x, float y, float w, String label, float mx, float my) {
        boolean hover = hit(mx, my, x + 10, y, w - 20, 25);
        Render2D.blur(x + 10, y, w - 20, 25, 6, 10f);
        Render2D.rect(x + 10, y, w - 20, 25, 6, hover ? 0x66335277 : 0x331C2D44);
        Render2D.outline(x + 10, y, w - 20, 25, 6, 0.7f, hover ? 0x887DAAE0 : 0x336688AA);
        Fonts.MEDIUM.draw(label, x + 19, y + 8, 5.5f, 0xFFE8F2FF);
    }

    @Override public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        metrics();
        float mx = Position.Companion.mouseX(), my = Position.Companion.mouseY();
        if (hit(mx, my, gearX, gearY, 32, 32)) {
            editing = !editing;
            dragging = -1;
            return true;
        }
        if (editing) {
            float pw = Math.min(235, width * .48f), px = width - pw - 16, py = 58;
            for (int row = 0; row < 4; row++) {
                if (hit(mx, my, px + 10, py + 55 + row * 33, pw - 20, 25)) {
                    switch (row) {
                        case 0 -> cycleBackground();
                        case 1 -> MenuBackgrounds.openFolder();
                        case 2 -> MenuBackgrounds.rescan();
                        case 3 -> MenuLayout.reset();
                    }
                    return true;
                }
            }
            // Last painted button receives the drag on overlapping layouts.
            for (int i = ACTIONS.length - 1; i >= 0; i--) {
                if (hit(mx, my, bx(i), by(i), buttonW, buttonH)) {
                    dragging = i;
                    offsetX = mx - bx(i);
                    offsetY = my - by(i);
                    return true;
                }
            }
            return true;
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            if (hit(mx, my, bx(i), by(i), buttonW, buttonH)) {
                activate(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void cycleBackground() {
        List<MenuBackground> all = MenuBackgrounds.all();
        if (all.isEmpty()) return;
        MenuBackground selected = MenuBackgrounds.selected();
        int index = all.indexOf(selected);
        MenuBackgrounds.select(all.get((index + 1) % all.size()));
    }

    private void activate(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (i) {
            case 0 -> mc.setScreen(new SelectWorldScreen(this));
            case 1 -> mc.setScreen(new MultiplayerScreen(this));
            case 2 -> mc.setScreen(new AccountScreen(this));
            case 3 -> mc.setScreen(new OptionsScreen(this, mc.options));
            case 4 -> mc.scheduleStop();
        }
    }

    @Override public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (editing && dragging >= 0) {
            metrics();
            float mx = Position.Companion.mouseX(), my = Position.Companion.mouseY();
            MenuLayout.move(dragging,
                (mx - offsetX - 15) / Math.max(1, width - buttonW - 30),
                (my - offsetY - 58) / Math.max(1, height - buttonH - 88));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }
    @Override public boolean mouseReleased(Click event) {
        if (dragging >= 0) {
            dragging = -1;
            MenuLayout.save();
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyInput event) {
        if (event.key() == 256) {
            if (editing) editing = false;
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean shouldPause() { return false; }
}
