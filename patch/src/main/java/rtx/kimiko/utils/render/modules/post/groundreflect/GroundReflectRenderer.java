package rtx.kimiko.utils.render.modules.post.groundreflect;

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
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import rtx.kimiko.Kimiko;
import rtx.kimiko.utils.render.others.RenderSampler;

/**
 * "Мокрый мир" (SSR-отражения).
 *
 * PERF: копия цвета нужна (отражение читает чужие пиксели), но копия глубины DEPTH32
 * каждый кадр — нет: глубина читается напрямую из depth-аттачмента, с авто-откатом на копию,
 * если драйвер не даёт. Остальное — в шейдере (ранние выходы до трассировки).
 */
public final class GroundReflectRenderer {
    @NotNull
    public static final GroundReflectRenderer INSTANCE = new GroundReflectRenderer();
    public static final int INV_VP_OFFSET = 20;
    public static final int VIEW_PROJ_OFFSET = 36;
    public static final int UNIFORM_FLOATS = 52;
    private static final int UNIFORM_SIZE = 208;
    @NotNull
    private static final Identifier PIPELINE_ID = INSTANCE.id("pipeline/post/groundreflect");
    @NotNull
    private static final Identifier SHADER = INSTANCE.id("post/groundreflect/groundreflect");
    @Nullable
    private static RenderPipeline pipeline;
    @Nullable
    private static GpuBuffer uniformBuffer;
    @Nullable
    private static GpuTexture sceneCopyTexture;
    @Nullable
    private static GpuTextureView sceneCopyTextureView;
    @Nullable
    private static GpuTexture depthCopyTexture;
    @Nullable
    private static GpuTextureView depthCopyTextureView;
    private static int sceneWidth = -1;
    private static int sceneHeight = -1;
    private static int depthWidth = -1;
    private static int depthHeight = -1;
    private static boolean disabledAfterError;
    private static boolean liveDepthFailed;
    private static boolean usingLiveDepth;

    private GroundReflectRenderer() {
    }

    public static void apply(@Nullable Framebuffer renderTarget, @NotNull float[] uniform) {
        if (disabledAfterError || renderTarget == null) {
            return;
        }
        GpuTexture colorTexture = renderTarget.getColorAttachment();
        GpuTextureView colorView = renderTarget.getColorAttachmentView();
        GpuTexture depthTexture = renderTarget.getDepthAttachment();
        int width = renderTarget.textureWidth;
        int height = renderTarget.textureHeight;
        if (colorTexture == null || colorView == null || depthTexture == null || width <= 0 || height <= 0) {
            return;
        }
        INSTANCE.init();
        RenderPipeline currentPipeline = pipeline;
        GpuBuffer currentUniform = uniformBuffer;
        if (currentPipeline == null || currentUniform == null || !INSTANCE.ensureSceneCopy(width, height)) {
            return;
        }
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(colorTexture, sceneCopyTexture, 0, 0, 0, 0, 0, width, height);
            GpuTextureView depthView = INSTANCE.resolveDepthView(renderTarget, depthTexture, encoder, width, height);
            if (depthView == null) {
                return;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(UNIFORM_SIZE);
                int n = Math.min(uniform.length, UNIFORM_FLOATS);
                for (int i = 0; i < n; ++i) {
                    data.putFloat(i * 4, uniform[i]);
                }
                data.position(0);
                encoder.writeToBuffer(currentUniform.slice(0L, UNIFORM_SIZE), data);
            }
            try (RenderPass pass = encoder.createRenderPass(() -> "kimiko:ground_reflect", colorView, OptionalInt.empty())) {
                pass.setPipeline(currentPipeline);
                pass.bindTexture("Scene", sceneCopyTextureView, RenderSampler.linear());
                pass.bindTexture("DepthSampler", depthView, RenderSampler.nearest());
                pass.setUniform("ReflectParams", currentUniform);
                pass.draw(0, 6);
            }
        } catch (Throwable throwable) {
            if (usingLiveDepth && !liveDepthFailed) {
                liveDepthFailed = true;
                return;
            }
            disabledAfterError = true;
            INSTANCE.closeTargets();
        }
    }

    @Nullable
    private GpuTextureView resolveDepthView(Framebuffer target, GpuTexture depthTexture, CommandEncoder encoder, int width, int height) {
        if (!liveDepthFailed) {
            GpuTextureView live = target.getDepthAttachmentView();
            if (live != null) {
                usingLiveDepth = true;
                return live;
            }
        }
        usingLiveDepth = false;
        if (!this.ensureDepthCopy(width, height)) {
            return null;
        }
        encoder.copyTextureToTexture(depthTexture, depthCopyTexture, 0, 0, 0, 0, 0, width, height);
        return depthCopyTextureView;
    }

    private void init() {
        if (disabledAfterError) {
            return;
        }
        try {
            if (pipeline == null) {
                pipeline = RenderPipelines.register(RenderPipeline.builder(new RenderPipeline.Snippet[0])
                        .withLocation(PIPELINE_ID)
                        .withVertexShader(SHADER)
                        .withFragmentShader(SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("ReflectParams", UniformType.UNIFORM_BUFFER)
                        .withSampler("Scene")
                        .withSampler("DepthSampler")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
            }
            GpuBuffer current = uniformBuffer;
            if (current == null || current.isClosed() || current.size() < UNIFORM_SIZE) {
                this.closeUniform();
                uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "kimiko:ground_reflect_uniforms", 136, UNIFORM_SIZE);
            }
        } catch (Throwable throwable) {
            disabledAfterError = true;
            pipeline = null;
            this.closeUniform();
        }
    }

    private boolean ensureSceneCopy(int width, int height) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }
        if (sceneCopyTexture != null && sceneWidth == width && sceneHeight == height) {
            return true;
        }
        this.closeScene();
        sceneCopyTexture = device.createTexture(() -> "kimiko:ground_reflect_scene_copy", 5, TextureFormat.RGBA8, width, height, 1, 1);
        sceneCopyTextureView = device.createTextureView(sceneCopyTexture);
        sceneWidth = width;
        sceneHeight = height;
        return true;
    }

    private boolean ensureDepthCopy(int width, int height) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }
        if (depthCopyTexture != null && depthWidth == width && depthHeight == height) {
            return true;
        }
        this.closeDepth();
        depthCopyTexture = device.createTexture(() -> "kimiko:ground_reflect_depth_copy", 5, TextureFormat.DEPTH32, width, height, 1, 1);
        depthCopyTextureView = device.createTextureView(depthCopyTexture);
        depthWidth = width;
        depthHeight = height;
        return true;
    }

    public static void clear() {
        INSTANCE.closeTargets();
    }

    private void closeTargets() {
        this.closeScene();
        this.closeDepth();
    }

    private void closeScene() {
        if (sceneCopyTextureView != null) {
            sceneCopyTextureView.close();
        }
        sceneCopyTextureView = null;
        if (sceneCopyTexture != null) {
            sceneCopyTexture.close();
        }
        sceneCopyTexture = null;
        sceneWidth = -1;
        sceneHeight = -1;
    }

    private void closeDepth() {
        if (depthCopyTextureView != null) {
            depthCopyTextureView.close();
        }
        depthCopyTextureView = null;
        if (depthCopyTexture != null) {
            depthCopyTexture.close();
        }
        depthCopyTexture = null;
        depthWidth = -1;
        depthHeight = -1;
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
