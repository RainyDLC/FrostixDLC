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
import org.joml.Vector3f;
import org.joml.Vector4f;
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
 *  - сам шейдер чёрной дыры ускорен (см. CustomskyFastShaders);
 *  - v5: composite получает NoiseTex (туманность чёрной дыры берёт шум из текстуры, а не из 8 хешей
 *    на каждый value-noise на полном разрешении). Текстура шума 256x256 создаётся один раз для всех типов неба,
 *    потому что composite общий и сэмплер должен быть привязан всегда.
 *
 * v8, чёрная дыра больше не пиксельная (FPS тот же):
 *  - раньше марш шёл в буфер 1/2 x 1/2 и растягивался на экран -> блоки 2x2, лесенка на кольце и диске;
 *  - теперь марш по-прежнему считает 1/4 пикселей за кадр (цена та же), но каждый кадр со своим
 *    субпиксельным сдвигом (4 фазы), а отдельный дешёвый resolve-проход собирает их в ПОЛНОЕ разрешение
 *    через историю с точной репроекцией (небо на бесконечности). Через 4 кадра каждый пиксель экрана
 *    посчитан честно, а не растянут;
 *  - bloom строится с той же базы 1/2, поэтому свечение выглядит как раньше и не дорожает.
 *
 * v9, PERF: пропуск марша, когда дыра вне кадра (картинка бит-в-бит та же):
 *  - шейдер марша выдаёт ровно vec4(0,0) для всех лучей с прицельным параметром >= 7.25
 *    (ветка impact >= IMPACT_CULL || along >= 0 пропускает цикл, все дальнейшие вклады — нули);
 *    impact = 14 * sin(угол(луч, BH_DIR)), т.е. ненулевой результат возможен только в конусе
 *    asin(7.25/14) ~= 31.1° вокруг BH_DIR;
 *  - на CPU раз в кадр считаем направление взгляда и макс. угол до углов экрана (через INV_VIEW_PROJ,
 *    без аллокаций — статические темпы); если угол(взгляд, BH_DIR) > угол_до_угла + 31.1° + eps,
 *    ни один луч экрана не достаёт до дыры -> пропускаем марш (самый дорогой проход: до 200 итераций
 *    реймарша), resolve и все 6 уровней bloom, а в composite подсовываем статичную чёрную 1x1 текстуру
 *    вместо Sky и Bloom0..5 (resolve/bloom нулей дали бы те же нули);
 *  - джеты, анаморфный блик, звёзды и туманность считаются в composite аналитически и от марша
 *    не зависят (кроме множителя тени, который при скипе и так равен 0, как у марша), поэтому
 *    их работа при скипе не меняется; история TAA инвалидируется, при возврате дыры в кадр
 *    resolve стартует заново без гостинга.
 *
 * Uniform SkyParams (std140):
 *  0 invViewProj | 64 misc | 80 skyColor | 96 skyColor2 | 112 taa | 128 prevViewProj
 *  taa = (фаза субпиксельного сдвига 0..3, 0, historyValid, taaSequence)
 *  192 skyExtra = (useClientColor, bhPalette, bhTemperature, bhJets)
 *  208 bhParams = (bhLensing, bhActivity, bhSpin, 0)  <- читают только blackhole/composite,
 *      остальные шейдеры объявляют блок на 208 байт, буфер больше блока — это нормально.
 */
