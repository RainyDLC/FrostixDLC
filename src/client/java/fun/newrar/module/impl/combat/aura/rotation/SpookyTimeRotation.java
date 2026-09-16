package fun.newrar.module.impl.combat.aura.rotation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import fun.newrar.manager.rotation.Rotation;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.aura.RotationAura;
import fun.newrar.utils.aura.LagCompensation;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.aura.UBoxPoints;
import fun.newrar.utils.math.MathUtil;

public class SpookyTimeRotation implements RotationAura {
    public static final SpookyTimeRotation INSTANCE = new SpookyTimeRotation();

    private static final float SMOOTHBACK_DONE_DEGREES = 1.0F;
    private static final long SMOOTHBACK_MAX_MS = 1500L;

    // Сглаженные текущие скорости наведения (строго в безопасном коридоре SpookyAC: макс yaw 27.5, макс pitch 8.8)
    private float currentSpeedYaw = 24.0F;
    private float currentSpeedPitch = 6.5F;

    private float slowPitchTicks = 1.0F;
    private float slowYawTicks = 1.0F;

    private long smoothbackShakeStartMs = -1L;
    private boolean hadTarget;
    private boolean smoothbackActive;

    private static int heldTargetId = -1;

    private static final String SUB_MODE = "Спуки 1.21";

