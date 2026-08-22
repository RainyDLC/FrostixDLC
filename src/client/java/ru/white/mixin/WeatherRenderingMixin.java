package ru.white.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.white.module.impl.render.WorldTweaks;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {

    /** Убираем ванильные осадки — их заменяет шейдерный дождь из World Tweaks. */
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void storm$cancelVanillaRain(VertexConsumerProvider consumers, Vec3d pos,
                                         WeatherRenderState state, CallbackInfo ci) {
        WorldTweaks wt = WorldTweaks.get();
        if (wt != null && wt.isEnabled() && wt.rains.getValue()) {
            ci.cancel();
        }
    }
}
