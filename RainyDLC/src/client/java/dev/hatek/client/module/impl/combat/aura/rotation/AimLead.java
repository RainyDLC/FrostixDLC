package dev.hatek.client.module.impl.combat.aura.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Упреждение цели для ротаций ауры.
 *
 * <p>Выделено из удалённой SpaceTime-ротации: эти две утилиты использует
 * NeuroRotation, поэтому они живут отдельно.
 */
public final class AimLead {
    /** Доля тика, на которую упреждаем цель (хедж между «уже сдвинулась» и «ещё нет»). */
    private static final double LEAD_FRACTION = 0.5;

    /** Предел упреждения за тик — дальше это телепорт/рывок, а не ход. */
    private static final double MAX_TICK_LEAD = 0.6;

    private static final double STILL_VEL_SQR = 1.0E-6;

    private AimLead() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Бокс цели, сдвинутый на половину её реального хода за тик (x - xo).
     *
     * <p>Удар идёт в TAIL нашего тика, и цель в этом же тике может успеть или не успеть
     * сдвинуться (зависит от порядка тика сущностей). Луч через центр полусдвинутого
     * бокса попадает в оба варианта, пока шаг меньше ширины хитбокса.
     */
    public static AABB leadedBox(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        if (dx * dx + dy * dy + dz * dz < STILL_VEL_SQR) {
            return box;
        }
        return box.move(
                Mth.clamp(dx * LEAD_FRACTION, -MAX_TICK_LEAD, MAX_TICK_LEAD),
                Mth.clamp(dy * LEAD_FRACTION, -MAX_TICK_LEAD, MAX_TICK_LEAD),
                Mth.clamp(dz * LEAD_FRACTION, -MAX_TICK_LEAD, MAX_TICK_LEAD));
    }

    /**
     * Глаза, откуда сервер увидит удар: позиция после нашего хода в этом тике.
     * Ротация считается до aiStep, а удар — после него; при стрейфе вокруг цели
     * свой сдвиг до 0.3 блока уводил луч к краю хитбокса.
     */
    public static Vec3 predictedEye() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 v = mc.player.getDeltaMovement();
        double vy = mc.player.onGround() ? 0.0 : v.y;
        return eye.add(Mth.clamp(v.x, -MAX_TICK_LEAD, MAX_TICK_LEAD),
                Mth.clamp(vy, -MAX_TICK_LEAD, MAX_TICK_LEAD),
                Mth.clamp(v.z, -MAX_TICK_LEAD, MAX_TICK_LEAD));
    }
}
