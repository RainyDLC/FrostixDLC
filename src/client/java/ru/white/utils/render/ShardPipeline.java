package ru.white.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;

public class ShardPipeline extends UniformArrayPipeline {
    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/shard");
    private static final Identifier SHADER = Identifier.of("client", "core/shard");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ShardData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final int MAX_SHARDS = 128;
    private static final int SHARD_SIZE = 4 * 16;
    private static final int UNIFORM_RING = 8;

    public ShardPipeline() {
        super("shard", PIPELINE, "ShardData", MAX_SHARDS, SHARD_SIZE, UNIFORM_RING);
    }

    @Override
    public int batchLayer() {
        return 2;
    }

    public void drawFaces(float[] verts, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;

        ByteBuffer buffer = beginItem();
        for (int i = 0; i < 12; i++) {
            buffer.putFloat(verts[i]);
        }
        buffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
        buffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
        buffer.putFloat((color & 0xFF) / 255.0f);
        buffer.putFloat(((color >> 24) & 0xFF) / 255.0f);
        endItem();
    }
}
