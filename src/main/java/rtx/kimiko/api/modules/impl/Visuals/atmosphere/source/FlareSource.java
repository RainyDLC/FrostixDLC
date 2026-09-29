package rtx.kimiko.api.modules.impl.Visuals.atmosphere.source;

/**
 * Universal optical light source that projects into the camera lens.
 *
 * @param screenX        screen X in scaled GUI pixels
 * @param screenY        screen Y in scaled GUI pixels
 * @param visibility     0..1 smoothed factor (occlusion, horizon, weather, edge fade)
 * @param alignment      0..1 alignment to center of screen (1 = dead center)
 * @param glowColor      0xRRGGBB base color for central glow & burst
 * @param streakColor    0xRRGGBB base color for anamorphic streak
 * @param scale          relative scale multiplier (1.0 for Sun, 0.75 for Moon, etc.)
 * @param ghostIntensity 0..1 multiplier for aperture reflection ghosts
 * @param hasStarburst   whether diffraction starburst spikes are emitted
 * @param hasStreak      whether anamorphic horizontal streak is emitted
 * @param dirtBoost      how strongly this source illuminates lens dirt
 */
public record FlareSource(
        float screenX,
        float screenY,
        float visibility,
        float alignment,
        int glowColor,
        int streakColor,
        float scale,
        float ghostIntensity,
        boolean hasStarburst,
        boolean hasStreak,
        float dirtBoost
) {
    public boolean isVisible() {
        return visibility > 0.001f;
    }
}
