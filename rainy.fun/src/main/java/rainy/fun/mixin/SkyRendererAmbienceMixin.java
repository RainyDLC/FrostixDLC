package rainy.fun.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rainy.fun.module.impl.render.ambience.AmbienceModule;

@Mixin(SkyRenderer.class)
abstract class SkyRendererAmbienceMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void rainyfun$adjustSky(ClientLevel level, float tickDelta, Camera camera,
                                    SkyRenderState state, CallbackInfo ci) {
        AmbienceModule ambience = AmbienceModule.getInstance();
        if (ambience == null || !ambience.isEnabled()) return;
        float saturation = ambience.getSaturation();
        state.skyColor = applySaturation(state.skyColor, saturation);
        state.sunriseAndSunsetColor = applySaturation(state.sunriseAndSunsetColor, saturation);
    }

    private static int applySaturation(int color, float saturation) {
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        float luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f;
        red = clamp(luminance + (red - luminance) * saturation);
        green = clamp(luminance + (green - luminance) * saturation);
        blue = clamp(luminance + (blue - luminance) * saturation);
        return (color & 0xFF000000) | (int) (red * 255) << 16
                | (int) (green * 255) << 8 | (int) (blue * 255);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