    public SpookyTimeRotation() {
    }

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) {
            return;
        }

        smoothbackShakeStartMs = -1L;
        hadTarget = true;
        smoothbackActive = false;

        String subMode = aura.spookyMode != null ? aura.spookyMode.getValue() : "1.21";

        if (heldTargetId != target.getId()) {
            heldTargetId = target.getId();
            currentSpeedYaw = 24.0F;
            currentSpeedPitch = 6.5F;
            slowPitchTicks = 1.0F;
            slowYawTicks = 1.0F;
        }

        Box hitbox = target.getBoundingBox();
        Rotation currentAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        // Точка прицеливания: оптимальная видимая точка хитбокса
        Vec3d aimPoint = getAimPoint(target, hitbox);
        Vec3d dir = aimPoint.subtract(mc.player.getEyePos());

        double dirDistXZ = Math.hypot(dir.x, dir.z);
        float targetYaw = dirDistXZ < 0.05 ? currentAngle.getYaw() : (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.max(0.05, dirDistXZ))), -89.5F, 89.5F);
        Rotation targetAngle = new Rotation(targetYaw, targetPitch);

        // Флаг готовности удара: кулдаун заряжен, либо крит в падении
        boolean hitNow = canAttack || UAttack.chargeReadyIn(1);

        Rotation nextAngle = switch (subMode) {
            case "Дуэли", "Спуки-дуэли" -> processSpookyDuels(currentAngle, targetAngle, target, aimPoint, hitNow);
            case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, hitNow);
            default -> processSpooky121(currentAngle, targetAngle, aura, target, hitbox, hitNow);
        };

        // Передаем контролируемые безопасные скорости в RotationProcess для GCD-выравнивания
        RotationProcess.update(nextAngle, currentSpeedYaw, currentSpeedPitch, 35.0F, 35.0F, 3, 1, false);
    }

    /**
     * Точка прицеливания: находит ближайшую и оптимальную точку на хитбоксе цели
     * без искажений и искусственной фиксации по высоте вблизи.
     */
    private static Vec3d getAimPoint(LivingEntity target, Box hitbox) {
        Vec3d reachable = UBoxPoints.getBestVector3dOnEntityBox(hitbox);
        if (reachable != null) {
            return reachable;
        }
        return new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.65, target.getZ());
    }

    public Rotation limitAngleChange(Rotation currentAngle, Rotation targetAngle, Vec3d vec3d, Entity entity) {
        AttackAura aura = AttackAura.get();
        if (aura == null || mc.player == null) {
            return currentAngle;
        }

        if (aura.isEnabled() && AttackAura.target != null && entity != null) {
            smoothbackShakeStartMs = -1L;
            hadTarget = true;
            smoothbackActive = false;

            String subMode = aura.spookyMode != null ? aura.spookyMode.getValue() : SUB_MODE;

            if (heldTargetId != entity.getId()) {
                heldTargetId = entity.getId();
            }

            Box hitbox = entity.getBoundingBox();
            boolean hitNow = UAttack.chargeReadyIn(1);

            return switch (subMode) {
                case "Дуэли", "Спуки-дуэли" -> (entity instanceof LivingEntity living)
                        ? processSpookyDuels(currentAngle, targetAngle, living, getAimPoint(living, hitbox), hitNow)
                        : targetAngle;
                case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, hitNow);
                default -> (entity instanceof LivingEntity living)
                        ? processSpooky121(currentAngle, targetAngle, aura, living, hitbox, hitNow)
                        : targetAngle;
            };
        }

        if (aura.isEnabled() && AttackAura.target != null) {
            hadTarget = true;
            smoothbackActive = false;
            smoothbackShakeStartMs = -1L;
            return currentAngle;
        }

        if (hadTarget) {
            hadTarget = false;
            smoothbackActive = true;
            smoothbackShakeStartMs = System.currentTimeMillis();
            heldTargetId = -1;
        }

        if (!smoothbackActive) {
            return currentAngle;
        }

        return processSmoothback(currentAngle);
    }

    /**
     * Режим 1.21: естественные плавные кривые, безопасные для SpookyAC (макс yaw 27.5°, pitch 8.8°).
     * Без заморозки pitch, что позволяет попадать в высшей/низшей точке крита без отставания.
     */
    private Rotation processSpooky121(Rotation currentAngle, Rotation targetAngle, AttackAura aura,
                                      LivingEntity entity, Box hitbox, boolean hitNow) {
        float yawDelta = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float pitchDelta = targetAngle.getPitch() - currentAngle.getPitch();

        float auraDistance = aura.attackRange.getValue();
        float distanceToTarget = (float) mc.player.getEntityPos().distanceTo(entity.getEntityPos());

        boolean hasTrace = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), auraDistance, hitbox);
        boolean isCritFalling = !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;

        float targetSpeedY;
        float targetSpeedP;

        if (hitNow || isCritFalling) {
            // В момент удара или падения для крита - верхняя граница безопасной скорости SpookyAC (yaw < 28, pitch < 9)
            targetSpeedY = MathUtil.random(25.0F, 27.5F);
            targetSpeedP = MathUtil.random(7.5F, 8.8F);
            slowYawTicks = 1.0F;
            slowPitchTicks = 1.0F;
        } else if (hasTrace) {
            // Сопровождение цели вне удара
            slowYawTicks = MathHelper.lerp(0.3F, slowYawTicks, 0.65F);
            slowPitchTicks = MathHelper.lerp(0.3F, slowPitchTicks, 0.60F);
            targetSpeedY = MathUtil.random(20.0F, 24.0F) * slowYawTicks;
            targetSpeedP = MathUtil.random(6.0F, 7.5F) * slowPitchTicks;
        } else {
            // Доворот к цели
            slowYawTicks = MathHelper.lerp(0.25F, slowYawTicks, 1.0F);
            slowPitchTicks = MathHelper.lerp(0.25F, slowPitchTicks, 1.0F);
            targetSpeedY = MathUtil.random(22.0F, 26.0F) * slowYawTicks;
            targetSpeedP = MathUtil.random(6.5F, 8.0F) * slowPitchTicks;
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        // Человекоподобный микро-джиттер (обязателен для обхода эвристик SpookyAC)
        float closeDamp = MathHelper.clamp(distanceToTarget / 1.5F, 0.2F, 1.0F);
        float jitterYaw = MathUtil.randomGaussian(-0.12F, 0.12F) * closeDamp;
        float jitterPitch = MathUtil.randomGaussian(-0.08F, 0.08F) * closeDamp;

        float clampedYaw = MathHelper.clamp(yawDelta, -currentSpeedYaw, currentSpeedYaw) + jitterYaw;
        float clampedPitch = MathHelper.clamp(pitchDelta, -currentSpeedPitch, currentSpeedPitch) + jitterPitch;

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    /**
     * Режим Дуэли:
     * - Плавное опережение цели в безопасных лимитах SpookyAC (yaw 24-27°, pitch 7-8.5°).
     * - Без pitch-hold'а и без искусственного зажимания pitch до 1.2°.
     */
    private Rotation processSpookyDuels(Rotation currentAngle, Rotation targetAngle, LivingEntity entity, Vec3d baseAim, boolean hitNow) {
        Vec3d targetVel = entity.getVelocity();
        double distXZ = Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());

        // Мягкое опережение движения цели
        double leadFactor = distXZ < 0.8 ? 0.0 : MathHelper.clamp(distXZ / 3.0, 0.05, 0.25);
        Vec3d predictedAim = baseAim.add(targetVel.x * leadFactor, targetVel.y * (leadFactor * 0.3), targetVel.z * leadFactor);
        Vec3d dir = predictedAim.subtract(mc.player.getEyePos());

        float predYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float predPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -89.5F, 89.5F);

        float deltaYaw = MathHelper.wrapDegrees(predYaw - currentAngle.getYaw());
        float deltaPitch = predPitch - currentAngle.getPitch();

        float range = AttackAura.get() != null ? AttackAura.get().attackRange.getValue() : 3.0F;
        boolean onTarget = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, entity.getBoundingBox());
        boolean isCritFalling = !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;

        float targetSpeedY;
        float targetSpeedP;

        if (hitNow || isCritFalling) {
            targetSpeedY = MathUtil.random(24.5F, 27.0F);
            targetSpeedP = MathUtil.random(7.2F, 8.5F);
        } else if (onTarget) {
            targetSpeedY = MathUtil.random(14.0F, 18.0F);
            targetSpeedP = MathUtil.random(4.5F, 6.0F);
        } else {
            targetSpeedY = MathUtil.random(21.0F, 25.0F);
            targetSpeedP = MathUtil.random(6.0F, 7.5F);
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        float closeDamp = MathHelper.clamp((float) (distXZ / 1.5), 0.2F, 1.0F);
        float jitterYaw = MathUtil.randomGaussian(-0.12F, 0.12F) * closeDamp;
        float jitterPitch = MathUtil.randomGaussian(-0.08F, 0.08F) * closeDamp;

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw) + jitterYaw;
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch) + jitterPitch;

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    private Rotation processSpooky116(Rotation currentAngle, Rotation targetAngle, boolean hitNow) {
        float deltaYaw = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float deltaPitch = targetAngle.getPitch() - currentAngle.getPitch();

        boolean isCritFalling = !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;

        float targetSpeedY;
        float targetSpeedP;

        if (hitNow || isCritFalling) {
            targetSpeedY = MathUtil.random(25.0F, 27.5F);
            targetSpeedP = MathUtil.random(7.5F, 8.8F);
        } else {
            targetSpeedY = MathUtil.random(20.0F, 24.0F);
            targetSpeedP = MathUtil.random(5.5F, 7.0F);
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        float jitterX = MathUtil.randomGaussian(-0.10F, 0.10F);
        float jitterY = MathUtil.randomGaussian(-0.06F, 0.06F);

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw) + jitterX;
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch) + jitterY;

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    public static boolean aimsAtBox(float yaw, float pitch, float range, Box box) {
        if (mc.player == null || box == null) {
            return false;
        }
        boolean ignoreBlocks = AttackAura.get() != null && AttackAura.get().others.getValue("Бить через блоки");
        return LagCompensation.rayHitsBox(box, yaw, pitch, range, ignoreBlocks);
    }

    public Rotation processSmoothback(Rotation currentAngle) {
        Rotation playerViewAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        float deltaYaw = MathHelper.wrapDegrees(playerViewAngle.getYaw() - currentAngle.getYaw());
        float deltaPitch = playerViewAngle.getPitch() - currentAngle.getPitch();
        float difference = Math.max((float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch)), 1.0E-4F);

        boolean timedOut = System.currentTimeMillis() - smoothbackShakeStartMs > SMOOTHBACK_MAX_MS;
        if (difference <= SMOOTHBACK_DONE_DEGREES || timedOut) {
            smoothbackActive = false;
            smoothbackShakeStartMs = -1L;
            return playerViewAngle;
        }

        boolean cooldownActive = !UAttack.getCooldownTimer().finished(750);
        float straightLineYaw = Math.abs(deltaYaw / difference) * (cooldownActive ? 0.0F : 24.0F);
        float straightLinePitch = Math.abs(deltaPitch / difference) * (cooldownActive ? 0.0F : 12.0F);

        return new Rotation(
                currentAngle.getYaw() + MathHelper.clamp(deltaYaw, -straightLineYaw, straightLineYaw),
                currentAngle.getPitch() + MathHelper.clamp(deltaPitch, -straightLinePitch, straightLinePitch)
        );
    }

    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    public static class SPAngle extends SpookyTimeRotation {
        public static final SPAngle INSTANCE = new SPAngle();
    }
}
