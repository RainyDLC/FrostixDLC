package dev.hatek.client.render.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Проверка блоков для коллизий частиц.
 * Чистая реализация с нуля (шаблон — как в CritUtil из origin/main).
 */
public final class WorldParticleUtil {
    private WorldParticleUtil() {
    }

    /** true — в точке твёрдый блок с коллизией. */
    public static boolean isSolidCollisionBlock(Minecraft mc, double x, double y, double z) {
        ClientLevel level = mc.level;
        if (level == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }
}
