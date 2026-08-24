package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
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
 * TpAura: HvH телепорт-удар с гарантированным критом.
 *
 * Обычное оружие: прыжок -> телепорт к цели на падении (сервер видит нас
 * в воздухе с fallDistance > 0 -> крит) -> удар -> резкий возврат.
 *
 * Булава: телепорт ВВЕРХ над целью на высоту смэша -> пауза (сервер принимает
 * позицию) -> сброс вниз -> удар на следующем тике: серверный fallDistance
 * даёт полный смэш-бонус булавы + крит. Возврат вверх обнуляет серверный
 * fallDistance — своего урона от падения нет.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Телепортируется к цели, бьёт критом и возвращается обратно", category = Category.COMBAT)
public class TpAura extends Module {

    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 15.0F, 3.0F, 50.0F, 0.5F);
    public SliderSetting standOff = new SliderSetting(this, "Дистанция удара", 2.2F, 1.5F, 3.0F, 0.1F);
    public SliderSetting cooldown = new SliderSetting(this, "Перезарядка", 800F, 250F, 2000F, 50F);
    public SliderSetting maceHeight = new SliderSetting(this, "Высота смэша булавы", 6.0F, 2.0F, 15.0F, 0.5F);

    public BooleanSetting critOnly = new BooleanSetting(this, "Только криты", true);
    public BooleanSetting autoJump = new BooleanSetting(this, "Прыжок для крита", true)
            .setVisible(() -> critOnly.getValue());

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_AT_TARGET = 1;
    private static final int PHASE_MACE_UP = 2;
    private static final int PHASE_MACE_DOWN = 3;

    private int strikePhase = PHASE_IDLE;
    private Vec3d returnPos = null;
    private Vec3d dropPos = null;
    private LivingEntity strikeTarget = null;
    private long phaseDeadline = 0L;
    private long nextStrike = 0L;
    private int phaseTicks = 0;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        switch (strikePhase) {
            case PHASE_AT_TARGET -> {
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
            }
            case PHASE_MACE_UP -> {
                LivingEntity t = strikeTarget;
                if (t == null || !t.isAlive() || System.currentTimeMillis() > phaseDeadline) {
                    goBack();
                    return;
                }
                if (++phaseTicks < 2) return;

                mc.player.setPosition(dropPos.x, dropPos.y, dropPos.z);
                strikePhase = PHASE_MACE_DOWN;
                phaseTicks = 0;
                phaseDeadline = System.currentTimeMillis() + 400L;
            }
            case PHASE_MACE_DOWN -> {
                LivingEntity t = strikeTarget;
                if (t == null || !t.isAlive() || System.currentTimeMillis() > phaseDeadline) {
                    goBack();
                    return;
                }
                if (++phaseTicks < 1) return;

                strike(t);
                goBack();
            }
            default -> idleTick();
        }
    }

    private void idleTick() {
        if (mc.player.isUsingItem() || mc.currentScreen != null) return;

        long ms = System.currentTimeMillis();
        if (ms < nextStrike) return;

        AttackAura aura = AttackAura.get();
        float atkRange = aura.attackRange.getValue();

        LivingEntity target = findTarget();
        if (target == null) return;

        double dist = mc.player.getEyePos().distanceTo(target.getEntityPos());
        if (dist > tpRadius.getValue() || dist <= atkRange) return;

        boolean mace = mc.player.getMainHandStack().getItem() == Items.MACE;

        Vec3d toPlayer = mc.player.getEntityPos().subtract(target.getEntityPos());
        Vec3d dir = new Vec3d(toPlayer.x, 0, toPlayer.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : dir.normalize();
        Vec3d flat = target.getEntityPos().add(dir.multiply(standOff.getValue()));

        returnPos = mc.player.getEntityPos();
        strikeTarget = target;
        phaseTicks = 0;

        // ── булава: подъём над целью, сброс, смэш ──
        if (mace) {
            Vec3d up = new Vec3d(flat.x, target.getY() + maceHeight.getValue(), flat.z);
            dropPos = new Vec3d(flat.x, target.getY() + 0.6, flat.z);
            mc.player.setPosition(up.x, up.y, up.z);
            mc.player.setVelocity(Vec3d.ZERO);
            strikePhase = PHASE_MACE_UP;
            phaseDeadline = ms + 600L;
            return;
        }

        // ── обычное оружие: крит на падении ──
        if (critOnly.getValue()) {
            if (mc.player.isOnGround()) {
                if (autoJump.getValue()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
                }
                returnPos = null;
                strikeTarget = null;
                return;
            }
            if (!AttackUtil.isPlayerInCriticalState() && mc.player.getVelocity().y >= 0) {
                returnPos = null;
                strikeTarget = null;
                return;
            }
            Vec3d landing = new Vec3d(flat.x, target.getY() + 0.35F, flat.z);
            mc.player.setPosition(landing.x, landing.y, landing.z);
            strikePhase = PHASE_AT_TARGET;
            phaseDeadline = ms + 400L;
            return;
        }

        // без крит-режима — обычный удар в корпус
        Vec3d landing = new Vec3d(flat.x, target.getY(), flat.z);
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
        mc.player.fallDistance = 0;
        returnPos = null;
        dropPos = null;
        strikeTarget = null;
        strikePhase = PHASE_IDLE;
        phaseTicks = 0;
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
