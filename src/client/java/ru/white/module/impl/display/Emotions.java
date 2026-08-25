package ru.white.module.impl.display;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.white.emotions.EmoteManager;
import ru.white.emotions.EmoteWheelScreen;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;

/**
 * Эмоции: колесо выбора открывается по ЛЮБОМУ из трёх независимых путей:
 * 1. Ванильный KeyBinding (виден в настройках управления Minecraft) —
 *    самый надёжный, не зависит от шины событий клиента.
 * 2. Прямое GLFW-опросление BindSetting каждый тик (мимо EventBus).
 * 3. Классический EventKey-обработчик.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "Emotions", desc = "Emote wheel (bind in MC controls or here)", category = Category.OTHER)
public class Emotions extends Module {

    /** Ванильный бинд — настраивается и в управлении Minecraft. */
    public static KeyBinding vanillaKey;

    /** Клиентский бинд — опрашивается напрямую через GLFW. */
    public BindSetting wheelKey = new BindSetting(this, "Wheel key", -1);

    private boolean lastBindDown = false;
    private boolean lastVanillaDown = false;

    public Emotions() {
        try {
            vanillaKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.nightix.emotions",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    KeyBinding.Category.MISC));
        } catch (Throwable t) {
            System.out.println("[Emotions] vanilla key registration failed: " + t);
        }

        // опрос в конце каждого тика — не зависит от шины событий
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        System.out.println("[Emotions] constructed, tick polling active");
    }

    private void onTick(MinecraftClient mc) {
        // путь 1: ванильный KeyBinding
        try {
            boolean vanillaPressed = vanillaKey != null && vanillaKey.wasPressed();
            if (vanillaPressed) {
                openWheel();
                return;
            }
        } catch (Throwable ignored) {
        }

        if (mc.player == null || mc.world == null) {
            lastBindDown = false;
            lastVanillaDown = false;
            return;
        }

        // путь 2: прямой GLFW-опрос клиентского бинда
        int code = wheelKey.get();
        if (code > 0) {
            long window = mc.getWindow().getHandle();
            boolean down = (code <= 7)
                    ? GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;

            if (down && !lastBindDown && (mc.currentScreen == null
                    || mc.currentScreen instanceof EmoteWheelScreen)) {
                openWheel();
            }
            lastBindDown = down;
        }
    }

    /** Путь 3: классическое событие клавиши. */
    @EventHandler
    public void onKey(EventKey e) {
        int code = wheelKey.get();
        if (code > 0 && e.getKey() == code) {
            openWheel();
        }
    }

    private void openWheel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen instanceof EmoteWheelScreen) {
            mc.setScreen(null);
            return;
        }
        if (mc.currentScreen != null) return;

        try {
            mc.setScreen(new EmoteWheelScreen());
            System.out.println("[Emotions] wheel opened");
        } catch (Throwable t) {
            System.out.println("[Emotions] wheel FAILED:");
            t.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        EmoteManager.stop();
        super.onDisable();
    }
}
