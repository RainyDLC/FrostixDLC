package dev.hatek.client.ambience.blackhole;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.hatek.client.module.impl.render.Ambience;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

public final class BlackHoleRenderer {
    static final int WRITABLE_UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
    private static final int UNIFORM_SIZE = 224;
    private static final int BLOOM_LEVELS = 6;
    private static final int BLOOM_UNIFORM_SIZE = 16;
    private static final int NOISE_SIZE = 256;
    private static final int NOISE_SHIFT_X = 37;
    private static final int NOISE_SHIFT_Y = 17;

    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("hatek", "post/customsky/customsky");
    private static final Identifier MARCH_SHADER = Identifier.fromNamespaceAndPath("hatek", "post/customsky/blackhole");
    private static final Identifier MARCH_PIPELINE_ID = Identifier.fromNamespaceAndPath("hatek", "pipeline/post/customsky/blackhole");
    private static final Identifier RESOLVE_SHADER = Identifier.fromNamespaceAndPath("hatek", "post/customsky/blackhole_resolve");
    private static final Identifier RESOLVE_PIPELINE_ID = Identifier.fromNamespaceAndPath("hatek", "pipeline/post/customsky/blackhole_resolve");
    private static final Identifier BLOOM_SHADER = Identifier.fromNamespaceAndPath("hatek", "post/customsky/bloom_down");
    private static final Identifier BLOOM_PIPELINE_ID = Identifier.fromNamespaceAndPath("hatek", "pipeline/post/customsky/bloom_down");
    private static final Identifier COMPOSITE_SHADER = Identifier.fromNamespaceAndPath("hatek", "post/customsky/composite");
    private static final Identifier COMPOSITE_PIPELINE_ID = Identifier.fromNamespaceAndPath("hatek", "pipeline/post/customsky/composite");

    private static final Vector3f BH_DIR = new Vector3f(0.34f, 0.62f, 0.71f).normalize();
    private static final float HOLE_ANGULAR_RADIUS = (float) Math.asin(7.25 / 14.0);
    private static final float HOLE_SKIP_EPS = 0.01f;

    private static final Vector4f TMP_CLIP_A = new Vector4f();
    private static final Vector3f TMP_VIEW_DIR = new Vector3f();
    private static final Vector3f TMP_RAY_DIR = new Vector3f();

    private static final Matrix4f INV_VIEW_PROJ = new Matrix4f();
    private static final Matrix4f PREV_VIEW_PROJ = new Matrix4f();

    private static RenderPipeline marchPipeline;
    private static RenderPipeline resolvePipeline;
    private static RenderPipeline bloomPipeline;
    private static RenderPipeline compositePipeline;

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer bloomUniformBuffer;

    private static final GpuTexture[] skyTextures = new GpuTexture[2];
    private static final GpuTextureView[] skyTextureViews = new GpuTextureView[2];

    private static GpuTexture marchHalfTexture;
    private static GpuTextureView marchHalfTextureView;

    private static GpuTexture noiseTexture;
    private static GpuTextureView noiseTextureView;

    private static GpuTexture blackTexture;
    private static GpuTextureView blackTextureView;

    private static final GpuTexture[] bloomTextures = new GpuTexture[BLOOM_LEVELS];
    private static final GpuTextureView[] bloomTextureViews = new GpuTextureView[BLOOM_LEVELS];
    private static final int[] bloomWidths = new int[BLOOM_LEVELS];
    private static final int[] bloomHeights = new int[BLOOM_LEVELS];

    private static int fullWidth = -1;
    private static int fullHeight = -1;
    private static int bloomSrcWidth = -1;
    private static int bloomSrcHeight = -1;

    private static int frameIndex;
    private static float taaSequence;
    private static boolean historyValid;
    private static boolean disabledAfterError;
    private static WeakReference<ClientLevel> lastLevel;

    private static GpuBuffer dummyVertexBuffer;

    private BlackHoleRenderer() {
    }

