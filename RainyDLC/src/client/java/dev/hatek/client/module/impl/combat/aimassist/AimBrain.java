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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Крошечный MLP 6 → 12 → 2 (tanh hidden, linear out), который учится
 * повторять стиль наводки игрока.
 *
 * <p>Вход: [dYawHead, dPitchHead, dYawBody, dPitchBody, distance, targetSpeed]
 * (углы — куда нужно довернуть до хитбокса головы/тела).
 * Выход: [yawDelta, pitchDelta] — доворот, который сделал бы сам игрок.
 *
 * <p>Формат весов {@code *.weights.json} один в один совпадает с тем,
 * что пишет {@code scripts/train_aim.py}, так что обучать можно
 * и на Python (numpy), а инференс всегда идёт здесь, в Java.
 */
public final class AimBrain {
    public static final int INPUT_SIZE = 6;
    public static final int HIDDEN_SIZE = 12;
    public static final int OUTPUT_SIZE = 2;

    private final double[][] w1 = new double[HIDDEN_SIZE][INPUT_SIZE];
    private final double[] b1 = new double[HIDDEN_SIZE];
    private final double[][] w2 = new double[OUTPUT_SIZE][HIDDEN_SIZE];
    private final double[] b2 = new double[OUTPUT_SIZE];

    private final double[] xMean = new double[INPUT_SIZE];
    private final double[] xStd = new double[INPUT_SIZE];
    private final double[] yMean = new double[OUTPUT_SIZE];
    private final double[] yStd = new double[OUTPUT_SIZE];

    private volatile boolean trained;
    /** Последний посчитанный loss (MSE на нормализованных данных). */
    public volatile double lastLoss = Double.NaN;
    private volatile int trainedSamples;
    /**
     * Авто-параметры из датасета: максимальная скорость доводки (°/с) —
     * 95-й перцентиль скорости игрока при записи; и альфа сглаживания —
     * из ровности движений игрока. Асист повторяет запись, а не слайдеры.
     */
    public volatile double autoSpeedDps = 120.0;
    public volatile double autoSmoothAlpha = 0.45;

    /** Колбэк обучения: progress 0..1, loss — текущий MSE. */
    @FunctionalInterface
    public interface TrainListener {
        void onEpoch(double progress, double loss);
    }

