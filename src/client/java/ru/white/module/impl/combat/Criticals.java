package ru.white.module.impl.combat;

import net.minecraft.client.util.InputUtil;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.utils.aura.AttackUtil;
import ru.white.utils.other.Instance;
import net.minecraft.registry.tag.FluidTags;

@ModuleInfo(
        name = "Criticals",
        desc = "Криты с земли",
        category = Category.COMBAT
)
public class Criticals extends Module {

    public static Criticals getInstance() {
        return Instance.get(Criticals.class);
    }

    /**
     * Грим — ванильный прыжок: единственное состояние крита с земли,
     * которое предсказывает симуляция GrimAC (мини-хоп 0.04 — невозможное
     * состояние = мгновенный флаг Prediction). Хоп — старый способ для
     * серверов без предикт-античитов.
     */
    public ModeSetting type = new ModeSetting(this, "Режим", "Грим", "Грим", "Хоп");

    private int hopTicks = 0;

    @EventHandler
    public void onEvent(EventUpdate e) {
        if (mc.player == null) return;

        if (type.is("Хоп")) {
            hop0();
            return;
        }

        // Грим: одноразовый ванильный прыжок перед ударом ауры.
        if (hopTicks > 0) {
            if (--hopTicks == 0) {
                // возвращаем клавише реальное физическое состояние
                mc.options.jumpKey.setPressed(InputUtil.isKeyPressed(
                        mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode()));
            }
            return;
        }

        if (mc.player.getAttackCooldownProgress(2.0F) >= 1.0F
                && mc.player.isOnGround()
                && !mc.options.jumpKey.isPressed()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedIn(FluidTags.WATER)
                && !mc.player.isClimbing()
                && !AttackUtil.hasMovementRestrictions()
                && AttackAura.target != null) {

            mc.options.jumpKey.setPressed(true);
            hopTicks = 2;
        }
    }

    /** Старый мини-хоп: работает там, где нет предикт-античита. */
    private void hop0() {
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
