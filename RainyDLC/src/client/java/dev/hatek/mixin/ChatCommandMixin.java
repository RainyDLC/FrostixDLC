package dev.hatek.mixin;

import dev.hatek.client.feature.config.ChatCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехватывает отправку чата: сообщения на «.» — команды клиента
 * (см. ChatCommands), на сервер не уходят.
 */
@Mixin(ClientPacketListener.class)
public class ChatCommandMixin {
    @Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void hatek$onChat(String message, CallbackInfo ci) {
        if (ChatCommands.handle(message)) {
            ci.cancel();
        }
    }
}
