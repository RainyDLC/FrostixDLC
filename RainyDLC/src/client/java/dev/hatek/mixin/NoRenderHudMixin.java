package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class NoRenderHudMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderVignette(GuiGraphicsExtractor extractor, Entity entity, CallbackInfo ci) {
        if (NoRender.hideVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderNausea(GuiGraphicsExtractor extractor, float strength, CallbackInfo ci) {
        if (NoRender.hideNausea()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderPortal(GuiGraphicsExtractor extractor, float strength, CallbackInfo ci) {
        if (NoRender.hidePortal()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderSpyglass(GuiGraphicsExtractor extractor, float scale, CallbackInfo ci) {
        if (NoRender.hideSpyglass()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderSleep(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideSleep()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderBossBar(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideBossBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderScoreboard(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderTitles(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideTitles()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderEffectIcons(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideEffectIcons()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderCrosshair(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.hideCrosshair()) {
            ci.cancel();
        }
    }
}
