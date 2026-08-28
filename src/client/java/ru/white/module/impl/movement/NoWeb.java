package ru.white.module.impl.movement;

import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.aura.AuraUtil;

@ModuleInfo(
        name = "No Web",
        desc = "Позволяет быстро передвигаться в паутине",
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
