package rainy.fun.mixin;

import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rainy.fun.module.impl.render.ambience.AmbienceModule;

@Mixin(Lightmap.class)
abstract class LightmapAmbienceMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void rainyfun$applyAmbienceBrightness(LightmapRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience == null || !ambience.isEnabled()) return;
        state.brightness = ambience.isFullbright()
                ? 1.0f
                : Math.max(state.brightness, ambience.getBrightness());
    }
}
