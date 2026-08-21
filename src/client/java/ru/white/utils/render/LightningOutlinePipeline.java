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
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/** Draws animated electric arcs along the exact first-person hand/item mask. */
public final class LightningOutlinePipeline {

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/lightning_outline"))
                    .withVertexShader(Identifier.of("client", "core/glass_outline"))
                    .withFragmentShader(Identifier.of("client", "core/lightning_outline"))
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("LightningOutlineData", UniformType.UNIFORM_BUFFER)
                    .withSampler("maskTexture")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int UNIFORM_SIZE = 48;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer uniformData;

    private void ensureInitialized() {
        if (uniformBuffer != null) return;

        uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:lightning_outline_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0).flip();
        dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:lightning_outline_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);
    }

    public void render(GpuTextureView maskView, GpuTextureView targetView,
                       int width, int height, int rgb, float alpha,
                       float count, float speed, float radius, float length, float swing) {
        if (maskView == null || targetView == null || alpha <= 0.001f) return;
        ensureInitialized();

        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        float time = (System.currentTimeMillis() / 1000f) * Math.max(0.1f, speed);
        float density = Math.max(0.05f, Math.min(1f, count / 32f));

        uniformData.clear();
        uniformData.putFloat(red).putFloat(green).putFloat(blue).putFloat(1f);
        uniformData.putFloat(1f / width).putFloat(1f / height).putFloat(time).putFloat(alpha);
        uniformData.putFloat(radius).putFloat(length).putFloat(density).putFloat(swing);
        uniformData.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "minecraft:lightning_outline_pass", targetView, OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("maskTexture", maskView, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("LightningOutlineData", uniformBuffer);
            pass.draw(0, 6);
        }
    }

    public void close() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (uniformData != null) { MemoryUtil.memFree(uniformData); uniformData = null; }
    }
}
