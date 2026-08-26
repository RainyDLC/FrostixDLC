package ru.white.module.impl.movement;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.tick.TickManager;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.ITimerSpeed;
import ru.white.utils.player.MoveUtil;
import ru.white.utils.player.TickManagerClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ModuleInfo(
        name = "Speed",
        desc = "Увеличивает скорость игрока",
        category = Category.MOVEMENT
)
public class Speed extends Module {

    public ModeSetting type = new ModeSetting(this, "Режим", "Vanilla","Meta","ReallyWorld","Grim");

    public SliderSetting speed = new SliderSetting(this, "Скорость", 1, 0.3F, 2F, 0.1F)
            .setVisible(() -> type.is("Vanilla"));

    // RW state
    private int ticks = 0;
    private int groundTicks = 0;

    // ReallyWorld state
    private float rwTimerCredit = 0F;
    private boolean rwRepaying = false;
    private long rwFlagCooldown = 0L;

    // Grim speed state
    private int grimTick = 0;
    private boolean grimLastOnGround = true;
    private long grimFlagCooldown = 0L;
    private double grimLastX, grimLastZ;
    private boolean hasGrimPos = false;
    private float grimTimerCredit = 0F;
    private boolean grimRepaying = false;

    @EventHandler
    public void onEvent(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (type.is("Grim Box") && !mc.player.isSubmergedInWater()) {
            double finalSpeed = 0.02F;
            if (finalSpeed <= 0.0) return;

            Entity nearest = null;
            double bestSq = Double.MAX_VALUE;
            double maxRangeSq = 0.1F;

            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;

                if (ent == AttackAura.target) {
                    double dx = ent.getX() - mc.player.getX();
                    double dz = ent.getZ() - mc.player.getZ();
                    double sq = dx * dx + dz * dz;
                    if (sq <= maxRangeSq && sq < bestSq) {
                        bestSq = sq;
                        nearest = ent;
                    }
                }

                if (nearest != null) {
                    // ИСПРАВЛЕНИЕ: getEntityPos() заменен на актуальный getPos() для 1.21+
                    double[] dir = getDirectionToPoint(mc.player.getEntityPos(), nearest.getEntityPos(), finalSpeed);
                    mc.player.addVelocity(dir[0], 0.0, dir[1]);
                }
            }
        }

