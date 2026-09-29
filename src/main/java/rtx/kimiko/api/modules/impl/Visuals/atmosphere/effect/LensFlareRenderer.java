package rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.source.FlareSource;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.LensTextures;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.ProceduralTexture;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.ColorUtil;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.Draw;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Optical lens flare: glow + starburst at the source, aperture ghosts along the optical axis,
 * and anamorphic horizontal streaks.
 */
public final class LensFlareRenderer {

    /**
     * @param position 0 = at the light source, 1 = screen center, 2 = mirrored across the center
     * @param size     fraction of screen height
     */
    private record Ghost(ProceduralTexture texture, float position, float size, int rgb, float alpha) {}

    private static final List<Ghost> GHOSTS = List.of(
            new Ghost(LensTextures.HEX_GHOST, 0.35f, 0.07f, 0x9FD8FF, 0.22f),
            new Ghost(LensTextures.GLOW,      0.60f, 0.06f, 0xFFB070, 0.30f),
            new Ghost(LensTextures.HEX_GHOST, 0.90f, 0.11f, 0x8CFFB0, 0.16f),
            new Ghost(LensTextures.RING,      1.25f, 0.24f, 0xA0C4FF, 0.14f),
            new Ghost(LensTextures.HEX_GHOST, 1.50f, 0.17f, 0xFF9FD0, 0.14f),
            new Ghost(LensTextures.GLOW,      1.80f, 0.08f, 0xFFD27F, 0.24f),
            new Ghost(LensTextures.RING,      2.10f, 0.45f, 0xFFFFFF, 0.08f));

    private static final int SUN_GLOW_RGB = 0xFFF2D6;
    private static final int STREAK_RGB = 0x9FC8FF;

    public void render(DrawContext ctx, FlareSource src, int width, int height, AtmosphereConfig cfg) {
        if (!src.isVisible()) return;

        float strength = src.visibility() * cfg.flareIntensity() * cfg.masterAlpha();
        float scale = height * cfg.flareScale() * src.scale();
        float centerX = width * 0.5f, centerY = height * 0.5f;
        float axisX = centerX - src.screenX(), axisY = centerY - src.screenY();
        int tint = cfg.flareTint();

        int glowRgb = ColorUtil.multiply(src.glowColor(), tint);
        int streakRgb = ColorUtil.multiply(src.streakColor(), tint);

        float glowSize = scale * (0.45f + 0.25f * src.alignment());
        Draw.textureCentered(ctx, LensTextures.GLOW.id(), src.screenX(), src.screenY(), glowSize, glowSize,
                ColorUtil.argb(0.85f * strength, glowRgb));

        if (cfg.starburst() && src.hasStarburst()) {
            float burst = scale * 0.8f;
            Draw.textureCentered(ctx, LensTextures.STARBURST.id(), src.screenX(), src.screenY(), burst, burst,
                    ColorUtil.argb(0.45f * strength, glowRgb));
        }

        if (cfg.anamorphicStreak() && src.hasStreak()) {
            Draw.textureCentered(ctx, LensTextures.STREAK.id(), src.screenX(), src.screenY(),
                    width * 1.4f * cfg.flareScale() * src.scale(), scale * 0.05f,
                    ColorUtil.argb(0.5f * strength, streakRgb));
        }

        if (src.ghostIntensity() > 0.001f) {
            float ghostStrength = strength * src.ghostIntensity() * (0.35f + 0.65f * src.alignment());
            for (Ghost ghost : GHOSTS) {
                float x = src.screenX() + axisX * ghost.position();
                float y = src.screenY() + axisY * ghost.position();
                float size = scale * ghost.size();
                int ghostColor = ColorUtil.multiply(ghost.rgb(), glowRgb);
                Draw.textureCentered(ctx, ghost.texture().id(), x, y, size, size,
                        ColorUtil.argb(ghost.alpha() * ghostStrength, ghostColor));
            }
        }
    }

    public void render(DrawContext ctx, SunState sun, int width, int height, AtmosphereConfig cfg) {
        if (!sun.isVisible()) return;
        render(ctx, new FlareSource(
                sun.screenX(), sun.screenY(), sun.visibility(), sun.alignment(),
                SUN_GLOW_RGB, STREAK_RGB, 1.0f, 1.0f, true, true, 1.0f), width, height, cfg);
    }
}
