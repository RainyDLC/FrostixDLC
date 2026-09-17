package fun.newrar.module.impl.render;

import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.render.LightningPath;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public class LightningRenderer implements IMinecraft {
    private static final int SPAWN_PER_TICK = 2;
    private static final int LIGHTNING_DEPTH = 3;

    private static final int WHITE = 0xFFFFFF;
    private static final int RED = 0xFF3B30;
    private static final long HIT_FLASH_DURATION_MS = 350;

    private static final Identifier GLOW = Identifier.of("client", "textures/visuals/particles_2.png");

    public boolean redOnHit = true;

    public int maxBolts = 16;
    public long spawnIntervalMs = 42;

    private final Random random = new Random();
    private final Bolt[] bolts = new Bolt[64];
    private int boltCount = 0;
    private long lastSpawn = 0;
    private long lastHitTime = -HIT_FLASH_DURATION_MS;
    private int lastHurtTime = 0;

    private static final RenderPipeline LIGHTNING_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/lightning_glow")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final Function<Identifier, RenderLayer> LIGHTNING_GLOW = Util.memoize(texture -> {
        RenderSetup setup = RenderSetup.builder(LIGHTNING_GLOW_PIPELINE)
                .texture("Sampler0", texture)
                .translucent()
                .expectedBufferSize(1536)
                .build();
        return RenderLayer.of("lightning_glow", setup);
    });

    private static final RenderPipeline LIGHTNING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "lightning_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LIGHTNING_LINE_LAYER = RenderLayer.of("lightning_line",
            RenderSetup.builder(LIGHTNING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());

    public void notifyHit() {
        lastHitTime = System.currentTimeMillis();
    }

    public void render(EventRender3D e, VertexConsumerProvider.Immediate immediate, LivingEntity target, float anim) {
        if (target == null || anim <= 0.01f) {
            boltCount = 0;
            return;
        }

        if (target.hurtTime > lastHurtTime) {
            notifyHit();
        }
        lastHurtTime = target.hurtTime;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d basePos = target.getLerpedPos(e.getTickDelta());
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();

        long now = System.currentTimeMillis();
        if (now - lastSpawn > spawnIntervalMs && boltCount < maxBolts) {
            for (int s = 0; s < SPAWN_PER_TICK && boltCount < maxBolts; s++) {
                bolts[boltCount++] = spawnBolt(target, basePos);
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

        int visible = 0;
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (now - bolt.spawnTime > bolt.lifetimeMs) {
                bolts[i] = bolts[--boltCount];
                i--;
                continue;
            }

            float life = (now - bolt.spawnTime) / (float) bolt.lifetimeMs;
            float fade = 1.0f - life;
            float flicker = 0.65f + random.nextFloat() * 0.35f;
            float boltAlpha = anim * fade * flicker;
            bolt.drawAlpha = boltAlpha;
            if (boltAlpha <= 0.02f) continue;
            visible++;

            int glowRgb = lerpRgb(WHITE, RED, hitT);
            int coreRgb = lerpRgb(WHITE, RED, hitT);

            bolt.glowColor = withAlpha(glowRgb, (int) (boltAlpha * 150));
            bolt.coreColor = withAlpha(coreRgb, (int) (boltAlpha * 255));
        }
        if (visible == 0) return;

        VertexConsumer glowBuf = immediate.getBuffer(LIGHTNING_GLOW.apply(GLOW));
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            for (Vec3d point : bolt.points) {
                float blobScale = 0.16f + random.nextFloat() * 0.10f;
                float h = blobScale / 2f;

                matrices.push();
                matrices.translate(point.x - cameraPos.x, point.y - cameraPos.y, point.z - cameraPos.z);
                matrices.multiply(cameraRotation);
                Matrix4f m = matrices.peek().getPositionMatrix();

                glowBuf.vertex(m, -h, -h, 0.0f).color(bolt.glowColor).texture(0.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h, -h, 0.0f).color(bolt.glowColor).texture(1.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m,  h,  h, 0.0f).color(bolt.glowColor).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, -h,  h, 0.0f).color(bolt.glowColor).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);

                matrices.pop();
            }
        }

        VertexConsumer lineBuf = immediate.getBuffer(LIGHTNING_LINE_LAYER);
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            matrices.push();
            matrices.translate(basePos.x - cameraPos.x, basePos.y - cameraPos.y, basePos.z - cameraPos.z);
            Matrix4f lm = matrices.peek().getPositionMatrix();

            List<Vec3d> points = bolt.points;
            for (int s = 0; s < points.size() - 1; s++) {
                Vec3d a = points.get(s).subtract(basePos);
                Vec3d b = points.get(s + 1).subtract(basePos);
                lineBuf.vertex(lm, (float) a.x, (float) a.y, (float) a.z).color(bolt.coreColor);
                lineBuf.vertex(lm, (float) b.x, (float) b.y, (float) b.z).color(bolt.coreColor);
            }

            matrices.pop();
        }
    }

    private Bolt spawnBolt(LivingEntity target, Vec3d basePos) {
        double width = target.getWidth();
        double height = target.getHeight();
        double surfaceRadius = width * 0.5 + 0.05;

        double angle = random.nextDouble() * Math.PI * 2;
        double h = random.nextDouble() * height;

        Vec3d start = basePos.add(Math.cos(angle) * surfaceRadius, h, Math.sin(angle) * surfaceRadius);

        double theta = random.nextDouble() * Math.PI * 2;
        double phi = Math.toRadians((random.nextDouble() - 0.5) * 140.0);
        double dx = Math.cos(phi) * Math.cos(theta);
        double dy = Math.sin(phi);
        double dz = Math.cos(phi) * Math.sin(theta);

        double spikeLength = 0.25 + random.nextDouble() * 0.3;
        Vec3d end = start.add(dx * spikeLength, dy * spikeLength, dz * spikeLength);

        List<Vec3d> points = LightningPath.generate(start, end, LIGHTNING_DEPTH, spikeLength * 0.4, random);

        Bolt bolt = new Bolt();
        bolt.points = points;
        bolt.spawnTime = System.currentTimeMillis();
        bolt.lifetimeMs = 130 + random.nextInt(140);
        return bolt;
    }

    private int lerpRgb(int rgbA, int rgbB, float t) {
        if (t <= 0f) return rgbA;
        if (t >= 1f) return rgbB;
        int rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
        int rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static class Bolt {
        List<Vec3d> points;
        long spawnTime;
        long lifetimeMs;

        float drawAlpha = 0f;
        int glowColor;
        int coreColor;
    }

    private final List<Bolt> pointBolts = new ArrayList<>();
    private long pointLastSpawn;

    public void renderPoint(EventRender3D e, Vec3d center, float spread, float animAlpha) {
        if (center == null || animAlpha <= 0.03f) {
            pointBolts.clear();
            return;
        }

        long now = System.currentTimeMillis();
        pointBolts.removeIf(b -> now - b.spawnTime > b.lifetimeMs);

        if (now - pointLastSpawn > 60L && pointBolts.size() < 8) {
            pointLastSpawn = now;
            pointBolts.add(spawnPointBolt(center, spread));
            if (random.nextBoolean()) pointBolts.add(spawnPointBolt(center, spread));
        }
        if (pointBolts.isEmpty()) return;

        var consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();

        VertexConsumer glowBuf = consumers.getBuffer(LIGHTNING_GLOW.apply(GLOW));
        for (Bolt b : pointBolts) {
            float fade = 1f - (now - b.spawnTime) / (float) b.lifetimeMs;
            int col = withAlpha(0x96C3FF, (int) (fade * animAlpha * 145));
            for (Vec3d point : b.points) {
                float h = 0.08f + random.nextFloat() * 0.07f;

                matrices.push();
                matrices.translate(point.x - cameraPos.x, point.y - cameraPos.y, point.z - cameraPos.z);
                matrices.multiply(cameraRotation);
                Matrix4f m = matrices.peek().getPositionMatrix();

                glowBuf.vertex(m, -h, -h, 0.0f).color(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                                (col & 0xFF) / 255f, ((col >>> 24) & 0xFF) / 255f)
                        .texture(0.0F, 1.0F).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, h, -h, 0.0f).color(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                                (col & 0xFF) / 255f, ((col >>> 24) & 0xFF) / 255f)
                        .texture(1.0F, 1.0F).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, h, h, 0.0f).color(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                                (col & 0xFF) / 255f, ((col >>> 24) & 0xFF) / 255f)
                        .texture(1.0F, 0.0F).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0).normal(0, 0, 1);
                glowBuf.vertex(m, -h, h, 0.0f).color(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                                (col & 0xFF) / 255f, ((col >>> 24) & 0xFF) / 255f)
                        .texture(0.0F, 0.0F).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0).normal(0, 0, 1);

                matrices.pop();
            }
        }
        consumers.draw(LIGHTNING_GLOW.apply(GLOW));

        VertexConsumer lineBuf = consumers.getBuffer(LIGHTNING_LINE_LAYER);
        Matrix4f lm = matrices.peek().getPositionMatrix();
        for (Bolt b : pointBolts) {
            float fade = 1f - (now - b.spawnTime) / (float) b.lifetimeMs;
            int ccol = withAlpha(0xEBF5FF, (int) (fade * animAlpha * 235));

            List<Vec3d> pts = b.points;
            for (int sIdx = 0; sIdx < pts.size() - 1; sIdx++) {
                Vec3d a = pts.get(sIdx).subtract(cameraPos);
                Vec3d b2 = pts.get(sIdx + 1).subtract(cameraPos);
                lineBuf.vertex(lm, (float) a.x, (float) a.y, (float) a.z).color(ccol);
                lineBuf.vertex(lm, (float) b2.x, (float) b2.y, (float) b2.z).color(ccol);
            }
        }
        consumers.draw(LIGHTNING_LINE_LAYER);
    }

    private Bolt spawnPointBolt(Vec3d c, float spread) {
        double ang = random.nextDouble() * Math.PI * 2;
        double ph = (random.nextDouble() - 0.5) * Math.PI;
        double r = spread * (0.35 + random.nextDouble() * 0.65);

        Vec3d start = c.add(
                Math.cos(ang) * Math.cos(ph) * r,
                Math.sin(ph) * r,
                Math.sin(ang) * Math.cos(ph) * r);
        Vec3d end = start.add(
                (random.nextDouble() - 0.5) * spread,
                -(0.2 + random.nextDouble() * 0.5) * spread,
                (random.nextDouble() - 0.5) * spread);

        Bolt bolt = new Bolt();
        bolt.points = LightningPath.generate(start, end, 2, r * 0.5, random);
        bolt.spawnTime = System.currentTimeMillis();
        bolt.lifetimeMs = 90 + random.nextInt(110);
        return bolt;
    }
}

