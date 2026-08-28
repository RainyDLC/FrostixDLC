package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.GCDUtil;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;

public class SlothRotation implements RotationAura {
    private float currentSpeedYaw = 18F;
    private float currentSpeedPitch = 7F;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        Vec3d vec = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox())
                .subtract(mc.player.getEyePos()).normalize();

        float rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float rawPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        rawYaw = GCDUtil.applyGCD(rawYaw, mc.player.getYaw());
        rawPitch = GCDUtil.applyGCD(rawPitch, mc.player.getPitch());

        boolean hitting = RayTraceUtil.rayTraceEntity(
                mc.player.getYaw(), mc.player.getPitch(), aura.attackRange.getValue(), target);

        float targetSpeedYaw = hitting ? MathUtil.randomLerp(2.5F, 4.5F) : MathUtil.randomLerp(24F, 38F);
        float targetSpeedPitch = hitting ? MathUtil.randomLerp(1.2F, 2.4F) : MathUtil.randomLerp(10F, 16F);

        float smooth = 0.32F;
        currentSpeedYaw += (targetSpeedYaw - currentSpeedYaw) * smooth;
        currentSpeedPitch += (targetSpeedPitch - currentSpeedPitch) * smooth;

        if (!hitting) {
            rawYaw += MathUtil.random(-0.09F, 0.09F);
            rawPitch += MathUtil.random(-0.06F, 0.06F);
        }

        rawPitch = MathHelper.clamp(rawPitch, -89F, 89F);

        RotationProcess.update(
                new Rotation(rawYaw, rawPitch),
                currentSpeedYaw,
                currentSpeedPitch,
                MathUtil.randomInt(80, 130),
                MathUtil.randomInt(80, 130),
                MathUtil.randomInt(0, 2),
                2,
                false);
    }
}
