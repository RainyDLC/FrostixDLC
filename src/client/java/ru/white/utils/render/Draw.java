package ru.white.utils.render;

import net.minecraft.util.Identifier;
import ru.white.Client;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorUtil;

/**
 * Единая точка входа для 2D-рендера: {@code Draw.rect(...)}, {@code Draw.blur(...)},
 * {@code Draw.outline(...)} и т.д. Вместо цепочек
 * {@code Client.get().render2D().getXxxPipeline().drawXxx(...)}.
 *
 * Старый {@link RenderUtil} делегирует сюда — старые вызовы продолжают работать.
 */
public final class Draw {

    // Параметры «стеклянного» блюра, когда включено искажение темы.
    // Ровно те значения, что реально применялись раньше (были захардкожены в BlurPipeline).
    private static final float GLASS_DISTORTION = 125f;
    private static final float GLASS_WAVE_SIZE = 75f;

    // Скретч-буферы вместо new float[]/new int[] на каждый примитив.
    // Пайплайны читают эти массивы синхронно (копируют в ByteBuffer) и нигде
    // не сохраняют ссылку, поэтому переиспользование безопасно. Весь 2D-рендер
    // идёт с одного (рендерного) потока.
    private static final float[] RADII = new float[4];
    private static final int[] COLORS4 = new int[4];
    private static final int[] COLORS8 = new int[8];
    private static final int[] COLORS9 = new int[9];
    private static final float[] THICKNESS8 = new float[8];

