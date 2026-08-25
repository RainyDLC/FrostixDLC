package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.UAttack;

/**
 * TpAura: HvH телепорт-удар, видимый блинк, всегда крит.
 *
 * Обычное оружие: прыжок (честный крит-фолл) -> блинк к цели ГОРИЗОНТАЛЬНО
 * на текущей высоте (fallDistance сервера не сбрасывается) -> удар -> блинк назад.
 * Блинк реальным перемещением — камера показывает телепорт.
 *
 * Булава: отдельная хореография — подъём на высоту смэша, сброс к цели
 * (серверный fallDistance = высоте -> смэш + крит), удар, возврат.
 *
 * Анти-промах: линия огня (8 углов, рейкаст), hurtTime == 0, полный кулдаун.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Teleport strike: visible blink, always crit", category = Category.COMBAT)
public class TpAura extends Module {

    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 6.0F, 3.0F, 50.0F, 0.5F);
    public SliderSetting standOff = new SliderSetting(this, "Дистанция удара", 2.2F, 1.5F, 3.0F, 0.1F);
    public SliderSetting cooldown = new SliderSetting(this, "Перезарядка", 800F, 250F, 2000F, 50F);
    public SliderSetting maceHeight = new SliderSetting(this, "Высота смэша булавы", 6.0F, 2.0F, 15.0F, 0.5F);

    public BooleanSetting critOnly = new BooleanSetting(this, "Только криты", true);
    public BooleanSetting autoJump = new BooleanSetting(this, "Прыжок для крита", true)
            .setVisible(() -> critOnly.getValue());

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_BLINK = 1;
    private static final int PHASE_MACE_UP = 2;
    private static final int PHASE_MACE_DOWN = 3;

    private int phase = PHASE_IDLE;
    private Vec3d returnPos = null;
    private Vec3d dropPos = null;
    private LivingEntity strikeTarget = null;
    private long phaseDeadline = 0L;
    private long nextStrike = 0L;
    private int phaseTicks = 0;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        switch (phase) {
            case PHASE_BLINK -> phaseBlink();
            case PHASE_MACE_UP -> phaseMaceUp();
            case PHASE_MACE_DOWN -> phaseMaceDown();
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

        // крит-гейт для обычного оружия: прыжок и ожидание падения
        if (!mace && critOnly.getValue()) {
            if (mc.player.isOnGround()) {
                if (autoJump.getValue()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
                }
                return;
            }
            if (mc.player.getVelocity().y >= 0) return;
        }

        // точка высадки на ТЕКУЩЕЙ высоте (горизонтальный блинк, крит не сбрасывается)
        Vec3d landing = findLanding(target, standOff.getValue());
        if (landing == null) return;

        returnPos = mc.player.getEntityPos();
        strikeTarget = target;
        phaseTicks = 0;

        if (mace) {
            Vec3d up = new Vec3d(landing.x, target.getY() + maceHeight.getValue(), landing.z);
            dropPos = new Vec3d(landing.x, target.getY() + 0.4, landing.z);
            mc.player.setPosition(up.x, up.y, up.z);
            mc.player.setVelocity(Vec3d.ZERO);
            phase = PHASE_MACE_UP;
        } else {
            mc.player.setPosition(landing.x, landing.y, landing.z);
            phase = PHASE_BLINK;
        }
        phaseDeadline = ms + 600L;
    }

    private void phaseBlink() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            goBack();
            return;
        }
        // тик на принятие позиции сервером, затем удар и возврат
        if (t.hurtTime > 0 && System.currentTimeMillis() < phaseDeadline) return;
        if (!UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())
                && System.currentTimeMillis() < phaseDeadline) return;

        strike(t);
        goBack();
    }

    private void phaseMaceUp() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            goBack();
            return;
        }
        if (++phaseTicks < 2) return;

        // сброс к цели: сервер видит падение с высоты смэша
        mc.player.setPosition(dropPos.x, dropPos.y, dropPos.z);
        phase = PHASE_MACE_DOWN;
        phaseTicks = 0;
        phaseDeadline = System.currentTimeMillis() + 600L;
    }

    private void phaseMaceDown() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            goBack();
            return;
        }
        if (++phaseTicks < 2) return;
        if (t.hurtTime > 0 && System.currentTimeMillis() < phaseDeadline) return;
        if (!UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())
                && System.currentTimeMillis() < phaseDeadline) return;

        strike(t);
        goBack();
    }

    private boolean valid(LivingEntity t) {
        return t != null && t.isAlive()
                && System.currentTimeMillis() <= phaseDeadline
                && mc.player != null;
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
        phase = PHASE_IDLE;
        phaseTicks = 0;
        nextStrike = System.currentTimeMillis() + cooldown.getValue().longValue();
    }

    @Override
    public void onDisable() {
        if (phase != PHASE_IDLE) {
            goBack();
        }
        super.onDisable();
    }

    /**
     * Точка высадки на текущей высоте игрока: 8 углов вокруг цели,
     * первая с чистой линией огня от глаз до корпуса цели.
     */
    private Vec3d findLanding(LivingEntity target, double stand) {
        Vec3d tpos = target.getEntityPos();
        Vec3d targetEye = tpos.add(0, target.getHeight() * 0.9, 0);
        double y = mc.player.getY();

        Vec3d toMe = mc.player.getEntityPos().subtract(tpos);
        double base = Math.atan2(toMe.z, toMe.x);

        for (int i = 0; i < 8; i++) {
            double a = base + Math.toRadians(i * 45.0);
            Vec3d cand = new Vec3d(
                    tpos.x + Math.cos(a) * stand,
                    y,
                    tpos.z + Math.sin(a) * stand);

            Vec3d candEye = cand.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
            RaycastContext rc = new RaycastContext(candEye, targetEye,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player);

            if (mc.world.raycast(rc).getType() == HitResult.Type.MISS) {
                return cand;
            }
        }
        return null;
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
