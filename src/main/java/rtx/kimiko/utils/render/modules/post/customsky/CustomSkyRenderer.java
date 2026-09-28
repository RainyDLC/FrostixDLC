package rtx.kimiko.utils.render.modules.post.customsky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import rtx.kimiko.Kimiko;
import rtx.kimiko.api.modules.impl.Visuals.Ambience;
import rtx.kimiko.utils.render.modules.post.usersky.UserSkyManager;
import rtx.kimiko.utils.render.others.RenderSampler;
import rtx.kimiko.utils.render.render2d.ThemeWaveUniform;

/**
 * Кастомное небо Ambience.
 *
 * PERF (картинка та же):
 *  - убрана полноэкранная копия цвета сцены каждый кадр: composite теперь делает discard
 *    на пикселях с геометрией, а там в буфере и так уже лежит сцена;
 *  - для аврор/звездопада/своего шейдера строятся только 3 уровня bloom из 6 —
 *    composite для них читает только Bloom0..2, остальные 3 прохода были впустую;
 *  - сам шейдер чёрной дыры ускорен (см. CustomskyFastShaders).
 */
public final class CustomSkyRenderer {
    @NotNull
    public static final CustomSkyRenderer INSTANCE = new CustomSkyRenderer();
    private static final int TYPE_COUNT = 3;
    private static final int TYPE_BLACKHOLE = 1;
    private static final int TYPE_STARFALL = 2;
    private static final int TYPE_USER = 3;
    private static final int UNIFORM_SIZE = 208;
    private static final int BLOOM_LEVELS = 6;
    /** Сколько уровней bloom реально читает composite для не-HDR неба (Bloom0..Bloom2). */
    private static final int BLOOM_LEVELS_LDR = 3;
    private static final int BLOOM_UNIFORM_SIZE = 16;
    private static final float BLOOM_THRESHOLD = 0.28f;
    private static final int NOISE_SIZE = 256;
    private static final int NOISE_SHIFT_X = 37;
    private static final int NOISE_SHIFT_Y = 17;

    @NotNull private static final Identifier VERTEX_SHADER = INSTANCE.id("post/customsky/customsky");
    @NotNull private static final Identifier COMPOSITE_SHADER = INSTANCE.id("post/customsky/composite");
    @NotNull private static final Identifier COMPOSITE_PIPELINE_ID = INSTANCE.id("pipeline/post/customsky/composite");
    @NotNull private static final Identifier BLOOM_SHADER = INSTANCE.id("post/customsky/bloom_down");
    @NotNull private static final Identifier BLOOM_PIPELINE_ID = INSTANCE.id("pipeline/post/customsky/bloom_down");
    @NotNull private static final Identifier[] MARCH_SHADERS = new Identifier[]{
            INSTANCE.id("post/customsky/aurora"), INSTANCE.id("post/customsky/blackhole"), INSTANCE.id("post/customsky/starfall")};
    @NotNull private static final Identifier[] MARCH_PIPELINE_IDS = new Identifier[]{
            INSTANCE.id("pipeline/post/customsky/aurora"), INSTANCE.id("pipeline/post/customsky/blackhole"), INSTANCE.id("pipeline/post/customsky/starfall")};
    @NotNull private static final String[] MARCH_PASS_NAMES = new String[]{
            "kimiko:customsky_march_aurora", "kimiko:customsky_march_blackhole", "kimiko:customsky_march_starfall"};

    @NotNull private static final Matrix4f INV_VIEW_PROJ = new Matrix4f();
    @NotNull private static final Matrix4f PREV_VIEW_PROJ = new Matrix4f();
    @NotNull private static final Matrix4f PENDING_VIEW_PROJ = new Matrix4f();

