package rtx.kimiko.api.modules.impl.Visuals.emotions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;

/**
 * Реестр пользовательских эмоций: хранение в памяти и сохранение
 * в kimiko/custom_emotions.json внутри папки запуска.
 */
public final class CustomEmotionStore {
    private static final CustomEmotionStore INSTANCE = new CustomEmotionStore();

    private final List<CustomEmotion> emotions = new ArrayList<>();
    private boolean loaded;

    private CustomEmotionStore() {
    }

    public static CustomEmotionStore get() {
        return INSTANCE;
    }

    public synchronized List<CustomEmotion> all() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(this.emotions));
    }

    /** Добавить новую или заменить существующую с тем же именем. */
    public synchronized void addOrReplace(CustomEmotion emotion) {
        ensureLoaded();
        this.emotions.removeIf(e -> e.displayName().equalsIgnoreCase(emotion.displayName()));
        this.emotions.add(emotion);
        save();
    }

    public synchronized boolean remove(CustomEmotion emotion) {
        ensureLoaded();
        boolean removed = this.emotions.remove(emotion);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized CustomEmotion byName(String name) {
        ensureLoaded();
        if (name == null) {
            return null;
        }
        for (CustomEmotion e : this.emotions) {
            if (e.displayName().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    public synchronized void reload() {
        this.loaded = false;
        ensureLoaded();
    }

    private void ensureLoaded() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        this.emotions.clear();
        try {
            File file = file();
            if (file != null && file.isFile()) {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonArray array = JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement e : array) {
                    try {
                        this.emotions.add(CustomEmotion.fromJson(e.toString()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        try {
            File file = file();
            if (file == null) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            JsonArray array = new JsonArray();
            for (CustomEmotion e : this.emotions) {
                try {
                    array.add(JsonParser.parseString(e.toJson()));
                } catch (Exception ignored) {
                }
            }
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    sb.append(",\n");
                }
                sb.append("  ").append(array.get(i).toString());
            }
            sb.append("\n]");
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static File file() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.runDirectory == null) {
                return null;
            }
            return new File(mc.runDirectory, "kimiko/custom_emotions.json");
        } catch (Exception e) {
            return null;
        }
    }
}
