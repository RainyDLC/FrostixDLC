package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class NoRenderHudMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoRender.hideVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderNausea(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.hideNausea()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderPortal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.hidePortal()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderSpyglass(DrawContext context, float scale, CallbackInfo ci) {
        if (NoRender.hideSpyglass()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderSleep(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideSleep()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideBossBar()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hatek$noRenderScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderTitles(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideTitles()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderEffectIcons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideEffectIcons()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void hatek$noRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideCrosshair()) {
            ci.cancel();
        }
    }
}
