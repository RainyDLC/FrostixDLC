package dev.hatek.client.render.particles;

import net.minecraft.util.Mth;

/**
 * Цветовые утилиты частиц. Все цвета — ARGB int.
 * Чистая реализация с нуля.
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
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Возвращает цвет с альфа-каналом, умноженным на alpha 0..1. */
    public static int multAlpha(int argb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /** Линейная интерполяция двух ARGB-цветов, t 0..1. */
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

    /**
     * Бегущая радуга. speed — миллисекунд на градус круга, index — сдвиг фазы
     * (0..360), чтобы частицы не мигали синхронно.
     */
    public static int rainbow(int speed, int index, float saturation, float brightness, float alpha) {
        long angle = (System.currentTimeMillis() / Math.max(1, speed) + index) % 360L;
        float h = (float) angle / 360.0f;
        int rgb = java.awt.Color.HSBtoRGB(h,
                Mth.clamp(saturation, 0.0f, 1.0f),
                Mth.clamp(brightness, 0.0f, 1.0f));
        return rgba((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF,
                Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    /**
     * Пульсация между двумя цветами: треугольная волна 0→180→0 градусов,
     * index — сдвиг фазы.
     */
    public static int fade(int speed, int index, int first, int second) {
        long angle = (System.currentTimeMillis() / Math.max(1, speed) + index) % 360L;
        float half = angle >= 180L ? 360L - angle : angle;
        return lerpColor(first, second, half / 180.0f);
    }
}
