package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.awt.Color;

/** Skin-preserving player chams, shared by the world model and first-person arm. */
public final class Chams extends Module {
    private static Chams instance;

    private final ModeSetting depth = new ModeSetting("Depth", 0, "Through walls", "Visible only");
    private final ColorSetting color = new ColorSetting("Color", 0xD842CFFF);
    private final BoolSetting pulse = new BoolSetting("Pulse", true);
    private final SliderSetting pulseSpeed = new SliderSetting("Pulse speed", 1.0, 0.1, 3.0, 0.1);
    private final BoolSetting firstPersonArm = new BoolSetting("First person arm", true);

    public Chams() {
        super("Chams", "Detailed player skin highlight through walls with animated color and first-person arm support", Category.RENDER);
        instance = this;
        with(this.depth, this.color, this.pulse, this.pulseSpeed, this.firstPersonArm);
    }

    public static boolean activeFor(AvatarRenderState state) {
        Chams module = instance;
        Minecraft mc = Minecraft.getInstance();
        return module != null && module.isEnabled() && mc.level != null && state != null;
    }

    public static boolean throughWalls() {
        return instance == null || instance.depth.is("Through walls");
    }

    public static boolean firstPersonArm() {
        return instance != null && instance.isEnabled() && instance.firstPersonArm.value();
    }

    public static int tint() {
        Chams module = instance;
        if (module == null) {
            return 0xFFFFFFFF;
        }
        int configured = module.color.value();
        if (!module.pulse.value()) {
            return configured;
        }

        float wave = 0.72f + 0.28f * (float) Math.sin(System.currentTimeMillis()
                * 0.0035 * module.pulseSpeed.value());
        int alpha = (configured >>> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB((configured >>> 16) & 0xFF,
                (configured >>> 8) & 0xFF, configured & 0xFF, null);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], Mth.clamp(hsb[2] * wave, 0.0f, 1.0f));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
