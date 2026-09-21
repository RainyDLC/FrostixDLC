package fun.newrar.module.impl.combat.aura.rotation;

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

public class SlothRotation implements RotationAura {
    private static final float MAX_YAW_SPEED = 27.5F;
    private static final float MAX_PITCH_SPEED = 8.8F;

    private float currentSpeedYaw = 16.0F;
    private float currentSpeedPitch = 5.0F;

    private int heldTargetId = -1;
    private double smoothedVelX;
    private double smoothedVelY;
    private double smoothedVelZ;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) {
            return;
        }

        if (heldTargetId != target.getId()) {
            heldTargetId = target.getId();
            currentSpeedYaw = 16.0F;
            currentSpeedPitch = 5.0F;
            smoothedVelX = 0.0;
            smoothedVelY = 0.0;
            smoothedVelZ = 0.0;
        }

        Rotation currentAngle = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        Box hitbox = ServerReach.attackBox(target);

        Vec3d aimPoint = applyLead(target, resolveAimPoint(target, hitbox));
        Vec3d dir = aimPoint.subtract(mc.player.getEyePos());
        double dirDistXZ = Math.hypot(dir.x, dir.z);

        float targetYaw = dirDistXZ < 0.05 ? currentAngle.getYaw() : (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.max(0.05, dirDistXZ))), -89.5F, 89.5F);

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentAngle.getYaw());
        float deltaPitch = targetPitch - currentAngle.getPitch();

        float range = aura.attackRange.getValue();
        boolean onTarget = aimsAtBox(currentAngle.getYaw(), currentAngle.getPitch(), range, hitbox);
        boolean hitNow = canAttack || UAttack.chargeReadyIn(1);
        boolean falling = !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;

        float targetSpeedYaw;
        float targetSpeedPitch;

        if (hitNow || falling) {
            targetSpeedYaw = MathUtil.random(24.0F, MAX_YAW_SPEED);
            targetSpeedPitch = MathUtil.random(7.4F, MAX_PITCH_SPEED);
        } else if (onTarget) {
            targetSpeedYaw = MathUtil.random(6.5F, 10.0F);
            targetSpeedPitch = MathUtil.random(2.2F, 3.4F);
        } else {
            targetSpeedYaw = MathUtil.random(17.0F, 23.0F);
            targetSpeedPitch = MathUtil.random(5.4F, 7.2F);
        }

        currentSpeedYaw += (targetSpeedYaw - currentSpeedYaw) * 0.28F;
        currentSpeedPitch += (targetSpeedPitch - currentSpeedPitch) * 0.28F;

        float jitterScale = MathHelper.clamp((float) dirDistXZ / 2.0F, 0.2F, 1.0F);
        float jitterYaw = MathUtil.randomGaussian(-0.11F, 0.11F) * jitterScale;
        float jitterPitch = MathUtil.randomGaussian(-0.07F, 0.07F) * jitterScale;

        float clampedYaw = MathHelper.clamp(deltaYaw, -currentSpeedYaw, currentSpeedYaw) + jitterYaw;
        float clampedPitch = MathHelper.clamp(deltaPitch, -currentSpeedPitch, currentSpeedPitch) + jitterPitch;

        Rotation nextAngle = new Rotation(
                currentAngle.getYaw() + clampedYaw,
                MathHelper.clamp(currentAngle.getPitch() + clampedPitch, -89.5F, 89.5F));

        RotationProcess.update(nextAngle, currentSpeedYaw, currentSpeedPitch,
                MathUtil.random(30.0F, 45.0F), MathUtil.random(30.0F, 45.0F), 3, 1, false);
    }

    private static Vec3d resolveAimPoint(LivingEntity target, Box hitbox) {
        Vec3d reachable = UBoxPoints.getBestVector3dOnEntityBox(hitbox);
        if (reachable != null) {
            return reachable;
        }
        return new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.65, target.getZ());
    }

    private Vec3d applyLead(LivingEntity target, Vec3d baseAim) {
        Vec3d velocity = target.getVelocity();
        smoothedVelX = MathHelper.lerp(0.35, smoothedVelX, velocity.x);
        smoothedVelY = MathHelper.lerp(0.30, smoothedVelY, velocity.y);
        smoothedVelZ = MathHelper.lerp(0.35, smoothedVelZ, velocity.z);

        double distanceXZ = Math.hypot(target.getX() - mc.player.getX(), target.getZ() - mc.player.getZ());
        double leadFactor = distanceXZ < 1.0 ? 0.0 : MathHelper.clamp(distanceXZ / 5.0, 0.03, 0.12);

        Vec3d lead = new Vec3d(smoothedVelX, smoothedVelY, smoothedVelZ).multiply(leadFactor);
        if (lead.lengthSquared() > 0.0625) {
            lead = lead.normalize().multiply(0.25);
        }
        return baseAim.add(lead);
    }

    private static boolean aimsAtBox(float yaw, float pitch, float range, Box box) {
        if (mc.player == null || box == null) {
            return false;
        }
        AttackAura aura = AttackAura.get();
        boolean ignoreBlocks = aura != null && aura.others.getValue("Бить через блоки");
        return LagCompensation.rayHitsBox(box, yaw, pitch, range, ignoreBlocks);
    }
}

