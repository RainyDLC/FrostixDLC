package fun.newrar.module.impl.movement;

import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.utils.aura.AuraUtil;

@ModuleInfo(
        name = "No Web",
        desc = "Снятие замедления при передвижении внутри блоков паутины",
        category = Category.MOVEMENT
)
public class NoWeb extends Module {
    @EventHandler
    public void onEvent(EventUpdate e) {
        if (!AuraUtil.nullCheck() && AuraUtil.isPlayerInWeb()) {
            double[] speed = AuraUtil.calculateDirection(0.5F);
            double y = mc.options.jumpKey.isPressed() ? 1.2 : mc.options.sneakKey.isPressed() ? -2.0 : 0.0;
            mc.player.setVelocity(speed[0], y, speed[1]);
        }
    }
}

