package fun.newrar.utils.aura;

import fun.newrar.manager.rotation.Rotation;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.math.MathUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.math.MathHelper.clamp;

@UtilityClass
public class AuraUtil implements IMinecraft {
    public double[] calculateDirection(double distance) {
        float[] movement = getMovementFromKeys();
        return calculateDirection(
                movement[0],
                movement[1],
                distance
        );
    }
    public boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }
    public boolean isPlayerInWeb() {
        Box playerBox = mc.player.getBoundingBox();
        BlockPos playerPosition = mc.player.getBlockPos();

        return getNearbyBlockPositions(playerPosition).stream()
                .anyMatch(pos -> isBlockCobweb(playerBox, pos));
    }
    private boolean isBlockCobweb(Box playerBox, BlockPos pos) {
        if (!mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
            return false;
        }

        Box blockBox = new Box(pos);
        return playerBox.intersects(blockBox);
    }
    private List<BlockPos> getNearbyBlockPositions(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
            for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
                for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    public double[] calculateDirection(float forward, float sideways, double distance) {
        float yaw = mc.player.getYaw();
        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += (forward > 0.0f) ? -45 : 45;
            } else if (sideways < 0.0f) {
                yaw += (forward > 0.0f) ? 45 : -45;
            }
            sideways = 0.0f;
            forward = (forward > 0.0f) ? 1.0f : -1.0f;
        }

        double sinYaw = Math.sin(Math.toRadians(yaw + 90.0f));
        double cosYaw = Math.cos(Math.toRadians(yaw + 90.0f));
        double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
        double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;

        return new double[]{xMovement, zMovement};
    }
    public float[] getMovementFromKeys() {
        float forward = 0;
        float strafe = 0;

        Window handle = mc.getWindow();

        if (InputUtil.isKeyPressed(handle, mc.options.forwardKey.getDefaultKey().getCode())) {
            forward += 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.backKey.getDefaultKey().getCode())) {
            forward -= 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.leftKey.getDefaultKey().getCode())) {
            strafe += 1.0F;
        }
        if (InputUtil.isKeyPressed(handle, mc.options.rightKey.getDefaultKey().getCode())) {
            strafe -= 1.0F;
        }

        return new float[]{forward, strafe};
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }
    public static Rotation getOffset(Rotation deltaToTarget, float progress) {
        float curveStrength = 3.0f;
        float smoothness = 1.0f - (float) Math.cos(progress * Math.PI);

        float yawSign = Math.signum(deltaToTarget.getYaw());
        float pitchSign = Math.signum(deltaToTarget.getPitch());

        float offsetYaw = 0f;
        float offsetPitch = 0f;

        boolean verticalAiming = Math.abs(deltaToTarget.getYaw()) < 1.5f && Math.abs(deltaToTarget.getYaw()) > 10f;

        if (verticalAiming) {
            offsetYaw = pitchSign * curveStrength * (1f - progress);
            offsetPitch = 0f;
        } else {
            if (pitchSign > 0 && yawSign >= 0) {
                offsetYaw = -curveStrength * (1f - progress);
                offsetPitch = -curveStrength * smoothness;
            } else if (pitchSign > 0 && yawSign < 0) {
                offsetYaw = curveStrength * (1f - progress);
                offsetPitch = -curveStrength * smoothness;
            } else if (pitchSign < 0 && yawSign >= 0) {
                offsetYaw = -curveStrength * (1f - progress);
                offsetPitch = curveStrength * smoothness;
            } else if (pitchSign < 0 && yawSign < 0) {
                offsetYaw = curveStrength * (1f - progress);
                offsetPitch = curveStrength * smoothness;
            }
        }

        return new Rotation(offsetYaw, offsetPitch);
    }
    public static double getStrictDistance(Entity entity) {
        return getClosestVec(entity).length();
    }
    public Rotation cameraAngle() {return new Rotation(mc.player.getYaw(), mc.player.getPitch());}
    public static float interpolate(double oldValue, double newValue, double interpolationValue) {
        return (float)(oldValue + (newValue - oldValue) * interpolationValue);
    }
    public static boolean validDistance(Entity entity, float distance, boolean smart) {
        if (entity instanceof LivingEntity living) {
            return ServerReach.canReach(living, distance);
        }
        return getStrictDistance(entity) < distance;
    }
    public static Vec3d getClosestVec(Entity entity) {
        Vec3d eyePosVec = mc.player.getEyePos();
        return getClosestVec(eyePosVec, entity).subtract(eyePosVec);
    }

    public static Vec3d getClosestVec(Vec3d vec, Box AABB) {
        return new Vec3d(
                MathUtil.clamp(vec.x, AABB.minX, AABB.maxX),
                MathUtil.clamp(vec.y, AABB.minY, AABB.maxY),
                MathUtil.clamp(vec.z, AABB.minZ, AABB.maxZ)
        );
    }

    public static Vec3d getClosestVec(Vec3d vec, Entity entity) {
        return getClosestVec(vec, entity.getBoundingBox());
    }

    public static Vec3d getVector3(LivingEntity target) {
        return new Vec3d(
                target.getX() - mc.player.getX(),
                target.getY() - mc.player.getY()  - 0.8F,
                target.getZ() - mc.player.getZ()
        );
    }

    public static Vec3d getVector2(LivingEntity target) {
        double yExpand = clamp(target.getEyeY() - target.getY(), 0, target.getHeight());

        return new Vec3d(
                target.getX() - mc.player.getX(),
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ()
        );
    }

    public static Vec3d getVector(LivingEntity target) {
        double wHalf = target.getWidth() / 2;

        double yExpand = clamp(target.getEyeY() - target.getY(), 0, target.getHeight());

        double xExpand = clamp(mc.player.getX() - target.getX(), -wHalf, wHalf);
        double zExpand = clamp(mc.player.getZ() - target.getZ(), -wHalf, wHalf);

        return new Vec3d(
                target.getX() - mc.player.getX() + xExpand,
                target.getY() - mc.player.getEyeY() + yExpand,
                target.getZ() - mc.player.getZ() + zExpand
        );
    }

    public static double direction(float rotationYaw, final float moveForward, final float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    public Vec3d getClosestTargetPoint(Vec3d vec, Entity entity, float point) {
        if (entity == null) {
            return Vec3d.ZERO;
        }

        Box box = entity.getBoundingBox().expand(-point);
        Vec3d center = box.getCenter();
        Vec3d closestPoint = null;
        double closestDistance = Double.MAX_VALUE;

        for (double offsetX = 0; offsetX <= (box.maxX - box.minX) / 2; offsetX += 0.1) {
            for (double offsetY = 0; offsetY <= (box.maxY - box.minY) / 2; offsetY += 0.1) {
                for (double offsetZ = 0; offsetZ <= (box.maxZ - box.minZ) / 2; offsetZ += 0.1) {
                    for (int signX : new int[]{-1, 1}) {
                        for (int signY : new int[]{-1, 1}) {
                            for (int signZ : new int[]{-1, 1}) {
                                double x = center.x + signX * offsetX;
                                double y = center.y + signY * offsetY;
                                double z = center.z + signZ * offsetZ;
                                Vec3d potentialPoint = new Vec3d(x, y, z);

                                Vector2f rotation = calculate(potentialPoint);

                                HitResult result = RayTraceUtil.calculateRayTrace(
                                        6.0D,
                                        rotation.x,
                                        rotation.y,mc.player,false
                                );

                                if (result instanceof EntityHitResult entityTrace && entityTrace.getEntity().equals(entity)) {
                                    double distance = vec.distanceTo(potentialPoint);
                                    if (distance < closestDistance) {
                                        closestDistance = distance;
                                        closestPoint = potentialPoint;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (closestPoint != null) {
            return closestPoint;
        }

        double closestX = MathUtil.clamp(vec.x, box.minX, box.maxX);
        double closestY = MathUtil.clamp(vec.y, box.minY, box.maxY);
        double closestZ = MathUtil.clamp(vec.z, box.minZ, box.maxZ);

        return new Vec3d(closestX, closestY, closestZ);
    }

    public Vector2f calculate(final Vec3d toVec) {
        return calculate(mc.player.getEntityPos().add(0, mc.player.getEyeY(), 0), toVec);
    }

    public Vector2f calculate(final Vec3d fromVec, final Vec3d toVec) {
        final double TO_DEGREES = 180.0F / Math.PI;
        final Vec3d diff = toVec.subtract(fromVec);
        final double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (MathHelper.atan2(diff.z, diff.x) * TO_DEGREES) - 90.0F;
        final float pitch = (float) (-(MathHelper.atan2(diff.y, distance) * TO_DEGREES));
        return new Vector2f(yaw, pitch);
    }
}

