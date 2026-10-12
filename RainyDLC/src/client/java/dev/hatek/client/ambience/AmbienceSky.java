package dev.hatek.client.ambience;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.hatek.client.module.impl.render.Ambience;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Рендер неба Ambience в sky-pass. Текстуры генерируются процедурно
 * в фоне (см. {@link SkyGen}, {@link BlackHoleGen}), здесь — только
 * загрузка на GPU и отрисовка ванильными пайплайнами неба.
 */
public final class AmbienceSky {
    private static final int BASE_W = 2048;
    private static final int BASE_H = 1024;
    private static final int PLASMA_W = 1024;
    private static final int PLASMA_H = 512;
    private static final int STAR_COUNT = 6500;
    private static final long STAR_SEED = 1337L;
    private static final float SKY_R = 100.0f;
    private static final float STAR_R = 90.0f;
    private static final float BH_R = 80.0f;

    private static final float[][] FOG = {
            {0.06f, 0.025f, 0.09f},
            {0.018f, 0.022f, 0.045f},
            {0.022f, 0.027f, 0.05f},
            {0.012f, 0.014f, 0.024f},
            {0.02f, 0.03f, 0.05f}
    };
    private static final int ATMO_W = 512;
    private static final int ATMO_H = 256;
    private static final int AURORA_W = 1024;
    private static final int AURORA_H = 512;

    private static final ExecutorService GEN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RainyDLC-Ambience");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    // ---- фоновые результаты
    private static volatile int requestedMode = -1;
    private static volatile int[] pendingBase;
    private static volatile int pendingBaseMode = -1;
    private static volatile int[][] pendingPlasma;
    private static volatile boolean plasmaRequested;
    private static volatile BlackHoleGen blackHole;
    private static volatile int[] bhRawBase;
    private static volatile boolean bhBuilding;
    private static volatile int[] pendingAtmo;
    private static volatile int pendingBaseKey;
    private static volatile int appliedKey = Integer.MIN_VALUE;
    private static volatile boolean postOn;
    private static volatile double postBloom;
    private static volatile double postSat = 1.0;
    private static int seenKey = Integer.MIN_VALUE;
    private static long seenKeyAt;
    private static int baseKey = Integer.MIN_VALUE;
    private static volatile boolean atmoBusy;
    private static volatile int[][] pendingAurora;
    private static volatile boolean auroraRequested;
    private static volatile float sunAngle;
    private static volatile float moonAngle = (float) Math.PI;
    private static volatile float starAngle;
    private static volatile float rain;
    private static float atmoSunAngle = Float.NaN;
    private static float atmoRain = -1;
    private static volatile Thread bhThread;
    private static volatile long bhWantedAt;
    private static volatile double animTime;
    private static volatile double animBrightness = 1.0;
    private static final AtomicReference<int[]> BH_READY = new AtomicReference<>();
    private static final AtomicReference<int[]> BH_FREE = new AtomicReference<>();

    // ---- GPU-ресурсы (только render thread)
    private static DynamicTexture baseTex;
    private static int baseMode = -1;
    private static DynamicTexture plasmaA;
    private static DynamicTexture plasmaB;
    private static DynamicTexture starTex;
    private static DynamicTexture spikeTex;
    private static DynamicTexture whiteTex;
    private static DynamicTexture bhTex;
    private static DynamicTexture atmoTex;
    private static DynamicTexture auroraA;
    private static DynamicTexture auroraB;
    private static DynamicTexture sunTex;
    private static GpuBuffer sunQuad;
    private static DynamicTexture streakTex;
    private static GpuBuffer bhStreak;
    private static GpuBuffer sunStreak;
    private static boolean bhHasFrame;
    private static GpuBuffer sphereTexColor;
    private static GpuBuffer sphereTex;
    private static int sphereQuads;
    private static GpuBuffer starsA;
    private static int starsAQuads;
    private static GpuBuffer starsB;
    private static int starsBQuads;
    private static GpuBuffer starsBright;
    private static int starsBrightQuads;
    private static GpuBuffer horizon;
    private static int horizonQuads;
    private static GpuBuffer bhQuad;

    private static long lastNanos;
    private static double time;
    private static float fogR = 0.02f;
    private static float fogG = 0.025f;
    private static float fogB = 0.045f;

    private AmbienceSky() {
    }

    // ------------------------------------------------------------ fog

