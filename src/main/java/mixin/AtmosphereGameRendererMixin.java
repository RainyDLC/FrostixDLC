package mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtx.kimiko.api.modules.impl.Visuals.Atmosphere;

@Mixin(GameRenderer.class)
public abstract class AtmosphereGameRendererMixin {

    /** Chromatic aberration runs on the world image only, so HUD and GUI stay sharp. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
            shift = At.Shift.AFTER), require = 0)
    private void kimiko$atmosphereAfterWorld(CallbackInfo ci) {
        Atmosphere module = Atmosphere.getInstance();
        if (module != null) module.onWorldRendered();
    }
}
