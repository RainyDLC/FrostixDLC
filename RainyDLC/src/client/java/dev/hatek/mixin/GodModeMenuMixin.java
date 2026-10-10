package dev.hatek.mixin;

import dev.hatek.client.module.impl.combat.GodModeMenu;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Не даёт ваниле открыть инвентарь поверх фейк-закрытого меню варпов,
 * пока GodModeMenu держит десинк.
 */
@Mixin(Gui.class)
public class GodModeMenuMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void hatek$blockInventoryDesync(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof InventoryScreen)) {
            return;
        }
        GodModeMenu menu = GodModeMenu.instance();
        if (menu != null && menu.isFakeClosed()) {
            ci.cancel();
        }
    }
}