    public AimBrain() {
        Random rnd = new Random(1337L);
        double s1 = Math.sqrt(2.0 / (INPUT_SIZE + HIDDEN_SIZE));
        double s2 = Math.sqrt(2.0 / (HIDDEN_SIZE + OUTPUT_SIZE));
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                this.w1[h][i] = rnd.nextGaussian() * s1;
            }
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                this.w2[o][h] = rnd.nextGaussian() * s2;
            }
        }
        for (int i = 0; i < INPUT_SIZE; i++) {
            this.xStd[i] = 1.0;
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            this.yStd[o] = 1.0;
        }
    }

    public boolean isTrained() {
        return this.trained;
    }

    /** Предсказать доворот игрока для данных признаков. */
    public float[] predict(float[] input) {
        double[] xn = new double[INPUT_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            xn[i] = (input[i] - this.xMean[i]) / this.xStd[i];
        }
        double[] out = forwardNorm(xn);
        float[] res = new float[OUTPUT_SIZE];
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            res[o] = (float) (out[o] * this.yStd[o] + this.yMean[o]);
        }
        return res;
    }

    private double[] forwardNorm(double[] xn) {
        double[] hidden = new double[HIDDEN_SIZE];
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            double sum = this.b1[h];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += this.w1[h][i] * xn[i];
            }
            hidden[h] = Math.tanh(sum);
        }
        double[] out = new double[OUTPUT_SIZE];
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            double sum = this.b2[o];
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                sum += this.w2[o][h] * hidden[h];
            }
            out[o] = sum;
        }
        return out;
    }

    /**
     * Обучить на датасете. Тяжёлая операция — вызывать в фоне.
     *
     * @param listener принимает (progress 0.0..1.0, loss) по мере эпох
     */
    public void train(List<AimSample> rawSamples, int epochs, TrainListener listener) {
        List<AimSample> samples = new ArrayList<>(rawSamples);
        if (samples.size() < 10) {
            return;
        }
        this.trainedSamples = samples.size();
        computeNormalization(samples);
        computeAutoParams(samples);

        final int batch = 32;
        final double lr = 0.01;
        Random rnd = new Random(42L);

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(samples, rnd);
            for (int from = 0; from < samples.size(); from += batch) {
                int to = Math.min(from + batch, samples.size());
                step(samples.subList(from, to), lr);
            }
            if (listener != null && (epoch % 10 == 0 || epoch == epochs - 1)) {
                double loss = mse(samples);
                this.lastLoss = loss;
                listener.onEpoch((epoch + 1.0) / epochs, loss);
            }
        }
        this.trained = true;
    }

    /** Совместимость со старым колбэком (только прогресс). */
    public void train(List<AimSample> rawSamples, int epochs, DoubleConsumer progress) {
        train(rawSamples, epochs, progress == null ? null
                : (p, loss) -> progress.accept(p));
    }

    /** MSE на нормализованных данных — честная метрика обучения. */
    private double mse(List<AimSample> samples) {
        double sum = 0.0;
        int n = 0;
        double[] xn = new double[INPUT_SIZE];
        double[] yn = new double[OUTPUT_SIZE];
        for (AimSample s : samples) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                xn[i] = (s.input[i] - this.xMean[i]) / this.xStd[i];
            }
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                yn[o] = (s.output[o] - this.yMean[o]) / this.yStd[o];
            }
            double[] pred = forwardNorm(xn);
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                double d = pred[o] - yn[o];
                sum += d * d;
            }
            n++;
        }
        return n == 0 ? Double.NaN : sum / (n * OUTPUT_SIZE);
    }

    /**
     * Авто-параметры из записи игрока: максимальная скорость доводки —
     * 95-й перцентиль скорости его доворотов (°/с); альфа сглаживания —
     * из ровности его движений (ровная запись → точный повтор,
     * рваная → сглаживается сильнее).
     */
    private void computeAutoParams(List<AimSample> samples) {
        int n = samples.size();
        if (n == 0) {
            return;
        }
        double[] mags = new double[n];
        double mean = 0.0;
        for (int i = 0; i < n; i++) {
            float[] out = samples.get(i).output;
            mags[i] = Math.hypot(out[0], out[1]); // °/тик
            mean += mags[i];
        }
        mean /= n;
        double jerk = 0.0;
        for (int i = 1; i < n; i++) {
            jerk += Math.abs(mags[i] - mags[i - 1]);
        }
        jerk /= Math.max(1, n - 1);

        double[] sorted = mags.clone();
        java.util.Arrays.sort(sorted);
        double p95 = sorted[Math.min(n - 1, (int) (n * 0.95))];
        this.autoSpeedDps = Math.max(30.0, Math.min(360.0, p95 * 20.0));

        double steadiness = mean / (mean + 3.0 * jerk + 1e-9);
        this.autoSmoothAlpha = Math.max(0.15, Math.min(0.9, steadiness));
    }

    private void computeNormalization(List<AimSample> samples) {
        for (int i = 0; i < INPUT_SIZE; i++) {
            this.xMean[i] = 0.0;
            this.xStd[i] = 1.0;
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            this.yMean[o] = 0.0;
            this.yStd[o] = 1.0;
        }
        int n = samples.size();
        for (AimSample s : samples) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                this.xMean[i] += s.input[i];
            }
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                this.yMean[o] += s.output[o];
            }
        }
        for (int i = 0; i < INPUT_SIZE; i++) {
            this.xMean[i] /= n;
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            this.yMean[o] /= n;
        }
        for (AimSample s : samples) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                double d = s.input[i] - this.xMean[i];
                this.xStd[i] += d * d;
            }
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                double d = s.output[o] - this.yMean[o];
                this.yStd[o] += d * d;
            }
        }
        for (int i = 0; i < INPUT_SIZE; i++) {
            this.xStd[i] = Math.sqrt(this.xStd[i] / n);
            if (this.xStd[i] < 1e-6) {
                this.xStd[i] = 1.0;
            }
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            this.yStd[o] = Math.sqrt(this.yStd[o] / n);
            if (this.yStd[o] < 1e-6) {
                this.yStd[o] = 1.0;
            }
        }
    }

    private void step(List<AimSample> batchSamples, double lr) {
        double[][] gW1 = new double[HIDDEN_SIZE][INPUT_SIZE];
        double[] gB1 = new double[HIDDEN_SIZE];
        double[][] gW2 = new double[OUTPUT_SIZE][HIDDEN_SIZE];
        double[] gB2 = new double[OUTPUT_SIZE];

        for (AimSample s : batchSamples) {
            double[] xn = new double[INPUT_SIZE];
            double[] yn = new double[OUTPUT_SIZE];
            for (int i = 0; i < INPUT_SIZE; i++) {
                xn[i] = (s.input[i] - this.xMean[i]) / this.xStd[i];
            }
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                yn[o] = (s.output[o] - this.yMean[o]) / this.yStd[o];
            }

            double[] pre = new double[HIDDEN_SIZE];
            double[] hidden = new double[HIDDEN_SIZE];
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                double sum = this.b1[h];
                for (int i = 0; i < INPUT_SIZE; i++) {
                    sum += this.w1[h][i] * xn[i];
                }
                pre[h] = sum;
                hidden[h] = Math.tanh(sum);
            }
            double[] pred = new double[OUTPUT_SIZE];
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                double sum = this.b2[o];
                for (int h = 0; h < HIDDEN_SIZE; h++) {
                    sum += this.w2[o][h] * hidden[h];
                }
                pred[o] = sum;
            }

            // Backprop, MSE: dL/dpred = 2*(pred - y)/batch (масштаб уедет в lr)
            double[] dOut = new double[OUTPUT_SIZE];
            for (int o = 0; o < OUTPUT_SIZE; o++) {
                dOut[o] = 2.0 * (pred[o] - yn[o]);
                gB2[o] += dOut[o];
                for (int h = 0; h < HIDDEN_SIZE; h++) {
                    gW2[o][h] += dOut[o] * hidden[h];
                }
            }
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                double dh = 0.0;
                for (int o = 0; o < OUTPUT_SIZE; o++) {
                    dh += dOut[o] * this.w2[o][h];
                }
                dh *= 1.0 - hidden[h] * hidden[h]; // tanh'
                gB1[h] += dh;
                for (int i = 0; i < INPUT_SIZE; i++) {
                    gW1[h][i] += dh * xn[i];
                }
            }
        }

        double scale = lr / batchSamples.size();
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            for (int i = 0; i < INPUT_SIZE; i++) {
                this.w1[h][i] -= scale * gW1[h][i];
            }
            this.b1[h] -= scale * gB1[h];
        }
        for (int o = 0; o < OUTPUT_SIZE; o++) {
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                this.w2[o][h] -= scale * gW2[o][h];
            }
            this.b2[o] -= scale * gB2[o];
        }
    }

    public void save(Path file) throws IOException {
        Gson gson = new GsonBuilder().create();
        JsonObject root = new JsonObject();
        root.addProperty("input_size", INPUT_SIZE);
        root.addProperty("hidden_size", HIDDEN_SIZE);
        root.addProperty("output_size", OUTPUT_SIZE);
        root.add("x_mean", doubles(this.xMean));
        root.add("x_std", doubles(this.xStd));
        root.add("y_mean", doubles(this.yMean));
        root.add("y_std", doubles(this.yStd));
        JsonArray w1 = new JsonArray();
        for (double[] row : this.w1) {
            w1.add(doubles(row));
        }
        root.add("w1", w1);
        root.add("b1", doubles(this.b1));
        JsonArray w2 = new JsonArray();
        for (double[] row : this.w2) {
            w2.add(doubles(row));
        }
        root.add("w2", w2);
        root.add("b2", doubles(this.b2));
        root.addProperty("final_loss", this.lastLoss);
        root.addProperty("samples", this.trainedSamples);
        root.addProperty("auto_speed_dps", this.autoSpeedDps);
        root.addProperty("auto_smooth_alpha", this.autoSmoothAlpha);
        root.addProperty("trained_at", System.currentTimeMillis());
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(root), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static AimBrain load(Path file) throws IOException {
        String json = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        AimBrain brain = new AimBrain();
        fill(brain.xMean, root.getAsJsonArray("x_mean"));
        fill(brain.xStd, root.getAsJsonArray("x_std"));
        fill(brain.yMean, root.getAsJsonArray("y_mean"));
        fill(brain.yStd, root.getAsJsonArray("y_std"));
        JsonArray w1 = root.getAsJsonArray("w1");
        for (int h = 0; h < HIDDEN_SIZE && h < w1.size(); h++) {
            fill(brain.w1[h], w1.get(h).getAsJsonArray());
        }
        fill(brain.b1, root.getAsJsonArray("b1"));
        JsonArray w2 = root.getAsJsonArray("w2");
        for (int o = 0; o < OUTPUT_SIZE && o < w2.size(); o++) {
            fill(brain.w2[o], w2.get(o).getAsJsonArray());
        }
        fill(brain.b2, root.getAsJsonArray("b2"));
        brain.autoSpeedDps = optDouble(root, "auto_speed_dps", 120.0);
        brain.autoSmoothAlpha = optDouble(root, "auto_smooth_alpha", 0.45);
        brain.trained = true;
        return brain;
    }

    public static Path weightsFileOf(String profileName) {
        Path base = AimDataset.fileOf(profileName);
        String name = base.getFileName().toString();
        return base.getParent().resolve(name.substring(0, name.length() - 5) + ".weights.json");
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

    private static double optDouble(JsonObject root, String key, double def) {
        try {
            return root.has(key) ? root.get(key).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
