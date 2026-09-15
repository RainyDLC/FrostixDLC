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
import fun.newrar.utils.aura.RayTraceUtil;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.aura.UBoxPoints;
import fun.newrar.utils.math.MathUtil;

public class SpookyTimeRotation implements RotationAura {
    public static final SpookyTimeRotation INSTANCE = new SpookyTimeRotation();

    private static final float SMOOTHBACK_DONE_DEGREES = 1.0F;
    private static final long SMOOTHBACK_MAX_MS = 1500L;

    private float slowPitchTicks = 1.0F;
    private float slowYawTicks = 1.0F;

    private long smoothbackShakeStartMs = -1L;
    private boolean hadTarget;
    private boolean smoothbackActive;

    private static float heldPitch;
    private static boolean pitchHeld;
    private static int heldTargetId = -1;

    private static float yawVelocity;
    private static float pitchVelocity;
    private static final float PITCH_BASE_SPEED = 7.0F;
    private static final float PITCH_APPROACH = 0.35F;

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
            pitchHeld = false;
            yawVelocity = 0.0F;
            pitchVelocity = 0.0F;
        }

        float range = ranges != null && ranges.length > 0 ? ranges[0] : aura.attackRange.getValue();
        Box hitbox = target.getBoundingBox();

        Rotation currentAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());

        // Проверка: если уже смотрим на хитбокс цели (с поддержкой нахождения в упор)
        if (aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, hitbox)) {
            heldPitch = currentAngle.getPitch();
            pitchHeld = true;
            yawVelocity = MathHelper.lerp(0.4F, yawVelocity, 0.0F);
            pitchVelocity = MathHelper.lerp(0.4F, pitchVelocity, 0.0F);

            RotationProcess.update(currentAngle, 180F, 180F, 35.0F, 35.0F, 3, 1, false);
            return;
        }

        // Вычисляем оптимальную точку прицеливания с защитой от ухода под ноги при вжимании в цель
        Vec3d aimPoint = getAimPoint(target, hitbox);
        Vec3d dir = aimPoint.subtract(mc.player.getEyePos());

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -89.5F, 89.5F);
        Rotation targetAngle = new Rotation(targetYaw, targetPitch);

        Rotation nextAngle = switch (subMode) {
            case "Дуэли", "Спуки-дуэли" -> processSpookyDuels(currentAngle, targetAngle, target);
            case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, canAttack);
            default -> processSpooky121(currentAngle, targetAngle, aura, target, hitbox);
        };

        Rotation finalAngle = applyPitchHold(currentAngle, nextAngle, targetAngle, target, range, hitbox);

        RotationProcess.update(finalAngle, 180F, 180F, 35.0F, 35.0F, 3, 1, false);
    }

    /**
     * Возвращает точку прицеливания.
     * При сближении вплотную (< 1.25 блоков) смещает цель к уровню груди/глаз,
     * предотвращая наклон головы вниз на 70-80 градусов.
     */
    private static Vec3d getAimPoint(LivingEntity target, Box hitbox) {
        Vec3d eye = mc.player.getEyePos();
        double distXZ = Math.hypot(target.getX() - mc.player.getX(), target.getZ() - mc.player.getZ());

        // При сближении вплотную прицеливаемся на уровень груди/глаз
        if (distXZ < 1.25 || hitbox.expand(0.15).contains(eye)) {
            double heightFraction = distXZ < 0.6 ? 0.82 : 0.72;
            return new Vec3d(target.getX(), target.getY() + target.getHeight() * heightFraction, target.getZ());
        }

        Vec3d reachable = UBoxPoints.getBestVector3dOnEntityBox(hitbox);
        if (reachable != null && !hitbox.expand(0.05).contains(eye)) {
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
                pitchHeld = false;
                yawVelocity = 0.0F;
                pitchVelocity = 0.0F;
            }

            float range = aura.attackRange.getValue();
            Box hitbox = entity.getBoundingBox();

            if (aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, hitbox)) {
                heldPitch = currentAngle.getPitch();
                pitchHeld = true;
                yawVelocity = MathHelper.lerp(0.4F, yawVelocity, 0.0F);
                pitchVelocity = MathHelper.lerp(0.4F, pitchVelocity, 0.0F);
                return currentAngle;
            }

            Rotation nextAngle = switch (subMode) {
                case "Дуэли", "Спуки-дуэли" -> (entity instanceof LivingEntity living)
                        ? processSpookyDuels(currentAngle, targetAngle, living)
                        : targetAngle;
                case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, true);
                default -> (entity instanceof LivingEntity living)
                        ? processSpooky121(currentAngle, targetAngle, aura, living, hitbox)
                        : targetAngle;
            };

            return applyPitchHold(currentAngle, nextAngle, targetAngle, entity, range, hitbox);
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
            pitchHeld = false;
            heldTargetId = -1;
            yawVelocity = 0.0F;
            pitchVelocity = 0.0F;
        }

        if (!smoothbackActive) {
            return currentAngle;
        }

        return processSmoothback(currentAngle);
    }

    private Rotation processSpooky121(Rotation currentAngle, Rotation targetAngle, AttackAura aura,
                                      LivingEntity entity, Box hitbox) {
        float yawDelta = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float pitchDelta = targetAngle.getPitch() - currentAngle.getPitch();

        float auraDistance = aura.attackRange.getValue();
        float distanceToTarget = (float) mc.player.getEntityPos().distanceTo(entity.getEntityPos());
        float distanceFactor = MathHelper.clamp(0.3F + 0.7F * (distanceToTarget / auraDistance), 0.0F, 1.0F);

        boolean hasTrace = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), auraDistance, hitbox);

        if (hasTrace) {
            slowYawTicks = MathHelper.lerp(0.35F, slowYawTicks, 0.7F);
            slowPitchTicks = MathHelper.lerp(0.35F, slowPitchTicks, 0.5F);
        } else {
            slowYawTicks = MathHelper.lerp(0.35F, slowYawTicks, 1.0F);
            slowPitchTicks = MathHelper.lerp(0.35F, slowPitchTicks, 1.0F);
        }

        float randomSpeedFactor = MathUtil.random(0.55F, 0.75F);
        float yawSpeed = MathUtil.random(26.0F, 32.0F) * slowYawTicks;
        float pitchSpeed = MathUtil.random(8.0F, 12.0F) * slowPitchTicks;

        // Подавление волнового джиттера при сближении в упор
        float closeDamp = MathHelper.clamp(distanceToTarget / 1.5F, 0.15F, 1.0F);

        long timeMs = System.currentTimeMillis();
        yawDelta += (float) (Math.cos(timeMs / 50.0) * (2.8F * distanceFactor * closeDamp));
        pitchDelta += (float) (Math.sin(timeMs / 60.0) * (4.2F * distanceFactor * closeDamp));

        float clampedYaw = MathHelper.clamp(yawDelta, -yawSpeed, yawSpeed) * randomSpeedFactor;
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchSpeed, pitchSpeed) * randomSpeedFactor * (hasTrace ? 0.6F : 1.0F);

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    private Rotation processSpookyDuels(Rotation currentAngle, Rotation targetAngle, LivingEntity entity) {
        Vec3d vel = mc.player.getVelocity();
        double distXZ = Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());

        // Защита от перелета предикта за спину цели (критическая причина разворотов на 180° и банов в дуэлях)
        double maxLead = Math.max(0.0, distXZ - 0.45);
        double leadX = vel.x * 1.5;
        double leadZ = vel.z * 1.5;
        double leadLen = Math.hypot(leadX, leadZ);
        if (leadLen > maxLead && leadLen > 0.0001) {
            double scale = maxLead / leadLen;
            leadX *= scale;
            leadZ *= scale;
        }

        Vec3d predictedEye = mc.player.getEyePos().add(leadX, 0.0, leadZ);

        // Точка цели: при сближении целимся выше (грудь/голова), чтобы не смотреть в пол
        double targetY = entity.getY() + (distXZ < 1.2 ? entity.getHeight() * 0.78 : entity.getHeight() * 0.55);
        Vec3d targetCenter = new Vec3d(entity.getX(), targetY, entity.getZ());
        Vec3d dir = targetCenter.subtract(predictedEye);

        float predYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float predPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -89.5F, 89.5F);

        float deltaYaw = MathHelper.wrapDegrees(predYaw - currentAngle.getYaw());
        float deltaPitch = predPitch - currentAngle.getPitch();

        float difference = Math.max((float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch)), 0.0001F);

        float yawSpeed = MathUtil.random(50.0F, 75.0F);
        float pitchSpeed = MathUtil.random(30.0F, 50.0F);

        if (!entity.isOnGround() && entity.fallDistance > 0.0F) {
            pitchSpeed *= 0.65F;
        }

        float straightLineYaw = Math.abs(deltaYaw / difference) * yawSpeed;
        float straightLinePitch = Math.abs(deltaPitch / difference) * pitchSpeed;

        // Подавление джиттера при сближении в упор
        float closeDamp = MathHelper.clamp((float) (distXZ / 1.5), 0.15F, 1.0F);
        float jitterYaw = MathUtil.random(-0.5F, 0.5F) * closeDamp;
        float jitterPitch = MathUtil.random(-0.35F, 0.35F) * closeDamp;

        return new Rotation(
                currentAngle.getYaw() + MathHelper.clamp(deltaYaw, -straightLineYaw, straightLineYaw) + jitterYaw,
                MathHelper.clamp(currentAngle.getPitch() + MathHelper.clamp(deltaPitch, -straightLinePitch, straightLinePitch) + jitterPitch, -89.5F, 89.5F)
        );
    }

    private Rotation processSpooky116(Rotation currentAngle, Rotation targetAngle, boolean canAttack) {
        float deltaYaw = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float deltaPitch = targetAngle.getPitch() - currentAngle.getPitch();
        float difference = Math.max((float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch)), 0.0001F);

        boolean canAttackSoon = UAttack.chargeReadyIn(2);
        boolean canAttackNow = canAttack && UAttack.chargeReadyIn(0);
        boolean airborne = !mc.player.isOnGround();

        float yawBudget = canAttackNow ? 38.0F : airborne ? MathUtil.random(16, 35) : MathUtil.random(18, 33);
        float pitchBudget = canAttackNow ? 45.0F : airborne ? MathUtil.random(14, 25) : MathUtil.random(14, 22);

        float straightLineYaw = Math.abs(deltaYaw / difference) * yawBudget;
        float straightLinePitch = Math.abs(deltaPitch / difference) * pitchBudget;

        float jitterY = canAttackSoon ? MathUtil.random(-0.25F, 0.25F) : 0.0F;
        float jitterX = canAttackSoon ? MathUtil.random(-0.25F, 0.25F) : 0.0F;

        return new Rotation(
                currentAngle.getYaw() + MathHelper.clamp(deltaYaw, -straightLineYaw, straightLineYaw) + jitterX,
                MathHelper.clamp(currentAngle.getPitch() + MathHelper.clamp(deltaPitch, -straightLinePitch, straightLinePitch) + jitterY, -89.5F, 89.5F)
        );
    }

    private Rotation applyPitchHold(Rotation currentAngle, Rotation nextAngle, Rotation targetAngle,
                                    Entity entity, float range, Box hitbox) {
        float distanceFactor = distanceFactor(entity);
        float response = MathHelper.clamp(0.22F + 0.28F * distanceFactor, 0.25F, 0.75F);

        float yawTarget = MathHelper.wrapDegrees(nextAngle.getYaw() - currentAngle.getYaw());
        yawVelocity = MathHelper.lerp(response, yawVelocity, yawTarget);
        float newYaw = currentAngle.getYaw() + yawVelocity;

        float frozenPitch = pitchHeld ? heldPitch : currentAngle.getPitch();
        if (aimsAtBox(newYaw, frozenPitch, range, hitbox)) {
            heldPitch = frozenPitch;
            pitchHeld = true;
            pitchVelocity = MathHelper.lerp(0.4F, pitchVelocity, 0.0F);
            return new Rotation(newYaw, frozenPitch);
        }

        float remaining = MathHelper.wrapDegrees(targetAngle.getPitch() - currentAngle.getPitch());
        float maxSpeed = PITCH_BASE_SPEED * distanceFactor;
        float desired = MathHelper.clamp(remaining * PITCH_APPROACH, -maxSpeed, maxSpeed);

        pitchVelocity = MathHelper.lerp(response, pitchVelocity, desired);
        float newPitch = MathHelper.clamp(currentAngle.getPitch() + pitchVelocity, -89.5F, 89.5F);

        heldPitch = newPitch;
        pitchHeld = true;
        return new Rotation(newYaw, newPitch);
    }

    public static boolean aimsAtBox(float yaw, float pitch, float range, Box box) {
        if (mc.player == null || box == null) {
            return false;
        }
        Vec3d eye = mc.player.getEyePos();
        Box expanded = box.expand(0.12);

        // Если точка глаз находится внутри или на границе хитбокса (при вжимании в цель)
        if (expanded.contains(eye)) {
            return true;
        }

        Vec3d look = RayTraceUtil.getVectorForRotation(pitch, yaw);
        return expanded.raycast(eye, eye.add(look.multiply(range))).isPresent();
    }

    private static float distanceFactor(Entity entity) {
        float distance = mc.player.distanceTo(entity);
        return MathHelper.clamp(3.5F / Math.max(distance, 1.2F), 0.4F, 1.6F);
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
        float straightLineYaw = Math.abs(deltaYaw / difference) * (cooldownActive ? 0.0F : 35.0F);
        float straightLinePitch = Math.abs(deltaPitch / difference) * (cooldownActive ? 0.0F : 35.0F);

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
