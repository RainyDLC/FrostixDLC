package rtx.kimiko.utils.math.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.utils.math.MathUtil;

public final class RotationUtil {
    private RotationUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Vec3d clampToBox(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return entity.getEntityPos();
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        return new Vec3d(
                MathHelper.clamp(eyePos.x, box.minX, box.maxX),
                MathHelper.clamp(eyePos.y, box.minY, box.maxY),
                MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
        );
    }

    public static Vec3d clampPointToBox(Entity entity, Vec3d point) {
        Box box = entity.getBoundingBox();
        return new Vec3d(
                MathHelper.clamp(point.x, box.minX, box.maxX),
                MathHelper.clamp(point.y, box.minY, box.maxY),
                MathHelper.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public static Vec3d getTargetVector(LivingEntity entity, Vec3d targetPoint) {
        return clampToBox(entity).subtract(entity.getEntityPos()).add(targetPoint);
    }

    public static Rotation getRotationToVector(Vec3d vector) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Rotation.ZERO;
        double dx = vector.getX() - mc.player.getX();
        double dy = vector.getY() - mc.player.getEyeY();
        double dz = vector.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new Rotation(yaw, pitch);
    }

    public static Rotation getRotationBetween(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new Rotation(yaw, pitch);
    }

    public static float getRotationStep() {
        MinecraftClient mc = MinecraftClient.getInstance();
        double mouseSensitivity = mc.options.getMouseSensitivity().getValue();
        double f = mouseSensitivity * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    public static Rotation snapRotation(Rotation current, Rotation target) {
        float step = getRotationStep();
        float deltaYaw = MathHelper.wrapDegrees(target.getYaw() - current.getYaw());
        float deltaPitch = target.getPitch() - current.getPitch();
        deltaYaw = (float) Math.round(deltaYaw / step) * step;
        deltaPitch = (float) Math.round(deltaPitch / step) * step;
        return new Rotation(current.getYaw() + deltaYaw, MathHelper.clamp(current.getPitch() + deltaPitch, -90.0f, 90.0f));
    }

    public static float snapYaw(float current, float target) {
        return snapRotation(new Rotation(current, 0.0f), new Rotation(target, 0.0f)).getYaw();
    }

    public static float snapPitch(float current, float target) {
        return snapRotation(new Rotation(0.0f, current), new Rotation(0.0f, target)).getPitch();
    }

    public static int getYawSteps(float current, float target) {
        float step = getRotationStep();
        return Math.round(MathHelper.wrapDegrees(target - current) / step);
    }

    public static int getStepCount(float from, float to) {
        float step = getRotationStep();
        return Math.round((to - from) / step);
    }

    public static float wrapTargetYaw(float current, float target) {
        float c = current % 360.0f;
        if (c < 0.0f) c += 360.0f;
        float t = target % 360.0f;
        if (t < 0.0f) t += 360.0f;
        int n = (int) (current / 360.0f);
        if (current < 0.0f && current % 360.0f != 0.0f) n--;
        float result = t + (float) (n * 360);
        float diff = result - current;
        if (diff > 180.0f) {
            result -= 360.0f;
        } else if (diff < -180.0f) {
            result += 360.0f;
        }
        return result;
    }

    public static float getAngleDifference(float a, float b) {
        float diff;
        for (diff = b - a; diff > 180.0f; diff -= 360.0f) {}
        while (diff < -180.0f) diff += 360.0f;
        return diff;
    }

    public static Rotation getRotationToEntity(LivingEntity entity, Vec3d targetPoint) {
        Rotation rotation = getRotationToVector(getTargetVector(entity, targetPoint));
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.getEyePos().squaredDistanceTo(entity.getEyePos()) < 9.0) {
            Vec3d centerPoint = targetPoint.add(0.0, entity.getHeight() / 2.0, 0.0);
            rotation = getRotationToVector(centerPoint);
        }
        if (rotation.getPitch() == (float) ((int) rotation.getPitch())) {
            rotation.setPitch(MathHelper.clamp(rotation.getPitch() + MathUtil.randomYawVariant(-1.0f, 1.0f), -90.0f, 90.0f));
        }
        if (rotation.getYaw() == (float) ((int) rotation.getYaw())) {
            rotation.setYaw(rotation.getYaw() + MathUtil.randomYawVariant(-1.0f, 1.0f));
        }
        return rotation;
    }
}
