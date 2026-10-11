package dev.hatek.client.ambience;

import java.util.stream.IntStream;

/**
 * Чёрная дыра Шварцшильда: трассировка нуль-геодезических (RK4),
 * аккреционный диск с доплеровским усилением и гравитационным
 * красным смещением, линзирование фона. Тяжёлая часть считается
 * один раз, кадры анимации — быстро (только вращение диска).
 */
public final class BlackHoleGen {
    /** Направление на чёрную дыру в системе неба и базис билборда. */
    public static final double[] DIR = SkyGen.norm(0.42, 0.40, -0.81);
    public static final double[] RIGHT;
    public static final double[] UP;
    /** tan половины углового размера билборда. */
    public static final double TAN_HALF = 0.62;

    private static final double CAM_DIST = 28.0;
    private static final double INCLINATION = Math.toRadians(8.5);
    private static final double ROLL = Math.toRadians(-14);
    private static final double R_IN = 6.0;
    private static final double R_OUT = 19.0;
    private static final int MAX_HITS = 3;
    private static final int NR = 128;
    private static final int NP = 1024;
    private static final double FLOW_PERIOD = 24.0;

    static {
        double[] r = SkyGen.cross(DIR, new double[]{0, 1, 0});
        RIGHT = SkyGen.norm(r[0], r[1], r[2]);
        UP = SkyGen.cross(RIGHT, DIR);
    }

    public final int size;
    private final float[] bg;
    private final float[] hitR;
    private final float[] hitPhi;
    private final float[] hitG;
    private final byte[] hitCount;
    /** Предрасчёт на попадание: угловая скорость, затухание, цвет×яркость. */
    private final float[] hitOmega;
    private final float[] hitFade;
    private final float[] hitCol;
    private final float[] edge;
    private final float[] diskNoiseA;
    private final float[] diskNoiseB;

    private BlackHoleGen(int size) {
        this.size = size;
        int n = size * size;
        this.bg = new float[n * 3];
        this.hitR = new float[n * MAX_HITS];
        this.hitPhi = new float[n * MAX_HITS];
        this.hitG = new float[n * MAX_HITS];
        this.hitCount = new byte[n];
        this.hitOmega = new float[n * MAX_HITS];
        this.hitFade = new float[n * MAX_HITS];
        this.hitCol = new float[n * MAX_HITS * 3];
        this.edge = new float[n];
        this.diskNoiseA = new float[NR * NP];
        this.diskNoiseB = new float[NR * NP];
    }

    /**
     * Полная предрасчётка. {@code baseSky} — ARGB equirect фона того же
     * режима (w×h), {@code starMap} — линейная карта звёзд того же размера.
     */
    public static BlackHoleGen build(int size, int[] baseSky, int bw, int bh, float[] starMap) {
        BlackHoleGen g = new BlackHoleGen(size);
        g.buildDiskNoise();
        g.trace(baseSky, bw, bh, starMap);
        g.prepareHits();
        return g;
    }

    private void buildDiskNoise() {
        IntStream.range(0, NR).parallel().forEach(i -> {
            double u = i / (double) (NR - 1);
            double lr = Math.log(R_IN) + u * (Math.log(R_OUT) - Math.log(R_IN));
            for (int j = 0; j < NP; j++) {
                double ph = j / (double) NP * Math.PI * 2;
                double cx = Math.cos(ph);
                double sx = Math.sin(ph);
                diskNoiseA[i * NP + j] = (float) diskDensity(cx, sx, lr, 0);
                diskNoiseB[i * NP + j] = (float) diskDensity(cx, sx, lr, 31.7);
            }
        });
    }

