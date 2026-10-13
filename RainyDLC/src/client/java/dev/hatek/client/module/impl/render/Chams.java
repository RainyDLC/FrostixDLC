package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.ChamsUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;

/** Skin-preserving procedural materials for world players and the first-person arm. */
public final class Chams extends Module {
    private static Chams instance;

    private final ModeSetting depth = new ModeSetting("Depth", 0, "Through walls", "Visible only");
    private final ModeSetting shader = new ModeSetting("Shader", 0, "Aurora", "Prism", "Hologram", "Nebula", "Classic");
    private final ColorSetting color = new ColorSetting("Color", 0xD842CFFF);
    private final ColorSetting secondary = new ColorSetting("Secondary color", 0xFFB478FF);
    private final SliderSetting strength = new SliderSetting("Effect strength", 0.65, 0, 1, 0.05);
    private final SliderSetting rim = new SliderSetting("Rim light", 0.7, 0, 1, 0.05);
    private final SliderSetting skinDetail = new SliderSetting("Skin detail", 0.4, 0, 1, 0.05);
    private final SliderSetting patternScale = new SliderSetting("Pattern scale", 1.0, 0.25, 3.0, 0.05);
    private final SliderSetting animationSpeed = new SliderSetting("Animation speed", 1.0, 0, 3.0, 0.1);
    // Retain the existing setting names for configuration compatibility.
    private final BoolSetting pulse = new BoolSetting("Pulse", true);
    private final SliderSetting pulseSpeed = new SliderSetting("Pulse speed", 1.0, 0.1, 3.0, 0.1);
    private final BoolSetting firstPersonArm = new BoolSetting("First person arm", true);
    private long lastAnimationNanos;
    private double animationTime;

    public Chams() {
        super("Chams", "Animated Aurora, Prism, Hologram and Nebula materials with skin detail and rim lighting", Category.RENDER);
        instance = this;
        with(this.depth, this.shader, this.color, this.secondary, this.strength, this.rim,
                this.skinDetail, this.patternScale, this.animationSpeed,
                this.pulse, this.pulseSpeed, this.firstPersonArm);
    }

    @Override
    protected void onEnable() {
        lastAnimationNanos = System.nanoTime();
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

    /** Evaluated at draw preparation, not cached into a RenderType or recompiled on slider changes. */
    public static Matrix4f shaderParameters() {
        Chams module = instance;
        if (module == null) return ChamsUniforms.pack(0xD842CFFF, 0xFFB478FF, 0,
                ChamsUniforms.AURORA, 0.65f, 1, 0.4f, 0.7f, 1);
        long now = System.nanoTime();
        if (module.lastAnimationNanos != 0) {
            double dt = Math.max(0, Math.min(0.25, (now - module.lastAnimationNanos) / 1_000_000_000.0));
            module.animationTime += dt * module.animationSpeed.value();
        }
        module.lastAnimationNanos = now;
        float time = (float) (module.animationTime % 86400.0);
        int preset = switch (module.shader.value()) {
            case "Prism" -> ChamsUniforms.PRISM;
            case "Hologram" -> ChamsUniforms.HOLOGRAM;
            case "Nebula" -> ChamsUniforms.NEBULA;
            case "Classic" -> ChamsUniforms.CLASSIC;
            default -> ChamsUniforms.AURORA;
        };
        float wave = module.pulse.value()
                ? 0.88f + 0.12f * (float) Math.sin(time * 3.5 * module.pulseSpeed.value()) : 1;
        return ChamsUniforms.pack(module.color.value(), module.secondary.value(), time, preset,
                (float) module.strength.value(), (float) module.patternScale.value(),
                (float) module.skinDetail.value(), (float) module.rim.value(), wave);
    }
}