    /** Состояние неба из ванили: углы солнца/луны/звёзд и дождь. */
    public static void capture(float sun, float moon, float stars, float rainBrightness) {
        sunAngle = sun;
        moonAngle = moon;
        starAngle = stars;
        rain = Math.max(0.0f, Math.min(1.0f, 1.0f - rainBrightness));
    }

    private static double[] sunDir() {
        return new double[]{-Math.sin(sunAngle), Math.cos(sunAngle), 0};
    }

    private static double[] moonDir() {
        return new double[]{-Math.sin(moonAngle), Math.cos(moonAngle), 0};
    }

    public static float[] fogColor(float fx, float fz) {
        int mode = Ambience.mode();
        if (mode == SkyGen.MODE_SHADER) {
            double[] s = sunDir();
            double[] m = moonDir();
            float[] c = Atmosphere.fog(fx, fz, s[0], s[1], s[2], m[0], m[1], m[2], rain);
            float night = (float) Noise.smooth(0.05, -0.2, s[1]);
            float b = (float) Math.min(1.5, Ambience.brightness());
            return new float[]{Math.max(c[0], 0.012f * night) * b, Math.max(c[1], 0.016f * night) * b,
                    Math.max(c[2], 0.03f * night) * b};
        }
        float[] c = FOG[Math.max(0, Math.min(FOG.length - 1, mode))];
        float b = (float) Math.min(1.5, Ambience.brightness());
        return new float[]{c[0] * b, c[1] * b, c[2] * b};
    }

    public static void lastFog(float r, float g, float b) {
        fogR = r;
        fogG = g;
        fogB = b;
    }

    // ------------------------------------------------------------ lifecycle

    /** Освобождаем тяжёлые CPU-данные при выключении модуля. */
    public static void release() {
        blackHole = null;
        bhRawBase = null;
        requestedMode = -1;
        pendingBase = null;
        pendingPlasma = null;
        plasmaRequested = false;
        pendingAurora = null;
        auroraRequested = false;
        pendingAtmo = null;
        atmoSunAngle = Float.NaN;
        BH_READY.set(null);
        BH_FREE.set(null);
    }

    private static void post(int[] px, int w, int h, boolean wrap, boolean opaque) {
        if (postOn) {
            SkyPost.apply(px, w, h, wrap, opaque, postBloom, postSat);
        }
    }

    /** Смена параметров Sky Shader → пересчёт текстур (с дебаунсом 0.5 с). */
    private static void updatePostKey() {
        int key = Ambience.postKey();
        long now = System.currentTimeMillis();
        if (key != seenKey) {
            seenKey = key;
            seenKeyAt = now;
        }
        if (key != appliedKey && (appliedKey == Integer.MIN_VALUE || now - seenKeyAt > 500)) {
            boolean first = appliedKey == Integer.MIN_VALUE;
            appliedKey = key;
            postOn = key != -1;
            postBloom = Ambience.bloom();
            postSat = Ambience.saturation();
            if (!first) {
                requestedMode = -1;
                plasmaRequested = false;
                auroraRequested = false;
                atmoSunAngle = Float.NaN;
            }
        }
    }

    private static void request(int mode) {
        if (requestedMode == mode) {
            return;
        }
        requestedMode = mode;
        if (mode == SkyGen.MODE_PLASMA && !plasmaRequested) {
            plasmaRequested = true;
            GEN.submit(() -> {
                int[] a = SkyGen.plasma(0, PLASMA_W, PLASMA_H);
                int[] b = SkyGen.plasma(1, PLASMA_W, PLASMA_H);
                post(a, PLASMA_W, PLASMA_H, true, false);
                post(b, PLASMA_W, PLASMA_H, true, false);
                pendingPlasma = new int[][]{a, b};
            });
        }
        if (mode == SkyGen.MODE_SHADER && !auroraRequested) {
            auroraRequested = true;
            GEN.submit(() -> {
                int[] a = Atmosphere.aurora(0, AURORA_W, AURORA_H);
                int[] b = Atmosphere.aurora(1, AURORA_W, AURORA_H);
                post(a, AURORA_W, AURORA_H, true, false);
                post(b, AURORA_W, AURORA_H, true, false);
                pendingAurora = new int[][]{a, b};
            });
        }
        if (baseMode == mode && baseKey == appliedKey && (mode != SkyGen.MODE_BLACK_HOLE || blackHole != null)) {
            return;
        }
        GEN.submit(() -> {
            if (requestedMode != mode) {
                return;
            }
            int key = appliedKey;
            int[] base = SkyGen.base(mode, BASE_W, BASE_H);
            if (requestedMode != mode) {
                return;
            }
            int[] shown = base;
            if (postOn) {
                shown = base.clone();
                post(shown, BASE_W, BASE_H, true, true);
            }
            pendingBaseKey = key;
            pendingBase = shown;
            pendingBaseMode = mode;
            if (mode == SkyGen.MODE_BLACK_HOLE) {
                bhRawBase = base;
                if (blackHole == null) {
                    buildBlackHole(base, Ambience.blackHoleSize());
                }
            }
        });
    }

