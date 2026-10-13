package dev.hatek.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import dev.hatek.client.module.impl.render.Chams;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Two cached pipelines; all procedural material settings are dynamic, not shader variants. */
public final class ChamsRenderType {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline THROUGH_PIPELINE = createPipeline(true);
    private static final com.mojang.blaze3d.pipeline.RenderPipeline VISIBLE_PIPELINE = createPipeline(false);
    private static final TextureTransform PARAMETERS = new TextureTransform("hatek_chams_parameters", Chams::shaderParameters);
    private static final Map<Identifier, RenderType> THROUGH_TEXTURE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> VISIBLE_TEXTURE = new ConcurrentHashMap<>();

    private ChamsRenderType() {
    }

    private static com.mojang.blaze3d.pipeline.RenderPipeline createPipeline(boolean throughWalls) {
        return RenderPipelines.register(com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("hatek",
                        throughWalls ? "chams_through_walls" : "chams_visible"))
                .withVertexShader(ChamsShaderSources.SHADER_ID)
                .withFragmentShader(ChamsShaderSources.SHADER_ID)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withCull(true)
                .withColorTargetState(RenderPipelines.ENTITY_TRANSLUCENT.getColorTargetState())
                .withDepthStencilState(new DepthStencilState(
                        throughWalls ? CompareOp.ALWAYS_PASS
                                : RenderPipelines.ENTITY_TRANSLUCENT.getDepthStencilState().depthTest(), false))
                .build());
    }

    public static RenderType forSkin(Identifier texture, boolean throughWalls) {
        Map<Identifier, RenderType> cache = throughWalls ? THROUGH_TEXTURE : VISIBLE_TEXTURE;
        return cache.computeIfAbsent(texture, id -> RenderType.create(
                throughWalls ? "hatek_chams_through_walls" : "hatek_chams_visible",
                RenderSetup.builder(throughWalls ? THROUGH_PIPELINE : VISIBLE_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .setTextureTransform(PARAMETERS)
                        .sortOnUpload()
                        .createRenderSetup()));
    }
}