    @NotNull private static final RenderPipeline[] marchPipelines = new RenderPipeline[TYPE_COUNT];
    @Nullable private static RenderPipeline compositePipeline;
    @Nullable private static RenderPipeline bloomPipeline;
    @Nullable private static GpuBuffer uniformBuffer;
    @Nullable private static GpuBuffer bloomUniformBuffer;
    @NotNull private static final GpuTexture[] skyTextures = new GpuTexture[2];
    @NotNull private static final GpuTextureView[] skyTextureViews = new GpuTextureView[2];
    @Nullable private static GpuTexture noiseTexture;
    @Nullable private static GpuTextureView noiseTextureView;
    @NotNull private static final GpuTexture[] bloomTextures = new GpuTexture[BLOOM_LEVELS];
    @NotNull private static final GpuTextureView[] bloomTextureViews = new GpuTextureView[BLOOM_LEVELS];
    @NotNull private static final int[] bloomWidths = new int[BLOOM_LEVELS];
    @NotNull private static final int[] bloomHeights = new int[BLOOM_LEVELS];

    private static int fullWidth = -1;
    private static int fullHeight = -1;
    private static int marchWidth = -1;
    private static int marchHeight = -1;
    private static int currentScale = -1;
    private static int frameIndex;
    private static float taaSequence;
    private static int lastType = -1;
    private static boolean historyValid;
    private static boolean disabledAfterError;
    private static boolean pendingValid;
    private static boolean appliedThisFrame;
    @Nullable private static WeakReference<ClientWorld> lastLevel;

