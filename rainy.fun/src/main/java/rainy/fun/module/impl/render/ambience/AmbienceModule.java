package rainy.fun.module.impl.render.ambience;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import rainy.fun.module.Module;
import rainy.fun.module.ModuleCategory;
import rainy.fun.module.settings.BooleanSetting;
import rainy.fun.module.settings.ModeSetting;
import rainy.fun.module.settings.NumberSetting;

import java.util.List;

/** Client-side time, weather, light and sky controls adapted from Frostix Ambience. */
public final class AmbienceModule extends Module {
    public static final String TIME_SERVER = "Server";
    public static final String TIME_DAY = "Day";
    public static final String TIME_NOON = "Noon";
    public static final String TIME_SUNSET = "Sunset";
    public static final String TIME_NIGHT = "Night";
    public static final String TIME_MIDNIGHT = "Midnight";
    public static final String TIME_CUSTOM = "Custom";

    public static final String WEATHER_SERVER = "Server";
    public static final String WEATHER_CLEAR = "Clear";
    public static final String WEATHER_RAIN = "Rain";
    public static final String WEATHER_STORM = "Storm";
    public static final String CHROMATIC_SUBTLE = "Subtle";
    public static final String CHROMATIC_MEDIUM = "Medium";
    public static final String CHROMATIC_STRONG = "Strong";

    private static AmbienceModule instance;

    private final ModeSetting timeMode = addSetting(new ModeSetting(
            "Time", TIME_NIGHT, List.of(TIME_SERVER, TIME_DAY, TIME_NOON, TIME_SUNSET,
            TIME_NIGHT, TIME_MIDNIGHT, TIME_CUSTOM)));
    private final NumberSetting customTime = addSetting(new NumberSetting(
            "Custom time", 1000, 0, 24000, 100));
    private final ModeSetting weatherMode = addSetting(new ModeSetting(
            "Weather", WEATHER_CLEAR, List.of(WEATHER_SERVER, WEATHER_CLEAR, WEATHER_RAIN, WEATHER_STORM)));
    private final BooleanSetting fullbright = addSetting(new BooleanSetting("Fullbright", false));
    private final NumberSetting saturation = addSetting(new NumberSetting("Sky saturation", 1.0, 0, 2, 0.05));
    private final NumberSetting brightness = addSetting(new NumberSetting("Lightmap brightness", 0.15, 0, 1, 0.05));
    private final BooleanSetting chromaticAberration = addSetting(new BooleanSetting("Chromatic aberration", false));
    private final ModeSetting chromaticStrength = addSetting(new ModeSetting(
            "Chromatic strength", CHROMATIC_MEDIUM,
            List.of(CHROMATIC_SUBTLE, CHROMATIC_MEDIUM, CHROMATIC_STRONG)));

    public AmbienceModule() {
        super("Ambience", "Local time, weather and atmosphere controls.", ModuleCategory.RENDER);
        instance = this;
    }

    @Override
    protected void onTick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        long time = selectedTime();
        if (time >= 0) level.setTimeFromServer(time);

        switch (weatherMode.getValue()) {
            case WEATHER_CLEAR -> setWeather(level, 0.0f, 0.0f);
            case WEATHER_RAIN -> setWeather(level, 1.0f, 0.0f);
            case WEATHER_STORM -> setWeather(level, 1.0f, 1.0f);
            default -> { }
        }
    }

    private long selectedTime() {
        return switch (timeMode.getValue()) {
            case TIME_DAY -> 1000L;
            case TIME_NOON -> 6000L;
            case TIME_SUNSET -> 12000L;
            case TIME_NIGHT -> 13000L;
            case TIME_MIDNIGHT -> 18000L;
            case TIME_CUSTOM -> Math.round(customTime.getValue());
            default -> -1L;
        };
    }

    private static void setWeather(ClientLevel level, float rain, float thunder) {
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
    }

    public static AmbienceModule getInstance() {
        return instance;
    }

    public boolean isFullbright() {
        return isEnabled() && fullbright.getValue();
    }

    public float getBrightness() {
        return brightness.getValue().floatValue();
    }

    public float getSaturation() {
        return saturation.getValue().floatValue();
    }

    public boolean hasChromaticAberration() {
        return isEnabled() && chromaticAberration.getValue();
    }

    public String getChromaticStrength() {
        return chromaticStrength.getValue();
    }
}
