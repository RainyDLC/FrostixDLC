package rtx.kimiko.api.modules.impl.Visuals.atmosphere;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.ChromaticAberrationEffect;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.DirtMaskRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.LensFlareRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect.VignetteRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.source.AtmosphereTracker;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.source.FlareSource;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Orchestrates all lens effects in physically sensible order: flare, dirt, vignette. */
public final class AtmosphereRenderer {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final AtmosphereTracker tracker = new AtmosphereTracker();
    private final LensFlareRenderer flare = new LensFlareRenderer();
    private final DirtMaskRenderer dirt = new DirtMaskRenderer();
    private final VignetteRenderer vignette = new VignetteRenderer();
    private final ChromaticAberrationEffect chromatic = new ChromaticAberrationEffect();

    public void onExplosion(Vec3d pos) {
        tracker.onExplosion(pos);
    }

    public void renderPostWorld(AtmosphereConfig cfg) {
        if (mc.world == null || !cfg.chromaticAberration() || cfg.masterAlpha() < 0.5f) return;
        chromatic.render(mc, cfg.chromaticStrength());
    }

    public void renderLensOverlay(DrawContext ctx, float tickDelta, AtmosphereConfig cfg) {
        if (mc.world == null || mc.player == null) return;

        int width = ctx.getScaledWindowWidth();
        int height = ctx.getScaledWindowHeight();

        AtmosphereTracker.TrackingResult result = cfg.needsFlares()
                ? tracker.update(mc, tickDelta, width, height, cfg)
                : new AtmosphereTracker.TrackingResult(List.of(), 0f, SunState.HIDDEN);

        if (cfg.lensFlare()) {
            for (FlareSource source : result.sources()) {
                flare.render(ctx, source, width, height, cfg);
            }
        }
        if (cfg.dirtMask()) {
            dirt.render(ctx, result.dirtIllumination(), width, height, cfg);
        }
        if (cfg.vignette()) {
            vignette.render(ctx, width, height, cfg);
        }
    }
}
