package fun.newrar.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import fun.newrar.manager.rotation.FreeLookUtil;
import fun.newrar.manager.rotation.Rotation;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.aura.RotationAura;
import fun.newrar.utils.aura.AuraUtil;
import fun.newrar.utils.math.MathUtil;

public class CustomRotation implements RotationAura {
    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        Vec3d vec3d = AuraUtil.getVector3(target);

        float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec3d.y, Math.hypot(vec3d.x, vec3d.z))), -90F, 90F);

        long ms = System.currentTimeMillis();

        float speedYaw = MathUtil.randomLerp(aura.cYawMin.getValue(), aura.cYawMax.getValue());
        float speedPitch = MathUtil.randomLerp(aura.cPitchMin.getValue(), aura.cPitchMax.getValue());

        boolean attacking = false;
        if (canAttack) aura.tick = 1;
        if (aura.tick > 0) {
            attacking = true;
            aura.tick--;
        }

        if (attacking) {
            float hitScaleY = aura.cHitYaw.getValue() / 200F;
            float hitScaleP = aura.cHitPitch.getValue() / 200F;
            float waveA = (float) Math.cos(ms / 30D);
            float waveB = (float) Math.sin(ms / 30D);
            yaw += waveA * MathUtil.randomLerp(4F, 8F) * hitScaleY;
            pitch += waveB * MathUtil.randomLerp(4F, 8F) * hitScaleP;

            yaw += MathUtil.randomLerp(-aura.cRandomYaw.getValue(), aura.cRandomYaw.getValue());
            pitch += MathUtil.randomLerp(-aura.cRandomPitch.getValue(), aura.cRandomPitch.getValue());

            yaw += (float) Math.sin(ms / 280D) * aura.cOscX.getValue() * 6F;
            pitch += (float) Math.cos(ms / 340D) * aura.cOscY.getValue() * 4F;
        } else {
            yaw = FreeLookUtil.freeYaw;
            pitch = FreeLookUtil.freePitch;
        }

        RotationProcess.update(new Rotation(yaw, pitch), speedYaw, speedPitch,
                MathUtil.randomInt(90, 180), MathUtil.randomInt(90, 180), MathUtil.randomInt(0, 3), 15, false);
    }
}

