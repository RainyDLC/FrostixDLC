package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;

/**
 * Настройки внешнего вида ClickGUI.
 * Solid — обычная панель, Blur — размытие мира за полупрозрачной панелью,
 * Liquid Glass — размытие + тонкое стекло с кромкой, преломлением и бликом.
 */
public final class ClickGui extends Module {
    public static final String SOLID = "Solid";
    public static final String BLUR = "Blur";
    public static final String LIQUID_GLASS = "Liquid Glass";

    private static ClickGui instance;

    private final ModeSetting style = new ModeSetting("Style", 0, SOLID, BLUR, LIQUID_GLASS);

    public ClickGui() {
        super("ClickGui", "GUI background: solid, blur or liquid glass", Category.RENDER);
        instance = this;
        with(this.style);
    }

    public static ClickGui instance() {
        return instance;
    }

    public static boolean liquidGlass() {
        return instance != null && instance.isEnabled() && instance.style.is(LIQUID_GLASS);
    }

    public static boolean blur() {
        return instance != null && instance.isEnabled() && instance.style.is(BLUR);
    }
}
