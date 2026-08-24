package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;

/**
 * RellyWorld: гибрид под Grim + Matrix.
 * - точка прицела блуждает по хитбоксу и переходит между зонами
 *   (нет «идеального центра» — против эвристики Matrix);
 * - физика мыши: плавный разгон и торможение скорости без спайков,
 *   на цели — микрошаги в безопасных для Grim пределах;
 * - после удара короткая «усадка»: замедление и микро-отворот,
 *   как коррекция руки у живого игрока.
 */
public class RellyWorldRotation implements RotationAura {

    /** Доля подтягивания текущей скорости к целевой за тик. */
    private static final float SPEED_SMOOTH = 0.28F;

    private float currentSpeedYaw = 30F;
    private float currentSpeedPitch = 10F;

    private float offX, offY, offZ;
    private long nextRepick = 0L;
    private long settleUntil = 0L;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        long ms = System.currentTimeMillis();

        if (aura.justAttacked) {
            aura.justAttacked = false;
            settleUntil = ms + 90L + MathUtil.randomInt(0, 90);
        }

        Box box = target.getBoundingBox();
        if (ms >= nextRepick) {
            nextRepick = ms + MathUtil.randomInt(140, 380);
            offX = MathUtil.randomGaussian(-0.14F, 0.14F) * (float) (box.maxX - box.minX);
            offY = MathUtil.randomGaussian(-0.10F, 0.16F) * (float) (box.maxY - box.minY);
            offZ = MathUtil.randomGaussian(-0.14F, 0.14F) * (float) (box.maxZ - box.minZ);
        }

        Vec3d base = UBoxPoints.getBestVector3dOnEntityBox(box);
        Vec3d aim = new Vec3d(
                MathHelper.clamp(base.x + offX, box.minX + 0.03, box.maxX - 0.03),
                MathHelper.clamp(base.y + offY, box.minY + 0.03, box.maxY - 0.03),
                MathHelper.clamp(base.z + offZ, box.minZ + 0.03, box.maxZ - 0.03)
        ).subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90F, 90F);

        boolean onTarget = RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(),
                ranges[0], target);
        boolean settling = ms < settleUntil;

        float wantYaw = MathUtil.randomLerp(38F, 62F);
        float wantPitch = MathUtil.randomLerp(9F, 15F);
        if (onTarget) {
            wantYaw = MathUtil.randomLerp(1.5F, 3.5F);
            wantPitch = MathUtil.randomLerp(1.0F, 2.0F);
        }
        if (settling) {
            wantYaw = MathUtil.randomLerp(0.8F, 1.8F);
            wantPitch = MathUtil.randomLerp(0.5F, 1.2F);
        }
        if (canAttack && !onTarget) {
            wantYaw = Math.max(wantYaw, MathUtil.randomLerp(28F, 44F));
        }

        currentSpeedYaw += (wantYaw - currentSpeedYaw) * SPEED_SMOOTH;
        currentSpeedPitch += (wantPitch - currentSpeedPitch) * SPEED_SMOOTH;

        float jitterScale = onTarget ? 0.35F : 1.0F;
        yaw += MathUtil.randomGaussian(-0.45F, 0.45F) * jitterScale;
        pitch += MathUtil.randomGaussian(-0.30F, 0.30F) * jitterScale;

        if (settling) {
            yaw += MathUtil.randomGaussian(-0.50F, 0.50F);
            pitch += MathUtil.randomGaussian(-0.35F, 0.35F);
        }

        RotationProcess.update(new Rotation(yaw, pitch),
                currentSpeedYaw, currentSpeedPitch,
                MathUtil.randomInt(330, 390), MathUtil.randomInt(330, 390),
                MathUtil.randomInt(3, 5), 15, false);
    }
}
