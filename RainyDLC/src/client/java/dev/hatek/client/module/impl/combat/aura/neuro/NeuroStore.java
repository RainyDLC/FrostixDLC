package dev.hatek.client.module.impl.combat.aura.neuro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hatek.client.module.impl.combat.aimassist.AimNet;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Хранилище датасетов и весов нейро-ротаций AttackAura:
 * {@code config/frostix/neuroaura/<имя>.json} и {@code <имя>.weights.json}.
 *
 * <p>Форматы те же, что у AimAssist ({@code aimdataset-v2} /
 * {@code aimnet-v2}), поэтому обучение выполняет общий Python-скрипт
 * {@code train_neuro.py} (numpy, Adam) — всё локально, в сеть ничего
 * не уходит.
 */
public final class NeuroStore {
    public static final String FORMAT_DATASET = "aimdataset-v2";

    private static final Gson GSON = new GsonBuilder().create();

    /** Короткая сводка о сохранённом профиле для панели обучения. */
    public static final class ProfileInfo {
        public final String name;
        public final int samples;
        public final boolean trained;

        public ProfileInfo(String name, int samples, boolean trained) {
            this.name = name;
            this.samples = samples;
            this.trained = trained;
        }
    }

    private NeuroStore() {
    }

    public static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("frostix").resolve("neuroaura");
    }

    public static String safeName(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_\\- ]", "_").trim();
        return s.isEmpty() ? "default" : s;
    }

    public static Path datasetPath(String name) {
        return dir().resolve(safeName(name) + ".json");
    }

    public static Path weightsPath(String name) {
        return dir().resolve(safeName(name) + ".weights.json");
    }

    public static Path scriptPath() {
        return dir().resolve("train_neuro.py");
    }

    public static void saveDataset(NeuroDataset dataset) throws IOException {
        Files.createDirectories(dir());
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_DATASET);
        root.addProperty("name", dataset.name());
        root.addProperty("input_size", AimNet.IN);
        root.addProperty("output_size", AimNet.OUT);
        JsonArray ins = new JsonArray();
        JsonArray outs = new JsonArray();
        for (NeuroSample s : dataset.copy()) {
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
        JsonArray hps = new JsonArray();
        for (float[] hp : dataset.hitPoints()) {
            JsonArray p = new JsonArray();
            for (float v : hp) {
                p.add(v);
            }
            hps.add(p);
        }
        root.add("hit_points", hps);
        Files.writeString(datasetPath(dataset.name()), GSON.toJson(root),
                StandardCharsets.UTF_8);
    }

    /** Все сохранённые профили: имя, число сэмплов, обучена ли нейронка. */
    public static List<ProfileInfo> listProfiles() {
        List<ProfileInfo> out = new ArrayList<>();
        try {
            Path dir = dir();
            if (!Files.isDirectory(dir)) {
                return out;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> !p.getFileName().toString().endsWith(".weights.json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> {
                            try {
                                String json = Files.readString(p, StandardCharsets.UTF_8);
                                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                                String fileName = p.getFileName().toString();
                                String name = root.has("name")
                                        ? root.get("name").getAsString()
                                        : fileName.substring(0, fileName.length() - 5);
                                int samples = root.has("inputs")
                                        ? root.getAsJsonArray("inputs").size() : 0;
                                boolean trained = Files.isRegularFile(weightsPath(name));
                                out.add(new ProfileInfo(name, samples, trained));
                            } catch (Exception ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Удалить датасет и веса профиля. */
    public static void deleteProfile(String name) {
        try {
            Files.deleteIfExists(datasetPath(name));
            Files.deleteIfExists(weightsPath(name));
            if (name.equals(loadActiveProfile())) {
                saveActiveProfile("");
            }
        } catch (IOException ignored) {
        }
    }

    public static void saveWeights(String profile, AimNet net) throws IOException {
        net.save(weightsPath(profile));
    }

    public static AimNet loadWeights(String name) throws IOException {
        return AimNet.load(weightsPath(name));
    }

    public static boolean hasWeights(String name) {
        return Files.isRegularFile(weightsPath(name));
    }

    public static void ensureDefaultWeights() {
        try {
            Path defaultPath = weightsPath("default");
            if (!Files.isRegularFile(defaultPath)) {
                try (var in = NeuroStore.class.getResourceAsStream("/neuro/default.weights.json")) {
                    if (in != null) {
                        Files.createDirectories(defaultPath.getParent());
                        Files.copy(in, defaultPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static String loadActiveProfile() {
        try {
            Path file = dir().resolve("active_profile.txt");
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    public static void saveActiveProfile(String name) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("active_profile.txt"),
                    name == null ? "" : name, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
