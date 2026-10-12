package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;

/**
 * Настройки внешнего вида ClickGUI.
 * Solid — обычная панель, Blur — размытие мира за полупрозрачной панелью.
 */
public final class ClickGui extends Module {
    public static final String SOLID = "Solid";
    public static final String BLUR = "Blur";

    private static ClickGui instance;

    private final ModeSetting style = new ModeSetting("Style", 0, SOLID, BLUR);

    public ClickGui() {
        super("ClickGui", "GUI background: solid or blurred", Category.RENDER);
        instance = this;
        with(this.style);
    }

    public static ClickGui instance() {
        return instance;
    }

    public static boolean blur() {
        return instance != null && instance.isEnabled() && instance.style.is(BLUR);
    }
}
