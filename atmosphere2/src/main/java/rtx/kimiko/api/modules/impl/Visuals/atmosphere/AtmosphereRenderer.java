package rtx.kimiko.api.modules.impl.Visuals.atmosphere;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.ChromaticAberrationEffect;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.DirtMaskRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.LensFlareRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.VignetteRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Orchestrates all lens effects in physically sensible order: flare, dirt, vignette. */
public final class AtmosphereRenderer {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SunTracker sunTracker = new SunTracker();
    private final LensFlareRenderer flare = new LensFlareRenderer();
    private final DirtMaskRenderer dirt = new DirtMaskRenderer();
    private final VignetteRenderer vignette = new VignetteRenderer();
    private final ChromaticAberrationEffect chromatic = new ChromaticAberrationEffect();

    public void renderPostWorld(AtmosphereConfig cfg) {
        if (mc.world == null || !cfg.chromaticAberration() || cfg.masterAlpha() < 0.5f) return;
        chromatic.render(mc, cfg.chromaticStrength());
    }

    public void renderLensOverlay(DrawContext ctx, float tickDelta, AtmosphereConfig cfg) {
        if (mc.world == null || mc.player == null) return;

        int width = ctx.getScaledWindowWidth();
        int height = ctx.getScaledWindowHeight();

        SunState sun = cfg.needsSun()
                ? sunTracker.update(mc, tickDelta, width, height, cfg)
                : SunState.HIDDEN;

        if (cfg.lensFlare()) flare.render(ctx, sun, width, height, cfg);
        if (cfg.dirtMask())  dirt.render(ctx, sun, width, height, cfg);
        if (cfg.vignette())  vignette.render(ctx, width, height, cfg);
    }
}
