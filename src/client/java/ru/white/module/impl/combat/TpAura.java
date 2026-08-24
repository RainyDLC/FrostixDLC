package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.AttackUtil;
import ru.white.utils.aura.UAttack;

/**
 * TpAura: HvH телепорт-удар. Когда цель вне досягаемости ауры, но в радиусе
 * телепорта — шлём пакет позиции у корпуса цели, наносим удар с обходом щита
 * и сразу возвращаемся пакетом на реальную позицию. Сервер видит:
 * рывок к цели -> удар -> мгновенный возврат.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Телепортируется к цели, бьёт и возвращается обратно", category = Category.COMBAT)
public class TpAura extends Module {

    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 15.0F, 3.0F, 50.0F, 0.5F);
    public SliderSetting standOff = new SliderSetting(this, "Дистанция удара", 2.2F, 1.5F, 3.0F, 0.1F);
    public SliderSetting cooldown = new SliderSetting(this, "Перезарядка", 800F, 250F, 2000F, 50F);

    public BooleanSetting critOnly = new BooleanSetting(this, "Только криты", true);
    public BooleanSetting autoJump = new BooleanSetting(this, "Прыжок для крита", true)
            .setVisible(() -> critOnly.getValue());

    private long nextStrike = 0L;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) return;
        if (mc.player.isUsingItem() || mc.currentScreen != null) return;

        long ms = System.currentTimeMillis();
        if (ms < nextStrike) return;

        AttackAura aura = AttackAura.get();
        float atkRange = aura.attackRange.getValue();

        // своя цель: аура не берёт цели дальше attackRange + обнаружение,
        // поэтому ищем самостоятельно в радиусе телепорта
        LivingEntity target = findTarget();
        if (target == null) return;

        double dist = mc.player.getEyePos().distanceTo(target.getEntityPos());
        if (dist > tpRadius.getValue() || dist <= atkRange) return;

        // только криты: бьём строго в состоянии крита, иначе прыгаем и ждём падения
        if (critOnly.getValue() && !AttackUtil.isPlayerInCriticalState()) {
            if (autoJump.getValue() && mc.player.isOnGround()) {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
            }
            return;
        }

        float[] ranges = aura.getRanges();
        if (!UAttack.shouldAttack(target, false, false, !critOnly.getValue(), 0L, ranges)) return;

        // точка высадки: между нами и целью, на дистанции удара от её корпуса
        Vec3d toPlayer = mc.player.getEntityPos().subtract(target.getEntityPos());
        Vec3d dir = new Vec3d(toPlayer.x, 0, toPlayer.z);
        if (dir.lengthSquared() < 1e-4) {
            dir = new Vec3d(1, 0, 0);
        } else {
            dir = dir.normalize();
        }
        Vec3d landing = target.getEntityPos().add(dir.multiply(standOff.getValue()));
        landing = new Vec3d(landing.x, target.getY(), landing.z);

        // 1) рывок к цели
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                landing, mc.player.isOnGround(), false));

        // 2) удар с обходом щита
        final Runnable[] shieldBreak = UAttack.hitShieldBreakTaskForUse(target, true);
        final Runnable[] shieldPress = UAttack.resetShieldSilentTaskForUse(true);
        final Runnable[] skipSprint = UAttack.skipSilentSprintingTaskForUse(false);
        UAttack.useEntity(target,
                () -> {
                    skipSprint[0].run();
                    shieldPress[0].run();
                    shieldBreak[0].run();
                },
                () -> {
                    shieldBreak[1].run();
                    shieldPress[1].run();
                    skipSprint[1].run();
                },
                Hand.MAIN_HAND, false);

        // 3) мгновенный возврат на реальную позицию
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getEntityPos(), mc.player.isOnGround(), false));

        nextStrike = ms + cooldown.getValue().longValue();
    }

    /** Ближайшая валидная цель в радиусе телепорта — фильтры самой ауры. */
    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        double radius = tpRadius.getValue();
        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof LivingEntity living)) continue;
            if (!AttackAura.get().isValidTarget(living, radius)) continue;
            double d = mc.player.getEyePos().distanceTo(living.getEntityPos());
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }
}
