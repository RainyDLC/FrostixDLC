package fun.newrar.module.impl.movement;

import fun.newrar.Client;
import fun.newrar.mixin.ClientWorldAccessor;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Снятие замедления в паутине.
 *
 * Клиент: CobwebBlockMixin отменяет CobwebBlock#onEntityCollision, поэтому movementMultiplier
 * остаётся Vec3d.ZERO — Entity#move не режет движение и не обнуляет velocity.
 * Сервер: на каждом касании паутины уходит sequence-пакет STOP_DESTROY_BLOCK
 * (PendingUpdateManager). Grim и форки после него не применяют паутинное замедление
 * к предсказанию движения — без этого клиентская отмена видна и её откатывает.
 *
 * Никаких режимов и бустов: только снятие.
 */
@ModuleInfo(
        name = "No Web",
        desc = "Снятие замедления при передвижении внутри блоков паутины",
        category = Category.MOVEMENT
)
public class NoWeb extends Module {
    public static NoWeb get() {
        try {
            return Client.get() != null && Client.get().moduleManager() != null
                    ? Client.get().moduleManager().get(NoWeb.class) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Дёргается из CobwebBlockMixin на каждом касании паутины. */
    public static void onEntityCollideCobweb(BlockPos pos) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        try {
            PendingUpdateManager pending = ((ClientWorldAccessor) mc.world).rainydlc$getPendingUpdateManager();
            try (PendingUpdateManager updates = pending.incrementSequence()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, updates.getSequence()));
            }
        } catch (Throwable ignored) {
        }
    }
}
