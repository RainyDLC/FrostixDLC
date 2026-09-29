package rtx.kimiko.api.ui.mainmenu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-adjustable positions are fractions of the usable screen, not pixel coordinates. */
public final class MenuLayout {
    private static final Gson GSON = new Gson();
    private static final float[][] DEFAULTS = {
        {0.050f, 0.50f}, {0.050f, 0.60f}, {0.050f, 0.70f},
        {0.050f, 0.80f}, {0.050f, 0.90f}
    };
    private static final float[][] positions = new float[DEFAULTS.length][2];
    private static boolean loaded;

    private MenuLayout() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rainydlc-menu-layout.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        reset(false);
        try {
            if (!Files.isRegularFile(file())) return;
            JsonObject root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (int i = 0; i < positions.length; i++) {
                if (!root.has("button" + i)) continue;
                JsonObject value = root.getAsJsonObject("button" + i);
                positions[i][0] = clamp(value.get("x").getAsFloat());
                positions[i][1] = clamp(value.get("y").getAsFloat());
            }
        } catch (Exception ignored) { reset(false); }
    }

    public static float x(int index) { load(); return positions[index][0]; }
    public static float y(int index) { load(); return positions[index][1]; }
    public static void move(int index, float x, float y) {
        load();
        positions[index][0] = clamp(x);
        positions[index][1] = clamp(y);
    }
    public static void reset() { load(); reset(true); }
    private static void reset(boolean save) {
        for (int i = 0; i < positions.length; i++) {
            positions[i][0] = DEFAULTS[i][0];
            positions[i][1] = DEFAULTS[i][1];
        }
        if (save) save();
    }
    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
    public static void save() {
        load();
        JsonObject root = new JsonObject();
        for (int i = 0; i < positions.length; i++) {
            JsonObject value = new JsonObject();
            value.addProperty("x", positions[i][0]);
            value.addProperty("y", positions[i][1]);
            root.add("button" + i, value);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }
}
