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

    // Сглаженные текущие скорости наведения (человекоподобное ускорение и торможение)
    private float currentSpeedYaw = 24.0F;
    private float currentSpeedPitch = 6.0F;

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

    private static final float PITCH_BASE_SPEED = 4.5F;
    private static final float PITCH_APPROACH = 0.28F;

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
            currentSpeedYaw = 24.0F;
            currentSpeedPitch = 6.0F;
        }

        float range = ranges != null && ranges.length > 0 ? ranges[0] : aura.attackRange.getValue();
        Box hitbox = target.getBoundingBox();

        Rotation currentAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        boolean onTarget = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, hitbox);

        // Если прицел уже стабильно на хитбоксе — плавно ведем за целью на микро-скорости
        if (onTarget) {
            heldPitch = currentAngle.getPitch() + MathUtil.randomGaussian(-0.025F, 0.025F);
            pitchHeld = true;
            yawVelocity = MathHelper.lerp(0.35F, yawVelocity, 0.0F);
            pitchVelocity = MathHelper.lerp(0.35F, pitchVelocity, 0.0F);

            // Плавное замедление до микро-трекинга на цели
            currentSpeedYaw += (MathUtil.randomLerp(2.8F, 4.5F) - currentSpeedYaw) * 0.35F;
            currentSpeedPitch += (MathUtil.randomLerp(1.2F, 2.2F) - currentSpeedPitch) * 0.35F;

            float microYaw = currentAngle.getYaw() + MathUtil.randomGaussian(-0.2F, 0.2F);
            float microPitch = MathHelper.clamp(currentAngle.getPitch() + MathUtil.randomGaussian(-0.12F, 0.12F), -89.5F, 89.5F);

            RotationProcess.update(new Rotation(microYaw, microPitch), currentSpeedYaw, currentSpeedPitch, 35.0F, 35.0F, 3, 1, false);
            return;
        }

        // Вычисляем оптимальную точку прицеливания
        Vec3d aimPoint = getAimPoint(target, hitbox);
        Vec3d dir = aimPoint.subtract(mc.player.getEyePos());

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -89.5F, 89.5F);
        Rotation targetAngle = new Rotation(targetYaw, targetPitch);

        Rotation nextAngle = switch (subMode) {
            case "Дуэли", "Спуки-дуэли" -> processSpookyDuels(currentAngle, targetAngle, target, aimPoint);
            case "1.16", "Спуки 1.16" -> processSpooky116(currentAngle, targetAngle, canAttack);
            default -> processSpooky121(currentAngle, targetAngle, aura, target, hitbox);
        };

        Rotation finalAngle = applyPitchHold(currentAngle, nextAngle, targetAngle, target, range, hitbox);

        // Передаем контролируемые безопасные скорости в RotationProcess для гарантированного соблюдения лимитов
        RotationProcess.update(finalAngle, currentSpeedYaw, currentSpeedPitch, 35.0F, 35.0F, 3, 1, false);
    }

    /**
     * Точка прицеливания: в упор целимся на уровне груди/глаз относительно собственной высоты,
     * чтобы предотвратить резкое задирание или опускание головы в пол.
     */
    private static Vec3d getAimPoint(LivingEntity target, Box hitbox) {
        Vec3d eye = mc.player.getEyePos();
        double distXZ = Math.hypot(target.getX() - mc.player.getX(), target.getZ() - mc.player.getZ());

        if (distXZ < 1.3 || hitbox.expand(0.15).contains(eye)) {
            double aimY = MathHelper.clamp(eye.y, hitbox.minY + 0.35, hitbox.maxY - 0.25);
            return new Vec3d(target.getX(), aimY, target.getZ());
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
                heldPitch = currentAngle.getPitch() + MathUtil.randomGaussian(-0.02F, 0.02F);
                pitchHeld = true;
                yawVelocity = MathHelper.lerp(0.35F, yawVelocity, 0.0F);
                pitchVelocity = MathHelper.lerp(0.35F, pitchVelocity, 0.0F);
                return currentAngle;
            }

            Rotation nextAngle = switch (subMode) {
                case "Дуэли", "Спуки-дуэли" -> (entity instanceof LivingEntity living)
                        ? processSpookyDuels(currentAngle, targetAngle, living, getAimPoint(living, hitbox))
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

    /**
     * Режим 1.21: естественные плавные кривые, адаптивное замедление на цели.
     */
    private Rotation processSpooky121(Rotation currentAngle, Rotation targetAngle, AttackAura aura,
                                      LivingEntity entity, Box hitbox) {
        float yawDelta = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float pitchDelta = targetAngle.getPitch() - currentAngle.getPitch();

        float auraDistance = aura.attackRange.getValue();
        float distanceToTarget = (float) mc.player.getEntityPos().distanceTo(entity.getEntityPos());
        float distanceFactor = MathHelper.clamp(0.4F + 0.6F * (distanceToTarget / auraDistance), 0.2F, 1.0F);

        boolean hasTrace = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), auraDistance, hitbox);

        if (hasTrace) {
            slowYawTicks = MathHelper.lerp(0.3F, slowYawTicks, 0.45F);
            slowPitchTicks = MathHelper.lerp(0.3F, slowPitchTicks, 0.35F);
        } else {
            slowYawTicks = MathHelper.lerp(0.25F, slowYawTicks, 1.0F);
            slowPitchTicks = MathHelper.lerp(0.25F, slowPitchTicks, 1.0F);
        }

        float targetSpeedY = MathUtil.random(22.0F, 28.0F) * slowYawTicks;
        float targetSpeedP = MathUtil.random(5.5F, 8.5F) * slowPitchTicks;

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        // Человекоподобное микро-колебание руки с низкой частотой (без резкого синуса)
        float closeDamp = MathHelper.clamp(distanceToTarget / 1.5F, 0.15F, 1.0F);
        long timeMs = System.currentTimeMillis();
        float waveYaw = (float) (Math.sin(timeMs / 135.0) * 0.9F * distanceFactor * closeDamp);
        float wavePitch = (float) (Math.cos(timeMs / 180.0) * 0.5F * distanceFactor * closeDamp);

        float clampedYaw = MathHelper.clamp(yawDelta + waveYaw, -currentSpeedYaw, currentSpeedYaw);
        float clampedPitch = MathHelper.clamp(pitchDelta + wavePitch, -currentSpeedPitch, currentSpeedPitch);

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    /**
     * Режим Дуэли:
     * - Исключены 180-градусные перелеты предикта.
     * - Предикт строится по плавному движению цели (0.35 тика), а не по бесконечному опережению камеры.
     * - Скорости ограничены безопасными значениями для античита SpookyTime/Matrix (макс 24-30° yaw, 6-9° pitch).
     * - На цели скорость плавно гасится до 3-5° yaw и 1.5-2.5° pitch.
     */
    private Rotation processSpookyDuels(Rotation currentAngle, Rotation targetAngle, LivingEntity entity, Vec3d baseAim) {
        Vec3d targetVel = entity.getVelocity();
        double distXZ = Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());

        // Мягкое опережение движения цели (без овершутов в упор)
        double leadFactor = distXZ < 0.8 ? 0.0 : MathHelper.clamp(distXZ / 3.0, 0.05, 0.35);
        Vec3d predictedAim = baseAim.add(targetVel.x * leadFactor, targetVel.y * (leadFactor * 0.5), targetVel.z * leadFactor);
        Vec3d dir = predictedAim.subtract(mc.player.getEyePos());

        float predYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float predPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -89.5F, 89.5F);

        float deltaYaw = MathHelper.wrapDegrees(predYaw - currentAngle.getYaw());
        float deltaPitch = predPitch - currentAngle.getPitch();

        float range = AttackAura.get() != null ? AttackAura.get().attackRange.getValue() : 3.0F;
        boolean onTarget = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, entity.getBoundingBox());

        // Скорости для дуэлей: безопасный диапазон, не вызывающий флагов Aim / Snap
        float targetSpeedY;
        float targetSpeedP;

        if (onTarget) {
            targetSpeedY = MathUtil.random(2.8F, 5.2F);
            targetSpeedP = MathUtil.random(1.2F, 2.6F);
        } else {
            targetSpeedY = MathUtil.random(20.0F, 28.0F);
            targetSpeedP = MathUtil.random(5.5F, 8.5F);
            if (!entity.isOnGround() && entity.fallDistance > 0.0F) {
                targetSpeedP *= 0.75F;
            }
        }

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        // Микро-джиттер с нормальным (гауссовым) распределением — как дрожание руки на мыши
        float closeDamp = MathHelper.clamp((float) (distXZ / 1.5), 0.15F, 1.0F);
        float jitterYaw = MathUtil.randomGaussian(-0.25F, 0.25F) * closeDamp;
        float jitterPitch = MathUtil.randomGaussian(-0.15F, 0.15F) * closeDamp;

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw) + jitterYaw;
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch) + jitterPitch;

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    private Rotation processSpooky116(Rotation currentAngle, Rotation targetAngle, boolean canAttack) {
        float deltaYaw = MathHelper.wrapDegrees(targetAngle.getYaw() - currentAngle.getYaw());
        float deltaPitch = targetAngle.getPitch() - currentAngle.getPitch();

        boolean canAttackSoon = UAttack.chargeReadyIn(2);
        boolean canAttackNow = canAttack && UAttack.chargeReadyIn(0);
        boolean airborne = !mc.player.isOnGround();

        float targetSpeedY = canAttackNow ? 28.0F : airborne ? MathUtil.random(16, 24) : MathUtil.random(14, 22);
        float targetSpeedP = canAttackNow ? 8.0F : airborne ? MathUtil.random(5, 8) : MathUtil.random(4, 7);

        currentSpeedYaw += (targetSpeedY - currentSpeedYaw) * 0.35F;
        currentSpeedPitch += (targetSpeedP - currentSpeedPitch) * 0.35F;

        float jitterY = canAttackSoon ? MathUtil.randomGaussian(-0.15F, 0.15F) : 0.0F;
        float jitterX = canAttackSoon ? MathUtil.randomGaussian(-0.15F, 0.15F) : 0.0F;

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw) + jitterX;
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch) + jitterY;

        return new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F)
        );
    }

    private Rotation applyPitchHold(Rotation currentAngle, Rotation nextAngle, Rotation targetAngle,
                                    Entity entity, float range, Box hitbox) {
        float distanceFactor = distanceFactor(entity);
        float response = MathHelper.clamp(0.25F + 0.25F * distanceFactor, 0.25F, 0.65F);

        float yawTarget = MathHelper.wrapDegrees(nextAngle.getYaw() - currentAngle.getYaw());
        yawVelocity = MathHelper.lerp(response, yawVelocity, yawTarget);
        float newYaw = currentAngle.getYaw() + yawVelocity;

        float frozenPitch = pitchHeld ? heldPitch : currentAngle.getPitch();
        if (aimsAtBox(newYaw, frozenPitch, range, hitbox)) {
            heldPitch = frozenPitch + MathUtil.randomGaussian(-0.02F, 0.02F);
            pitchHeld = true;
            pitchVelocity = MathHelper.lerp(0.35F, pitchVelocity, 0.0F);
            return new Rotation(newYaw, heldPitch);
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

        Vec3d look = RayTraceUtil.getVectorForRotation(pitch, yaw);
        Vec3d center = expanded.getCenter();
        Vec3d toCenter = center.subtract(eye);
        double distToCenter = toCenter.length();

        if (expanded.contains(eye) || distToCenter < 0.6) {
            if (distToCenter > 0.01 && look.dotProduct(toCenter.normalize()) < 0.35) {
                return false;
            }
            Vec3d backStart = eye.subtract(look.multiply(0.5));
            Vec3d end = eye.add(look.multiply(range + 0.4));
            var hit = expanded.raycast(backStart, end);
            return hit.isPresent() && look.dotProduct(hit.get().subtract(eye)) >= -0.1;
        }

        return expanded.raycast(eye, eye.add(look.multiply(range + 0.35))).isPresent();
    }

    private static float distanceFactor(Entity entity) {
        float distance = mc.player.distanceTo(entity);
        return MathHelper.clamp(3.0F / Math.max(distance, 1.4F), 0.5F, 1.3F);
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
