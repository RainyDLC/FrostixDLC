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
import fun.newrar.utils.aura.ServerReach;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.aura.UBoxPoints;
import fun.newrar.utils.math.MathUtil;

public class SpookyTimeRotation implements RotationAura {
    public static final SpookyTimeRotation INSTANCE = new SpookyTimeRotation();

    private static final float SMOOTHBACK_DONE_DEGREES = 1.0F;
    private static final long SMOOTHBACK_MAX_MS = 1500L;

    private float currentSpeedYaw = 24.0F;
    private float currentSpeedPitch = 6.5F;

    private float slowPitchTicks = 1.0F;
    private float slowYawTicks = 1.0F;

    private long smoothbackShakeStartMs = -1L;
    private boolean hadTarget;
    private boolean smoothbackActive;

    private static int heldTargetId = -1;
    private double smoothedTargetVelX = 0.0;
    private double smoothedTargetVelY = 0.0;
    private double smoothedTargetVelZ = 0.0;

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

        Box hitbox = ServerReach.attackBox(target);
        Rotation currentAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        Vec3d aimPoint = getAimPoint(target, hitbox);
        Vec3d dir = aimPoint.subtract(mc.player.getEyePos());

        double dirDistXZ = Math.hypot(dir.x, dir.z);
        float targetYaw = dirDistXZ < 0.05 ? currentAngle.getYaw() : (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.max(0.05, dirDistXZ))), -89.5F, 89.5F);
        Rotation targetAngle = new Rotation(targetYaw, targetPitch);

        boolean hitNow = canAttack || UAttack.chargeReadyIn(1);

        Rotation nextAngle = switch (subMode) {
            case "Дуэли", "Спуки-дуэли" -> processSpookyDuels(currentAngle, targetAngle, target, aimPoint, hitNow);
            case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, hitNow);
            default -> processSpooky121(currentAngle, targetAngle, aura, target, hitbox, hitNow);
        };

        RotationProcess.update(nextAngle, currentSpeedYaw, currentSpeedPitch, 35.0F, 35.0F, 3, 1, false);
    }

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
            targetSpeedY = MathUtil.random(25.0F, 27.5F);
            targetSpeedP = MathUtil.random(7.5F, 8.8F);
            slowYawTicks = 1.0F;
            slowPitchTicks = 1.0F;
        } else if (hasTrace) {
            slowYawTicks = MathHelper.lerp(0.3F, slowYawTicks, 0.65F);
            slowPitchTicks = MathHelper.lerp(0.3F, slowPitchTicks, 0.60F);
            targetSpeedY = MathUtil.random(20.0F, 24.0F) * slowYawTicks;
            targetSpeedP = MathUtil.random(6.0F, 7.5F) * slowPitchTicks;
        } else {
            slowYawTicks = MathHelper.lerp(0.25F, slowYawTicks, 1.0F);
            slowPitchTicks = MathHelper.lerp(0.25F, slowPitchTicks, 1.0F);
            targetSpeedY = MathUtil.random(22.0F, 26.0F) * slowYawTicks;
            targetSpeedP = MathUtil.random(6.5F, 8.0F) * slowPitchTicks;
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

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

    private Rotation processSpookyDuels(Rotation currentAngle, Rotation targetAngle, LivingEntity entity, Vec3d baseAim, boolean hitNow) {
        Vec3d rawVel = entity.getVelocity();
        double distXZ = Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());

        smoothedTargetVelX = MathHelper.lerp(0.35, smoothedTargetVelX, rawVel.x);
        smoothedTargetVelY = MathHelper.lerp(0.30, smoothedTargetVelY, rawVel.y);
        smoothedTargetVelZ = MathHelper.lerp(0.35, smoothedTargetVelZ, rawVel.z);

        double leadFactor = distXZ < 1.0 ? 0.0 : MathHelper.clamp(distXZ / 4.5, 0.03, 0.16);
        Vec3d predictedAim = baseAim.add(
                smoothedTargetVelX * leadFactor,
                smoothedTargetVelY * (leadFactor * 0.2),
                smoothedTargetVelZ * leadFactor
        );

        float closeDamp = (float) MathHelper.clamp(distXZ / 2.0, 0.15, 1.0);
        double jitterX = MathUtil.randomGaussian(-0.03F, 0.03F) * closeDamp;
        double jitterY = MathUtil.randomGaussian(-0.02F, 0.02F) * closeDamp;
        double jitterZ = MathUtil.randomGaussian(-0.03F, 0.03F) * closeDamp;
        predictedAim = predictedAim.add(jitterX, jitterY, jitterZ);

        Vec3d dir = predictedAim.subtract(mc.player.getEyePos());
        double dirDist = Math.hypot(dir.x, dir.z);

        float predYaw = dirDist < 0.05 ? currentAngle.getYaw() : (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float predPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dir.y, Math.max(0.05, dirDist))), -89.5F, 89.5F);

        float deltaYaw = MathHelper.wrapDegrees(predYaw - currentAngle.getYaw());
        float deltaPitch = predPitch - currentAngle.getPitch();

        float range = AttackAura.get() != null ? AttackAura.get().attackRange.getValue() : 3.0F;
        boolean onTarget = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, entity.getBoundingBox());

        float targetSpeedY;
        float targetSpeedP;
        if (onTarget) {
            targetSpeedY = MathUtil.random(14.0F, 18.0F);
            targetSpeedP = MathUtil.random(4.0F, 5.5F);
        } else {
            targetSpeedY = MathUtil.random(20.0F, 24.0F);
            targetSpeedP = MathUtil.random(5.5F, 6.8F);
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.3F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.3F;

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw);
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch);

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
