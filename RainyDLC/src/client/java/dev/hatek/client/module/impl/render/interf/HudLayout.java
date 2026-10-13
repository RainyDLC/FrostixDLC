package dev.hatek.client.module.impl.render.interf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Свободное расположение элементов HUD модуля Interface.
 * Для каждого элемента хранятся позиция (левый верхний угол,
 * в GUI-пикселях) и масштаб. Позиция null = якорь по умолчанию.
 * Персист: config/hatek/hud.json.
 */
public final class HudLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float MIN_SCALE = 0.25f;
    private static final float MAX_SCALE = 4.0f;

    public enum Element {
        WATERMARK("Watermark"),
        KEYBINDS("Hotkeys"),
        HOTBAR("Hotbar"),
        ARMOR("Armor"),
        STATS("Stats"),
        ARRAYLIST("Active Modules"),
        TARGET("Target"),
        NOTIFICATIONS("Notifications"),
        EFFECTS("Effects");

        private final String label;

        Element(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public static final class Entry {
        /** null = использовать якорь по умолчанию. */
        public Float x;
        public Float y;
        public float scale = 1.0f;

        public boolean positioned() {
            return this.x != null && this.y != null;
        }
    }

    private static final Map<Element, Entry> ENTRIES = new EnumMap<>(Element.class);
    private static boolean loaded;

    static {
        for (Element el : Element.values()) {
            ENTRIES.put(el, new Entry());
        }
    }

    private HudLayout() {
    }

    public static Entry get(Element el) {
        load();
        return ENTRIES.get(el);
    }

    public static void setPos(Element el, float x, float y) {
        load();
        Entry e = ENTRIES.get(el);
        e.x = x;
        e.y = y;
    }

    /** Сбросить сохранённую позицию — снова используется якорь. */
    public static void clearPos(Element el) {
        load();
        Entry e = ENTRIES.get(el);
        if (e.x != null || e.y != null) {
            e.x = null;
            e.y = null;
            save();
        }
    }

    public static void setScale(Element el, float scale) {
        load();
        Entry e = ENTRIES.get(el);
        e.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<Element, Entry> kv : ENTRIES.entrySet()) {
                JsonObject o = new JsonObject();
                Entry e = kv.getValue();
                if (e.x != null) {
                    o.addProperty("x", e.x);
                }
                if (e.y != null) {
                    o.addProperty("y", e.y);
                }
                o.addProperty("scale", e.scale);
                root.add(kv.getKey().name(), o);
            }
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) {
                return;
            }
            JsonObject root = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Element el : Element.values()) {
                if (!root.has(el.name())) {
                    continue;
                }
                JsonObject o = root.getAsJsonObject(el.name());
                Entry e = ENTRIES.get(el);
                if (o.has("x")) {
                    e.x = o.get("x").getAsFloat();
                }
                if (o.has("y")) {
                    e.y = o.get("y").getAsFloat();
                }
                if (o.has("scale")) {
                    e.scale = Math.max(MIN_SCALE,
                            Math.min(MAX_SCALE, o.get("scale").getAsFloat()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("hatek").resolve("hud.json");
    }
}