public final class CustomSkyRenderer {
    @NotNull
    public static final CustomSkyRenderer INSTANCE = new CustomSkyRenderer();
    private static final int TYPE_COUNT = 3;
    private static final int TYPE_BLACKHOLE = 1;
    private static final int TYPE_STARFALL = 2;
    private static final int TYPE_USER = 3;
    private static final int UNIFORM_SIZE = 224;
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
    @NotNull private static final Identifier RESOLVE_SHADER = INSTANCE.id("post/customsky/blackhole_resolve");
    @NotNull private static final Identifier RESOLVE_PIPELINE_ID = INSTANCE.id("pipeline/post/customsky/blackhole_resolve");
    @NotNull private static final Identifier[] MARCH_SHADERS = new Identifier[]{
            INSTANCE.id("post/customsky/aurora"), INSTANCE.id("post/customsky/blackhole"), INSTANCE.id("post/customsky/starfall")};
    @NotNull private static final Identifier[] MARCH_PIPELINE_IDS = new Identifier[]{
            INSTANCE.id("pipeline/post/customsky/aurora"), INSTANCE.id("pipeline/post/customsky/blackhole"), INSTANCE.id("pipeline/post/customsky/starfall")};
    @NotNull private static final String[] MARCH_PASS_NAMES = new String[]{
            "kimiko:customsky_march_aurora", "kimiko:customsky_march_blackhole", "kimiko:customsky_march_starfall"};

    /** Направление на дыру в world space — та же константа, что в шейдере blackhole. */
    @NotNull private static final Vector3f BH_DIR = new Vector3f(0.34f, 0.62f, 0.71f).normalize();
    /** Угловой радиус области, где марш может дать ненулевой результат (asin(IMPACT_CULL / CAM_DIST)). */
    private static final float HOLE_ANGULAR_RADIUS = (float) Math.asin(7.25 / 14.0);
    /** Запас точности для скипа марша, радианы. */
    private static final float HOLE_SKIP_EPS = 0.01f;
    @NotNull private static final Vector4f TMP_CLIP_A = new Vector4f();
    @NotNull private static final Vector4f TMP_CLIP_B = new Vector4f();
    @NotNull private static final Vector3f TMP_VIEW_DIR = new Vector3f();
    @NotNull private static final Vector3f TMP_RAY_DIR = new Vector3f();

    @NotNull private static final Matrix4f INV_VIEW_PROJ = new Matrix4f();
    @NotNull private static final Matrix4f PREV_VIEW_PROJ = new Matrix4f();
    @NotNull private static final Matrix4f PENDING_VIEW_PROJ = new Matrix4f();

    @NotNull private static final RenderPipeline[] marchPipelines = new RenderPipeline[TYPE_COUNT];
    @Nullable private static RenderPipeline compositePipeline;
    @Nullable private static RenderPipeline bloomPipeline;
    @Nullable private static RenderPipeline resolvePipeline;
    @Nullable private static GpuBuffer uniformBuffer;
    @Nullable private static GpuBuffer bloomUniformBuffer;
    /** Полноэкранные буферы неба (для чёрной дыры это ping-pong история resolve). */
    @NotNull private static final GpuTexture[] skyTextures = new GpuTexture[2];
    @NotNull private static final GpuTextureView[] skyTextureViews = new GpuTextureView[2];
    /** Буфер марша чёрной дыры 1/2 x 1/2 (каждый кадр со своим субпиксельным сдвигом). */
    @Nullable private static GpuTexture marchHalfTexture;
    @Nullable private static GpuTextureView marchHalfTextureView;
    @Nullable private static GpuTexture noiseTexture;
    @Nullable private static GpuTextureView noiseTextureView;
    /** Статичная чёрная текстура 1x1 — подмена неба/bloom, когда дыра вне кадра (марш дал бы ровно 0). */
    @Nullable private static GpuTexture blackTexture;
    @Nullable private static GpuTextureView blackTextureView;
    @NotNull private static final GpuTexture[] bloomTextures = new GpuTexture[BLOOM_LEVELS];
    @NotNull private static final GpuTextureView[] bloomTextureViews = new GpuTextureView[BLOOM_LEVELS];
    @NotNull private static final int[] bloomWidths = new int[BLOOM_LEVELS];
    @NotNull private static final int[] bloomHeights = new int[BLOOM_LEVELS];

