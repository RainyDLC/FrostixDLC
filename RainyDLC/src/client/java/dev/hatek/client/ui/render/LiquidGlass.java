package dev.hatek.client.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Линза из снимка мира. Рисуется непрозрачным пайплайном (альфа main target у мира
 * бывает 0 — через GUI_TEXTURED снимок просто не виден), тонировка запекается в RGB.
 *
 * Отображение по каждой оси (p — локальная координата, c — центр, t — расстояние до края):
 *   src = c + (p - c) * ZOOM  -/+  BEND * E * (1 - t/E)^3     в полосе E у края
 * ZOOM < 1 — середина чуть увеличена; у кромки линза смотрит на BEND*E px наружу
 * и сжимает картинку в (1 + 3*BEND) раз, к середине стык гладкий.
 * Скругление углов — построчно в верхней/нижней полосе.
 */
public final class LiquidGlass {
    /** GL-текстура фреймбуфера лежит снизу вверх. Если стекло окажется перевёрнутым — false. */
    private static final boolean FLIP_Y = true;
    private static final int EDGE = 48;
    private static final float BEND = 0.75f;
    private static final float ZOOM = 0.94f;
    private static final int SEGMENTS = 12;
    private static final int ROW = 2;

    // Текущая сила (анимация открытия: линза "наливается" вместе с панелью).
    private static float bend = BEND;
    private static float zoom = ZOOM;

    private LiquidGlass() {
    }

    /** Чёткий мир поверх ванильного blur — снаружи панели фон остаётся чистым. */
    public static void restoreSharp(GuiGraphicsExtractor gg) {
        int w = LiquidBackdrop.width();
        int h = LiquidBackdrop.height();
        quad(gg, LiquidBackdrop.SHARP, 0, 0, w, h, 0, 0, w, h, 0xFFFFFFFF);
    }

    /**
     * @param offsetY смещение pose относительно экрана (анимация открытия), чтобы брать
     *                из снимка ровно то, что под панелью на экране.
     * @param tint    RGB-множитель (альфа игнорируется — пайплайн непрозрачный).
     */
    public static void render(GuiGraphicsExtractor gg, Identifier texture, float x, float y, int w, int h,
                              float r, float offsetY, int tint, float strength) {
        bend = BEND * strength;
        zoom = 1.0f - (1.0f - ZOOM) * strength;
        int edge = Math.min(EDGE, Math.min(w, h) / 2);
        float radius = Math.min(r, edge);
        int color = 0xFF000000 | (tint & 0x00FFFFFF);
        float[] cols = breakpoints(w, edge);
        float sx = x;
        float sy = y + offsetY;

        for (int ry = 0; ry < edge; ry += ROW) {
            row(gg, texture, x, y, sx, sy, w, h, radius, edge, cols, color, ry, Math.min(ROW, edge - ry));
        }
        for (int ry = h - edge; ry < h; ry += ROW) {
            row(gg, texture, x, y, sx, sy, w, h, radius, edge, cols, color, ry, Math.min(ROW, h - ry));
        }
        // Середина по высоте: вертикальное отображение линейное — колонки целиком.
        float top = edge;
        float bottom = h - edge;
        if (bottom <= top) {
            return;
        }
        for (int i = 0; i + 1 < cols.length; i++) {
            float a = cols[i];
            float b = cols[i + 1];
            quad(gg, texture, x + a, y + top, x + b, y + bottom,
                    sx + map(a, w, edge), sy + map(top, h, edge),
                    sx + map(b, w, edge), sy + map(bottom, h, edge), color);
        }
    }

    private static void row(GuiGraphicsExtractor gg, Identifier texture, float x, float y, float sx, float sy,
                            int w, int h, float r, int edge, float[] cols, int color, int ry, int rh) {
        if (rh <= 0) {
            return;
        }
        float center = ry + rh / 2.0f;
        float dy = center < r ? r - center : center > h - r ? center - (h - r) : 0.0f;
        float inset = dy > 0.0f ? r - (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy)) : 0.0f;
        float v0 = sy + map(ry, h, edge);
        float v1 = sy + map(ry + rh, h, edge);
        for (int i = 0; i + 1 < cols.length; i++) {
            float a = Math.max(cols[i], inset);
            float b = Math.min(cols[i + 1], w - inset);
            if (b <= a) {
                continue;
            }
            quad(gg, texture, x + a, y + ry, x + b, y + ry + rh,
                    sx + map(a, w, edge), v0, sx + map(b, w, edge), v1, color);
        }
    }

    /** Локальная координата -> локальная координата в снимке. */
    private static float map(float p, float length, int edge) {
        float c = length / 2.0f;
        float base = c + (p - c) * zoom;
        float t = Math.min(p, length - p);
        if (t >= edge) {
            return base;
        }
        float k = 1.0f - Mth.clamp(t / edge, 0.0f, 1.0f);
        float shift = bend * edge * k * k * k;
        return p < c ? base - shift : base + shift;
    }

    private static float[] breakpoints(int w, int edge) {
        List<Float> out = new ArrayList<>();
        for (int i = 0; i <= SEGMENTS; i++) {
            out.add(edge * i / (float) SEGMENTS);
        }
        for (int i = 0; i <= SEGMENTS; i++) {
            float p = w - edge + edge * i / (float) SEGMENTS;
            if (p > out.get(out.size() - 1)) {
                out.add(p);
            }
        }
        float[] cols = new float[out.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = out.get(i);
        }
        return cols;
    }

    /** Экранный прямоугольник (x0..x1, y0..y1) <- область снимка (sx0..sx1, sy0..sy1) в пикселях экрана. */
    private static void quad(GuiGraphicsExtractor gg, Identifier texture, float x0, float y0, float x1, float y1,
                             float sx0, float sy0, float sx1, float sy1, int color) {
        int ix0 = Math.round(x0);
        int iy0 = Math.round(y0);
        int iw = Math.round(x1) - ix0;
        int ih = Math.round(y1) - iy0;
        if (iw <= 0 || ih <= 0) {
            return;
        }
        int texW = LiquidBackdrop.width();
        int texH = LiquidBackdrop.height();
        float u = Mth.clamp(sx0, 0.0f, texW - 1.0f);
        int uw = Math.max(1, Math.round(sx1 - sx0));
        float span = Math.max(1.0f, sy1 - sy0);
        float v = FLIP_Y ? texH - sy0 : sy0;
        int vh = Math.round(FLIP_Y ? -span : span);
        gg.blit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, texture, ix0, iy0, u, v, iw, ih, uw, vh,
                texW, texH, color);
    }
}
