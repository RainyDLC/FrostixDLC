package su.DSF.calcite.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.DSF.calcite.api.events.PacketEvent;

@Mixin({Connection.class})
public class ClientConnectionMixin {
   @Inject(
      method = {"channelRead0"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void calcite$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
      PacketEvent.Receive event = new PacketEvent.Receive(packet).call();
      if (event.isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"sendPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void calcite$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      PacketEvent.Send event = new PacketEvent.Send(packet).call();
      if (event.isCancelled()) {
         ci.cancel();
      }
   }
}
