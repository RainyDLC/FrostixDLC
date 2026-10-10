package dev.hatek.mixin;

import dev.hatek.client.module.impl.player.NoFall;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks the server packets that complete the Grim no-fall sequence:
 * the corrective teleport ({@code handleMovePlayer}) and the entity
 * velocity applied to the player ({@code handleSetEntityMotion}).
 */
@Mixin(ClientPacketListener.class)
public class NoFallPacketMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void hatek$onTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        NoFall.onTeleportApplied();
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void hatek$onEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        NoFall.onMotionApplied(packet.id());
    }
}
