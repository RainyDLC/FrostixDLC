package rainy.fun.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;

/** Calls GameRenderer's private post-effect setter without changing its loaded class schema. */
public final class PostEffectAccess {
    private PostEffectAccess() { }

    public static void set(Minecraft minecraft, Identifier effect) {
        try {
            Method setter = minecraft.gameRenderer.getClass().getDeclaredMethod("setPostEffect", Identifier.class);
            setter.setAccessible(true);
            setter.invoke(minecraft.gameRenderer, effect);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to set post effect " + effect, failure);
        }
    }
}
