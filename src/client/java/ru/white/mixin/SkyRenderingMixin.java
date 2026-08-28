package ru.white.mixin;

import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.ShaderSky;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void rainydlc$cancelGlowingSky(MatrixStack matrices, float sunAngle, int sunriseAndSunsetColor, CallbackInfo ci) {
        if (!rainydlc$domeActive()) return;
        ci.cancel();
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true, require = 0)
    private void rainydlc$cancelSkyDark(CallbackInfo ci) {
        if (!rainydlc$domeActive()) return;
        ci.cancel();
    }

    private boolean rainydlc$domeActive() {
        ShaderSky module = ShaderSky.getInstance();
        return module != null && module.isEnabled() && !module.mode.is("Blur");
    }
}
