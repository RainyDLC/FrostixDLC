package dev.hatek.client.render.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Утилиты спавна/коллизий частиц в мире. Порт rtx.kimiko WorldParticleUtil,
 * маппинги переведены на 26.2 official (Vec3, BlockPos.containing, ClientLevel).
 */
public final class WorldParticleUtil {
    private WorldParticleUtil() {
    }

    /** Случайная точка вокруг at на дистанции [maxDistance/5, maxDistance], вне твёрдых блоков. */
    public static Vec3 randomSpawnPosition(Minecraft mc, Random random, float maxDistance, Vec3 at) {
        if (mc.level == null) {
            return null;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 vec = randomVec(random, at, maxDistance / 5.0f, maxDistance);
            if (isSolidCollisionBlock(mc, vec.x, vec.y, vec.z)) {
                continue;
            }
            return vec;
        }
        return null;
    }

    public static boolean isSolidCollisionBlock(Minecraft mc, double x, double y, double z) {
        ClientLevel level = mc.level;
        if (level == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        // Тот же шаблон, что в CritUtil:142 (origin/main).
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 randomVec(Random random, Vec3 at, float minDistance, float maxDistance) {
        float minDist = Math.min(minDistance, maxDistance);
        float maxDist = Math.max(minDistance, maxDistance);
        float yaw = -180.0f + random.nextFloat() * 360.0f;
        float pitch = -90.0f + random.nextFloat() * 180.0f;
        float distance = minDist == maxDist ? minDist : minDist + random.nextFloat() * (maxDist - minDist);
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return at.add(
                Math.cos(pitchRad) * Math.sin(yawRad) * -distance,
                Math.sin(pitchRad) * -distance,
                Math.cos(pitchRad) * Math.cos(yawRad) * distance);
    }
}