    private static int fullWidth = -1;
    private static int fullHeight = -1;
    /** Виртуальный размер источника первого прохода bloom (1/2 для чёрной дыры, как было раньше). */
    private static int bloomSrcWidth = -1;
    private static int bloomSrcHeight = -1;
    private static boolean currentHalfMarch;
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
        // NoiseTex нужен и composite (туманность), поэтому проверяем для всех типов
        if (marchPipeline == null || compositePipeline == null || bloomPipeline == null || uniformBuffer == null
                || bloomUniformBuffer == null || noiseTextureView == null || (hdr && resolvePipeline == null)
                || !INSTANCE.ensureTargets(renderTarget.textureWidth, renderTarget.textureHeight, hdr)
                || (hdr && marchHalfTextureView == null)) {
            return;
        }
        if (lastType != type) {
            historyValid = false;
            lastType = type;
        }
        int write = frameIndex & 1;
        int read = 1 - write;

        // настройки чёрной дыры (дефолты = то, что стоит в модуле по умолчанию)
        float bhPalette = 2.0f;
        float bhTemperature = 0.5f;
        float bhJets = 0.7f;
        float bhLensing = 1.0f;
        float bhActivity = 1.0f;
        float bhSpin = 1.0f;
        Ambience ambience = Ambience.Companion.getInstance();
        if (ambience != null) {
            bhPalette = ambience.bhPaletteIndex();
            bhTemperature = ambience.bhTemperature();
            bhJets = ambience.bhJets();
            bhLensing = ambience.bhLensing();
            bhActivity = ambience.bhActivity();
            bhSpin = ambience.bhSpin();
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            INV_VIEW_PROJ.set((Matrix4fc) viewProj).invert();
            // PERF: если дыра вне кадра, марш выдал бы ровно vec4(0,0) на каждом пикселе
            // (см. isHoleOnScreen) — пропускаем марш, resolve и bloom, подменяем чёрной 1x1 текстурой.
            // Джеты/блик/звёзды/туманность живут в composite и от марша не зависят.
            boolean holeOnScreen = !hdr || blackTextureView == null
                    || isHoleOnScreen(renderTarget.textureWidth, renderTarget.textureHeight);
            if (hdr && !holeOnScreen) {
                historyValid = false;
            }
            boolean useHistory = hdr && historyValid;
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
                // фаза субпиксельного сдвига марша чёрной дыры (0..3), resolve знает, какой пиксель свежий
                data.putFloat(112, (float) (frameIndex & 3));
                data.putFloat(116, 0.0f);
                data.putFloat(120, useHistory ? 1.0f : 0.0f);
                data.putFloat(124, taaSequence);
                PREV_VIEW_PROJ.get(128, data);
                data.putFloat(192, useClientColor ? 1.0f : 0.0f);
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
            if (holeOnScreen) {
                String marchName = type == TYPE_USER ? "kimiko:customsky_march_user" : MARCH_PASS_NAMES[type];
                GpuTextureView marchTarget = hdr ? marchHalfTextureView : skyTextureViews[write];
                try (RenderPass pass = encoder.createRenderPass(() -> marchName, marchTarget, OptionalInt.empty())) {
                    pass.setPipeline(marchPipeline);
                    pass.setUniform("SkyParams", uniformBuffer);
                    if (type != TYPE_USER) {
                        ThemeWaveUniform.bind(pass);
                    }
                    if (hdr) {
                        pass.bindTexture("NoiseTex", noiseTextureView, RenderSampler.linearRepeat());
                    }
                    pass.draw(0, 6);
                }

                if (hdr) {
                    // resolve: собираем 1/4-сэмплы этого кадра + репроецированную историю в полное разрешение
                    try (RenderPass pass = encoder.createRenderPass(() -> "kimiko:customsky_resolve_blackhole",
                            skyTextureViews[write], OptionalInt.empty())) {
                        pass.setPipeline(resolvePipeline);
                        pass.setUniform("SkyParams", uniformBuffer);
                        pass.bindTexture("Current", marchHalfTextureView, RenderSampler.linear());
                        pass.bindTexture("History", skyTextureViews[read], RenderSampler.linear());
                        pass.draw(0, 6);
                    }
                }

                INSTANCE.buildBloom(encoder, skyTextureViews[write], hdr);
                skyView = skyTextureViews[write];
            } else {
                // дыра вне кадра: resolve чёрного Current и bloom чёрного неба дали бы нули,
                // поэтому сразу подменяем статичной чёрной текстурой — composite бит-в-бит тот же.
                skyView = blackTextureView;
            }

            // Без копии сцены: composite делает discard там, где есть геометрия.
            try (RenderPass pass = encoder.createRenderPass(() -> "kimiko:customsky_composite",
                    renderTarget.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(compositePipeline);
                pass.setUniform("SkyParams", uniformBuffer);
                pass.bindTexture("DepthTex", renderTarget.getDepthAttachmentView(), RenderSampler.nearest());
                pass.bindTexture("Sky", skyView, RenderSampler.linear());
                for (int level = 0; level < BLOOM_LEVELS; ++level) {
                    pass.bindTexture("Bloom" + level, holeOnScreen ? bloomTextureViews[level] : blackTextureView,
                            RenderSampler.linear());
                }
                pass.bindTexture("NoiseTex", noiseTextureView, RenderSampler.linearRepeat());
                pass.draw(0, 6);
            }

            PREV_VIEW_PROJ.set((Matrix4fc) viewProj);
            // историю валидируем только если реально посчитали небо; при скипе дыры она уже инвалидирована выше
            if (holeOnScreen) {
                historyValid = true;
            }
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
        // для чёрной дыры источник полноэкранный, но шаг выборки первого прохода как у буфера 1/2:
        // линейный фильтр усредняет 2x2 -> bloom выглядит и стоит ровно как раньше
        int srcWidth = bloomSrcWidth;
        int srcHeight = bloomSrcHeight;
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
                    builder.withSampler("NoiseTex");
                }
                marchPipelines[type] = RenderPipelines.register(builder.build());
            }
            if (type == TYPE_BLACKHOLE && resolvePipeline == null) {
                resolvePipeline = RenderPipelines.register(RenderPipeline.builder(new RenderPipeline.Snippet[0])
                        .withLocation(RESOLVE_PIPELINE_ID)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(RESOLVE_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("SkyParams", UniformType.UNIFORM_BUFFER)
                        .withSampler("Current")
                        .withSampler("History")
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build());
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
                builder.withSampler("NoiseTex");
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
            // composite общий для всех типов и читает NoiseTex -> текстура нужна всегда (256x256, создаётся один раз)
            this.ensureNoiseTexture();
            // чёрная 1x1 для скипа марша/bloom, когда дыра вне кадра
            this.ensureBlackTexture();
        } catch (Throwable throwable) {
            disabledAfterError = true;
            for (int i = 0; i < TYPE_COUNT; ++i) {
                marchPipelines[i] = null;
            }
            compositePipeline = null;
            bloomPipeline = null;
            resolvePipeline = null;
            this.closeUniform();
            this.closeBloomUniform();
            this.closeNoise();
            this.closeBlack();
        }
    }

