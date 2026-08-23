package ru.white.utils.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Осколки с содержимым настоящего меню: каждый элемент - две треугольные грани
 * с UV в захваченную панель. Формат элемента: 6 вершин по (x, y, u, v) + цвет,
 * итого 7 vec4 (112 байт) - совпадает с core/shard_tex.
 */
public final class ShardTexturePipeline implements DrawBatcher.Batched {

    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/shard_tex");
    private static final Identifier SHADER = Identifier.of("client", "core/shard_tex");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ShardTexData", UniformType.UNIFORM_BUFFER)
                    .withSampler("PanelTex")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final int MAX_SHARDS = 128;
    private static final int ITEM_FLOATS = 28;          // 6 вершин * 4 + rgba
    private static final int ITEM_SIZE = 7 * 16;        // 112 байт, std140
    private static final int HEADER_SIZE = 16;
    private static final int UNIFORM_RING = 8;

    private GpuBuffer[] uniformBuffers;
    private int uniformRingIndex;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    private record Chunk(GpuBuffer buffer, int count) {}

    private int batchedItems;
    private final ArrayList<Chunk> chunks = new ArrayList<>();

    /** Захваченная панель; ставится перед отрисовкой осколков. */
    private GpuTextureView panelView;

    @Override
    public int batchLayer() {
        return 2;
    }

    public void setPanel(GpuTextureView view) {
        this.panelView = view;
    }

    /** Одна пара граней: 24 float (x,y,u,v на вершину) + argb. */
    public void drawFacePair(float[] xyuv, int color) {
        ByteBuffer buffer = beginItem();
        for (int i = 0; i < 24; i++) {
            buffer.putFloat(xyuv[i]);
        }
        buffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
        buffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
        buffer.putFloat((color & 0xFF) / 255.0f);
        buffer.putFloat(((color >> 24) & 0xFF) / 255.0f);
        endItem();
    }

    private ByteBuffer beginItem() {
        ensureInitialized();
        if (batchedItems == 0) {
            dataBuffer.clear();
            dataBuffer.position(HEADER_SIZE);
        }
        return dataBuffer;
    }

    private void endItem() {
        batchedItems++;
        if (DrawBatcher.isEnabled()) {
            DrawBatcher.register(this);
            if (batchedItems >= MAX_SHARDS) {
                if (chunks.size() >= UNIFORM_RING - 1) {
                    DrawBatcher.drawImmediate(this, false);
                } else {
                    sealChunk();
                }
            }
        } else {
            DrawBatcher.drawImmediate(this);
        }
    }

    private void sealChunk() {
        if (batchedItems == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        float fixedScreenWidth = client.getWindow().getFramebufferWidth() / UniformArrayScale.FIXED_GUI_SCALE;
        float fixedScreenHeight = client.getWindow().getFramebufferHeight() / UniformArrayScale.FIXED_GUI_SCALE;

        int endPosition = dataBuffer.position();
        dataBuffer.position(0);
        dataBuffer.putFloat(fixedScreenWidth);
        dataBuffer.putFloat(fixedScreenHeight);
        dataBuffer.putFloat(UniformArrayScale.FIXED_GUI_SCALE);
        dataBuffer.putFloat(0f);
        dataBuffer.position(0);
        dataBuffer.limit(endPosition);

        GpuBuffer uniformBuffer = nextUniformBuffer();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
        dataBuffer.limit(dataBuffer.capacity());

        chunks.add(new Chunk(uniformBuffer, batchedItems));
        batchedItems = 0;
    }

    private GpuBuffer nextUniformBuffer() {
        GpuBuffer buf = uniformBuffers[uniformRingIndex];
        if (buf == null) {
            final int idx = uniformRingIndex;
            buf = RenderSystem.getDevice().createBuffer(
                    () -> "client:shard_tex_uniform_" + idx,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    HEADER_SIZE + MAX_SHARDS * ITEM_SIZE
            );
            uniformBuffers[uniformRingIndex] = buf;
        }
        uniformRingIndex = (uniformRingIndex + 1) % UNIFORM_RING;
        return buf;
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(HEADER_SIZE + MAX_SHARDS * ITEM_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "client:shard_tex_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.uniformBuffers = new GpuBuffer[UNIFORM_RING];
        this.initialized = true;
    }

    @Override
    public void uploadBatch(CommandEncoder encoder) {
        sealChunk();
    }

    @Override
    public void drawBatch(RenderPass pass) {
        if (chunks.isEmpty() || panelView == null) return;

        pass.setPipeline(PIPELINE);
        pass.setVertexBuffer(0, dummyVertexBuffer);
        pass.bindTexture("PanelTex", panelView,
                RenderSystem.getSamplerCache().get(FilterMode.LINEAR));

        for (Chunk chunk : chunks) {
            pass.setUniform("ShardTexData", chunk.buffer());
            pass.draw(0, chunk.count() * 6);
        }

        chunks.clear();
    }

    @Override
    public void discardBatch() {
        chunks.clear();
    }

    public void close() {
        batchedItems = 0;
        discardBatch();
        if (uniformBuffers != null) {
            for (GpuBuffer buf : uniformBuffers) {
                if (buf != null) buf.close();
            }
            uniformBuffers = null;
        }
        uniformRingIndex = 0;
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }

    /** Константы вынесены, чтобы не тянуть наследование от UniformArrayPipeline. */
    static final class UniformArrayScale {
        static final float FIXED_GUI_SCALE = 2.0f;
    }
}
