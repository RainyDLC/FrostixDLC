package ru.white;

import net.fabricmc.api.ClientModInitializer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
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

    public static String build = "5.0";

    @Setter
    ModuleManager moduleManager;
    @Setter
    Render2D render2D;
    @Setter
    Menu clickGuiScreen;
    @Setter
    ComponentManager componentManager;
    @Setter
    CommandManager commandManager;
    @Setter
    ConfigManager configManager;
    @Setter
    FriendManager friendManager;
    @Setter
    InventoryPresetManager inventoryPresetManager;
    @Setter
    GuiManager guiManager;

    final RPC rpc = new RPC();

    @Override
    public void onInitializeClient() {
        NightixLoader.bootstrap(this);
    }

    public void unload() {
        if (configManager != null) {
            configManager.autoSave();
        }
    }
}
