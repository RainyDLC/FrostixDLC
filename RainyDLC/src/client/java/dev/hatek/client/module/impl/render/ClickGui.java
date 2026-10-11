package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;

/**
 * Настройки внешнего вида ClickGUI.
 * Включён — фон панели с эффектом (Liquid Glass или Blur), выключен — обычная панель.
 */
public final class ClickGui extends Module {
    public static final String LIQUID_GLASS = "Liquid Glass";
    public static final String BLUR = "Blur";

    private static ClickGui instance;

    private final ModeSetting style = new ModeSetting("Style", 0, LIQUID_GLASS, BLUR);

    public ClickGui() {
        super("ClickGui", "Controls the look of the GUI: liquid glass or background blur", Category.RENDER);
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
