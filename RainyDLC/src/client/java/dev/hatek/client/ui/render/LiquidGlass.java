package dev.hatek.client.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Преломление мира под стеклом. Снимок LiquidBackdrop рисуется кусками с искажёнными UV:
 * в полосе EDGE у края стекло показывает мир, который на самом деле за кромкой, сжатым —
 * как толстая линза (src = t - K * (1 - u)^3, u = t / E: у края смотрим на K px наружу,
 * сжатие 1 + 3K/E, к середине стык гладкий, наклон 1),
 * плюс лёгкий хроматический сдвиг по краю. Середина — без искажений, одним квадом на колонку.
 * Скругление углов — построчно (у верхней/нижней полосы своя ширина ряда).
 */
public final class LiquidGlass {
    /** GL-текстура фреймбуфера лежит снизу вверх. Если стекло окажется перевёрнутым — false. */
    private static final boolean FLIP_Y = true;
    private static final int EDGE = 40;
    private static final float BEND = 0.45f;
    private static final int SEGMENTS = 10;
    private static final int ROW = 2;
    private static final float FRINGE = 3.5f;
    private static final float FRINGE_ALPHA = 0.18f;
    private static final int FRINGE_WARM = 0xFFFF5A7A;
    private static final int FRINGE_COLD = 0xFF5AC8FF;

    private LiquidGlass() {
    }

    /**
     * @param offsetX/offsetY смещение pose относительно экрана (анимация открытия),
     *                        чтобы брать из снимка ровно то, что под панелью на экране.
     */
    public static void render(GuiGraphicsExtractor gg, float x, float y, int w, int h, float r,
                              float offsetX, float offsetY) {
        if (!LiquidBackdrop.ready()) {
            return;
        }
        int edge = Math.min(EDGE, Math.min(w, h) / 2);
        float radius = Math.min(r, edge);
        float alpha = Render2D.alpha();
        float[] cols = breakpoints(w, edge);

        // Основа + два цветных сдвинутых слоя только в полосе края.
        pass(gg, x, y, w, h, radius, edge, cols, offsetX, offsetY, 0.0f, Render2D.withAlpha(0xFFFFFFFF, alpha), false);
        pass(gg, x, y, w, h, radius, edge, cols, offsetX, offsetY, FRINGE,
                Render2D.withAlpha(FRINGE_WARM, alpha * FRINGE_ALPHA), true);
        pass(gg, x, y, w, h, radius, edge, cols, offsetX, offsetY, -FRINGE,
                Render2D.withAlpha(FRINGE_COLD, alpha * FRINGE_ALPHA), true);
    }

    private static void pass(GuiGraphicsExtractor gg, float x, float y, int w, int h, float r, int edge,
                             float[] cols, float ox, float oy, float shift, int color, boolean bandOnly) {
        // Верхняя и нижняя полосы — построчно.
        for (int ry = 0; ry < edge; ry += ROW) {
            row(gg, x, y, w, h, r, edge, cols, ox, oy, shift, color, ry, Math.min(ROW, edge - ry));
        }
        for (int ry = h - edge; ry < h; ry += ROW) {
            row(gg, x, y, w, h, r, edge, cols, ox, oy, shift, color, ry, Math.min(ROW, h - ry));
        }
        // Середина по высоте: вертикального искажения нет, колонки целиком.
        float top = edge;
        float bottom = h - edge;
        if (bottom <= top) {
            return;
        }
        for (int i = 0; i + 1 < cols.length; i++) {
            float a = cols[i];
            float b = cols[i + 1];
            boolean band = a < edge || b > w - edge;
            if (bandOnly && !band) {
                continue;
            }
            quad(gg, x + a, y + top, x + b, y + bottom,
                    x + ox + map(a, w, edge, shift), y + oy + top,
                    x + ox + map(b, w, edge, shift), y + oy + bottom, color);
        }
    }

    private static void row(GuiGraphicsExtractor gg, float x, float y, int w, int h, float r, int edge,
                            float[] cols, float ox, float oy, float shift, int color, int ry, int rh) {
        if (rh <= 0) {
            return;
        }
        float center = ry + rh / 2.0f;
        float inset = 0.0f;
        float dy = center < r ? r - center : center > h - r ? center - (h - r) : 0.0f;
        if (dy > 0.0f) {
            inset = r - (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy));
        }
        float sy0 = y + oy + map(ry, h, edge, shift);
        float sy1 = y + oy + map(ry + rh, h, edge, shift);
        for (int i = 0; i + 1 < cols.length; i++) {
            float a = Math.max(cols[i], inset);
            float b = Math.min(cols[i + 1], w - inset);
            if (b <= a) {
                continue;
            }
            quad(gg, x + a, y + ry, x + b, y + ry + rh,
                    x + ox + map(a, w, edge, shift), sy0,
                    x + ox + map(b, w, edge, shift), sy1, color);
        }
    }

    /** Локальная координата -> локальная координата в снимке. */
    private static float map(float p, float length, int edge, float shift) {
        if (p < edge) {
            return depth(p, edge, shift);
        }
        if (p > length - edge) {
            return length - depth(length - p, edge, shift);
        }
        return p;
    }

    private static float depth(float t, int edge, float shift) {
        float u = Mth.clamp(t / edge, 0.0f, 1.0f);
        float k = 1.0f - u;
        return t - BEND * edge * k * k * k + shift * k;
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
    private static void quad(GuiGraphicsExtractor gg, float x0, float y0, float x1, float y1,
                             float sx0, float sy0, float sx1, float sy1, int color) {
        int ix0 = Math.round(x0);
        int iy0 = Math.round(y0);
        int iw = Math.round(x1) - ix0;
        int ih = Math.round(y1) - iy0;
        if (iw <= 0 || ih <= 0 || (color >>> 24) == 0) {
            return;
        }
        int texW = LiquidBackdrop.width();
        int texH = LiquidBackdrop.height();
        float u = Mth.clamp(sx0, 0.0f, texW - 1.0f);
        int uw = Math.max(1, Math.round(sx1 - sx0));
        float span = Math.max(1.0f, sy1 - sy0);
        float v = FLIP_Y ? texH - sy0 : sy0;
        int vh = Math.round(FLIP_Y ? -span : span);
        gg.blit(RenderPipelines.GUI_TEXTURED, LiquidBackdrop.ID, ix0, iy0, u, v, iw, ih, uw, vh,
                texW, texH, color);
    }
}
