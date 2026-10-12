package dev.hatek.client.ambience;

/**
 * «Sky Shader»: пост-обработка текстур неба Ambience — мягкий
 * двухмасштабный bloom, цветокоррекция (насыщенность + S-кривая
 * контраста) и дизеринг против полос на градиентах. Работает
 * в премультиплицированном пространстве, поэтому годится и для
 * непрозрачных, и для аддитивных/полупрозрачных слоёв.
 */
public final class SkyPost {
    private static final int DOWN = 4;
    private static final float THRESHOLD = 0.5f;

    private SkyPost() {
    }

    public static void apply(int[] argb, int w, int h, boolean wrapX, boolean opaque,
                             double bloom, double saturation) {
        int n = w * h;
        float[] p = new float[n * 3];
        float[] alpha = new float[n];
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            float a = opaque ? 1.0f : (c >>> 24) / 255.0f;
            alpha[i] = a;
            p[i * 3] = (c >> 16 & 255) / 255.0f * a;
            p[i * 3 + 1] = (c >> 8 & 255) / 255.0f * a;
            p[i * 3 + 2] = (c & 255) / 255.0f * a;
        }
        if (bloom > 0.001) {
            addBloom(p, w, h, wrapX, (float) bloom);
        }
        float sat = (float) saturation;
        java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
            int k = i * 3;
            float r = p[k];
            float g = p[k + 1];
            float b = p[k + 2];
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            r = curve(lum + (r - lum) * sat);
            g = curve(lum + (g - lum) * sat);
            b = curve(lum + (b - lum) * sat);
            float a = opaque ? 1.0f : Math.min(1.0f, Math.max(alpha[i], Math.max(r, Math.max(g, b))));
            if (a <= 0.0f) {
                argb[i] = 0;
                return;
            }
            float inv = 1.0f / a;
            float d = dither(i);
            int ir = q(r * inv + d);
            int ig = q(g * inv + d);
            int ib = q(b * inv + d);
            argb[i] = q(a) << 24 | ir << 16 | ig << 8 | ib;
        });
    }

    private static float curve(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        float s = x * x * (3.0f - 2.0f * x);
        return x * 0.78f + s * 0.22f;
    }

    private static float dither(int i) {
        int x = i * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return ((x & 0xFFFF) / 65535.0f - 0.5f) / 255.0f;
    }

    private static int q(float v) {
        int i = Math.round(v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    private static void addBloom(float[] p, int w, int h, boolean wrapX, float strength) {
        int lw = Math.max(1, w / DOWN);
        int lh = Math.max(1, h / DOWN);
        float[] lo = new float[lw * lh * 3];
        float norm = 1.0f / (DOWN * DOWN);
        for (int y = 0; y < lh; y++) {
            for (int x = 0; x < lw; x++) {
                float r = 0;
                float g = 0;
                float b = 0;
                for (int oy = 0; oy < DOWN; oy++) {
                    int row = Math.min(h - 1, y * DOWN + oy) * w;
                    for (int ox = 0; ox < DOWN; ox++) {
                        int k = (row + Math.min(w - 1, x * DOWN + ox)) * 3;
                        r += bright(p[k]);
                        g += bright(p[k + 1]);
                        b += bright(p[k + 2]);
                    }
                }
                int o = (y * lw + x) * 3;
                lo[o] = r * norm;
                lo[o + 1] = g * norm;
                lo[o + 2] = b * norm;
            }
        }
        float[] near = blur(lo, lw, lh, wrapX, 1.6f);
        float[] wide = blur(near, lw, lh, wrapX, 5.0f);
        float kn = 0.45f * strength;
        float kw = 0.55f * strength;
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            float fy = (y + 0.5f) / DOWN - 0.5f;
            int y0 = (int) Math.floor(fy);
            float ay = fy - y0;
            int ya = Math.max(0, Math.min(lh - 1, y0));
            int yb = Math.max(0, Math.min(lh - 1, y0 + 1));
            for (int x = 0; x < w; x++) {
                float fx = (x + 0.5f) / DOWN - 0.5f;
                int x0 = (int) Math.floor(fx);
                float ax = fx - x0;
                int xa = wrapX ? Math.floorMod(x0, lw) : Math.max(0, Math.min(lw - 1, x0));
                int xb = wrapX ? Math.floorMod(x0 + 1, lw) : Math.max(0, Math.min(lw - 1, x0 + 1));
                int k = (y * w + x) * 3;
                for (int c = 0; c < 3; c++) {
                    float vn = lerp2(near, lw, xa, xb, ya, yb, ax, ay, c);
                    float vw = lerp2(wide, lw, xa, xb, ya, yb, ax, ay, c);
                    p[k + c] += vn * kn + vw * kw;
                }
            }
        });
    }

    private static float bright(float v) {
        float t = v - THRESHOLD;
        return t <= 0 ? 0 : t * t / (t + 0.15f);
    }

    private static float lerp2(float[] m, int lw, int xa, int xb, int ya, int yb, float ax, float ay, int c) {
        float a = m[(ya * lw + xa) * 3 + c] * (1 - ax) + m[(ya * lw + xb) * 3 + c] * ax;
        float b = m[(yb * lw + xa) * 3 + c] * (1 - ax) + m[(yb * lw + xb) * 3 + c] * ax;
        return a * (1 - ay) + b * ay;
    }

    /** Раздельный гауссов блюр (X с заворачиванием для equirect). */
    private static float[] blur(float[] src, int w, int h, boolean wrapX, float sigma) {
        int r = Math.max(1, (int) Math.ceil(sigma * 2.5f));
        float[] kernel = new float[r * 2 + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            kernel[i + r] = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            sum += kernel[i + r];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        float[] tmp = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float a = 0;
                float b = 0;
                float c = 0;
                for (int i = -r; i <= r; i++) {
                    int sx = wrapX ? Math.floorMod(x + i, w) : Math.max(0, Math.min(w - 1, x + i));
                    int k = (y * w + sx) * 3;
                    float kv = kernel[i + r];
                    a += src[k] * kv;
                    b += src[k + 1] * kv;
                    c += src[k + 2] * kv;
                }
                int o = (y * w + x) * 3;
                tmp[o] = a;
                tmp[o + 1] = b;
                tmp[o + 2] = c;
            }
        }
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float a = 0;
                float b = 0;
                float c = 0;
                for (int i = -r; i <= r; i++) {
                    int sy = Math.max(0, Math.min(h - 1, y + i));
                    int k = (sy * w + x) * 3;
                    float kv = kernel[i + r];
                    a += tmp[k] * kv;
                    b += tmp[k + 1] * kv;
                    c += tmp[k + 2] * kv;
                }
                int o = (y * w + x) * 3;
                out[o] = a;
                out[o + 1] = b;
                out[o + 2] = c;
            }
        }
        return out;
    }

    /** Анаморфный блик (горизонтальная полоса), белый, альфа = яркость. */
    public static int[] streakSprite(int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = Math.abs((x + 0.5) / w * 2 - 1);
                double dy = Math.abs((y + 0.5) / h * 2 - 1);
                double v = Math.exp(-dy / 0.10) * (Math.exp(-dx * 3.2) * 0.75 + Math.exp(-dx * 14) * 0.6)
                        * Noise.smooth(1.0, 0.85, dx) * Noise.smooth(1.0, 0.6, dy);
                out[y * w + x] = (int) Math.round(255 * Noise.clamp01(v)) << 24 | 0xFFFFFF;
            }
        }
        return out;
    }
}
