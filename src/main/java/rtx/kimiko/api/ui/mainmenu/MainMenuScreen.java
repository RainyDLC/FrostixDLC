package rtx.kimiko.api.ui.mainmenu;

import mods.acountswiher.ru.vidtu.ias.screen.AccountScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.ui.BaseScreen;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.sounds.SoundManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainMenuScreen extends BaseScreen {
    private static MainMenuScreen INSTANCE;

    // Actions
    private static final String[] ACTION_TITLES = {
        "Сетевая игра",
        "Одиночная игра",
        "Сменить аккаунт",
        "Настройки",
        "Выйти"
    };

    // Vector Icon Glyphs from Fonts.MAINMENU
    // 'a' = Globe (Multiplayer)
    // 'b' = Gamepad (Singleplayer)
    // 'c' = Account / Profile
    // 'd' = Settings / Gear
    // 'e' = Power / Exit
    private static final String[] ACTION_ICONS_GLYPH = {
        "a",
        "b",
        "c",
        "d",
        "e"
    };

    // Animation & State
    private long lastFrameTime = System.nanoTime();
    private float appearAnim = 0.0f;
    private final float[] buttonHoverAnim = new float[5];
    private float gearHoverAnim = 0.0f;
    private float modalAnim = 0.0f;
    private boolean settingsOpen = false;
    private int settingsTab = 0; // 0 = Background, 1 = Layout, 2 = Effects

    // Free Dragging
    private int draggingButton = -1;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    // Layout Metrics
    private float buttonW = 168.0f;
    private float buttonH = 29.0f;
    private float splitW = 81.0f; // for button 3 & 4

    public MainMenuScreen() {
        super(Text.literal("Frostix Main Menu"));
    }

    public static MainMenuScreen instance() {
        if (INSTANCE == null) {
            INSTANCE = new MainMenuScreen();
        }
        return INSTANCE;
    }

    @Override
    protected void init() {
        super.init();
        lastFrameTime = System.nanoTime();
    }

    @Override
    protected void renderScreen(@NotNull DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.08f, (now - lastFrameTime) / 1_000_000_000.0f);
        lastFrameTime = now;

        appearAnim = Math.min(1.0f, appearAnim + dt * 3.2f);
        float mx = Position.Companion.mouseX();
        float my = Position.Companion.mouseY();

        MenuConfig cfg = MenuConfig.get();

        // 1. Render Cinematic Backdrop
        MenuBackdrop.render(graphics, width, height, mx, my, dt);

        // 2. Top-Left Player Profile Card
        renderPlayerCard(mx, my, dt);

        // 3. Top-Right Atmospheric Time Widget & Gear Button
        renderTopRightWidgets(mx, my, dt, cfg);

        // 4. Left Hero Title & Subtitle (Matching reference)
        renderHeroBlock(cfg);

        // 5. Main Buttons (Frosted Matte Glass)
        renderButtons(mx, my, dt, cfg);

        // 6. Free Move Banner / Grid
        if (cfg.freeMove) {
            renderFreeMoveOverlay(mx, my);
        }

        // 7. Bottom-Left Footer
        renderFooter();

        // 8. Settings Modal Dialog (If open)
        if (settingsOpen || modalAnim > 0.01f) {
            modalAnim += ((settingsOpen ? 1.0f : 0.0f) - modalAnim) * Math.min(1.0f, dt * 10.0f);
            renderSettingsModal(mx, my, cfg);
        }
    }

    /* =========================================================================
     * TOP-LEFT: Player Profile Card
     * ========================================================================= */
    private void renderPlayerCard(float mx, float my, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float cardX = 24.0f;
        float cardY = 18.0f;
        float cardW = 120.0f;
        float cardH = 32.0f;
        boolean hover = hit(mx, my, cardX, cardY, cardW, cardH);

        // Frosted Matte Acrylic Glass
        int fill = hover ? 0x65162436 : 0x420E1825;
        Render2D.rect(cardX, cardY, cardW, cardH, 8.0f, fill);
        Render2D.rect(cardX + 2.0f, cardY + 1.0f, cardW - 4.0f, 1.0f, 0.5f, 0x1AFFFFFF);
        int outlineCol = hover ? 0x9068B1FF : 0x2AFFFFFF;
        Render2D.outline(cardX, cardY, cardW, cardH, 8.0f, 0.85f, outlineCol);

        // Player Head
        try {
            java.util.UUID uuid = (mc.getSession() != null && mc.getSession().getUuidOrNull() != null) ? mc.getSession().getUuidOrNull() : java.util.UUID.randomUUID();
            Identifier skinId = DefaultSkinHelper.getSkinTextures(uuid).body().texturePath();
            String skinTex = skinId.toString();
            float headX = cardX + 6.0f;
            float headY = cardY + 6.0f;
            float headSize = 20.0f;
            if (Render2D.imageReady(skinTex)) {
                // Base layer
                Render2D.imageUvNearest(skinTex, headX, headY, headSize, 4.0f, 4.0f, 4.0f, 4.0f, 0.5f, 0.125f, 0.125f, 0.25f, 0.25f, 0xFFFFFFFF);
                // Hat / outer layer
                Render2D.imageUvNearest(skinTex, headX, headY, headSize, 4.0f, 4.0f, 4.0f, 4.0f, 0.5f, 0.625f, 0.125f, 0.75f, 0.25f, 0xFFFFFFFF);
            } else {
                Render2D.rect(headX, headY, headSize, headSize, 4.0f, 0xFF4A6B8A);
            }
        } catch (Throwable ignored) {
            Render2D.rect(cardX + 6.0f, cardY + 6.0f, 20.0f, 20.0f, 4.0f, 0xFF4A6B8A);
        }

        // Subtitle "Текущий аккаунт"
        Fonts.REGULAR.draw("Текущий аккаунт", cardX + 32.0f, cardY + 7.5f, 4.6f, 0xCC9EC0E2);
        // Username
        String name = mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "Player";
        Fonts.SEMIBOLD.draw(name, cardX + 32.0f, cardY + 18.0f, 6.2f, 0xFFFFFFFF);
    }

    /* =========================================================================
     * TOP-RIGHT: Atmospheric Time Widget & Gear Button
     * ========================================================================= */
    private void renderTopRightWidgets(float mx, float my, float dt, MenuConfig cfg) {
        float rightMargin = 24.0f;
        float gearSize = 32.0f;
        float gearX = width - rightMargin - gearSize;
        float gearY = 18.0f;

        // Gear Button (Settings)
        boolean gearHover = hit(mx, my, gearX, gearY, gearSize, gearSize);
        gearHoverAnim += ((gearHover || settingsOpen ? 1.0f : 0.0f) - gearHoverAnim) * Math.min(1.0f, dt * 10.0f);

        int gearFill = settingsOpen ? 0x992563EB : (gearHover ? 0x651C3048 : 0x420E1825);
        Render2D.rect(gearX, gearY, gearSize, gearSize, 8.0f, gearFill);
        Render2D.rect(gearX + 2.0f, gearY + 1.0f, gearSize - 4.0f, 1.0f, 0.5f, 0x1AFFFFFF);
        int gearOutline = settingsOpen || gearHover ? 0xCC68B1FF : 0x2AFFFFFF;
        Render2D.outline(gearX, gearY, gearSize, gearSize, 8.0f, 0.85f, gearOutline);

        // Gear Icon (Vector MSDF glyph 'd' with rotation)
        float rotDeg = gearHoverAnim * 50.0f;
        Fonts.MAINMENU.draw("d", gearX + 11.5f, gearY + 11.5f, 9.0f, 0xFFFFFFFF, rotDeg, gearX + 16.0f, gearY + 16.0f);

        // Time Widget
        if (cfg.showClock) {
            float widgetW = 166.0f;
            float widgetH = 32.0f;
            float widgetX = gearX - widgetW - 10.0f;
            float widgetY = 18.0f;

            Render2D.rect(widgetX, widgetY, widgetW, widgetH, 8.0f, 0x420E1825);
            Render2D.rect(widgetX + 2.0f, widgetY + 1.0f, widgetW - 4.0f, 1.0f, 0.5f, 0x1AFFFFFF);
            Render2D.outline(widgetX, widgetY, widgetW, widgetH, 8.0f, 0.85f, 0x2AFFFFFF);

            // Clock
            String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            Fonts.BOLD.draw(timeStr, widgetX + 10.0f, widgetY + 11.0f, 8.2f, 0xFFFFFFFF);

            // Day phase indicators: "Восход  День  Закат  Ночь"
            int currentPhase = MenuBackdrop.getRealTimePhase();

            String[] phases = {"Восход", "День", "Закат", "Ночь"};
            float phaseStartX = widgetX + 56.0f;
            float phaseSpacing = 27.0f;

            for (int p = 0; p < phases.length; p++) {
                boolean active = (p == currentPhase);
                float px = phaseStartX + p * phaseSpacing;
                if (active) {
                    Render2D.rect(px - 3.0f, widgetY + 8.0f, 25.0f, 16.0f, 4.0f, 0x553B82F6);
                    Render2D.outline(px - 3.0f, widgetY + 8.0f, 25.0f, 16.0f, 4.0f, 0.7f, 0xAA60A5FA);
                }
                Fonts.MEDIUM.draw(phases[p], px, widgetY + 12.0f, 4.6f, active ? 0xFFFFFFFF : 0x7598AFC7);
            }
        }
    }

    /* =========================================================================
     * LEFT HERO: Badge, Title & Subtitle (From screenshot reference)
     * ========================================================================= */
    private void renderHeroBlock(MenuConfig cfg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float startX = getButtonX(0);
        float startY = Math.max(55.0f, getButtonY(0) - 58.0f);

        // Uppercase greeting badge
        String playerName = (mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "Player").toUpperCase();
        String greeting = (cfg.greetingText != null ? cfg.greetingText : "РАД ВИДЕТЬ") + ", " + playerName;
        Fonts.BOLD.draw(greeting, startX, startY, 5.0f, 0xFF3E8EFF);

        // Big Headline
        Fonts.BOLD.draw("Сборка собрана и прогрета —", startX, startY + 14.0f, 12.0f, 0xFFFFFFFF);
        Fonts.BOLD.draw("это Frostix Client.", startX, startY + 28.0f, 12.0f, 0xFFFFFFFF);

        // Subtitle
        Fonts.REGULAR.draw("Куда сегодня — на сервер или в свой мир?", startX, startY + 44.0f, 5.5f, 0xBBA7C4E0);
    }

    /* =========================================================================
     * BUTTONS: Frosted Matte Translucent Glass
     * ========================================================================= */
    private void renderButtons(float mx, float my, float dt, MenuConfig cfg) {
        for (int i = 0; i < 5; i++) {
            float bx = getButtonX(i);
            float by = getButtonY(i);
            float bw = getButtonW(i);
            float bh = buttonH;

            boolean hover = hit(mx, my, bx, by, bw, bh);
            buttonHoverAnim[i] += ((hover ? 1.0f : 0.0f) - buttonHoverAnim[i]) * Math.min(1.0f, dt * 11.0f);
            float anim = buttonHoverAnim[i];

            // Slide right slightly on hover (+3px)
            float renderX = bx + anim * 3.0f;

            // 1. MATTE TRANSLUCENT FILL
            int fill;
            if (i == 0) {
                // Multiplayer button: featured with vibrant royal azure glass from screenshot!
                int baseBlue = 0xCC1E5CE0;
                int hoverBlue = 0xEE2A6DF5;
                fill = lerpColor(baseBlue, hoverBlue, anim);
            } else {
                // Other buttons: frosted matte translucent acrylic
                int baseGlass = 0x48101C2B;
                int hoverGlass = 0x751E3249;
                fill = lerpColor(baseGlass, hoverGlass, anim);
            }
            Render2D.rect(renderX, by, bw, bh, 7.0f, fill);

            // 2. SPECULAR TOP BEVEL HIGHLIGHT (Creates authentic glass look)
            int specularCol = (i == 0) ? 0x35FFFFFF : 0x18FFFFFF;
            Render2D.rect(renderX + 2.0f, by + 1.0f, bw - 4.0f, 1.0f, 0.5f, specularCol);

            // 3. FROSTED GLASS OUTLINE
            int baseOutline = (i == 0) ? 0x7770A8FF : 0x2AFFFFFF;
            int hoverOutline = (i == 0) ? 0xFFB0D5FF : 0xCC68B1FF;
            if (cfg.freeMove && draggingButton == i) {
                hoverOutline = 0xFF00E5FF;
            }
            int outline = lerpColor(baseOutline, hoverOutline, anim);
            Render2D.outline(renderX, by, bw, bh, 7.0f, 0.85f, outline);

            // 4. LEFT ACCENT GLOW PILL ON HOVER
            if (anim > 0.02f) {
                int pillAlpha = Math.max(0, Math.min(255, (int) (anim * 240.0f)));
                int pillColor = (pillAlpha << 24) | 0x64ABFF;
                Render2D.rect(renderX + 2.0f, by + 4.5f, 2.5f, bh - 9.0f, 1.25f, pillColor);
            }

            // 5. VECTOR MSDF GLYPH ICON (Crisp, sharp, no background texture bleed!)
            String glyph = ACTION_ICONS_GLYPH[i];
            float iconX = renderX + 9.0f;
            float iconY = by + 11.0f;
            int iconColor = (i == 0) ? 0xFFFFFFFF : lerpColor(0xCCB8D4EE, 0xFFFFFFFF, anim);
            Fonts.MAINMENU.draw(glyph, iconX, iconY, 6.8f, iconColor);

            // 6. TEXT
            float textX = renderX + 24.0f;
            float textY = by + bh * 0.5f - 3.0f;
            int textColor = (i == 0) ? 0xFFFFFFFF : lerpColor(0xEEEDF4FA, 0xFFFFFFFF, anim);
            Fonts.SEMIBOLD.draw(ACTION_TITLES[i], textX, textY, 5.6f, textColor);

            // Free move drag handle indicator
            if (cfg.freeMove) {
                Render2D.rect(renderX + bw - 11.0f, by + bh * 0.5f - 4.0f, 1.8f, 8.0f, 0.9f, 0x88FFFFFF);
                Render2D.rect(renderX + bw - 7.0f, by + bh * 0.5f - 4.0f, 1.8f, 8.0f, 0.9f, 0x88FFFFFF);
            }
        }
    }

    /* =========================================================================
     * BOTTOM-LEFT: Footer
     * ========================================================================= */
    private void renderFooter() {
        float fy = height - 22.0f;
        Fonts.MEDIUM.draw("Frostix Client • Minecraft 1.21.4", 24.0f, fy, 4.9f, 0x88AABFD4);
        Fonts.REGULAR.draw("Не является официальным продуктом Mojang", 24.0f, fy + 9.0f, 4.0f, 0x5588A0B8);
    }

    /* =========================================================================
     * FREE MOVE OVERLAY
     * ========================================================================= */
    private void renderFreeMoveOverlay(float mx, float my) {
        float bw = Math.min(360.0f, width - 40.0f);
        float bx = (width - bw) * 0.5f;
        float by = 18.0f;

        Render2D.rect(bx, by, bw, 28.0f, 6.0f, 0x88142336);
        Render2D.rect(bx + 2.0f, by + 1.0f, bw - 4.0f, 1.0f, 0.5f, 0x22FFFFFF);
        Render2D.outline(bx, by, bw, 28.0f, 6.0f, 0.85f, 0xCC5EA8FF);

        Fonts.SEMIBOLD.draw("✏️ РЕЖИМ ПЕРЕМЕЩЕНИЯ", bx + 12.0f, by + 10.0f, 5.6f, 0xFF4DA6FF);
        Fonts.REGULAR.draw("Перетаскивай кнопки мышью", bx + 118.0f, by + 10.0f, 5.2f, 0xFFD8E7F8);

        // Reset button
        float resetW = 60.0f;
        float resetX = bx + bw - resetW - 8.0f;
        float resetY = by + 5.0f;
        boolean rHover = hit(mx, my, resetX, resetY, resetW, 18.0f);
        Render2D.rect(resetX, resetY, resetW, 18.0f, 4.0f, rHover ? 0x88385C88 : 0x55284060);
        Render2D.outline(resetX, resetY, resetW, 18.0f, 4.0f, 0.6f, rHover ? 0xFF7BB4F8 : 0x557BB4F8);
        Fonts.MEDIUM.draw("Сбросить", resetX + 11.0f, resetY + 5.5f, 4.9f, 0xFFFFFFFF);
    }

    /* =========================================================================
     * SETTINGS MODAL DIALOG
     * ========================================================================= */
    private void renderSettingsModal(float mx, float my, MenuConfig cfg) {
        float mw = Math.min(270.0f, width * 0.48f);
        float mh = 250.0f;
        float modalX = width - mw - 18.0f;
        float modalY = 56.0f;

        // Frosted Glass Window
        Render2D.rect(modalX, modalY, mw, mh, 10.0f, 0xE5101C2B);
        Render2D.rect(modalX + 2.0f, modalY + 1.0f, mw - 4.0f, 1.0f, 0.5f, 0x22FFFFFF);
        Render2D.outline(modalX, modalY, mw, mh, 10.0f, 0.85f, 0x556EA6E6);

        // Modal Header
        Fonts.BOLD.draw("Настройки главного меню", modalX + 14.0f, modalY + 14.0f, 7.5f, 0xFFF0F6FF);

        // Close (X) button
        float closeX = modalX + mw - 22.0f;
        float closeY = modalY + 11.0f;
        boolean closeHover = hit(mx, my, closeX, closeY, 14.0f, 14.0f);
        Fonts.BOLD.draw("✕", closeX + 2.0f, closeY + 2.0f, 7.0f, closeHover ? 0xFFFF6060 : 0x88FFFFFF);

        // Tab Strip
        float tabY = modalY + 30.0f;
        float tabW = (mw - 28.0f) / 3.0f;
        String[] tabs = {"Фон", "Кнопки", "Эффекты"};
        for (int t = 0; t < 3; t++) {
            float tx = modalX + 14.0f + t * tabW;
            boolean active = (settingsTab == t);
            boolean tHover = hit(mx, my, tx, tabY, tabW - 4.0f, 18.0f);

            int tabFill = active ? 0x882A64B0 : (tHover ? 0x44203652 : 0x22142236);
            Render2D.rect(tx, tabY, tabW - 4.0f, 18.0f, 4.0f, tabFill);
            if (active) {
                Render2D.outline(tx, tabY, tabW - 4.0f, 18.0f, 4.0f, 0.7f, 0x996DA6EA);
            }
            float textOff = (tabW - 4.0f - Fonts.MEDIUM.width(tabs[t], 5.0f)) * 0.5f;
            Fonts.MEDIUM.draw(tabs[t], tx + textOff, tabY + 5.5f, 5.0f, active ? 0xFFFFFFFF : 0x99B8D0E8);
        }

        // Tab Content
        float contentY = modalY + 56.0f;
        if (settingsTab == 0) {
            renderTabBackground(modalX, contentY, mw, mx, my, cfg);
        } else if (settingsTab == 1) {
            renderTabLayout(modalX, contentY, mw, mx, my, cfg);
        } else {
            renderTabEffects(modalX, contentY, mw, mx, my, cfg);
        }
    }

    private void renderTabBackground(float mx, float cy, float mw, float mouseX, float mouseY, MenuConfig cfg) {
        Fonts.MEDIUM.draw("Выбор заднего фона:", mx + 14.0f, cy, 5.2f, 0xCCADC6E2);

        // Cycle through presets button
        float btnY = cy + 10.0f;
        boolean pnlHover = hit(mouseX, mouseY, mx + 14.0f, btnY, mw - 28.0f, 24.0f);
        Render2D.rect(mx + 14.0f, btnY, mw - 28.0f, 24.0f, 5.0f, pnlHover ? 0x652C4C74 : 0x40182B42);
        Render2D.outline(mx + 14.0f, btnY, mw - 28.0f, 24.0f, 5.0f, 0.7f, pnlHover ? 0x996DA6EA : 0x336DA6EA);

        String currentName = MenuBackdrop.PRESET_NAMES[Math.max(0, Math.min(MenuBackdrop.PRESET_NAMES.length - 1, cfg.backgroundPreset))];
        if (cfg.backgroundPreset == 6 && cfg.customBackgroundName != null && !cfg.customBackgroundName.isBlank()) {
            currentName = "Свой: " + cfg.customBackgroundName;
        }
        Fonts.SEMIBOLD.draw(truncate(currentName, 26), mx + 22.0f, btnY + 8.0f, 5.4f, 0xFFFFFFFF);
        Fonts.BOLD.draw(">", mx + mw - 26.0f, btnY + 7.5f, 6.0f, 0xCC6DA6EA);

        // If Auto preset is active, show the currently detected real-life phase!
        if (cfg.backgroundPreset == 0) {
            int phase = MenuBackdrop.getRealTimePhase();
            String[] phaseDescriptions = {
                "Утро (Восход) • 05:00-11:00",
                "День (Солнечно) • 11:00-18:00",
                "Закат (Золотой час) • 18:00-22:00",
                "Ночь (Звезды) • 22:00-05:00"
            };
            Fonts.REGULAR.draw("Сейчас в реале: " + phaseDescriptions[phase], mx + 14.0f, btnY + 28.0f, 4.4f, 0xFF60A5FA);
        }

        // Open folder button
        float folderBtnY = btnY + (cfg.backgroundPreset == 0 ? 38.0f : 30.0f);
        boolean fHover = hit(mouseX, mouseY, mx + 14.0f, folderBtnY, mw - 28.0f, 22.0f);
        Render2D.rect(mx + 14.0f, folderBtnY, mw - 28.0f, 22.0f, 5.0f, fHover ? 0x552C4C74 : 0x33182B42);
        Render2D.outline(mx + 14.0f, folderBtnY, mw - 28.0f, 22.0f, 5.0f, 0.7f, fHover ? 0x996DA6EA : 0x336DA6EA);
        Fonts.MEDIUM.draw("📁 Открыть папку .minecraft/frostix", mx + 22.0f, folderBtnY + 7.5f, 5.2f, 0xFFFFFFFF);

        // Darkness / vignette slider
        float darkY = folderBtnY + 30.0f;
        Fonts.MEDIUM.draw("Затемнение фона: " + (int) (cfg.backgroundDarkness * 100.0f) + "%", mx + 14.0f, darkY, 5.2f, 0xCCADC6E2);

        float barW = mw - 28.0f;
        float darkBarY = darkY + 10.0f;
        Render2D.rect(mx + 14.0f, darkBarY, barW, 6.0f, 3.0f, 0x441A2A3E);
        float darkProgress = cfg.backgroundDarkness / 0.8f;
        Render2D.rect(mx + 14.0f, darkBarY, barW * darkProgress, 6.0f, 3.0f, 0xEE4D94FF);
        Render2D.circle(mx + 14.0f + barW * darkProgress, darkBarY + 3.0f, 5.0f, 0xFFFFFFFF);
    }

    private void renderTabLayout(float mx, float cy, float mw, float mouseX, float mouseY, MenuConfig cfg) {
        // Toggle Free Move Mode
        float moveY = cy;
        boolean mHover = hit(mouseX, mouseY, mx + 14.0f, moveY, mw - 28.0f, 24.0f);
        int moveFill = cfg.freeMove ? 0x882A64B0 : (mHover ? 0x552C4C74 : 0x33182B42);
        Render2D.rect(mx + 14.0f, moveY, mw - 28.0f, 24.0f, 5.0f, moveFill);
        Render2D.outline(mx + 14.0f, moveY, mw - 28.0f, 24.0f, 5.0f, 0.7f, cfg.freeMove ? 0xFF68B1FF : 0x4468B1FF);

        String toggleText = "Свободное перемещение: " + (cfg.freeMove ? "ВКЛ" : "ВЫКЛ");
        Fonts.SEMIBOLD.draw(toggleText, mx + 22.0f, moveY + 8.0f, 5.4f, 0xFFFFFFFF);

        Fonts.REGULAR.draw("При включении можно двигать", mx + 14.0f, moveY + 30.0f, 4.8f, 0x99ADC6E2);
        Fonts.REGULAR.draw("кнопки мышью по всему экрану.", mx + 14.0f, moveY + 40.0f, 4.8f, 0x99ADC6E2);

        // Preset Layouts: Left (Reference), Center, Right
        float layoutY = moveY + 58.0f;
        Fonts.MEDIUM.draw("Готовые схемы расположения:", mx + 14.0f, layoutY, 5.2f, 0xCCADC6E2);

        float pBtnY = layoutY + 12.0f;
        String[] layouts = {"Слева (Референс)", "По центру", "Справа"};
        for (int l = 0; l < layouts.length; l++) {
            float ly = pBtnY + l * 24.0f;
            boolean lHover = hit(mouseX, mouseY, mx + 14.0f, ly, mw - 28.0f, 20.0f);
            Render2D.rect(mx + 14.0f, ly, mw - 28.0f, 20.0f, 4.0f, lHover ? 0x552C4C74 : 0x2A182B42);
            Render2D.outline(mx + 14.0f, ly, mw - 28.0f, 20.0f, 4.0f, 0.6f, lHover ? 0x886DA6EA : 0x336DA6EA);
            Fonts.MEDIUM.draw(layouts[l], mx + 22.0f, ly + 6.5f, 5.0f, 0xFFFFFFFF);
        }

        // Reset button
        float resetY = pBtnY + 76.0f;
        boolean rHover = hit(mouseX, mouseY, mx + 14.0f, resetY, mw - 28.0f, 20.0f);
        Render2D.rect(mx + 14.0f, resetY, mw - 28.0f, 20.0f, 4.0f, rHover ? 0x883C2A3A : 0x442C1A2A);
        Render2D.outline(mx + 14.0f, resetY, mw - 28.0f, 20.0f, 4.0f, 0.7f, rHover ? 0xFFFF7070 : 0x55FF7070);
        Fonts.MEDIUM.draw("Сбросить все позиции", mx + 22.0f, resetY + 6.5f, 5.0f, 0xFFFFB4B4);
    }

    private void renderTabEffects(float mx, float cy, float mw, float mouseX, float mouseY, MenuConfig cfg) {
        // Particles toggle
        float y = cy;
        boolean partHover = hit(mouseX, mouseY, mx + 14.0f, y, mw - 28.0f, 22.0f);
        Render2D.rect(mx + 14.0f, y, mw - 28.0f, 22.0f, 5.0f, cfg.particlesEnabled ? 0x662A64B0 : (partHover ? 0x44203652 : 0x22142236));
        Render2D.outline(mx + 14.0f, y, mw - 28.0f, 22.0f, 5.0f, 0.7f, cfg.particlesEnabled ? 0xCC68B1FF : 0x3368B1FF);
        Fonts.SEMIBOLD.draw("Светлячки / Частицы: " + (cfg.particlesEnabled ? "ВКЛ" : "ВЫКЛ"), mx + 22.0f, y + 7.5f, 5.2f, 0xFFFFFFFF);

        // Particle count slider
        float countY = y + 28.0f;
        Fonts.MEDIUM.draw("Количество частиц: " + cfg.particleCount, mx + 14.0f, countY, 5.0f, 0xCCADC6E2);
        float barY = countY + 10.0f;
        float barW = mw - 28.0f;
        Render2D.rect(mx + 14.0f, barY, barW, 6.0f, 3.0f, 0x441A2A3E);
        float pProgress = (cfg.particleCount - 15.0f) / 135.0f;
        Render2D.rect(mx + 14.0f, barY, barW * pProgress, 6.0f, 3.0f, 0xEE4D94FF);
        Render2D.circle(mx + 14.0f + barW * pProgress, barY + 3.0f, 5.0f, 0xFFFFFFFF);

        // Parallax toggle
        float paraY = barY + 18.0f;
        boolean paraHover = hit(mouseX, mouseY, mx + 14.0f, paraY, mw - 28.0f, 22.0f);
        Render2D.rect(mx + 14.0f, paraY, mw - 28.0f, 22.0f, 5.0f, cfg.mouseParallax ? 0x662A64B0 : (paraHover ? 0x44203652 : 0x22142236));
        Render2D.outline(mx + 14.0f, paraY, mw - 28.0f, 22.0f, 5.0f, 0.7f, cfg.mouseParallax ? 0xCC68B1FF : 0x3368B1FF);
        Fonts.SEMIBOLD.draw("Параллакс мыши: " + (cfg.mouseParallax ? "ВКЛ" : "ВЫКЛ"), mx + 22.0f, paraY + 7.5f, 5.2f, 0xFFFFFFFF);

        // Clock widget toggle
        float clockY = paraY + 28.0f;
        boolean clkHover = hit(mouseX, mouseY, mx + 14.0f, clockY, mw - 28.0f, 22.0f);
        Render2D.rect(mx + 14.0f, clockY, mw - 28.0f, 22.0f, 5.0f, cfg.showClock ? 0x662A64B0 : (clkHover ? 0x44203652 : 0x22142236));
        Render2D.outline(mx + 14.0f, clockY, mw - 28.0f, 22.0f, 5.0f, 0.7f, cfg.showClock ? 0xCC68B1FF : 0x3368B1FF);
        Fonts.SEMIBOLD.draw("Виджет времени и суток: " + (cfg.showClock ? "ВКЛ" : "ВЫКЛ"), mx + 22.0f, clockY + 7.5f, 5.2f, 0xFFFFFFFF);
    }

    /* =========================================================================
     * INPUT HANDLING: Mouse & Keyboard
     * ========================================================================= */
    @Override
    public boolean mouseClicked(@NotNull Click event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        float mx = Position.Companion.mouseX();
        float my = Position.Companion.mouseY();

        MenuConfig cfg = MenuConfig.get();

        // 1. Gear button click (toggle settings)
        float gearSize = 32.0f;
        float gearX = width - 24.0f - gearSize;
        float gearY = 18.0f;
        if (hit(mx, my, gearX, gearY, gearSize, gearSize)) {
            settingsOpen = !settingsOpen;
            SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
            return true;
        }

        // 2. Settings modal click
        if (settingsOpen) {
            float mw = Math.min(270.0f, width * 0.48f);
            float mh = 250.0f;
            float modalX = width - mw - 18.0f;
            float modalY = 56.0f;

            // Close button (X)
            if (hit(mx, my, modalX + mw - 22.0f, modalY + 11.0f, 14.0f, 14.0f)) {
                settingsOpen = false;
                SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                return true;
            }

            // Tab bar
            float tabY = modalY + 30.0f;
            float tabW = (mw - 28.0f) / 3.0f;
            for (int t = 0; t < 3; t++) {
                float tx = modalX + 14.0f + t * tabW;
                if (hit(mx, my, tx, tabY, tabW - 4.0f, 18.0f)) {
                    settingsTab = t;
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
            }

            // Tab contents click
            float contentY = modalY + 56.0f;
            if (settingsTab == 0) {
                // Background preset cycle button
                float btnY = contentY + 10.0f;
                if (hit(mx, my, modalX + 14.0f, btnY, mw - 28.0f, 24.0f)) {
                    cycleBackgroundPreset(cfg);
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Open folder button
                float folderBtnY = btnY + (cfg.backgroundPreset == 0 ? 38.0f : 30.0f);
                if (hit(mx, my, modalX + 14.0f, folderBtnY, mw - 28.0f, 22.0f)) {
                    MenuConfig.openBackgroundsFolder();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Darkness slider
                float darkY = folderBtnY + 30.0f;
                float barW = mw - 28.0f;
                float darkBarY = darkY + 10.0f;
                if (hit(mx, my, modalX + 14.0f, darkBarY - 4.0f, barW, 14.0f)) {
                    float p = Math.max(0.0f, Math.min(1.0f, (mx - (modalX + 14.0f)) / barW));
                    cfg.backgroundDarkness = p * 0.8f;
                    cfg.save();
                    return true;
                }
            } else if (settingsTab == 1) {
                // Free move toggle
                float moveY = contentY;
                if (hit(mx, my, modalX + 14.0f, moveY, mw - 28.0f, 24.0f)) {
                    cfg.freeMove = !cfg.freeMove;
                    cfg.save();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Layout presets
                float pBtnY = moveY + 70.0f;
                if (hit(mx, my, modalX + 14.0f, pBtnY, mw - 28.0f, 20.0f)) {
                    // Left
                    applyPresetLayout(0);
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                } else if (hit(mx, my, modalX + 14.0f, pBtnY + 24.0f, mw - 28.0f, 20.0f)) {
                    // Center
                    applyPresetLayout(1);
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                } else if (hit(mx, my, modalX + 14.0f, pBtnY + 48.0f, mw - 28.0f, 20.0f)) {
                    // Right
                    applyPresetLayout(2);
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Reset positions
                float resetY = pBtnY + 76.0f;
                if (hit(mx, my, modalX + 14.0f, resetY, mw - 28.0f, 20.0f)) {
                    cfg.resetLayout();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
            } else {
                // Effects tab
                float y = contentY;
                if (hit(mx, my, modalX + 14.0f, y, mw - 28.0f, 22.0f)) {
                    cfg.particlesEnabled = !cfg.particlesEnabled;
                    cfg.save();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Particle count bar
                float barY = y + 38.0f;
                float barW = mw - 28.0f;
                if (hit(mx, my, modalX + 14.0f, barY - 4.0f, barW, 14.0f)) {
                    float p = Math.max(0.0f, Math.min(1.0f, (mx - (modalX + 14.0f)) / barW));
                    cfg.particleCount = (int) (15.0f + p * 135.0f);
                    cfg.save();
                    return true;
                }
                // Parallax toggle
                float paraY = barY + 18.0f;
                if (hit(mx, my, modalX + 14.0f, paraY, mw - 28.0f, 22.0f)) {
                    cfg.mouseParallax = !cfg.mouseParallax;
                    cfg.save();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
                // Clock toggle
                float clockY = paraY + 28.0f;
                if (hit(mx, my, modalX + 14.0f, clockY, mw - 28.0f, 22.0f)) {
                    cfg.showClock = !cfg.showClock;
                    cfg.save();
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    return true;
                }
            }

            // Click inside modal shouldn't click elements underneath
            if (hit(mx, my, modalX, modalY, mw, mh)) {
                return true;
            }
        }

        // 3. Player Card Click -> Account screen
        if (hit(mx, my, 24.0f, 18.0f, 120.0f, 32.0f)) {
            SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
            MinecraftClient.getInstance().setScreen(new AccountScreen(this));
            return true;
        }

        // 4. Free move overlay reset button
        if (cfg.freeMove) {
            float bw = Math.min(360.0f, width - 40.0f);
            float bx = (width - bw) * 0.5f;
            float by = 18.0f;
            float resetW = 60.0f;
            float resetX = bx + bw - resetW - 8.0f;
            float resetY = by + 5.0f;
            if (hit(mx, my, resetX, resetY, resetW, 18.0f)) {
                cfg.resetLayout();
                SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                return true;
            }
        }

        // 5. Main Buttons click / drag start
        for (int i = 0; i < 5; i++) {
            float bx = getButtonX(i);
            float by = getButtonY(i);
            float bw = getButtonW(i);
            float bh = buttonH;

            if (hit(mx, my, bx, by, bw, bh)) {
                if (cfg.freeMove) {
                    draggingButton = i;
                    dragOffsetX = mx - bx;
                    dragOffsetY = my - by;
                    return true;
                } else {
                    SoundManager.playSound(SoundManager.BUTTON_CLICK, 1.0f, 1.0f);
                    activateButton(i);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NotNull Click event, double dragX, double dragY) {
        MenuConfig cfg = MenuConfig.get();
        if (cfg.freeMove && draggingButton >= 0) {
            float mx = Position.Companion.mouseX();
            float my = Position.Companion.mouseY();
            float newX = mx - dragOffsetX;
            float newY = my - dragOffsetY;

            // Normalize coordinates
            float normX = Math.max(0.01f, Math.min(0.95f, newX / Math.max(1.0f, width)));
            float normY = Math.max(0.01f, Math.min(0.95f, newY / Math.max(1.0f, height)));

            cfg.buttonPositions[draggingButton][0] = normX;
            cfg.buttonPositions[draggingButton][1] = normY;
            cfg.hasCustomPositions = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NotNull Click event) {
        if (draggingButton >= 0) {
            draggingButton = -1;
            MenuConfig.get().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NotNull KeyInput event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsOpen) {
                settingsOpen = false;
                return true;
            }
            if (MenuConfig.get().freeMove) {
                MenuConfig.get().freeMove = false;
                MenuConfig.get().save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void activateButton(int index) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (index) {
            case 0 -> mc.setScreen(new MultiplayerScreen(this));
            case 1 -> mc.setScreen(new SelectWorldScreen(this));
            case 2 -> mc.setScreen(new AccountScreen(this));
            case 3 -> mc.setScreen(new OptionsScreen(this, mc.options));
            case 4 -> mc.scheduleStop();
        }
    }

    private void cycleBackgroundPreset(MenuConfig cfg) {
        List<String> customs = MenuConfig.listCustomBackgrounds();
        int maxPresets = customs.isEmpty() ? (MenuBackdrop.PRESET_COUNT - 1) : MenuBackdrop.PRESET_COUNT;
        cfg.backgroundPreset = (cfg.backgroundPreset + 1) % maxPresets;
        if (cfg.backgroundPreset == (MenuBackdrop.PRESET_COUNT - 1) && !customs.isEmpty()) {
            cfg.customBackgroundName = customs.get(0);
        }
        MenuBackdrop.invalidate();
        cfg.save();
    }

    private void applyPresetLayout(int layout) {
        MenuConfig cfg = MenuConfig.get();
        cfg.hasCustomPositions = true;
        float baseW = width;
        float baseH = height;

        float startX;
        float startY = 0.44f;

        if (layout == 0) {
            // Left (like reference)
            startX = 38.0f / baseW;
        } else if (layout == 1) {
            // Center
            startX = (baseW - buttonW) * 0.5f / baseW;
        } else {
            // Right
            startX = (baseW - buttonW - 38.0f) / baseW;
        }

        float stepY = (buttonH + 6.0f) / baseH;
        cfg.buttonPositions[0][0] = startX;
        cfg.buttonPositions[0][1] = startY;

        cfg.buttonPositions[1][0] = startX;
        cfg.buttonPositions[1][1] = startY + stepY;

        cfg.buttonPositions[2][0] = startX;
        cfg.buttonPositions[2][1] = startY + stepY * 2.0f;

        cfg.buttonPositions[3][0] = startX;
        cfg.buttonPositions[3][1] = startY + stepY * 3.0f;

        cfg.buttonPositions[4][0] = startX + (splitW + 6.0f) / baseW;
        cfg.buttonPositions[4][1] = startY + stepY * 3.0f;

        cfg.save();
    }

    /* =========================================================================
     * HELPER CALCULATIONS
     * ========================================================================= */
    private float getButtonX(int index) {
        MenuConfig cfg = MenuConfig.get();
        if (cfg.hasCustomPositions && cfg.buttonPositions != null && cfg.buttonPositions[index][0] > 0.001f) {
            return cfg.buttonPositions[index][0] * width;
        }
        // Default Reference Positions (Left aligned hero stack)
        float leftX = 38.0f;
        if (index == 4) {
            return leftX + splitW + 6.0f;
        }
        return leftX;
    }

    private float getButtonY(int index) {
        MenuConfig cfg = MenuConfig.get();
        if (cfg.hasCustomPositions && cfg.buttonPositions != null && cfg.buttonPositions[index][1] > 0.001f) {
            return cfg.buttonPositions[index][1] * height;
        }
        // Default Reference Positions
        float startY = Math.max(145.0f, height * 0.44f);
        float stepY = buttonH + 6.0f;
        return switch (index) {
            case 0 -> startY;
            case 1 -> startY + stepY;
            case 2 -> startY + stepY * 2.0f;
            default -> startY + stepY * 3.0f; // 3 and 4 are split side-by-side
        };
    }

    private float getButtonW(int index) {
        return (index == 3 || index == 4) ? splitW : buttonW;
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int lerpColor(int col1, int col2, float factor) {
        int a1 = (col1 >> 24) & 0xFF;
        int r1 = (col1 >> 16) & 0xFF;
        int g1 = (col1 >> 8) & 0xFF;
        int b1 = col1 & 0xFF;

        int a2 = (col2 >> 24) & 0xFF;
        int r2 = (col2 >> 16) & 0xFF;
        int g2 = (col2 >> 8) & 0xFF;
        int b2 = col2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String truncate(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
