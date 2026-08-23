package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.rotation.FreeLookUtil;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.AuraUtil;
import ru.white.utils.math.MathUtil;

public class SwastonRotation implements RotationAura {

    private static final long FLIP_TIME_MS = 220L;

    private boolean side;
    private long flipStart;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (aura.justAttacked) {
            aura.justAttacked = false;
            side = !side;
            flipStart = System.currentTimeMillis();
        }

        boolean flipping = System.currentTimeMillis() - flipStart <= FLIP_TIME_MS;

        float waveA = (float) Math.cos(System.currentTimeMillis() / 45D);
        float waveB = (float) Math.sin(System.currentTimeMillis() / 65D);

        float yawJitter = flipping ? waveA * MathUtil.randomLerp(6, 12) : waveA * MathUtil.randomLerp(2, 5);
        float pitchJitter = flipping ? waveB * MathUtil.randomLerp(4, 8) : waveB * MathUtil.randomLerp(1, 4);

        Vec3d vec = AuraUtil.getVector3(target);
        float baseYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float basePitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        float yaw = flipping
                ? baseYaw + (side ? 180F : -180F)
                : baseYaw;
        float pitch = basePitch;

        float yawSpeed = flipping ? MathUtil.randomLerp(75, 90) : MathUtil.randomLerp(35, 55);
        float pitchSpeed = flipping ? MathUtil.randomLerp(55, 75) : MathUtil.randomLerp(30, 50);

        if (!flipping && !canAttack) {
            yaw = FreeLookUtil.freeYaw;
            pitch = FreeLookUtil.freePitch;
            yawJitter = 0;
            pitchJitter = 0;
        }

        Rotation newRotation = new Rotation(MathHelper.wrapDegrees(yaw + yawJitter),
                MathHelper.clamp(pitch + pitchJitter, -90F, 90F));

        RotationProcess.update(newRotation, yawSpeed, pitchSpeed,
                flipping ? MathUtil.randomInt(120, 200) : MathUtil.randomInt(60, 110),
                MathUtil.randomInt(60, 110),
                flipping ? MathUtil.randomInt(1, 3) : 0, 15, false);
    }
}
