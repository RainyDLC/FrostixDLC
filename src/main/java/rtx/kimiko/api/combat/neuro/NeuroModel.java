package rtx.kimiko.api.combat.neuro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class NeuroModel {
    public int hiddenSize;
    public int mixtures;
    public int features;
    public int actions;
    public int combat;
    public int freezeCut;
    public float limitYaw;
    public float limitPitch;
    public float[] errorCurve;
    public float slack;
    public float temperature;
    public float speed;
    public float memory;
    public float memory2;
    public int hold;
    public boolean dequant;

    public float[][] gruInputWeights;
    public float[][] gruHiddenWeights;
    public float[] gruInputBias;
    public float[] gruHiddenBias;
    public float[][] outputWeights;
    public float[] outputBias;

    private static boolean loaded = false;
    private static NeuroModel active = null;
    private static String activeName = null;

    private NeuroModel(JsonObject json) {
        this.hiddenSize = json.get("hidden").getAsInt();
        this.mixtures = json.get("mix").getAsInt();
        this.features = json.get("features").getAsInt();
        this.actions = json.has("actions") ? json.get("actions").getAsInt() : 0;
        this.combat = json.has("combat") ? json.get("combat").getAsInt() : 0;
        this.freezeCut = json.has("freezeCut") ? json.get("freezeCut").getAsInt() : 12;

        JsonArray limits = json.getAsJsonArray("limits");
        this.limitYaw = limits.get(0).getAsFloat();
        this.limitPitch = limits.get(1).getAsFloat();

        this.errorCurve = json.has("error") ? parseFloatArray(json.getAsJsonArray("error")) : new float[0];

        JsonObject style = json.has("style") ? json.getAsJsonObject("style") : null;
        this.slack = style != null && style.has("slack") ? style.get("slack").getAsFloat() : 0.45f;
        this.temperature = style != null && style.has("temperature") ? style.get("temperature").getAsFloat() : 0.7f;
        this.speed = style != null && style.has("speed") ? style.get("speed").getAsFloat() : 1.1f;
        this.memory = style != null && style.has("memory") ? style.get("memory").getAsFloat() : 0.0f;
        this.memory2 = style != null && style.has("memory2") ? style.get("memory2").getAsFloat() : 0.0f;
        this.hold = style != null && style.has("hold") ? style.get("hold").getAsInt() : 1;
        this.dequant = style != null && style.has("dequant") && style.get("dequant").getAsInt() != 0;

        JsonObject gru = json.getAsJsonObject("gru");
        this.gruInputWeights = parseFloatMatrix(gru.getAsJsonArray("wi"));
        this.gruHiddenWeights = parseFloatMatrix(gru.getAsJsonArray("wh"));
        this.gruInputBias = parseFloatArray(gru.getAsJsonArray("bi"));
        this.gruHiddenBias = parseFloatArray(gru.getAsJsonArray("bh"));

        JsonObject out = json.getAsJsonObject("out");
        this.outputWeights = parseFloatMatrix(out.getAsJsonArray("w"));
        this.outputBias = parseFloatArray(out.getAsJsonArray("b"));
    }

    public float[] forward(float[] inputFeatures, float[] hiddenState) {
        float[] inputGate = new float[3 * this.hiddenSize];
        float[] hiddenGate = new float[3 * this.hiddenSize];

        for (int n = 0; n < 3 * this.hiddenSize; ++n) {
            float inVal = this.gruInputBias[n];
            float[] wi = this.gruInputWeights[n];
            for (int j = 0; j < wi.length; ++j) {
                inVal += wi[j] * inputFeatures[j];
            }
            inputGate[n] = inVal;

            float hidVal = this.gruHiddenBias[n];
            float[] wh = this.gruHiddenWeights[n];
            for (int j = 0; j < wh.length; ++j) {
                hidVal += wh[j] * hiddenState[j];
            }
            hiddenGate[n] = hidVal;
        }

        for (int n = 0; n < this.hiddenSize; ++n) {
            float r = sigmoid(inputGate[n] + hiddenGate[n]);
            float z = sigmoid(inputGate[this.hiddenSize + n] + hiddenGate[this.hiddenSize + n]);
            float hCand = (float) Math.tanh(inputGate[2 * this.hiddenSize + n] + r * hiddenGate[2 * this.hiddenSize + n]);
            hiddenState[n] = (1.0f - z) * hCand + z * hiddenState[n];
        }

        float[] output = new float[this.outputWeights.length];
        for (int i = 0; i < this.outputWeights.length; ++i) {
            float sum = this.outputBias[i];
            float[] w = this.outputWeights[i];
            for (int j = 0; j < w.length; ++j) {
                sum += w[j] * hiddenState[j];
            }
            output[i] = sum;
        }
        return output;
    }

    public float[] newHiddenState() {
        return new float[this.hiddenSize];
    }

    public int sampleMixture(float[] fs, float rnd) {
        float maxVal = fs[1];
        for (int i = 1; i < this.mixtures; ++i) {
            maxVal = Math.max(maxVal, fs[1 + 6 * i]);
        }
        float sumExp = 0.0f;
        for (int i = 0; i < this.mixtures; ++i) {
            sumExp += (float) Math.exp(fs[1 + 6 * i] - maxVal);
        }
        float threshold = rnd * sumExp;
        for (int i = 0; i < this.mixtures; ++i) {
            threshold -= (float) Math.exp(fs[1 + 6 * i] - maxVal);
            if (threshold <= 0.0f) {
                return i;
            }
        }
        return this.mixtures - 1;
    }

    public float computeStep(float mean, float logStd, boolean isYaw, float bias, float speed, float scale) {
        float std = (float) Math.exp(Math.max(-4.0f, Math.min(1.5f, logStd)));
        float val = Math.max(-8.0f, Math.min(8.0f, mean + bias * std * speed));
        float limit = isYaw ? this.limitYaw : this.limitPitch;
        return Math.max(-limit, Math.min(limit, scale * (float) Math.sinh(val)));
    }

    public float sampleErrorCurve(float rnd) {
        if (this.errorCurve.length == 0) {
            return 0.0f;
        }
        float scaled = Math.max(0.0f, Math.min(1.0f, rnd)) * (float) (this.errorCurve.length - 1);
        int idx = (int) scaled;
        if (idx >= this.errorCurve.length - 1) {
            return this.errorCurve[this.errorCurve.length - 1];
        }
        return this.errorCurve[idx] + (this.errorCurve[idx + 1] - this.errorCurve[idx]) * (scaled - (float) idx);
    }

    public int sampleGranular(float[] fs, float rnd) {
        return this.sampleCategory(fs, 1, 24, rnd);
    }

    public int sampleFine(float[] fs, float rnd) {
        return this.sampleCategory(fs, 25, 8, rnd);
    }

    public int sampleCoarse(float[] fs, float rnd) {
        return this.sampleCategory(fs, 33, 16, rnd);
    }

    private int sampleCategory(float[] fs, int offset, int count, float rnd) {
        if (!this.isCombatModel()) {
            return 0;
        }
        int base = 1 + 6 * this.mixtures + offset;
        float maxVal = fs[base];
        for (int k = 1; k < count; ++k) {
            maxVal = Math.max(maxVal, fs[base + k]);
        }
        float sumExp = 0.0f;
        for (int k = 0; k < count; ++k) {
            sumExp += (float) Math.exp(fs[base + k] - maxVal);
        }
        float threshold = Math.max(0.0f, Math.min(1.0f, rnd)) * sumExp;
        for (int k = 0; k < count; ++k) {
            threshold -= (float) Math.exp(fs[base + k] - maxVal);
            if (threshold <= 0.0f) {
                return k;
            }
        }
        return count - 1;
    }

    public static float sigmoid(float x) {
        return 1.0f / (1.0f + (float) Math.exp(-x));
    }

    public static float boundedTanh(float x) {
        return (float) Math.tanh(x) * 0.95f;
    }

    public static Path getNeuroDir() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path runDir = mc.runDirectory != null ? mc.runDirectory.toPath() : Path.of(".");
        return runDir.resolve("kimiko").resolve("neuro");
    }

    public static Path getProfilePath(String name) {
        return getNeuroDir().resolve(name + ".json");
    }

    public static Path getDataDir() {
        return getNeuroDir().resolve("data");
    }

    public static Path getTrainerDir() {
        return getNeuroDir().resolve("trainer");
    }

    public static String getActiveName() {
        if (activeName == null) {
            try {
                Path path = getNeuroDir().resolve("active.txt");
                activeName = Files.isRegularFile(path) ? Files.readString(path).trim() : "default";
            } catch (Throwable t) {
                activeName = "default";
            }
            if (activeName.isEmpty()) {
                activeName = "default";
            }
        }
        return activeName;
    }

    public static NeuroModel getActive() {
        if (!loaded) {
            loaded = true;
            active = load(getActiveName());
        }
        return active;
    }

    public static void invalidate() {
        loaded = false;
        active = null;
        AuraHumanStyle.invalidate();
    }

    public static NeuroModel load(String name) {
        try {
            JsonObject json = null;
            Path profilePath = getProfilePath(name);
            if (Files.isRegularFile(profilePath)) {
                json = JsonParser.parseString(Files.readString(profilePath)).getAsJsonObject();
            } else if ("default".equals(name)) {
                InputStream is = NeuroModel.class.getClassLoader().getResourceAsStream("assets/kimiko/neuro/default.json");
                if (is != null) {
                    try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        json = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                }
            }

            if (json == null) {
                return null;
            }

            int featCount = json.get("features").getAsInt();
            if (featCount < 17 || featCount > 27 || !json.has("mix")) {
                return null;
            }
            if (!json.has("units") || !"deg".equals(json.get("units").getAsString())) {
                return null;
            }
            return new NeuroModel(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> listProfiles() {
        List<String> list = new ArrayList<>();
        list.add("default");
        Path dir = getNeuroDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                        .filter(n -> !list.contains(n))
                        .sorted()
                        .forEach(list::add);
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    public static boolean setActive(String name) {
        try {
            activeName = name;
            Files.createDirectories(getNeuroDir());
            Files.writeString(getNeuroDir().resolve("active.txt"), name);
            invalidate();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static float[] parseFloatArray(JsonArray array) {
        float[] res = new float[array.size()];
        for (int i = 0; i < array.size(); ++i) {
            res[i] = array.get(i).getAsFloat();
        }
        return res;
    }

    private static float[][] parseFloatMatrix(JsonArray array) {
        float[][] res = new float[array.size()][];
        for (int i = 0; i < array.size(); ++i) {
            res[i] = parseFloatArray(array.get(i).getAsJsonArray());
        }
        return res;
    }

    public boolean isCombatModel() {
        return this.combat == 2 && this.actions == 49;
    }

    public boolean isDequantized() {
        return this.dequant;
    }

    public float getSlack() {
        return this.slack;
    }

    public float getTemperature() {
        return this.temperature;
    }

    public float getSpeed() {
        return this.speed;
    }

    public float getMemory() {
        return this.memory;
    }

    public float getMemory2() {
        return this.memory2;
    }

    public int getHold() {
        return Math.max(1, this.hold);
    }

    public int getFreezeCut() {
        return this.freezeCut;
    }

    public int getFeatures() {
        return this.features;
    }

    public int getActions() {
        return this.actions;
    }
}
