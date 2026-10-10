package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.GodModeMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ручной выбор варпа: в режиме Manual Select клик по слоту в меню варпов
 * перехватывается — слот запоминается, меню прячется (десинк), клик отменяется
 * (телепорта не происходит).
 */
@Mixin(AbstractContainerScreen.class)
public class ManualWarpCaptureMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void hatek$captureWarpClick(Slot slot, int slotId, int mouseButton, ContainerInput clickType,
                                       CallbackInfo ci) {
        GodModeMenu menu = GodModeMenu.instance();
        if (menu != null && menu.tryManualClickCapture(slot)) {
            ci.cancel();
        }
    }
}
