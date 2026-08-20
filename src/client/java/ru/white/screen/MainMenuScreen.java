package ru.white.screen;

import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.*;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Главное меню в морозном (ледяном / cyan) стиле:
 *  - фон: глубокий сине-чёрный градиент с кобальтом и аквамарином,
 *    вспышка полярного сияния с рваными неоновыми всполохами в центре,
 *    морозные узоры (кристаллы инея) по краям экрана;
 *  - кнопки: полированные блоки тёмно-синего льда с градиентной
 *    "замороженной жидкостью" внутри и неоновым контуром Ice Blue;
 *  - внизу слева — консоль крио-лаборатории, внизу справа — подпись
 *    автора со сияющей снежинкой.
 * Логотип клиента не изменён.
 */
public class MainMenuScreen extends Screen implements IMinecraft {

    // ── морозная палитра ────────────────────────────────────────────────
    private static final int ICE_R = 125, ICE_G = 228, ICE_B = 255;   // Ice Blue (контур/акцент)
    private static final int AQUA_R = 64, AQUA_G = 224, AQUA_B = 208; // бирюза (консоль)
    private static final int TXT_R = 222, TXT_G = 240, TXT_B = 255;   // морозно-белый текст

    private static final Identifier MENU_BG = Identifier.of("client", "textures/frame/menu.png");
    private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");
    private static final Identifier SNOWFLAKE_TEX = Identifier.of("client", "textures/particles/snowflake.png");

    /** Кристаллы инея по краям: {X, Y (доли экрана), размер, альфа 0-255, фаза пульсации}. */
    private static final float[][] FROST_SPOTS = {
            {0.030f, 0.14f, 24, 40, 0.0f},
            {0.012f, 0.44f, 17, 28, 1.3f},
            {0.045f, 0.76f, 28, 44, 2.6f},
            {0.968f, 0.20f, 22, 36, 0.7f},
            {0.988f, 0.54f, 16, 26, 2.0f},
            {0.952f, 0.84f, 26, 42, 3.4f},
            {0.10f, 0.968f, 19, 32, 1.0f},
            {0.30f, 0.988f, 14, 22, 2.9f},
            {0.70f, 0.988f, 16, 26, 0.5f},
            {0.89f, 0.965f, 21, 34, 3.9f},
            {0.08f, 0.045f, 17, 30, 1.8f},
            {0.87f, 0.055f, 15, 26, 4.4f},
    };

    public MainMenuScreen() {
        super(Text.literal("MainMenuScreen"));
    }

    public Animation alpha = new Animation();
    private final List<MenuButton> buttons = new ArrayList<>();
    private MenuButton multiplayerButton;
    private MenuButton singleplayerButton;
    private MenuButton settingsButton;
    private MenuButton accountsButton;
    private MenuButton modsButton;
    private MenuButton exitButton;

    boolean exit = false;
    private float scaleFix = 1F;
    double lastMouseX;
    double lastMouseY;
    private final MainMenuShaderRenderer menuShader = new MainMenuShaderRenderer();
    private boolean exitSlideDragging = false;
    private float exitSlideProgress = 0F;

    // язык: маленькая кнопка-переключатель РУС/АНГ в углу экрана
    private float langBtnX, langBtnY, langBtnW, langBtnH;
    private final Animation langHover = new Animation();

    @Override
    protected void init() {
        exit = false;
        alpha.set(0);
        alpha.run(1, 0.5F, Easings.BACK_OUT);

        // применяем сохранённый активный аккаунт при первом показе меню
        ru.white.alt.AltManager.get().bootstrap();

        buildButtons();
    }