    public static boolean render(RenderTarget renderTarget) {
        if (disabledAfterError || renderTarget == null
                || renderTarget.getColorTexture() == null
                || renderTarget.getColorTextureView() == null
                || renderTarget.width <= 0 || renderTarget.height <= 0) {
            return false;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (lastLevel == null || lastLevel.get() != level) {
            lastLevel = new WeakReference<>(level);
            historyValid = false;
        }

        init();
        if (disabledAfterError || marchPipeline == null || resolvePipeline == null
                || bloomPipeline == null || compositePipeline == null
                || uniformBuffer == null || bloomUniformBuffer == null
                || noiseTextureView == null
                || !ensureTargets(renderTarget.width, renderTarget.height)
                || marchHalfTextureView == null) {
            return false;
        }

        int write = frameIndex & 1;
        int read = 1 - write;

        float bhPalette = (float) Ambience.bhPalette();
        float bhTemperature = (float) (Ambience.bhTemperature() / 100.0);
        float bhJets = (float) (Ambience.bhJets() / 100.0);
        float bhLensing = (float) (Ambience.bhLensing() / 100.0);
        float bhActivity = (float) (Ambience.bhActivity() / 100.0);
        float bhSpin = (float) (Ambience.bhSpin() / 100.0);

        int accentArgb = Style.accent();
        float colR = ((accentArgb >> 16) & 0xFF) / 255.0f;
        float colG = ((accentArgb >> 8) & 0xFF) / 255.0f;
        float colB = (accentArgb & 0xFF) / 255.0f;

        float time = (float) ((System.currentTimeMillis() % 20000000L) / 1000.0);
        float brightness = (float) Ambience.brightness();

        Matrix4f viewProj = new Matrix4f();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(viewProj);
        } else {
            viewProj.identity();
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            INV_VIEW_PROJ.set((Matrix4fc) viewProj).invert();

            boolean holeOnScreen = blackTextureView == null
                    || isHoleOnScreen(renderTarget.width, renderTarget.height);
            if (!holeOnScreen) {
                historyValid = false;
            }
            boolean useHistory = historyValid;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(UNIFORM_SIZE);
                INV_VIEW_PROJ.get(0, data);
                data.putFloat(64, time);
                data.putFloat(68, renderTarget.width);
                data.putFloat(72, renderTarget.height);
                data.putFloat(76, brightness);
                data.putFloat(80, colR);
                data.putFloat(84, colG);
                data.putFloat(88, colB);
                data.putFloat(92, 0.0f);
                data.putFloat(96, colR);
                data.putFloat(100, colG);
                data.putFloat(104, colB);
                data.putFloat(108, 1.0f);
                data.putFloat(112, (float) (frameIndex & 3));
                data.putFloat(116, 0.0f);
                data.putFloat(120, useHistory ? 1.0f : 0.0f);
                data.putFloat(124, taaSequence);
                PREV_VIEW_PROJ.get(128, data);
                data.putFloat(192, 1.0f);
                data.putFloat(196, bhPalette);
                data.putFloat(200, bhTemperature);
                data.putFloat(204, bhJets);
                data.putFloat(208, bhLensing);
                data.putFloat(212, bhActivity);
                data.putFloat(216, bhSpin);
                data.putFloat(220, 0.0f);
                data.position(0);
                encoder.writeToBuffer(uniformBuffer.slice(0L, (long) UNIFORM_SIZE), data);
            }

            GpuTextureView skyView;
            GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler repeatSampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);

            if (holeOnScreen) {
                try (RenderPass pass = encoder.createRenderPass(() -> "RainyDLC blackhole march",
                        marchHalfTextureView, Optional.empty())) {
                    pass.setPipeline(marchPipeline);
                    pass.setVertexBuffer(0, dummyVertexBuffer.slice());
                    pass.setUniform("SkyParams", uniformBuffer);
                    pass.bindTexture("NoiseTex", noiseTextureView, repeatSampler);
                    pass.draw(6, 1, 0, 0);
                }

                try (RenderPass pass = encoder.createRenderPass(() -> "RainyDLC blackhole resolve",
                        skyTextureViews[write], Optional.empty())) {
                    pass.setPipeline(resolvePipeline);
                    pass.setVertexBuffer(0, dummyVertexBuffer.slice());
                    pass.setUniform("SkyParams", uniformBuffer);
                    pass.bindTexture("Current", marchHalfTextureView, linearSampler);
                    pass.bindTexture("History", skyTextureViews[read], linearSampler);
                    pass.draw(6, 1, 0, 0);
                }

                buildBloom(encoder, skyTextureViews[write], linearSampler);
                skyView = skyTextureViews[write];
            } else {
                skyView = blackTextureView;
            }

