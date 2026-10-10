package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;

/**
 * Модуль "Interface": HUD клиента.
 * Ватермарка, кейбинды, активные эффекты и кастомный хотбар.
 * Позиция ватермарки (слева / по центру / справа) выбирается
 * правым кликом по самой ватермарке.
 */
public final class Interface extends Module {
    private static Interface instance;

    private final BoolSetting watermark = new BoolSetting("Watermark", true);
    private final ModeSetting watermarkPos = new ModeSetting("Watermark Position", 0,
            "Left", "Center", "Right");
    private final BoolSetting keybinds = new BoolSetting("Keybinds", true);
    private final BoolSetting effects = new BoolSetting("Effects", true);
    private final BoolSetting hotbar = new BoolSetting("Custom Hotbar", true);
    private final BoolSetting hideVanilla = new BoolSetting("Hide Vanilla Hotbar", true);
    private final BoolSetting media2d = new BoolSetting("Media 2D", false);
    private final TextSetting mediaFile = new TextSetting("Media File", "logo.gif", "logo.gif");
    private final SliderSetting mediaSize = new SliderSetting("Media Size", 64, 16, 256, 8);
    private final SliderSetting mediaX = new SliderSetting("Media X", 50, 0, 100, 1);
    private final SliderSetting mediaY = new SliderSetting("Media Y", 50, 0, 100, 1);
    private final BoolSetting media3d = new BoolSetting("Media 3D", false);
    private final TextSetting media3dFile = new TextSetting("Media 3D File", "logo.gif", "logo.gif");
    private final SliderSetting media3dSize = new SliderSetting("Media 3D Size", 1.0, 0.25, 4.0, 0.25);
    private final SliderSetting media3dHeight = new SliderSetting("Media 3D Height", 2.5, 0.5, 6.0, 0.25);

    public Interface() {
        super("Interface", "Ватермарка клиента, кейбинды, активные эффекты и кастомный хотбар",
                Category.RENDER);
        instance = this;
        with(this.watermark,
                this.watermarkPos,
                this.keybinds,
                this.effects,
                this.hotbar,
                this.hideVanilla,
                this.media2d,
                this.mediaFile,
                this.mediaSize,
                this.mediaX,
                this.mediaY,
                this.media3d,
                this.media3dFile,
                this.media3dSize,
                this.media3dHeight);
        InterfaceHud.init();
    }

    public static Interface instance() {
        return instance;
    }

    public static boolean showWatermark() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.watermark.value();
    }

    /** "Left", "Center" или "Right". */
    public static String watermarkPos() {
        Interface module = instance;
        return module == null ? "Left" : module.watermarkPos.value();
    }

    public void setWatermarkPos(String pos) {
        this.watermarkPos.value(pos);
    }

    public static boolean showKeybinds() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.keybinds.value();
    }

    public static boolean showEffects() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.effects.value();
    }

    public static boolean showHotbar() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.hotbar.value();
    }

    public static boolean hideVanillaHotbar() {
        Interface module = instance;
        return module != null && module.isEnabled()
                && module.hotbar.value() && module.hideVanilla.value();
    }

    public static boolean showMedia2D() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.media2d.value();
    }

    public static String mediaFile() {
        Interface module = instance;
        return module == null ? "" : module.mediaFile.value();
    }

    public static double mediaSize() {
        Interface module = instance;
        return module == null ? 64 : module.mediaSize.value();
    }

    public static double mediaX() {
        Interface module = instance;
        return module == null ? 50 : module.mediaX.value();
    }

    public static double mediaY() {
        Interface module = instance;
        return module == null ? 50 : module.mediaY.value();
    }

    public static boolean showMedia3D() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.media3d.value();
    }

    public static String media3dFile() {
        Interface module = instance;
        return module == null ? "" : module.media3dFile.value();
    }

    public static double media3dSize() {
        Interface module = instance;
        return module == null ? 1.0 : module.media3dSize.value();
    }

    public static double media3dHeight() {
        Interface module = instance;
        return module == null ? 2.5 : module.media3dHeight.value();
    }
}
