package fun.newrar.module.impl.movement;

import net.minecraft.util.math.Vec3d;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;

@ModuleInfo(
        name = "Spider",
        desc = "Возможность вертикального подъема по любым отвесным стенам и препятствиям",
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