    private static void buildBlackHole(int[] base, int size) {
        bhBuilding = true;
        try {
            float[] map = SkyGen.starMap(SkyGen.stars(STAR_COUNT, STAR_SEED), BASE_W, BASE_H);
            blackHole = BlackHoleGen.build(size, base, BASE_W, BASE_H, map);
        } finally {
            bhBuilding = false;
        }
    }

    /** Смена качества чёрной дыры → пересборка в фоне (старая показывается до готовности). */
    private static void checkBlackHoleQuality() {
        BlackHoleGen gen = blackHole;
        int[] raw = bhRawBase;
        int want = Ambience.blackHoleSize();
        if (gen != null && gen.size != want && raw != null && !bhBuilding) {
            bhBuilding = true;
            GEN.submit(() -> buildBlackHole(raw, want));
        }
    }

    private static void requestAtmosphere() {
        float sa = sunAngle;
        float r = rain;
        boolean stale = Float.isNaN(atmoSunAngle) || Math.abs(sa - atmoSunAngle) > 0.002f
                || Math.abs(r - atmoRain) > 0.02f;
        if (!stale || atmoBusy) {
            return;
        }
        atmoBusy = true;
        atmoSunAngle = sa;
        atmoRain = r;
        double[] s = sunDir();
        double[] m = moonDir();
        GEN.submit(() -> {
            try {
                int[] out = new int[ATMO_W * ATMO_H];
                Atmosphere.render(ATMO_W, ATMO_H, s[0], s[1], s[2], m[0], m[1], m[2], r, out);
                post(out, ATMO_W, ATMO_H, true, true);
                pendingAtmo = out;
            } finally {
                atmoBusy = false;
            }
        });
    }

    private static void renderShader(RenderTarget target, Matrix4f view, float bright,
                                     GpuSampler repeat, GpuSampler clamp) {
        requestAtmosphere();
        double[] s = sunDir();
        float night = (float) Noise.smooth(0.05, -0.2, s[1]);
        float t = (float) time;
        // Ночное небо вращается вместе с ванильными звёздами.
        Matrix4f stars = new Matrix4f(view).rotateY((float) Math.toRadians(-90)).rotateX(starAngle);
        float clear = 1.0f - rain;
        if (baseTex != null && baseMode == SkyGen.MODE_SHADER) {
            float nb = bright * night;
            draw(target, RenderPipelines.END_SKY, stars, new Vector4f(nb, nb, nb, 1.0f),
                    baseTex, repeat, sphereTexColor, sphereQuads);
        }
        if (Ambience.showStars() && starTex != null && night > 0.01f) {
            float a = night * clear;
            float sb = Math.min(1.0f, bright);
            draw(target, RenderPipelines.END_SKY, stars, new Vector4f(sb, sb, sb, a * (0.85f + 0.15f * (float) Math.sin(t * 1.3))),
                    starTex, clamp, starsA, starsAQuads);
            draw(target, RenderPipelines.END_SKY, stars, new Vector4f(sb, sb, sb, a * (0.85f + 0.15f * (float) Math.cos(t * 1.7))),
                    starTex, clamp, starsB, starsBQuads);
            draw(target, RenderPipelines.END_SKY, stars, new Vector4f(sb, sb, sb, a), spikeTex, clamp,
                    starsBright, starsBrightQuads);
        }
        if (auroraA != null && night > 0.01f) {
            float a1 = night * clear * (0.6f + 0.25f * (float) Math.sin(t * 0.35));
            float a2 = night * clear * (0.45f + 0.25f * (float) Math.sin(t * 0.27 + 1.7));
            draw(target, RenderPipelines.CELESTIAL, new Matrix4f(view).rotateY(t * 0.012f),
                    new Vector4f(bright, bright, bright, a1), auroraA, repeat, sphereTex, sphereQuads);
            draw(target, RenderPipelines.CELESTIAL, new Matrix4f(view).rotateY(-t * 0.008f + 0.6f),
                    new Vector4f(bright, bright, bright, a2), auroraB, repeat, sphereTex, sphereQuads);
        }
        if (atmoTex != null) {
            draw(target, RenderPipelines.CELESTIAL, view, new Vector4f(bright, bright, bright, 1.0f),
                    atmoTex, repeat, sphereTex, sphereQuads);
        }
        if (sunTex != null) {
            double[] tr = new double[3];
            Atmosphere.sunTransmittance(s[1], tr);
            float k = 1.6f * clear + 0.2f;
            Matrix4f sun = new Matrix4f(view).rotateY((float) Math.toRadians(-90)).rotateX(sunAngle);
            draw(target, RenderPipelines.CELESTIAL, sun,
                    new Vector4f((float) Math.min(1, tr[0] * k), (float) Math.min(1, tr[1] * k),
                            (float) Math.min(1, tr[2] * k), 1.0f), sunTex, clamp, sunQuad, 1);
            if (Ambience.lensFlare() && s[1] > -0.05) {
                float fa = (float) Math.min(1.0, 0.55 * Ambience.bloom()) * clear;
                draw(target, RenderPipelines.CELESTIAL, sun,
                        new Vector4f((float) Math.min(1, tr[0] * k), (float) Math.min(1, tr[1] * k),
                                (float) Math.min(1, tr[2] * k), fa), streakTex, clamp, sunStreak, 1);
            }
        }
    }

