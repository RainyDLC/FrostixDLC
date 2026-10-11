package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.TextSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Косметика: 3D-мечи из ресурспака Blades of Majestica.
 * Бинд функции открывает колесо косметики, переключатель включает/выключает показ.
 */
public final class Cosmetics extends Module {
    private static Cosmetics instance;

    /** Название меча из пака; пусто — обычный меч. */
    private final TextSetting sword = new TextSetting("Sword", "", "Vanilla");

    public Cosmetics() {
        super("Cosmetics", "3D swords from Blades of Majestica. Press the bind to open the cosmetics wheel",
                Category.RENDER);
        instance = this;
        keybind(GLFW.GLFW_KEY_B);
        with(this.sword);
    }

    public static Cosmetics instance() {
        return instance;
    }

    /** Выбранный меч или null, если косметика выключена / выбран обычный меч. */
    public static String selectedSword() {
        if (instance == null || !instance.isEnabled()) {
            return null;
        }
        String value = instance.sword.value();
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String selectedName() {
        return instance == null ? "" : instance.sword.value();
    }

    public static void select(String name) {
        if (instance != null) {
            instance.sword.value(name == null ? "" : name);
        }
    }
}
