package dev.hatek.client.module.impl.combat.aimassist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Настоящая нейросеть AimAssist: MLP 6 -&gt; 24 -&gt; 24 -&gt; 2.
 *
 * <p>Вход: [yawErr, pitchErr, dist, targetYawVel, targetPitchVel, part]
 * (угловая ошибка до хитбокса головы/тела в градусах, дистанция в блоках,
 * угловая скорость цели в градусах за тик, флаг части тела 0/1).
 * <p>Выход: [dYaw, dPitch] — доворот в градусах за тик, который сделал бы
 * сам игрок. Так сеть один в один копирует его скорость, плавность
 * и манеру наведения.
 *
 * <p>Обучение — настоящий backprop с оптимизатором Adam, мини-батчами,
 * z-нормализацией входов/выходов и разбиением train/validation 90/10.
 * Выполняется в фоновом потоке; инференс ({@link #predict}) потокобезопасен
 * после публикации обученного экземпляра.
 */
public final class AimNet {
    public static final int IN = 6;
    public static final int H1 = 24;
    public static final int H2 = 24;
    public static final int OUT = 2;
    public static final String FORMAT = "aimnet-v2";

    /** Минимум сэмплов, при котором обучение имеет смысл. */
    public static final int MIN_TRAIN_SAMPLES = 120;

    public interface Listener {
        void onEpoch(int epoch, int epochs, double trainLoss, double valLoss);
    }

    /** Параметр сети вместе с моментами Adam. */
    private static final class Param {
        final double[] v;
        final double[] m;
        final double[] vv;

        Param(int n) {
            this.v = new double[n];
            this.m = new double[n];
            this.vv = new double[n];
        }
    }

    private final Param w1 = new Param(H1 * IN);
    private final Param b1 = new Param(H1);
    private final Param w2 = new Param(H2 * H1);
    private final Param b2 = new Param(H2);
    private final Param w3 = new Param(OUT * H2);
    private final Param b3 = new Param(OUT);

    private final double[] xMean = new double[IN];
    private final double[] xStd = new double[IN];
    private final double[] yMean = new double[OUT];
    private final double[] yStd = new double[OUT];

    private volatile boolean trained;
    private double finalTrainLoss = Double.NaN;
    private double finalValLoss = Double.NaN;
    private int trainedSamples;
    /** p95 скорости доворота игрока, градусов/сек — кап для асиста. */
    private double autoMaxDegPerSec = 240.0;

    public AimNet() {
        Random rnd = new Random(0xA1ABE771L);
        xavier(this.w1.v, IN, H1, rnd);
        xavier(this.w2.v, H1, H2, rnd);
        xavier(this.w3.v, H2, OUT, rnd);
        Arrays.fill(this.xStd, 1.0);
        Arrays.fill(this.yStd, 1.0);
    }

    private static void xavier(double[] w, int fanIn, int fanOut, Random rnd) {
        double s = Math.sqrt(2.0 / (fanIn + fanOut));
        for (int i = 0; i < w.length; i++) {
            w[i] = rnd.nextGaussian() * s;
        }
    }

    public boolean isTrained() {
        return this.trained;
    }

    public double finalTrainLoss() {
        return this.finalTrainLoss;
    }

    public double finalValLoss() {
        return this.finalValLoss;
    }

    public int trainedSamples() {
        return this.trainedSamples;
    }

    public double autoMaxDegPerSec() {
        return this.autoMaxDegPerSec;
    }

    // ---------------- инференс ----------------

    /** Предсказать доворот игрока для данных признаков. */
    public float[] predict(float[] input) {
        double[] xn = new double[IN];
        for (int i = 0; i < IN; i++) {
            xn[i] = (input[i] - this.xMean[i]) / this.xStd[i];
        }
        double[] a1 = new double[H1];
        double[] a2 = new double[H2];
        double[] out = new double[OUT];
        forward(xn, a1, a2, out);
        return new float[]{
                (float) (out[0] * this.yStd[0] + this.yMean[0]),
                (float) (out[1] * this.yStd[1] + this.yMean[1])};
    }

    private void forward(double[] xn, double[] a1, double[] a2, double[] out) {
        double[] W1 = this.w1.v;
        double[] B1 = this.b1.v;
        double[] W2 = this.w2.v;
        double[] B2 = this.b2.v;
        double[] W3 = this.w3.v;
        double[] B3 = this.b3.v;
        for (int h = 0; h < H1; h++) {
            double s = B1[h];
            int base = h * IN;
            for (int i = 0; i < IN; i++) {
                s += W1[base + i] * xn[i];
            }
            a1[h] = Math.tanh(s);
        }
        for (int h = 0; h < H2; h++) {
            double s = B2[h];
            int base = h * H1;
            for (int j = 0; j < H1; j++) {
                s += W2[base + j] * a1[j];
            }
            a2[h] = Math.tanh(s);
        }
        for (int o = 0; o < OUT; o++) {
            double s = B3[o];
            int base = o * H2;
            for (int h = 0; h < H2; h++) {
                s += W3[base + h] * a2[h];
            }
            out[o] = s;
        }
    }

    // ---------------- обучение ----------------

    /**
     * Обучить сеть на сэмплах. Тяжёлая операция — вызывать в фоне.
     *
     * @return false, если данных слишком мало
     */
    public boolean train(List<AimSample> raw, int epochs, Listener listener) {
        List<AimSample> clean = new ArrayList<>(raw.size());
        for (AimSample s : raw) {
            if (s == null || s.input == null || s.output == null) {
                continue;
            }
            if (s.input.length != IN || s.output.length != OUT) {
                continue;
            }
            boolean ok = true;
            for (float v : s.input) {
                if (!Float.isFinite(v)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (float v : s.output) {
                    if (!Float.isFinite(v) || Math.abs(v) > 45.0f) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                clean.add(s);
            }
        }
        if (clean.size() < MIN_TRAIN_SAMPLES) {
            return false;
        }
        if (clean.size() > 12000) {
            List<AimSample> sub = new ArrayList<>(12000);
            double step = (double) clean.size() / 12000.0;
            for (int i = 0; i < 12000; i++) {
                sub.add(clean.get((int) (i * step)));
            }
            clean = sub;
        }

        Collections.shuffle(clean, new Random(42L));
        int valCount = clean.size() >= 400 ? Math.min(clean.size() / 10, 1500) : 0;
        List<AimSample> trainList = clean.subList(0, clean.size() - valCount);
        List<AimSample> valList = clean.subList(clean.size() - valCount, clean.size());

        computeNorms(trainList);

        int n = trainList.size();
        double[][] X = new double[n][IN];
        double[][] Y = new double[n][OUT];
        for (int k = 0; k < n; k++) {
            AimSample s = trainList.get(k);
            for (int i = 0; i < IN; i++) {
                X[k][i] = (s.input[i] - this.xMean[i]) / this.xStd[i];
            }
            for (int o = 0; o < OUT; o++) {
                Y[k][o] = (s.output[o] - this.yMean[o]) / this.yStd[o];
            }
        }
        int nv = valList.size();
        double[][] Xv = new double[nv][IN];
        double[][] Yv = new double[nv][OUT];
        for (int k = 0; k < nv; k++) {
            AimSample s = valList.get(k);
            for (int i = 0; i < IN; i++) {
                Xv[k][i] = (s.input[i] - this.xMean[i]) / this.xStd[i];
            }
            for (int o = 0; o < OUT; o++) {
                Yv[k][o] = (s.output[o] - this.yMean[o]) / this.yStd[o];
            }
        }

        final int batch = 64;
        final double lr = 0.003;
        Random rnd = new Random(7L);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        double[] a1 = new double[H1];
        double[] a2 = new double[H2];
        double[] pr = new double[OUT];
        double[] dO = new double[OUT];
        double[] da1 = new double[H1];
        double[] gw1 = new double[this.w1.v.length];
        double[] gb1 = new double[this.b1.v.length];
        double[] gw2 = new double[this.w2.v.length];
        double[] gb2 = new double[this.b2.v.length];
        double[] gw3 = new double[this.w3.v.length];
        double[] gb3 = new double[this.b3.v.length];

        double[] W1 = this.w1.v;
        double[] W2 = this.w2.v;
        double[] W3 = this.w3.v;

        double trainLoss = Double.NaN;
        double valLoss = Double.NaN;
        int step = 0;
        for (int e = 0; e < epochs; e++) {
            shuffle(order, rnd);
            for (int from = 0; from < n; from += batch) {
                int to = Math.min(from + batch, n);
                Arrays.fill(gw1, 0.0);
                Arrays.fill(gb1, 0.0);
                Arrays.fill(gw2, 0.0);
                Arrays.fill(gb2, 0.0);
                Arrays.fill(gw3, 0.0);
                Arrays.fill(gb3, 0.0);
                for (int k = from; k < to; k++) {
                    int idx = order[k];
                    forward(X[idx], a1, a2, pr);

                    for (int o = 0; o < OUT; o++) {
                        dO[o] = 2.0 * (pr[o] - Y[idx][o]) / OUT;
                        gb3[o] += dO[o];
                        int base = o * H2;
                        for (int h = 0; h < H2; h++) {
                            gw3[base + h] += dO[o] * a2[h];
                        }
                    }
                    Arrays.fill(da1, 0.0);
                    for (int h = 0; h < H2; h++) {
                        double dh = 0.0;
                        int base3 = h;
                        for (int o = 0; o < OUT; o++) {
                            dh += dO[o] * W3[o * H2 + base3];
                        }
                        dh *= 1.0 - a2[h] * a2[h]; // tanh'
                        gb2[h] += dh;
                        int base = h * H1;
                        for (int j = 0; j < H1; j++) {
                            gw2[base + j] += dh * a1[j];
                            da1[j] += dh * W2[base + j];
                        }
                    }
                    for (int j = 0; j < H1; j++) {
                        double dz = da1[j] * (1.0 - a1[j] * a1[j]); // tanh'
                        gb1[j] += dz;
                        int base = j * IN;
                        for (int i = 0; i < IN; i++) {
                            gw1[base + i] += dz * X[idx][i];
                        }
                    }
                }
                step++;
                adam(this.w1, gw1, lr, step);
                adam(this.b1, gb1, lr, step);
                adam(this.w2, gw2, lr, step);
                adam(this.b2, gb2, lr, step);
                adam(this.w3, gw3, lr, step);
                adam(this.b3, gb3, lr, step);
            }
            trainLoss = mse(X, Y, a1, a2, pr);
            valLoss = nv == 0 ? Double.NaN : mse(Xv, Yv, a1, a2, pr);
            if (listener != null) {
                listener.onEpoch(e + 1, epochs, trainLoss, valLoss);
            }
        }

        this.finalTrainLoss = trainLoss;
        this.finalValLoss = valLoss;
        this.trainedSamples = clean.size();
        this.autoMaxDegPerSec = percentile95(clean);
        this.trained = true;
        return true;
    }

    private static void shuffle(int[] order, Random rnd) {
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
    }

    private static void adam(Param p, double[] grad, double lr, int t) {
        final double b1 = 0.9;
        final double b2 = 0.999;
        final double eps = 1e-8;
        double bc1 = 1.0 - Math.pow(b1, t);
        double bc2 = 1.0 - Math.pow(b2, t);
        for (int i = 0; i < p.v.length; i++) {
            double g = grad[i];
            p.m[i] = b1 * p.m[i] + (1.0 - b1) * g;
            p.vv[i] = b2 * p.vv[i] + (1.0 - b2) * g * g;
            double mh = p.m[i] / bc1;
            double vh = p.vv[i] / bc2;
            p.v[i] -= lr * mh / (Math.sqrt(vh) + eps);
        }
    }

    private double mse(double[][] X, double[][] Y, double[] a1, double[] a2, double[] pr) {
        double sum = 0.0;
        for (int k = 0; k < X.length; k++) {
            forward(X[k], a1, a2, pr);
            for (int o = 0; o < OUT; o++) {
                double d = pr[o] - Y[k][o];
                sum += d * d;
            }
        }
        return X.length == 0 ? Double.NaN : sum / (X.length * OUT);
    }

    private void computeNorms(List<AimSample> samples) {
        Arrays.fill(this.xMean, 0.0);
        Arrays.fill(this.yMean, 0.0);
        int n = samples.size();
        for (AimSample s : samples) {
            for (int i = 0; i < IN; i++) {
                this.xMean[i] += s.input[i];
            }
            for (int o = 0; o < OUT; o++) {
                this.yMean[o] += s.output[o];
            }
        }
        for (int i = 0; i < IN; i++) {
            this.xMean[i] /= n;
        }
        for (int o = 0; o < OUT; o++) {
            this.yMean[o] /= n;
        }
        Arrays.fill(this.xStd, 0.0);
        Arrays.fill(this.yStd, 0.0);
        for (AimSample s : samples) {
            for (int i = 0; i < IN; i++) {
                double d = s.input[i] - this.xMean[i];
                this.xStd[i] += d * d;
            }
            for (int o = 0; o < OUT; o++) {
                double d = s.output[o] - this.yMean[o];
                this.yStd[o] += d * d;
            }
        }
        for (int i = 0; i < IN; i++) {
            this.xStd[i] = Math.sqrt(this.xStd[i] / n);
            if (this.xStd[i] < 1e-6) {
                this.xStd[i] = 1.0;
            }
        }
        for (int o = 0; o < OUT; o++) {
            this.yStd[o] = Math.sqrt(this.yStd[o] / n);
            if (this.yStd[o] < 1e-6) {
                this.yStd[o] = 1.0;
            }
        }
    }

    /** p95 модуля доворота за тик, переведённый в градусы/сек. */
    private static double percentile95(List<AimSample> samples) {
        double[] mags = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            float[] o = samples.get(i).output;
            mags[i] = Math.hypot(o[0], o[1]);
        }
        Arrays.sort(mags);
        double p95 = mags[(int) (mags.length * 0.95)];
        return Math.min(720.0, Math.max(60.0, p95 * 20.0));
    }

    // ---------------- сохранение ----------------

    public void save(Path file) throws IOException {
        Gson gson = new GsonBuilder().create();
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("in", IN);
        root.addProperty("h1", H1);
        root.addProperty("h2", H2);
        root.addProperty("out", OUT);
        root.add("x_mean", doubles(this.xMean));
        root.add("x_std", doubles(this.xStd));
        root.add("y_mean", doubles(this.yMean));
        root.add("y_std", doubles(this.yStd));
        root.add("w1", doubles(this.w1.v));
        root.add("b1", doubles(this.b1.v));
        root.add("w2", doubles(this.w2.v));
        root.add("b2", doubles(this.b2.v));
        root.add("w3", doubles(this.w3.v));
        root.add("b3", doubles(this.b3.v));
        JsonObject meta = new JsonObject();
        meta.addProperty("trained_samples", this.trainedSamples);
        meta.addProperty("final_train_loss", this.finalTrainLoss);
        meta.addProperty("final_val_loss", this.finalValLoss);
        meta.addProperty("auto_max_deg_per_sec", this.autoMaxDegPerSec);
        meta.addProperty("saved_at", System.currentTimeMillis());
        root.add("meta", meta);
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8);
    }

    public static AimNet load(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String format = root.has("format") ? root.get("format").getAsString() : "";
        if (!format.startsWith("aimnet")) {
            throw new IOException("Неизвестный формат весов: " + format);
        }
        if (!root.has("in") || !root.has("h1") || !root.has("h2") || !root.has("out")
                || root.get("in").getAsInt() != IN || root.get("h1").getAsInt() != H1
                || root.get("h2").getAsInt() != H2 || root.get("out").getAsInt() != OUT) {
            throw new IOException("Архитектура весов не совпадает с " + FORMAT);
        }
        AimNet net = new AimNet();
        fill(net.xMean, root.getAsJsonArray("x_mean"));
        fill(net.xStd, root.getAsJsonArray("x_std"));
        fill(net.yMean, root.getAsJsonArray("y_mean"));
        fill(net.yStd, root.getAsJsonArray("y_std"));
        fill(net.w1.v, root.getAsJsonArray("w1"));
        fill(net.b1.v, root.getAsJsonArray("b1"));
        fill(net.w2.v, root.getAsJsonArray("w2"));
        fill(net.b2.v, root.getAsJsonArray("b2"));
        fill(net.w3.v, root.getAsJsonArray("w3"));
        fill(net.b3.v, root.getAsJsonArray("b3"));
        if (root.has("meta")) {
            JsonObject meta = root.getAsJsonObject("meta");
            net.trainedSamples = meta.has("trained_samples")
                    ? meta.get("trained_samples").getAsInt() : 0;
            net.finalTrainLoss = meta.has("final_train_loss")
                    ? meta.get("final_train_loss").getAsDouble() : Double.NaN;
            net.finalValLoss = meta.has("final_val_loss")
                    ? meta.get("final_val_loss").getAsDouble() : Double.NaN;
            net.autoMaxDegPerSec = meta.has("auto_max_deg_per_sec")
                    ? meta.get("auto_max_deg_per_sec").getAsDouble() : 240.0;
        }
        net.trained = true;
        return net;
    }

    private static JsonArray doubles(double[] values) {
        JsonArray arr = new JsonArray();
        for (double v : values) {
            arr.add(v);
        }
        return arr;
    }

    private static void fill(double[] dst, JsonArray arr) {
        for (int i = 0; i < dst.length && i < arr.size(); i++) {
            dst[i] = arr.get(i).getAsDouble();
        }
    }
}
