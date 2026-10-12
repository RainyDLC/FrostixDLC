package dev.hatek.client.ambience;

import java.util.stream.IntStream;

/**
 * Чёрная дыра Шварцшильда.
 * <ul>
 *   <li>Трассировка нуль-геодезических RK4 (уравнение x'' = -3/2·h²·x/r⁵).</li>
 *   <li>Тонкий аккреционный диск Новикова–Торна: профиль температуры
 *       T(r) ∝ r^-3/4 (1 - √(r_isco/r))^1/4, внутренний край на ISCO (6M),
 *       слабое свечение «плунжа» между ISCO и горизонтом.</li>
 *   <li>Полный фактор сдвига частоты g = √(1-3M/r)-подобный: гравитационное
 *       красное смещение × релятивистский Доплер; яркость ∝ g⁴, цвет —
 *       спектр абсолютно чёрного тела при T·g.</li>
 *   <li>До 6 пересечений диска на луч: первичное, вторичное (под тенью)
 *       и тонкие фотонные кольца высших порядков.</li>
 *   <li>Турбулентная структура диска: логарифмические спиральные рукава,
 *       волокна, горячие сгустки; дифференциальное (кеплеровское) вращение.</li>
 *   <li>Линзирование фона (небо + звёзды) с кольцом Эйнштейна.</li>
 *   <li>Свечение у критической кривой (b = 3√3 M): тонкое яркое кольцо и мягкий ореол
 *       по краю тени — тень не сливается с космосом.</li>
 *   <li>Релятивистские джеты вдоль оси вращения (вертикально) и дымный оттёк
 *       в плоскости диска (горизонтально): анимированный турбулентный "дым".</li>
 *   <li>Кольцевая структура диска: резонансные полосы и тёмные щели
 *       (как щель Кассини), фотонные кольца высших порядков подсвечены.</li>
 * </ul>
 * Попадания хранятся разреженно, поэтому 1024² укладывается в ~30 МБ.
 */
public final class BlackHoleGen {
    /** Направление на чёрную дыру в системе неба и базис билборда. */
    // Выше над горизонтом (~34°), чтобы огромный диск не уходил в землю.
    public static final double[] DIR = SkyGen.norm(0.40, 0.56, -0.72);
    public static final double[] RIGHT;
    public static final double[] UP;
    /** tan половины углового размера билборда. */
    public static final double TAN_HALF = 0.72;

    // Компактнее на небе (~±36°); тень крупная, диск прижат к ней.
    private static final double CAM_DIST = 11.5;
    private static final double INCLINATION = Math.toRadians(6.0);
    private static final double ROLL = Math.toRadians(-12);
    private static final double R_ISCO = 4.0;
    private static final double R_PLUNGE = 2.6;
    private static final double R_OUT = 7.2;
    private static final double T_PEAK_K = 5000.0;
    private static final int MAX_HITS = 6;
    private static final int NR = 400;
    private static final int NP = 2048;
    private static final double FLOW_PERIOD = 26.0;
    private static final double LN_IN = Math.log(R_PLUNGE);
    private static final double LN_OUT = Math.log(R_OUT);
    /** Радиусы тёмных щелей (в M) и их полуширина. */
    private static final double[] GAPS = {};
    private static final double GAP_W = 0.17;
    /** Усиление фотонных колец (3-е и далее пересечения диска). */
    private static final double PHOTON_RING_BOOST = 1.3;

    /** Критический прицельный параметр (край тени), M. */
    private static final double B_CRIT = 3.0 * Math.sqrt(3.0);
    private static final double RING_W = 0.16;
    private static final double RING_I = 0.0;
    private static final double HALO_W = 1.1;
    private static final double HALO_I = 0.0;
    private static final double[] RING_RGB = {1.0, 0.72, 0.42};
    /** Тень не абсолютно чёрная: тёмно-янтарная заливка + свечение изнутри к краю. */
    private static final double SHADOW_FILL = 0.035;
    private static final double INNER_W = 1.3;
    private static final double INNER_I = 0.10;
    private static final double[] SHADOW_RGB = {0.55, 0.45, 0.42};
    /** Множитель и потолок угловой скорости диска (рад/с) — плавное движение без рывков. */
    private static final double OMEGA_SCALE = 0.45;
    private static final double OMEGA_MAX = 0.6;

