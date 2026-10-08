package rainy.fun.module.impl.render.ambience;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import rainy.fun.gui.ClickGuiScreen;
import rainy.fun.module.impl.render.PostEffectAccess;

/** Owns the post effect requested by the Ambience module. */
public final class AmbiencePostEffect {
    private static final String EFFECT_PREFIX = "rainyfun:ambience/chromatic_";
    private static Identifier appliedEffect;
    private static volatile boolean resourcesReady;

    private AmbiencePostEffect() { }

    public static void loadEmbeddedResources(Minecraft minecraft) {
        resourcesReady = false;
        minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
            if (failure != null) {
                System.err.println("[Rainy.fun] Ambience shader reload failed: " + failure);
                return;
            }
            resourcesReady = true;
            System.out.println("[Rainy.fun] Ambience shader resources are ready");
        });
    }

    public static void update(Minecraft minecraft) {
        if (minecraft.gui.screen() instanceof ClickGuiScreen) {
            clearOwnedEffect(minecraft);
            return;
        }
        AmbienceModule module = AmbienceModule.getInstance();
        if (module == null || !module.hasChromaticAberration() || minecraft.level == null || !resourcesReady) {
            clearOwnedEffect(minecraft);
            return;
        }

        String level = switch (module.getChromaticStrength()) {
            case AmbienceModule.CHROMATIC_SUBTLE -> "subtle";
            case AmbienceModule.CHROMATIC_STRONG -> "strong";
            default -> "medium";
        };
        Identifier desired = Identifier.parse(EFFECT_PREFIX + level);
        if (desired.equals(appliedEffect) && desired.equals(minecraft.gameRenderer.currentPostEffect())) return;

        clearOwnedEffect(minecraft);
        PostEffectAccess.set(minecraft, desired);
        appliedEffect = desired;
    }

    private static void clearOwnedEffect(Minecraft minecraft) {
        if (appliedEffect == null) return;
        if (appliedEffect.equals(minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        appliedEffect = null;
    }
}
