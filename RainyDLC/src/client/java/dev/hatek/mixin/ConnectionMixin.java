package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import dev.hatek.client.module.impl.combat.aura.util.LagGuard;
import io.netty.channel.ChannelHandlerContext;
import dev.hatek.client.module.impl.player.NoFall;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;"
                    + "Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hatek$onSend(Packet<?> packet, ChannelFutureListener listener, boolean flush,
                              CallbackInfo ci) {
        ComponentManager.packetSent(packet);
        if (NoFall.onPacketSend(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;"
                    + "Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void hatek$onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        LagGuard.onPacketReceived(packet);
        dev.hatek.client.module.impl.render.interf.quick.QuickTelemetry.packet(packet);
    }
}
