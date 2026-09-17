package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.LightningPath;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public class LightningRenderer implements IMinecraft {
    private static final int MAX_BOLTS_CAP = 64;
    private static final long HIT_FLASH_DURATION_MS = 360L;

    private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");
    private static final Identifier SPARKLE_TEX = Identifier.of("client", "textures/particles/sparkle.png");

    public boolean redOnHit = true;
    public boolean skyStrike = true;
    public boolean groundRing = true;
    public boolean sparksEnabled = true;

    public int maxBolts = 16;
    public long spawnIntervalMs = 42L;
    public float thickness = 1.2F;
    public String colorMode = "Электрический";
    public int customColor = 0x23B4FF;

    private final Random random = new Random();
    private final Bolt[] bolts = new Bolt[MAX_BOLTS_CAP];
    private int boltCount = 0;
    private final List<Spark> sparks = new ArrayList<>();

    private long lastSpawn = 0L;
    private long lastSkyStrike = 0L;
    private long skyStrikeInterval = 1600L;
    private long lastHitTime = -HIT_FLASH_DURATION_MS;
    private int lastHurtTime = 0;
    private long lastFrameTime = 0L;

    private LivingEntity lastTrackedTarget;

    private boolean shockwaveActive = false;
    private long shockwaveStartTime = 0L;
    private Vec3d shockwaveCenter = Vec3d.ZERO;

    private static final RenderPipeline LIGHTNING_COLOR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/target_lightning_color"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LIGHTNING_COLOR_LAYER = RenderLayer.of(
            "target_lightning_color",
            RenderSetup.builder(LIGHTNING_COLOR_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 16)
                    .build()
    );

    private static final RenderPipeline LIGHTNING_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/target_lightning_glow"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final Function<Identifier, RenderLayer> LIGHTNING_TEX_LAYER = Util.memoize(texture -> {
        RenderSetup setup = RenderSetup.builder(LIGHTNING_GLOW_PIPELINE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1 << 14)
                .build();
        return RenderLayer.of("target_lightning_" + texture.getPath().replace('/', '_'), setup);
    });

    public void clear() {
        boltCount = 0;
        sparks.clear();
        shockwaveActive = false;
        lastTrackedTarget = null;
    }

    public void notifyHit() {
        lastHitTime = System.currentTimeMillis();
    }

    public void render(EventRender3D e, VertexConsumerProvider.Immediate immediate, LivingEntity target, float anim) {
        if (target == null || anim <= 0.005f || mc.world == null || mc.player == null) {
            clear();
            return;
        }

        if (target != lastTrackedTarget) {
            clear();
            lastTrackedTarget = target;
            lastHurtTime = target.hurtTime;
        }

        long now = System.currentTimeMillis();
        float dt = lastFrameTime == 0L ? 0.016f : (float) ((now - lastFrameTime) / 1000.0);
        lastFrameTime = now;
        dt = MathHelper.clamp(dt, 0.001f, 0.05f);

        if (target.hurtTime > lastHurtTime) {
            notifyHit();
            if (skyStrike && now - lastSkyStrike > 350L) {
                spawnSkyStrike(target, target.getLerpedPos(e.getTickDelta()), now);
                lastSkyStrike = now;
            }
        }
        lastHurtTime = target.hurtTime;

        Vec3d basePos = target.getLerpedPos(e.getTickDelta());
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();
        Vector3f camRight = cameraRotation.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = cameraRotation.transform(new Vector3f(0, 1, 0));

        if (skyStrike && now - lastSkyStrike > skyStrikeInterval) {
            spawnSkyStrike(target, basePos, now);
            lastSkyStrike = now;
            skyStrikeInterval = 1300L + random.nextInt(1100);
        }

        if (now - lastSpawn > spawnIntervalMs && boltCount < Math.min(maxBolts, MAX_BOLTS_CAP)) {
            int toSpawn = 1 + (random.nextFloat() < 0.45f ? 1 : 0);
            for (int s = 0; s < toSpawn && boltCount < Math.min(maxBolts, MAX_BOLTS_CAP); s++) {
                float roll = random.nextFloat();
                if (groundRing && roll < 0.28f) {
                    bolts[boltCount++] = spawnGroundArc(target, basePos, now);
                } else if (roll < 0.65f) {
                    bolts[boltCount++] = spawnOrbitArc(target, basePos, now);
                } else {
                    bolts[boltCount++] = spawnBodyArc(target, basePos, now);
                }
            }
            lastSpawn = now;
        }

        float hitT = 0f;
        if (redOnHit) {
            long sinceHit = now - lastHitTime;
            if (sinceHit < HIT_FLASH_DURATION_MS) {
                hitT = 1.0f - (sinceHit / (float) HIT_FLASH_DURATION_MS);
            }
        }

        MatrixStack matrices = e.getMatrixStack();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer colorBuf = immediate.getBuffer(LIGHTNING_COLOR_LAYER);
        VertexConsumer glowBuf = immediate.getBuffer(LIGHTNING_TEX_LAYER.apply(GLOW_TEX));
        VertexConsumer sparkleBuf = immediate.getBuffer(LIGHTNING_TEX_LAYER.apply(SPARKLE_TEX));

        if (groundRing) {
            renderGroundSeal(colorBuf, glowBuf, matrix, basePos, cameraPos, target.getWidth(), anim, hitT, now);
        }

        if (shockwaveActive) {
            long swAge = now - shockwaveStartTime;
            if (swAge > 500L) {
                shockwaveActive = false;
            } else {
                float swProgress = swAge / 500.0f;
                float swRadius = (target.getWidth() * 0.5f) + 0.2f + swProgress * 1.8f;
                float swAlpha = (1.0f - swProgress) * (1.0f - swProgress) * anim;
                int swColor = withAlpha(getOuterRgb(0.5f, hitT), (int) (swAlpha * 180));
                renderFlatCircleRing(colorBuf, matrix, shockwaveCenter, cameraPos, swRadius, 0.16f * (1.0f - swProgress * 0.6f), swColor);
            }
        }

        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            long age = now - bolt.spawnTime;
            if (age > bolt.lifetimeMs) {
                bolts[i] = bolts[--boltCount];
                i--;
                continue;
            }

            float life = age / (float) bolt.lifetimeMs;
            float env = (float) Math.sin(life * Math.PI);
            float flicker = 0.72f + random.nextFloat() * 0.28f;
            float boltAlpha = anim * env * flicker;
            if (boltAlpha <= 0.02f) continue;

            float boltThick = bolt.thickness * thickness;
            int outerCol = withAlpha(getOuterRgb((bolt.spawnTime % 1000L) / 1000.0f, hitT), (int) (boltAlpha * 190));
            int coreCol = withAlpha(getCoreRgb(outerCol, hitT), (int) (boltAlpha * 255));
            int glowCol = withAlpha(outerCol, (int) (boltAlpha * 140));

            float outerWidth = (bolt.isSkyStrike ? 0.13f : 0.075f) * boltThick;
            ribbonPolyline(colorBuf, matrix, bolt.points, cameraPos, outerWidth, outerCol, outerCol, bolt.points.size());
            for (List<Vec3d> branch : bolt.branches) {
                ribbonPolyline(colorBuf, matrix, branch, cameraPos, outerWidth * 0.65f, outerCol, outerCol, branch.size());
            }

            float coreWidth = (bolt.isSkyStrike ? 0.046f : 0.024f) * boltThick;
            ribbonPolyline(colorBuf, matrix, bolt.points, cameraPos, coreWidth, coreCol, coreCol, bolt.points.size());
            for (List<Vec3d> branch : bolt.branches) {
                ribbonPolyline(colorBuf, matrix, branch, cameraPos, coreWidth * 0.62f, coreCol, coreCol, branch.size());
            }

            int ptCount = bolt.points.size();
            for (int p = 0; p < ptCount; p += 2) {
                Vec3d pt = bolt.points.get(p);
                float haloSize = (bolt.isSkyStrike ? 0.32f : 0.16f) * boltThick;
                drawSpriteDirect(glowBuf, matrix, pt.x, pt.y, pt.z, cameraPos, camRight, camUp, haloSize, glowCol);
            }

            if (bolt.isSkyStrike && bolt.impact != null) {
                float impactHalo = 0.85f * env * boltThick;
                drawSpriteDirect(glowBuf, matrix, bolt.impact.x, bolt.impact.y + 0.1, bolt.impact.z,
                        cameraPos, camRight, camUp, impactHalo, withAlpha(outerCol, (int) (boltAlpha * 240)));
            }
        }

        if (sparksEnabled) {
            updateAndRenderSparks(sparkleBuf, matrix, cameraPos, camRight, camUp, basePos, now, dt, anim, hitT);
        }
    }

    private void renderGroundSeal(VertexConsumer colorBuf, VertexConsumer glowBuf, Matrix4f matrix,
                                  Vec3d basePos, Vec3d cameraPos, float targetWidth, float anim, float hitT, long now) {
        float groundRadius = (targetWidth * 0.5f) + 0.38f;
        int outerColor = getOuterRgb(0.2f, hitT);

        float discPulse = 0.85f + 0.15f * (float) Math.sin(now * 0.008);
        drawFlatDisc(glowBuf, matrix, basePos.x, basePos.y + 0.02, basePos.z, cameraPos,
                groundRadius * 1.55f, withAlpha(outerColor, (int) (55 * anim * discPulse)));

        renderFlatCircleRing(colorBuf, matrix, basePos, cameraPos, groundRadius, 0.065f * thickness,
                withAlpha(outerColor, (int) (115 * anim * (0.8f + 0.2f * (float) Math.sin(now * 0.012)))));

        renderFlatCircleRing(colorBuf, matrix, basePos, cameraPos, groundRadius * 0.65f, 0.038f * thickness,
                withAlpha(outerColor, (int) (90 * anim * (0.8f + 0.2f * (float) Math.cos(now * 0.015)))));
    }

    private void renderFlatCircleRing(VertexConsumer buf, Matrix4f matrix, Vec3d center, Vec3d camPos,
                                      float radius, float width, int color) {
        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y + 0.025 - camPos.y);
        float cz = (float) (center.z - camPos.z);

        int segs = 32;
        float rOut = radius + width * 0.5f;
        float rIn = Math.max(0.01f, radius - width * 0.5f);

        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;

            float x0_out = cx + (float) Math.cos(a0) * rOut;
            float z0_out = cz + (float) Math.sin(a0) * rOut;
            float x1_out = cx + (float) Math.cos(a1) * rOut;
            float z1_out = cz + (float) Math.sin(a1) * rOut;

            float x0_in = cx + (float) Math.cos(a0) * rIn;
            float z0_in = cz + (float) Math.sin(a0) * rIn;
            float x1_in = cx + (float) Math.cos(a1) * rIn;
            float z1_in = cz + (float) Math.sin(a1) * rIn;

            buf.vertex(matrix, x0_out, cy, z0_out).color(color);
            buf.vertex(matrix, x1_out, cy, z1_out).color(color);
            buf.vertex(matrix, x1_in, cy, z1_in).color(color);
            buf.vertex(matrix, x0_in, cy, z0_in).color(color);
        }
    }

    private void drawFlatDisc(VertexConsumer buf, Matrix4f matrix, double px, double py, double pz,
                              Vec3d camPos, float radius, int color) {
        float x = (float) (px - camPos.x);
        float y = (float) (py - camPos.y);
        float z = (float) (pz - camPos.z);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;

        buf.vertex(matrix, x - radius, y, z - radius).texture(0f, 0f).color(r, g, b, a);
        buf.vertex(matrix, x - radius, y, z + radius).texture(0f, 1f).color(r, g, b, a);
        buf.vertex(matrix, x + radius, y, z + radius).texture(1f, 1f).color(r, g, b, a);
        buf.vertex(matrix, x + radius, y, z - radius).texture(1f, 0f).color(r, g, b, a);
    }

    private void updateAndRenderSparks(VertexConsumer sparkBuf, Matrix4f matrix, Vec3d camPos,
                                      Vector3f right, Vector3f up, Vec3d basePos, long now, float dt, float anim, float hitT) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark sp = sparks.get(i);
            float ageSec = (now - sp.born) / 1000.0f;
            if (ageSec > sp.life) {
                sparks.remove(i);
                continue;
            }

            sp.vy -= 9.8 * dt;
            sp.px += sp.vx * dt;
            sp.py += sp.vy * dt;
            sp.pz += sp.vz * dt;

            if (sp.py < basePos.y + 0.02) {
                sp.py = basePos.y + 0.02;
                sp.vy = -sp.vy * 0.4;
                sp.vx *= 0.65;
                sp.vz *= 0.65;
            }

            float lifeRatio = 1.0f - (ageSec / sp.life);
            int sparkAlpha = (int) (lifeRatio * anim * 245);
            if (sparkAlpha <= 3) continue;

            int col = withAlpha(sp.color, sparkAlpha);
            float sz = sp.size * (0.6f + 0.4f * lifeRatio) * (0.8f + random.nextFloat() * 0.4f);
            drawSpriteDirect(sparkBuf, matrix, sp.px, sp.py, sp.pz, camPos, right, up, sz, col);
        }
    }

    private void triggerShockwave(Vec3d center) {
        shockwaveActive = true;
        shockwaveStartTime = System.currentTimeMillis();
        shockwaveCenter = center;
    }

    private void spawnSkyStrike(LivingEntity target, Vec3d basePos, long now) {
        double height = target.getHeight();
        Vec3d strikeImpact = basePos.add((random.nextDouble() - 0.5) * 0.25, height * 0.9, (random.nextDouble() - 0.5) * 0.25);
        double skyHeight = 9.0 + random.nextDouble() * 5.0;
        Vec3d skyStart = basePos.add((random.nextDouble() - 0.5) * 2.0, height + skyHeight, (random.nextDouble() - 0.5) * 2.0);

        Bolt bolt = new Bolt();
        bolt.isSkyStrike = true;
        bolt.spawnTime = now;
        bolt.lifetimeMs = 240 + random.nextInt(80);
        bolt.thickness = 1.6F;
        bolt.impact = strikeImpact;
        bolt.points = LightningPath.generate(skyStart, strikeImpact, 4, 0.75 * thickness, 0.62, random);

        int branchCount = 2 + random.nextInt(2);
        for (int b = 0; b < branchCount; b++) {
            if (bolt.points.size() < 6) break;
            int idx = 2 + random.nextInt(bolt.points.size() - 4);
            Vec3d from = bolt.points.get(idx);
            double blen = 2.0 + random.nextDouble() * 3.0;
            double ba = random.nextDouble() * Math.PI * 2.0;
            Vec3d bend = new Vec3d(
                    from.x + Math.cos(ba) * blen,
                    from.y - blen * (0.55 + random.nextDouble() * 0.45),
                    from.z + Math.sin(ba) * blen
            );
            bolt.branches.add(LightningPath.generate(from, bend, 3, blen * 0.26, 0.58, random));
        }

        if (boltCount < MAX_BOLTS_CAP) {
            bolts[boltCount++] = bolt;
        }

        triggerShockwave(basePos);
        if (sparksEnabled) {
            int sparkCount = 14 + random.nextInt(8);
            for (int s = 0; s < sparkCount; s++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double spd = 2.5 + random.nextDouble() * 5.5;
                double vx = Math.cos(a) * spd;
                double vz = Math.sin(a) * spd;
                double vy = 2.5 + random.nextDouble() * 5.0;
                int col = getOuterRgb(random.nextFloat(), redOnHit && (now - lastHitTime < HIT_FLASH_DURATION_MS) ? 1f : 0f);
                sparks.add(new Spark(strikeImpact.x, strikeImpact.y, strikeImpact.z, vx, vy, vz, now,
                        0.5f + random.nextFloat() * 0.5f, 0.08f + random.nextFloat() * 0.08f, col));
            }
        }
    }

    private Bolt spawnBodyArc(LivingEntity target, Vec3d basePos, long now) {
        double width = target.getWidth();
        double height = target.getHeight();
        double radius = width * 0.5 + 0.06;

        double a1 = random.nextDouble() * Math.PI * 2.0;
        double h1 = 0.1 + random.nextDouble() * (height - 0.2);
        Vec3d start = basePos.add(Math.cos(a1) * (radius * (0.8 + random.nextDouble() * 0.4)), h1, Math.sin(a1) * (radius * (0.8 + random.nextDouble() * 0.4)));

        double a2 = a1 + (random.nextBoolean() ? 1 : -1) * (0.6 + random.nextDouble() * 1.5);
        double h2 = MathHelper.clamp(h1 + (random.nextDouble() - 0.5) * height * 0.8, 0.05, height);
        Vec3d end = basePos.add(Math.cos(a2) * (radius * (0.8 + random.nextDouble() * 0.4)), h2, Math.sin(a2) * (radius * (0.8 + random.nextDouble() * 0.4)));

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 90 + random.nextInt(100);
        bolt.thickness = 0.9F + random.nextFloat() * 0.4F;
        bolt.points = LightningPath.generate(start, end, 3, 0.18 * thickness, 0.56, random);

        if (random.nextFloat() < 0.35f && bolt.points.size() > 3) {
            int forkIdx = 1 + random.nextInt(bolt.points.size() - 2);
            Vec3d fStart = bolt.points.get(forkIdx);
            Vec3d fEnd = fStart.add(
                    (random.nextDouble() - 0.5) * 0.5 * thickness,
                    (random.nextDouble() - 0.5) * 0.5 * thickness,
                    (random.nextDouble() - 0.5) * 0.5 * thickness
            );
            bolt.branches.add(LightningPath.generate(fStart, fEnd, 2, 0.12 * thickness, 0.55, random));
        }

        if (sparksEnabled && random.nextFloat() < 0.4f) {
            Vec3d spPos = bolt.points.get(random.nextInt(bolt.points.size()));
            sparks.add(new Spark(spPos.x, spPos.y, spPos.z,
                    (random.nextDouble() - 0.5) * 2.0, 1.2 + random.nextDouble() * 2.0, (random.nextDouble() - 0.5) * 2.0,
                    now, 0.35f + random.nextFloat() * 0.35f, 0.05f + random.nextFloat() * 0.06f,
                    getOuterRgb(random.nextFloat(), 0f)));
        }

        return bolt;
    }

    private Bolt spawnOrbitArc(LivingEntity target, Vec3d basePos, long now) {
        double width = target.getWidth();
        double height = target.getHeight();
        double radius = width * 0.5 + 0.16;

        double aStart = random.nextDouble() * Math.PI * 2.0;
        double aEnd = aStart + (random.nextBoolean() ? 1 : -1) * (1.2 + random.nextDouble() * 1.6);
        double hStart = random.nextDouble() * (height * 0.6);
        double hEnd = MathHelper.clamp(hStart + 0.3 + random.nextDouble() * 0.7, 0.1, height);

        Vec3d start = basePos.add(Math.cos(aStart) * radius, hStart, Math.sin(aStart) * radius);
        Vec3d end = basePos.add(Math.cos(aEnd) * radius, hEnd, Math.sin(aEnd) * radius);

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 110 + random.nextInt(90);
        bolt.thickness = 1.0F;
        bolt.points = LightningPath.generate(start, end, 3, 0.22 * thickness, 0.58, random);

        return bolt;
    }

    private Bolt spawnGroundArc(LivingEntity target, Vec3d basePos, long now) {
        double groundRadius = (target.getWidth() * 0.5) + 0.38;
        double a1 = random.nextDouble() * Math.PI * 2.0;
        double a2 = a1 + (random.nextDouble() - 0.5) * 1.4;

        Vec3d start = basePos.add(Math.cos(a1) * groundRadius, 0.02, Math.sin(a1) * groundRadius);
        Vec3d end = (random.nextFloat() < 0.45f)
                ? basePos.add((random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2)
                : basePos.add(Math.cos(a2) * groundRadius, 0.02, Math.sin(a2) * groundRadius);

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 80 + random.nextInt(80);
        bolt.thickness = 0.85F;
        bolt.points = LightningPath.generate(start, end, 2, 0.14 * thickness, 0.55, random);

        return bolt;
    }

    public int getOuterRgb(float progress, float hitT) {
        int base;
        switch (colorMode) {
            case "Тема":
                base = ColorUtil.fade((int) (progress * 360));
                break;
            case "Фиолетовый":
                base = 0xB537F2;
                break;
            case "Золотой":
                base = 0xFFA000;
                break;
            case "Красный":
                base = 0xFF2A2A;
                break;
            case "Свой":
                base = customColor;
                break;
            case "Электрический":
            default:
                base = 0x22B2FF;
                break;
        }

        if (redOnHit && hitT > 0.01f) {
            base = ColorUtil.overCol(base, 0xFF1C24, hitT);
        }
        return base;
    }

    public int getCoreRgb(int outerRgb, float hitT) {
        if (redOnHit && hitT > 0.4f) {
            return 0xFFDADA;
        }
        return 0xFFFFFF;
    }

    private static void ribbonPolyline(VertexConsumer buf, Matrix4f matrix,
                                       List<Vec3d> pts, Vec3d camPos, float halfW,
                                       int colStart, int colEnd, int maxVerts) {
        int segs = Math.min(pts.size(), maxVerts) - 1;
        if (segs <= 0) return;

        for (int i = 0; i < segs; i++) {
            Vec3d a = pts.get(i);
            Vec3d b = pts.get(i + 1);
            float ax = (float) (a.x - camPos.x), ay = (float) (a.y - camPos.y), az = (float) (a.z - camPos.z);
            float bx = (float) (b.x - camPos.x), by = (float) (b.y - camPos.y), bz = (float) (b.z - camPos.z);

            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float cx = ay * dz - az * dy;
            float cy = az * dx - ax * dz;
            float cz = ax * dy - ay * dx;
            float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1e-6f) continue;

            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;

            float w0 = halfW * (1.0f - 0.22f * t0);
            float w1 = halfW * (1.0f - 0.22f * t1);

            float cx0 = cx / len * w0, cy0 = cy / len * w0, cz0 = cz / len * w0;
            float cx1 = cx / len * w1, cy1 = cy / len * w1, cz1 = cz / len * w1;

            int c0 = lerpRgb(colStart, colEnd, t0);
            int c1 = lerpRgb(colStart, colEnd, t1);

            vertex(buf, matrix, ax - cx0, ay - cy0, az - cz0, c0);
            vertex(buf, matrix, ax + cx0, ay + cy0, az + cz0, c0);
            vertex(buf, matrix, bx + cx1, by + cy1, bz + cz1, c1);
            vertex(buf, matrix, bx - cx1, by - cy1, bz - cz1, c1);
        }
    }

    private static void drawSpriteDirect(VertexConsumer buf, Matrix4f matrix,
                                         double px, double py, double pz,
                                         Vec3d camPos, Vector3f right, Vector3f up,
                                         float half, int color) {
        float x = (float) (px - camPos.x);
        float y = (float) (py - camPos.y);
        float z = (float) (pz - camPos.z);
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;

        buf.vertex(matrix, x - rx - ux, y - ry - uy, z - rz - uz).texture(0f, 0f).color(r, g, b, a);
        buf.vertex(matrix, x - rx + ux, y - ry + uy, z - rz + uz).texture(0f, 1f).color(r, g, b, a);
        buf.vertex(matrix, x + rx + ux, y + ry + uy, z + rz + uz).texture(1f, 1f).color(r, g, b, a);
        buf.vertex(matrix, x + rx - ux, y + ry - uy, z + rz - uz).texture(1f, 0f).color(r, g, b, a);
    }

    private static void vertex(VertexConsumer buf, Matrix4f matrix, float x, float y, float z, int color) {
        buf.vertex(matrix, x, y, z).color(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f
        );
    }

    private static int lerpRgb(int rgbA, int rgbB, float t) {
        if (t <= 0f) return rgbA;
        if (t >= 1f) return rgbB;
        int aA = (rgbA >>> 24) & 0xFF, rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
        int aB = (rgbB >>> 24) & 0xFF, rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;
        int a = (int) (aA + (aB - aA) * t);
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private static class Bolt {
        boolean isSkyStrike = false;
        List<Vec3d> points = new ArrayList<>();
        List<List<Vec3d>> branches = new ArrayList<>();
        Vec3d impact;
        long spawnTime;
        long lifetimeMs;
        float thickness = 1.0F;
    }

    private static class Spark {
        double px, py, pz;
        double vx, vy, vz;
        final long born;
        final float life;
        final float size;
        final int color;

        Spark(double x, double y, double z, double vx, double vy, double vz, long born, float life, float size, int col) {
            this.px = x;
            this.py = y;
            this.pz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.born = born;
            this.life = life;
            this.size = size;
            this.color = col;
        }
    }
}


