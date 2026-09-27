package rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.LensTextures;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture.ProceduralTexture;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.ColorUtil;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.Draw;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Classic lens flare: glow + starburst at the sun, ghosts along the axis through the frame center,
 * optional anamorphic streak.
 */
public final class LensFlareRenderer {

    /**
     * @param position 0 = at the sun, 1 = screen center, 2 = mirrored across the center
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

    public void render(DrawContext ctx, SunState sun, int width, int height, AtmosphereConfig cfg) {
        if (!sun.isVisible()) return;

        float strength = sun.visibility() * cfg.flareIntensity() * cfg.masterAlpha();
        float scale = height * cfg.flareScale();
        float centerX = width * 0.5f, centerY = height * 0.5f;
        float axisX = centerX - sun.screenX(), axisY = centerY - sun.screenY();
        int tint = cfg.flareTint();

        float glowSize = scale * (0.45f + 0.25f * sun.alignment());
        Draw.textureCentered(ctx, LensTextures.GLOW.id(), sun.screenX(), sun.screenY(), glowSize, glowSize,
                ColorUtil.argb(0.85f * strength, ColorUtil.multiply(SUN_GLOW_RGB, tint)));

        if (cfg.starburst()) {
            float burst = scale * 0.8f;
            Draw.textureCentered(ctx, LensTextures.STARBURST.id(), sun.screenX(), sun.screenY(), burst, burst,
                    ColorUtil.argb(0.45f * strength, ColorUtil.multiply(SUN_GLOW_RGB, tint)));
        }

        if (cfg.anamorphicStreak()) {
            Draw.textureCentered(ctx, LensTextures.STREAK.id(), sun.screenX(), sun.screenY(),
                    width * 1.4f * cfg.flareScale(), scale * 0.05f,
                    ColorUtil.argb(0.5f * strength, ColorUtil.multiply(STREAK_RGB, tint)));
        }

        // Ghosts are internal reflections: strongest when the sun is near the center of the frame.
        float ghostStrength = strength * (0.35f + 0.65f * sun.alignment());
        for (Ghost ghost : GHOSTS) {
            float x = sun.screenX() + axisX * ghost.position();
            float y = sun.screenY() + axisY * ghost.position();
            float size = scale * ghost.size();
            Draw.textureCentered(ctx, ghost.texture().id(), x, y, size, size,
                    ColorUtil.argb(ghost.alpha() * ghostStrength, ColorUtil.multiply(ghost.rgb(), tint)));
        }
    }
}
