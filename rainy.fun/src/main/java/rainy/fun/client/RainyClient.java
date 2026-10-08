package rainy.fun.client;

import rainy.fun.gui.ClickGuiScreen;
import rainy.fun.module.ModuleManager;
import rainy.fun.module.impl.render.ambience.AmbienceModule;
import rainy.fun.module.impl.render.ambience.AmbiencePostEffect;
import rainy.fun.module.impl.render.glass.GlassBlurPostEffect;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class RainyClient implements ClientModInitializer {
    public static final String MOD_ID = "rainyfun";

    private static RainyClient instance;

    private final ModuleManager moduleManager = new ModuleManager();
    private boolean rightShiftWasPressed;

    @Override
    public void onInitializeClient() {
        if (instance != null) {
            return;
        }
        instance = this;
        moduleManager.register(new AmbienceModule());

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        Minecraft.getInstance().execute(() -> AmbiencePostEffect.loadEmbeddedResources(Minecraft.getInstance()));
        System.out.println("[Rainy.fun] Client framework initialized");
    }

    private void onClientTick(Minecraft client) {
        moduleManager.tickEnabled();
        rainy.fun.module.impl.render.ambience.AmbiencePostEffect.update(client);
        GlassBlurPostEffect.update(client);
        boolean rightShiftPressed = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT)
                == GLFW.GLFW_PRESS;
        if (rightShiftPressed && !rightShiftWasPressed) {
            if (client.gui.screen() instanceof ClickGuiScreen) {
                client.gui.setScreen(null);
            } else if (client.gui.screen() == null) {
                client.gui.setScreen(new ClickGuiScreen(moduleManager));
            }
        }
        rightShiftWasPressed = rightShiftPressed;
    }

    public static RainyClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Rainy.fun has not been initialized");
        }
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static Component text(String key) {
        return Component.translatable(key);
    }
}
