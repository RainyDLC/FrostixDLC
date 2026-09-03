package fun.newrar.mixin;

import fun.newrar.module.impl.player.PvpSafe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientDisconnectMixin {
    @Inject(
            method = {"disconnectWithProgressScreen()V", "disconnectWithSavingScreen()V"},
            at = @At("HEAD"),
            cancellable = true
    )
    private void pvpSafe$blockLeave(CallbackInfo ci) {
        if (PvpSafe.isActive()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "disconnectWithProgressScreen(Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void pvpSafe$blockLeaveTransfer(boolean transferring, CallbackInfo ci) {
        if (PvpSafe.isActive()) {
            ci.cancel();
        }
    }
}

