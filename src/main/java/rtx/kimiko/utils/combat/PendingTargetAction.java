package rtx.kimiko.utils.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class PendingTargetAction {
    public static PlayerEntity target;
    public static Runnable deferred;
    public static Runnable callback;
    public static boolean immediate;

    private PendingTargetAction() {
    }

    public static void arm(PlayerEntity player, Runnable runnable, boolean isImmediate) {
        target = player;
        callback = runnable;
        immediate = isImmediate;
    }

    public static boolean isTarget(Entity entity) {
        return target != null && entity == target;
    }

    public static void onTargetDeath(Entity entity) {
        if (!isTarget(entity)) {
            return;
        }
        Runnable cb = callback;
        boolean imm = immediate;
        clearTarget(entity);
        if (cb != null) {
            if (imm) {
                cb.run();
            } else {
                deferred = cb;
            }
        }
    }

    public static boolean hasPending() {
        return deferred != null;
    }

    public static void flush() {
        if (deferred == null) {
            return;
        }
        Runnable r = deferred;
        deferred = null;
        r.run();
    }

    public static void watch(PlayerEntity player) {
        watchWithCallback(player, null);
    }

    public static void watchWithCallback(PlayerEntity player, Runnable runnable) {
        arm(player, runnable, false);
    }

    public static void clearTarget(Entity entity) {
        if (target != null && entity == target) {
            target = null;
            callback = null;
            immediate = false;
        }
    }

    public static boolean isImmediateTarget(Entity entity) {
        return isTarget(entity) && immediate;
    }
}
