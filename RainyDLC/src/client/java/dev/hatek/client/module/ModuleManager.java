package dev.hatek.client.module;

import dev.hatek.client.ui.Style;
import dev.hatek.client.module.impl.combat.AimAssist;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.AutoMace;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.impl.misc.VoiceControl;
import dev.hatek.client.module.impl.misc.Jarvis;
import dev.hatek.client.module.impl.player.NoFall;
import dev.hatek.client.module.impl.player.NoJumpDelay;
import dev.hatek.client.module.impl.render.ClickGui;
import dev.hatek.client.module.impl.render.Ambience;
import dev.hatek.client.module.impl.render.Chams;
import dev.hatek.client.module.impl.render.KillEffect;
import dev.hatek.client.module.impl.render.Cosmetics;
import dev.hatek.client.module.impl.render.HitParticles;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.NoRender;
import dev.hatek.client.module.impl.render.WorldParticles;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.Setting;
import dev.hatek.client.module.setting.TextSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private static final Map<Module, Boolean> DEFAULT_ENABLED = new IdentityHashMap<>();
    private static final Map<Module, Integer> DEFAULT_KEYS = new IdentityHashMap<>();
    private static final Map<Setting, String> DEFAULT_SETTINGS = new IdentityHashMap<>();

    private ModuleManager() {
    }

    public static List<Module> all() {
        if (MODULES.isEmpty()) {
            register();
        }
        return MODULES;
    }

    public static List<Module> of(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module module : all()) {
            if (module.category() == category) {
                out.add(module);
            }
        }
        return out;
    }

    public static void tickAll() {
        for (Module module : all()) {
            if (module.isEnabled()) {
                module.onClientTick();
            }
        }
    }

    /** Сброс всех модулей, биндов и настроек до заводских (момент регистрации). */
    public static void resetDefaults() {
        for (Module module : all()) {
            Boolean enabled = DEFAULT_ENABLED.get(module);
            if (enabled != null && module.isEnabled() != enabled) {
                module.toggle();
            }
            Integer key = DEFAULT_KEYS.get(module);
            if (key != null) {
                module.keybind(key);
            }
            for (Setting setting : module.settings()) {
                String value = DEFAULT_SETTINGS.get(setting);
                if (value != null) {
                    setting.deserialize(value);
                }
            }
        }
        dev.hatek.client.feature.config.ConfigManager.clearLoaded();
    }

    public static boolean onKey(int key) {
        boolean handled = false;
        for (Module module : all()) {
            if (module.keyCode() == key) {
                if (module instanceof Cosmetics) {
                    // Бинд косметики открывает колесо, а не переключает функцию.
                    net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                            new dev.hatek.client.ui.screen.CosmeticsScreen(key));
                    handled = true;
                    continue;
                }
                module.toggle();
                handled = true;
            }
        }
        return handled;
    }

    private static void register() {
        MODULES.add(new AttackAura().expanded(true));

        MODULES.add(new AimAssist().expanded(true));

        MODULES.add(new AutoMace().expanded(true));

        MODULES.add(new AutoSprint().enabled(true).expanded(true));

        MODULES.add(new NoJumpDelay());

        MODULES.add(new NoFall());

        MODULES.add(new NoRender().expanded(true));

        MODULES.add(new Ambience().expanded(true));

        MODULES.add(new Chams().expanded(true));

        MODULES.add(new KillEffect().expanded(true));

        MODULES.add(new HitParticles().expanded(true));

        MODULES.add(new WorldParticles().expanded(true));

        MODULES.add(new Interface().expanded(true));

        MODULES.add(new ClickGui().enabled(true).expanded(true));

        MODULES.add(new Cosmetics().enabled(true).expanded(true));

        MODULES.add(new VoiceControl());

        MODULES.add(new Jarvis());

        for (Module module : MODULES) {
            DEFAULT_ENABLED.put(module, module.isEnabled());
            DEFAULT_KEYS.put(module, module.keyCode());
            for (Setting setting : module.settings()) {
                DEFAULT_SETTINGS.put(setting, setting.serialize());
            }
        }
    }
}