    /** Джеты: начальная ширина, раскрытие, яркость, скорость дыма (M/с), цвет. */
    private static final double JET_W0 = 0.10;
    private static final double JET_SPREAD = 0.018;
    private static final double JET_I = 0.75;
    private static final double JET_SPEED = 2.4;
    private static final double[] JET_RGB = {0.55, 0.74, 1.0};
    /** Горизонтальный оттёк в плоскости диска. */
    private static final double FLOW_W0 = 0.08;
    private static final double FLOW_SPREAD = 0.012;
    private static final double FLOW_I = 0.3;
    private static final double FLOW_SPEED = 1.3;
    private static final double[] FLOW_RGB = {1.0, 0.78, 0.55};
    /** Таблица дыма: период по оси (M) и разрешение (вдоль оси x поперёк). */
    private static final double SMOKE_PERIOD = 16.0;
    private static final int NS = 512;
    private static final int NQ = 64;
    private static final double Q_MAX = 3.0;

    static {
        double[] r = SkyGen.cross(DIR, new double[]{0, 1, 0});
        RIGHT = SkyGen.norm(r[0], r[1], r[2]);
        UP = SkyGen.cross(RIGHT, DIR);
    }

    public final int size;
    private final float[] bg;
    private final byte[] edge;
    /** Разреженный список попаданий: [start[i], start[i+1]). */
    private final int[] start;
    private float[] hitU;
    private float[] hitPhi;
    private float[] hitInten;
    private int[] hitChroma;
    private final float[] noiseA = new float[NR * NP];
    private final float[] noiseB = new float[NR * NP];
    private final float[] omegaLut = new float[NR];
    private final float[] fadeLut = new float[NR];
    private final float[] alphaLut = new float[NR];
    /** Прицельный параметр луча и координаты пикселя в осях дыры (M): вдоль оси вращения / вдоль диска. */
    private final float[] impact;
    private final float[] axisS;
    private final float[] axisQ;
    private final float[] smokeA = new float[NS * NQ];
    private final float[] smokeB = new float[NS * NQ];
    private double maxReach;

    private BlackHoleGen(int size) {
        this.size = size;
        int n = size * size;
        this.bg = new float[n * 3];
        this.edge = new byte[n];
        this.start = new int[n + 1];
        this.impact = new float[n];
        this.axisS = new float[n];
        this.axisQ = new float[n];
    }

    public static BlackHoleGen build(int size, int[] baseSky, int bw, int bh, float[] starMap) {
        BlackHoleGen g = new BlackHoleGen(size);
        g.buildLuts();
        g.buildDiskNoise();
        g.buildSmoke();
        g.trace(baseSky, bw, bh, starMap);
        return g;
    }

    private static double rOfU(double u) {
        return Math.exp(LN_IN + u * (LN_OUT - LN_IN));
    }

    private static double uOfR(double r) {
        return (Math.log(r) - LN_IN) / (LN_OUT - LN_IN);
    }

    private void buildLuts() {
        for (int i = 0; i < NR; i++) {
            double r = rOfU(i / (double) (NR - 1));
            // Кеплер вне ISCO; внутри — падение с почти постоянной угловой скоростью.
            double rr = Math.max(r, R_ISCO * 0.85);
            omegaLut[i] = (float) Math.min(OMEGA_MAX, Math.pow(R_ISCO / rr, 1.5) * OMEGA_SCALE);
            double outer = Noise.smooth(R_OUT, R_OUT * 0.75, r);
            double inner = r >= R_ISCO ? Noise.smooth(R_ISCO - 0.05, R_ISCO + 0.35, r) * 0.75 + 0.25 : 0.22;
            fadeLut[i] = (float) (outer * inner);
            alphaLut[i] = (float) (r >= R_ISCO ? outer : 0.30 * Noise.smooth(R_PLUNGE, R_ISCO, r));
        }
    }

    // ------------------------------------------------------------ disk structure

    private void buildDiskNoise() {
        IntStream.range(0, NR).parallel().forEach(i -> {
            double u = i / (double) (NR - 1);
            double lr = LN_IN + u * (LN_OUT - LN_IN);
            for (int j = 0; j < NP; j++) {
                double ph = j / (double) NP * Math.PI * 2;
                noiseA[i * NP + j] = (float) density(ph, lr, 0.0);
                noiseB[i * NP + j] = (float) density(ph, lr, 41.3);
            }
        });
    }

