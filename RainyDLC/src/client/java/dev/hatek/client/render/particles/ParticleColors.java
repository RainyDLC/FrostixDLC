package dev.hatek.client.render.particles;

import net.minecraft.util.Mth;

/**
 * Цветовые утилиты частиц: радуга, градиентное затухание, палитра.
 * Порт rtx.kimiko ParticleColors + нужные методы их ColorEngine
 * (rainbow/lerpColor/multAlpha/rgba/alpha), переписанные чистым Java.
 * Все цвета — ARGB int.
 */
public final class ParticleColors {
    public static final String CLIENT = "Клиент";
    public static final String RAINBOW = "Радуга";
    public static final String CUSTOM = "Свой";

    private ParticleColors() {
    }

    public static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Умножает альфа-канал цвета на alpha 0..1. */
    public static int multAlpha(int argb, float alpha) {
        int a = Math.round(afloatClamp(alpha) * 255.0f);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static float afloatClamp(float alpha) {
        return Mth.clamp(alpha, 0.0f, 1.0f);
    }

    /** Линейная интерполяция двух ARGB-цветов. */
    public static int lerpColor(int first, int second, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = Math.round(lerp((first >>> 24) & 0xFF, (second >>> 24) & 0xFF, t));
        int r = Math.round(lerp((first >>> 16) & 0xFF, (second >>> 16) & 0xFF, t));
        int g = Math.round(lerp((first >>> 8) & 0xFF, (second >>> 8) & 0xFF, t));
        int b = Math.round(lerp(first & 0xFF, second & 0xFF, t));
        return rgba(r, g, b, a);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** HSB (0..360°) → RGB, альфа задаётся отдельно. */
    public static int rainbowColor(int angle, float saturation, float brightness) {
        float h = ((angle % 360) + 360) % 360 / 360.0f;
        int rgb = java.awt.Color.HSBtoRGB(h, Mth.clamp(saturation, 0.0f, 1.0f), Mth.clamp(brightness, 0.0f, 1.0f));
        return rgb | 0xFF000000;
    }

    /** Бегущая радуга: speed — мс на градус, index — сдвиг фазы. */
    public static int rainbow(int speed, int index, float saturation, float brightness, float alpha) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        int rgb = rainbowColor(angle, saturation, brightness);
        return rgba((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF,
                Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    /** Пульсация между двумя цветами (треугольная волна 0→180→0). */
    public static int fade(int speed, int index, int first, int second) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        angle = angle >= 180 ? 360 - angle : angle;
        return lerpColor(first, second, angle / 180.0f);
    }

    /** Плавный переход по круговой палитре. */
    public static int paletteFade(int speed, int index, int[] palette) {
        int n = palette.length;
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360L);
        float f = (float) angle / 360.0f * n;
        int i = (int) f % n;
        int j = (i + 1) % n;
        int a = palette[i] | 0xFF000000;
        int b = palette[j] | 0xFF000000;
        return lerpColor(a, b, f - (float) Math.floor(f)) | 0xFF000000;
    }
}
