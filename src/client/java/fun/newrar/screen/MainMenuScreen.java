package fun.newrar.screen;

import fun.newrar.Client;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.MathUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.Render2D;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.ScreenBlur;
import fun.newrar.utils.render.font.Fonts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen implements IMinecraft {
    private static final Identifier MENU_BG = Identifier.of("client", "textures/frame/mainmenu.png");
    private static final Identifier ICON_LOGO = Identifier.of("client", "textures/icon.png");

    public Animation alpha = new Animation();
    private final List<MenuButton> buttons = new ArrayList<>();
    private MenuButton multiplayerButton;
    private MenuButton singleplayerButton;
    private MenuButton settingsButton;
    private MenuButton accountsButton;
    private MenuButton modsButton;
    private MenuButton exitButton;

    private float scaleFix = 1F;
    private double lastMouseX;
    private double lastMouseY;

    private float langBtnX, langBtnY, langBtnW, langBtnH;
    private final Animation langHover = new Animation();

    private MenuBackgroundCarousel bgCarousel;

    public MainMenuScreen() {
        super(Text.literal("MainMenuScreen"));
    }

    @Override
    protected void init() {
        if (alpha.getValue() < 0.1F) {
            alpha.set(0);
            alpha.run(1, 0.35F, Easings.QUAD_OUT);
        } else {
            alpha.set(1);
        }

        fun.newrar.alt.AltManager.get().bootstrap();
        buildButtons();
        if (bgCarousel == null) bgCarousel = new MenuBackgroundCarousel();
    }

    private void buildButtons() {
        buttons.clear();

        singleplayerButton = new MenuButton("Одиночная игра", "Singleplayer", "k",
                () -> mc.setScreen(new SelectWorldScreen(this)));
        multiplayerButton = new MenuButton("Сетевая игра", "Multiplayer", "j",
                () -> mc.setScreen(new MultiplayerScreen(this)));

        buttons.add(multiplayerButton);
        buttons.add(singleplayerButton);

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            modsButton = new MenuButton("Моды", "Mods", "n", () -> {
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

        settingsButton = new MenuButton("Настройки", "Settings", "l",
                () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        accountsButton = new MenuButton("Аккаунты", "Accounts", "m",
                () -> mc.setScreen(new AltManagerScreen(this)));
        exitButton = new MenuButton("Выход", "Exit", "i", true,
                () -> mc.scheduleStop());

        buttons.add(settingsButton);
        buttons.add(accountsButton);
        buttons.add(exitButton);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        mouseX = (int) (mouseX / scaleFix);
        mouseY = (int) (mouseY / scaleFix);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        alpha.update();

        renderOverlay(context);
    }

    public void renderOverlay(DrawContext context) {
        MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        float alphaVal = Math.max(0.01F, this.alpha.get());
        float time = (System.currentTimeMillis() % 100000L) / 1000F;

        float parallaxStrength = 0.035F;
        float offsetX = (screenWidth / 2F - (float) lastMouseX) * parallaxStrength;
        float offsetY = (screenHeight / 2F - (float) lastMouseY) * parallaxStrength;

        float bgScale = 1.08F;
        float bgW = screenWidth * bgScale;
        float bgH = screenHeight * bgScale;
        float bgX = (screenWidth - bgW) / 2F + offsetX;
        float bgY = (screenHeight - bgH) / 2F + offsetY;

        Draw.rect(0, 0, screenWidth, screenHeight, ColorUtil.getColor(7, 12, 24, 255));
        Identifier bgFrame = bgCarousel != null ? bgCarousel.currentBackground() : MENU_BG;
        if (bgFrame != null) {
            RenderUtil.Images.texture(bgFrame, bgX, bgY, bgW, bgH, ColorUtil.getColor(255, 255, 255, alphaVal));
        }

        Draw.rect(0, 0, screenWidth, screenHeight, ColorUtil.getColor(7, 12, 24, alphaVal * 0.42F));
        ScreenBlur.capture(3);

        int edgeTop = ColorUtil.getColor(3, 7, 16, alphaVal * 0.75F);
        int edgeBot = ColorUtil.getColor(3, 7, 16, alphaVal * 0.82F);
        Draw.gradientRect(0, 0, screenWidth, 75, new int[]{edgeTop, edgeTop, 0, 0}, 0);
        Draw.gradientRect(0, screenHeight - 85, screenWidth, 85, new int[]{0, 0, edgeBot, edgeBot}, 0);

        float logoSize = 22F;
        float logoY = screenHeight * 0.31F - 25F * (1F - alphaVal);
        RenderUtil.Images.texture(ICON_LOGO, screenWidth / 2F - logoSize / 2F, logoY, logoSize, logoSize,
                ColorUtil.getColor(255, alphaVal));

        Fonts.sf_medium.drawCentered("RainyDLC", screenWidth / 2F, logoY + logoSize + 6F, 12.5F,
                ColorUtil.getColor(235, 243, 252, alphaVal));
        Fonts.sf_regular.drawCentered("1.21.11 • Fabric", screenWidth / 2F, logoY + logoSize + 21F, 5.5F,
                ColorUtil.getColor(125, 160, 205, alphaVal * 0.65F));

        for (MenuButton b : buttons) {
            b.update(lastMouseX, lastMouseY);
        }
        drawMenuButtons(screenWidth, screenHeight, alphaVal);

        drawInfoBar(screenWidth, screenHeight, alphaVal, time);
        drawSignature(screenWidth, screenHeight, alphaVal);
        drawLanguageButton(screenWidth, alphaVal);

        if (bgCarousel != null) {
            bgCarousel.render(screenWidth, screenHeight, alphaVal, lastMouseX, lastMouseY);
        }

        Draw.flush();
        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private void drawMenuButtons(int screenWidth, int screenHeight, float alphaVal) {
        float panelWidth = Math.min(270F, screenWidth - 36F);
        float gap = 5.5F;
        float cardHeight = 26F;

        float topRowCardW = (panelWidth - gap) / 2F;

        int midCount = modsButton == null ? 2 : 3;
        float midRowCardW = (panelWidth - gap * (midCount - 1)) / midCount;

        float exitH = 24F;
        float totalH = cardHeight + gap + cardHeight + gap + exitH;

        float startX = screenWidth / 2F - panelWidth / 2F;
        float startY = screenHeight / 2F - totalH / 2F + 22F + (1F - alphaVal) * 20F;

        float currentY = startY;

        multiplayerButton.draw(startX, currentY, topRowCardW, cardHeight, alphaVal);
        singleplayerButton.draw(startX + topRowCardW + gap, currentY, topRowCardW, cardHeight, alphaVal);

        currentY += cardHeight + gap;

        float midX = startX;
        settingsButton.draw(midX, currentY, midRowCardW, cardHeight, alphaVal);
        midX += midRowCardW + gap;
        accountsButton.draw(midX, currentY, midRowCardW, cardHeight, alphaVal);
        if (modsButton != null) {
            midX += midRowCardW + gap;
            modsButton.draw(midX, currentY, midRowCardW, cardHeight, alphaVal);
        }

        currentY += cardHeight + gap;

        exitButton.draw(startX, currentY, panelWidth, exitH, alphaVal);
    }

    private void drawInfoBar(int sw, int sh, float a, float time) {
        String user = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
        int fps = mc.getCurrentFps();
        String text = user + "   •   " + fps + " FPS   •   v1.0";

        float tw = Fonts.sf_regular.getWidth(text, 5.5F);
        float w = tw + 24F;
        float h = 18F;
        float x = 14F;
        float y = sh - h - 12F;

        RenderUtil.Blur.blur(x, y, w, h, 1, 6F, ColorUtil.getColor(8, 14, 28, a * 0.45F));
        Draw.rect(x, y, w, h, ColorUtil.getColor(12, 20, 38, a * 0.42F), 6F);
        Draw.outline(x, y, w, h, 0.6F, ColorUtil.getColor(70, 120, 190, a * 0.22F), 6F);

        float pulse = 0.55F + 0.45F * (float) Math.abs(Math.sin(time * 2.2F));
        Draw.rect(x + 7.5F, y + h / 2F - 1.75F, 3.5F, 3.5F,
                ColorUtil.getColor(65, 215, 175, a * pulse), 1.75F);

        Fonts.sf_regular.draw(text, x + 15.5F, y + h / 2F - 3.2F, 5.5F,
                ColorUtil.getColor(185, 215, 240, a * 0.85F));
    }

    private void drawSignature(int sw, int sh, float a) {
        String text = "RainyDLC";
        float tw = Fonts.sf_regular.getWidth(text, 5.5F);
        Fonts.sf_regular.draw(text, sw - tw - 14F, sh - 20F, 5.5F,
                ColorUtil.getColor(110, 150, 195, a * 0.55F));
    }

    private void drawLanguageButton(int screenWidth, float alphaVal) {
        float w = 26F, h = 16F;
        float bx = screenWidth - w - 14F, by = 12F;
        langBtnX = bx; langBtnY = by; langBtnW = w; langBtnH = h;

        boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, bx, by, w, h);
        langHover.update();
        langHover.run(hov ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        float hp = langHover.get();

        RenderUtil.Blur.blur(bx, by, w, h, 1, 5F, ColorUtil.getColor(8, 14, 28, alphaVal * 0.45F));
        Draw.rect(bx, by, w, h, ColorUtil.getColor(12, 20, 38, alphaVal * (0.42F + hp * 0.2F)), 5F);
        Draw.outline(bx, by, w, h, 0.6F,
                ColorUtil.getColor(80, 140, 210, alphaVal * (0.2F + hp * 0.35F)), 5F);

        int textColor = ColorUtil.interpolateColor(
                ColorUtil.getColor(150, 190, 220, alphaVal * 0.75F),
                ColorUtil.getColor(245, 250, 255, alphaVal), hp);
        Fonts.sf_medium.drawCentered(fun.newrar.lang.Lang.tag(), bx + w / 2F, by + h / 2F - 3.4F, 5.5F, textColor);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);
        int button = click.button();

        if (button == 0) {
            if (bgCarousel != null && bgCarousel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (MathUtil.isHovered(mouseX, mouseY, langBtnX, langBtnY, langBtnW, langBtnH)) {
                fun.newrar.lang.Lang.toggle();
                return true;
            }
            for (MenuButton btn : buttons) {
                if (btn.isHovered()) {
                    btn.onClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (bgCarousel != null && bgCarousel.mouseScrolled((float) (mouseX / scaleFix), (float) (mouseY / scaleFix), vertical)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private static class MenuButton {
        private final String textRu, textEn;
        private final String icon;
        private final Runnable action;
        private final boolean danger;
        private final Animation hoverAnim = new Animation();
        private boolean hovered;

        private float lastX, lastY, lastW, lastH;

        public MenuButton(String textRu, String textEn, String icon, Runnable action) {
            this(textRu, textEn, icon, false, action);
        }

        public MenuButton(String textRu, String textEn, String icon, boolean danger, Runnable action) {
            this.textRu = textRu;
            this.textEn = textEn;
            this.icon = icon;
            this.danger = danger;
            this.action = action;
            this.hoverAnim.set(0);
        }

        private String text() { return fun.newrar.lang.Lang.pick(textRu, textEn); }

        public void update(double mouseX, double mouseY) {
            hovered = MathUtil.isHovered((float) mouseX, (float) mouseY, lastX, lastY, lastW, lastH);
            hoverAnim.update();
            hoverAnim.run(hovered ? 1 : 0, 0.18F, Easings.QUAD_OUT);
        }

        public void draw(float x, float y, float width, float height, float globalAlpha) {
            lastX = x;
            lastY = y;
            lastW = width;
            lastH = height;

            float hp = hoverAnim.get();
            float radius = 6F;

            int bgBase = ColorUtil.getColor(11, 18, 34, globalAlpha * (0.42F + hp * 0.22F));
            if (danger && hp > 0.01F) {
                bgBase = ColorUtil.interpolateColor(bgBase, ColorUtil.getColor(50, 18, 24, globalAlpha * 0.55F), hp);
            }

            RenderUtil.Blur.blur(x, y, width, height, 1, radius, ColorUtil.getColor(7, 13, 26, globalAlpha * 0.40F));
            Draw.rect(x, y, width, height, bgBase, radius);

            int outlineCol = danger
                    ? ColorUtil.interpolateColor(ColorUtil.getColor(75, 120, 185, globalAlpha * 0.18F), ColorUtil.getColor(230, 80, 95, globalAlpha * 0.45F), hp)
                    : ColorUtil.getColor(75, 130, 205, globalAlpha * (0.18F + hp * 0.32F));
            Draw.outline(x, y, width, height, 0.6F, outlineCol, radius);

            if (hp > 0.01F) {
                int glowCol = danger
                        ? ColorUtil.getColor(235, 75, 90, globalAlpha * hp * 0.20F)
                        : ColorUtil.getColor(65, 145, 235, globalAlpha * hp * 0.22F);
                Draw.glow(x, y, width, height, glowCol, radius, 4F, 0.35F);
            }

            int textColor = danger
                    ? ColorUtil.interpolateColor(ColorUtil.getColor(185, 210, 235, globalAlpha * 0.80F), ColorUtil.getColor(255, 160, 170, globalAlpha), hp)
                    : ColorUtil.interpolateColor(ColorUtil.getColor(185, 210, 235, globalAlpha * 0.80F), ColorUtil.getColor(245, 250, 255, globalAlpha), hp);

            int iconColor = danger
                    ? ColorUtil.interpolateColor(ColorUtil.getColor(130, 170, 215, globalAlpha * 0.60F), ColorUtil.getColor(255, 110, 125, globalAlpha * 0.95F), hp)
                    : ColorUtil.interpolateColor(ColorUtil.getColor(130, 175, 225, globalAlpha * 0.60F), ColorUtil.getColor(120, 205, 255, globalAlpha * 0.95F), hp);

            if (danger) {
                Fonts.sf_regular.drawCentered(text(), x + width / 2F + 5F, y + height / 2F - 3.5F, 6.5F, textColor);
                Fonts.rainydlc_2.drawCentered(icon, x + width / 2F - Fonts.sf_regular.getWidth(text(), 6.5F) / 2F - 6F, y + height / 2F - 3.2F, 6.5F, iconColor);
            } else {
                Fonts.sf_regular.draw(text(), x + 11F, y + height / 2F - 3.5F, 6.5F, textColor);
                Fonts.rainydlc_2.drawCentered(icon, x + width - 13F, y + height / 2F - 3.2F, 6.5F, iconColor);
            }
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
