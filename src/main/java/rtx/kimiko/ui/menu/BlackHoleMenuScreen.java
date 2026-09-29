package rtx.kimiko.ui.menu;

import mixin.accessor.GuiGraphicsExtractorAccessor;
import mods.acountswiher.ru.vidtu.ias.screen.AccountScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import rtx.kimiko.utils.render.fonts.Fonts;

/**
 * Главное меню "Горизонт событий".
 *
 * Весь фон считает шейдер ui/mainmenu/space в полном разрешении окна:
 *  - снаружи: честная трассировка лучей вокруг черной дыры Шварцшильда
 *    (гравитационное линзирование, аккреционный диск с доплеровским усилением и красным смещением,
 *    линзированный Млечный Путь и звезды);
 *  - засасывание: камера падает к горизонту по спирали, пространство искажается, всё гаснет;
 *  - внутри: пустота и вращающаяся спиральная галактика, по орбите которой крутятся иконки-кнопки.
 *
 * Java здесь только считает состояние, хит-тесты и подписи. Формулы позиций иконок
 * синхронизированы с шейдером (renderInside / galToScreen), не меняй одно без другого.
 */
public class BlackHoleMenuScreen extends Screen {

    private static final long START = System.nanoTime();
    private static boolean introPlayed = false;

    private static final float SUCK_DUR = 3.6f;
    private static final float UNFOLD_DUR = 2.8f;

    // --- константы шейдера (renderInside) ---
    private static final float GAL_S = 0.80f;
    private static final float GAL_COSI = 0.46f;
    private static final float GAL_ROLL = -0.16f;
    private static final float GAL_CY = -0.03f;
    private static final float ICON_R = 0.72f;
    private static final int ICONS = 5;
    private static final int PLAY_INDEX = 5;

    private static final String[] LABELS = {"Одиночная игра", "Мультиплеер", "Аккаунты", "Настройки", "Выход"};

    private enum Phase { OUTSIDE, SUCK, INSIDE }

    private Phase phase;
    private long phaseStart = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private float playHover;
    private int hovered = -1;
    private int lastHovered = -1;
    private float hoverAmt;
    private float orbitAngle = 0.35f;
    private final float[] iconX = new float[ICONS];
    private final float[] iconY = new float[ICONS];
    private final float[] iconR = new float[ICONS];