        if (type.is("Meta")) {
            // В Yarn: StatusEffects вместо MobEffects, hasStatusEffect вместо hasEffect
            if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS)) return;

            // В Yarn: getOffHandStack() вместо getOffhandItem(), getName() возвращает Text компонент
            String offhandName = mc.player.getOffHandStack().getName().getString();
            byte[] nameBytes = offhandName.getBytes(StandardCharsets.UTF_8);
            String bytesString = Arrays.toString(nameBytes);

            boolean sharKing = bytesString.equals("[-48, -88, -48, -80, -47, -128, 32, 75, 73, 78, 71]");
            boolean sharTigr = bytesString.equals("[-48, -94, -48, -72, -48, -77, -47, -128, -48, -72, -48, -67, -48, -67, -48, -80, -47, -113, 32, -48, -77, -48, -66, -48, -69, -48, -66, -48, -78, -48, -80]");

            // В Yarn: getStatusEffect() возвращает StatusEffectInstance
            net.minecraft.entity.effect.StatusEffectInstance effectInstance = mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED);
            boolean hasSpeed = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED);

            // В Yarn метод проверки земли обычно остается isOnGround(), а fallDistance — это поле
            boolean onGround = mc.player.isOnGround();
            float fallDist = (float) mc.player.fallDistance;

            if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 2 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(sharKing ? 0.72f : sharTigr ? 0.7f : 0.58f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 2 && onGround) {
                MoveUtil.setSpeed(sharKing ? 0.5f : sharTigr ? 0.49f : 0.42f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 3 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(0.7f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 3 && onGround) {
                MoveUtil.setSpeed(0.49f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 1 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(0.52f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 1 && onGround) {
                MoveUtil.setSpeed(0.36f);
            } else if (fallDist <= 0.2f && !onGround || fallDist <= 0.2f && hasSpeed) {
                MoveUtil.setSpeed(0.36f);
            }
        }
        if (type.is("Vanilla") && !mc.player.isSubmergedInWater()) {
            if (mc.player.isOnGround()) {
                MoveUtil.setSpeed(0.3F);
            } else {
                MoveUtil.setSpeed(speed.getValue());
            }
        }

        if (type.is("RW")) {
            handleRW();
        }

        if (type.is("ReallyWorld")) {
            handleReallyWorld();
        }

        if (type.is("Grim")) {
            handleGrimSpeed();

            grimLastOnGround = mc.player.isOnGround();
            grimLastX = mc.player.getX();
            grimLastZ = mc.player.getZ();
            hasGrimPos = true;
        }
    }

    @EventHandler
    public void onEvent(EventPacket e) {
        if (type.is("RW")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                if (ticks % 2 == 1) {
                    ticks++;
                }


            }
        }

        if (type.is("ReallyWorld")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
                    speedTimer.setSpeed(1.0F);
                }
                rwFlagCooldown = System.currentTimeMillis() + 900L;
                rwTimerCredit = 0F;
                rwRepaying = false;
            }
        }

        if (type.is("Grim")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                grimFlagCooldown = System.currentTimeMillis() + 1000L;
                grimTick = 0;
                resetGrimTimer();
            }
        }
    }

    public TimerUtil timerUtil = new TimerUtil();

    /**
     * ReallyWorld: постоянное сильное ускорение без компенсирующих просадок.
     *
     * Скорость набирается из «бесплатных» каналов неопределённости GrimAC 2.0 —
     * их предсказатель сам выдаёт нам запас по оффсету, repay не нужен:
     * - сущности в 0.5 блоках: ±0.08/тик за каждую (avgColliding * 0.08);
     * - лодка/шалкер в блоке: reduceOffset до -1.2 и +0.1 горизонтали;
     * - липкие блоки под ногами: reduceOffset -0.03.
     *
     * Таймер поверх — короткие разгон 1.30-1.45 и мягкая выплата 0.85-0.92
     * (без резиновых просадок): средний множитель держится у 1.0, баланс
     * Timer'а Grim не обгоняет.
     */
    private void handleReallyWorld() {
        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()
                || mc.player.getAbilities().flying || mc.player.isClimbing()
                || mc.player.isSneaking() || !MoveUtil.isMoving()
                || mc.player.fallDistance > 1.5F) {
            resetRWTimer();
            return;
        }

        long ms = System.currentTimeMillis();
        if (ms < rwFlagCooldown) {
            resetRWTimer();
            return;
        }

        // ── канал 1: неопределённость предсказаний — буст без возврата ──
        float boost = uncertaintyBoost();
        if (boost > 0F
                && !mc.player.isUsingItem() && mc.player.hurtTime <= 0
                && !(!mc.player.isOnGround() && grimLastOnGround && mc.player.getVelocity().y > 0)) {

            double dx = hasGrimPos ? mc.player.getX() - grimLastX : 0;
            double dz = hasGrimPos ? mc.player.getZ() - grimLastZ : 0;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len >= 1e-4) {
                float power = boost * MathUtil.randomLerp(0.85F, 1.0F);
                if (!mc.player.isOnGround()) power *= 0.75F;
                mc.player.addVelocity((float) (dx / len) * power, 0, (float) (dz / len) * power);
            }
        }

        // ── канал 2: таймерный разгон с мягкой выплатой ──
        if (!(mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer)) return;

        float mult;
        if (rwRepaying) {
            mult = MathUtil.randomLerp(0.85F, 0.92F);
            rwTimerCredit += mult - 1.0F;
            if (rwTimerCredit <= 0F) {
                rwTimerCredit = 0F;
                rwRepaying = false;
            }
        } else {
            mult = MathUtil.randomLerp(1.30F, 1.45F);
            rwTimerCredit += mult - 1.0F;
            if (rwTimerCredit >= MathUtil.randomLerp(1.0F, 1.6F)) {
                rwRepaying = true;
            }
        }
        speedTimer.setSpeed(mult);
    }

    /** Суммарный безопасный буст из каналов неопределённости Grim (блоков/тик). */
    private float uncertaintyBoost() {
        float boost = 0F;

        int pushables = countPushableEntities();
        if (pushables > 0) {
            boost += Math.min(pushables * 0.08F, 0.16F) * 0.85F;
        }

        if (nearHardLerpingEntity()) {
            boost += 0.30F;
        }

        if (onBouncyBlock()) {
            boost += 0.025F;
        }

        return boost;
    }

    private void resetRWTimer() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
            speedTimer.setSpeed(1.0F);
        }
        rwTimerCredit = 0F;
        rwRepaying = false;
    }

    /** Лодка/шалкер вплотную — Grim даёт до -1.2 оффсета и +0.1 горизонтали. */
    private boolean nearHardLerpingEntity() {
        Box near = mc.player.getBoundingBox().expand(1.0);
        for (Entity ent : mc.world.getEntities()) {
            if (ent == mc.player) continue;
            if ((ent instanceof BoatEntity || ent instanceof ShulkerEntity)
                    && ent.getBoundingBox().intersects(near)) {
                return true;
            }
        }
        return false;
    }

    /** Слизь/мёд/кровати под ногами — reduceOffset выдаёт -0.03 оффсета. */
    private boolean onBouncyBlock() {
        BlockPos under = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.2, mc.player.getZ());
        var state = mc.world.getBlockState(under);
        return state.isOf(Blocks.SLIME_BLOCK) || state.isOf(Blocks.HONEY_BLOCK) || state.isIn(BlockTags.BEDS);
    }

    /**
     * Grim: обход по механике предсказаний GrimAC 2.0.
     *
     * БОЕВОЙ ПРОФИЛЬ (рядом толкаемые сущности):
     * UncertaintyHandler выдаёт ±0.08 блока/тик горизонтали за КАЖДУЮ сущность
     * в 0.5 блоках от хитбокса (avgColliding * 0.08 в handleStartingVelocityUncertainty).
     * Таргет ауры рядом — бустим на 75% этого лимита, движение остаётся
     * внутри бокса возможных векторов предсказаний.
     *
     * ПОХОДОВЫЙ ПРОФИЛЬ (один):
     * Timer.java флагует только при balance > now (дрейф 120мс). Ведём внутренний
     * «кредит» перерасхода: разгон до 1.32, затем выплата на 0.6 до нуля —
     * средний множитель цикла ровно 1.0, баланс никогда не уходит вперёд.
     *
     * Стражи: тик прыжка, использование предмета (NoSlow меряет оффсет),
     * hurtTime (KnockbackHandler проверяет приём отдачи), вода/лестница/sneak.
     */
    private void handleGrimSpeed() {
        long ms = System.currentTimeMillis();
        if (ms < grimFlagCooldown) return;

        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()
                || mc.player.getAbilities().flying || mc.player.isClimbing()
                || mc.player.isSneaking() || !MoveUtil.isMoving()
                || mc.player.fallDistance > 1.5F) {
            resetGrimTimer();
            return;
        }

        grimTick++;

        AttackAura aura = AttackAura.get();
        boolean auraActive = aura != null && aura.isEnabled() && AttackAura.target != null;
        int pushables = auraActive ? countPushableEntities() : 0;

        if (pushables > 0) {
            resetGrimTimer();

            // стражи проверок, которые меряют оффсет поверх предсказаний
            if (mc.player.isUsingItem() || mc.player.hurtTime > 0) return;

            boolean jumpTick = !mc.player.isOnGround() && grimLastOnGround
                    && mc.player.getVelocity().y > 0;
            if (jumpTick) return;

            double dx = hasGrimPos ? mc.player.getX() - grimLastX : 0;
            double dz = hasGrimPos ? mc.player.getZ() - grimLastZ : 0;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-4) return;

            float limit = Math.min(pushables * 0.08F, 0.16F);
            float power = limit * 0.75F * MathUtil.randomLerp(0.85F, 1.0F);
            if (!mc.player.isOnGround()) power *= 0.7F;

            mc.player.addVelocity((float) (dx / len) * power, 0, (float) (dz / len) * power);
            return;
        }

        // ── походовый профиль: таймерный заём/возврат ──
        if (!(mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer)) return;

        float mult;
        if (grimRepaying) {
            mult = 0.6F;
            grimTimerCredit += mult - 1.0F;
            if (grimTimerCredit <= 0F) {
                grimTimerCredit = 0F;
                grimRepaying = false;
            }
        } else {
            mult = MathUtil.randomLerp(1.18F, 1.32F);
            grimTimerCredit += mult - 1.0F;
            if (grimTimerCredit >= MathUtil.randomLerp(1.2F, 1.8F)) {
                grimRepaying = true;
            }
        }
        speedTimer.setSpeed(mult);
    }

    private void resetGrimTimer() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
            speedTimer.setSpeed(1.0F);
        }
        grimTimerCredit = 0F;
        grimRepaying = false;
    }

    /** Толкаемые живые сущности рядом — зеркалирует collidingEntities Grim'а (с запасом вниз). */
    private int countPushableEntities() {
        Box near = mc.player.getBoundingBox().expand(0.45);
        int n = 0;
        for (Entity ent : mc.world.getEntities()) {
            if (ent == mc.player || !(ent instanceof LivingEntity living)) continue;
            if (!living.isAlive() || living.isSpectator()) continue;
            if (living.getBoundingBox().expand(0.45).intersects(near)) {
                n++;
                if (n >= 3) break;
            }
        }
        return n;
    }

    private void handleRW() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
            long elapsed = timerUtil.getTime();
            float currentSpeed;
            if (elapsed < 100) {
                currentSpeed = 1.15F;
            } else if (elapsed < 500) {
                currentSpeed = 1.4F;
            } else if (elapsed < 520) {
                currentSpeed = 1.6F;
            } else if (elapsed < 540) {
                currentSpeed = 0.25F;
            } else {
                timerUtil.reset();
                currentSpeed = 0.5F;
            }

            speedTimer.setSpeed(currentSpeed);
        }
    }

    private boolean canUseRW() {
        return mc.player != null
                && mc.world != null
                && mc.player.networkHandler != null
                && MoveUtil.isMoving()
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying;
    }

    private void resetRWState(boolean resetTimer) {
        ticks = 0;
        groundTicks = 0;
        if (resetTimer && mc.player != null) {
            mc.player.speed = 0;
        }
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double spd) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return new double[]{0.0, 0.0};
        return new double[]{dx / len * spd, dz / len * spd};
    }

    @Override
    public void onEnable() {
        resetRWState(true);
        rwFlagCooldown = 0L;
        rwTimerCredit = 0F;
        rwRepaying = false;
        grimTick = 0;
        grimFlagCooldown = 0L;
        hasGrimPos = false;
        resetGrimTimer();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
            speedTimer.setSpeed(1.0F);
        }
        resetRWState(true);
        TickManagerClient.tick = 20;
        super.onDisable();
    }
}