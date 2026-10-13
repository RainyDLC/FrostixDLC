package dev.hatek.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.IdentifierException;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Uses the actual Minecraft classes: catches the namespaced String-overload crash. */
public final class ChamsPipelineRegression {
    public static void main(String[] args) {
        boolean badVertexRejected = false, badFragmentRejected = false;
        try { RenderPipeline.builder().withVertexShader(ChamsShaderSources.SHADER_ID); }
        catch (IdentifierException expected) { badVertexRejected = true; }
        try { RenderPipeline.builder().withFragmentShader(ChamsShaderSources.SHADER_ID); }
        catch (IdentifierException expected) { badFragmentRejected = true; }
        check(badVertexRejected && badFragmentRejected, "namespaced String overload must not be used");

        Identifier skin = Identifier.fromNamespaceAndPath("hatek", "textures/test_skin.png");
        Identifier expected = Identifier.parse(ChamsShaderSources.SHADER_ID);
        RenderType through = ChamsRenderType.forSkin(skin, true);
        RenderType visible = ChamsRenderType.forSkin(skin, false);
        check(expected.equals(through.pipeline().getVertexShader()), "through-wall vertex ID");
        check(expected.equals(through.pipeline().getFragmentShader()), "through-wall fragment ID");
        check(expected.equals(visible.pipeline().getVertexShader()), "visible vertex ID");
        check(expected.equals(visible.pipeline().getFragmentShader()), "visible fragment ID");
        check(through == ChamsRenderType.forSkin(skin, true), "through-wall type cache");
        check(visible == ChamsRenderType.forSkin(skin, false), "visible type cache");
        check(through != visible, "independent depth modes");
        check(!through.pipeline().getDepthStencilState().writeDepth(), "through-wall depth writes disabled");
        check(!visible.pipeline().getDepthStencilState().writeDepth(), "visible depth writes disabled");
        check(visible.pipeline().getDepthStencilState().depthTest()
                == RenderPipelines.ENTITY_TRANSLUCENT.getDepthStencilState().depthTest(), "vanilla depth convention");
        System.out.println("Chams pipeline: 11 checks passed on real Minecraft classes");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
