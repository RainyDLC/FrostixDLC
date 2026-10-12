package dev.hatek.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.hatek.client.ambience.AmbienceSky;
import dev.hatek.client.module.impl.render.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience: подменяем ванильное небо своим процедурным. */
@Mixin(SkyRenderer.class)
public abstract class AmbienceSkyMixin {
    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hatek$ambienceState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
                                     CallbackInfo ci) {
        AmbienceSky.capture(state.sunAngle, state.moonAngle, state.starAngle, state.rainBrightness);
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceSky(int skyColor, CallbackInfo ci) {
        if (Ambience.active()) {
            AmbienceSky.render(this.renderTarget);
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceSunrise(CallbackInfo ci) {
        if (Ambience.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceDarkDisc(CallbackInfo ci) {
        if (Ambience.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceStars(CallbackInfo ci) {
        if (Ambience.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceSun(CallbackInfo ci) {
        if (Ambience.hideSun()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceMoon(CallbackInfo ci) {
        if (Ambience.hideSunMoon()) {
            ci.cancel();
        }
    }
}