    private CustomSkyRenderer() {
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    public static boolean isAllocated() {
        return skyTextures[0] != null;
    }

    public static void beginFrame() {
    }

    public static void prepareFrame(@NotNull Matrix4f viewProj) {
        ClientWorld level = MinecraftClient.getInstance().world;
        WeakReference<ClientWorld> last = lastLevel;
        if (last == null || last.get() != level) {
            lastLevel = new WeakReference<>(level);
            invalidate();
        }
        PENDING_VIEW_PROJ.set((Matrix4fc) viewProj);
        pendingValid = true;
        appliedThisFrame = false;
    }

    public static void invalidate() {
        pendingValid = false;
        appliedThisFrame = false;
        disabledAfterError = false;
        historyValid = false;
        lastType = -1;
        clear();
    }

    public static void finishFrame() {
        pendingValid = false;
    }

    public static void applyPending(@Nullable Framebuffer renderTarget) {
        if (appliedThisFrame || !pendingValid || renderTarget == null) {
            return;
        }
        Ambience ambience = Ambience.Companion.getInstance();
        if (ambience == null || !ambience.isCustomSkyActive()) {
            if (isAllocated()) {
                clear();
            }
            return;
        }
        appliedThisFrame = true;
        beginFrame();
        float time = (float) ((double) (System.currentTimeMillis() % 20000000L) / 1000.0);
        int color = ambience.skyColorRGB();
        int color2 = ambience.skyColor2RGB();
        apply(renderTarget, PENDING_VIEW_PROJ, time, ambience.skyTypeIndex(),
                (float) (color >> 16 & 0xFF) / 255.0f, (float) (color >> 8 & 0xFF) / 255.0f, (float) (color & 0xFF) / 255.0f,
                (float) (color2 >> 16 & 0xFF) / 255.0f, (float) (color2 >> 8 & 0xFF) / 255.0f, (float) (color2 & 0xFF) / 255.0f,
                ambience.skyGradientMode(), ambience.skyBrightness(), ambience.skyUsesClientColor());
    }

    public static void apply(@Nullable Framebuffer renderTarget, @Nullable Matrix4f viewProj, float time, int skyType,
                             float colorR, float colorG, float colorB, float color2R, float color2G, float color2B,
                             float gradientMode, float brightness, boolean useClientColor) {
        if (disabledAfterError || renderTarget == null || viewProj == null || renderTarget.getColorAttachment() == null
                || renderTarget.getColorAttachmentView() == null || renderTarget.getDepthAttachmentView() == null
                || renderTarget.textureWidth <= 0 || renderTarget.textureHeight <= 0) {
            return;
        }
        int type = Math.clamp((long) skyType, 0, 3);
        boolean hdr = type == TYPE_BLACKHOLE;
        INSTANCE.init(type);
        RenderPipeline marchPipeline = type == TYPE_USER ? UserSkyManager.activePipeline() : marchPipelines[type];
        if (marchPipeline == null || compositePipeline == null || bloomPipeline == null || uniformBuffer == null
                || bloomUniformBuffer == null || (hdr && noiseTextureView == null)
                || !INSTANCE.ensureTargets(renderTarget.textureWidth, renderTarget.textureHeight, INSTANCE.resScale(type))) {
            return;
        }
        if (lastType != type) {
            historyValid = false;
            lastType = type;
        }
        int write = frameIndex & 1;
        int read = 1 - write;
        boolean useHistory = hdr && historyValid;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            INV_VIEW_PROJ.set((Matrix4fc) viewProj).invert();
            float jitterX = INSTANCE.halton(frameIndex % 8 + 1, 2) - 0.5f;
            float jitterY = INSTANCE.halton(frameIndex % 8 + 1, 3) - 0.5f;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(UNIFORM_SIZE);
                INV_VIEW_PROJ.get(0, data);
                data.putFloat(64, time);
                data.putFloat(68, renderTarget.textureWidth);
                data.putFloat(72, renderTarget.textureHeight);
                data.putFloat(76, brightness);
                data.putFloat(80, colorR);
                data.putFloat(84, colorG);
                data.putFloat(88, colorB);
                data.putFloat(92, gradientMode);
                data.putFloat(96, color2R);
                data.putFloat(100, color2G);
                data.putFloat(104, color2B);
                data.putFloat(108, type);
                data.putFloat(112, hdr ? jitterX : 0.0f);
                data.putFloat(116, hdr ? jitterY : 0.0f);
                data.putFloat(120, useHistory ? 1.0f : 0.0f);
                data.putFloat(124, taaSequence);
                PREV_VIEW_PROJ.get(128, data);
                data.putFloat(192, useClientColor ? 1.0f : 0.0f);
                data.position(0);
                encoder.writeToBuffer(uniformBuffer.slice(0L, (long) UNIFORM_SIZE), data);
            }

            String marchName = type == TYPE_USER ? "kimiko:customsky_march_user" : MARCH_PASS_NAMES[type];
            try (RenderPass pass = encoder.createRenderPass(() -> marchName, skyTextureViews[write], OptionalInt.empty())) {
                pass.setPipeline(marchPipeline);
                pass.setUniform("SkyParams", uniformBuffer);
                if (type != TYPE_USER) {
                    ThemeWaveUniform.bind(pass);
                }
                if (hdr) {
                    pass.bindTexture("NoiseTex", noiseTextureView, RenderSampler.linearRepeat());
                    pass.bindTexture("History", skyTextureViews[read], RenderSampler.linear());
                }
                pass.draw(0, 6);
            }

            INSTANCE.buildBloom(encoder, skyTextureViews[write], hdr);

            // Без копии сцены: composite делает discard там, где есть геометрия.
            try (RenderPass pass = encoder.createRenderPass(() -> "kimiko:customsky_composite",
                    renderTarget.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(compositePipeline);
                pass.setUniform("SkyParams", uniformBuffer);
                pass.bindTexture("DepthTex", renderTarget.getDepthAttachmentView(), RenderSampler.nearest());
                pass.bindTexture("Sky", skyTextureViews[write], RenderSampler.linear());
                for (int level = 0; level < BLOOM_LEVELS; ++level) {
                    pass.bindTexture("Bloom" + level, bloomTextureViews[level], RenderSampler.linear());
                }
                pass.draw(0, 6);
            }

            PREV_VIEW_PROJ.set((Matrix4fc) viewProj);
            historyValid = true;
            frameIndex = frameIndex + 1 & 0x3FFFFFFF;
            taaSequence = (taaSequence + 0.618034f) % 1.0f;
        } catch (Throwable throwable) {
            disabledAfterError = true;
            INSTANCE.closeTargets();
            INSTANCE.closeUniform();
            INSTANCE.closeBloomUniform();
        }
    }