            try (RenderPass pass = encoder.createRenderPass(() -> "RainyDLC blackhole composite",
                    renderTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(compositePipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer.slice());
                pass.setUniform("SkyParams", uniformBuffer);
                pass.bindTexture("Sky", skyView, linearSampler);
                for (int levelIdx = 0; levelIdx < BLOOM_LEVELS; ++levelIdx) {
                    pass.bindTexture("Bloom" + levelIdx, holeOnScreen ? bloomTextureViews[levelIdx] : blackTextureView,
                            linearSampler);
                }
                pass.bindTexture("NoiseTex", noiseTextureView, repeatSampler);
                pass.draw(6, 1, 0, 0);
            }

            PREV_VIEW_PROJ.set((Matrix4fc) viewProj);
            if (holeOnScreen) {
                historyValid = true;
            }
            frameIndex = (frameIndex + 1) & 0x3FFFFFFF;
            taaSequence = (taaSequence + 0.618034f) % 1.0f;
            return true;
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("RainyDLC-BlackHole").error("BlackHoleRenderer.render failed", t);
            disabledAfterError = true;
            closeTargets();
            closeUniform();
            closeBloomUniform();
            return false;
        }
    }

    private static void buildBloom(CommandEncoder encoder, GpuTextureView skyView, GpuSampler linearSampler) {
        GpuTextureView source = skyView;
        int srcWidth = bloomSrcWidth;
        int srcHeight = bloomSrcHeight;
        for (int level = 0; level < BLOOM_LEVELS; ++level) {
            final int current = level;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(BLOOM_UNIFORM_SIZE);
                data.putFloat(0, 1.0f / (float) srcWidth);
                data.putFloat(4, 1.0f / (float) srcHeight);
                data.putFloat(8, 0.0f);
                data.putFloat(12, 1.0f);
                data.position(0);
                encoder.writeToBuffer(bloomUniformBuffer.slice(0L, (long) BLOOM_UNIFORM_SIZE), data);
            }
            GpuTextureView target = bloomTextureViews[level];
            try (RenderPass pass = encoder.createRenderPass(() -> "RainyDLC blackhole bloom " + current,
                    target, Optional.empty())) {
                pass.setPipeline(bloomPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer.slice());
                pass.setUniform("BloomParams", bloomUniformBuffer);
                pass.bindTexture("Source", source, linearSampler);
                pass.draw(6, 1, 0, 0);
            }
            source = target;
            srcWidth = bloomWidths[level];
            srcHeight = bloomHeights[level];
        }
    }

    private static void init() {
        if (disabledAfterError) {
            return;
        }
        try {
            GpuDevice device = RenderSystem.getDevice();
            if (dummyVertexBuffer == null || dummyVertexBuffer.isClosed()) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    ByteBuffer buf = stack.calloc(6 * 12);
                    dummyVertexBuffer = device.createBuffer(() -> "RainyDLC dummy screenquad",
                            GpuBuffer.USAGE_VERTEX, buf);
                }
            }

            if (marchPipeline == null) {
                RenderPipeline.Builder builder = RenderPipeline.builder()
                        .withLocation(MARCH_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(MARCH_SHADER)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION)
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                        .withDepthStencilState(Optional.empty())
                        .withCull(false)
                        .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder()
                                .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                                .withSampler("NoiseTex")
                                .build());
                marchPipeline = builder.build();
                if (!device.precompilePipeline(marchPipeline, BlackHoleShaders.INSTANCE).isValid()) {
                    throw new IllegalStateException("Black hole march shader did not compile");
                }
            }

            if (resolvePipeline == null) {
                RenderPipeline.Builder builder = RenderPipeline.builder()
                        .withLocation(RESOLVE_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(RESOLVE_SHADER)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION)
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                        .withDepthStencilState(Optional.empty())
                        .withCull(false)
                        .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder()
                                .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                                .withSampler("Current")
                                .withSampler("History")
                                .build());
                resolvePipeline = builder.build();
                if (!device.precompilePipeline(resolvePipeline, BlackHoleShaders.INSTANCE).isValid()) {
                    throw new IllegalStateException("Black hole resolve shader did not compile");
                }
            }

            if (bloomPipeline == null) {
                RenderPipeline.Builder builder = RenderPipeline.builder()
                        .withLocation(BLOOM_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(BLOOM_SHADER)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION)
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                        .withDepthStencilState(Optional.empty())
                        .withCull(false)
                        .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder()
                                .withUniform("BloomParams", UniformType.UNIFORM_BUFFER)
                                .withSampler("Source")
                                .build());
                bloomPipeline = builder.build();
                if (!device.precompilePipeline(bloomPipeline, BlackHoleShaders.INSTANCE).isValid()) {
                    throw new IllegalStateException("Black hole bloom shader did not compile");
                }
            }

            if (compositePipeline == null) {
                var bgBuilder = com.mojang.blaze3d.pipeline.BindGroupLayout.builder()
                        .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                        .withSampler("Sky");
                for (int i = 0; i < BLOOM_LEVELS; ++i) {
                    bgBuilder.withSampler("Bloom" + i);
                }
                bgBuilder.withSampler("NoiseTex");

                RenderPipeline.Builder builder = RenderPipeline.builder()
                        .withLocation(COMPOSITE_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(COMPOSITE_SHADER)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION)
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                        .withDepthStencilState(Optional.empty())
                        .withCull(false)
                        .withBindGroupLayout(bgBuilder.build());
                compositePipeline = builder.build();
                if (!device.precompilePipeline(compositePipeline, BlackHoleShaders.INSTANCE).isValid()) {
                    throw new IllegalStateException("Black hole composite shader did not compile");
                }
            }

            if (bloomUniformBuffer == null || bloomUniformBuffer.isClosed() || bloomUniformBuffer.size() < BLOOM_UNIFORM_SIZE) {
                closeBloomUniform();
                bloomUniformBuffer = device.createBuffer(() -> "RainyDLC blackhole bloom uniform",
                        WRITABLE_UNIFORM_USAGE, (long) BLOOM_UNIFORM_SIZE);
            }

            if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_SIZE) {
                closeUniform();
                uniformBuffer = device.createBuffer(() -> "RainyDLC blackhole uniform",
                        WRITABLE_UNIFORM_USAGE, (long) UNIFORM_SIZE);
            }

            ensureNoiseTexture();
            ensureBlackTexture();
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("RainyDLC-BlackHole").error("BlackHoleRenderer.init failed", t);
            disabledAfterError = true;
            marchPipeline = null;
            resolvePipeline = null;
            bloomPipeline = null;
            compositePipeline = null;
            closeUniform();
            closeBloomUniform();
            closeNoise();
            closeBlack();
        }
    }

    private static boolean isHoleOnScreen(int width, int height) {
        rayDirNdc(0.0f, 0.0f, TMP_VIEW_DIR);
        float ex = 1.0f + 2.0f / (float) Math.max(1, width);
        float ey = 1.0f + 2.0f / (float) Math.max(1, height);
        float minCos = 1.0f;
        rayDirNdc(ex, ey, TMP_RAY_DIR);
        minCos = Math.min(minCos, TMP_VIEW_DIR.dot(TMP_RAY_DIR));
        rayDirNdc(-ex, ey, TMP_RAY_DIR);
        minCos = Math.min(minCos, TMP_VIEW_DIR.dot(TMP_RAY_DIR));
        rayDirNdc(ex, -ey, TMP_RAY_DIR);
        minCos = Math.min(minCos, TMP_VIEW_DIR.dot(TMP_RAY_DIR));
        rayDirNdc(-ex, -ey, TMP_RAY_DIR);
        minCos = Math.min(minCos, TMP_VIEW_DIR.dot(TMP_RAY_DIR));
        float cornerAngle = (float) Math.acos(Math.clamp(minCos, -1.0f, 1.0f));
        float toHole = (float) Math.acos(Math.clamp(TMP_VIEW_DIR.dot(BH_DIR), -1.0f, 1.0f));
        return toHole <= cornerAngle + HOLE_ANGULAR_RADIUS + HOLE_SKIP_EPS;
    }

    private static void rayDirNdc(float ndcX, float ndcY, Vector3f dest) {
        // Rotation-only VP: the camera is at the origin. Do not subtract clip endpoints:
        // 26.2 uses reversed Z, and some backends also use the [0, 1] clip range.
        TMP_CLIP_A.set(ndcX, ndcY, 0.5f, 1.0f);
        INV_VIEW_PROJ.transform(TMP_CLIP_A);
        dest.set(TMP_CLIP_A.x, TMP_CLIP_A.y, TMP_CLIP_A.z).div(TMP_CLIP_A.w).normalize();
    }

    private static void ensureBlackTexture() {
        if (blackTexture != null && !blackTexture.isClosed() && blackTextureView != null) {
            return;
        }
        closeBlack();
        GpuDevice device = RenderSystem.getDevice();
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, 0xFF000000);
        blackTexture = device.createTexture(() -> "RainyDLC blackhole black 1x1",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        device.createCommandEncoder().writeToTexture(blackTexture, image);
        blackTextureView = device.createTextureView(blackTexture);
        image.close();
    }

    private static void closeBlack() {
        if (blackTextureView != null) {
            blackTextureView.close();
            blackTextureView = null;
        }
        if (blackTexture != null) {
            blackTexture.close();
            blackTexture = null;
        }
    }

    private static void ensureNoiseTexture() {
        if (noiseTexture != null && !noiseTexture.isClosed() && noiseTextureView != null) {
            return;
        }
        closeNoise();
        GpuDevice device = RenderSystem.getDevice();
        int[] red = new int[NOISE_SIZE * NOISE_SIZE];
        int[] blue = new int[NOISE_SIZE * NOISE_SIZE];
        Random random = new Random(1592639215L);
        for (int i = 0; i < red.length; ++i) {
            red[i] = random.nextInt(256);
            blue[i] = random.nextInt(256);
        }
        NativeImage image = new NativeImage(NOISE_SIZE, NOISE_SIZE, false);
        for (int y = 0; y < NOISE_SIZE; ++y) {
            for (int x = 0; x < NOISE_SIZE; ++x) {
                int sx = Math.floorMod(x - NOISE_SHIFT_X, NOISE_SIZE);
                int sy = Math.floorMod(y - NOISE_SHIFT_Y, NOISE_SIZE);
                int r = red[y * NOISE_SIZE + x];
                int g = red[sy * NOISE_SIZE + sx];
                int b = blue[y * NOISE_SIZE + x];
                image.setPixel(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        noiseTexture = device.createTexture(() -> "RainyDLC blackhole noise",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, NOISE_SIZE, NOISE_SIZE, 1, 1);
        device.createCommandEncoder().writeToTexture(noiseTexture, image);
        noiseTextureView = device.createTextureView(noiseTexture);
        image.close();
    }

    private static void closeNoise() {
        if (noiseTextureView != null) {
            noiseTextureView.close();
            noiseTextureView = null;
        }
        if (noiseTexture != null) {
            noiseTexture.close();
            noiseTexture = null;
        }
    }

    private static boolean ensureTargets(int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        if (skyTextures[0] != null && skyTextures[1] != null && fullWidth == width && fullHeight == height
                && marchHalfTexture != null) {
            return true;
        }
        closeTargets();

        for (int i = 0; i < 2; ++i) {
            final int index = i;
            skyTextures[i] = device.createTexture(() -> "RainyDLC blackhole sky " + index,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            skyTextureViews[i] = device.createTextureView(skyTextures[i]);
        }

        int hw = Math.max(1, (width + 1) / 2);
        int hh = Math.max(1, (height + 1) / 2);
        marchHalfTexture = device.createTexture(() -> "RainyDLC blackhole march half",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                GpuFormat.RGBA8_UNORM, hw, hh, 1, 1);
        marchHalfTextureView = device.createTextureView(marchHalfTexture);

        bloomSrcWidth = Math.max(1, width / 2);
        bloomSrcHeight = Math.max(1, height / 2);

        int bw = bloomSrcWidth;
        int bh = bloomSrcHeight;
        for (int level = 0; level < BLOOM_LEVELS; ++level) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            final int index = level;
            bloomTextures[level] = device.createTexture(() -> "RainyDLC blackhole bloom " + index,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    GpuFormat.RGBA8_UNORM, bw, bh, 1, 1);
            bloomTextureViews[level] = device.createTextureView(bloomTextures[level]);
            bloomWidths[level] = bw;
            bloomHeights[level] = bh;
        }

        fullWidth = width;
        fullHeight = height;
        historyValid = false;
        return true;
    }

    public static void release() {
        closeTargets();
        closeUniform();
        closeBloomUniform();
        closeNoise();
        closeBlack();
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        disabledAfterError = false;
        historyValid = false;
    }

    private static void closeTargets() {
        for (int i = 0; i < 2; ++i) {
            if (skyTextureViews[i] != null) {
                skyTextureViews[i].close();
                skyTextureViews[i] = null;
            }
            if (skyTextures[i] != null) {
                skyTextures[i].close();
                skyTextures[i] = null;
            }
        }
        if (marchHalfTextureView != null) {
            marchHalfTextureView.close();
            marchHalfTextureView = null;
        }
        if (marchHalfTexture != null) {
            marchHalfTexture.close();
            marchHalfTexture = null;
        }
        for (int level = 0; level < BLOOM_LEVELS; ++level) {
            if (bloomTextureViews[level] != null) {
                bloomTextureViews[level].close();
                bloomTextureViews[level] = null;
            }
            if (bloomTextures[level] != null) {
                bloomTextures[level].close();
                bloomTextures[level] = null;
            }
            bloomWidths[level] = 0;
            bloomHeights[level] = 0;
        }
        fullWidth = -1;
        fullHeight = -1;
        bloomSrcWidth = -1;
        bloomSrcHeight = -1;
        historyValid = false;
    }

    private static void closeBloomUniform() {
        if (bloomUniformBuffer != null) {
            bloomUniformBuffer.close();
            bloomUniformBuffer = null;
        }
    }

    private static void closeUniform() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
    }
}