    private void buildButtons() {
        buttons.clear();

        singleplayerButton = new MenuButton("Одиночная игра", "Singleplayer",
                "Играть в кампанию и оффлайн-миры", "Play the campaign and offline worlds",
                () -> mc.setScreen(new SelectWorldScreen(this)));
        multiplayerButton = new MenuButton("Сетевая игра", "Multiplayer",
                "Подключиться к мультиплеер-серверам", "Join and play on multiplayer servers",
                () -> mc.setScreen(new MultiplayerScreen(this)));

        buttons.add(multiplayerButton);
        buttons.add(singleplayerButton);

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            modsButton = new MenuButton("Моды", "Mods",
                    "Управление установленными модификациями", "Manage your installed modifications", () -> {
                try {
                    Class<?> modMenuClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                    Screen modsScreen = (Screen) modMenuClass.getConstructor(Screen.class).newInstance(this);
                    mc.setScreen(modsScreen);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            buttons.add(modsButton);
        } else {
            modsButton = null;
        }

        accountsButton = new MenuButton("Аккаунты", "Accounts",
                "Переключение и управление аккаунтами", "Switch and manage your accounts",
                () -> mc.setScreen(new AltManagerScreen(this)));
        settingsButton = new MenuButton("Настройки", "Settings",
                "Настройки игры и видео", "Adjust game and video options",
                () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        exitButton = new MenuButton("Выход", "Exit",
                "Закрыть игру и выйти на рабочий стол", "Close the game and return to desktop",
                true, () -> mc.scheduleStop());

        buttons.add(settingsButton);
        buttons.add(accountsButton);
        buttons.add(exitButton);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        closeCheck();
        mouseX = (int) (mouseX / scaleFix);
        mouseY = (int) (mouseY / scaleFix);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        alpha.update();

        renderOverlay(context);
    }

    public void renderOverlay(DrawContext context) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        float alphaVal = this.alpha.get();
        float time = (System.currentTimeMillis() % 100000L) / 1000F;

        float parallaxStrength = 0.07F; // Сила смещения
        float offsetX = (screenWidth / 2F - (float) lastMouseX) * parallaxStrength;
        float offsetY = (screenHeight / 2F - (float) lastMouseY) * parallaxStrength;

        float bgScale = 1.1F;
        float bgW = screenWidth * bgScale;
        float bgH = screenHeight * bgScale;
        float bgX = (screenWidth - bgW) / 2F + offsetX;
        float bgY = (screenHeight - bgH) / 2F + offsetY;

        // фон-картинка в ледяной тонировке
        RenderUtil.Images.texture(MENU_BG, bgX, bgY, bgW, bgH, ColorUtil.getColor(170, 205, 255, alphaVal));

        ScreenBlur.capture(2);
        RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, alphaVal,
                ColorUtil.getColor(5, 12, 30, alphaVal * 0.35F));
        ScreenBlur.capture(4);

        // морозная атмосфера: градиент, полярное сияние, всполохи, иней
        drawFrostBackground(screenWidth, screenHeight, alphaVal, time);

        // ЛОГОТИП — без изменений
        Fonts.nightix_2.drawCentered("G", screenWidth / 2,
                screenHeight * 0.32F + 30 - 30 * alphaVal, 16, ColorUtil.replAlpha(ColorUtil.client(), alphaVal));

        Fonts.sf_regular.drawCentered("Nightix @LuminasMinecraft", screenWidth / 2,
                screenHeight * 0.36F + 30 - 30 * alphaVal, 12, ColorUtil.getColor(255, alphaVal));

        for (MenuButton b : buttons) b.update(lastMouseX, lastMouseY);
        drawMenuButtons(screenWidth, screenHeight, alphaVal);

        // вспомогательный UI морозной темы
        drawCryoConsole(screenWidth, screenHeight, alphaVal, time);
        drawSignature(screenWidth, screenHeight, alphaVal, time);
        drawLanguageButton(screenWidth, alphaVal);

        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    // ── морозный фон ────────────────────────────────────────────────────

    private void drawFrostBackground(int sw, int sh, float a, float time) {
        // 1) градиент: кобальт/аквамарин вверху → иссиня-чёрный внизу
        int tl = ColorUtil.getColor(14, 38, 84, a * 0.55F);
        int tr = ColorUtil.getColor(8, 30, 66, a * 0.55F);
        int br = ColorUtil.getColor(3, 9, 24, a * 0.75F);
        int bl = ColorUtil.getColor(4, 12, 30, a * 0.75F);
        Draw.gradientRect(0, 0, sw, sh, new int[]{tl, tr, br, bl}, 0);

        float cx = sw / 2F;
        float cy = sh * 0.30F;

        // 2) яркая вспышка полярного сияния в центре (под логотипом)
        RenderUtil.Images.texture(GLOW_TEX, cx - 190, cy - 150, 380, 300,
                ColorUtil.getColor(80, 200, 255, a * 0.30F));
        RenderUtil.Images.texture(GLOW_TEX, cx - 100, cy - 80, 200, 160,
                ColorUtil.getColor(140, 240, 255, a * 0.35F));
        RenderUtil.Images.texture(GLOW_TEX, cx - 46, cy - 40, 92, 80,
                ColorUtil.getColor(200, 250, 255, a * 0.40F));

        // 3) морозные узоры: полупрозрачные кристаллы инея по краям экрана
        for (float[] s : FROST_SPOTS) {
            float pulse = 0.7F + 0.3F * (float) Math.sin(time * 1.4F + s[4]);
            float size = s[2];
            RenderUtil.Images.texture(SNOWFLAKE_TEX, s[0] * sw - size / 2F, s[1] * sh - size / 2F, size, size,
                    ColorUtil.getColor(175, 228, 255, a * (s[3] / 255F) * pulse));
        }

        // 6) виньетка: лёд темнеет к верхнему и нижнему краю
        int edge = ColorUtil.getColor(3, 8, 20, a * 0.8F);
        int edgeT = ColorUtil.getColor(3, 8, 20, 0F);
        Draw.gradientRect(0, 0, sw, 64, new int[]{edge, edge, edgeT, edgeT}, 0);
        Draw.gradientRect(0, sh - 80, sw, 80, new int[]{edgeT, edgeT, edge, edge}, 0);
    }

    // ── консоль крио-лаборатории (низ слева) ────────────────────────────

    private void drawCryoConsole(int sw, int sh, float a, float time) {
        float w = 160, h = 48;
        float x = 12, y = sh - h - 12;

        // панель приборов с бирюзовой подсветкой
        RenderUtil.Blur.blur(x, y, w, h, 1, 9, ColorUtil.getColor(6, 16, 38, a * 0.60F));
        int top = ColorUtil.getColor(16, 56, 88, a * 0.35F);
        int bot = ColorUtil.getColor(4, 12, 30, a * 0.55F);
        Draw.gradientRect(x, y, w, h, new int[]{top, top, bot, bot}, 9);
        Draw.outline(x, y, w, h, 0.8F, ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.55F), 9);
        Draw.glow(x, y, w, h, ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.30F), 9, 4F, 0.45F);

        float blink = 0.45F + 0.55F * (float) Math.abs(Math.sin(time * 2.4F));
        int teal = ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.95F);
        int dim = ColorUtil.getColor(150, 200, 210, a * 0.55F);

