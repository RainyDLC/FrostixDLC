package rtx.kimiko.api.modules.impl.Visuals.atmosphere.effect;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.ChromaticStrength;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.ObjectAllocator;

/** RGB channel split that grows toward the frame edges. Runs on the world framebuffer, before the HUD. */
public final class ChromaticAberrationEffect {

    public void render(MinecraftClient mc, ChromaticStrength strength) {
        PostEffectProcessor processor = mc.getShaderLoader()
                .loadPostEffect(strength.postEffect(), DefaultFramebufferSet.MAIN_ONLY);
        if (processor != null) {
            processor.render(mc.getFramebuffer(), ObjectAllocator.TRIVIAL);
        }
    }
}
