package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
 * TpAura: HvH телепорт-удар. Игрок реально телепортируется к корпусу цели
 * (видно визуально), наносит удар и резко возвращается на исходную точку.
 * Фазы: 0 — поиск/прыжок для крита, 1 — стоим у цели (сервер уже принял
 * позицию), удар, 2 — мгновенный возврат.
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

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_AT_TARGET = 1;

    private int strikePhase = PHASE_IDLE;
    private Vec3d returnPos = null;
    private LivingEntity strikeTarget = null;
    private long phaseDeadline = 0L;
    private long nextStrike = 0L;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (strikePhase == PHASE_AT_TARGET) {
            LivingEntity t = strikeTarget;
            if (t == null || !t.isAlive() || System.currentTimeMillis() > phaseDeadline) {
                goBack();
                return;
            }

            if (!UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())) {
                return;
            }

            strike(t);
            goBack();
            return;
        }

        if (mc.player.isUsingItem() || mc.currentScreen != null) return;

        long ms = System.currentTimeMillis();
        if (ms < nextStrike) return;

        AttackAura aura = AttackAura.get();
        float atkRange = aura.attackRange.getValue();

        LivingEntity target = findTarget();
        if (target == null) return;

        double dist = mc.player.getEyePos().distanceTo(target.getEntityPos());
        if (dist > tpRadius.getValue() || dist <= atkRange) return;

        if (critOnly.getValue() && !AttackUtil.isPlayerInCriticalState()) {
            if (autoJump.getValue() && mc.player.isOnGround()) {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
            }
            return;
        }

        Vec3d toPlayer = mc.player.getEntityPos().subtract(target.getEntityPos());
        Vec3d dir = new Vec3d(toPlayer.x, 0, toPlayer.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : dir.normalize();
        Vec3d landing = target.getEntityPos().add(dir.multiply(standOff.getValue()));
        landing = new Vec3d(landing.x, target.getY(), landing.z);

        returnPos = mc.player.getEntityPos();
        strikeTarget = target;
        mc.player.setPosition(landing.x, landing.y, landing.z);
        strikePhase = PHASE_AT_TARGET;
        phaseDeadline = ms + 400L;
    }

    private void strike(LivingEntity t) {
        final Runnable[] shieldBreak = UAttack.hitShieldBreakTaskForUse(t, true);
        final Runnable[] shieldPress = UAttack.resetShieldSilentTaskForUse(true);
        final Runnable[] skipSprint = UAttack.skipSilentSprintingTaskForUse(false);
        UAttack.useEntity(t,
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
    }

    private void goBack() {
        if (returnPos != null) {
            mc.player.setPosition(returnPos.x, returnPos.y, returnPos.z);
        }
        returnPos = null;
        strikeTarget = null;
        strikePhase = PHASE_IDLE;
        nextStrike = System.currentTimeMillis() + cooldown.getValue().longValue();
    }

    @Override
    public void onDisable() {
        if (strikePhase != PHASE_IDLE) {
            goBack();
        }
        super.onDisable();
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