        // заголовок + мигающий индикатор
        Fonts.sf_regular.draw("CRYO CONSOLE", x + 10, y + 7, 5.5F, teal);
        Draw.rect(x + w - 16, y + 7.5F, 4.5F, 4.5F, ColorUtil.getColor(80, 255, 230, a * blink), 2.2F);

        // разделитель
        Draw.rect(x + 10, y + 16.5F, w - 20, 0.5F, ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.25F));

        // строка 1: FPS + версия
        Fonts.sf_regular.draw("FPS " + mc.getCurrentFps(), x + 10, y + 20, 5, dim);
        String ver = "VER 1.21.11";
        Fonts.sf_regular.draw(ver, x + w - 10 - Fonts.sf_regular.getWidth(ver, 5), y + 20, 5, dim);

        // строка 2: время + статус
        String t = new SimpleDateFormat("HH:mm").format(new Date());
        Fonts.sf_regular.draw("TIME " + t, x + 10, y + 29, 5, dim);
        String status = "SYSTEM ONLINE";
        Fonts.sf_regular.draw(status, x + w - 10 - Fonts.sf_regular.getWidth(status, 5), y + 29, 5,
                ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * (0.5F + 0.4F * blink)));
    }

    // ── подпись автора со сияющей снежинкой (низ справа) ────────────────

    private void drawSignature(int sw, int sh, float a, float time) {
        String text = "Nightix  @LuminasMinecraft";
        float tw = Fonts.sf_regular.getWidth(text, 6);
        float flake = 8F, gap = 5F;
        float x = sw - tw - gap - flake - 12;
        float y = sh - 20;

        float pulse = 0.65F + 0.35F * (float) Math.sin(time * 2.0F);

        Fonts.sf_regular.draw(text, x, y, 6, ColorUtil.getColor(210, 232, 255, a * 0.7F));

        float fx = x + tw + gap;
        // ореол за снежинкой
        RenderUtil.Images.texture(GLOW_TEX, fx - 7, y - 7, flake + 14, flake + 14,
                ColorUtil.getColor(120, 220, 255, a * 0.50F * pulse));
        // сама снежинка
        RenderUtil.Images.texture(SNOWFLAKE_TEX, fx, y, flake, flake,
                ColorUtil.getColor(190, 240, 255, a * (0.75F + 0.25F * pulse)));
    }

    // ── кнопки ──────────────────────────────────────────────────────────

    private void drawMenuButtons(int screenWidth, int screenHeight, float alphaVal) {
        updateExitSlideProgress();

        float gap = 4;
        float panelWidth = Math.min(220, screenWidth - 36F);
        float cardWidth = (panelWidth - gap) / 2F;
        float cardHeight = 25;
        float totalCardsWidth = cardWidth * 2F + gap;

        float smallGap = 4;
        int smallCount = modsButton == null ? 2 : 3;
        float smallWidth = (totalCardsWidth - smallGap * (smallCount - 1)) / smallCount;
        float smallHeight = 25;

        float exitWidth = Math.min(150F, totalCardsWidth * 0.65F);
        float exitHeight = 25;

        // --- ВЫЧИСЛЕНИЕ ВЫСОТЫ БЛОКА ДЛЯ ОТЦЕНТРОВКИ ---
        float totalBlockHeight = cardHeight + gap + smallHeight + gap + exitHeight;

        float startX = screenWidth / 2F - totalCardsWidth / 2F;
        float startY = screenHeight / 2F - totalBlockHeight / 2F;
        float appearY = (1F - alphaVal) * 34F;

        float currentY = startY + appearY;

        // 1 Ряд: Основные кнопки
        multiplayerButton.drawHero(startX, currentY, cardWidth, cardHeight, alphaVal, 0);
        singleplayerButton.drawHero(startX + cardWidth + gap, currentY, cardWidth, cardHeight, alphaVal, 1);

        currentY += cardHeight + gap;

        // 2 Ряд: Мелкие кнопки
        float smallX = startX;
        settingsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "l");
        smallX += smallWidth + smallGap;
        accountsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "m");
        if (modsButton != null) {
            smallX += smallWidth + smallGap;
            modsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "n");
        }

        currentY += smallHeight + gap;

        // 3 Ряд: Кнопка выхода
        float exitX = screenWidth / 2F - exitWidth / 2F;
        exitButton.drawExit(exitX, currentY, exitWidth, exitHeight, alphaVal, exitSlideProgress, exitSlideDragging);
    }

    private void updateExitSlideProgress() {
        if (!exitSlideDragging || exitButton == null) return;

        float trackX = exitButton.getLastX() + 5F;
        float trackW = Math.max(1F, exitButton.getLastW() - 34F);
        exitSlideProgress = Math.max(0F, Math.min(1F, ((float) lastMouseX - trackX) / trackW));
    }

    private void drawLanguageButton(int screenWidth, float alphaVal) {
        float w = 25, h = 15;
        float bx = screenWidth - w - 12, by = 12;
        langBtnX = bx; langBtnY = by; langBtnW = w; langBtnH = h;

        boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, bx, by, w, h);
        langHover.update();
        langHover.run(hov ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        float hp = langHover.get();

        int accent = ColorUtil.getColor(ICE_R, ICE_G, ICE_B);
        RenderUtil.Blur.blur(bx, by, w, h, 1, 5, ColorUtil.getColor(8, 20, 46, alphaVal * (0.4F + 0.15F * hp)));
        Draw.outline(bx, by, w, h, 0.6F,
                ColorUtil.replAlpha(accent, (int) (alphaVal * 255 * (0.3F + hp * 0.4F))), 5);

        int textColor = ColorUtil.replAlpha(
                ColorUtil.interpolateColor(ColorUtil.getColor(170, 200, 215, 1F), accent, hp), alphaVal);
        Fonts.sf_regular.drawCentered(ru.white.lang.Lang.tag(), bx + w / 2F, by + h / 2F - 3.8F, 6, textColor);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);
        int button = click.button();

        if (button == 0) {
            if (MathUtil.isHovered(mouseX, mouseY, langBtnX, langBtnY, langBtnW, langBtnH)) {
                ru.white.lang.Lang.toggle();
                return true;
            }
            for (MenuButton btn : buttons) {
                if (btn.isHovered()) {
                    if (btn == exitButton) {
                        exitSlideDragging = true;
                        updateExitSlideProgress();
                        return true;
                    }
                    btn.onClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (exitSlideDragging) {
            lastMouseX = click.x() / scaleFix;
            lastMouseY = click.y() / scaleFix;
            updateExitSlideProgress();
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (exitSlideDragging) {
            lastMouseX = click.x() / scaleFix;
            lastMouseY = click.y() / scaleFix;
            updateExitSlideProgress();
            exitSlideDragging = false;

            if (exitSlideProgress >= 0.90F) {
                exitSlideProgress = 1F;
                exitButton.onClick();
            } else {
                exitSlideProgress = 0F;
            }
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() {
        if (!exit) {
            exit = false;
        }
        return false;
    }

    private void closeCheck() {
        if (exit && alpha.isFinished()) {
            close();
            alpha.run(0, 0.5F, Easings.SINE_OUT);
            exit = false;
        }
    }

    private static class MenuButton {
        private final String textRu, textEn;
        private final String descRu, descEn;
        private final Runnable action;
        private final boolean danger;
        private final Animation hoverAnim = new Animation();
        private boolean hovered;

        private float lastX, lastY, lastW, lastH;

        public MenuButton(String textRu, String textEn, String descRu, String descEn, Runnable action) {
            this(textRu, textEn, descRu, descEn, false, action);
        }

        public MenuButton(String textRu, String textEn, String descRu, String descEn, boolean danger, Runnable action) {
            this.textRu = textRu;
            this.textEn = textEn;
            this.descRu = descRu;
            this.descEn = descEn;
            this.danger = danger;
            this.action = action;
            this.hoverAnim.set(0);
        }

        private String text() { return textRu; }
        private String description() { return ru.white.lang.Lang.pick(descRu, descEn); }

        public float getWidth() {
            Font font = Fonts.sf_regular;
            return Math.max(font.getWidth(text(), 8), font.getWidth(description(), 6)) + 34;
        }

        public float getHoverValue() {
            return hoverAnim.get();
        }

        public void update(double mouseX, double mouseY) {
            hovered = MathUtil.isHovered((float) mouseX, (float) mouseY, lastX, lastY, lastW, lastH);
            hoverAnim.update();
            hoverAnim.run(hovered ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        }

        private void rememberBounds(float x, float y, float width, float height) {
            lastX = x;
            lastY = y;
            lastW = width;
            lastH = height;
        }

        public float getLastX() {
            return lastX;
        }

        public float getLastW() {
            return lastW;
        }

        /**
         * Ледяная панель: блюр + градиент "замороженной жидкости" +
         * неоновый контур Ice Blue + Arctic Glow при наведении.
         */
        private void drawFrostPanel(float x, float y, float width, float height, float globalAlpha, float hp, int accent) {
            float radius = 9F;

            RenderUtil.Blur.blur(x, y, width, height, 1, radius,
                    ColorUtil.getColor(8, 20, 46, globalAlpha * (0.50F + hp * 0.15F)));

            int top = ColorUtil.getColor(30, 72, 130, globalAlpha * (0.14F + hp * 0.14F));
            int bottom = ColorUtil.getColor(6, 16, 40, globalAlpha * (0.22F + hp * 0.16F));
            Draw.gradientRect(x, y, width, height, new int[]{top, top, bottom, bottom}, radius);

            Draw.outline(x, y, width, height, 0.7F,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.30F + hp * 0.50F))), radius);

            if (hp > 0.01F) {
                Draw.glow(x, y, width, height,
                        ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (hp * 0.45F))),
                        radius, 5F, hp * 0.7F);
            }
        }

        public void drawHero(float x, float y, float width, float height, float globalAlpha, int variant) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            int accent = ColorUtil.getColor(ICE_R, ICE_G, ICE_B);
            drawFrostPanel(x, y, width, height, globalAlpha, hp, accent);

            int textColor = ColorUtil.getColor(TXT_R, TXT_G, TXT_B, globalAlpha * (0.35F + hp * 0.65F));
            Fonts.sf_regular.draw(text(), x + 12F, y + 7.8F, 7, textColor);
            Fonts.icon.drawCentered(variant == 0 ? "j" : "k", x + width - 15, y + 9.25F, 6,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.35F + hp * 0.65F))));
        }

        public void drawCompact(float x, float y, float width, float height, float globalAlpha, String icon) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            int accent = ColorUtil.getColor(ICE_R, ICE_G, ICE_B);
            drawFrostPanel(x, y, width, height, globalAlpha, hp, accent);

            int textColor = ColorUtil.getColor(TXT_R, TXT_G, TXT_B, globalAlpha * (0.35F + hp * 0.65F));
            Fonts.sf_regular.draw(text(), x + 12F, y + 7.8F, 7, textColor);
            Fonts.icon.drawCentered(icon, x + width - 15, y + 9.25F, 6,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.25F + hp * 0.75F))));
        }

        public void drawExit(float x, float y, float width, float height, float globalAlpha, float slideProgress, boolean dragging) {
            rememberBounds(x, y, width, height);

            float hp = hoverAnim.get();
            int accent = ColorUtil.getColor(ICE_R, ICE_G, ICE_B);
            drawFrostPanel(x, y, width, height, globalAlpha, hp, accent);

            float progress = Math.max(0F, Math.min(1F, slideProgress));

            // заполнение дорожки при сдвиге — волна ледяной энергии
            if (progress > 0.01F) {
                int pTop = ColorUtil.getColor(90, 220, 255, globalAlpha * (0.06F + progress * 0.16F));
                int pBot = ColorUtil.getColor(40, 130, 200, globalAlpha * (0.02F + progress * 0.06F));
                Draw.gradientRect(x + 2, y + 2, (width - 4) * progress, height - 4,
                        new int[]{pTop, pTop, pBot, pBot}, 7);
            }

            float knobSize = height - 8F;
            float knobX = x + 5F + (width - knobSize - 10F) * progress;

            // ледяной кубик-ползунок
            Draw.gradientRect(knobX, y + 4F, knobSize, knobSize, new int[]{
                    ColorUtil.getColor(170, 235, 255, globalAlpha * (0.25F + progress * 0.45F)),
                    ColorUtil.getColor(120, 200, 250, globalAlpha * (0.25F + progress * 0.45F)),
                    ColorUtil.getColor(50, 120, 190, globalAlpha * (0.25F + progress * 0.45F)),
                    ColorUtil.getColor(90, 180, 240, globalAlpha * (0.25F + progress * 0.45F))}, 4);
            Draw.outline(knobX, y + 4F, knobSize, knobSize, 0.6F,
                    ColorUtil.getColor(160, 235, 255, globalAlpha * (0.35F + progress * 0.50F)), 4);

            Fonts.icon.drawCentered("i", knobX + knobSize / 2F, y + height / 2F - 3, 7,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.25F + hp * 0.2F + progress * 0.55F))));
            Fonts.sf_regular.drawCentered(ru.white.lang.Lang.pick("Сдвиньте, чтобы выйти", "Сдвиньте, чтобы выйти"), x + width / 2F + 8F,
                    y + height / 2F - 4.4F, 7,
                    ColorUtil.getColor(TXT_R, TXT_G, TXT_B, globalAlpha * (0.10F + hp * 0.25F - progress * 0.15F)));
        }

        public void draw(float x, float y, float width, float height, float globalAlpha) {
            float hp = hoverAnim.get();
            int accent = danger ? ColorUtil.getColor(255, 120, 130) : ColorUtil.getColor(ICE_R, ICE_G, ICE_B);

            rememberBounds(x, y, width, height);
            drawFrostPanel(x, y, width, height, globalAlpha, hp, accent);

            Font font = Fonts.sf_regular;
            int titleColor = ColorUtil.getColor(TXT_R, TXT_G, TXT_B, globalAlpha * (0.35F + hp * 0.65F));

            String text = text();
            float titleX = x + width / 2F - font.getWidth(text, 8) / 2F;
            font.draw(text, titleX, y + 12, 8, titleColor);
        }

        public boolean isHovered() {
            return hovered;
        }

        public void onClick() {
            if (action != null) {
                action.run();
            }
        }
    }
}