    private static double density(double ph, double lr, double o) {
        // Закручивающиеся (trailing) логарифмические спирали.
        double sp = ph + 2.6 * lr;
        double cs = Math.cos(sp);
        double ss = Math.sin(sp);
        double cp = Math.cos(ph);
        double sn = Math.sin(ph);
        double large = Noise.fbm(cs * 1.8 + o, ss * 1.8, lr * 5 + o, 5);
        double fil = Noise.ridged(cs * 3.0 + o, ss * 3.0, lr * 22 + o, 4);
        double fine = Noise.fbm(cp * 9 + o, sn * 9, lr * 70 + o, 3);
        double clumps = Math.pow(Noise.fbm(cs * 7 + o, ss * 7, lr * 16 + o, 4), 4) * 2.2;
        double gaps = Noise.smooth(0.30, 0.55, Noise.fbm(cp * 2.2 - o, sn * 2.2, lr * 12, 4));
        double rings = 0.5 + 0.5 * Math.sin(lr * 85 + large * 8 + o);
        double v = 0.42 * large + 0.26 * fil * fil + 0.12 * fine + 0.04 * rings * fine + clumps;
        v *= 0.45 + 0.55 * gaps;
        // Кольца: резонансные полосы (слегка гуляют с крупным шумом) + тёмные щели.
        double r = Math.exp(lr);
        double bands = Noise.smooth(0.25, 0.75, 0.5 + 0.5 * Math.sin(lr * 46 + large * 2.2 + o * 0.3));
        double fineBands = 0.5 + 0.5 * Math.sin(lr * 160 + fil * 3.0);
        double ringMask = 1.0;
        for (double g : GAPS) {
            double d = (r - g) / GAP_W;
            ringMask *= 1.0 - 0.82 * Math.exp(-d * d);
        }
        v *= (0.82 + 0.18 * bands) * (0.92 + 0.08 * fineBands);
        v *= ringMask;
        return Noise.clamp01((v - 0.12) * 1.45);
    }

    /** Периодичный по оси турбулентный дым: (s по периоду) x (q / ширина в [-Q_MAX, Q_MAX]). */
    private void buildSmoke() {
        double rad = SMOKE_PERIOD / (Math.PI * 2);
        IntStream.range(0, NS).parallel().forEach(i -> {
            double a = i / (double) NS * Math.PI * 2;
            double cx = Math.cos(a) * rad;
            double cz = Math.sin(a) * rad;
            for (int j = 0; j < NQ; j++) {
                double q = (j / (double) (NQ - 1) * 2 - 1) * Q_MAX;
                smokeA[i * NQ + j] = (float) smoke(cx, cz, q, 0.0);
                smokeB[i * NQ + j] = (float) smoke(cx, cz, q, 17.7);
            }
        });
    }

    private static double smoke(double cx, double cz, double q, double o) {
        // Клубы + рваные волокна, поперёк чуть закручены.
        double warp = Noise.fbm(cx * 0.35 + o, cz * 0.35, q * 0.6 + o, 3);
        double billow = Noise.fbm(cx * 0.9 + warp * 1.6 + o, cz * 0.9, q * 1.1 + warp, 5);
        double wisps = Noise.ridged(cx * 1.7 + o, cz * 1.7 + warp, q * 2.4, 4);
        double v = 0.65 * billow + 0.55 * wisps * wisps;
        return Noise.clamp01((v - 0.18) * 1.6);
    }

    // ------------------------------------------------------------ tracing

