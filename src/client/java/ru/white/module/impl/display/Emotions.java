package ru.white.module.impl.display;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.white.emotions.EmoteManager;
import ru.white.emotions.EmoteWheelScreen;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "Emotions", desc = "Emote wheel (bind in MC controls or here)", category = Category.OTHER)
public class Emotions extends Module {
    public static KeyBinding vanillaKey;

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
        } catch (Throwable ignored) {
        }

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
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
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        EmoteManager.stop();
        super.onDisable();
    }
}
