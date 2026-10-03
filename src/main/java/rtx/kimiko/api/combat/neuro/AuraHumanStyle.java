package rtx.kimiko.api.combat.neuro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AuraHumanStyle {
    private final float slack;
    private final float temperature;
    private final float speed;
    private static AuraHumanStyle instance;

    private AuraHumanStyle(float slack, float temperature, float speed) {
        this.slack = clamp(slack, 0.3f, 0.65f);
        this.temperature = clamp(temperature, 0.62f, 1.0f);
        this.speed = clamp(speed, 0.9f, 1.4f);
    }

    public static AuraHumanStyle getInstance() {
        if (instance == null) {
            instance = loadOrCreate();
        }
        return instance;
    }

    public static void invalidate() {
        instance = null;
    }

    private static AuraHumanStyle loadOrCreate() {
        Path path = NeuroModel.getNeuroDir().resolve("style.json");
        NeuroModel active = NeuroModel.getActive();
        float defSlack = active == null ? 0.45f : active.getSlack();
        float defTemp = active == null ? 0.7f : active.getTemperature();
        float defSpeed = active == null ? 1.1f : active.getSpeed();
        String center = String.format(Locale.ROOT, "%.3f/%.3f/%.3f", defSlack, defTemp, defSpeed);

        try {
            if (Files.isRegularFile(path)) {
                JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (obj.has("center") && center.equals(obj.get("center").getAsString())) {
                    return new AuraHumanStyle(
                            obj.get("slack").getAsFloat(),
                            obj.get("temperature").getAsFloat(),
                            obj.get("speed").getAsFloat()
                    );
                }
            }
        } catch (Exception ignored) {
        }

        AuraHumanStyle generated = new AuraHumanStyle(jitter(defSlack), jitter(defTemp), jitter(defSpeed));
        save(path, generated, center);
        return generated;
    }

    private static void save(Path path, AuraHumanStyle style, String center) {
        try {
            Files.createDirectories(path.getParent());
            String json = String.format(Locale.ROOT,
                    "{\"slack\": %.3f, \"temperature\": %.3f, \"speed\": %.3f, \"center\": \"%s\"}",
                    style.slack, style.temperature, style.speed, center);
            Files.writeString(path, json);
        } catch (Exception ignored) {
        }
    }

    private static float jitter(float val) {
        return val * (1.0f + 0.1f * (2.0f * ThreadLocalRandom.current().nextFloat() - 1.0f));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
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

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "slack %.0f%%, temperature %.2f, speed %.2f",
                100.0f * (1.0f - this.slack), this.temperature, this.speed);
    }
}
