package rtx.kimiko.api.modules.impl.Visuals.atmosphere.util;

public final class ColorUtil {

    private ColorUtil() {}

    /** Builds ARGB from 0..1 alpha and 0xRRGGBB. */
    public static int argb(float alpha, int rgb) {
        int a = Math.round(MathUtil.clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Per-channel multiply of two 0xRRGGBB colors. */
    public static int multiply(int rgbA, int rgbB) {
        int r = ((rgbA >> 16) & 0xFF) * ((rgbB >> 16) & 0xFF) / 255;
        int g = ((rgbA >> 8) & 0xFF) * ((rgbB >> 8) & 0xFF) / 255;
        int b = (rgbA & 0xFF) * (rgbB & 0xFF) / 255;
        return (r << 16) | (g << 8) | b;
    }
}
