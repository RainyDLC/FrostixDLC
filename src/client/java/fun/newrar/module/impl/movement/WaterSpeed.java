package fun.newrar.module.impl.movement;

import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.player.MoveUtil;

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

