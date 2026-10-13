package dev.hatek.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached skin render types with independent depth behavior for chams. */
public final class ChamsRenderType {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline THROUGH_PIPELINE = createPipeline(true);
    private static final com.mojang.blaze3d.pipeline.RenderPipeline VISIBLE_PIPELINE = createPipeline(false);
    private static final Map<Identifier, RenderType> THROUGH_TEXTURE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> VISIBLE_TEXTURE = new ConcurrentHashMap<>();

    private ChamsRenderType() {
    }

    private static com.mojang.blaze3d.pipeline.RenderPipeline createPipeline(boolean throughWalls) {
        return com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("hatek",
                        throughWalls ? "chams_through_walls" : "chams_visible"))
                .withColorTargetState(RenderPipelines.ENTITY_TRANSLUCENT.getColorTargetState())
                .withDepthStencilState(new DepthStencilState(
                        throughWalls ? CompareOp.ALWAYS_PASS : CompareOp.LESS_THAN_OR_EQUAL, false))
                .build();
    }

    public static RenderType forSkin(Identifier texture, boolean throughWalls) {
        Map<Identifier, RenderType> cache = throughWalls ? THROUGH_TEXTURE : VISIBLE_TEXTURE;
        return cache.computeIfAbsent(texture, id -> RenderType.create(
                throughWalls ? "hatek_chams_through_walls" : "hatek_chams_visible",
                RenderSetup.builder(throughWalls ? THROUGH_PIPELINE : VISIBLE_PIPELINE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }
}
