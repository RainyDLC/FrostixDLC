package rtx.kimiko.api.modules.impl.Visuals.atmosphere;

/**
 * Immutable per-frame snapshot of the module settings. Renderers never touch settings directly.
 * {@code masterAlpha} is the module's fade in/out (Module#visualAlpha), applied to every effect.
 */
public record AtmosphereConfig(
        boolean lensFlare, float flareIntensity, float flareScale, int flareTint,
        boolean anamorphicStreak, boolean starburst,
        boolean dirtMask, float dirtIntensity, float dirtBaseVisibility,
        boolean chromaticAberration, ChromaticStrength chromaticStrength,
        boolean vignette, float vignetteIntensity, float vignetteRadius, float vignetteSoftness,
        boolean occlusionCheck, float fadeSpeed,
        float masterAlpha) {

    public boolean needsSun() {
        return lensFlare || dirtMask;
    }
}
