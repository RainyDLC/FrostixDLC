package ru.white.module.impl.movement;

import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;

@ModuleInfo(
        name = "Spider",
        desc = "Позволяет лазить по стенам",
        category = Category.MOVEMENT
)
public class Spider extends Module {
    public ModeSetting type = new ModeSetting(this, "Режим", "Vanilla");
    public SliderSetting speed = new SliderSetting(this, "Скорость", 0.2F, 0.1F, 1.0F, 0.05F);

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (!type.is("Vanilla")) return;

        if (mc.player.horizontalCollision) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, speed.getValue(), vel.z);
        }
    }
}
