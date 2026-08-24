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
 * RellyWorld: гибрид под Grim + Matrix, быстрая и умная версия.
 * - пропорциональный контроллер: скорость растёт с ошибкой наведения —
 *   большая ошибка гасится резкой доводкой, малая — микрошагами (Grim-safe);
 * - предсказание движения: точка прицела ведёт цель по её скорости,
 *   упреждение зависит от дистанции;
 * - точка прицела блуждает по хитбоксу (против «идеального центра» Matrix),
 *   репик замирает перед ударом, чтобы не сорвать трейс;
 * - после удара короткая «усадка» с микро-отворотом, как коррекция руки.
 */
public class RellyWorldRotation implements RotationAura {

    private static final float SPEED_SMOOTH = 0.35F;

    private float currentSpeedYaw = 40F;
    private float currentSpeedPitch = 12F;

    private float offX, offY, offZ;
    private long nextRepick = 0L;
    private long settleUntil = 0L;

    private int lastTargetId = Integer.MIN_VALUE;
    private boolean hasLastPos = false;
    private double lastTX, lastTY, lastTZ;
    private long lastMs;
    private float velX, velY, velZ;
    private long lastOnTarget = 0L;

    @Override
    public void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack) {
        if (mc.player == null || target == null) return;

        long ms = System.currentTimeMillis();

        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            hasLastPos = false;
            velX = velY = velZ = 0F;
        }

        if (aura.justAttacked) {
            aura.justAttacked = false;
            settleUntil = ms + 70L + MathUtil.randomInt(0, 70);
        }

        Box box = target.getBoundingBox();

        // ── сглаженная скорость цели (блоков/сек) ──
        if (!hasLastPos) {
            lastTX = target.getX();
            lastTY = target.getY();
            lastTZ = target.getZ();
            lastMs = ms;
            hasLastPos = true;
        }
        long dt = ms - lastMs;
        boolean teleported = false;
        if (dt >= 40L) {
            float instX = (float) ((target.getX() - lastTX) / dt * 1000.0);
            float instY = (float) ((target.getY() - lastTY) / dt * 1000.0);
            float instZ = (float) ((target.getZ() - lastTZ) / dt * 1000.0);
            if (instX * instX + instY * instY + instZ * instZ > 9.0F) {
                teleported = true;
                velX = velY = velZ = 0F;
            } else {
                velX += (instX - velX) * 0.3F;
                velY += (instY - velY) * 0.3F;
                velZ += (instZ - velZ) * 0.3F;
            }
            lastTX = target.getX();
            lastTY = target.getY();
            lastTZ = target.getZ();
            lastMs = ms;
        }

        boolean onTarget = RayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(),
                ranges[0], target);
        boolean settling = ms < settleUntil;
        if (onTarget) {
            lastOnTarget = ms;
        }
        boolean lostTooLong = !onTarget && ms - lastOnTarget > 400L;

        // ── точка прицела: лучшая точка + блуждание + упреждение ──
        float speed2D = (float) Math.hypot(velX, velZ);
        if (ms >= nextRepick) {
            boolean moving = speed2D > 1.5F;
            nextRepick = ms + (moving ? MathUtil.randomInt(90, 180) : MathUtil.randomInt(140, 380));
            float spread = moving ? 0.10F : 0.14F;
            offX = MathUtil.randomGaussian(-spread, spread) * (float) (box.maxX - box.minX);
            offY = MathUtil.randomGaussian(-spread * 0.7F, spread * 1.15F) * (float) (box.maxY - box.minY);
            offZ = MathUtil.randomGaussian(-spread, spread) * (float) (box.maxZ - box.minZ);
        }
        if (canAttack && onTarget) {
            nextRepick = Math.max(nextRepick, ms + 60L);
        }

        Vec3d base = UBoxPoints.getBestVector3dOnEntityBox(box);
        float leadSec = MathHelper.clamp(
                (float) mc.player.getEyePos().distanceTo(base) / 14.0F, 0.04F, 0.15F);
        float leadX = MathHelper.clamp(velX * leadSec, -1.0F, 1.0F);
        float leadY = MathHelper.clamp(velY * leadSec, -0.5F, 0.5F);
        float leadZ = MathHelper.clamp(velZ * leadSec, -1.0F, 1.0F);

        Vec3d aim = new Vec3d(
                MathHelper.clamp(base.x + offX + leadX, box.minX + 0.03, box.maxX - 0.03),
                MathHelper.clamp(base.y + offY + leadY, box.minY + 0.03, box.maxY - 0.03),
                MathHelper.clamp(base.z + offZ + leadZ, box.minZ + 0.03, box.maxZ - 0.03)
        ).subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90F, 90F);

        // ── пропорциональный контроллер скорости ──
        float errYaw = Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
        float errPitch = Math.abs(pitch - mc.player.getPitch());
        float err = (float) Math.hypot(errYaw, errPitch);

        float wantYaw = MathHelper.clamp(err * MathUtil.randomLerp(2.2F, 3.2F), 2.0F, 90F);
        float wantPitch = MathHelper.clamp(err * MathUtil.randomLerp(0.8F, 1.3F), 1.5F, 28F);

        if (onTarget) {
            wantYaw = Math.min(wantYaw, MathUtil.randomLerp(2.0F, 4.0F));
            wantPitch = Math.min(wantPitch, MathUtil.randomLerp(1.0F, 2.0F));
        }
        if (settling) {
            wantYaw = Math.min(wantYaw, 1.8F);
            wantPitch = Math.min(wantPitch, 1.2F);
        }
        if (canAttack && !onTarget) {
            wantYaw = Math.max(wantYaw, MathUtil.randomLerp(40F, 70F));
            wantPitch = Math.max(wantPitch, MathUtil.randomLerp(8F, 16F));
        }
        if (teleported) {
            wantYaw = Math.max(wantYaw, 70F);
        }
        if (lostTooLong) {
            offX *= 0.5F;
            offY *= 0.5F;
            offZ *= 0.5F;
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
