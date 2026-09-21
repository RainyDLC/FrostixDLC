package fun.newrar.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import fun.newrar.manager.neuro.NeuroManager;
import fun.newrar.manager.rotation.Rotation;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.aura.RotationAura;
import fun.newrar.utils.aura.RayTraceUtil;
import fun.newrar.utils.aura.ServerReach;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.aura.UBoxPoints;
import fun.newrar.utils.math.MathUtil;

public class ConstructorRotation implements RotationAura {
    public enum Profile { MATRIX, NEURO, GRIM }

    private static final float SPEED_SMOOTH = 0.30F;

    private final Profile profile;

    private float currentSpeedYaw = 40F;
    private float currentSpeedPitch = 14F;

    private int pauseTicks = 0;
    private float overshootYaw = 0F;
    private float overshootPitch = 0F;

    public ConstructorRotation(Profile profile) {
        this.profile = profile;
    }

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        Vec3d aimPoint = UBoxPoints.getBestVector3dOnEntityBox(target);
        Vec3d vec = aimPoint.subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        boolean onTarget = RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(),
                ranges[0], target);
        long ms = System.currentTimeMillis();

        boolean hitNow = canAttack && UAttack.chargeReadyIn(1);

        if (hitNow) {
            pauseTicks = 0;
            overshootYaw = 0F;
            overshootPitch = 0F;
        }

        float speedYaw = MathUtil.randomLerp(aura.cYawMin.getValue(), aura.cYawMax.getValue());
        float speedPitch = MathUtil.randomLerp(aura.cPitchMin.getValue(), aura.cPitchMax.getValue());

        int retYawSpeed;
        int retPitchSpeed;

        switch (profile) {
            case MATRIX -> {
                if (pauseTicks > 0) {
                    pauseTicks--;
                    speedYaw = 0.25F;
                    speedPitch = 0.15F;
                } else if (MathUtil.randomInt(0, 100) < 3) {
                    pauseTicks = MathUtil.randomInt(1, 2);
                }

                if (onTarget) {
                    speedYaw = MathUtil.randomLerp(3F, 6F);
                    speedPitch = MathUtil.randomLerp(1.5F, 3F);
                }
                currentSpeedYaw += (speedYaw - currentSpeedYaw) * SPEED_SMOOTH;
                currentSpeedPitch += (speedPitch - currentSpeedPitch) * SPEED_SMOOTH;
                speedYaw = currentSpeedYaw;
                speedPitch = currentSpeedPitch;

                yaw += MathUtil.randomGaussian(-0.7F, 0.7F);
                pitch += MathUtil.randomGaussian(-0.45F, 0.45F);

                if (Math.abs(overshootYaw) > 0.05F || Math.abs(overshootPitch) > 0.05F) {
                    yaw += overshootYaw;
                    pitch += overshootPitch;
                    overshootYaw *= -0.35F;
                    overshootPitch *= -0.35F;
                } else if (!hitNow && !onTarget && MathUtil.randomInt(0, 100) < 5) {
                    float yawDeltaAbs = Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
                    if (yawDeltaAbs > 12F) {
                        float dir = Math.signum(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
                        overshootYaw = dir * MathUtil.randomLerp(0.8F, 1.8F);
                        overshootPitch = MathUtil.randomGaussian(-0.5F, 0.5F);
                    }
                }

                retYawSpeed = MathUtil.randomInt(360, 400);
                retPitchSpeed = MathUtil.randomInt(360, 400);
            }
            case NEURO -> {
                float applied = (float) Math.hypot(
                        MathHelper.wrapDegrees(yaw - mc.player.getYaw()),
                        pitch - mc.player.getPitch());
                float[] jitter = NeuroManager.get().nextJitter(applied);
                yaw += MathHelper.clamp(jitter[0], -8F, 8F);
                pitch += MathHelper.clamp(jitter[1], -5F, 5F);

                speedYaw = MathUtil.randomGaussian(speedYaw * 0.75F, speedYaw * 1.15F);
                speedPitch = MathUtil.randomGaussian(speedPitch * 0.75F, speedPitch * 1.15F);

                retYawSpeed = MathUtil.randomInt(900, 1424);
                retPitchSpeed = MathUtil.randomInt(900, 1424);
            }
            default -> {
                if (pauseTicks > 0) {
                    pauseTicks--;
                    speedYaw = 0.2F;
                    speedPitch = 0.15F;
                } else if (MathUtil.randomInt(0, 100) < 2) {
                    pauseTicks = 1;
                }

                if (onTarget) {
                    speedYaw = MathUtil.randomLerp(1.5F, 3.5F);
                    speedPitch = MathUtil.randomLerp(1F, 2F);
                }
                currentSpeedYaw += (speedYaw - currentSpeedYaw) * (SPEED_SMOOTH + 0.1F);
                currentSpeedPitch += (speedPitch - currentSpeedPitch) * (SPEED_SMOOTH + 0.1F);
                speedYaw = currentSpeedYaw;
                speedPitch = currentSpeedPitch;

                yaw += MathUtil.randomGaussian(-0.35F, 0.35F);
                pitch += MathUtil.randomGaussian(-0.25F, 0.25F);

                retYawSpeed = MathUtil.randomInt(320, 380);
                retPitchSpeed = MathUtil.randomInt(320, 380);
            }
        }

        yaw += MathUtil.randomLerp(-aura.cRandomYaw.getValue(), aura.cRandomYaw.getValue());
        pitch += MathUtil.randomLerp(-aura.cRandomPitch.getValue(), aura.cRandomPitch.getValue());
        yaw += (float) Math.sin(ms / 280D) * aura.cOscX.getValue() * 6F;
        pitch += (float) Math.cos(ms / 340D) * aura.cOscY.getValue() * 4F;

        if (hitNow) {
            float needYaw = Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
            float needPitch = Math.abs(pitch - mc.player.getPitch());
            speedYaw = Math.min(Math.max(speedYaw, needYaw), aura.cHitYaw.getValue());
            speedPitch = Math.min(Math.max(speedPitch, needPitch), aura.cHitPitch.getValue());
        }

        RotationProcess.update(new Rotation(yaw, pitch), speedYaw, speedPitch,
                retYawSpeed, retPitchSpeed, MathUtil.randomInt(3, 5), 15, false);
    }
}

