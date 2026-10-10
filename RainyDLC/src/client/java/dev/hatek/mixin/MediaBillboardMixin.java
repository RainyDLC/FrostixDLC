package dev.hatek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.media.MediaBillboard;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Точка входа 3D-рендера медиа: после сущностей рисуем билборд
 * с картинкой/GIF над игроком.
 */
@Mixin(LevelRenderer.class)
public class MediaBillboardMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void hatek$mediaBillboard(PoseStack poseStack, LevelRenderState state,
                                      SubmitNodeCollector collector, CallbackInfo ci) {
        MediaBillboard.render(poseStack, state, collector);
    }
}