    private static void ensureBhThread() {
        bhWantedAt = System.currentTimeMillis();
        if (bhThread != null) {
            return;
        }
        Thread t = new Thread(AmbienceSky::bhLoop, "RainyDLC-BlackHole");
        t.setDaemon(true);
        bhThread = t;
        t.start();
    }

    private static void bhLoop() {
        try {
            while (System.currentTimeMillis() - bhWantedAt < 3000) {
                long start = System.nanoTime();
                BlackHoleGen gen = blackHole;
                if (gen != null) {
                    int[] buf = BH_FREE.getAndSet(null);
                    if (buf == null || buf.length != gen.size * gen.size) {
                        buf = new int[gen.size * gen.size];
                    }
                    gen.frame(animTime, animBrightness, buf);
                    post(buf, gen.size, gen.size, false, false);
                    int[] old = BH_READY.getAndSet(buf);
                    if (old != null) {
                        BH_FREE.set(old);
                    }
                }
                long spent = (System.nanoTime() - start) / 1_000_000L;
                Thread.sleep(Math.max(4, 33 - spent));
            }
        } catch (InterruptedException ignored) {
        } finally {
            bhThread = null;
        }
    }

    // ------------------------------------------------------------ render

    public static void render(RenderTarget target) {
        int mode = Ambience.mode();
        updatePostKey();
        request(mode);

        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        time += dt * Ambience.speed();
        animTime = time;
        float bright = (float) Ambience.brightness();
        animBrightness = bright;

        uploadPending(mode);
        ensureGeometry();

        Matrix4f view = RenderSystem.getModelViewMatrixCopy();
        Matrix4f sky = new Matrix4f(view).rotate((float) (time * 0.004), 0.28734788f, 0.95782626f, 0.0f);

        GpuSampler repeat = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        if (mode == SkyGen.MODE_SHADER) {
            renderShader(target, view, bright, repeat, clamp);
            draw(target, RenderPipelines.END_SKY, view, new Vector4f(fogR, fogG, fogB, 1.0f), whiteTex, clamp,
                    horizon, horizonQuads);
            return;
        }

        // 1. Опорный фон.
        if (baseTex != null && baseMode == mode) {
            draw(target, RenderPipelines.END_SKY, sky, new Vector4f(bright, bright, bright, 1.0f),
                    baseTex, repeat, sphereTexColor, sphereQuads);
        }

        // 2. Плазма: аддитивные слои, вращающиеся в разные стороны.
        if (mode == SkyGen.MODE_PLASMA && plasmaA != null) {
            float t = (float) time;
            Matrix4f l1 = new Matrix4f(sky).rotate(t * 0.030f, 0.0f, 1.0f, 0.0f);
            Matrix4f l2 = new Matrix4f(sky).rotate(-t * 0.022f, 0.6f, 0.0f, 0.8f);
            Matrix4f l3 = new Matrix4f(sky).rotate(t * 0.017f, 0.0f, 0.70710677f, 0.70710677f);
            float p1 = 0.55f + 0.25f * (float) Math.sin(t * 0.40);
            float p2 = 0.45f + 0.25f * (float) Math.sin(t * 0.31 + 2.0);
            float p3 = 0.30f + 0.15f * (float) Math.sin(t * 0.53 + 4.0);
            draw(target, RenderPipelines.CELESTIAL, l1, new Vector4f(bright, bright, bright, p1),
                    plasmaA, repeat, sphereTex, sphereQuads);
            draw(target, RenderPipelines.CELESTIAL, l2, new Vector4f(bright, bright, bright, p2),
                    plasmaB, repeat, sphereTex, sphereQuads);
            draw(target, RenderPipelines.CELESTIAL, l3, new Vector4f(bright, bright, bright, p3),
                    plasmaA, repeat, sphereTex, sphereQuads);
        }

        // 3. Звёзды (две группы мерцают в противофазе).
        if (Ambience.showStars() && starTex != null) {
            float t = (float) time;
            float sb = Math.min(1.0f, bright);
            float a1 = 0.85f + 0.15f * (float) Math.sin(t * 1.3);
            float a2 = 0.85f + 0.15f * (float) Math.cos(t * 1.7);
            draw(target, RenderPipelines.END_SKY, sky, new Vector4f(sb, sb, sb, a1), starTex, clamp, starsA, starsAQuads);
            draw(target, RenderPipelines.END_SKY, sky, new Vector4f(sb, sb, sb, a2), starTex, clamp, starsB, starsBQuads);
            draw(target, RenderPipelines.END_SKY, sky, new Vector4f(sb, sb, sb, 1.0f), spikeTex, clamp,
                    starsBright, starsBrightQuads);
        }

        // 4. Чёрная дыра.
        if (mode == SkyGen.MODE_BLACK_HOLE) {
            checkBlackHoleQuality();
            ensureBhThread();
            if (bhTex != null && bhHasFrame) {
                draw(target, RenderPipelines.END_SKY, sky, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), bhTex, clamp, bhQuad, 1);
                if (Ambience.lensFlare()) {
                    float fa = (float) Math.min(1.0, 0.45 * Ambience.bloom());
                    draw(target, RenderPipelines.CELESTIAL, sky, new Vector4f(0.62f, 0.78f, 1.0f, fa),
                            streakTex, clamp, bhStreak, 1);
                }
            }
        }

