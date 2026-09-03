package fun.newrar.mixin;

import fun.newrar.Client;
import fun.newrar.screen.MainMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void rainydlc$onInit(CallbackInfo ci) {
        if (rainydlc$isUnhooked()) return;
        ci.cancel();
        MinecraftClient.getInstance().setScreen(new MainMenuScreen());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rainydlc$openMainMenu(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (rainydlc$isUnhooked()) return;
        ci.cancel();
        MinecraftClient.getInstance().setScreen(new MainMenuScreen());
    }

    @Unique
    private boolean rainydlc$isUnhooked() {
        try {
            if (Client.get() == null || Client.get().moduleManager() == null) return false;
            fun.newrar.module.api.Module unhook = Client.get().moduleManager().get("unhook");
            return unhook != null && unhook.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
