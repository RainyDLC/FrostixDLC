package rtx.kimiko.api.modules.impl.Utils.neuro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GRU-based aim model ported from the Rockstar neuro aim system.
 * Loads a quantized JSON profile (bundled default or user profile in the run directory)
 * and runs forward inference producing mixture-density outputs for human-like aim steps.
 */
public final class NeuroModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("Kimiko/Neuro");

    public final int hiddenSize;
    public final int mixtures;
    public final int features;
    public final int actions;
    public final int combat;
    public final int freezeCut;
    public final float limitYaw;
    public final float limitPitch;
    public final float[] errorCurve;
    public final float slack;
    public final float temperature;
    public final float speed;
    public final float memory;
    public final float memory2;
    public final int hold;
    public final boolean dequant;
    public final float[][] gruInputWeights;
    public final float[][] gruHiddenWeights;
    public final float[] gruInputBias;
    public final float[] gruHiddenBias;
    public final float[][] outputWeights;
    public final float[] outputBias;

    private static boolean loaded;
    @Nullable
    private static NeuroModel active;
    @Nullable
    private static String activeName;

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

    public boolean isCombatModel() {
        return this.combat == 2 && this.actions == 49;
    }

    public boolean isDequantized() {
        return this.dequant;
    }

    public int getFreezeCut() {
        return this.freezeCut;
    }

    public int getHold() {
        return Math.max(1, this.hold);
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

    public int getFeatures() {
        return this.features;
    }

    public int getActions() {
        return this.actions;
    }

    /** Sample one yaw/pitch step from a mixture component: mu + sigma * noise, squashed and limited. */
    public float computeStep(float mu, float logSigma, boolean isYaw, float noise, float speedFactor, float memoryFactor) {
        float sigma = (float) Math.exp(Math.max(-4.0, Math.min(1.5, logSigma)));
        float value = Math.max(-8.0f, Math.min(8.0f, mu + speedFactor * sigma * noise));
        float limit = isYaw ? this.limitYaw : this.limitPitch;
        return Math.max(-limit, Math.min(limit, memoryFactor * (float) Math.sinh(value)));
    }

    /** Interpolates the learned aim-error magnitude curve at t in [0,1]. */
    public float sampleErrorCurve(float t) {
        if (this.errorCurve.length == 0) {
            return 0.0f;
        }
        float pos = Math.max(0.0f, Math.min(1.0f, t)) * (this.errorCurve.length - 1);
        int idx = (int) pos;
        if (idx >= this.errorCurve.length - 1) {
            return this.errorCurve[this.errorCurve.length - 1];
        }
        return this.errorCurve[idx] + (this.errorCurve[idx + 1] - this.errorCurve[idx]) * (pos - idx);
    }

    /** Samples a mixture component index from the softmax over mixture logits (stride 6 starting at 1). */
    public int sampleMixture(float[] output, float rand) {
        float max = output[1];
        for (int i = 1; i < this.mixtures; i++) {
            max = Math.max(max, output[1 + 6 * i]);
        }
        float sum = 0.0f;
        for (int i = 0; i < this.mixtures; i++) {
            sum += (float) Math.exp(output[1 + 6 * i] - max);
        }
        float pick = rand * sum;
        for (int i = 0; i < this.mixtures; i++) {
            pick -= (float) Math.exp(output[1 + 6 * i] - max);
            if (pick <= 0.0f) {
                return i;
            }
        }
        return this.mixtures - 1;
    }

    /** Combat models only: samples a categorical duration head (granular/fine/coarse). */
    public int sampleDuration(float[] output, int headOffset, int categories, float rand) {
        if (!this.isCombatModel()) {
            return 0;
        }
        int base = 1 + 6 * this.mixtures + headOffset;
        float max = output[base];
        for (int k = 1; k < categories; k++) {
            max = Math.max(max, output[base + k]);
        }
        float sum = 0.0f;
        for (int k = 0; k < categories; k++) {
            sum += (float) Math.exp(output[base + k] - max);
        }
        float pick = Math.max(0.0f, Math.min(1.0f, rand)) * sum;
        for (int k = 0; k < categories; k++) {
            pick -= (float) Math.exp(output[base + k] - max);
            if (pick <= 0.0f) {
                return k;
            }
        }
        return categories - 1;
    }

    public int sampleGranular(float[] output, float rand) {
        return this.sampleDuration(output, 1, 24, rand);
    }

    public int sampleFine(float[] output, float rand) {
        return this.sampleDuration(output, 25, 8, rand);
    }

    public int sampleCoarse(float[] output, float rand) {
        return this.sampleDuration(output, 33, 16, rand);
    }

    /** One GRU cell update followed by the linear output head. Mutates and returns via hidden state array. */
    public float[] forward(float[] input, float[] hidden) {
        int h = this.hiddenSize;
        float[] gateInput = new float[3 * h];
        float[] gateHidden = new float[3 * h];
        for (int n = 0; n < 3 * h; n++) {
            float acc = this.gruInputBias[n];
            float[] row = this.gruInputWeights[n];
            for (int k = 0; k < row.length; k++) {
                acc += row[k] * input[k];
            }
            gateInput[n] = acc;
            acc = this.gruHiddenBias[n];
            row = this.gruHiddenWeights[n];
            for (int k = 0; k < row.length; k++) {
                acc += row[k] * hidden[k];
            }
            gateHidden[n] = acc;
        }
        for (int n = 0; n < h; n++) {
            float reset = sigmoid(gateInput[n] + gateHidden[n]);
            float update = sigmoid(gateInput[h + n] + gateHidden[h + n]);
            float candidate = (float) Math.tanh(gateInput[2 * h + n] + reset * gateHidden[2 * h + n]);
            hidden[n] = (1.0f - update) * candidate + update * hidden[n];
        }
        float[] out = new float[this.outputWeights.length];
        for (int i = 0; i < this.outputWeights.length; i++) {
            float acc = this.outputBias[i];
            float[] row = this.outputWeights[i];
            for (int j = 0; j < row.length; j++) {
                acc += row[j] * hidden[j];
            }
            out[i] = acc;
        }
        return out;
    }

    public float[] newHiddenState() {
        return new float[this.hiddenSize];
    }

    public static float sigmoid(float x) {
        return 1.0f / (1.0f + (float) Math.exp(-x));
    }

    public static float boundedTanh(float x) {
        return (float) Math.tanh(x) * 0.95f;
    }

    // === Profile management ===

    public static Path getNeuroDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("kimiko").resolve("neuro");
    }

    public static Path getProfilePath(String name) {
        return getNeuroDir().resolve(name + ".json");
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

    @Nullable
    public static NeuroModel getActive() {
        if (!loaded) {
            loaded = true;
            active = load(getActiveName());
        }
        return active;
    }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        out.add("default");
        try (Stream<Path> stream = Files.list(getNeuroDir())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                    .filter(name -> !out.contains(name))
                    .sorted()
                    .forEach(out::add);
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static boolean profileExists(String name) {
        return "default".equals(name) || Files.isRegularFile(getProfilePath(name));
    }

    public static boolean setActive(String name) {
        try {
            if (!profileExists(name)) {
                return false;
            }
            activeName = name;
            Files.createDirectories(getNeuroDir());
            Files.writeString(getNeuroDir().resolve("active.txt"), name);
        } catch (Throwable t) {
            LOGGER.error("[Neuro] unable to persist active model", t);
        }
        invalidate();
        return true;
    }

    public static void invalidate() {
        loaded = false;
        active = null;
        AuraHumanStyle.invalidate();
    }

    @Nullable
    private static NeuroModel load(String name) {
        try {
            JsonObject json;
            Path profile = getProfilePath(name);
            if (Files.isRegularFile(profile)) {
                json = JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
            } else if ("default".equals(name)) {
                InputStream in = NeuroModel.class.getClassLoader().getResourceAsStream("assets/kimiko/neuro/default.json");
                if (in == null) {
                    return null;
                }
                try (in) {
                    json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } else {
                return null;
            }
            int features = json.get("features").getAsInt();
            if (features < 17 || features > 27 || !json.has("mix")) {
                return null;
            }
            if (!json.has("units") || !"deg".equals(json.get("units").getAsString())) {
                return null;
            }
            return new NeuroModel(json);
        } catch (Throwable t) {
            LOGGER.error("[Neuro] model '{}' unreadable", name, t);
            return null;
        }
    }

    private static float[] parseFloatArray(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static float[][] parseFloatMatrix(JsonArray array) {
        float[][] out = new float[array.size()][];
        for (int i = 0; i < array.size(); i++) {
            out[i] = parseFloatArray(array.get(i).getAsJsonArray());
        }
        return out;
    }
}
