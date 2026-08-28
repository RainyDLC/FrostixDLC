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

@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "TpAura", desc = "Teleport strike at any range via hops, always crit", category = Category.COMBAT)
public class TpAura extends Module {
    public SliderSetting tpRadius = new SliderSetting(this, "Радиус телепорта", 15.0F, 3.0F, 50.0F, 0.5F);
    public SliderSetting standOff = new SliderSetting(this, "Дистанция удара", 2.2F, 1.5F, 3.0F, 0.1F);
    public SliderSetting cooldown = new SliderSetting(this, "Перезарядка", 800F, 250F, 2000F, 50F);
    public SliderSetting hopSpeed = new SliderSetting(this, "Скорость рывка", 9.0F, 3.0F, 15.0F, 0.5F);
    public SliderSetting maceHeight = new SliderSetting(this, "Высота смэша булавы", 6.0F, 2.0F, 15.0F, 0.5F);

    public BooleanSetting throughWalls = new BooleanSetting(this, "Через стены", true);
    public BooleanSetting critOnly = new BooleanSetting(this, "Только криты", true);
    public BooleanSetting autoJump = new BooleanSetting(this, "Прыжок для крита", true)
            .setVisible(() -> critOnly.getValue());

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_HOP_OUT = 1;
    private static final int PHASE_MACE_UP = 2;
    private static final int PHASE_MACE_DOWN = 3;
    private static final int PHASE_HOP_BACK = 4;

    private int phase = PHASE_IDLE;
    private Vec3d returnPos = null;
    private Vec3d strikePos = null;
    private Vec3d upPos = null;
    private Vec3d dropPos = null;
    private LivingEntity strikeTarget = null;
    private long phaseDeadline = 0L;
    private long nextStrike = 0L;
    private int phaseTicks = 0;

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        switch (phase) {
            case PHASE_HOP_OUT -> hopTo(strikePos, () -> afterHopOut());
            case PHASE_MACE_UP -> phaseMaceUp();
            case PHASE_MACE_DOWN -> phaseMaceDown();
            case PHASE_HOP_BACK -> hopTo(returnPos, () -> finish());
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

        if (!mace && critOnly.getValue()) {
            if (mc.player.isOnGround()) {
                if (autoJump.getValue()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
                }
                return;
            }
            if (mc.player.getVelocity().y >= 0) return;
        }

        Vec3d landing = throughWalls.getValue()
                ? landingSimple(target, standOff.getValue())
                : findLanding(target, standOff.getValue());
        if (landing == null) return;

        returnPos = mc.player.getEntityPos();
        strikeTarget = target;
        strikePos = landing;
        phaseTicks = 0;

        if (mace) {
            dropPos = new Vec3d(landing.x, target.getY() + 0.4, landing.z);
            phase = PHASE_HOP_OUT;
        } else {
            phase = PHASE_HOP_OUT;
        }
        phaseDeadline = ms + 3000L;
    }

    private void afterHopOut() {
        boolean mace = mc.player.getMainHandStack().getItem() == Items.MACE;
        if (!mace) {
            strikeNow();
            return;
        }
        upPos = new Vec3d(strikePos.x, strikeTarget.getY() + maceHeight.getValue(), strikePos.z);
        dropPos = new Vec3d(strikePos.x, strikeTarget.getY() + 1.0, strikePos.z);
        phase = PHASE_MACE_UP;
        phaseTicks = 0;
        phaseDeadline = System.currentTimeMillis() + 2000L;
    }

    private void phaseMaceUp() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            abort();
            return;
        }
        pin(upPos);
        if (++phaseTicks < 2) return;

        phase = PHASE_MACE_DOWN;
        phaseTicks = 0;
    }

    private void phaseMaceDown() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            abort();
            return;
        }
        pin(dropPos);
        if (++phaseTicks < 2) return;

        if (t.hurtTime > 0 || !UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())) {
            if (System.currentTimeMillis() > phaseDeadline) {
                abort();
            }
            return;
        }

        hitTarget(t);

        mc.player.setPosition(dropPos.x, dropPos.y + 1.2, dropPos.z);
        mc.player.setVelocity(Vec3d.ZERO);
        phase = PHASE_HOP_BACK;
        phaseTicks = 0;
    }

    private void pin(Vec3d pos) {
        mc.player.setPosition(pos.x, pos.y, pos.z);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0;
    }

    private void hitTarget(LivingEntity t) {
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
                Hand.MAIN_HAND);
    }

    private void strikeNow() {
        LivingEntity t = strikeTarget;
        if (!valid(t)) {
            phase = PHASE_HOP_BACK;
            phaseTicks = 0;
            return;
        }
        if (t.hurtTime > 0 || !UAttack.shouldAttack(t, false, false, false, 0L, AttackAura.get().getRanges())) {
            if (System.currentTimeMillis() > phaseDeadline) {
                phase = PHASE_HOP_BACK;
                phaseTicks = 0;
            }
            return;
        }

        hitTarget(t);

        phase = PHASE_HOP_BACK;
        phaseTicks = 0;
    }

    private void hopTo(Vec3d target, Runnable onArrive) {
        if (!valid(strikeTarget) && phase == PHASE_HOP_OUT) {
            abort();
            return;
        }
        if (mc.player == null) return;

        Vec3d p = mc.player.getEntityPos();
        double dx = target.x - p.x;
        double dz = target.z - p.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double step = hopSpeed.getValue();
        if (horizontal <= step) {
            mc.player.setPosition(target.x, p.y, target.z);
            mc.player.setVelocity(Vec3d.ZERO);
            onArrive.run();
            return;
        }

        double k = step / horizontal;
        mc.player.setPosition(p.x + dx * k, p.y, p.z + dz * k);

        mc.player.setVelocity(Vec3d.ZERO);
    }

    private boolean valid(LivingEntity t) {
        return t != null && t.isAlive() && mc.player != null;
    }

    private void finish() {
        if (returnPos != null && mc.player != null) {
            mc.player.setPosition(returnPos.x, returnPos.y, returnPos.z);
        }
        if (mc.player != null) mc.player.fallDistance = 0;
        returnPos = null;
        strikePos = null;
        dropPos = null;
        strikeTarget = null;
        phase = PHASE_IDLE;
        phaseTicks = 0;
        nextStrike = System.currentTimeMillis() + cooldown.getValue().longValue();
    }

    private void abort() {
        phase = PHASE_HOP_BACK;
        phaseTicks = 0;
    }

    @Override
    public void onDisable() {
        if (phase != PHASE_IDLE && mc.player != null && returnPos != null) {
            mc.player.setPosition(returnPos.x, returnPos.y, returnPos.z);
        }
        if (mc.player != null) mc.player.fallDistance = 0;
        returnPos = null;
        strikePos = null;
        dropPos = null;
        strikeTarget = null;
        phase = PHASE_IDLE;
        super.onDisable();
    }

    private Vec3d landingSimple(LivingEntity target, double stand) {
        Vec3d tpos = target.getEntityPos();
        double y = mc.player.getY();

        Vec3d toMe = mc.player.getEntityPos().subtract(tpos);
        double a = Math.atan2(toMe.z, toMe.x);
        return new Vec3d(
                tpos.x + Math.cos(a) * stand,
                y,
                tpos.z + Math.sin(a) * stand);
    }

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
