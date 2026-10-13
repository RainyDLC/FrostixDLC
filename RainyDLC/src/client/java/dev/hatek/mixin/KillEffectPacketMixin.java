package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.KillEffect;
import dev.hatek.client.render.killeffect.KillEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe server events on the client thread. Never rewrite, cancel or send packets. */
@Mixin(ClientPacketListener.class)
public abstract class KillEffectPacketMixin {
    // getEntity is AFTER PacketUtils' thread guard and BEFORE vanilla handles/removes the corpse.
    @Inject(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private void hatek$confirmedDeath(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.getEventId() == 3 && mc.level != null
                && packet.getEntity(mc.level) instanceof AbstractClientPlayer player) {
            KillEffect.death(player);
        }
    }
    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void hatek$removedCorpse(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        for (int id : packet.getEntityIds()) KillEffectRenderer.removed(id);
    }
}
