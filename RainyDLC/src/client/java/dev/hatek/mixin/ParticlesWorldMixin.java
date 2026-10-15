package dev.hatek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.impl.render.HitParticles;
import dev.hatek.client.module.impl.render.WorldParticles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рендер частиц в мире. Тот же хук, что у KillEffectWorldMixin
 * (TAIL LevelRenderer.submitEntities) — см. origin/main.
 * Требует регистрации в hatek_client.client.mixins.json (см. register.diff).
 */
@Mixin(LevelRenderer.class)
public abstract class ParticlesWorldMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void hatek$particlesWorld(PoseStack stack, LevelRenderState state,
                                      SubmitNodeCollector collector, CallbackInfo ci) {
        HitParticles.renderWorld(stack, state, collector);
        WorldParticles.renderWorld(stack, state, collector);
    }
}
