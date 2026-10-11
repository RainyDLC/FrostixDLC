package dev.hatek.client.module.impl.render;

import dev.hatek.client.cosmetic.SwordModel;
import dev.hatek.client.cosmetic.Swords;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Косметика: кастомные 3D-мечи. Бинд функции открывает колесо косметики,
 * переключатель включает/выключает показ косметики.
 */
public final class Cosmetics extends Module {
    private static Cosmetics instance;

    private final ModeSetting sword = new ModeSetting("Sword", 0, Swords.names());

    public Cosmetics() {
        super("Cosmetics", "Custom 3D swords. Press the bind to open the cosmetics wheel", Category.RENDER);
        instance = this;
        keybind(GLFW.GLFW_KEY_B);
        with(this.sword);
    }

    public static Cosmetics instance() {
        return instance;
    }

    /** Выбранный меч или null, если косметика выключена / выбран обычный меч. */
    public static SwordModel selectedSword() {
        if (instance == null || !instance.isEnabled()) {
            return null;
        }
        return Swords.byName(instance.sword.value());
    }

    public static String selectedName() {
        return instance == null ? Swords.DEFAULT : instance.sword.value();
    }

    public static void select(String name) {
        if (instance != null) {
            instance.sword.value(name);
        }
    }
}
