package mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import rtx.kimiko.ui.menu.BlackHoleMenuScreen;

/** Подменяет ванильный TitleScreen на меню с черной дырой. */
@Mixin(MinecraftClient.class)
public abstract class BlackHoleTitleScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen kimiko$blackHoleMenu(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new BlackHoleMenuScreen();
        }
        return screen;
    }
}
