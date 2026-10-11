package dev.hatek.mixin;

import dev.hatek.client.event.ClientEvents;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void hatek$onPlayerTick(CallbackInfo ci) {
        ClientEvents.playerTick();
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void hatek$stabilizeHandBob(CallbackInfo ci) {
        if (dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.detached()) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            self.xBob = dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.freePitch;
            self.xBobO = dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.freePitch;
            self.yBob = dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.freeYaw;
            self.yBobO = dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.freeYaw;
        }
    }
}
