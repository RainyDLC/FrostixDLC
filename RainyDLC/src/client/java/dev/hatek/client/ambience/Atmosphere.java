package dev.hatek.client.ambience;

import java.util.stream.IntStream;

/**
 * Режим «Shader»: аналитическое однократное рассеяние (Рэлей + Ми)
 * в стиле шейдерпаков — голубой день, оранжево-розовые закаты,
 * сумерки и лунная ночь. Плюс текстура полярного сияния.
 */
public final class Atmosphere {
    private static final double[] BETA_R = {0.075, 0.19, 0.46};
    private static final double BETA_M = 0.04;
    private static final double MIE_G = 0.78;
    private static final double SUN_PATH = 0.33;
    private static final double EXPOSURE = 3.0;

    private Atmosphere() {
    }

    /** Оптическая масса воздуха (Kasten–Young) для высоты y = sin(elev). */
    static double airmass(double y) {
        double yy = Math.max(y, -0.02);
        double zen = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, yy))));
        return 1.0 / (yy + 0.50572 * Math.pow(Math.max(0.5, 96.07995 - zen), -1.6364));
    }

    /** Пропускание атмосферы для солнечного света (цвет диска солнца). */
    public static void sunTransmittance(double sunY, double[] out) {
        double m = airmass(sunY) * SUN_PATH;
        double fade = Noise.smooth(-0.04, 0.02, sunY);
        for (int c = 0; c < 3; c++) {
            out[c] = Math.exp(-(BETA_R[c] + BETA_M) * m) * fade;
        }
    }

    private static final double[] OZONE = {0.021, 0.060, 0.004};

    /**
     * Свет солнца, дошедший до точки рассеяния: чем выше взгляд, тем короче
     * путь; озон поглощает оранжевый — сумеречный зенит остаётся синим.
     */
    private static void scatterLight(double sunY, double viewY, double[] out) {
        double m = airmass(Math.max(sunY, 0.0)) * SUN_PATH * (0.18 + 0.82 * Math.exp(-viewY * 5.0));
        double fade = Math.pow(Noise.smooth(-0.20, 0.03, sunY), 1.6);
        for (int c = 0; c < 3; c++) {
            out[c] = Math.exp(-(BETA_R[c] + BETA_M) * m - OZONE[c] * m * 3.0) * fade;
        }
    }

    /** Линейная яркость неба в направлении d. */
    public static void sky(double dx, double dy, double dz, double sx, double sy, double sz,
                           double mx, double my, double mz, double rain, double[] out) {
        double y = Math.max(dy, 0.0);
        double view = airmass(y);
        double[] st = new double[3];
        scatterLight(sy, y, st);
        double mu = dx * sx + dy * sy + dz * sz;
        double phaseR = 0.75 * (1 + mu * mu);
        double g = MIE_G;
        double phaseM = (1 - g * g) / Math.pow(1 + g * g - 2 * g * mu, 1.5) * 0.08;
        // Луна: та же модель, но в ~300 раз тусклее.
        double muM = dx * mx + dy * my + dz * mz;
        double[] mt = new double[3];
        scatterLight(my, y, mt);
        double phaseMm = (1 - g * g) / Math.pow(1 + g * g - 2 * g * muM, 1.5) * 0.08;
        double phaseRm = 0.75 * (1 + muM * muM);
        // Сумеречный «пояс Венеры» и мягкое многократное рассеяние.
        double twilight = Noise.smooth(-0.22, 0.0, sy) * Noise.smooth(0.25, -0.02, sy);
        for (int c = 0; c < 3; c++) {
            double b = BETA_R[c] + BETA_M;
            double scat = 1 - Math.exp(-b * view);
            double sun = st[c] * (BETA_R[c] * phaseR + BETA_M * phaseM) / b * scat;
            double moon = mt[c] * (BETA_R[c] * phaseRm + BETA_M * phaseMm) / b * scat * 0.03;
            double multi = st[c] * 0.12 * scat * (BETA_R[c] / 0.46);
            out[c] = (sun + moon + multi) * EXPOSURE;
        }
        double belt = twilight * Math.exp(-Math.abs(dy) * 6) * Noise.smooth(0.3, -0.6, mu) * 0.10;
        out[0] += belt * 0.95;
        out[1] += belt * 0.45;
        out[2] += belt * 0.65;
        // Дождь: серое затянутое небо.
        if (rain > 0) {
            double lum = out[0] * 0.3 + out[1] * 0.55 + out[2] * 0.15;
            for (int c = 0; c < 3; c++) {
                out[c] = out[c] * (1 - rain) + lum * 0.55 * rain;
            }
        }
        // Под горизонтом — мягко затемняем.
        if (dy < 0) {
            double f = Math.exp(dy * 12);
            for (int c = 0; c < 3; c++) {
                out[c] *= 0.35 + 0.65 * f;
            }
        }
    }

    /** Equirect-текстура атмосферы (ARGB, альфа = 1, рисуется аддитивно). */
    public static void render(int w, int h, double sx, double sy, double sz,
                              double mx, double my, double mz, double rain, int[] out) {
        IntStream.range(0, h).parallel().forEach(yy -> {
            double[] d = new double[3];
            double[] c = new double[3];
            for (int x = 0; x < w; x++) {
                SkyGen.dir(x, yy, w, h, d);
                sky(d[0], d[1], d[2], sx, sy, sz, mx, my, mz, rain, c);
                out[yy * w + x] = SkyGen.tone(c[0], c[1], c[2], 1);
            }
        });
    }

    /** Цвет тумана: небо у горизонта в сторону взгляда. */
    public static float[] fog(double fx, double fz, double sx, double sy, double sz,
                              double mx, double my, double mz, double rain) {
        double l = Math.sqrt(fx * fx + fz * fz);
        if (l < 1e-4) {
            fx = 1;
            fz = 0;
            l = 1;
        }
        double dy = 0.06;
        double k = Math.sqrt(1 - dy * dy) / l;
        double[] c = new double[3];
        sky(fx * k, dy, fz * k, sx, sy, sz, mx, my, mz, rain, c);
        int argb = SkyGen.tone(c[0], c[1], c[2], 1);
        return new float[]{(argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f};
    }

    /** Полярное сияние: занавеси с вертикальными лучами, альфа = яркость. */
    public static int[] aurora(int variant, int w, int h) {
        int[] out = new int[w * h];
        double o = variant * 21.3;
        IntStream.range(0, h).parallel().forEach(yy -> {
            double[] d = new double[3];
            for (int x = 0; x < w; x++) {
                SkyGen.dir(x, yy, w, h, d);
                double e = Math.asin(Math.max(-1, Math.min(1, d[1])));
                if (e < 0.02 || e > 1.25) {
                    continue;
                }
                double a = Math.atan2(d[0], d[2]);
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                double warp = Noise.fbm(ca * 1.3 + o, sa * 1.3, 3.1 + o, 3);
                double base = 0.16 + 0.22 * Noise.fbm(ca * 1.1 + 7 + o, sa * 1.1, 1.7, 3);
                double curtain = 1 - Math.abs(Noise.perlin(ca * 2.2 + warp * 2.2 + o, sa * 2.2 + warp, 5.3 + o));
                double curtain2 = 1 - Math.abs(Noise.perlin(ca * 1.2 - warp * 1.5 + o, sa * 1.2, 9.1 + o));
                curtain = Math.pow(curtain, 5) + 0.6 * Math.pow(curtain2, 4);
                double rays = 0.45 + 0.8 * Math.pow(Noise.fbm(ca * 28 + o, sa * 28, warp * 3, 3), 1.6);
                double above = e - base;
                if (above < -0.03) {
                    continue;
                }
                double vertical = Noise.smooth(-0.03, 0.015, above) * Math.exp(-Math.max(0, above) / 0.28);
                double north = 0.45 + 0.55 * Noise.smooth(0.3, -0.9, d[2]);
                double v = curtain * rays * vertical * north * 1.6;
                if (v < 0.002) {
                    continue;
                }
                double t = Noise.clamp01(above / 0.38);
                double r = 0.15 + (0.75 - 0.15) * t * t;
                double g = 1.0 - 0.75 * t;
                double b = 0.45 + 0.45 * t;
                int ia = (int) Math.round(255 * Noise.clamp01(v));
                out[yy * w + x] = ia << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
            }
        });
        return out;
    }

    /** Спрайт солнца: диск с потемнением к краю + ореол. */
    public static int[] sunSprite(int size) {
        int[] out = new int[size * size];
        double c = (size - 1) / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x - c, y - c) / c;
                double disc = Noise.smooth(0.075, 0.065, r) * (0.75 + 0.25 * Math.sqrt(Math.max(0, 1 - (r / 0.07) * (r / 0.07))));
                double glow = 0.55 * Math.exp(-r / 0.07) + 0.22 * Math.exp(-r / 0.25);
                double v = Noise.clamp01(disc + glow) * Noise.smooth(1.0, 0.8, r);
                out[y * size + x] = (int) Math.round(255 * v) << 24 | 0xFFFFFF;
            }
        }
        return out;
    }
}
