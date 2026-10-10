package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.Interface;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Прячет ванильный хотбар, когда включён кастомный хотбар модуля Interface.
 * Здоровье, броня, голод и полоска опыта рисуются отдельно и не затрагиваются.
 */
@Mixin(Hud.class)
public class InterfaceHudMixin {
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void hatek$hideVanillaHotbar(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Interface.hideVanillaHotbar()) {
            ci.cancel();
        }
    }
}
