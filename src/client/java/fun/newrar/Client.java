package fun.newrar;

import net.fabricmc.api.ClientModInitializer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import fun.newrar.command.CommandManager;
import fun.newrar.config.ConfigManager;
import fun.newrar.friend.FriendManager;
import fun.newrar.inventorypreset.InventoryPresetManager;
import fun.newrar.manager.GuiManager;
import fun.newrar.manager.events.orbit.EventBus;
import fun.newrar.manager.rotation.ComponentManager;
import fun.newrar.module.api.ModuleManager;
import fun.newrar.rpc.RPC;
import fun.newrar.screen.DropdownScreen;
import fun.newrar.screen.Menu;
import fun.newrar.utils.render.Render2D;

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
    DropdownScreen dropdownClickGuiScreen;
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
        RainyDlcLoader.bootstrap(this);
    }

    public void unload() {
        if (configManager != null) {
            configManager.autoSave();
        }
    }
}

