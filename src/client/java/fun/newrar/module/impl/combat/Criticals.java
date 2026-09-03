package fun.newrar.module.impl.combat;

import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.utils.other.Instance;

@ModuleInfo(
        name = "Criticals",
        desc = "Нанесение критических ударов с земли за счет микро-прыжков",
        category = Category.COMBAT
)
public class Criticals extends Module {
    public static Criticals getInstance() {
        return Instance.get(Criticals.class);
    }

    public ModeSetting type = new ModeSetting(this, "Режим", "Хоп", "Хоп");

    @EventHandler
    public void onEvent(EventUpdate e) {
        if (mc.player != null
                && mc.player.getAttackCooldownProgress(2.0F) >= 1.0F
                && mc.player.isOnGround()
                && AttackAura.target != null) {
            mc.player.setVelocity(
                    mc.player.getVelocity().x,
                    0.04,
                    mc.player.getVelocity().z
            );

            mc.player.velocityDirty = true;
        }
    }

    public boolean canCritical() {
        return this.isEnabled() && mc.player.fallDistance <= 0.0F && !mc.player.isOnGround();
    }
}

