package ru.white;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import ru.white.command.CommandManager;
import ru.white.config.ConfigManager;
import ru.white.friend.FriendManager;
import ru.white.inventorypreset.InventoryPresetManager;
import ru.white.manager.GuiManager;
import ru.white.manager.events.orbit.EventBus;
import ru.white.manager.rotation.ComponentManager;
import ru.white.module.api.ModuleManager;
import ru.white.rpc.RPC;
import ru.white.screen.Menu;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.font.FontInitializer;
import ru.white.script.LuaScriptManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;


import java.lang.invoke.MethodHandles;

@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Client implements ClientModInitializer {

    public static Client get;

    public static Client get() {
        return Client.get;
    }

    @Getter
    private static final EventBus eventHandler = EventBus.threadSafe();

    
    public void start() {
        eventHandler.registerLambdaFactory("", (lookupInMethod,
                                                klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        FontInitializer.register();
    }

    private ModuleManager moduleManager;
    private Render2D render2D;
    private Menu clickGuiScreen;
    private ComponentManager componentManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private FriendManager friendManager;
    private InventoryPresetManager inventoryPresetManager;
    final RPC rpc = new RPC();
    public static String build = "5.0";
    private GuiManager guiManager;

    
    /** Иконка окна/таскбара из ресурсов клиента (без фона). */
    private void applyWindowIcon() {
        try {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            if (handle == 0L) return;

            try (var s64 = Client.class.getResourceAsStream("/assets/client/textures/icon2.png");
                 var s128 = Client.class.getResourceAsStream("/assets/client/textures/icon.png")) {
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

    @Override
    public void onInitializeClient() {

        System.out.print("Йа:3");

        get = this;

        applyWindowIcon();

        ru.white.lang.Lang.init();

        start();
        rpc.startRpc();

        this.configManager = new ConfigManager();
        this.configManager.setup();

        this.friendManager = new FriendManager();
        this.friendManager.init();

        this.inventoryPresetManager = new InventoryPresetManager();
        this.inventoryPresetManager.init();

        this.moduleManager = new ModuleManager();
        this.moduleManager.init();

        // пользовательские Lua-модули — после основных модулей
        LuaScriptManager.get().loadAll();

        this.componentManager = new ComponentManager();
        this.componentManager.init();

        this.commandManager = new CommandManager();
        this.commandManager.init();

        this.configManager.init();


        this.guiManager = new GuiManager();
        this.guiManager.init();


        this.render2D = new Render2D();
        this.clickGuiScreen = new Menu();

        Menu.selectedTheme = guiManager.getCurrentTheme();
        Menu.preSelectedTheme = guiManager.getCurrentTheme();

        Runtime.getRuntime().addShutdownHook(new Thread(this::unload));
    }

    public void unload() {
        if (configManager != null) {
            configManager.autoSave();
        }
    }

}