    private static float[] radii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        RADII[0] = topLeft;
        RADII[1] = topRight;
        RADII[2] = bottomRight;
        RADII[3] = bottomLeft;
        return RADII;
    }

    private static float[] radii(float radius) {
        return radii(radius, radius, radius, radius);
    }

    private Draw() {
    }

    private static Render2D r2d() {
        return Client.get().render2D();
    }

    public static void flush() {
        r2d().flushAll();
    }

    // ── блюр ─────────────────────────────────────────────────────────────

    public static void blur(float x, float y, float width, float height, float alpha, int tintColor) {
        blur(x, y, width, height, alpha, 0f, 0f, 0f, 0f, tintColor);
    }

    public static void blur(float x, float y, float width, float height, float alpha,
                            float radius, int tintColor) {
        blur(x, y, width, height, alpha, radius, radius, radius, radius, tintColor);
    }

    public static void blur(float x, float y, float width, float height, float alpha,
                            float topLeft, float topRight, float bottomRight, float bottomLeft,
                            int tintColor) {
        float[] radii = radii(topLeft, topRight, bottomRight, bottomLeft);
        BlurPipeline pipeline = r2d().getBlurPipeline();
        if (ThemeColor.getDistortion()) {
            pipeline.drawGlassBlur(x, y, width, height, alpha, radii, tintColor,
                    GLASS_DISTORTION, GLASS_WAVE_SIZE, 0f, 0f);
        } else {
            pipeline.drawBlur(x, y, width, height, alpha, radii, tintColor);
        }
    }

    /** Всегда «стеклянный» блюр, независимо от настройки темы. */
    public static void glass(float x, float y, float width, float height,
                             float alpha, float topLeft, float topRight,
                             float bottomRight, float bottomLeft, int tintColor) {
        r2d().getBlurPipeline().drawGlassBlur(x, y, width, height, alpha,
                radii(topLeft, topRight, bottomRight, bottomLeft), tintColor,
                GLASS_DISTORTION, GLASS_WAVE_SIZE, 0f, 0f);
    }

    // ── заливки ──────────────────────────────────────────────────────────

    public static void rect(float x, float y, float width, float height, int color) {
        rect(x, y, width, height, color, 0f);
    }

    public static void rect(float x, float y, float width, float height, int color, float radius) {
        rect(x, y, width, height, color, radius, radius, radius, radius);
    }

    public static void rect(float x, float y, float width, float height, int color,
                            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        for (int i = 0; i < 9; i++) {
            COLORS9[i] = color;
        }
        r2d().getRectPipeline().drawRect(x, y, width, height, COLORS9,
                radii(topLeft, topRight, bottomRight, bottomLeft));
    }

    public static void gradientRect(float x, float y, float width, float height,
                                    int[] colors, float radius) {
        gradientRect(x, y, width, height, colors, radius, radius, radius, radius);
    }

    public static void gradientRect(float x, float y, float width, float height,
                                    int[] colors, float topLeft, float topRight,
                                    float bottomRight, float bottomLeft) {
        r2d().getRectPipeline().drawRect(x, y, width, height, colors,
                radii(topLeft, topRight, bottomRight, bottomLeft));
    }

    public static void glow(float x, float y, float width, float height,
                            int color, float radius, float glowSize, float strength) {
        glow(x, y, width, height, color, radius, radius, radius, radius, glowSize, strength, 1f);
    }

    public static void glow(float x, float y, float width, float height,
                            int color, float radius, float glowSize, float strength, float softness) {
        glow(x, y, width, height, color, radius, radius, radius, radius, glowSize, strength, softness);
    }

    public static void glow(float x, float y, float width, float height,
                            int color, float topLeft, float topRight, float bottomRight, float bottomLeft,
                            float glowSize, float strength, float softness) {
        r2d().getRectPipeline().drawGlow(x, y, width, height, color,
                radii(topLeft, topRight, bottomRight, bottomLeft), glowSize, strength, softness);
    }

    // ── обводки ──────────────────────────────────────────────────────────

    public static void outline(float x, float y, float width, float height, float thickness, int color) {
        outline(x, y, width, height, thickness, color, 0f, 0f, 0f, 0f);
    }

    public static void outline(float x, float y, float width, float height,
                               float thickness, int color, float radius) {
        outline(x, y, width, height, thickness, color, radius, radius, radius, radius);
    }

    public static void outline(float x, float y, float width, float height, float thickness, int color,
                               float topLeft, float topRight, float bottomRight, float bottomLeft) {
        for (int i = 0; i < 8; i++) {
            COLORS8[i] = color;
            THICKNESS8[i] = thickness;
        }
        r2d().getOutlinePipeline().drawOutline(x, y, width, height, COLORS8, THICKNESS8,
                radii(topLeft, topRight, bottomRight, bottomLeft), 1.0f);
    }

    public static void glassOutline(float x, float y, float width, float height,
                                    float thickness, float radius, float alpha, float shine) {
        glassOutline(x, y, width, height, thickness, radius, radius, radius, radius, alpha, shine);
    }

    public static void glassOutline(float x, float y, float width, float height,
                                    float thickness, float topLeft, float topRight,
                                    float bottomRight, float bottomLeft, float alpha, float shine) {
        float base = Math.max(0f, alpha);
        float hot = Math.max(0f, shine);

        COLORS8[0] = glassColor(base * 0.42f);
        COLORS8[1] = glassColor(base * (0.55f + hot * 0.30f));
        COLORS8[2] = glassColor(base * (0.28f + hot * 0.10f));
        COLORS8[3] = glassColor(base * 0.24f);
        COLORS8[4] = glassColor(base * (0.34f + hot * 0.16f));
        COLORS8[5] = glassColor(base * (0.48f + hot * 0.24f));
        COLORS8[6] = glassColor(base * 0.30f);
        COLORS8[7] = glassColor(base * (0.62f + hot * 0.38f));

        THICKNESS8[0] = thickness;
        THICKNESS8[1] = thickness + hot * 0.18f;
        THICKNESS8[2] = thickness * 0.86f;
        THICKNESS8[3] = thickness * 0.78f;
        THICKNESS8[4] = thickness * 0.9f;
        THICKNESS8[5] = thickness + hot * 0.16f;
        THICKNESS8[6] = thickness * 0.84f;
        THICKNESS8[7] = thickness + hot * 0.24f;

        r2d().getOutlinePipeline().drawOutline(x, y, width, height, COLORS8, THICKNESS8,
                radii(topLeft, topRight, bottomRight, bottomLeft), 1.35f);
    }

    private static int glassColor(float alpha) {
        return ColorUtil.getColor(255, Math.min(alpha, 1f));
    }

    // ── текстуры ─────────────────────────────────────────────────────────

    public static void texture(Identifier id, float x, float y, float width, float height, int color) {
        texture(id, x, y, width, height, 0, 0, 1, 1, color, 1f, 0f);
    }

    public static void texture(Identifier id, float x, float y, float width, float height,
                               float smoothness, float radius, int color) {
        texture(id, x, y, width, height, 0, 0, 1, 1, color, smoothness, radius);
    }

    public static void texture(Identifier id, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1,
                               int color, float smoothness, float radius) {
        COLORS4[0] = color;
        COLORS4[1] = color;
        COLORS4[2] = color;
        COLORS4[3] = color;
        r2d().getTexturePipeline()
                .drawTexture(id, x, y, width, height, u0, v0, u1, v1, COLORS4, radii(radius), smoothness);
    }
}