        // 5. Дымка у горизонта под цвет тумана — мягкий стык с ландшафтом.
        draw(target, RenderPipelines.END_SKY, view, new Vector4f(fogR, fogG, fogB, 1.0f), whiteTex, clamp, horizon, horizonQuads);
    }

    private static void draw(RenderTarget target, RenderPipeline pipeline, Matrix4f modelView, Vector4f color,
                             DynamicTexture tex, GpuSampler sampler, GpuBuffer vertices, int quads) {
        if (tex == null || vertices == null || quads <= 0) {
            return;
        }
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int count = quads * 6;
        GpuBuffer indexBuffer = indices.getBuffer(count);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), color);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RainyDLC ambience", target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.bindTexture("Sampler0", tex.getTextureView(), sampler);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(count, 1, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ textures

    private static void uploadPending(int mode) {
        int[] base = pendingBase;
        if (base != null && pendingBaseMode == mode) {
            pendingBase = null;
            baseTex = replace(baseTex, "ambience_base", base, BASE_W, BASE_H);
            baseMode = mode;
            baseKey = pendingBaseKey;
        }
        int[][] plasma = pendingPlasma;
        if (plasma != null) {
            pendingPlasma = null;
            plasmaA = replace(plasmaA, "ambience_plasma_a", plasma[0], PLASMA_W, PLASMA_H);
            plasmaB = replace(plasmaB, "ambience_plasma_b", plasma[1], PLASMA_W, PLASMA_H);
        }
        int[] atmo = pendingAtmo;
        if (atmo != null) {
            pendingAtmo = null;
            if (atmoTex == null) {
                atmoTex = replace(null, "ambience_atmosphere", atmo, ATMO_W, ATMO_H);
            } else {
                write(atmoTex.getPixels(), atmo);
                atmoTex.upload();
            }
        }
        int[][] aurora = pendingAurora;
        if (aurora != null) {
            pendingAurora = null;
            auroraA = replace(auroraA, "ambience_aurora_a", aurora[0], AURORA_W, AURORA_H);
            auroraB = replace(auroraB, "ambience_aurora_b", aurora[1], AURORA_W, AURORA_H);
        }
        if (sunTex == null) {
            sunTex = replace(null, "ambience_sun", Atmosphere.sunSprite(256), 256, 256);
            streakTex = replace(null, "ambience_streak", SkyPost.streakSprite(512, 64), 512, 64);
        }
        if (starTex == null) {
            starTex = replace(null, "ambience_star", SkyGen.starSprite(64, false), 64, 64);
            spikeTex = replace(null, "ambience_star_spikes", SkyGen.starSprite(128, true), 128, 128);
            whiteTex = replace(null, "ambience_white", new int[]{-1, -1, -1, -1}, 2, 2);
        }
        int[] frame = BH_READY.getAndSet(null);
        if (frame != null) {
            if (bhTex == null || bhTex.getPixels() == null || bhTex.getPixels().getWidth() * bhTex.getPixels().getHeight() != frame.length) {
                int side = (int) Math.round(Math.sqrt(frame.length));
                bhTex = replace(bhTex, "ambience_black_hole", frame, side, side);
            } else {
                write(bhTex.getPixels(), frame);
                bhTex.upload();
            }
            bhHasFrame = true;
            BH_FREE.set(frame);
        }
    }

    private static DynamicTexture replace(DynamicTexture old, String name, int[] argb, int w, int h) {
        if (old != null) {
            old.close();
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        write(image, argb);
        DynamicTexture tex = new DynamicTexture(() -> "hatek/" + name, image);
        tex.upload();
        return tex;
    }

    /** ARGB → RGBA-память NativeImage (little-endian ABGR int). */
    private static void write(NativeImage image, int[] argb) {
        long ptr = image.getPointer();
        int n = Math.min(argb.length, image.getWidth() * image.getHeight());
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            int abgr = (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
            MemoryUtil.memPutInt(ptr + (long) i * 4L, abgr);
        }
    }

    // ------------------------------------------------------------ geometry

    private static void ensureGeometry() {
        if (sphereTexColor != null) {
            return;
        }
        int lon = 96;
        int lat = 48;
        sphereQuads = lon * lat;
        sphereTexColor = buildSphere(DefaultVertexFormat.POSITION_TEX_COLOR, lon, lat, true);
        sphereTex = buildSphere(DefaultVertexFormat.POSITION_TEX, lon, lat, false);

        SkyGen.Star[] stars = SkyGen.stars(STAR_COUNT, STAR_SEED);
        int na = 0;
        int nb = 0;
        int nbright = 0;
        for (int i = 0; i < stars.length; i++) {
            if (stars[i].bright) {
                nbright++;
            } else if ((i & 1) == 0) {
                na++;
            } else {
                nb++;
            }
        }
        starsAQuads = na;
        starsBQuads = nb;
        starsBrightQuads = nbright;
        starsA = buildStars(stars, na, 0);
        starsB = buildStars(stars, nb, 1);
        starsBright = buildStars(stars, nbright, 2);
        buildHorizon();
        buildBlackHoleQuad();
        // Солнце в локальной системе ванили: над головой (0, +Y, 0).
        float h = 13.0f;
        sunQuad = build("ambience sun", DefaultVertexFormat.POSITION_TEX, 1, false,
                q -> q.quad(new float[]{-h, 80, -h}, new float[]{h, 80, -h}, new float[]{h, 80, h}, new float[]{-h, 80, h},
                        0, 0, 1, 0, 1, 1, 0, 1, -1, -1, -1, -1));
        // Анаморфные блики: у солнца — вдоль горизонта, у чёрной дыры — по базису билборда.
        float sw = 42.0f;
        float sh = 2.6f;
        sunStreak = build("ambience sun streak", DefaultVertexFormat.POSITION_TEX, 1, false,
                q -> q.quad(new float[]{-sw, 79, -sh}, new float[]{sw, 79, -sh}, new float[]{sw, 79, sh}, new float[]{-sw, 79, sh},
                        0, 0, 1, 0, 1, 1, 0, 1, -1, -1, -1, -1));
        double[] d = BlackHoleGen.DIR;
        double[] r = BlackHoleGen.RIGHT;
        double[] u = BlackHoleGen.UP;
        double bw = BH_R * BlackHoleGen.TAN_HALF * 1.5;
        double bh = BH_R * BlackHoleGen.TAN_HALF * 0.09;
        float dist = BH_R - 2.0f;
        float[] a0 = streakCorner(d, r, u, dist, -bw, bh);
        float[] a1 = streakCorner(d, r, u, dist, bw, bh);
        float[] a2 = streakCorner(d, r, u, dist, bw, -bh);
        float[] a3 = streakCorner(d, r, u, dist, -bw, -bh);
        bhStreak = build("ambience bh streak", DefaultVertexFormat.POSITION_TEX, 1, false,
                q -> q.quad(a0, a1, a2, a3, 0, 0, 1, 0, 1, 1, 0, 1, -1, -1, -1, -1));
    }

    private static float[] streakCorner(double[] d, double[] r, double[] u, float dist, double x, double y) {
        return new float[]{
                (float) (d[0] * dist + r[0] * x + u[0] * y),
                (float) (d[1] * dist + r[1] * x + u[1] * y),
                (float) (d[2] * dist + r[2] * x + u[2] * y)};
    }

    private interface Emit {
        void quads(Quads q);
    }

    /** Помощник: квады всегда обращены лицом к центру сферы. */
    private static final class Quads {
        final BufferBuilder builder;
        final boolean color;

        Quads(BufferBuilder builder, boolean color) {
            this.builder = builder;
            this.color = color;
        }

        void quad(float[] p0, float[] p1, float[] p2, float[] p3,
                  float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                  int c0, int c1, int c2, int c3) {
            float ax = p1[0] - p0[0], ay = p1[1] - p0[1], az = p1[2] - p0[2];
            float bx = p2[0] - p0[0], by = p2[1] - p0[1], bz = p2[2] - p0[2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float cx = p0[0] + p1[0] + p2[0] + p3[0];
            float cy = p0[1] + p1[1] + p2[1] + p3[1];
            float cz = p0[2] + p1[2] + p2[2] + p3[2];
            if (nx * cx + ny * cy + nz * cz > 0) {
                vertex(p0, u0, v0, c0);
                vertex(p3, u3, v3, c3);
                vertex(p2, u2, v2, c2);
                vertex(p1, u1, v1, c1);
            } else {
                vertex(p0, u0, v0, c0);
                vertex(p1, u1, v1, c1);
                vertex(p2, u2, v2, c2);
                vertex(p3, u3, v3, c3);
            }
        }

        private void vertex(float[] p, float u, float v, int c) {
            if (this.color) {
                this.builder.addVertex(p[0], p[1], p[2]).setUv(u, v).setColor(c);
            } else {
                this.builder.addVertex(p[0], p[1], p[2]).setUv(u, v);
            }
        }
    }

    private static GpuBuffer build(String name, VertexFormat format, int quads, boolean color, Emit emit) {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(Math.max(1, quads) * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            emit.quads(new Quads(builder, color));
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "RainyDLC " + name, GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            }
        }
    }

    private static float[] sph(double lon, double lat, float r) {
        double c = Math.cos(lat);
        return new float[]{(float) (c * Math.sin(lon) * r), (float) (Math.sin(lat) * r), (float) (c * Math.cos(lon) * r)};
    }

    private static GpuBuffer buildSphere(VertexFormat format, int lonSeg, int latSeg, boolean color) {
        return build("ambience sphere", format, lonSeg * latSeg, color, q -> {
            for (int j = 0; j < latSeg; j++) {
                double la0 = Math.PI / 2 - Math.PI * j / latSeg;
                double la1 = Math.PI / 2 - Math.PI * (j + 1) / latSeg;
                float v0 = j / (float) latSeg;
                float v1 = (j + 1) / (float) latSeg;
                for (int i = 0; i < lonSeg; i++) {
                    double lo0 = Math.PI * 2 * i / lonSeg - Math.PI;
                    double lo1 = Math.PI * 2 * (i + 1) / lonSeg - Math.PI;
                    float u0 = i / (float) lonSeg;
                    float u1 = (i + 1) / (float) lonSeg;
                    q.quad(sph(lo0, la0, SKY_R), sph(lo1, la0, SKY_R), sph(lo1, la1, SKY_R), sph(lo0, la1, SKY_R),
                            u0, v0, u1, v0, u1, v1, u0, v1, -1, -1, -1, -1);
                }
            }
        });
    }

    private static GpuBuffer buildStars(SkyGen.Star[] stars, int count, int group) {
        return build("ambience stars", DefaultVertexFormat.POSITION_TEX_COLOR, count, true, q -> {
            for (int i = 0; i < stars.length; i++) {
                SkyGen.Star s = stars[i];
                int g = s.bright ? 2 : (i & 1);
                if (g != group) {
                    continue;
                }
                float dx = s.x, dy = s.y, dz = s.z;
                float ax = Math.abs(dy) < 0.95f ? 0 : 1;
                float ay = Math.abs(dy) < 0.95f ? 1 : 0;
                // u = d × a, v = d × u
                float ux = dy * 0 - dz * ay, uy = dz * ax - dx * 0, uz = dx * ay - dy * ax;
                float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                ux /= ul; uy /= ul; uz /= ul;
                float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
                float half = s.bright ? 0.9f + s.size * 1.1f : 0.13f + s.size * 0.5f;
                float cx = dx * STAR_R, cy = dy * STAR_R, cz = dz * STAR_R;
                float[] p0 = {cx - ux * half - vx * half, cy - uy * half - vy * half, cz - uz * half - vz * half};
                float[] p1 = {cx + ux * half - vx * half, cy + uy * half - vy * half, cz + uz * half - vz * half};
                float[] p2 = {cx + ux * half + vx * half, cy + uy * half + vy * half, cz + uz * half + vz * half};
                float[] p3 = {cx - ux * half + vx * half, cy - uy * half + vy * half, cz - uz * half + vz * half};
                int c = (int) (s.a * 255) << 24 | (int) (s.r * 255) << 16 | (int) (s.g * 255) << 8 | (int) (s.b * 255);
                q.quad(p0, p1, p2, p3, 0, 0, 1, 0, 1, 1, 0, 1, c, c, c, c);
            }
        });
    }

    private static void buildHorizon() {
        double[] lats = {-90, -12, -2, 4, 10, 18, 28};
        int[] alpha = {255, 255, 245, 190, 110, 40, 0};
        int seg = 64;
        horizonQuads = seg * (lats.length - 1);
        horizon = build("ambience horizon", DefaultVertexFormat.POSITION_TEX_COLOR, horizonQuads, true, q -> {
            for (int j = 0; j < lats.length - 1; j++) {
                double la0 = Math.toRadians(lats[j]);
                double la1 = Math.toRadians(lats[j + 1]);
                int c0 = alpha[j] << 24 | 0xFFFFFF;
                int c1 = alpha[j + 1] << 24 | 0xFFFFFF;
                for (int i = 0; i < seg; i++) {
                    double lo0 = Math.PI * 2 * i / seg;
                    double lo1 = Math.PI * 2 * (i + 1) / seg;
                    q.quad(sph(lo0, la0, 85), sph(lo1, la0, 85), sph(lo1, la1, 85), sph(lo0, la1, 85),
                            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, c0, c0, c1, c1);
                }
            }
        });
    }

    private static void buildBlackHoleQuad() {
        double[] d = BlackHoleGen.DIR;
        double[] r = BlackHoleGen.RIGHT;
        double[] u = BlackHoleGen.UP;
        double s = BH_R * BlackHoleGen.TAN_HALF;
        float[] tl = corner(d, r, u, -s, s);
        float[] tr = corner(d, r, u, s, s);
        float[] br = corner(d, r, u, s, -s);
        float[] bl = corner(d, r, u, -s, -s);
        bhQuad = build("ambience black hole", DefaultVertexFormat.POSITION_TEX_COLOR, 1, true,
                q -> q.quad(tl, tr, br, bl, 0, 0, 1, 0, 1, 1, 0, 1, -1, -1, -1, -1));
    }

    private static float[] corner(double[] d, double[] r, double[] u, double x, double y) {
        return new float[]{
                (float) (d[0] * BH_R + r[0] * x + u[0] * y),
                (float) (d[1] * BH_R + r[1] * x + u[1] * y),
                (float) (d[2] * BH_R + r[2] * x + u[2] * y)};
    }
}
