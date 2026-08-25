package ru.white.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import ru.white.Client;
import ru.white.command.CommandManager;
import ru.white.config.ConfigManager;
import ru.white.friend.FriendManager;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.manager.GuiManager;
import ru.white.manager.rotation.ComponentManager;
import ru.white.module.api.ModuleManager;
import ru.white.rpc.RPC;
import ru.white.screen.Menu;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.font.FontInitializer;
import ru.white.script.LuaScriptManager;

import java.lang.invoke.MethodHandles;

/**
 * Ядро инициализации: в защищённой сборке класс зашифрован и загружается
 * только через NightixLoader после проверки целостности.
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void init(Client client) {
        Client.get = client;

        applyWindowIcon();

        ru.white.lang.Lang.init();

        Client.eventHandler().registerLambdaFactory("", (lookupInMethod,
                                                         klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        FontInitializer.register();

        RPC rpc = client.rpc();
        rpc.startRpc();

        ConfigManager configManager = new ConfigManager();
        configManager.setup();
        client.configManager(configManager);

        FriendManager friendManager = new FriendManager();
        friendManager.init();
        client.friendManager(friendManager);

        InventoryPresetManager inventoryPresetManager = new InventoryPresetManager();
        inventoryPresetManager.init();
        client.inventoryPresetManager(inventoryPresetManager);

        ModuleManager moduleManager = new ModuleManager();
        moduleManager.init();
        client.moduleManager(moduleManager);

        // пользовательские Lua-модули — после основных модулей
        LuaScriptManager.get().loadAll();

        ComponentManager componentManager = new ComponentManager();
        componentManager.init();
        client.componentManager(componentManager);

        CommandManager commandManager = new CommandManager();
        commandManager.init();
        client.commandManager(commandManager);

        configManager.init();

        GuiManager guiManager = new GuiManager();
        guiManager.init();
        client.guiManager(guiManager);

        Render2D render2D = new Render2D();
        client.render2D(render2D);

        Menu clickGuiScreen = new Menu();
        client.clickGuiScreen(clickGuiScreen);

        Menu.selectedTheme = guiManager.getCurrentTheme();
        Menu.preSelectedTheme = guiManager.getCurrentTheme();

        Runtime.getRuntime().addShutdownHook(new Thread(client::unload));
    }

    /** Иконка окна/таскбара из ресурсов клиента (без фона). */
    private static void applyWindowIcon() {
        try {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            if (handle == 0L) return;

            try (var s64 = ClientMain.class.getResourceAsStream("/assets/client/textures/icon2.png");
                 var s128 = ClientMain.class.getResourceAsStream("/assets/client/textures/icon.png")) {
                if (s64 == null || s128 == null) return;

                try (NativeImage img64 = NativeImage.read(s64);
                     NativeImage img128 = NativeImage.read(s128);
                     GLFWImage i64 = GLFWImage.malloc();
                     GLFWImage i128 = GLFWImage.malloc();
                     GLFWImage.Buffer buffer = GLFWImage.malloc(2)) {

                    buffer.put(0, i64.set(img64.getWidth(), img64.getHeight(),
                            org.lwjgl.system.MemoryUtil.memByteBuffer(img64.pointer, img64.getWidth() * img64.getHeight() * 4)));
                    buffer.put(1, i128.set(img128.getWidth(), img128.getHeight(),
                            org.lwjgl.system.MemoryUtil.memByteBuffer(img128.pointer, img128.getWidth() * img128.getHeight() * 4)));
                    GLFW.glfwSetWindowIcon(handle, buffer);
                }
            }
        } catch (Exception e) {
            // иконка не критична: окно может быть ещё не готово или ресурс недоступен
        }
    }
}
