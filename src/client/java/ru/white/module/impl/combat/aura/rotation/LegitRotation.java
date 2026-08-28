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

public class LegitRotation implements RotationAura {
    private int idleTicks = 25;

    private static float blend(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        Vec3d vec3d = AuraUtil.getVector3(target);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
        float targetPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec3d.y, Math.hypot(vec3d.x, vec3d.z))), -90F, 90F);

        boolean attacking = false;
        if (canAttack) aura.tick = 1;
        if (aura.tick > 0) {
            attacking = true;
            aura.tick--;
        }

        if (attacking) idleTicks = 0;
        else idleTicks++;

        float t = (System.currentTimeMillis() % 1000000L) / 50F;

        float sway = (float) ((((Math.sin(t * 0.31F) * 0.5D)
                + (Math.sin(t * 0.73F + 1.1D) * 0.3D)
                + (Math.sin(t * 1.7F + 2.6D) * 0.2D)) * 12.0D) / 4.0D);
        float swayPitch = (float) ((((Math.sin(t * 0.27F + 0.9D) * 0.4D)
                + (Math.sin(t * 0.61F + 2.3D) * 0.25D)) * 12.0D) / 4.0D);

        float outYaw;
        float outPitch;

        if (attacking) {
            outYaw = targetYaw;
            outPitch = targetPitch;
        } else if (idleTicks <= 20) {
            outYaw = FreeLookUtil.freeYaw;
            outPitch = FreeLookUtil.freePitch;
        } else {
            float lerp = MathUtil.randomLerp(0.15F, 0.35F);
            outYaw = blend(FreeLookUtil.freeYaw, mc.player.getYaw(), lerp);
            outPitch = blend(FreeLookUtil.freePitch, mc.player.getPitch(), lerp);
        }

        RotationProcess.update(
                new Rotation(outYaw + MathHelper.clamp(sway, -0.6F, 0.6F),
                        MathHelper.clamp(outPitch + MathHelper.clamp(swayPitch, -0.45F, 0.45F), -90F, 90F)),
                MathUtil.randomLerp(55F, 85F),
                MathUtil.randomLerp(55F, 85F),
                MathUtil.randomInt(60, 120),
                MathUtil.randomInt(60, 120),
                MathUtil.randomInt(0, 3),
                15,
                false);
    }
}