    public BlackHoleMenuScreen() {
        super(Text.literal("Frostix"));
        this.phase = introPlayed ? Phase.INSIDE : Phase.OUTSIDE;
        if (introPlayed) {
            // возвращаемся из мира / настроек: галактика уже развернута
            this.phaseStart = System.nanoTime() - (long) (UNFOLD_DUR * 1.0e9);
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    private float phaseTime() { return (System.nanoTime() - phaseStart) / 1.0e9f; }
    private static float time() { return (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0); }

    private void setPhase(Phase p) {
        phase = p;
        phaseStart = System.nanoTime();
    }

    private void startSuck() {
        if (phase != Phase.OUTSIDE) return;
        introPlayed = true;
        setPhase(Phase.SUCK);
        play(PositionedSoundInstance.ambient(SoundEvents.BLOCK_PORTAL_TRIGGER, 0.6f, 0.5f));
    }

    private void play(PositionedSoundInstance sound) {
        try {
            client.getSoundManager().play(sound);
        } catch (Throwable ignored) {
        }
    }

    private void activate(int i) {
        play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        switch (i) {
            case 0 -> client.setScreen(new SelectWorldScreen(this));
            case 1 -> client.setScreen(new MultiplayerScreen(this));
            case 2 -> client.setScreen(new AccountScreen(this));
            case 3 -> client.setScreen(new OptionsScreen(this, client.options));
            default -> client.scheduleStop();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (phase == Phase.OUTSIDE && overPlay(mx, my)) {
            startSuck();
            return true;
        }
        if (phase == Phase.INSIDE) {
            int i = iconAt(mx, my);
            if (i >= 0) {
                activate(i);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (phase == Phase.OUTSIDE && (key == 257 || key == 335 || key == 32)) {
            startSuck();
            return true;
        }
        return super.keyPressed(input);
    }

    // ------------------------------------------------------------------ geometry (зеркало шейдера)

    private boolean overPlay(double mx, double my) {
        float h = this.height;
        float cx = this.width / 2f;
        float cy = h * 0.82f;
        float r = 0.058f * h;
        double dx = mx - cx;
        double dy = my - cy;
        return dx * dx + dy * dy <= (double) (r * r);
    }

    private int iconAt(double mx, double my) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < ICONS; i++) {
            double dx = mx - iconX[i], dy = my - iconY[i];
            double d = Math.sqrt(dx * dx + dy * dy);
            if (iconR[i] > 1f && d <= iconR[i] * 1.15 && d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    private void layoutIcons(float p1, float angle) {
        float e = 1f - (float) Math.pow(1f - clamp01(p1), 3);
        float scale = 0.03f + (1f - 0.03f) * e;
        float iconsIn = smoothstep(0.55f, 0.9f, p1);
        float aspect = this.width / (float) Math.max(1, this.height);
        float cr = (float) Math.cos(GAL_ROLL), sr = (float) Math.sin(GAL_ROLL);
        for (int i = 0; i < ICONS; i++) {
            float a = angle + i * (float) (Math.PI * 2.0 / ICONS);
            float gx = ICON_R * (float) Math.cos(a);
            float gy = ICON_R * (float) Math.sin(a);
            float vx = gx * GAL_S * scale;
            float vy = gy * GAL_COSI * GAL_S * scale;
            float qx = cr * vx - sr * vy;
            float qy = GAL_CY + sr * vx + cr * vy;
            float persp = 1f - 0.22f * (float) Math.sin(a);
            float hov = i == hovered ? hoverAmt : 0f;
            iconX[i] = (qx / aspect + 0.5f) * this.width;
            iconY[i] = (1f - (qy + 0.5f)) * this.height;
            iconR[i] = 0.052f * persp * (1f + 0.18f * hov) * iconsIn * this.height;
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;
        float t = time();

        if (phase == Phase.SUCK && phaseTime() >= SUCK_DUR) setPhase(Phase.INSIDE);

        float p0, p1;
        int hoverIndex;
        float hover;

        if (phase == Phase.INSIDE) {
            p1 = clamp01(phaseTime() / UNFOLD_DUR);
            float e = 1f - (float) Math.pow(1f - p1, 3);
            int h = iconAt(mouseX, mouseY);
            if (h != hovered) {
                if (h >= 0) play(PositionedSoundInstance.ui(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 0.35f));
                hovered = h;
                if (h >= 0 && h != lastHovered) hoverAmt = 0f;
                if (h >= 0) lastHovered = h;
            }
            hoverAmt += ((hovered >= 0 ? 1f : 0f) - hoverAmt) * Math.min(1f, dt * 10f);
            float omega = hovered >= 0 ? 0.02f : 0.09f;
            orbitAngle += dt * omega;
            float angle = orbitAngle + 4.0f * (float) Math.pow(1f - e, 3);
            layoutIcons(p1, angle);
            p0 = angle;
            hoverIndex = hovered >= 0 ? hovered : lastHovered;
            hover = hoverAmt;
        } else {
            float s = phase == Phase.SUCK ? clamp01(phaseTime() / SUCK_DUR) : 0f;
            boolean over = phase == Phase.OUTSIDE && overPlay(mouseX, mouseY);
            playHover += ((over ? 1f : 0f) - playHover) * Math.min(1f, dt * 10f);
            p0 = s;
            p1 = -1f;
            hoverIndex = PLAY_INDEX;
            hover = playHover;
        }

        ((GuiGraphicsExtractorAccessor) ctx).kimiko$getGuiRenderState().addSimpleElement(
                new SpaceMenuRenderState(ctx.getMatrices(), this.width, this.height, p0, p1, t, hoverIndex, hover));

        if (phase == Phase.INSIDE) {
            drawInsideText(ctx, p1);
        } else {
            drawPlayLabel(ctx, p0);
        }
    }

    private void drawPlayLabel(DrawContext ctx, float s) {
        float a = 1f - smoothstep(0f, 0.12f, s);
        if (a <= 0.01f) return;
        float h = this.height;
        float cx = this.width / 2f;
        float cy = h * 0.82f;
        float r = 0.052f * h * (1f + 0.15f * playHover);
        float size = Math.max(7f, h * 0.026f) * (1f + 0.12f * playHover);
        int c = argb((int) (a * (120 + 135 * playHover)), (int) (205 + 50 * playHover), (int) (215 + 40 * playHover), 255);
        text(ctx, Fonts.MEDIUM, "ИГРАТЬ", cx, cy + r * 1.45f + size * 0.6f, size, c);
    }

    private void drawInsideText(DrawContext ctx, float p1) {
        float ta = smoothstep(0.35f, 0.8f, p1);
        if (ta > 0.01f) {
            float size = Math.max(14f, this.height * 0.07f);
            text(ctx, Fonts.EXTRALIGHT, "F R O S T I X", this.width / 2f, this.height * 0.095f, size, argb((int) (ta * 235), 236, 242, 255));
            text(ctx, Fonts.LIGHT, "по ту сторону горизонта событий", this.width / 2f, this.height * 0.095f + size * 0.85f,
                    size * 0.26f, argb((int) (ta * 130), 190, 200, 230));
        }
        float la = smoothstep(0.7f, 1f, p1);
        if (la <= 0.01f) return;
        for (int i = 0; i < ICONS; i++) {
            if (iconR[i] < 1f) continue;
            float hov = i == hovered ? hoverAmt : 0f;
            float size = Math.max(7f, this.height * 0.026f) * (1f + 0.12f * hov);
            int c = argb((int) (la * (120 + 135 * hov)), (int) (205 + 50 * hov), (int) (215 + 40 * hov), 255);
            text(ctx, Fonts.MEDIUM, LABELS[i], iconX[i], iconY[i] + iconR[i] * 1.45f + size * 0.6f, size, c);
        }
    }

    /** Векторный шрифт клиента (гладкий на любом масштабе), с откатом на ванильный. */
    private void text(DrawContext ctx, Fonts font, String s, float cx, float cy, float size, int color) {
        try {
            float w = font.width(s, size);
            font.draw(ctx, s, cx - w / 2f, cy - size / 2f, size, color);
        } catch (Throwable th) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, s, (int) cx, (int) (cy - 4), color);
        }
    }

    // ------------------------------------------------------------------ math

    private static int argb(int a, int r, int g, int b) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static float smoothstep(float e0, float e1, float x) {
        float k = clamp01((x - e0) / (e1 - e0));
        return k * k * (3 - 2 * k);
    }
}