    private static double diskDensity(double cx, double sx, double lr, double o) {
        double n = Noise.fbm(cx * 2.2 + o, sx * 2.2, lr * 9 + o, 5);
        double fine = Noise.fbm(cx * 6 + o, sx * 6, lr * 30, 3);
        double rings = 0.5 + 0.5 * Math.sin(lr * 40 + n * 9 + o);
        double v = 0.62 * n + 0.18 * rings * fine * 2 + 0.2 * fine;
        return Noise.clamp01((v - 0.25) * 1.6);
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

        IntStream.range(0, size).parallel().forEach(y -> {
            double[] p = new double[3];
            double[] v = new double[3];
            double[] tmp = new double[12];
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                double px = (x + 0.5) / size * 2 - 1;
                double py = 1 - (y + 0.5) / size * 2;
                double rho = Math.sqrt(px * px + py * py);
                edge[idx] = (float) Noise.smooth(1.0, 0.70, rho);
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
                boolean escaped = integrate(idx, p, v, tmp);
                int k = idx * 3;
                if (escaped) {
                    double lx = v[0] * rt[0] + v[1] * rt[1] + v[2] * rt[2];
                    double ly = v[0] * up[0] + v[1] * up[1] + v[2] * up[2];
                    double lz = v[0] * f[0] + v[1] * f[1] + v[2] * f[2];
                    double wx = RIGHT[0] * lx + UP[0] * ly + DIR[0] * lz;
                    double wy = RIGHT[1] * lx + UP[1] * ly + DIR[1] * lz;
                    double wz = RIGHT[2] * lx + UP[2] * ly + DIR[2] * lz;
                    sampleSky(wx, wy, wz, baseSky, bw, bh, starMap, bg, k);
                }
            }
        });
    }

    /** Возвращает true, если луч ушёл на бесконечность. */
    private boolean integrate(int idx, double[] p, double[] v, double[] t) {
        double hx = p[1] * v[2] - p[2] * v[1];
        double hy = p[2] * v[0] - p[0] * v[2];
        double hz = p[0] * v[1] - p[1] * v[0];
        double h2 = hx * hx + hy * hy + hz * hz;
        int hits = 0;
        for (int step = 0; step < 4000; step++) {
            double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            if (r < 2.02) {
                return false;
            }
            if (r > CAM_DIST * 1.8 && p[0] * v[0] + p[1] * v[1] + p[2] * v[2] > 0) {
                return true;
            }
            double dt = Math.min(1.6, Math.max(0.02, 0.022 * r * r));
            double y0 = p[1];
            double ox = p[0];
            double oz = p[2];
            double ovx = v[0];
            double ovy = v[1];
            double ovz = v[2];
            rk4(p, v, h2, dt, t);
            if (hits < MAX_HITS && (y0 > 0) != (p[1] > 0)) {
                double s = y0 / (y0 - p[1]);
                double cx = ox + (p[0] - ox) * s;
                double cz = oz + (p[2] - oz) * s;
                double cr = Math.sqrt(cx * cx + cz * cz);
                if (cr >= R_IN && cr <= R_OUT) {
                    double nx = -(ovx + (v[0] - ovx) * s);
                    double ny = -(ovy + (v[1] - ovy) * s);
                    double nz = -(ovz + (v[2] - ovz) * s);
                    double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    double phi = Math.atan2(cz, cx);
                    double tx = -Math.sin(phi);
                    double tz = Math.cos(phi);
                    double beta = Math.min(0.75, Math.sqrt(1.0 / (cr - 2.0)));
                    double gamma = 1.0 / Math.sqrt(1 - beta * beta);
                    double cosT = (tx * nx + tz * nz) / nl;
                    double g = Math.sqrt(1 - 2.0 / cr) / (gamma * (1 - beta * cosT));
                    int k = idx * MAX_HITS + hits;
                    hitR[k] = (float) cr;
                    hitPhi[k] = (float) phi;
                    hitG[k] = (float) g;
                    hits++;
                    hitCount[idx] = (byte) hits;
                }
            }
        }
        return false;
    }

    private static void rk4(double[] p, double[] v, double h2, double dt, double[] t) {
        // k1
        accel(p[0], p[1], p[2], h2, t, 0);
        double k1px = v[0], k1py = v[1], k1pz = v[2];
        double k1vx = t[0], k1vy = t[1], k1vz = t[2];
        // k2
        double hx = dt * 0.5;
        accel(p[0] + k1px * hx, p[1] + k1py * hx, p[2] + k1pz * hx, h2, t, 0);
        double k2px = v[0] + k1vx * hx, k2py = v[1] + k1vy * hx, k2pz = v[2] + k1vz * hx;
        double k2vx = t[0], k2vy = t[1], k2vz = t[2];
        // k3
        accel(p[0] + k2px * hx, p[1] + k2py * hx, p[2] + k2pz * hx, h2, t, 0);
        double k3px = v[0] + k2vx * hx, k3py = v[1] + k2vy * hx, k3pz = v[2] + k2vz * hx;
        double k3vx = t[0], k3vy = t[1], k3vz = t[2];
        // k4
        accel(p[0] + k3px * dt, p[1] + k3py * dt, p[2] + k3pz * dt, h2, t, 0);
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

    private static void accel(double x, double y, double z, double h2, double[] out, int o) {
        double r2 = x * x + y * y + z * z;
        double r5 = r2 * r2 * Math.sqrt(r2);
        double k = -1.5 * h2 / r5;
        out[o] = k * x;
        out[o + 1] = k * y;
        out[o + 2] = k * z;
    }

    /** Обратный тонмаппинг ARGB → линейная яркость. */
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

    private static final double[][] PALETTE = {
            {0.0, 1.0, 0.16, 0.02},
            {0.45, 1.0, 0.42, 0.08},
            {0.85, 1.0, 0.72, 0.38},
            {1.15, 1.0, 0.93, 0.82},
            {1.6, 0.78, 0.86, 1.0}
    };

    private static final double T_MAX;

    static {
        double m = 0;
        for (double r = R_IN; r < R_OUT; r += 0.01) {
            m = Math.max(m, tProfile(r));
        }
        T_MAX = m;
    }

    private static double tProfile(double r) {
        return Math.pow(R_IN / r, 0.75) * Math.pow(Math.max(0, 1 - Math.sqrt(R_IN / r)), 0.25);
    }

    private void prepareHits() {
        int n = size * size;
        for (int idx = 0; idx < n; idx++) {
            for (int h = 0; h < hitCount[idx]; h++) {
                int k = idx * MAX_HITS + h;
                double r = hitR[k];
                double g = hitG[k];
                double tn = tProfile(r) / T_MAX;
                double inten = 2.8 * Math.pow(tn, 1.3) * g * g * g * g;
                double[] col = palette(tn * g);
                hitOmega[k] = (float) (Math.pow(R_IN / r, 1.5) * 0.9);
                hitFade[k] = (float) (Noise.smooth(R_IN, R_IN + 0.8, r) * Noise.smooth(R_OUT, 10.5, r));
                hitCol[k * 3] = (float) (col[0] * inten);
                hitCol[k * 3 + 1] = (float) (col[1] * inten);
                hitCol[k * 3 + 2] = (float) (col[2] * inten);
            }
        }
    }

    private static final int TONE_LUT = 4096;
    private static final double TONE_MAX = 10.0;
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

    private float noiseAt(float[] tex, double r, double phi) {
        double u = (Math.log(r) - Math.log(R_IN)) / (Math.log(R_OUT) - Math.log(R_IN)) * (NR - 1);
        double vv = phi / (Math.PI * 2) * NP;
        int i = Math.max(0, Math.min(NR - 2, (int) u));
        double fu = Math.max(0, Math.min(1, u - i));
        double fv0 = Math.floor(vv);
        int j = Math.floorMod((int) fv0, NP);
        int j1 = (j + 1) % NP;
        double fv = vv - fv0;
        double a = tex[i * NP + j] * (1 - fv) + tex[i * NP + j1] * fv;
        double b = tex[(i + 1) * NP + j] * (1 - fv) + tex[(i + 1) * NP + j1] * fv;
        return (float) (a * (1 - fu) + b * fu);
    }

    /**
     * Рендер кадра в ARGB. {@code time} — секунды анимации,
     * {@code brightness} — множитель яркости диска.
     */
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
                int hits = hitCount[idx];
                for (int h = 0; h < hits; h++) {
                    int k = idx * MAX_HITS + h;
                    double r = hitR[k];
                    double omega = hitOmega[k];
                    double d1 = noiseAt(diskNoiseA, r, hitPhi[k] - omega * t1);
                    double d2 = noiseAt(diskNoiseB, r, hitPhi[k] - omega * t2);
                    double dens = d1 * w1 + d2 * w2;
                    double fade = hitFade[k];
                    double a = Noise.clamp01((0.35 + 0.9 * dens) * fade);
                    double m = trans * a * (0.3 + 1.2 * dens) * fade * brightness;
                    cr += m * hitCol[k * 3];
                    cg += m * hitCol[k * 3 + 1];
                    cb += m * hitCol[k * 3 + 2];
                    trans *= 1 - a;
                }
                int s = idx * 3;
                cr += trans * bg[s];
                cg += trans * bg[s + 1];
                cb += trans * bg[s + 2];
                double e = edge[idx];
                double lum = Math.max(cr, Math.max(cg, cb));
                double alpha = Math.max(e, Math.min(1, lum * 1.5) * Math.min(1, e * 3));
                out[idx] = (int) (alpha * 255) << 24 | toneLut(cr) << 16 | toneLut(cg) << 8 | toneLut(cb);
            }
        });
    }

    private static double[] palette(double t) {
        double[][] p = PALETTE;
        if (t <= p[0][0]) {
            return new double[]{p[0][1], p[0][2], p[0][3]};
        }
        for (int i = 1; i < p.length; i++) {
            if (t <= p[i][0]) {
                double f = (t - p[i - 1][0]) / (p[i][0] - p[i - 1][0]);
                return new double[]{
                        p[i - 1][1] + (p[i][1] - p[i - 1][1]) * f,
                        p[i - 1][2] + (p[i][2] - p[i - 1][2]) * f,
                        p[i - 1][3] + (p[i][3] - p[i - 1][3]) * f};
            }
        }
        double[] l = p[p.length - 1];
        return new double[]{l[1], l[2], l[3]};
    }
}
