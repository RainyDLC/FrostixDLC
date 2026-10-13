package dev.hatek.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.hatek.client.render.ChamsShaderSources;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supply only our own ID at the shared source lookup. This covers BOTH ordinary draws and
 * the temporary CompilationCache used to precompile registered pipelines during resource reload.
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class ChamsShaderCacheMixin {
    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true)
    private void hatek$embeddedChamsShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (ChamsShaderSources.SHADER_ID.equals(id.toString())) {
            cir.setReturnValue(type == ShaderType.VERTEX
                    ? ChamsShaderSources.VERTEX : ChamsShaderSources.FRAGMENT);
        }
    }
}
