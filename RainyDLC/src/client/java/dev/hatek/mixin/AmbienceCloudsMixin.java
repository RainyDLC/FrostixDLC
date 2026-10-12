package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.Ambience;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience → Clouds: скрываем облака. */
@Mixin(CloudRenderer.class)
public class AmbienceCloudsMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hatek$ambienceClouds(CallbackInfo ci) {
        if (Ambience.hideClouds()) {
            ci.cancel();
        }
    }
}
