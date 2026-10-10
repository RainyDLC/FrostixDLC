package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.GodModeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ванильный handleContainerClose игнорирует containerId пакета и сносит текущее
 * меню без проверки. Если сервер присылает запоздалое закрытие СТАРОГО меню
 * (например, после клика по категории "Серверные варпы"), оно убивает актуальное
 * подменю. Пока GodModeMenu держит десинк — отбрасываем закрытия чужого ID.
 */
@Mixin(ClientPacketListener.class)
public class ContainerCloseMixin {
    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    private void hatek$ignoreStaleClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        GodModeMenu menu = GodModeMenu.instance();
        if (menu == null || !menu.isFakeClosed()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu current = mc.player != null ? mc.player.containerMenu : null;
        int currentId = current != null ? current.containerId : -1;
        int packetId = packet.getContainerId();
        if (currentId != packetId) {
            menu.debug("игнорирую закрытие меню #" + packetId + " (текущее #" + currentId + ")");
            ci.cancel();
        }
    }
}
