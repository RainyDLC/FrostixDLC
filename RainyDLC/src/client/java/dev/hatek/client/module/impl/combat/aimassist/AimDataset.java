package dev.hatek.client.module.impl.combat.aimassist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Датасет обучения наводки. Хранится в
 * {@code config/frostix/aimassist/<name>.json} и имеет тот же формат,
 * который понимает {@code scripts/train_aim.py}, так что обучать можно
 * как встроенной Java-нейронкой, так и Python-скриптом.
 */
public final class AimDataset {
    private static final Gson GSON = new GsonBuilder().create();

    private final String name;
    private final List<AimSample> samples = new ArrayList<>();

    public AimDataset(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public synchronized void add(AimSample sample) {
        this.samples.add(sample);
    }

    public synchronized int size() {
        return this.samples.size();
    }

    public synchronized List<AimSample> copy() {
        return new ArrayList<>(this.samples);
    }

    public synchronized void clear() {
        this.samples.clear();
    }

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("frostix").resolve("aimassist");
    }

    public static Path fileOf(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_\\- ]", "_").trim();
        if (safe.isEmpty()) {
            safe = "default";
        }
        return dir().resolve(safe + ".json");
    }

    public synchronized void save() throws IOException {
        Path dir = dir();
        Files.createDirectories(dir);
        JsonObject root = new JsonObject();
        root.addProperty("name", this.name);
        root.addProperty("input_size", AimBrain.INPUT_SIZE);
        root.addProperty("output_size", AimBrain.OUTPUT_SIZE);
        JsonArray ins = new JsonArray();
        JsonArray outs = new JsonArray();
        for (AimSample s : this.samples) {
            JsonArray in = new JsonArray();
            for (float v : s.input) {
                in.add(v);
            }
            JsonArray out = new JsonArray();
            for (float v : s.output) {
                out.add(v);
            }
            ins.add(in);
            outs.add(out);
        }
        root.add("inputs", ins);
        root.add("outputs", outs);
        Files.writeString(fileOf(this.name), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    public static AimDataset load(String name) throws IOException {
        Path file = fileOf(name);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        AimDataset dataset = new AimDataset(root.has("name")
                ? root.get("name").getAsString() : name);
        JsonArray ins = root.getAsJsonArray("inputs");
        JsonArray outs = root.getAsJsonArray("outputs");
        for (int i = 0; i < ins.size() && i < outs.size(); i++) {
            JsonArray in = ins.get(i).getAsJsonArray();
            JsonArray out = outs.get(i).getAsJsonArray();
            float[] input = new float[in.size()];
            float[] output = new float[out.size()];
            for (int j = 0; j < input.length; j++) {
                input[j] = in.get(j).getAsFloat();
            }
            for (int j = 0; j < output.length; j++) {
                output[j] = out.get(j).getAsFloat();
            }
            dataset.samples.add(new AimSample(input, output));
        }
        return dataset;
    }

    /** Имена всех сохранённых датасетов (без расширения). */
    public static List<String> listSaved() {
        List<String> names = new ArrayList<>();
        try {
            Path dir = dir();
            if (!Files.isDirectory(dir)) {
                return names;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> !p.getFileName().toString().endsWith(".weights.json"))
                        .forEach(p -> {
                            String n = p.getFileName().toString();
                            names.add(n.substring(0, n.length() - 5));
                        });
            }
        } catch (IOException ignored) {
        }
        return names;
    }
}
