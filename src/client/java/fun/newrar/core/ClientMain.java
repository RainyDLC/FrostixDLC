package fun.newrar.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import fun.newrar.Client;
import fun.newrar.command.CommandManager;
import fun.newrar.config.ConfigManager;
import fun.newrar.friend.FriendManager;
import fun.newrar.inventorypreset.InventoryPresetManager;
import fun.newrar.manager.GuiManager;
import fun.newrar.manager.rotation.ComponentManager;
import fun.newrar.module.api.ModuleManager;
import fun.newrar.rpc.RPC;
import fun.newrar.screen.Menu;
import fun.newrar.utils.render.Render2D;
import fun.newrar.utils.render.font.FontInitializer;
import fun.newrar.script.LuaScriptManager;

import java.lang.invoke.MethodHandles;

public final class ClientMain {
    private ClientMain() {
    }

    public static void init(Client client) {
        Client.get = client;

        applyWindowIcon();

        fun.newrar.lang.Lang.init();

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
        }
    }
}

