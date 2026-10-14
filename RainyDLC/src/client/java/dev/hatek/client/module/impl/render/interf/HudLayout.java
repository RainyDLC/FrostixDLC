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
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    public enum Element {
        WATERMARK("Watermark"),
        KEYBINDS("Keybinds"),
        EFFECTS("Effects"),
        HOTBAR("Hotbar"),
        ARMOR("Armor"),
        TARGET("Target"),
        ARRAYLIST("ArrayList"), STAFF("StaffList"), COOLDOWNS("CoolDowns"),
        NOTIFICATIONS("Notifications"), TICKS("Ticks"), PING("Ping"), FPS("FPS"),
        BPS("BPS"), XYZ("XYZ"), TOTEMS("TotemCounter");

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
        public float opacity = 1.0f;
        public float glow = 1.0f;

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
        if (Float.isFinite(x) && Float.isFinite(y)) { e.x = x; e.y = y; }
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
        e.scale = clampFinite(scale, MIN_SCALE, MAX_SCALE, 1);
    }

    public static float clampFinite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max,value)) : fallback;
    }
    public static void setAppearance(Element el, float opacity, float glow) {
        Entry e=get(el); e.opacity=clampFinite(opacity,0.1f,1,1); e.glow=clampFinite(glow,0,2,1);
    }
    public static void resetAppearance(Element el) {
        Entry e=get(el); e.scale=1; e.opacity=1; e.glow=1; save();
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
                o.addProperty("opacity", e.opacity);
                o.addProperty("glow", e.glow);
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
                if (el == Element.KEYBINDS && !root.has(el.name()) && root.has("HOTKEYS")) root.add(el.name(),root.get("HOTKEYS"));
                if (!root.has(el.name())) {
                    continue;
                }
                JsonObject o = root.getAsJsonObject(el.name());
                Entry e = ENTRIES.get(el);
                if (o.has("x")) {
                    float x=o.get("x").getAsFloat(); if(Float.isFinite(x)) e.x=x;
                }
                if (o.has("y")) {
                    float y=o.get("y").getAsFloat(); if(Float.isFinite(y)) e.y=y;
                }
                if (o.has("scale")) {
                    e.scale=clampFinite(o.get("scale").getAsFloat(),MIN_SCALE,MAX_SCALE,1);
                }
                if(o.has("opacity")) e.opacity=clampFinite(o.get("opacity").getAsFloat(),0.1f,1,1);
                if(o.has("glow")) e.glow=clampFinite(o.get("glow").getAsFloat(),0,2,1);
            }
        } catch (Exception ignored) {
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("hatek").resolve("hud.json");
    }
}
