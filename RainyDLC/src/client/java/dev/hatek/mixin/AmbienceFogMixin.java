package dev.hatek.mixin;

import dev.hatek.client.ambience.AmbienceSky;
import dev.hatek.client.module.impl.render.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience: тёмный «космический» цвет тумана вместо голубого неба. */
@Mixin(FogRenderer.class)
public class AmbienceFogMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void hatek$ambienceFog(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                   float darkenWorld, Vector4f color, CallbackInfo ci) {
        FogType type = camera.getFluidInCamera();
        boolean air = type == FogType.NONE || type == FogType.ATMOSPHERIC;
        if (air && Ambience.darkFog()) {
            float[] c = AmbienceSky.fogColor(camera.forwardVector().x(), camera.forwardVector().z());
            color.set(c[0], c[1], c[2], color.w);
        }
        AmbienceSky.lastFog(color.x, color.y, color.z);
    }
}
