package rtx.kimiko.utils.math;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import rtx.kimiko.api.combat.aura.AuraWallsMode;

import java.security.SecureRandom;

public final class MathUtil {
    public static final SecureRandom secureRandom = new SecureRandom();

    private MathUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static float angleDifference(float a, float b) {
        float diff = (a - b) % 360.0f;
        if (diff < -180.0f) {
            diff += 360.0f;
        } else if (diff > 180.0f) {
            diff -= 360.0f;
        }
        return diff;
    }

    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float f = -yaw * ((float)Math.PI / 180.0f) - (float)Math.PI;
        float f1 = -pitch * ((float)Math.PI / 180.0f);
        float f2 = MathHelper.cos(f);
        float f3 = MathHelper.sin(f);
        float f4 = -MathHelper.cos(f1);
        float f5 = MathHelper.sin(f1);
        return new Vec3d((double)(f3 * f4), (double)f5, (double)(f2 * f4));
    }

    public static float lerp(double a, double b, double pct) {
        return (float)(a + (b - a) * pct);
    }

    public static float lerpFloat(float a, float b, float pct) {
        return a + (b - a) * pct;
    }

    public static float randomFloat(double min, double max) {
        return (float)(min + (max - min) * Math.random());
    }

    public static float randomRange(float min, float max) {
        return min + (max - min) * (float)Math.random();
    }

    public static float randomInRange(float min, float max) {
        return min + (max - min) * secureRandom.nextFloat();
    }

    public static float randomAveraged(float min, float max) {
        float r1 = secureRandom.nextFloat();
        float r2 = secureRandom.nextFloat();
        return min + (max - min) * ((r1 + r2) / 2.0f);
    }

    public static float randomGaussian(float mean, float stdDev) {
        return (float)secureRandom.nextGaussian() * stdDev + mean;
    }

    public static float randomGaussianRaw(float mean, float stdDev) {
        return (float)secureRandom.nextGaussian() * stdDev + mean;
    }

    public static float randomGaussianAveraged(float mean, float stdDev) {
        float g1 = (float)secureRandom.nextGaussian() * stdDev + mean;
        float g2 = (float)secureRandom.nextGaussian() * stdDev + mean;
        return (g1 + g2) / 2.0f;
    }

    public static float randomHumanized(float min, float max) {
        double d = secureRandom.nextDouble();
        double d2 = secureRandom.nextDouble();
        double d3 = secureRandom.nextGaussian() * 0.02;
        double d4 = Math.pow(d, 1.0 + secureRandom.nextDouble() * 0.7);
        double d5 = (d2 * 0.8 + 0.1) * (Math.log1p(d * 3.0) * 0.5 + 0.5);
        return (float)((double)min + (double)(max - min) * d4 * d5 + d3);
    }

    public static float randomHumanizedGaussian(float mean, float stdDev) {
        double d = secureRandom.nextGaussian() * (double)stdDev + (double)mean;
        double d2 = secureRandom.nextGaussian();
        double d3 = secureRandom.nextGaussian() * 0.02;
        double d4 = Math.pow(Math.abs(d2), 1.0 + secureRandom.nextDouble() * 0.7);
        double d5 = (Math.abs(d2) * 0.8 + 0.1) * (Math.log1p(Math.abs(d - (double)mean) * 3.0) * 0.5 + 0.5);
        return (float)((double)mean + (double)stdDev * d4 * d5 + d3);
    }

    public static float randomYawVariant(float min, float max) {
        return switch (secureRandom.nextInt(4)) {
            case 0 -> randomAveraged(min, max);
            case 1 -> randomHumanized(min, max);
            case 2 -> randomRange(min, max);
            default -> randomInRange(min, max);
        };
    }

    public static float randomPitchVariant(float min, float max) {
        return switch (secureRandom.nextInt(4)) {
            case 0 -> randomGaussianAveraged(min, max);
            case 1 -> randomHumanizedGaussian(min, max);
            case 2 -> randomGaussian(min, max);
            default -> randomGaussianRaw(min, max);
        };
    }

    public static boolean canReach(double reach, float yaw, float pitch, Entity player, Entity target, AuraWallsMode wallsMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || player == null || mc.world == null) {
            return false;
        }
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw);
        Vec3d reachVec = eyePos.add(lookVec.multiply(reach));

        if (!wallsMode.isRaycastMode()) {
            Box targetBox = target.getBoundingBox();
            if (targetBox.contains(player.getEyePos())) {
                return true;
            }
            BlockHitResult hit = raycastBlocks(eyePos, reachVec, player, wallsMode);
            if (hit != null && hit.getPos().squaredDistanceTo(eyePos) < target.getEyePos().squaredDistanceTo(eyePos)) {
                return false;
            }
        }
        return intersectsBox(eyePos, reachVec, target, player);
    }

    public static BlockHitResult raycastBlocks(Vec3d start, Vec3d end, Entity entity, AuraWallsMode wallsMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || wallsMode.isRaycastMode()) {
            return null;
        }
        Vec3d diff = end.subtract(start);
        double distSq = diff.lengthSquared();
        if (distSq < 1.0E-8) {
            return null;
        }
        BlockHitResult initialHit = raycastBlockAt(start, entity, wallsMode);
        if (initialHit != null) {
            return initialHit;
        }
        Vec3d step = diff.normalize().multiply(0.01);
        Vec3d current = start;
        for (int i = 0; i < 40; ++i) {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    current, end,
                    RaycastContext.ShapeType.COLLIDER,
                    wallsMode.getFluidHandling(),
                    entity
            ));
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            BlockPos blockPos = hit.getBlockPos();
            if (!wallsMode.canPassThrough(mc.world, blockPos, mc.world.getBlockState(blockPos))) {
                return hit;
            }
            current = hit.getPos().add(step);
            if (current.squaredDistanceTo(start) >= distSq) {
                return null;
            }
        }
        return null;
    }

    private static BlockHitResult raycastBlockAt(Vec3d point, Entity entity, AuraWallsMode wallsMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || wallsMode.getFluidHandling() != RaycastContext.FluidHandling.NONE) {
            return null;
        }
        BlockPos pos = BlockPos.ofFloored(point);
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || wallsMode.canPassThrough(mc.world, pos, state)) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(mc.world, pos, ShapeContext.of(entity));
        if (shape.isEmpty()) {
            return null;
        }
        return new BlockHitResult(point, Direction.UP, pos, true);
    }

    public static boolean intersectsBox(Vec3d start, Vec3d end, Entity target, Entity player) {
        Box targetBox = target.getBoundingBox();
        return targetBox.raycast(start, end).isPresent();
    }
}
