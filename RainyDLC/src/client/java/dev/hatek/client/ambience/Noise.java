package dev.hatek.client.ambience;

/**
 * Классический улучшенный шум Перлина (3D) + fbm/ridged.
 * Чистая Java без зависимостей — используется генераторами неба.
 */
final class Noise {
    private static final int[] P = new int[512];

    static {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }
        long seed = 0x5DEECE66DL;
        for (int i = 255; i > 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((seed >>> 33) % (i + 1));
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            P[i] = perm[i & 255];
        }
    }

    private Noise() {
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Шум Перлина в диапазоне примерно [-1, 1]. */
    static double perlin(double x, double y, double z) {
        double fx = Math.floor(x);
        double fy = Math.floor(y);
        double fz = Math.floor(z);
        int X = (int) fx & 255;
        int Y = (int) fy & 255;
        int Z = (int) fz & 255;
        x -= fx;
        y -= fy;
        z -= fz;
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);
        int A = P[X] + Y;
        int AA = P[A] + Z;
        int AB = P[A + 1] + Z;
        int B = P[X + 1] + Y;
        int BA = P[B] + Z;
        int BB = P[B + 1] + Z;
        return lerp(w,
                lerp(v, lerp(u, grad(P[AA], x, y, z), grad(P[BA], x - 1, y, z)),
                        lerp(u, grad(P[AB], x, y - 1, z), grad(P[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(P[AA + 1], x, y, z - 1), grad(P[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(P[AB + 1], x, y - 1, z - 1), grad(P[BB + 1], x - 1, y - 1, z - 1))));
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** fbm в диапазоне примерно [0, 1]. */
    static double fbm(double x, double y, double z, int octaves) {
        double sum = 0;
        double amp = 0.5;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * perlin(x, y, z);
            norm += amp;
            x = x * 2.03 + 17.1;
            y = y * 2.03 + 3.7;
            z = z * 2.03 + 11.3;
            amp *= 0.5;
        }
        return 0.5 + 0.5 * (sum / norm) * 1.6;
    }

    /** Гребневый шум (тонкие нити/пылевые прожилки), [0, 1]. */
    static double ridged(double x, double y, double z, int octaves) {
        double sum = 0;
        double amp = 0.5;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(perlin(x, y, z));
            sum += amp * n * n;
            norm += amp;
            x = x * 2.1 + 5.3;
            y = y * 2.1 + 1.9;
            z = z * 2.1 + 7.7;
            amp *= 0.5;
        }
        return sum / norm;
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static double smooth(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }
}
