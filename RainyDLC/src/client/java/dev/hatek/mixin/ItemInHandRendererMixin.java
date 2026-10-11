package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Redirect(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"
            )
    )
    private float hatek$redirectViewXRot(LocalPlayer player, float partialTicks) {
        if (FreeLookUtil.detached()) {
            return FreeLookUtil.freePitch;
        }
        return player.getViewXRot(partialTicks);
    }

    @Redirect(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"
            )
    )
    private float hatek$redirectViewYRot(LocalPlayer player, float partialTicks) {
        if (FreeLookUtil.detached()) {
            return FreeLookUtil.freeYaw;
        }
        return player.getViewYRot(partialTicks);
    }

    @Redirect(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getXRot(F)F"
            )
    )
    private float hatek$redirectGetXRot(LocalPlayer player, float partialTicks) {
        if (FreeLookUtil.detached()) {
            return FreeLookUtil.freePitch;
        }
        return player.getXRot(partialTicks);
    }
}
