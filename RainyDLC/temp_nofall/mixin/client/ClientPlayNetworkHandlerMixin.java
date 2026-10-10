package su.DSF.calcite.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.DSF.calcite.client.modules.impl.player.NoFall;

@Mixin({ClientPacketListener.class})
public class ClientPlayNetworkHandlerMixin {
   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("TAIL")}
   )
   private void calcite$afterTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      NoFall.onTeleportApplied();
   }

   @Inject(
      method = {"handleSetEntityMotion"},
      at = {@At("TAIL")}
   )
   private void calcite$afterMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
      NoFall.onMotionApplied(packet.id());
   }
}
