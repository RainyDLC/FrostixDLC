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

    private final ModeSetting sky = new ModeSetting("Sky", 3, "Plasma", "Space", "Milky Way", "Black Hole");
    private final SliderSetting speed = new SliderSetting("Speed", 1.0, 0.0, 3.0, 0.1);
    private final SliderSetting brightness = new SliderSetting("Brightness", 1.0, 0.3, 2.0, 0.05);
    private final BoolSetting stars = new BoolSetting("Stars", true);
    private final BoolSetting sunMoon = new BoolSetting("Sun & Moon", false);
    private final BoolSetting darkFog = new BoolSetting("Dark Fog", true);

    public Ambience() {
        super("Ambience", "Shader-like animated skies: plasma, deep space, milky way and a realistic black hole", Category.RENDER);
        instance = this;
        with(this.sky, this.speed, this.brightness, this.stars, this.sunMoon, this.darkFog);
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

    public static boolean darkFog() {
        return active() && instance.darkFog.value();
    }
}
