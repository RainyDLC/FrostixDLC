package fun.newrar.module.impl.display;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import fun.newrar.emotions.EmoteManager;
import fun.newrar.emotions.EmoteWheelScreen;
import fun.newrar.manager.event_impl.EventKey;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BindSetting;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "Emotion Wheel", desc = "Интерактивное колесо выбора и воспроизведения анимаций и эмоций", category = Category.OTHER)
public class Emotions extends Module {
    public BindSetting wheelKey = new BindSetting(this, "Кнопка открытия", -1);

    private boolean lastBindDown = false;

    public Emotions() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            lastBindDown = false;
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
        if (!isEnabled()) return;
        int code = wheelKey.get();
        if (code > 0 && e.getKey() == code) {
            openWheel();
        }
    }

    private void openWheel() {
        if (!isEnabled()) return;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof EmoteWheelScreen) {
            mc.setScreen(null);
        }
        super.onDisable();
    }
}