    private void buildBloom(CommandEncoder encoder, GpuTextureView skyView, boolean hdr) {
        GpuTextureView source = skyView;
        int srcWidth = marchWidth;
        int srcHeight = marchHeight;
        int levels = hdr ? BLOOM_LEVELS : BLOOM_LEVELS_LDR;
        for (int level = 0; level < levels; ++level) {
            final int current = level;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(BLOOM_UNIFORM_SIZE);
                data.putFloat(0, 1.0f / (float) srcWidth);
                data.putFloat(4, 1.0f / (float) srcHeight);
                data.putFloat(8, !hdr && level == 0 ? BLOOM_THRESHOLD : 0.0f);
                data.putFloat(12, hdr ? 1.0f : 0.0f);
                data.position(0);
                encoder.writeToBuffer(bloomUniformBuffer.slice(0L, (long) BLOOM_UNIFORM_SIZE), data);
            }
            GpuTextureView target = bloomTextureViews[level];
            try (RenderPass pass = encoder.createRenderPass(() -> "kimiko:customsky_bloom" + current, target, OptionalInt.empty())) {
                pass.setPipeline(bloomPipeline);
                pass.setUniform("BloomParams", bloomUniformBuffer);
                pass.bindTexture("Source", source, RenderSampler.linear());
                pass.draw(0, 6);
            }
            source = target;
            srcWidth = bloomWidths[level];
            srcHeight = bloomHeights[level];
        }
    }

    private void init(int type) {
        if (disabledAfterError) {
            return;
        }
        try {
            if (type != TYPE_USER && marchPipelines[type] == null) {
                RenderPipeline.Builder builder = RenderPipeline.builder(new RenderPipeline.Snippet[0])
                        .withLocation(MARCH_PIPELINE_IDS[type])
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(MARCH_SHADERS[type])
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                        .withUniform("ThemeWaveParams", UniformType.UNIFORM_BUFFER)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false);
                if (type == TYPE_BLACKHOLE) {
                    builder.withSampler("NoiseTex").withSampler("History");
                }
                marchPipelines[type] = RenderPipelines.register(builder.build());
            }
            if (compositePipeline == null) {
                RenderPipeline.Builder builder = RenderPipeline.builder(new RenderPipeline.Snippet[0])
                        .withLocation(COMPOSITE_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(COMPOSITE_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                        .withSampler("DepthTex")
                        .withSampler("Sky");
                for (int level = 0; level < BLOOM_LEVELS; ++level) {
                    builder.withSampler("Bloom" + level);
                }
                compositePipeline = RenderPipelines.register(builder.withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            if (bloomPipeline == null) {
                bloomPipeline = RenderPipelines.register(RenderPipeline.builder(new RenderPipeline.Snippet[0])
                        .withLocation(BLOOM_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(BLOOM_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("BloomParams", UniformType.UNIFORM_BUFFER)
                        .withSampler("Source")
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            GpuBuffer bloomUniform = bloomUniformBuffer;
            if (bloomUniform == null || bloomUniform.isClosed() || bloomUniform.size() < BLOOM_UNIFORM_SIZE) {
                this.closeBloomUniform();
                bloomUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "kimiko:customsky_bloom_uniforms", 136, (long) BLOOM_UNIFORM_SIZE);
            }
            GpuBuffer uniform = uniformBuffer;
            if (uniform == null || uniform.isClosed() || uniform.size() < UNIFORM_SIZE) {
                this.closeUniform();
                uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "kimiko:customsky_uniforms", 136, (long) UNIFORM_SIZE);
            }
            if (type == TYPE_BLACKHOLE) {
                this.ensureNoiseTexture();
            }
        } catch (Throwable throwable) {
            disabledAfterError = true;
            for (int i = 0; i < TYPE_COUNT; ++i) {
                marchPipelines[i] = null;
            }
            compositePipeline = null;
            bloomPipeline = null;
            this.closeUniform();
            this.closeBloomUniform();
            this.closeNoise();
        }
    }

    private void ensureNoiseTexture() {
        GpuTexture current = noiseTexture;
        if (current != null && !current.isClosed()) {
            return;
        }
        this.closeNoise();
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
                image.setColor(x, y, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
        noiseTexture = device.createTexture(() -> "kimiko:customsky_noise", 5, TextureFormat.RGBA8, NOISE_SIZE, NOISE_SIZE, 1, 1);
        device.createCommandEncoder().writeToTexture(noiseTexture, image);
        noiseTextureView = device.createTextureView(noiseTexture);
        image.close();
    }

    private int resScale(int type) {
        return type == TYPE_BLACKHOLE ? 2 : 1;
    }

    private boolean ensureTargets(int width, int height, int scale) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }
        if (skyTextures[0] != null && skyTextures[1] != null && fullWidth == width && fullHeight == height && currentScale == scale) {
            return true;
        }
        this.closeTargets();
        int hw = Math.max(1, width / scale);
        int hh = Math.max(1, height / scale);
        for (int i = 0; i < 2; ++i) {
            final int index = i;
            skyTextures[i] = device.createTexture(() -> "kimiko:customsky_march" + index, 12, TextureFormat.RGBA8, hw, hh, 1, 1);
            skyTextureViews[i] = device.createTextureView(skyTextures[i]);
        }
        int bw = hw;
        int bh = hh;
        for (int level = 0; level < BLOOM_LEVELS; ++level) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            final int index = level;
            bloomTextures[level] = device.createTexture(() -> "kimiko:customsky_bloom" + index, 12, TextureFormat.RGBA8, bw, bh, 1, 1);
            bloomTextureViews[level] = device.createTextureView(bloomTextures[level]);
            bloomWidths[level] = bw;
            bloomHeights[level] = bh;
        }
        fullWidth = width;
        fullHeight = height;
        marchWidth = hw;
        marchHeight = hh;
        currentScale = scale;
        historyValid = false;
        return true;
    }

    private float halton(int index, int base) {
        float result = 0.0f;
        float f = 1.0f;
        for (int i = index; i > 0; i /= base) {
            result += (f /= (float) base) * (float) (i % base);
        }
        return result;
    }

    public static void clear() {
        INSTANCE.closeTargets();
    }

    private void closeTargets() {
        for (int i = 0; i < 2; ++i) {
            if (skyTextureViews[i] != null) {
                skyTextureViews[i].close();
            }
            skyTextureViews[i] = null;
            if (skyTextures[i] != null) {
                skyTextures[i].close();
            }
            skyTextures[i] = null;
        }
        for (int level = 0; level < BLOOM_LEVELS; ++level) {
            if (bloomTextureViews[level] != null) {
                bloomTextureViews[level].close();
            }
            bloomTextureViews[level] = null;
            if (bloomTextures[level] != null) {
                bloomTextures[level].close();
            }
            bloomTextures[level] = null;
            bloomWidths[level] = 0;
            bloomHeights[level] = 0;
        }
        fullWidth = -1;
        fullHeight = -1;
        historyValid = false;
    }

    private void closeNoise() {
        if (noiseTextureView != null) {
            noiseTextureView.close();
        }
        noiseTextureView = null;
        if (noiseTexture != null) {
            noiseTexture.close();
        }
        noiseTexture = null;
    }

    private void closeBloomUniform() {
        if (bloomUniformBuffer != null) {
            bloomUniformBuffer.close();
        }
        bloomUniformBuffer = null;
    }

    private void closeUniform() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
        }
        uniformBuffer = null;
    }

    private Identifier id(String path) {
        return Identifier.of(Kimiko.Companion.namespace(), path);
    }
}
