package dev.hatek.client.ambience;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * Процедурная генерация текстур неба (equirectangular, ARGB).
 * Чистая Java: можно гонять вне игры для превью.
 */
public final class SkyGen {
    public static final int MODE_PLASMA = 0;
    public static final int MODE_SPACE = 1;
    public static final int MODE_MILKY_WAY = 2;
    public static final int MODE_BLACK_HOLE = 3;

    /** Галактическая система координат: «север» и направление на центр. */
    static final double[] GAL_N = norm(-0.33, 0.62, 0.71);
    static final double[] GAL_C = ortho(GAL_N, norm(0.25, 0.75, -0.6));
    static final double[] GAL_B = cross(GAL_N, GAL_C);

    private SkyGen() {
    }

    /** Звезда: направление, цвет, яркость и размер. */
    public static final class Star {
        public final float x;
        public final float y;
        public final float z;
        public final float r;
        public final float g;
        public final float b;
        public final float a;
        public final float size;
        public final boolean bright;

        Star(double x, double y, double z, double r, double g, double b, double a, double size, boolean bright) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            this.r = (float) r;
            this.g = (float) g;
            this.b = (float) b;
            this.a = (float) a;
            this.size = (float) size;
            this.bright = bright;
        }
    }

    // ------------------------------------------------------------ helpers

    static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double x, double y, double z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    private static double[] ortho(double[] n, double[] v) {
        double d = v[0] * n[0] + v[1] * n[1] + v[2] * n[2];
        return norm(v[0] - n[0] * d, v[1] - n[1] * d, v[2] - n[2] * d);
    }

    /** Направление для пикселя equirect-текстуры (Y — вверх). */
    static void dir(int x, int y, int w, int h, double[] out) {
        double lon = (x + 0.5) / w * Math.PI * 2 - Math.PI;
        double lat = Math.PI / 2 - (y + 0.5) / h * Math.PI;
        double c = Math.cos(lat);
        out[0] = c * Math.sin(lon);
        out[1] = Math.sin(lat);
        out[2] = c * Math.cos(lon);
    }

    /** Тонмаппинг HDR → ARGB. */
    static int tone(double r, double g, double b, double alpha) {
        int ir = (int) Math.round(255 * Math.pow(1 - Math.exp(-Math.max(0, r)), 0.8));
        int ig = (int) Math.round(255 * Math.pow(1 - Math.exp(-Math.max(0, g)), 0.8));
        int ib = (int) Math.round(255 * Math.pow(1 - Math.exp(-Math.max(0, b)), 0.8));
        int ia = (int) Math.round(255 * Noise.clamp01(alpha));
        return ia << 24 | ir << 16 | ig << 8 | ib;
    }

    // ------------------------------------------------------------ base sky

    /** Опорная (непрозрачная) текстура неба для режима. */
    public static int[] base(int mode, int w, int h) {
        int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            double[] d = new double[3];
            double[] c = new double[3];
            for (int x = 0; x < w; x++) {
                dir(x, y, w, h, d);
                c[0] = 0;
                c[1] = 0;
                c[2] = 0;
                switch (mode) {
                    case MODE_PLASMA -> plasmaBase(d, c);
                    case MODE_SPACE -> {
                        deepSpace(d, c);
                        milkyWay(d, c, 0.35);
                        nebulae(d, c);
                    }
                    case MODE_MILKY_WAY -> {
                        deepSpace(d, c);
                        milkyWay(d, c, 1.0);
                    }
                    default -> {
                        deepSpace(d, c);
                        milkyWay(d, c, 0.7);
                    }
                }
                out[y * w + x] = tone(c[0], c[1], c[2], 1);
            }
        });
        return out;
    }

    private static void deepSpace(double[] d, double[] c) {
        double n = Noise.fbm(d[0] * 2.2, d[1] * 2.2, d[2] * 2.2, 4);
        c[0] += 0.006 + 0.010 * n;
        c[1] += 0.008 + 0.012 * n;
        c[2] += 0.018 + 0.026 * n;
    }

    private static void plasmaBase(double[] d, double[] c) {
        double n = Noise.fbm(d[0] * 1.6 + 4, d[1] * 1.6, d[2] * 1.6, 4);
        c[0] += 0.03 + 0.05 * n;
        c[1] += 0.008 + 0.01 * n;
        c[2] += 0.06 + 0.08 * n;
    }

    /** Млечный Путь: полоса, балдж, пылевые рифты, HII-области. */
    static void milkyWay(double[] d, double[] c, double strength) {
        double sb = dot(GAL_N, d[0], d[1], d[2]);
        double b = Math.asin(Math.max(-1, Math.min(1, sb)));
        double l = Math.atan2(dot(GAL_B, d[0], d[1], d[2]), dot(GAL_C, d[0], d[1], d[2]));
        if (Math.abs(b) > 0.9) {
            return;
        }
        double width = 0.20 + 0.14 * Math.exp(-l * l / 0.6);
        double wob = (Noise.fbm(d[0] * 2.0 + 9, d[1] * 2.0, d[2] * 2.0, 3) - 0.5) * 0.22;
        double bb = b + wob;
        double band = Math.exp(-(bb / width) * (bb / width));
        double halo = Math.exp(-(bb / (width * 2.6)) * (bb / (width * 2.6)));
        double bulge = Math.exp(-(l * l / 0.40 + bb * bb / 0.06));

        double clouds = Noise.fbm(d[0] * 3.5, d[1] * 3.5, d[2] * 3.5, 5);
        double fine = Noise.fbm(d[0] * 16, d[1] * 16, d[2] * 16, 4);
        double grain = Math.pow(Noise.fbm(d[0] * 70, d[1] * 70, d[2] * 70, 2), 4.0);

        double knots = Noise.smooth(0.45, 0.9, Noise.fbm(d[0] * 7 + 2, d[1] * 7, d[2] * 7, 4));
        double light = band * (0.25 + 1.1 * clouds * clouds) * (0.65 + 0.7 * fine)
                + band * knots * 0.6
                + bulge * (1.3 + 0.8 * fine)
                + halo * 0.18 * clouds
                + band * grain * 0.7;

        // Пылевые рифты — тёмные нити вдоль плоскости.
        double wx = Noise.fbm(d[0] * 3 + 1, d[1] * 3, d[2] * 3, 3);
        double rid = Noise.ridged(d[0] * 4 + wx * 1.5, d[1] * 4 - wx, d[2] * 4 + wx, 6);
        double dustMask = Noise.smooth(0.30, 0.85, rid)
                * Math.exp(-(bb / (width * 0.6)) * (bb / (width * 0.6)));
        double riftW = width * (0.22 + 0.25 * wx);
        double centralRift = Math.exp(-((bb - 0.015) / riftW) * ((bb - 0.015) / riftW))
                * (0.4 + 0.6 * Noise.smooth(0.3, 0.7, clouds));
        double dust = Noise.clamp01(dustMask * 0.75 + centralRift * 0.7);
        light *= 1 - 0.85 * dust;

        double warm = Noise.clamp01(bulge * 1.4 + 0.15);
        double cr = 0.66 + (1.00 - 0.66) * warm;
        double cg = 0.72 + (0.82 - 0.72) * warm;
        double cb = 1.00 + (0.62 - 1.00) * warm;

        double hii = Math.pow(Math.max(0, Noise.fbm(d[0] * 22 + 3, d[1] * 22, d[2] * 22, 4) - 0.62) / 0.38, 2)
                * band * (1 - dust);

        double k = 0.55 * strength;
        c[0] += light * cr * k + hii * 0.9 * strength + dust * band * 0.02 * strength;
        c[1] += light * cg * k + hii * 0.18 * strength;
        c[2] += light * cb * k + hii * 0.45 * strength;
    }

    private static final double[][] NEB_DIR = {
            norm(0.6, 0.45, 0.66), norm(-0.7, 0.3, -0.2), norm(0.1, 0.8, -0.6), norm(-0.3, -0.1, 0.95)
    };
    private static final double[][] NEB_COL = {
            {0.15, 0.75, 0.85}, {0.95, 0.25, 0.65}, {1.0, 0.5, 0.18}, {0.45, 0.3, 1.0}
    };

    /** Цветные эмиссионные туманности для режима «Космос». */
    private static void nebulae(double[] d, double[] c) {
        for (int i = 0; i < NEB_DIR.length; i++) {
            double[] nd = NEB_DIR[i];
            double cos = nd[0] * d[0] + nd[1] * d[1] + nd[2] * d[2];
            if (cos < 0.2) {
                continue;
            }
            double ang = Math.acos(Math.min(1, cos));
            double mask = Math.exp(-ang * ang / 0.16);
            double o = i * 7.3;
            double q = Noise.fbm(d[0] * 2.5 + o, d[1] * 2.5, d[2] * 2.5, 4);
            double n = Noise.fbm(d[0] * 3.5 + q * 2.0 + o, d[1] * 3.5 + q * 2.0, d[2] * 3.5, 6);
            double v = Math.pow(Noise.smooth(0.2, 1.0, n * (0.5 + 0.8 * mask)), 1.8) * mask;
            double rid = Noise.ridged(d[0] * 6 + o, d[1] * 6 + q, d[2] * 6, 5);
            v *= 0.45 + 0.9 * rid;
            double[] col = NEB_COL[i];
            c[0] += v * col[0] * 1.3;
            c[1] += v * col[1] * 1.3;
            c[2] += v * col[2] * 1.3;
        }
    }

    // ------------------------------------------------------------ plasma

    /**
     * Слой плазмы: domain-warped fbm. Альфа = интенсивность
     * (рисуется аддитивно).
     */
    public static int[] plasma(int variant, int w, int h) {
        int[] out = new int[w * h];
        double o = variant * 13.7;
        IntStream.range(0, h).parallel().forEach(y -> {
            double[] d = new double[3];
            for (int x = 0; x < w; x++) {
                dir(x, y, w, h, d);
                double px = d[0] * 0.9 + o;
                double py = d[1] * 0.9;
                double pz = d[2] * 0.9 - o;
                double qx = Noise.fbm(px, py, pz, 4);
                double qy = Noise.fbm(px + 5.2, py + 1.3, pz + 2.8, 4);
                double rx = Noise.fbm(px + 2.5 * qx + 1.7, py + 2.5 * qy + 9.2, pz, 4);
                double ry = Noise.fbm(px + 2.5 * qx + 8.3, py + 2.5 * qy + 2.8, pz + 1.1, 4);
                double f = Noise.fbm(px + 2.5 * rx, py + 2.5 * ry, pz + 1.5 * rx, 4);
                double fil = Math.pow(1 - Math.abs(2 * f - 1), 10);
                double e = Noise.smooth(0.35, 0.95, f) * 0.75 + fil * 0.55;
                double r;
                double g;
                double b;
                if (variant == 0) {
                    r = 0.55 + 0.45 * qx;
                    g = 0.10 + 0.55 * ry * ry;
                    b = 0.95;
                    double t = Noise.clamp01(rx * 1.4 - 0.2);
                    r = r * (1 - t) + 0.10 * t;
                    g = g * (1 - t) + 0.85 * t;
                    b = b * (1 - t) + 1.00 * t;
                } else {
                    r = 1.0;
                    g = 0.25 + 0.45 * qy;
                    b = 0.15 + 0.3 * rx;
                    double t = Noise.clamp01(ry * 1.5 - 0.35);
                    r = r * (1 - t) + 0.65 * t;
                    g = g * (1 - t) + 0.15 * t;
                    b = b * (1 - t) + 0.95 * t;
                }
                double hot = fil * 0.35;
                r = Noise.clamp01(r + hot);
                g = Noise.clamp01(g + hot);
                b = Noise.clamp01(b + hot);
                int a = (int) Math.round(255 * Noise.clamp01(e * 0.95));
                out[y * w + x] = a << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
            }
        });
        return out;
    }

    // ------------------------------------------------------------ stars

    public static Star[] stars(int count, long seed) {
        Random rnd = new Random(seed);
        Star[] stars = new Star[count];
        for (int i = 0; i < count; i++) {
            double x;
            double y;
            double z;
            if (rnd.nextDouble() < 0.35) {
                // Сгущение к галактической плоскости.
                double l = rnd.nextDouble() * Math.PI * 2;
                double b = rnd.nextGaussian() * 0.16;
                double cb = Math.cos(b);
                double sb = Math.sin(b);
                double cl = Math.cos(l) * cb;
                double sl = Math.sin(l) * cb;
                x = GAL_C[0] * cl + GAL_B[0] * sl + GAL_N[0] * sb;
                y = GAL_C[1] * cl + GAL_B[1] * sl + GAL_N[1] * sb;
                z = GAL_C[2] * cl + GAL_B[2] * sl + GAL_N[2] * sb;
            } else {
                z = rnd.nextDouble() * 2 - 1;
                double t = rnd.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(1 - z * z);
                x = rr * Math.cos(t);
                y = rr * Math.sin(t);
            }
            double m = Math.pow(rnd.nextDouble(), 7);
            boolean bright = m > 0.45;
            double size = 0.16 + 0.85 * m + rnd.nextDouble() * 0.06;
            double a = 0.35 + 0.65 * Math.pow(rnd.nextDouble(), 0.6) * (0.6 + 0.4 * m) + m * 0.3;
            double pick = rnd.nextDouble();
            double r;
            double g;
            double b;
            if (pick < 0.18) {
                r = 0.68; g = 0.78; b = 1.0;
            } else if (pick < 0.55) {
                r = 0.92; g = 0.95; b = 1.0;
            } else if (pick < 0.80) {
                r = 1.0; g = 0.94; b = 0.82;
            } else if (pick < 0.94) {
                r = 1.0; g = 0.78; b = 0.55;
            } else {
                r = 1.0; g = 0.6; b = 0.45;
            }
            stars[i] = new Star(x, y, z, r, g, b, Math.min(1, a), size, bright);
        }
        return stars;
    }

    /** Спрайт звезды: ядро + ореол, у ярких — дифракционные лучи. */
    public static int[] starSprite(int size, boolean spikes) {
        int[] out = new int[size * size];
        double c = (size - 1) / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x - c) / c;
                double dy = (y - c) / c;
                double r = Math.sqrt(dx * dx + dy * dy);
                double v = Math.exp(-(r / 0.10) * (r / 0.10)) + 0.30 * Math.exp(-r / 0.16);
                if (spikes) {
                    double sx = Math.exp(-Math.abs(dy) / 0.012) * Math.pow(Math.max(0, 1 - Math.abs(dx)), 3);
                    double sy = Math.exp(-Math.abs(dx) / 0.012) * Math.pow(Math.max(0, 1 - Math.abs(dy)), 3);
                    v += 0.55 * (sx + sy);
                }
                v *= Noise.smooth(1.0, 0.85, r);
                int a = (int) Math.round(255 * Noise.clamp01(v));
                out[y * size + x] = a << 24 | 0xFFFFFF;
            }
        }
        return out;
    }

    /** Карта звёзд (линейный RGB) для линзирования за чёрной дырой. */
    public static float[] starMap(Star[] stars, int w, int h) {
        float[] map = new float[w * h * 3];
        for (Star s : stars) {
            double lon = Math.atan2(s.x, s.z);
            double lat = Math.asin(Math.max(-1, Math.min(1, s.y)));
            double fx = (lon + Math.PI) / (Math.PI * 2) * w - 0.5;
            double fy = (Math.PI / 2 - lat) / Math.PI * h - 0.5;
            int ix = (int) Math.floor(fx);
            int iy = (int) Math.floor(fy);
            double e = s.a * (0.6 + s.size * 2.5);
            for (int oy = 0; oy <= 1; oy++) {
                for (int ox = 0; ox <= 1; ox++) {
                    int px = Math.floorMod(ix + ox, w);
                    int py = iy + oy;
                    if (py < 0 || py >= h) {
                        continue;
                    }
                    double wgt = (1 - Math.abs(fx - (ix + ox))) * (1 - Math.abs(fy - (iy + oy)));
                    int k = (py * w + px) * 3;
                    map[k] += (float) (s.r * e * wgt);
                    map[k + 1] += (float) (s.g * e * wgt);
                    map[k + 2] += (float) (s.b * e * wgt);
                }
            }
        }
        return map;
    }
}
