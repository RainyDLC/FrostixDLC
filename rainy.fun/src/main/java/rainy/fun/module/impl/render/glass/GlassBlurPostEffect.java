package rainy.fun.module.impl.render.glass;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import rainy.fun.gui.ClickGuiScreen;
import rainy.fun.module.impl.render.PostEffectAccess;

/** Applies the reusable dark-glass blur while the custom interface is open. */
public final class GlassBlurPostEffect {
    private static final Identifier EFFECT = Identifier.fromNamespaceAndPath("rainyfun", "ui/glass_blur");
    private static Identifier appliedEffect;

    private GlassBlurPostEffect() { }

    public static void update(Minecraft minecraft) {
        boolean shouldApply = minecraft.level != null
                && minecraft.gui.screen() instanceof ClickGuiScreen;
        if (!shouldApply) {
            clearOwnedEffect(minecraft);
            return;
        }

        if (EFFECT.equals(appliedEffect) && EFFECT.equals(minecraft.gameRenderer.currentPostEffect())) return;
        clearOwnedEffect(minecraft);
        PostEffectAccess.set(minecraft, EFFECT);
        appliedEffect = EFFECT;
    }

    private static void clearOwnedEffect(Minecraft minecraft) {
        if (appliedEffect == null) return;
        if (appliedEffect.equals(minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        appliedEffect = null;
    }
}
