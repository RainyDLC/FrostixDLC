package rtx.kimiko.api.modules.impl.Visuals.atmosphere;

import net.minecraft.util.Identifier;

/**
 * Post effects in 1.21.5+ have uniforms baked in JSON, so each strength is its own
 * tiny post-effect file (assets/kimiko/post_effect/atmosphere).
 */
public enum ChromaticStrength {
    SUBTLE("chromatic_subtle"),
    MEDIUM("chromatic_medium"),
    STRONG("chromatic_strong");

    private final Identifier postEffect;

    ChromaticStrength(String file) {
        this.postEffect = Identifier.of("kimiko", "atmosphere/" + file);
    }

    public Identifier postEffect() {
        return postEffect;
    }
}
