package rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.ProceduralTexture;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.ColorUtil;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.Draw;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.MathUtil;
import net.minecraft.client.gui.DrawContext;

/** Optical vignetting: smooth darkening toward the frame corners. */
public final class VignetteRenderer {

    private float radius = Float.NaN;
    private float softness = Float.NaN;
    private ProceduralTexture texture;

    public void render(DrawContext ctx, int width, int height, AtmosphereConfig cfg) {
        float alpha = cfg.vignetteIntensity() * cfg.masterAlpha();
        if (alpha <= 0f) return;
        Draw.texture(ctx, textureFor(cfg.vignetteRadius(), cfg.vignetteSoftness()).id(),
                0, 0, width, height, ColorUtil.argb(alpha, 0x000000));
    }

    /** Re-bakes the falloff only when the shape settings actually change. */
    private ProceduralTexture textureFor(float newRadius, float newSoftness) {
        if (texture == null || newRadius != radius || newSoftness != softness) {
            radius = newRadius;
            softness = newSoftness;
            final float r = newRadius, s = newSoftness;
            texture = new ProceduralTexture("vignette", 256, 256,
                    (u, v) -> MathUtil.smoothstep(r, r + s, (float) Math.sqrt(u * u + v * v)));
        }
        return texture;
    }
}
