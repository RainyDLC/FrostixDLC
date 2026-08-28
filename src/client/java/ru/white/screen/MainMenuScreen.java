package ru.white.screen;

import ru.white.Client;
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
import java.util.Iterator;
import java.util.List;

public class MainMenuScreen extends Screen implements IMinecraft {
    private static final int ICE_R = 65, ICE_G = 145, ICE_B = 205;
    private static final int AQUA_R = 74, AQUA_G = 157, AQUA_B = 209;
    private static final int TXT_R = 218, TXT_G = 235, TXT_B = 250;

    private static final Identifier MENU_BG = Identifier.of("client", "textures/frame/mainmenu.png");

    private static final Identifier ICON_LOGO = Identifier.of("client", "textures/icon.png");
    private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");

    private static final Identifier BOLT_TEX = Identifier.of("client", "textures/visuals/particles_2.png");

    private static final Identifier ARROW_TEX = Identifier.of("client", "textures/arrow.png");

    public MainMenuScreen() {
        super(Text.literal("MainMenuScreen"));
        infoAnim.set(1);
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

    private float langBtnX, langBtnY, langBtnW, langBtnH;
    private final Animation langHover = new Animation();

    private boolean infoExpanded = true;
    private final Animation infoAnim = new Animation();
    private float infoBtnX, infoBtnY, infoBtnW, infoBtnH;

    @Override
    protected void init() {
        exit = false;
        alpha.set(0);
        alpha.run(1, 0.5F, Easings.BACK_OUT);

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

        float parallaxStrength = 0.07F;
        float offsetX = (screenWidth / 2F - (float) lastMouseX) * parallaxStrength;
        float offsetY = (screenHeight / 2F - (float) lastMouseY) * parallaxStrength;

        float bgScale = 1.1F;
        float bgW = screenWidth * bgScale;
        float bgH = screenHeight * bgScale;
        float bgX = (screenWidth - bgW) / 2F + offsetX;
        float bgY = (screenHeight - bgH) / 2F + offsetY;

        RenderUtil.Images.texture(MENU_BG, bgX, bgY, bgW, bgH, ColorUtil.getColor(255, 255, 255, alphaVal));

        Draw.rect(0, 0, screenWidth, screenHeight,
                ColorUtil.getColor(5, 12, 30, alphaVal * 0.35F));
        ScreenBlur.capture(4);

        drawRainyBackground(screenWidth, screenHeight, alphaVal, time);

        renderMenuRain(screenWidth, screenHeight, alphaVal,
                Math.max(0.8f, currentScale / 2f));

        float logoSize = 19F;
        RenderUtil.Images.texture(ICON_LOGO, screenWidth / 2F - logoSize / 2F,
                screenHeight * 0.32F + 26F - 30F * alphaVal, logoSize, logoSize,
                ColorUtil.getColor(255, alphaVal));

        Fonts.sf_regular.drawCentered("RainyDLC", screenWidth / 2,
                screenHeight * 0.36F + 30 - 30 * alphaVal, 12, ColorUtil.getColor(255, alphaVal));

        for (MenuButton b : buttons) b.update(lastMouseX, lastMouseY);
        drawMenuButtons(screenWidth, screenHeight, alphaVal);

        drawInfoPanel(screenWidth, screenHeight, alphaVal, time);
        drawSignature(screenWidth, screenHeight, alphaVal, time);
        drawLanguageButton(screenWidth, alphaVal);

        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private static final class GlassDrop {
        float x, y, r;
        double vy = 0;
        boolean sliding;
        float slideStartY;
        final long born = System.currentTimeMillis();
        final long lifeMs = 9000 + (long) (Math.random() * 13000);

        GlassDrop(float x, float y, float r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }
    }

    private final java.util.List<GlassDrop> glassDrops = new ArrayList<>();
    private long lastRainFrame;

    private void renderMenuRain(float w, float h, float anim, float S) {
        if (anim <= 0.01F) return;
        long now = System.currentTimeMillis();
        long dt = Math.min(60L, Math.max(1L, now - lastRainFrame));
        lastRainFrame = now;

        int targetDrops = Math.min(230, Math.max(90, (int) (w / 6)));
        while (glassDrops.size() < targetDrops)
            glassDrops.add(spawnGlassDrop(w, h));

        Iterator<GlassDrop> dit = glassDrops.iterator();
        while (dit.hasNext()) {
            GlassDrop d = dit.next();

            if (!d.sliding && now - d.born > d.lifeMs) {
                dit.remove();
                continue;
            }

            if (!d.sliding && d.r > 2.6f && Math.random() < 0.0009 * dt) {
                d.sliding = true;
                d.slideStartY = d.y;
            }

            float bh = d.r * S;
            if (d.sliding) {
                bh *= Math.min(1.8f, 1f + (float) d.vy * 7f);
                d.vy += 0.00035 * dt;
                d.y += d.vy * dt;

                float trailH = d.y - d.slideStartY;
                if (trailH > 2F) {
                    Draw.gradientRect(d.x - d.r * S * 0.7f, d.slideStartY,
                            d.r * S * 1.4f, trailH,
                            new int[]{
                                    ColorUtil.getColor(10, 24, 46, 0),
                                    ColorUtil.getColor(10, 24, 46, 0),
                                    ColorUtil.replAlpha(ColorUtil.getColor(10, 24, 46), (int) (anim * 70)),
                                    ColorUtil.replAlpha(ColorUtil.getColor(10, 24, 46), (int) (anim * 70))
                            }, d.r * S * 1.4f);
                }
                if (d.y > h + 30) {
                    dit.remove();
                    continue;
                }
            } else {
                if (d.r < 3.4F) d.r += 0.00035 * dt;
            }

            drawGlassDrop(d, anim, S);
        }
        while (glassDrops.size() < targetDrops)
            glassDrops.add(spawnGlassDrop(w, h));
    }

    private GlassDrop spawnGlassDrop(float w, float h) {
        float t = (float) Math.random();
        float r;
        if (t < 0.72f) r = 0.7f + (float) Math.random() * 0.8f;
        else if (t < 0.95f) r = 1.5f + (float) Math.random() * 1.1f;
        else r = 2.7f + (float) Math.random() * 1.5f;
        return new GlassDrop((float) (Math.random() * w), (float) (Math.random() * h), r);
    }

    private void drawGlassDrop(GlassDrop d, float anim, float S) {
        float dr = d.r * S;
        float stretch = d.sliding ? Math.min(1.7f, 1f + (float) d.vy * 6f) : 1f;
        float bh = dr * stretch;

        RenderUtil.Render2D.rect(d.x - dr, d.y - bh, dr * 2, bh * 2,
                ColorUtil.replAlpha(ColorUtil.getColor(12, 26, 48), (int) (anim * 125)),
                Math.min(dr, bh));

        RenderUtil.Render2D.rect(d.x - dr * 0.42f, d.y - bh * 0.25f, dr * 1.02f, bh * 1.05f,
                ColorUtil.replAlpha(ColorUtil.getColor(6, 14, 30), (int) (anim * 95)),
                dr * 0.5f);

        RenderUtil.Render2D.rect(d.x - dr * 0.66f, d.y + bh * 0.32f, dr * 1.32f, bh * 0.36f,
                ColorUtil.replAlpha(ColorUtil.getColor(150, 195, 240), (int) (anim * 75)),
                dr * 0.34f);

        if (dr > 1.1f) {
            RenderUtil.Render2D.rect(d.x - dr * 0.62f, d.y - bh * 0.76f,
                    dr * 0.5f, bh * 0.32f,
                    ColorUtil.replAlpha(ColorUtil.getColor(238, 249, 255), (int) (anim * 185)),
                    dr * 0.17f);

            RenderUtil.Render2D.rect(d.x + dr * 0.05f, d.y - bh * 0.42f,
                    dr * 0.15f, bh * 0.12f,
                    ColorUtil.replAlpha(ColorUtil.getColor(255), (int) (anim * 155)),
                    dr * 0.07f);
        }
    }

    private void drawRainyBackground(int sw, int sh, float a, float time) {
        float cx = sw / 2F;
        float cy = sh * 0.30F;

        float breathe = 0.70F + 0.30F * (float) Math.sin(time * 0.7F);
        RenderUtil.Images.texture(GLOW_TEX, cx - 200, cy - 155, 400, 310,
                ColorUtil.getColor(40, 105, 185, a * 0.12F * breathe));
        RenderUtil.Images.texture(GLOW_TEX, cx - 110, cy - 85, 220, 170,
                ColorUtil.getColor(55, 130, 210, a * 0.10F * breathe));

        int edge = ColorUtil.getColor(3, 8, 20, a * 0.8F);
        int edgeT = ColorUtil.getColor(3, 8, 20, 0F);
        Draw.gradientRect(0, 0, sw, 64, new int[]{edge, edge, edgeT, edgeT}, 0);
        Draw.gradientRect(0, sh - 80, sw, 80, new int[]{edgeT, edgeT, edge, edge}, 0);
    }

    private void drawInfoPanel(int sw, int sh, float a, float time) {
        float w = 160;
        float headerH = 20F;
        float bodyH = 28F;
        float x = 12;

        infoAnim.update();
        float open = infoAnim.get();
        float h = headerH + bodyH * open;
        float y = sh - h - 12;

        infoBtnX = x; infoBtnY = y; infoBtnW = w; infoBtnH = headerH;
        boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, x, y, w, headerH);

        RenderUtil.Blur.blur(x, y, w, h, 1, 9, ColorUtil.getColor(6, 16, 38, a * 0.60F));
        Draw.rect(x, y, w, h, ColorUtil.getColor(14, 40, 74, a * 0.40F), 9);
        Draw.outline(x, y, w, h, 0.8F, ColorUtil.getColor(6, 24, 46, a * 0.90F), 9);
        Draw.glow(x, y, w, h, ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.22F), 9, 4F, 0.35F);

        float blink = 0.45F + 0.55F * (float) Math.abs(Math.sin(time * 2.4F));
        int teal = ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * 0.95F);

