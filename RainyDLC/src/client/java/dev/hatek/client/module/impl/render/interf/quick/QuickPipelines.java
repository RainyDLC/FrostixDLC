package dev.hatek.client.module.impl.render.interf.quick;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Same GUI bindings/vertex format as 26.2, with HUD-specific fragment stages. */
public final class QuickPipelines {
    public static final RenderPipeline MSDF = create("quick_msdf");
    public static final RenderPipeline BLUR = create("quick_blur");
    private QuickPipelines() {}
    private static RenderPipeline create(String name) {
        RenderPipeline base = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("hatek_client", name))
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(Identifier.fromNamespaceAndPath("hatek_client", "core/" + name))
                .withPrimitiveTopology(base.getPrimitiveTopology())
                .withColorTargetState(base.getColorTargetState())
                .withDepthStencilState(java.util.Optional.ofNullable(base.getDepthStencilState()))
                .withCull(base.isCull());
        for (var layout : base.getBindGroupLayouts()) builder.withBindGroupLayout(layout);
        var formats = base.getVertexFormatBindings();
        for (int i = 0; i < formats.length; i++) builder.withVertexBinding(i, formats[i]);
        return RenderPipelines.register(builder.build());
    }
}