    private void trace(int[] baseSky, int bw, int bh, float[] starMap) {
        double[] cam = {0, CAM_DIST * Math.sin(INCLINATION), -CAM_DIST * Math.cos(INCLINATION)};
        double[] f = SkyGen.norm(-cam[0], -cam[1], -cam[2]);
        double[] r0 = SkyGen.cross(f, new double[]{0, 1, 0});
        r0 = SkyGen.norm(r0[0], r0[1], r0[2]);
        double[] u0 = SkyGen.cross(r0, f);
        double cr = Math.cos(ROLL);
        double sr = Math.sin(ROLL);
        double[] rt = {r0[0] * cr + u0[0] * sr, r0[1] * cr + u0[1] * sr, r0[2] * cr + u0[2] * sr};
        double[] up = {u0[0] * cr - r0[0] * sr, u0[1] * cr - r0[1] * sr, u0[2] * cr - r0[2] * sr};

        float[][] rowU = new float[size][];
        float[][] rowPhi = new float[size][];
        float[][] rowInt = new float[size][];
        int[][] rowChr = new int[size][];
        int[][] rowCount = new int[size][];

        // Экранные оси дыры: проекция оси вращения (мировой Y) и перпендикуляр — линия диска.
        double sa = rt[1];
        double sb = up[1];
        double sl = Math.sqrt(sa * sa + sb * sb);
        final double axX = sa / sl;
        final double axY = sb / sl;
        final double scale = TAN_HALF * CAM_DIST;
        this.maxReach = scale * 0.82;

        IntStream.range(0, size).parallel().forEach(y -> {
            double[] p = new double[3];
            double[] v = new double[3];
            double[] tmp = new double[3];
            float[] hu = new float[MAX_HITS];
            float[] hp = new float[MAX_HITS];
            float[] hi = new float[MAX_HITS];
            int[] hc = new int[MAX_HITS];
            Grow g = new Grow();
            int[] counts = new int[size];
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                double px = (x + 0.5) / size * 2 - 1;
                double py = 1 - (y + 0.5) / size * 2;
                double rho = Math.sqrt(px * px + py * py);
                double mx = px * scale;
                double my = py * scale;
                axisS[idx] = (float) (mx * axX + my * axY);
                axisQ[idx] = (float) (-mx * axY + my * axX);
                edge[idx] = (byte) Math.round(255 * Noise.smooth(1.0, 0.70, rho));
                double dx = f[0] + (rt[0] * px + up[0] * py) * TAN_HALF;
                double dy = f[1] + (rt[1] * px + up[1] * py) * TAN_HALF;
                double dz = f[2] + (rt[2] * px + up[2] * py) * TAN_HALF;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                p[0] = cam[0];
                p[1] = cam[1];
                p[2] = cam[2];
                v[0] = dx / dl;
                v[1] = dy / dl;
                v[2] = dz / dl;
                double bx = cam[1] * v[2] - cam[2] * v[1];
                double by = cam[2] * v[0] - cam[0] * v[2];
                double bz = cam[0] * v[1] - cam[1] * v[0];
                impact[idx] = (float) Math.sqrt(bx * bx + by * by + bz * bz);
                int[] hits = {0};
                boolean escaped = integrate(p, v, tmp, hu, hp, hi, hc, hits);
                counts[x] = hits[0];
                for (int h = 0; h < hits[0]; h++) {
                    g.add(hu[h], hp[h], hi[h], hc[h]);
                }
                if (escaped) {
                    double lx = v[0] * rt[0] + v[1] * rt[1] + v[2] * rt[2];
                    double ly = v[0] * up[0] + v[1] * up[1] + v[2] * up[2];
                    double lz = v[0] * f[0] + v[1] * f[1] + v[2] * f[2];
                    double wx = RIGHT[0] * lx + UP[0] * ly + DIR[0] * lz;
                    double wy = RIGHT[1] * lx + UP[1] * ly + DIR[1] * lz;
                    double wz = RIGHT[2] * lx + UP[2] * ly + DIR[2] * lz;
                    sampleSky(wx, wy, wz, baseSky, bw, bh, starMap, bg, idx * 3);
                }
            }
            rowU[y] = g.u();
            rowPhi[y] = g.phi();
            rowInt[y] = g.inten();
            rowChr[y] = g.chroma();
            rowCount[y] = counts;
        });

