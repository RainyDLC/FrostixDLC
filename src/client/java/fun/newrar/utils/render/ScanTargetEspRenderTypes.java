package fun.newrar.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public final class ScanTargetEspRenderTypes {
    public static final RenderPipeline TARGET_ESP_SCAN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/target_esp_scan"))
                    .withVertexShader(Identifier.of("client", "core/target_esp_scan"))
                    .withFragmentShader(Identifier.of("client", "core/target_esp_scan"))
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final Function<Identifier, RenderLayer> TARGET_ESP_SCAN = Util.memoize(texture -> {
        RenderSetup setup = RenderSetup.builder(TARGET_ESP_SCAN_PIPELINE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1536)
                .build();
        return RenderLayer.of("target_esp_scan", setup);
    });

    public static RenderLayer targetEspScan(Identifier texture) {
        return TARGET_ESP_SCAN.apply(texture);
    }

    private ScanTargetEspRenderTypes() {
    }

    public static void initialize() {
    }
}
