package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.neuro.NeuroManager;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UBoxPoints;
import ru.white.utils.math.MathUtil;

/**
 * Ротации из конструктора: профили Matrix / Neuro / Grim берут скорости, рандом
 * и осцилляцию из настроек конструктора ротации AttackAura, а доводка прицела
 * сделана под проверки соответствующих античитов:
 *
 * - Matrix: плавный разгон скорости (как настоящая мышь), по наведении на цель
 *   скорость падает до микро-коррекций — нет резких рывков и спайков углов.
 * - Neuro: почерк руки из нейро-записей (NeuroManager) поверх плавной доводки.
 * - Grim: минимальные шаги без овершута, микродрожь в пределах GCD,
 *   у цели почти замирает — не ловится на «удар без наведения».
 */
public class ConstructorRotation implements RotationAura {

    public enum Profile { MATRIX, NEURO, GRIM }

    /** Доля подтягивания текущей скорости к целевой за тик (сглаженный разгон). */
    private static final float SPEED_SMOOTH = 0.30F;

    private final Profile profile;

    // сглаженные скорости: имитируют физику движения мыши
    private float currentSpeedYaw = 40F;
    private float currentSpeedPitch = 14F;

    public ConstructorRotation(Profile profile) {
        this.profile = profile;
    }

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        Vec3d aimPoint = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox());
        Vec3d vec = aimPoint.subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        boolean onTarget = RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(),
                ranges[0], target);
        long ms = System.currentTimeMillis();

        // базовые скорости из конструктора ротации
        float speedYaw = MathUtil.randomLerp(aura.cYawMin.getValue(), aura.cYawMax.getValue());
        float speedPitch = MathUtil.randomLerp(aura.cPitchMin.getValue(), aura.cPitchMax.getValue());

        int retYawSpeed;
        int retPitchSpeed;

        switch (profile) {
            case MATRIX -> {
                if (onTarget) {
                    speedYaw = MathUtil.randomLerp(3F, 6F);
                    speedPitch = MathUtil.randomLerp(1.5F, 3F);
                }
                currentSpeedYaw += (speedYaw - currentSpeedYaw) * SPEED_SMOOTH;
                currentSpeedPitch += (speedPitch - currentSpeedPitch) * SPEED_SMOOTH;
                speedYaw = currentSpeedYaw;
                speedPitch = currentSpeedPitch;

                // лёгкая тряска руки — живая траектория без резких спайков
                yaw += MathUtil.randomGaussian(-0.7F, 0.7F);
                pitch += MathUtil.randomGaussian(-0.45F, 0.45F);

                retYawSpeed = MathUtil.randomInt(360, 400);
                retPitchSpeed = MathUtil.randomInt(360, 400);
            }
            case NEURO -> {
                // человеческий почерк руки из нейро-записей
                float applied = (float) Math.hypot(
                        MathHelper.wrapDegrees(yaw - mc.player.getYaw()),
                        pitch - mc.player.getPitch());
                float[] jitter = NeuroManager.get().nextJitter(applied);
                yaw += MathHelper.clamp(jitter[0], -8F, 8F);
                pitch += MathHelper.clamp(jitter[1], -5F, 5F);

                // гауссово распределение скоростей: чаще средние значения, как у игрока
                speedYaw = MathUtil.randomGaussian(speedYaw * 0.75F, speedYaw * 1.15F);
                speedPitch = MathUtil.randomGaussian(speedPitch * 0.75F, speedPitch * 1.15F);

                retYawSpeed = MathUtil.randomInt(900, 1424);
                retPitchSpeed = MathUtil.randomInt(900, 1424);
            }
            default -> {
                // GRIM: никаких рывков, маленький шаг, без овершута
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

        // случайный разброс и осцилляция из конструктора
        yaw += MathUtil.randomLerp(-aura.cRandomYaw.getValue(), aura.cRandomYaw.getValue());
        pitch += MathUtil.randomLerp(-aura.cRandomPitch.getValue(), aura.cRandomPitch.getValue());
        yaw += (float) Math.sin(ms / 280D) * aura.cOscX.getValue() * 6F;
        pitch += (float) Math.cos(ms / 340D) * aura.cOscY.getValue() * 4F;

        RotationProcess.update(new Rotation(yaw, pitch), speedYaw, speedPitch,
                retYawSpeed, retPitchSpeed, MathUtil.randomInt(3, 5), 15, false);
    }
}
