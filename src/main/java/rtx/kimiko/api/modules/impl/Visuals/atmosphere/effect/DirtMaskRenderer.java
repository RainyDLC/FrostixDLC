package rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.LensTextures;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.ColorUtil;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.Draw;
import net.minecraft.client.gui.DrawContext;

/** Dust and scratches on the virtual lens, lit up only when bright light enters it. */
public final class DirtMaskRenderer {

    private static final int DIRT_RGB = 0xFFEBC8; // warm, sunlit

    public void render(DrawContext ctx, SunState sun, int width, int height, AtmosphereConfig cfg) {
        float lit = sun.visibility() * (0.35f + 0.65f * sun.alignment());
        float alpha = (cfg.dirtBaseVisibility() + cfg.dirtIntensity() * lit) * cfg.masterAlpha();
        if (alpha <= 0.002f) return;

        int color = ColorUtil.argb(alpha, ColorUtil.multiply(DIRT_RGB, cfg.flareTint()));
        Draw.texture(ctx, LensTextures.DIRT.id(), 0, 0, width, height, color);
    }
}
