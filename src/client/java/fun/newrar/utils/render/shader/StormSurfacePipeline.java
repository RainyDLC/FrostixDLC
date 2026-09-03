package fun.newrar.utils.render.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class StormSurfacePipeline implements AutoCloseable {
    private static final Identifier PIPELINE_ID = Identifier.of("client", "pipeline/storm_surface");
    private static final Identifier SHADER_ID = Identifier.of("client", "core/storm_surface");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(SHADER_ID)
                    .withFragmentShader(SHADER_ID)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("StormData", UniformType.UNIFORM_BUFFER)
                    .withSampler("DepthTex")
                    .withSampler("SceneTex")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0f, 0f, 0f);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final int UNIFORM_BYTES = 512;
    private static final int BUFFER_COUNT = 8;

    private GpuBuffer[] uniformRing;
    private int ringIndex;
    private GpuBuffer dummyQuadVbo;
    private ByteBuffer uniformMem;

    private GpuTexture colorSnapshot;
    private GpuTextureView colorSnapshotView;
    private GpuTexture depthSnapshot;
    private GpuTextureView depthSnapshotView;

    private int prevW = -1;
    private int prevH = -1;
    private boolean ready;

    public static final class SurfaceConfig {
        public float dampness = 1.0f;
        public float puddleSpread = 0.65f;
        public float glossiness = 1.2f;
        public float rippleAmt = 0.0f;
        public float range = 48.0f;
        public float lightningGlint = 0.0f;
        public int raySteps = 10;
    }

    private void initBuffers() {
        if (ready) return;

        uniformMem = MemoryUtil.memAlloc(UNIFORM_BYTES);

        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0).flip();
        dummyQuadVbo = RenderSystem.getDevice().createBuffer(
                () -> "client:storm_surface_dummy",
                GpuBuffer.USAGE_VERTEX,
                dummy
        );
        MemoryUtil.memFree(dummy);

        uniformRing = new GpuBuffer[BUFFER_COUNT];
        ready = true;
    }

    private GpuBuffer acquireUniformBuffer() {
        GpuBuffer buf = uniformRing[ringIndex];
        if (buf == null) {
            final int slot = ringIndex;
            buf = RenderSystem.getDevice().createBuffer(
                    () -> "client:storm_surface_ubo_" + slot,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            uniformRing[ringIndex] = buf;
        }
        ringIndex = (ringIndex + 1) % BUFFER_COUNT;
        return buf;
    }

    private void allocateSnapshots(int width, int height) {
        if (colorSnapshot != null && depthSnapshot != null && width == prevW && height == prevH) {
            return;
        }

        destroySnapshots();

        colorSnapshot = RenderSystem.getDevice().createTexture(
                () -> "client:storm_surface_color",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        colorSnapshotView = RenderSystem.getDevice().createTextureView(colorSnapshot);

        depthSnapshot = RenderSystem.getDevice().createTexture(
                () -> "client:storm_surface_depth",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.DEPTH32,
                width, height, 1, 1
        );
        depthSnapshotView = RenderSystem.getDevice().createTextureView(depthSnapshot);

        prevW = width;
        prevH = height;
    }

    private void destroySnapshots() {
        if (colorSnapshotView != null) {
            colorSnapshotView.close();
            colorSnapshotView = null;
        }
        if (colorSnapshot != null) {
            colorSnapshot.close();
            colorSnapshot = null;
        }
        if (depthSnapshotView != null) {
            depthSnapshotView.close();
            depthSnapshotView = null;
        }
        if (depthSnapshot != null) {
            depthSnapshot.close();
            depthSnapshot = null;
        }
        prevW = -1;
        prevH = -1;
    }

    public boolean apply(
            GpuTextureView outputTarget,
            GpuTexture rawColor,
            GpuTexture rawDepth,
            int fbW,
            int fbH,
            Camera cam,
            PlayerEntity player,
            Matrix4f viewMat,
            Matrix4f projMat,
            SurfaceConfig config
    ) {
        if (outputTarget == null || rawColor == null || rawDepth == null || cam == null
                || viewMat == null || projMat == null || config == null) {
            return false;
        }
        if (fbW <= 2 || fbH <= 2 || config.dampness <= 0.005f) {
            return false;
        }

        initBuffers();
        allocateSnapshots(fbW, fbH);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(rawColor, colorSnapshot, 0, 0, 0, 0, 0, fbW, fbH);
        encoder.copyTextureToTexture(rawDepth, depthSnapshot, 0, 0, 0, 0, 0, fbW, fbH);

        Matrix4f invV = new Matrix4f(viewMat).invert();
        Matrix4f invP = new Matrix4f(projMat).invert();
        Vec3d eyePos = cam.getCameraPos();
        invV.m30((float) eyePos.x);
        invV.m31((float) eyePos.y);
        invV.m32((float) eyePos.z);

        serializeUniforms(invV, invP, viewMat, projMat, eyePos, player, fbW, fbH, config);

        GpuBuffer ubo = acquireUniformBuffer();
        encoder.writeToBuffer(ubo.slice(0, uniformMem.remaining()), uniformMem);

        GpuBufferSlice dynTransforms = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(),
                COLOR_MODULATOR,
                MODEL_OFFSET,
                TEXTURE_MATRIX
        );

        GpuSampler pointSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass pass = encoder.createRenderPass(
                () -> "client:storm_surface_pass",
                outputTarget,
                OptionalInt.empty()
        )) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyQuadVbo);

            pass.bindTexture("DepthTex", depthSnapshotView, pointSampler);
            pass.bindTexture("SceneTex", colorSnapshotView, linearSampler);

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynTransforms);
            pass.setUniform("StormData", ubo);

            pass.draw(0, 6);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void serializeUniforms(
            Matrix4f invV,
            Matrix4f invP,
            Matrix4f v,
            Matrix4f p,
            Vec3d eye,
            PlayerEntity player,
            int w,
            int h,
            SurfaceConfig cfg
    ) {
        uniformMem.clear();

        appendMatrix(uniformMem, invV);
        appendMatrix(uniformMem, invP);
        appendMatrix(uniformMem, v);
        appendMatrix(uniformMem, p);

        uniformMem.putFloat((float) eye.x);
        uniformMem.putFloat((float) eye.y);
        uniformMem.putFloat((float) eye.z);
        uniformMem.putFloat(1.0f);

        uniformMem.putFloat((float) w);
        uniformMem.putFloat((float) h);
        uniformMem.putFloat((float) ((System.currentTimeMillis() % 600_000L) / 1000.0));
        uniformMem.putFloat(cfg.range);

        uniformMem.putFloat(cfg.dampness);
        uniformMem.putFloat(cfg.puddleSpread);
        uniformMem.putFloat(cfg.glossiness);
        uniformMem.putFloat(cfg.rippleAmt);

        uniformMem.putFloat(cfg.lightningGlint);
        uniformMem.putFloat(0.0f);
        uniformMem.putFloat((float) cfg.raySteps);
        uniformMem.putFloat(0.0f);

        if (player != null) {
            uniformMem.putFloat((float) player.getX());
            uniformMem.putFloat((float) player.getY());
            uniformMem.putFloat((float) player.getZ());
            uniformMem.putFloat(player.getHeight());
        } else {
            uniformMem.putFloat(0.0f);
            uniformMem.putFloat(-999.0f);
            uniformMem.putFloat(0.0f);
            uniformMem.putFloat(0.0f);
        }

        uniformMem.flip();
    }

    private static void appendMatrix(ByteBuffer buf, Matrix4f mat) {
        buf.putFloat(mat.m00()).putFloat(mat.m01()).putFloat(mat.m02()).putFloat(mat.m03());
        buf.putFloat(mat.m10()).putFloat(mat.m11()).putFloat(mat.m12()).putFloat(mat.m13());
        buf.putFloat(mat.m20()).putFloat(mat.m21()).putFloat(mat.m22()).putFloat(mat.m23());
        buf.putFloat(mat.m30()).putFloat(mat.m31()).putFloat(mat.m32()).putFloat(mat.m33());
    }

    @Override
    public void close() {
        destroySnapshots();
        if (uniformRing != null) {
            for (int i = 0; i < uniformRing.length; i++) {
                if (uniformRing[i] != null) {
                    uniformRing[i].close();
                    uniformRing[i] = null;
                }
            }
            uniformRing = null;
        }
        ringIndex = 0;
        if (dummyQuadVbo != null) {
            dummyQuadVbo.close();
            dummyQuadVbo = null;
        }
        if (uniformMem != null) {
            MemoryUtil.memFree(uniformMem);
            uniformMem = null;
        }
        ready = false;
    }
}
