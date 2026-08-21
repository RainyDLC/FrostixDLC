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

/**
 * Треугольные грани с произвольными вершинами — то, чего не умеют rect/texture:
 * там квад с поворотом вокруг центра, а осколкам нужны свои три точки, иначе они
 * не стыкуются друг с другом.
 *
 * Один элемент uniform-массива — две грани (6 вершин), как и рассчитывает
 * {@link UniformArrayPipeline}. Вершины приходят уже в пикселях фиксированного
 * 2x-GUI: всю трансформацию (поворот, сжатие, перспектива) делает CPU.
 */
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

    // Должно совпадать с shard.vsh: vec4 screen + vec4 shards[128 * 4]
    private static final int MAX_SHARDS = 128;
    private static final int SHARD_SIZE = 4 * 16;
    private static final int UNIFORM_RING = 8;

    public ShardPipeline() {
        super("shard", PIPELINE, "ShardData", MAX_SHARDS, SHARD_SIZE, UNIFORM_RING);
    }

    @Override
    public int batchLayer() {
        return 2; // как текстуры: над заливками и обводками, под текстом
    }

    /**
     * Рисует две грани одним элементом.
     *
     * @param verts 12 float — по (x, y) на вершину; вершины 0..2 первая грань,
     *              3..5 вторая. Вторую грань можно вырождить, повторив одну
     *              точку трижды — нулевая площадь ничего не даёт.
     */
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
