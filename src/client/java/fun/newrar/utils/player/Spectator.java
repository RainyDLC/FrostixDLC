package fun.newrar.utils.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

public final class Spectator {
    private static Entity target;

    private Spectator() {
    }

    public static boolean isActive() {
        return target != null;
    }

    public static Entity getTarget() {
        return target;
    }

    public static boolean start(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || mc.player == null || mc.world == null) return false;
        if (entity == mc.player || entity.isRemoved()) return false;

        target = entity;
        mc.setCameraEntity(entity);

        if (mc.worldRenderer != null) {
            mc.worldRenderer.scheduleTerrainUpdate();
        }
        return true;
    }

    public static boolean isTerrainLoaded(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || mc.world == null) return false;
        ChunkPos pos = entity.getChunkPos();
        return mc.world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null;
    }

    public static int loadedChunksAround(Entity entity, int radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || mc.world == null) return 0;

        ChunkPos center = entity.getChunkPos();
        int loaded = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (mc.world.getChunkManager().getChunk(center.x + x, center.z + z, ChunkStatus.FULL, false) != null) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    public static int chunksInSquare(int radius) {
        int side = radius * 2 + 1;
        return side * side;
    }

    public static void stop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        target = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    public static void reset() {
        target = null;
    }

    public static boolean validate() {
        if (target == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null
                || target.isRemoved()
                || target.getEntityWorld() != mc.world
                || (target instanceof LivingEntity living && !living.isAlive())) {
            stop();
            return true;
        }

        if (mc.getCameraEntity() != target) {
            mc.setCameraEntity(target);
        }
        return false;
    }
}

