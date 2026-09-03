package fun.newrar.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import fun.newrar.manager.rotation.Rotation;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.aura.RotationAura;
import fun.newrar.utils.aura.AuraUtil;
import fun.newrar.utils.aura.RayTraceUtil;
import fun.newrar.utils.math.MathUtil;

public class HvHRotation implements RotationAura {
    private float currentSpeedPitch = 12F;
    private float currentSpeedPitch2 = 0;

    @Override

    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
            Vec3d vec = AuraUtil.getVector2(target);
            float rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
            float rawPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

            Rotation rotation = new Rotation(rawYaw , rawPitch );

            float speed_1 = MathUtil.random1(480, 625);
            float speed_2 = MathUtil.random1(455, 555);

            float smoothFactor = 0.5f;

            if (RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), aura.attackRange.getValue(), target)) {
                speed_2 = 0;
                speed_1 = MathUtil.randomLerp(450, 700);
            }

            RotationProcess.update(rotation, speed_1, speed_2, 425, 425, 0, 1, false);
    }
}

