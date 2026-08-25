package ru.white.module.impl.display;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import ru.white.emotions.EmoteManager;
import ru.white.emotions.EmoteWheelScreen;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;

/**
 * Эмоции: бинд открывает колесо выбора, выбранная эмоция проигрывается
 * на модели локального игрока (руки/ноги/голова/корпус).
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "Emotions", desc = "Emote wheel on bind", category = Category.OTHER)
public class Emotions extends Module {

    public BindSetting wheelKey = new BindSetting(this, "Wheel key", -1);

    @EventHandler
    public void onKey(EventKey e) {
        // бинд работает всегда, как у ClickGui — модуль включать не нужно
        if (wheelKey.get() == -1 || e.getKey() != wheelKey.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen == null) {
            mc.setScreen(new EmoteWheelScreen());
        } else if (mc.currentScreen instanceof EmoteWheelScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    public void onDisable() {
        EmoteManager.stop();
        super.onDisable();
    }
}
