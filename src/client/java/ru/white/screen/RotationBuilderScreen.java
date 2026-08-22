package ru.white.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import ru.white.Client;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.Scissor;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Конструктор ротации AttackAura: пресеты, три вкладки параметров,
 * живое превью наведения по силуэту модели. Правки применяются напрямую
 * к настройкам модуля и сохраняются вместе с конфигом.
 */
public class RotationBuilderScreen extends Screen implements IMinecraft {

    private static final String[] TABS = {"Скорости", "Случайность", "Осцилляция"};

    private static final LinkedHashMap<String, float[]> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("FunTime",    new float[]{140, 190, 85, 115, 240, 210, 2.5f, 2f,   0.8f, 0});
        PRESETS.put("SpookyTime", new float[]{100, 160, 70, 110, 220, 200, 2f,   1.6f, 0,    0.6f});
        PRESETS.put("Matrix",     new float[]{80,  90,  80, 90,  180, 180, 1f,   1f,   0,    0});
        PRESETS.put("Snap",       new float[]{150, 220, 110, 160, 260, 230, 3f,   2.4f, -0.8f, 0.9f});
        PRESETS.put("HvH",        new float[]{170, 240, 120, 170, 280, 250, 3.5f, 3f,  -1.2f, 1.2f});
        PRESETS.put("Neuro",      new float[]{110, 170, 80,  120, 230, 205, 2.2f, 1.8f, 0.5f, 0.4f});
        PRESETS.put("Custom",     new float[]{120, 180, 80,  120, 200, 180, 1f,   1f,   0f,    0f});
        PRESETS.put("Legit",      new float[]{60,  90,  45,  70,  140, 120, 0.6f, 0.5f, -0.3f, 0.3f});
        PRESETS.put("Sloth",      new float[]{35,  55,  25,  40,  110, 95,  0.15f,0.1f, 0.05f, 0.05f});
    }

    private static final float[] DEFAULTS = {120, 180, 80, 120, 200, 180, 1f, 1f, 0, 0};

    private int activeTab = 0;
    private String activePreset = "FunTime";
    private float scaleFix = 1F;
    private float mouseX, mouseY;

    private SliderSetting dragging = null;

    private final Animation anim = new Animation();

    // hit-геометрия, заполняется каждый кадр в render()
    private final List<String> chipNames = new ArrayList<>();
    private final List<float[]> chipRects = new ArrayList<>();
    private final List<float[]> sliderRects = new ArrayList<>();
    private final float[][] tabRects = new float[TABS.length][4];
    private float[] closeRect, resetRect;
    private float panelX, panelY, panelW, panelH;

    public RotationBuilderScreen() {
        super(Text.literal("RotationBuilder"));
    }

    private AttackAura aura() {
        return AttackAura.get();
    }

    private SliderSetting[] tabSettings() {
        return switch (activeTab) {
            case 0 -> new SliderSetting[]{aura().cYawMin, aura().cYawMax, aura().cPitchMin, aura().cPitchMax, aura().cHitYaw, aura().cHitPitch};
            case 1 -> new SliderSetting[]{aura().cRandomYaw, aura().cRandomPitch};
            default -> new SliderSetting[]{aura().cOscX, aura().cOscY};
        };
    }

    @Override
    protected void init() {
        anim.set(0);
        anim.run(1, 0.25F, Easings.SINE_OUT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean shouldPause() { return false; }

    private String fmt(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }

    @Override
    public void render(DrawContext context, int rawX, int rawY, float delta) {
        scaleFix = 2F / mc.getWindow().getScaleFactor();
        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);
        mouseX = rawX / scaleFix;
        mouseY = rawY / scaleFix;

        anim.update();
        float a = anim.get();

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        Font f = Fonts.sf_regular;
        int accent = ColorUtil.client();

        // затемнение фона
        RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, a, 10F, ColorUtil.getColor(0, 0.45F));

        float w = 470F, h = 310F;
        float x = screenWidth / 2F - w / 2F;
        float y = screenHeight / 2F - h / 2F;
        panelX = x; panelY = y; panelW = w; panelH = h;

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F, ColorUtil.getColor(0, 0.18F * a), 10F, 15, 1);
        RenderUtil.Blur.blur(x, y, w, h, a, 10F, ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.55F), a));
        RenderUtil.Render2D.outline(x, y, w, h, 0.8F, ColorUtil.replAlpha(accent, a * 90), 10F);

        // ── заголовок ──
        f.draw("Конструктор ротации", x + 14, y + 12, 9F, ColorUtil.getColor(235, a));
        String presetLabel = "Профиль: " + activePreset;
        f.draw(presetLabel, x + 14, y + 27, 6F, ColorUtil.replAlpha(accent, a * 0.9F));

        float closeS = 16F;
        float cx = x + w - closeS - 8F;
        float cyC = y + 8F;
        closeRect = new float[]{cx, cyC, closeS, closeS};
        boolean cHov = MathUtil.isHovered(mouseX, mouseY, cx, cyC, closeS, closeS);
        RenderUtil.Render2D.rect(cx, cyC, closeS, closeS, ColorUtil.getColor(255, a * (cHov ? 0.10F : 0.04F)), 5F);
        RenderUtil.Render2D.outline(cx, cyC, closeS, closeS, 0.5F, ColorUtil.getColor(255, a * (cHov ? 0.45F : 0.15F)), 5F);
        f.drawCentered("X", cx + closeS / 2F, cyC + 5F, 6.5F, ColorUtil.getColor(255, a * (cHov ? 0.95F : 0.55F)));

        // сброс — в шапке слева от крестика, чтобы не пересекался с пресетами
        float resetW = 46F;
        float resetX = cx - resetW - 6F;
        resetRect = new float[]{resetX, cyC, resetW, closeS};
        boolean rHovH = MathUtil.isHovered(mouseX, mouseY, resetX, cyC, resetW, closeS);
        RenderUtil.Render2D.rect(resetX, cyC, resetW, closeS, ColorUtil.getColor(255, 60, 60, a * (rHovH ? 0.14F : 0.05F)), 5F);
        RenderUtil.Render2D.outline(resetX, cyC, resetW, closeS, 0.5F, ColorUtil.getColor(255, 90, 90, a * (rHovH ? 0.65F : 0.28F)), 5F);
        f.drawCentered("Сброс", resetX + resetW / 2F, cyC + 4.5F, 6F, ColorUtil.getColor(255, 130, 130, a * (rHovH ? 1F : 0.8F)));

        // ── пресеты: переносятся на новую строку, если не влезают ──
        chipNames.clear();
        chipRects.clear();
        float chipY = y + 44F;
        float chipX = x + 12F;
        float chipMaxX = x + w - 12F;
        for (String name : PRESETS.keySet()) {
            float tw = f.getWidth(name, 6F) + 12F;
            if (chipX + tw > chipMaxX) {
                chipX = x + 12F;
                chipY += 20F;
            }
            boolean hov = MathUtil.isHovered(mouseX, mouseY, chipX, chipY, tw, 16F);
            boolean act = name.equals(activePreset);
            RenderUtil.Render2D.rect(chipX, chipY, tw, 16F, ColorUtil.overCol(
                    ColorUtil.getColor(255, a * (hov ? 0.09F : 0.04F)),
                    ColorUtil.replAlpha(accent, a * (act ? 0.45F : 0.20F)), hov ? 1F : 0F), 4F);
            RenderUtil.Render2D.outline(chipX, chipY, tw, 16F, 0.5F,
                    ColorUtil.replAlpha(accent, a * (act ? 0.85F : (hov ? 0.65F : 0.22F))), 4F);
            f.draw(name, chipX + 6F, chipY + 4.5F, 6F,
                    ColorUtil.getColor(act ? 250 : 225, a * (hov ? 1F : (act ? 0.95F : 0.75F))));
            chipNames.add(name);
            chipRects.add(new float[]{chipX, chipY, tw, 16F});
            chipX += tw + 4F;
        }
        float presetsBottom = chipY + 16F;

        // ── вкладки (левая колонка) ──
        float tabX = x + 12F;
        float tabY = presetsBottom + 6F;
        float tabW = 104F;
        for (int i = 0; i < TABS.length; i++) {
            boolean act = i == activeTab;
            boolean hov = MathUtil.isHovered(mouseX, mouseY, tabX, tabY, tabW, 24F);
            tabRects[i] = new float[]{tabX, tabY, tabW, 24F};

            RenderUtil.Render2D.rect(tabX, tabY, tabW, 24F, ColorUtil.overCol(
                    ColorUtil.getColor(255, a * (hov && !act ? 0.06F : 0.03F)),
                    ColorUtil.replAlpha(accent, a * 0.22F), act ? 1F : 0F), 6F);
            if (act)
                RenderUtil.Render2D.outline(tabX, tabY, tabW, 24F, 0.6F, ColorUtil.replAlpha(accent, a * 0.85F), 6F);
            RenderUtil.Render2D.rect(tabX + 4F, tabY + 19F, (tabW - 8F) * (act ? 1F : (hov ? 0.35F : 0F)), 1.2F,
                    ColorUtil.replAlpha(accent, a * (act ? 0.9F : 0.4F)), 1F);
            f.draw(TABS[i], tabX + 10F, tabY + 8.5F, 7F, ColorUtil.getColor(act ? 240 : 200, a * (act ? 1F : 0.7F)));
            tabY += 30F;
        }

        // ── превью (низ левой колонки) ──
        float pvX = x + 12F;
        float pvY = tabY + 4F;
        float pvW = tabW;
        float pvH = y + h - pvY - 12F;
        RenderUtil.Render2D.rect(pvX, pvY, pvW, pvH, ColorUtil.getColor(0, 0.25F * a), 6F);
        RenderUtil.Render2D.outline(pvX, pvY, pvW, pvH, 0.5F, ColorUtil.getColor(255, a * 0.12F), 6F);

        float bx = pvX + pvW / 2F;
        float by = pvY + pvH / 2F + 26F;

        int limb = ColorUtil.getColor(185, a * 0.30F);
        // ноги
        RenderUtil.Render2D.rect(bx - 8F, by - 16F, 7F, 16F, limb, 1.5F);
        RenderUtil.Render2D.rect(bx + 1F, by - 16F, 7F, 16F, limb, 1.5F);
        // корпус
        RenderUtil.Render2D.rect(bx - 9F, by - 34F, 18F, 18F, ColorUtil.getColor(205, a * 0.38F), 2F);
        // руки
        RenderUtil.Render2D.rect(bx - 15F, by - 33F, 5F, 15F, limb, 1.5F);
        RenderUtil.Render2D.rect(bx + 10F, by - 33F, 5F, 15F, limb, 1.5F);
        // голова
        RenderUtil.Render2D.rect(bx - 6F, by - 48F, 12F, 13F, ColorUtil.getColor(225, a * 0.45F), 2F);

        // точка наведения: живёт от осцилляции и рандома
        long ms = System.currentTimeMillis();
        float t = ms / 1000F;
        float ox = (float) Math.sin(t * 2.1) * aura().cOscX.getValue() * 8F
                + (float) Math.sin(ms / 260.0) * aura().cRandomYaw.getValue() * 1.5F;
        float oy = -(float) Math.cos(t * 1.7) * aura().cOscY.getValue() * 6F
                + (float) Math.sin(ms / 300.0) * aura().cRandomPitch.getValue() * 1.5F;
        float px2 = bx + ox;
        float py2 = by - 28F + oy;

        int red = ColorUtil.getColor(255, 70, 70, a);
        RenderUtil.Render2D.glow(px2 - 5F, py2 - 5F, 10F, 10F, ColorUtil.getColor(255, 60, 60, a * 0.35F), 5F, 7, 1);
        RenderUtil.Render2D.rect(px2 - 3.5F, py2 - 0.4F, 7F, 0.8F, red, 0.5F);
        RenderUtil.Render2D.rect(px2 - 0.4F, py2 - 3.5F, 0.8F, 7F, red, 0.5F);

        f.drawCentered("Превью наведения", pvX + pvW / 2F, pvY + pvH - 9F, 5.5F, ColorUtil.getColor(200, a * 0.55F));

        // ── слайдеры активной вкладки ──
        float sx = x + 128F;
        float sy = y + 56F;
        float sw = x + w - 14F - sx;
        float sh = y + h - 14F - sy;

        RenderUtil.Render2D.rect(sx, sy, sw, sh, ColorUtil.getColor(0, 0.22F * a), 8F);
        Scissor.enable(sx, sy, sw, sh, 2);

        SliderSetting[] settings = tabSettings();
        sliderRects.clear();

        float rowY = sy + 8F;
        float rowH = 36F;
        for (SliderSetting s : settings) {
            if (dragging == s) {
                float perc = MathUtil.clamp((mouseX - (sx + 10F)) / (sw - 20F), 0F, 1F);
                float nv = MathUtil.clamp(Math.round((s.min + (s.max - s.min) * perc) / s.increment) * s.increment, s.min, s.max);
                if (nv != s.getValue()) s.set(nv);
            }

            float trackX = sx + 10F;
            float trackW = sw - 20F;
            float perc = MathUtil.clamp((s.getValue() - s.min) / (s.max - s.min), 0F, 1F);

            boolean hov = MathUtil.isHovered(mouseX, mouseY, sx, rowY, sw, rowH);
            f.draw(s.getName(), trackX, rowY + 3F, 6.5F, ColorUtil.getColor(230, a * (hov ? 1F : 0.8F)));
            f.draw(fmt(s.getValue()), trackX + trackW - f.getWidth(fmt(s.getValue()), 6F), rowY + 3.5F, 6F,
                    ColorUtil.replAlpha(accent, a * 0.95F));

            RenderUtil.Render2D.rect(trackX, rowY + 17F, trackW, 3F, ColorUtil.getColor(255, a * 0.10F), 1.5F);
            RenderUtil.Render2D.rect(trackX, rowY + 17F, trackW * perc, 3F, ColorUtil.replAlpha(accent, a * 0.85F), 1.5F);
            RenderUtil.Render2D.rect(trackX + trackW * perc - 3.5F, rowY + 15F, 7F, 7F, ColorUtil.getColor(240, a), 7F);
            RenderUtil.Render2D.rect(trackX + trackW * perc - 2.5F, rowY + 16F, 5F, 5F, ColorUtil.replAlpha(accent, a), 7F);

            sliderRects.add(new float[]{sx, rowY, sw, rowH});
            rowY += rowH;
        }

        Scissor.reset();

        // live-обновление во время драга уже выше; здесь только рендер
        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = (float) (click.x() / scaleFix);
        float my = (float) (click.y() / scaleFix);
        mouseX = mx;
        mouseY = my;

        if (closeRect != null && MathUtil.isHovered(mx, my, closeRect[0], closeRect[1], closeRect[2], closeRect[3])) {
            close();
            return true;
        }

        if (resetRect != null && MathUtil.isHovered(mx, my, resetRect[0], resetRect[1], resetRect[2], resetRect[3])) {
            applyValues(DEFAULTS);
            activePreset = "Custom";
            return true;
        }

        for (int i = 0; i < chipRects.size(); i++) {
            float[] r = chipRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                String name = chipNames.get(i);
                applyValues(PRESETS.get(name));
                activePreset = name;
                return true;
            }
        }

        for (int i = 0; i < TABS.length; i++) {
            float[] r = tabRects[i];
            if (r != null && MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                activeTab = i;
                dragging = null;
                return true;
            }
        }

        SliderSetting[] settings = tabSettings();
        for (int i = 0; i < sliderRects.size() && i < settings.length; i++) {
            float[] r = sliderRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                dragging = settings[i];
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    private void applyValues(float[] v) {
        SliderSetting[] all = {
                aura().cYawMin, aura().cYawMax, aura().cPitchMin, aura().cPitchMax,
                aura().cHitYaw, aura().cHitPitch, aura().cRandomYaw, aura().cRandomPitch,
                aura().cOscX, aura().cOscY
        };
        for (int i = 0; i < all.length && i < v.length; i++) {
            SliderSetting s = all[i];
            s.set(MathUtil.clamp(v[i], s.min, s.max));
        }
    }
}
