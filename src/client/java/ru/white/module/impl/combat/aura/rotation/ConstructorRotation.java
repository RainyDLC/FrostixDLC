package ru.white.module.impl.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import ru.white.manager.neuro.NeuroManager;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.aura.RotationAura;
import ru.white.utils.aura.LagCompensation;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UAttack;
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

    // человеческие микро-косяки руки: паузы и овершут
    private int pauseTicks = 0;
    private float overshootYaw = 0F;
    private float overshootPitch = 0F;

    public ConstructorRotation(Profile profile) {
        this.profile = profile;
    }

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        // наводимся по живому хитбоксу — той же системе отсчёта, по которой
        // удар потом валидируется (LagCompensation.attackDistance / rayHits)
        Vec3d aimPoint = UBoxPoints.getBestVector3dOnEntityBox(target.getBoundingBox());
        Vec3d vec = aimPoint.subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        boolean onTarget = RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(),
                ranges[0], target);
        long ms = System.currentTimeMillis();

        // Тик, в котором удар уже готов (заряд добирается за этот тик, цель в радиусе).
        // Наведение применяется в конце тика, а удар уходит на HEAD следующего — значит
        // именно сейчас прицел обязан оказаться на хитбоксе, иначе рейкаст в
        // UAttack.shouldAttack отбросит удар и заряд сгорит впустую.
        boolean hitNow = canAttack && UAttack.chargeReadyIn(1);

        // Перед ударом руке не до микро-пауз и перелётов: и то, и другое уводит прицел
        // с бокса ровно в тот тик, когда он там нужен.
        if (hitNow) {
            pauseTicks = 0;
            overshootYaw = 0F;
            overshootPitch = 0F;
        }

        // базовые скорости из конструктора ротации
        float speedYaw = MathUtil.randomLerp(aura.cYawMin.getValue(), aura.cYawMax.getValue());
        float speedPitch = MathUtil.randomLerp(aura.cPitchMin.getValue(), aura.cPitchMax.getValue());

        int retYawSpeed;
        int retPitchSpeed;

        switch (profile) {
            case MATRIX -> {
                // микро-пауза руки: настоящая мышь иногда замирает на тик-два
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

                // лёгкая тряска руки — живая траектория без резких спайков
                yaw += MathUtil.randomGaussian(-0.7F, 0.7F);
                pitch += MathUtil.randomGaussian(-0.45F, 0.45F);

                // овершут: мышь проносится мимо цели и доводится обратно —
                // идеальная доводка без перелёта ловится на «роботизм»
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

        // случайный разброс и осцилляция из конструктора
        yaw += MathUtil.randomLerp(-aura.cRandomYaw.getValue(), aura.cRandomYaw.getValue());
        pitch += MathUtil.randomLerp(-aura.cRandomPitch.getValue(), aura.cRandomPitch.getValue());
        yaw += (float) Math.sin(ms / 280D) * aura.cOscX.getValue() * 6F;
        pitch += (float) Math.cos(ms / 340D) * aura.cOscY.getValue() * 4F;

        // Доводка в тик удара — здесь работают «Скорость удара Yaw/Pitch».
        //
        // Скорости в RotationProcess.updateRotation клампятся за вызов, то есть это
        // ГРАДУСЫ ЗА ТИК. «Замирание» у цели (1.5-3.5°/тик у Grim, 3-6° у Matrix) не
        // успевает за игроком, который стрейфит: уход на 0.2 блока за тик на дистанции
        // 3 блока — это ~3.8°/тик. Прицел сползал с хитбокса, рейкаст резал удар, и так
        // терялись удары в те моменты, когда бить было можно.
        //
        // Поэтому в тик удара шаг не меньше того, что реально нужно доехать до точки
        // наведения, но не больше «Скорости удара» из конструктора. Рывка от этого не
        // появляется: доводим ровно на столько, на сколько ушла цель, — при большом
        // отставании прицел всё равно ещё не на боксе, и там работают обычные скорости.
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
