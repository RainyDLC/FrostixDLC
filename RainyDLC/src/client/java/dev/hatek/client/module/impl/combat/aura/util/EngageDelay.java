package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Человеческая задержка реакции на новую цель (150–350 мс).
 *
 * <p>Мгновенная реакция ротации на появление цели в радиусе — классический
 * детект-сигнал: человек физически не успевает отреагировать быстрее
 * ~150 мс. При смене цели наведение просто не начинается первые тики.</p>
 */
public final class EngageDelay {
    private static int lastId = -2;
    private static int holdUntil = Integer.MIN_VALUE;

    private EngageDelay() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void reset() {
        lastId = -2;
        holdUntil = Integer.MIN_VALUE;
    }

    /**
     * @return true — на эту цель ещё рано наводиться, кадр ротации пропускаем.
     *         Идемпотентен: можно вызывать каждый кадр.
     */
    public static boolean hold(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        int tick = mc.player.tickCount;
        int id = target == null ? -1 : target.getId();
        if (id != lastId) {
            lastId = id;
            holdUntil = id == -1 ? Integer.MIN_VALUE : tick + 3 + AuraRandom.range(0, 4);
        }
        return target != null && tick < holdUntil;
    }
}
