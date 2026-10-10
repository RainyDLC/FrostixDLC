package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;

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

    public Interface() {
        super("Interface", "Ватермарка клиента, кейбинды, активные эффекты и кастомный хотбар",
                Category.RENDER);
        instance = this;
        with(this.watermark,
                this.watermarkPos,
                this.keybinds,
                this.effects,
                this.hotbar,
                this.hideVanilla);
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
}