    /**
     * PERF: марш чёрной дыры — самый дорогой проход (до 200 итераций реймарша на half-res пиксель).
     * Шейдер выдаёт ровно vec4(0,0) для всех лучей с прицельным параметром >= 7.25
     * (ветка impact >= IMPACT_CULL || along >= 0 пропускает цикл, а дальше все вклады — нули:
     * color=0, captured=0 -> кольцо 0, bend=0 -> фотонное кольцо 0, fell=0 -> shadow 0).
     * Такие лучи — это угловое расстояние от направления взгляда до BH_DIR больше, чем
     * (макс. угол до угла экрана + asin(7.25/14)). Тогда марш, resolve и bloom можно не запускать
     * вообще, а в composite подсунуть статичную чёрную 1x1 текстуру — результат бит-в-бит тот же.
     * Джеты/блик/звёзды/туманность считаются в composite аналитически и от марша не зависят.
     */
    private static boolean isHoleOnScreen(int width, int height) {
        rayDirNdc(0.0f, 0.0f, TMP_VIEW_DIR);
        // расширяем NDC-рамку на 2 пикселя: сэмпл марша может брать луч со сдвигом до ~1.5px от центра пикселя
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

    /** Направление луча в world space для NDC-координат (тот же rayDir, что в шейдерах). */
    private static void rayDirNdc(float ndcX, float ndcY, Vector3f dest) {
        TMP_CLIP_A.set(ndcX, ndcY, 1.0f, 1.0f);
        INV_VIEW_PROJ.transform(TMP_CLIP_A);
        TMP_CLIP_A.div(TMP_CLIP_A.w);
        TMP_CLIP_B.set(ndcX, ndcY, -1.0f, 1.0f);
        INV_VIEW_PROJ.transform(TMP_CLIP_B);
        TMP_CLIP_B.div(TMP_CLIP_B.w);
        dest.set(TMP_CLIP_A.x - TMP_CLIP_B.x, TMP_CLIP_A.y - TMP_CLIP_B.y, TMP_CLIP_A.z - TMP_CLIP_B.z).normalize();
    }

    private void ensureBlackTexture() {
        GpuTexture current = blackTexture;
        if (current != null && !current.isClosed() && blackTextureView != null) {
            return;
        }
        this.closeBlack();
        GpuDevice device = RenderSystem.getDevice();
        NativeImage image = new NativeImage(1, 1, false);
        image.setColor(0, 0, 0xFF000000);
        blackTexture = device.createTexture(() -> "kimiko:customsky_black", 5, TextureFormat.RGBA8, 1, 1, 1, 1);
        device.createCommandEncoder().writeToTexture(blackTexture, image);
        blackTextureView = device.createTextureView(blackTexture);
        image.close();
    }

    private void closeBlack() {
        if (blackTextureView != null) {
            blackTextureView.close();
        }
        blackTextureView = null;
        if (blackTexture != null) {
            blackTexture.close();
        }
        blackTexture = null;
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

    private boolean ensureTargets(int width, int height, boolean halfMarch) {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }
        if (skyTextures[0] != null && skyTextures[1] != null && fullWidth == width && fullHeight == height
                && currentHalfMarch == halfMarch && (!halfMarch || marchHalfTexture != null)) {
            return true;
        }
        this.closeTargets();
        // небо (и история чёрной дыры) всегда в полном разрешении
        for (int i = 0; i < 2; ++i) {
            final int index = i;
            skyTextures[i] = device.createTexture(() -> "kimiko:customsky_sky" + index, 12, TextureFormat.RGBA8, width, height, 1, 1);
            skyTextureViews[i] = device.createTextureView(skyTextures[i]);
        }
        if (halfMarch) {
            // округление вверх, чтобы 4 фазы сдвига покрывали и последний столбец/строку при нечётном размере
            int hw = Math.max(1, (width + 1) / 2);
            int hh = Math.max(1, (height + 1) / 2);
            marchHalfTexture = device.createTexture(() -> "kimiko:customsky_march_half", 12, TextureFormat.RGBA8, hw, hh, 1, 1);
            marchHalfTextureView = device.createTextureView(marchHalfTexture);
            bloomSrcWidth = Math.max(1, width / 2);
            bloomSrcHeight = Math.max(1, height / 2);
        } else {
            bloomSrcWidth = width;
            bloomSrcHeight = height;
        }
        int bw = bloomSrcWidth;
        int bh = bloomSrcHeight;
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
        currentHalfMarch = halfMarch;
        historyValid = false;
        return true;
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
        if (marchHalfTextureView != null) {
            marchHalfTextureView.close();
        }
        marchHalfTextureView = null;
        if (marchHalfTexture != null) {
            marchHalfTexture.close();
        }
        marchHalfTexture = null;
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
        bloomSrcWidth = -1;
        bloomSrcHeight = -1;
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