        String title = "INFORMATION";
        Fonts.sf_regular.draw(title, x + 10, y + 7, 5.5F, teal);
        Draw.rect(x + 10 + Fonts.sf_regular.getWidth(title, 5.5F) + 4, y + 7.5F, 4.5F, 4.5F,
                ColorUtil.getColor(80, 255, 230, a * blink), 2.2F);

        float arrowSize = 7F;
        int arrowCol = ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, a * (0.45F + (hov ? 0.55F : 0F)));
        Client.get().render2D().getTexturePipeline().drawGlowTexture(
                ARROW_TEX, x + w - 14, y + headerH / 2F - arrowSize / 2F, arrowSize, arrowSize,
                0F, 0F, 1F, 1F,
                new int[]{arrowCol, arrowCol, arrowCol, arrowCol},
                new float[]{0F, 0F, 0F, 0F}, 0F, 180F * (1F - open));

        if (open > 0.01F) {
            float ca = a * open;
            int dim = ColorUtil.getColor(150, 200, 210, ca * 0.55F);

            Draw.rect(x + 10, y + 16.5F, w - 20, 0.5F, ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, ca * 0.25F));

            String nick = mc.getSession() != null ? mc.getSession().getUsername() : "-";
            Fonts.sf_regular.draw("USER " + nick, x + 10, y + 20, 5,
                    ColorUtil.getColor(190, 235, 250, ca * 0.85F));

            if (h >= 35F) {
                Fonts.sf_regular.draw("FPS " + mc.getCurrentFps(), x + 10, y + 28, 5, dim);
                String ver = "VER 1.21.11";
                Fonts.sf_regular.draw(ver, x + w - 10 - Fonts.sf_regular.getWidth(ver, 5), y + 28, 5, dim);
            }

            if (h >= 43F) {
                String t = new SimpleDateFormat("HH:mm").format(new Date());
                Fonts.sf_regular.draw("TIME " + t, x + 10, y + 36, 5, dim);
                String status = "SYSTEM ONLINE";
                Fonts.sf_regular.draw(status, x + w - 10 - Fonts.sf_regular.getWidth(status, 5), y + 36, 5,
                        ColorUtil.getColor(AQUA_R, AQUA_G, AQUA_B, ca * (0.5F + 0.4F * blink)));
            }
        }
    }

    private void drawSignature(int sw, int sh, float a, float time) {
        String text = "RainyDLC";
        float tw = Fonts.sf_regular.getWidth(text, 6);
        float x = sw - tw - 12;
        float y = sh - 20;

        Fonts.sf_regular.draw(text, x, y, 6, ColorUtil.getColor(170, 210, 242, a * 0.78F));
    }

    private void drawMenuButtons(int screenWidth, int screenHeight, float alphaVal) {
        updateExitSlideProgress();

        float gap = 6;
        float panelWidth = Math.min(264, screenWidth - 40F);
        float cardWidth = (panelWidth - gap) / 2F;
        float cardHeight = 27;
        float totalCardsWidth = cardWidth * 2F + gap;

        float smallGap = 6;
        int smallCount = modsButton == null ? 2 : 3;
        float smallWidth = (totalCardsWidth - smallGap * (smallCount - 1)) / smallCount;
        float smallHeight = 27;

        float exitWidth = totalCardsWidth;
        float exitHeight = 24;

        float totalBlockHeight = cardHeight + gap + smallHeight + gap + exitHeight;

        float startX = screenWidth / 2F - totalCardsWidth / 2F;

        float startY = screenHeight / 2F - totalBlockHeight / 2F + 14F;
        float appearY = (1F - alphaVal) * 34F;

        float currentY = startY + appearY;

        multiplayerButton.drawHero(startX, currentY, cardWidth, cardHeight, alphaVal, 0);
        singleplayerButton.drawHero(startX + cardWidth + gap, currentY, cardWidth, cardHeight, alphaVal, 1);

        currentY += cardHeight + gap;

        float smallX = startX;
        settingsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "l");
        smallX += smallWidth + smallGap;
        accountsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "m");
        if (modsButton != null) {
            smallX += smallWidth + smallGap;
            modsButton.drawCompact(smallX, currentY, smallWidth, smallHeight, alphaVal, "n");
        }

        currentY += smallHeight + gap;

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
            if (MathUtil.isHovered(mouseX, mouseY, infoBtnX, infoBtnY, infoBtnW, infoBtnH)) {
                infoExpanded = !infoExpanded;
                infoAnim.run(infoExpanded ? 1 : 0, 0.3F, Easings.QUAD_OUT);
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

        private String text() { return ru.white.lang.Lang.pick(textRu, textEn); }
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

        private void drawFrostPanel(float x, float y, float width, float height, float globalAlpha, float hp, int accent) {
            float radius = 9F;

            RenderUtil.Blur.blur(x, y, width, height, 1, radius + 3F,
                    ColorUtil.getColor(8, 20, 46, globalAlpha * (0.16F + hp * 0.08F)));

            int fill = ColorUtil.getColor(20, 48, 88, globalAlpha * (0.07F + hp * 0.06F));
            Draw.rect(x, y, width, height, fill, radius);

            if (hp < 0.98F) {
                int edge = ColorUtil.getColor(10, 26, 52, globalAlpha * (1F - hp * 0.85F));
                Draw.outline(x, y, width, height, 0.8F, edge, radius);
            }

            Draw.glow(x, y, width, height,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.10F + hp * 0.12F))),
                    radius, 4F, 0.35F + hp * 0.35F);

            if (hp > 0.01F) {
                Draw.glow(x, y, width, height,
                        ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (hp * 0.40F))),
                        radius, 5F, hp * 0.6F);

                drawBolts(x, y, width, height, globalAlpha, hp);
            }
        }

        private static final int MAX_BOLTS = 14;
        private static final long BOLT_INTERVAL_MS = 42L;
        private static final java.util.Random BOLT_RAND = new java.util.Random();

        private final List<Bolt2D> bolts = new ArrayList<>();
        private long lastBoltSpawn = 0L;

        private static final class Bolt2D {
            final float[] pts;
            final long spawnTime;
            final long lifetimeMs;

            Bolt2D(float[] pts, long spawnTime, long lifetimeMs) {
                this.pts = pts;
                this.spawnTime = spawnTime;
                this.lifetimeMs = lifetimeMs;
            }
        }

        private void drawBolts(float x, float y, float w, float h, float globalAlpha, float hp) {
            long now = System.currentTimeMillis();

            if (hp > 0.25F && now - lastBoltSpawn > BOLT_INTERVAL_MS && bolts.size() < MAX_BOLTS) {
                bolts.add(spawnBolt(x, y, w, h));
                lastBoltSpawn = now;
            }

            bolts.removeIf(bolt -> now - bolt.spawnTime > bolt.lifetimeMs);

            for (Bolt2D bolt : bolts) {
                float life = (now - bolt.spawnTime) / (float) bolt.lifetimeMs;
                float fade = 1F - life;
                float flicker = 0.65F + BOLT_RAND.nextFloat() * 0.35F;
                float alpha = globalAlpha * hp * fade * flicker;
                if (alpha <= 0.03F) continue;

                for (int i = 0; i < bolt.pts.length; i += 2) {
                    float s = 3.5F;
                    drawRotTexture(BOLT_TEX, bolt.pts[i] - s / 2F, bolt.pts[i + 1] - s / 2F, s, s, 0F,
                            ColorUtil.getColor(150, 225, 255, alpha * 0.55F));
                }

                for (int i = 0; i < bolt.pts.length - 2; i += 2) {
                    float ax = bolt.pts[i], ay = bolt.pts[i + 1];
                    float bx = bolt.pts[i + 2], by = bolt.pts[i + 3];
                    float dx = bx - ax, dy = by - ay;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len < 0.1F) continue;
                    float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
                    float segH = 1.8F;
                    drawRotTexture(BOLT_TEX, (ax + bx) / 2F - (len + 2F) / 2F, (ay + by) / 2F - segH / 2F,
                            len + 2F, segH, angleDeg,
                            ColorUtil.getColor(235, 250, 255, alpha * 0.85F));
                }
            }
        }

        private static void drawRotTexture(Identifier tex, float x, float y, float w, float h,
                                           float rotationDeg, int color) {
            Client.get().render2D().getTexturePipeline().drawGlowTexture(
                    tex, x, y, w, h, 0F, 0F, 1F, 1F,
                    new int[]{color, color, color, color},
                    new float[]{0F, 0F, 0F, 0F}, 0F, rotationDeg);
        }

        private Bolt2D spawnBolt(float x, float y, float w, float h) {
            List<Float> pts = new ArrayList<>(32);

            if (BOLT_RAND.nextInt(4) == 0) {
                float t = BOLT_RAND.nextFloat();
                float[] p = perimeterPoint(x, y, w, h, t);
                float[] n = perimeterNormal(x, y, w, h, t);
                float len = 5F + BOLT_RAND.nextFloat() * 8F;
                float[] end = {p[0] + n[0] * len, p[1] + n[1] * len};
                pts.add(p[0]);
                pts.add(p[1]);
                buildPath(pts, p, end, 3, len * 0.4F);
            } else {
                float per = 2F * (w + h);
                float t0 = BOLT_RAND.nextFloat() * per;
                float arc = 16F + BOLT_RAND.nextFloat() * 46F;
                int steps = Math.max(3, (int) (arc / 10F));
                float[] prev = null;
                for (int i = 0; i <= steps; i++) {
                    float[] p = perimeterPoint(x, y, w, h, t0 + arc * i / steps);
                    if (prev != null) {
                        float segLen = (float) Math.hypot(p[0] - prev[0], p[1] - prev[1]);
                        buildPath(pts, prev, p, 2, Math.min(3.5F, segLen * 0.4F));
                    } else {
                        pts.add(p[0]);
                        pts.add(p[1]);
                    }
                    prev = p;
                }
            }

            float[] flat = new float[pts.size()];
            for (int i = 0; i < flat.length; i++) flat[i] = pts.get(i);
            return new Bolt2D(flat, System.currentTimeMillis(), 130 + BOLT_RAND.nextInt(140));
        }

        private void buildPath(List<Float> out, float[] a, float[] b, int depth, float maxOffset) {
            if (depth <= 0) {
                out.add(b[0]);
                out.add(b[1]);
                return;
            }
            float dx = b[0] - a[0], dy = b[1] - a[1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001F) {
                out.add(b[0]);
                out.add(b[1]);
                return;
            }
            float off = (BOLT_RAND.nextFloat() - 0.5F) * 2F * maxOffset;
            float mx = (a[0] + b[0]) / 2F - dy / len * off;
            float my = (a[1] + b[1]) / 2F + dx / len * off;
            float[] mid = {mx, my};
            buildPath(out, a, mid, depth - 1, maxOffset * 0.5F);
            buildPath(out, mid, b, depth - 1, maxOffset * 0.5F);
        }

        private float[] perimeterPoint(float x, float y, float w, float h, float t) {
            float per = 2F * (w + h);
            t = ((t % per) + per) % per;
            if (t < w) return new float[]{x + t, y};
            t -= w;
            if (t < h) return new float[]{x + w, y + t};
            t -= h;
            if (t < w) return new float[]{x + w - t, y + h};
            t -= w;
            return new float[]{x, y + h - t};
        }

        private float[] perimeterNormal(float x, float y, float w, float h, float t) {
            float per = 2F * (w + h);
            t = ((t % per) + per) % per;
            if (t < w) return new float[]{0F, -1F};
            t -= w;
            if (t < h) return new float[]{1F, 0F};
            t -= h;
            if (t < w) return new float[]{0F, 1F};
            return new float[]{-1F, 0F};
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

            if (progress > 0.01F) {
                Draw.rect(x + 2, y + 2, (width - 4) * progress, height - 4,
                        ColorUtil.getColor(70, 190, 240, globalAlpha * (0.10F + progress * 0.16F)), 7);
            }

            float knobSize = height - 8F;
            float knobX = x + 5F + (width - knobSize - 10F) * progress;

            Draw.rect(knobX, y + 4F, knobSize, knobSize,
                    ColorUtil.getColor(150, 220, 250, globalAlpha * (0.25F + progress * 0.45F)), 4);
            Draw.outline(knobX, y + 4F, knobSize, knobSize, 0.7F,
                    ColorUtil.getColor(14, 36, 66, globalAlpha * 0.90F), 4);

            Fonts.icon.drawCentered("i", knobX + knobSize / 2F, y + height / 2F - 3, 7,
                    ColorUtil.replAlpha(accent, (int) (globalAlpha * 255 * (0.25F + hp * 0.2F + progress * 0.55F))));
            Fonts.sf_regular.drawCentered(ru.white.lang.Lang.pick("Сдвиньте, чтобы выйти", "Drag to exit"), x + width / 2F + 8F,
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
