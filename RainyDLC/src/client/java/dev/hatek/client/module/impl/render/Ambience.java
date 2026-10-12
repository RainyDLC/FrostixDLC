package dev.hatek.client.module.impl.render;

import dev.hatek.client.ambience.AmbienceSky;
import dev.hatek.client.ambience.SkyGen;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.dimension.DimensionType;

public final class Ambience extends Module {
    private static Ambience instance;

    private final ModeSetting sky = new ModeSetting("Sky", 3, "Plasma", "Space", "Milky Way", "Black Hole", "Shader");
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.0, 3.0, 0.1);
    private final SliderSetting brightness = new SliderSetting("Brightness", 1.0, 0.3, 2.0, 0.05);
    private final BoolSetting stars = new BoolSetting("Stars", true);
    private final BoolSetting sunMoon = new BoolSetting("Sun & Moon", false);
    private final BoolSetting darkFog = new BoolSetting("Dark Fog", true);
    private final BoolSetting clouds = new BoolSetting("Clouds", true);
    private final BoolSetting skyShader = new BoolSetting("Sky Shader", true);
    private final SliderSetting bloom = new SliderSetting("Bloom", 1.0, 0.0, 2.0, 0.05);
    private final SliderSetting saturation = new SliderSetting("Saturation", 1.15, 0.5, 1.8, 0.05);
    private final BoolSetting lensFlare = new BoolSetting("Lens Flare", true);

    public Ambience() {
        super("Ambience", "Shader-like animated skies: plasma, deep space, milky way, a realistic black hole and a shaderpack-style atmosphere", Category.RENDER);
        instance = this;
        with(this.sky, this.speed, this.brightness, this.stars, this.sunMoon, this.darkFog, this.clouds,
                this.skyShader, this.bloom, this.saturation, this.lensFlare);
    }

    @Override
    protected void onDisable() {
        AmbienceSky.release();
    }

    /** Модуль включён и мы в измерении с обычным (верхним) небом. */
    public static boolean active() {
        Ambience m = instance;
        if (m == null || !m.isEnabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
    }

    public static int mode() {
        Ambience m = instance;
        if (m == null) {
            return SkyGen.MODE_BLACK_HOLE;
        }
        return switch (m.sky.value()) {
            case "Plasma" -> SkyGen.MODE_PLASMA;
            case "Space" -> SkyGen.MODE_SPACE;
            case "Milky Way" -> SkyGen.MODE_MILKY_WAY;
            case "Shader" -> SkyGen.MODE_SHADER;
            default -> SkyGen.MODE_BLACK_HOLE;
        };
    }

    public static double speed() {
        return instance == null ? 1.0 : instance.speed.value();
    }

    public static double brightness() {
        return instance == null ? 1.0 : instance.brightness.value();
    }

    public static boolean showStars() {
        return instance == null || instance.stars.value();
    }

    public static boolean hideSunMoon() {
        return active() && !instance.sunMoon.value();
    }

    /** В режиме Shader ванильное солнце заменяется своим. */
    public static boolean hideSun() {
        return active() && (!instance.sunMoon.value() || mode() == SkyGen.MODE_SHADER);
    }

    /** Пост-обработка неба (bloom + цветокоррекция + дизеринг). */
    public static boolean skyShader() {
        return instance != null && instance.skyShader.value();
    }

    public static double bloom() {
        return skyShader() ? instance.bloom.value() : 0.0;
    }

    public static double saturation() {
        return skyShader() ? instance.saturation.value() : 1.0;
    }

    public static boolean lensFlare() {
        return skyShader() && instance.lensFlare.value() && instance.bloom.value() > 0.01;
    }

    /** Ключ параметров пост-обработки: при смене текстуры пересчитываются. */
    public static int postKey() {
        if (!skyShader()) {
            return -1;
        }
        return (int) Math.round(instance.bloom.value() * 100) * 1000 + (int) Math.round(instance.saturation.value() * 100);
    }

    public static boolean hideClouds() {
        Ambience m = instance;
        return m != null && m.isEnabled() && !m.clouds.value();
    }

    public static boolean darkFog() {
        return active() && instance.darkFog.value();
    }
}
