package ru.white.module.impl.combat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
 * TpAura: HvH телепорт-удар с минимальным следом.
 *
 * Минимализм сигнатуры — главный обход:
 * - крит берётся ЧЕСТНЫМ прыжком (сервер видит настоящую физику падения);
 * - телепорт строго ГОРИЗОНТАЛЬНЫЙ на текущей высоте: fallDistance сервера
 *   не сбрасывается, крит сохраняется;
 * - весь удар — один тик: [пакет позиции у цели] -> [удар] -> [пакет назад].
 *   Всего 2 нестандартных пакета, дельта по умолчанию <= 6 блоков
 *   (порог ванильного "moved too quickly" — 10);
 * - точка высадки с чистой линией огня (8 углов, рейкаст);
 * - удар только при hurtTime == 0 цели и полном кулдауне.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Teleport strike: minimal packets, always crit", category = Category.COMBAT)
public class TpAura extends Module {

    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 6.0F, 3.0F, 50.0F, 0.5F);
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

        LivingEntity target = findTarget();
        if (target == null) return;

        double dist = mc.player.getEyePos().distanceTo(target.getEntityPos());
        if (dist > tpRadius.getValue() || dist <= atkRange) return;

        // крит: только в честном падении (сервер видит настоящую физику)
        if (critOnly.getValue()) {
            if (mc.player.isOnGround()) {
                if (autoJump.getValue()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
                }
                return;
            }
            if (mc.player.getVelocity().y >= 0) return; // ждём падение
        }

        if (target.hurtTime > 0) return;
        float[] ranges = aura.getRanges();
        if (!UAttack.shouldAttack(target, false, false, false, 0L, ranges)) return;

        // точка высадки на ТЕКУЩЕЙ высоте (горизонтальный рывок, крит не сбрасывается)
        Vec3d landing = findLanding(target, standOff.getValue());
        if (landing == null) return;

        strike(target, landing);
        nextStrike = ms + cooldown.getValue().longValue();
    }

    /**
     * Весь удар в одном тике: пакет позиции у цели -> удар -> пакет назад.
     * Порядок пакетов гарантирует, что сервер считает удар из точки высадки.
     */
    private void strike(LivingEntity target, Vec3d landing) {
        // 1) рывок к цели (горизонтальный, Y текущий)
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
        Vec3d back = mc.player.getEntityPos();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                back, mc.player.isOnGround(), false));
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

    @Override
    public void onDisable() {
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
