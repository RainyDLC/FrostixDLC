package ru.white.module.impl.movement;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.player.MoveUtil;

@ModuleInfo(
        name = "WaterSpeed",
        desc = "Увеличение скорости плавания и горизонтального перемещения в воде",
        category = Category.MOVEMENT
)
public class WaterSpeed extends Module {
    public ModeSetting type = new ModeSetting(this, "Режим", "Vanilla");
    public SliderSetting speed = new SliderSetting(this, "Скорость", 1.0F, 0.1F, 3.0F, 0.05F);

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (!type.is("Vanilla")) return;

        if (mc.player.isTouchingWater() && MoveUtil.isMoving()) {
            MoveUtil.setSpeed(speed.getValue());
        }
    }
}
