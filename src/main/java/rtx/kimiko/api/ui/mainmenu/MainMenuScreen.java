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
        "РћРґРёРЅРѕС‡РЅР°СЏ РёРіСЂР°", "РЎРµС‚РµРІР°СЏ РёРіСЂР°", "РЎРјРµРЅРёС‚СЊ Р°РєРєР°СѓРЅС‚", "РќР°СЃС‚СЂРѕР№РєРё", "Р’С‹Р№С‚Рё РёР· РёРіСЂС‹"
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
        buttonW = Math.min(170, Math.max(108, width * 0.22f));
        buttonH = Math.max(23, Math.min(32, height * 0.065f));
        gearX = width - 47;
        gearY = 17;
    }

    private float bx(int i) { return 15 + MenuLayout.x(i) * Math.max(0, width - buttonW - 30); }
    private float by(int i) { return 58 + MenuLayout.y(i) * Math.max(0, height - buttonH - 88); }
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
        // Keep the scene visible; a restrained dark rail gives the actions consistent contrast.
        Render2D.rect(0, 0, Math.min(width * .39f, 280), height, 0,
            0xD90A111C, 0xB309101A, 0x1509101A, 0xB90A111C);
        Render2D.rect(0, 0, width, 1, 0, 0x494E98FA);
        Fonts.SMALL_PIXEL.msdf("RAINYDLC", 24, 25, 13, 0xFFF3F7FF);
        Fonts.MEDIUM.draw("ТВОЯ ИГРА. ТВОИ ПРАВИЛА.", 25, 43, 5.1f, 0xFF9FB4D5);
        float titleSize = Math.min(30, Math.max(14, width * .037f));
        Fonts.SMALL_PIXEL.msdf("RAINYDLC", Math.max(24, width * .37f), height * .145f,
            titleSize, MenuTheme.white(240, appear));
        Render2D.rect(25, height * .355f, 31, 2, 1, 0xFF5C9EFF);
        Fonts.BOLD.draw("Твой мир начинается здесь", 25, height * .375f, 10, 0xFFF6F8FF);
        Fonts.MEDIUM.draw("Выбери путь и отправляйся в приключение.", 25,
            height * .375f + 17, 5.4f, 0xFFC7D4E9);
        for (int i = 0; i < ACTIONS.length; i++) drawAction(i, mx, my);
        Render2D.rect(gearX, gearY, 30, 30, 8,
            editing ? 0xE03364B0 : (hit(mx,my,gearX,gearY,30,30) ? 0xD13C536F : 0xB319273A));
        Fonts.I2.msdf(MainMenuTab.SETTINGS.glyph(), gearX + 8, gearY + 7, 16, 0xFFFFFFFF);
        Fonts.MEDIUM.draw("RAINYDLC  /  1.21.11", 24, height - 18, 4.8f, 0xFFAAB9CB);
        if (editing) drawEditor(mx, my);
    }

    private void drawAction(int i, float mx, float my) {
        float x = bx(i), y = by(i);
        boolean hover = hit(mx, my, x, y, buttonW, buttonH);
        Render2D.rect(x, y, buttonW, buttonH, 7,
            i == 0 ? (hover ? 0xF75F9BFA : 0xE83C77E3) : (hover ? 0xDE304666 : 0xBA182333));
        Render2D.outline(x, y, buttonW, buttonH, 7, .7f,
            editing && dragging == i ? 0xFFD9EDFF : 0x637D9BC5);
        Fonts.SEMIBOLD.draw(ACTIONS[i], x + 12, y + buttonH * .5f - 3.5f, 6,
            0xFFFFFFFF);
        if (editing) {
            Render2D.rect(x + buttonW - 14, y + buttonH * .5f - 4, 2, 8, 1, 0xFFC2D6F3);
            Render2D.rect(x + buttonW - 10, y + buttonH * .5f - 4, 2, 8, 1, 0xFFC2D6F3);
        }
    }

    private void drawEditor(float mx, float my) {
        float pw = Math.min(235, width * .48f), px = width - pw - 16, py = 58;
        float ph = 191;
        Render2D.rect(px, py, pw, ph, 10, 0xE9121D2C);
        Render2D.outline(px, py, pw, ph, 10, .8f, 0x665F88BF);
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
        Render2D.rect(x + 10, y, w - 20, 25, 6,
            hit(mx,my,x+10,y,w-20,25) ? 0xD5364D6D : 0xA925344A);
        Fonts.MEDIUM.draw(label, x + 19, y + 8, 5.5f, 0xFFE8F2FF);
    }

    @Override public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        metrics();
        float mx = Position.Companion.mouseX(), my = Position.Companion.mouseY();
        if (hit(mx, my, gearX, gearY, 30, 30)) {
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
