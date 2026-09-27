package mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtx.kimiko.api.modules.impl.Visuals.Atmosphere;

@Mixin(InGameHud.class)
public abstract class AtmosphereHudMixin {

    /** HEAD: lens effects are drawn before hotbar/chat/HudRenderEvent, so the HUD stays on top. */
    @Inject(method = "render", at = @At("HEAD"))
    private void kimiko$atmosphereLens(DrawContext graphics, RenderTickCounter tickCounter, CallbackInfo ci) {
        Atmosphere module = Atmosphere.getInstance();
        if (module != null) module.onLensOverlay(graphics, tickCounter.getTickProgress(true));
    }
}
