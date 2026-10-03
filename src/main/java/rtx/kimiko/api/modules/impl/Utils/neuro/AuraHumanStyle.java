package rtx.kimiko.api.modules.impl.Utils.neuro;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-player "handwriting": small persistent jitter around the model's
 * slack/temperature/speed so every install aims slightly differently.
 * Ported from the Rockstar neuro aim style profile.
 */
public final class AuraHumanStyle {

    private static final Logger LOGGER = LoggerFactory.getLogger("Kimiko/Neuro");

    private final float slack;
    private final float temperature;
    private final float speed;

    @Nullable
    private static AuraHumanStyle instance;

    private AuraHumanStyle(float slack, float temperature, float speed) {
        this.slack = clamp(slack, 0.3f, 0.65f);
        this.temperature = clamp(temperature, 0.62f, 1.0f);
        this.speed = clamp(speed, 0.9f, 1.4f);
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

    public static AuraHumanStyle getInstance() {
        if (instance == null) {
            instance = loadOrCreate();
        }
        return instance;
    }

    public static void invalidate() {
        instance = null;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "slack %.0f%%, temperature %.2f, speed %.2f",
                100.0f * (1.0f - this.slack), this.temperature, this.speed);
    }

    private static AuraHumanStyle loadOrCreate() {
        Path path = NeuroModel.getNeuroDir().resolve("style.json");
        NeuroModel model = NeuroModel.getActive();
        float slack = model == null ? 0.45f : model.getSlack();
        float temperature = model == null ? 0.7f : model.getTemperature();
        float speed = model == null ? 1.1f : model.getSpeed();
        String center = String.format(Locale.ROOT, "%.3f/%.3f/%.3f", slack, temperature, speed);
        try {
            if (Files.isRegularFile(path)) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (json.has("center") && center.equals(json.get("center").getAsString())) {
                    return new AuraHumanStyle(
                            json.get("slack").getAsFloat(),
                            json.get("temperature").getAsFloat(),
                            json.get("speed").getAsFloat());
                }
                LOGGER.info("[Neuro] model changed, re-drawing aim style");
            }
        } catch (Exception e) {
            LOGGER.warn("[Neuro] style unreadable, re-drawing: {}", e.getMessage());
        }
        AuraHumanStyle style = new AuraHumanStyle(jitter(slack), jitter(temperature), jitter(speed));
        save(path, style, center);
        LOGGER.info("[Neuro] player aim style: {}", style);
        return style;
    }

    private static void save(Path path, AuraHumanStyle style, String center) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.format(Locale.ROOT,
                    "{\"slack\": %.3f, \"temperature\": %.3f, \"speed\": %.3f, \"center\": \"%s\"}",
                    style.slack, style.temperature, style.speed, center));
        } catch (Exception e) {
            LOGGER.warn("[Neuro] unable to save aim style: {}", e.getMessage());
        }
    }

    private static float jitter(float value) {
        return value * (1.0f + 0.1f * (2.0f * ThreadLocalRandom.current().nextFloat() - 1.0f));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