        int total = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                start[y * size + x] = total;
                total += rowCount[y][x];
            }
        }
        start[size * size] = total;
        hitU = new float[total];
        hitPhi = new float[total];
        hitInten = new float[total];
        hitChroma = new int[total];
        for (int y = 0; y < size; y++) {
            int s = start[y * size];
            int len = rowU[y].length;
            System.arraycopy(rowU[y], 0, hitU, s, len);
            System.arraycopy(rowPhi[y], 0, hitPhi, s, len);
            System.arraycopy(rowInt[y], 0, hitInten, s, len);
            System.arraycopy(rowChr[y], 0, hitChroma, s, len);
        }
    }

    /** Растущий буфер попаданий одной строки. */
    private static final class Grow {
        float[] u = new float[256];
        float[] phi = new float[256];
        float[] inten = new float[256];
        int[] chroma = new int[256];
        int n;

        void add(float uu, float pp, float ii, int cc) {
            if (n == u.length) {
                int m = n * 2;
                u = java.util.Arrays.copyOf(u, m);
                phi = java.util.Arrays.copyOf(phi, m);
                inten = java.util.Arrays.copyOf(inten, m);
                chroma = java.util.Arrays.copyOf(chroma, m);
            }
            u[n] = uu;
            phi[n] = pp;
            inten[n] = ii;
            chroma[n] = cc;
            n++;
        }

        float[] u() {
            return java.util.Arrays.copyOf(u, n);
        }

        float[] phi() {
            return java.util.Arrays.copyOf(phi, n);
        }

        float[] inten() {
            return java.util.Arrays.copyOf(inten, n);
        }

        int[] chroma() {
            return java.util.Arrays.copyOf(chroma, n);
        }
    }

    private static double tProfile(double r) {
        if (r < R_ISCO) {
            // Плунж: быстро остывающий газ.
            return Math.pow(0.2, 0.25) * Math.pow(Math.max(0, (r - R_PLUNGE) / (R_ISCO - R_PLUNGE)), 0.8);
        }
        return Math.pow(R_ISCO / r, 0.75) * Math.pow(Math.max(0, 1 - 0.8 * Math.sqrt(R_ISCO / r)), 0.25);
    }

    private static final double T_MAX;

    static {
        double m = 0;
        for (double r = R_ISCO; r < R_OUT; r += 0.01) {
            m = Math.max(m, tProfile(r));
        }
        T_MAX = m;
    }

    /** Возвращает true, если луч ушёл на бесконечность. */
    private static boolean integrate(double[] p, double[] v, double[] t, float[] hu, float[] hp, float[] hi,
                                     int[] hc, int[] hits) {
        double hx = p[1] * v[2] - p[2] * v[1];
        double hy = p[2] * v[0] - p[0] * v[2];
        double hz = p[0] * v[1] - p[1] * v[0];
        double h2 = hx * hx + hy * hy + hz * hz;
        for (int step = 0; step < 6000; step++) {
            double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            if (r < 2.0005) {
                return false;
            }
            if (r > CAM_DIST * 1.8 && p[0] * v[0] + p[1] * v[1] + p[2] * v[2] > 0) {
                return true;
            }
            double dt = Math.min(1.4, Math.max(0.006, 0.018 * (r - 1.6) * (r - 1.6)));
            double y0 = p[1];
            double ox = p[0];
            double oz = p[2];
            double ovx = v[0];
            double ovy = v[1];
            double ovz = v[2];
            rk4(p, v, h2, dt, t);
            if (hits[0] < MAX_HITS && (y0 > 0) != (p[1] > 0)) {
                double s = y0 / (y0 - p[1]);
                double cx = ox + (p[0] - ox) * s;
                double cz = oz + (p[2] - oz) * s;
                double cr = Math.sqrt(cx * cx + cz * cz);
                if (cr >= R_PLUNGE && cr <= R_OUT) {
                    double nx = -(ovx + (v[0] - ovx) * s);
                    double ny = -(ovy + (v[1] - ovy) * s);
                    double nz = -(ovz + (v[2] - ovz) * s);
                    double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double phi = Math.atan2(cz, cx);
                    double tx = -Math.sin(phi);
                    double tz = Math.cos(phi);
                    // Скорость газа относительно статического наблюдателя.
                    double beta = cr >= R_ISCO ? Math.sqrt(1.0 / (cr - 2.0)) : Math.min(0.8, Math.sqrt(1.0 / (R_ISCO - 2.0)) * Math.sqrt(R_ISCO / cr));
                    double gamma = 1.0 / Math.sqrt(1 - beta * beta);
                    double cosT = (tx * nx + tz * nz) / nl;
                    double g = Math.sqrt(1 - 2.0 / cr) / (gamma * (1 - beta * cosT));
                    double tn = tProfile(cr) / T_MAX;
                    double tObs = T_PEAK_K * tn * g;
                    int chroma = blackbody(tObs);
                    // Болометрическая яркость ∝ g⁴·T⁴, смягчённая для читаемости внешнего диска.
                    double inten = 2.3 * Math.pow(tn, 1.9) * g * g * g * g;
                    int k = hits[0];
                    if (k >= 2) {
                        inten *= PHOTON_RING_BOOST;
                    }
                    hu[k] = (float) uOfR(cr);
                    hp[k] = (float) phi;
                    hi[k] = (float) inten;
                    hc[k] = chroma;
                    hits[0]++;
                }
            }
        }
        return false;
    }

    /** Цвет абсолютно чёрного тела (нормирован на максимум канала), RGB-байты. */
    static int blackbody(double kelvin) {
        double t = Math.max(1000, Math.min(40000, kelvin)) / 100.0;
        double r;
        double g;
        double b;
        if (t <= 66) {
            r = 255;
            g = 99.4708025861 * Math.log(t) - 161.1195681661;
            b = t <= 19 ? 0 : 138.5177312231 * Math.log(t - 10) - 305.0447927307;
        } else {
            r = 329.698727446 * Math.pow(t - 60, -0.1332047592);
            g = 288.1221695283 * Math.pow(t - 60, -0.0755148492);
            b = 255;
        }
        int ir = (int) Math.max(0, Math.min(255, r));
        int ig = (int) Math.max(0, Math.min(255, g));
        int ib = (int) Math.max(0, Math.min(255, b));
        return ir << 16 | ig << 8 | ib;
    }

    private static void rk4(double[] p, double[] v, double h2, double dt, double[] t) {
        accel(p[0], p[1], p[2], h2, t);
        double k1px = v[0], k1py = v[1], k1pz = v[2];
        double k1vx = t[0], k1vy = t[1], k1vz = t[2];
        double hx = dt * 0.5;
        accel(p[0] + k1px * hx, p[1] + k1py * hx, p[2] + k1pz * hx, h2, t);
        double k2px = v[0] + k1vx * hx, k2py = v[1] + k1vy * hx, k2pz = v[2] + k1vz * hx;
        double k2vx = t[0], k2vy = t[1], k2vz = t[2];
        accel(p[0] + k2px * hx, p[1] + k2py * hx, p[2] + k2pz * hx, h2, t);
        double k3px = v[0] + k2vx * hx, k3py = v[1] + k2vy * hx, k3pz = v[2] + k2vz * hx;
        double k3vx = t[0], k3vy = t[1], k3vz = t[2];
        accel(p[0] + k3px * dt, p[1] + k3py * dt, p[2] + k3pz * dt, h2, t);
        double k4px = v[0] + k3vx * dt, k4py = v[1] + k3vy * dt, k4pz = v[2] + k3vz * dt;
        double k4vx = t[0], k4vy = t[1], k4vz = t[2];
        double s = dt / 6.0;
        p[0] += s * (k1px + 2 * k2px + 2 * k3px + k4px);
        p[1] += s * (k1py + 2 * k2py + 2 * k3py + k4py);
        p[2] += s * (k1pz + 2 * k2pz + 2 * k3pz + k4pz);
        v[0] += s * (k1vx + 2 * k2vx + 2 * k3vx + k4vx);
        v[1] += s * (k1vy + 2 * k2vy + 2 * k3vy + k4vy);
        v[2] += s * (k1vz + 2 * k2vz + 2 * k3vz + k4vz);
    }

    private static void accel(double x, double y, double z, double h2, double[] out) {
        double r2 = x * x + y * y + z * z;
        double r5 = r2 * r2 * Math.sqrt(r2);
        double k = -1.5 * h2 / r5;
        out[0] = k * x;
        out[1] = k * y;
        out[2] = k * z;
    }

    static double untone(int c) {
        double v = Math.min(0.996, c / 255.0);
        return -Math.log(1 - Math.pow(v, 1 / 0.8));
    }

    private static void sampleSky(double x, double y, double z, int[] sky, int w, int h,
                                  float[] stars, float[] out, int k) {
        double lon = Math.atan2(x, z);
        double lat = Math.asin(Math.max(-1, Math.min(1, y)));
        double fx = (lon + Math.PI) / (Math.PI * 2) * w - 0.5;
        double fy = (Math.PI / 2 - lat) / Math.PI * h - 0.5;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        double ax = fx - ix;
        double ay = fy - iy;
        double r = 0;
        double g = 0;
        double b = 0;
        for (int oy = 0; oy <= 1; oy++) {
            int py = Math.max(0, Math.min(h - 1, iy + oy));
            for (int ox = 0; ox <= 1; ox++) {
                int px = Math.floorMod(ix + ox, w);
                double wgt = (ox == 0 ? 1 - ax : ax) * (oy == 0 ? 1 - ay : ay);
                int c = sky[py * w + px];
                r += untone(c >> 16 & 255) * wgt;
                g += untone(c >> 8 & 255) * wgt;
                b += untone(c & 255) * wgt;
                int s = (py * w + px) * 3;
                r += stars[s] * wgt;
                g += stars[s + 1] * wgt;
                b += stars[s + 2] * wgt;
            }
        }
        out[k] = (float) r;
        out[k + 1] = (float) g;
        out[k + 2] = (float) b;
    }

    // ------------------------------------------------------------ frames

    private static final int TONE_LUT = 4096;
    private static final double TONE_MAX = 12.0;
    private static final int[] TONE = new int[TONE_LUT + 1];

    static {
        for (int i = 0; i <= TONE_LUT; i++) {
            double v = i / (double) TONE_LUT * TONE_MAX;
            TONE[i] = (int) Math.round(255 * Math.pow(1 - Math.exp(-v), 0.8));
        }
    }

    private static int toneLut(double v) {
        if (v <= 0) {
            return 0;
        }
        int i = (int) (v * (TONE_LUT / TONE_MAX));
        return TONE[Math.min(TONE_LUT, i)];
    }

    private float noiseAt(float[] tex, int i, double fu, double phi) {
        double vv = phi * (NP / (Math.PI * 2));
        double fv0 = Math.floor(vv);
        int j = Math.floorMod((int) fv0, NP);
        int j1 = j + 1 == NP ? 0 : j + 1;
        double fv = vv - fv0;
        int r0 = i * NP;
        int r1 = r0 + NP;
        double a = tex[r0 + j] + (tex[r0 + j1] - tex[r0 + j]) * fv;
        double b = tex[r1 + j] + (tex[r1 + j1] - tex[r1 + j]) * fv;
        return (float) (a + (b - a) * fu);
    }

    /**
     * Двусторонний дымный поток вдоль оси s (q — поперёк), в M.
     * Ширина растёт конусом, у основания и у края билборда — мягкое затухание.
     */
    private double smokeLayer(double s, double q, double w0, double spread, double speed, double time,
                              double startA, double startB) {
        double sa = Math.abs(s);
        double w = w0 + sa * spread;
        double qn = q / w;
        if (qn > Q_MAX || qn < -Q_MAX) {
            return 0;
        }
        double fall = Noise.smooth(startA, startB, sa) * (1 - Noise.smooth(this.maxReach * 0.55, this.maxReach, sa));
        if (fall <= 0) {
            return 0;
        }
        double core = Math.exp(-qn * qn * 0.9);
        // Две скорости — клубы меняют форму, а не просто едут. Сторона (знак s) — свой сдвиг.
        double side = s < 0 ? 5.3 : 0.0;
        double a = smokeAt(this.smokeA, sa - time * speed + side, qn);
        double b = smokeAt(this.smokeB, sa - time * speed * 0.62 + side * 1.7, qn);
        double dens = 0.45 + 0.55 * a * (0.45 + 0.75 * b);
        // Ярче у основания, тускнеет по мере расширения.
        double thin = w0 / w;
        return core * dens * fall * (0.35 + 0.65 * thin);
    }

    private float smokeAt(float[] tab, double s, double qn) {
        double fs = s / SMOKE_PERIOD * NS;
        double f0 = Math.floor(fs);
        int i = Math.floorMod((int) f0, NS);
        int i1 = i + 1 == NS ? 0 : i + 1;
        double ts = fs - f0;
        double fq = (qn / Q_MAX * 0.5 + 0.5) * (NQ - 1);
        int j = Math.max(0, Math.min(NQ - 2, (int) fq));
        double tq = fq - j;
        double a = tab[i * NQ + j] + (tab[i * NQ + j + 1] - tab[i * NQ + j]) * tq;
        double b = tab[i1 * NQ + j] + (tab[i1 * NQ + j + 1] - tab[i1 * NQ + j]) * tq;
        return (float) (a + (b - a) * ts);
    }

    /** Рендер кадра в ARGB. */
    public void frame(double time, double brightness, int[] out) {
        double t1 = time % FLOW_PERIOD;
        double t2 = (time + FLOW_PERIOD * 0.5) % FLOW_PERIOD;
        double w1 = 1 - Math.abs(2 * t1 / FLOW_PERIOD - 1);
        double w2 = 1 - w1;
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                double cr = 0;
                double cg = 0;
                double cb = 0;
                double trans = 1;
                int end = start[idx + 1];
                for (int k = start[idx]; k < end; k++) {
                    double u = hitU[k] * (NR - 1);
                    int i = Math.min(NR - 2, (int) u);
                    double fu = u - i;
                    double omega = omegaLut[i];
                    double phi = hitPhi[k];
                    double d1 = noiseAt(noiseA, i, fu, phi - omega * t1);
                    double d2 = noiseAt(noiseB, i, fu, phi - omega * t2);
                    double dens = d1 * w1 + d2 * w2;
                    double fade = fadeLut[i];
                    double a = Noise.clamp01((0.30 + 0.95 * dens) * alphaLut[i]);
                    double m = trans * Math.max(a, 0.12 * fade) * (0.25 + 1.35 * dens) * fade * hitInten[k] * brightness;
                    int c = hitChroma[k];
                    cr += m * (c >> 16 & 255) * (1 / 255.0);
                    cg += m * (c >> 8 & 255) * (1 / 255.0);
                    cb += m * (c & 255) * (1 / 255.0);
                    trans *= 1 - a;
                }
                int s = idx * 3;
                cr += trans * bg[s];
                cg += trans * bg[s + 1];
                cb += trans * bg[s + 2];

                // Кромка тени: тонкое кольцо у b_crit + ореол снаружи.
                double db = impact[idx] - B_CRIT;
                double glow = RING_I * Math.exp(-(db / RING_W) * (db / RING_W))
                        + (db > 0 ? HALO_I * Math.exp(-db / HALO_W) : 0);
                // Внутри тени: тёмная заливка, светлее к краю — тень читается на фоне космоса.
                double in = 1 - Noise.smooth(-0.25, 0.05, db);
                double shade = in * (SHADOW_FILL + INNER_I * Math.exp(Math.min(0, db) / INNER_W)) * trans * brightness;
                cr += shade * SHADOW_RGB[0];
                cg += shade * SHADOW_RGB[1];
                cb += shade * SHADOW_RGB[2];
                // Дым: виден только снаружи тени и за диском.
                double captured = bg[s] + bg[s + 1] + bg[s + 2] > 0 ? 1 : 0;
                double jet = captured * smokeLayer(axisS[idx], axisQ[idx], JET_W0, JET_SPREAD,
                        JET_SPEED, time, 1.4, 4.0);
                double flow = captured * smokeLayer(axisQ[idx], axisS[idx], FLOW_W0, FLOW_SPREAD,
                        FLOW_SPEED, time + 9.0, R_OUT * 0.6, R_OUT * 1.05);
                double em = trans * brightness;
                glow *= em;
                jet *= em * JET_I;
                flow *= em * FLOW_I;
                cr += glow * RING_RGB[0] + jet * JET_RGB[0] + flow * FLOW_RGB[0];
                cg += glow * RING_RGB[1] + jet * JET_RGB[1] + flow * FLOW_RGB[1];
                cb += glow * RING_RGB[2] + jet * JET_RGB[2] + flow * FLOW_RGB[2];
                double e = (edge[idx] & 255) / 255.0;
                double lum = Math.max(cr, Math.max(cg, cb));
                double alpha = Math.max(e, Math.min(1, lum * 1.5) * Math.min(1, e * 3));
                out[idx] = (int) (alpha * 255) << 24 | toneLut(cr) << 16 | toneLut(cg) << 8 | toneLut(cb);
            }
        });
    }
}
