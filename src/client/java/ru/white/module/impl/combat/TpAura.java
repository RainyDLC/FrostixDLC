package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.UAttack;

/**
 * TpAura: HvH телепорт-удар без промахов, всегда критом.
 *
 * Хореография пакетов позиции:
 * 1. Телепорт ВВЕРХ над точкой высадки (+1.2) — сервер принимает позицию.
 * 2. Сброс вниз (+0.4) — сервер видит нисходящее движение,
 *    его fallDistance становится > 0, мы в воздухе.
 * 3. Удар следующим тиком: серверные условия крита выполнены
 *    (в воздухе, fallDistance > 0) — крит гарантирован.
 *
 * Анти-промах:
 * - точка высадки выбирается перебором 8 углов вокруг цели с проверкой
 *   линии огня рейкастом (не бьём через стены);
 * - удар только при hurtTime == 0 цели (i-frames = гарантированный нулевой урон);
 * - полный кулдаун атаки (UAttack.msCooldownReached).
 * Булава: высота подъёма = высота смэша, остальное одинаково.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Teleport strike, always crit, no miss", category = Category.COMBAT)
public class TpAura extends Module {

    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 15.0F, 3.0F, 50.0F, 0.5F);
    public SliderSetting standOff = new SliderSetting(this, "Дистанция удара", 2.2F, 1.5F, 3.0F, 0.1F);
    public SliderSetting cooldown = new SliderSetting(this, "Перезарядка", 800F, 250F, 2000F, 50F);
    public SliderSetting maceHeight = new SliderSetting(this, "Высота смэша булавы", 6.0F, 2.0F, 15.0F, 0.5F);

    public BooleanSetting critOnly = new BooleanSetting(this, "Только криты", true);

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_UP = 1;
    private static final int PHASE_DOWN = 2;

    private int phase = PHASE_IDLE;
    private Vec3d returnPos = null;
    private Vec3d strikePos = null;
    private LivingEntity strikeTarget = null;
    private long phaseDeadline = 0L;
    private long nextStrike = 0L;
    private int phaseTicks = 0;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        switch (phase) {
            case PHASE_UP -> phaseUp();
            case PHASE_DOWN -> phaseDown();
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
        boolean highArc = mace || critOnly.getValue();
        float upHeight = mace ? maceHeight.getValue() : (critOnly.getValue() ? 1.2F : 0.4F);

        // точка высадки с линией огня: 8 углов вокруг цели, старт со своей стороны
        Vec3d landing = findLanding(target, standOff.getValue());
        if (landing == null) return;

        returnPos = mc.player.getEntityPos();
        strikeTarget = target;
        strikePos = new Vec3d(landing.x, target.getY() + 0.4, landing.z);
        phaseTicks = 0;

        mc.player.setPosition(landing.x, target.getY() + upHeight, landing.z);
        mc.player.setVelocity(Vec3d.ZERO);
        phase = PHASE_UP;
        phaseDeadline = ms + 600L;
    }

    private void phaseUp() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            goBack();
            return;
        }
        // 2 тика: естественный пакет позиции уходит, сервер принимает верхнюю точку
        if (++phaseTicks < 2) return;

        mc.player.setPosition(strikePos.x, strikePos.y, strikePos.z);
        phase = PHASE_DOWN;
        phaseTicks = 0;
        phaseDeadline = System.currentTimeMillis() + 600L;
    }

    private void phaseDown() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            goBack();
            return;
        }
        // 2 тика: сервер видит нисходящее движение -> его fallDistance > 0
        if (++phaseTicks < 2) return;

        // анти-промах: не бьём в i-frames цели
        if (t.hurtTime > 0) {
            if (System.currentTimeMillis() > phaseDeadline) goBack();
            return;
        }
        if (!UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())) {
            if (System.currentTimeMillis() > phaseDeadline) goBack();
            return;
        }

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
        strikePos = null;
        strikeTarget = null;
        phase = PHASE_IDLE;
        phaseTicks = 0;
        nextStrike = System.currentTimeMillis() + cooldown.getValue().longValue();
    }

    /**
     * Точка высадки: перебор 8 углов вокруг цели (старт со своей стороны),
     * берём первую с чистой линией огня от глаз до корпуса цели.
     */
    private Vec3d findLanding(LivingEntity target, double stand) {
        Vec3d tpos = target.getEntityPos();
        Vec3d targetEye = tpos.add(0, target.getHeight() * 0.9, 0);

        Vec3d toMe = mc.player.getEntityPos().subtract(tpos);
        double base = Math.atan2(toMe.z, toMe.x);

        for (int i = 0; i < 8; i++) {
            double a = base + Math.toRadians(i * 45.0);
            Vec3d cand = new Vec3d(
                    tpos.x + Math.cos(a) * stand,
                    target.getY(),
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

    @Override
    public void onDisable() {
        if (phase != PHASE_IDLE) {
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
