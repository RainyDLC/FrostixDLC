package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventRender3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Эффект мокрой поверхности земли: тёмная плёнка воды, зеркальный глянец с Френелем
 * и вытянутые отражения объектов / источников света.
 */
public final class WetGroundRenderer {
    private static final Identifier GLOW_TEX =
            Identifier.of("client", "textures/particles/glow.png");

    private static final int REBUILD_TICKS = 10;
    private static final int MAX_TILES = 6000;

    /** Плёнка воды — затемняет поверхность (TRANSLUCENT). */
    private static final RenderPipeline FILM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/wetground_film"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer FILM_LAYER = RenderLayer.of("wetground_film",
            RenderSetup.builder(FILM_PIPELINE).translucent().expectedBufferSize(1 << 16).build());

    /** Зеркальный блик — аддитивный (как у дождя / глоу). */
    private static final RenderPipeline GLOSS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/wetground_gloss"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer GLOSS_LAYER = RenderLayer.of("wetground_gloss",
            RenderSetup.builder(GLOSS_PIPELINE).translucent().expectedBufferSize(1 << 16).build());

    /** Отражения объектов — мягкое пятно по glow-текстуре. */
    private static final RenderPipeline REFLECT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/wetground_reflect"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    private static final RenderLayer REFLECT_LAYER = RenderLayer.of("wetground_reflect",
            RenderSetup.builder(REFLECT_PIPELINE)
                    .texture("Sampler0", GLOW_TEX)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build());

    private record Tile(int x, int z, float y, float puddle, float blockLight, float skyLight) {
    }

    private static final List<Tile> tiles = new ArrayList<>();
    private static final Map<Long, Tile> lookup = new HashMap<>();

    private static float cfgWetness = 55F;
    private static float cfgGloss = 65F;
    private static float cfgFresnel = 5F;
    private static float cfgF0 = 0.08F;
    private static boolean cfgRipples = true;
    private static boolean cfgObjects = true;
    private static boolean cfgSkyTint = true;
    private static int cfgTint = 0xFF9CC6FF;
    private static int cfgRadius = 20;
    private static float cfgPuddles = 0.45F;

    private static int lastX = Integer.MIN_VALUE, lastY, lastZ;
    private static int lastRadius = -1;
    private static boolean lastSkyOnly;
    private static int skipTicks;

    private WetGroundRenderer() {
    }

    public static void configure(float wetness, float gloss, float fresnelPower, float baseSpecular,
                                 boolean ripples, boolean objects, boolean skyTint, int tintColor) {
        cfgWetness = wetness;
        cfgGloss = gloss;
        cfgFresnel = Math.max(1F, fresnelPower);
        cfgF0 = MathHelper.clamp(baseSpecular / 100F, 0F, 0.5F);
        cfgRipples = ripples;
        cfgObjects = objects;
        cfgSkyTint = skyTint;
        cfgTint = tintColor;
    }

    public static void update(boolean enabled, int radius, boolean skyOnly, float puddleCoverage) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.world == null) {
            clear();
            return;
        }

        cfgRadius = radius;
        cfgPuddles = MathHelper.clamp(puddleCoverage / 100F, 0F, 1F);

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();

        boolean moved = px != lastX || pz != lastZ || Math.abs(py - lastY) > 1;
        boolean dirty = radius != lastRadius || skyOnly != lastSkyOnly;

        if (!tiles.isEmpty() && !moved && !dirty && ++skipTicks < REBUILD_TICKS) return;

        skipTicks = 0;
        lastX = px;
        lastY = py;
        lastZ = pz;
        lastRadius = radius;
        lastSkyOnly = skyOnly;

        rebuild(mc, px, py, pz, radius, skyOnly);
    }

    public static void clear() {
        tiles.clear();
        lookup.clear();
        lastX = Integer.MIN_VALUE;
        lastRadius = -1;
        skipTicks = 0;
    }

    private static void rebuild(MinecraftClient mc, int px, int py, int pz, int radius, boolean skyOnly) {
        tiles.clear();
        lookup.clear();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        float rSq = (float) radius * radius;

        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                float dx = x + 0.5F - px;
                float dz = z + 0.5F - pz;
                if (dx * dx + dz * dz > rSq) continue;
                if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) continue;

                int surfaceY = skyOnly
                        // при других маппингах: mc.world.getTopPosition(...).getY()
                        ? mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1
                        : findLocalSurface(mc, mutable, x, z, py);
                if (surfaceY == Integer.MIN_VALUE) continue;

                mutable.set(x, surfaceY, z);
                BlockState state = mc.world.getBlockState(mutable);
                if (state.isAir()) continue;
                // вода и лава отражают сами
                if (!state.getFluidState().isEmpty()) continue;
                if (!state.isSideSolidFullSquare(mc.world, mutable, Direction.UP)) continue;

                VoxelShape shape = state.getCollisionShape(mc.world, mutable);
                if (shape.isEmpty()) continue;
                if (shape.getMin(Direction.Axis.X) > 0.01 || shape.getMax(Direction.Axis.X) < 0.99) continue;
                if (shape.getMin(Direction.Axis.Z) > 0.01 || shape.getMax(Direction.Axis.Z) < 0.99) continue;

                float topY = (float) (surfaceY + shape.getMax(Direction.Axis.Y));

                mutable.set(x, surfaceY + 1, z);
                int blockLight = mc.world.getLightLevel(LightType.BLOCK, mutable);
                int skyLight = mc.world.getLightLevel(LightType.SKY, mutable);

                Tile tile = new Tile(x, z, topY, puddleStrength(x, z, cfgPuddles), blockLight, skyLight);
                tiles.add(tile);
                lookup.put(key(x, z), tile);

                if (tiles.size() >= MAX_TILES) return;
            }
        }
    }

    private static int findLocalSurface(MinecraftClient mc, BlockPos.Mutable mutable, int x, int z, int py) {
        int top = Math.min(py + 3, mc.world.getTopYInclusive());
        int bottom = Math.max(py - 8, mc.world.getBottomY());
        for (int y = top; y >= bottom; y--) {
            mutable.set(x, y, z);
            BlockState state = mc.world.getBlockState(mutable);
            if (state.isAir()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    public static void render(EventRender3D e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (tiles.isEmpty() || mc.player == null || mc.world == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f matrix = e.getMatrixStack().peek().getPositionMatrix();

        float flash = SkyLightningRenderer.flashLevel();
        int reflect = reflectionColor(mc, flash);

        int filmR = 8 + (((reflect >> 16) & 0xFF) * 22) / 255;
        int filmG = 11 + (((reflect >> 8) & 0xFF) * 24) / 255;
        int filmB = 16 + ((reflect & 0xFF) * 30) / 255;

        float wetAmt = cfgWetness / 100F;
        float glossAmt = cfgGloss / 100F;
        float time = (System.currentTimeMillis() % 600_000L) / 1000F;
        float radius = Math.max(1F, cfgRadius);

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        // ---- 1. Плёнка воды: поверхность становится темнее и насыщеннее
        VertexConsumer film = consumers.getBuffer(FILM_LAYER);
        for (Tile t : tiles) {
            if (cam.y < t.y + 0.05) continue;
            float fade = fade(cam, t, radius);
            if (fade <= 0F) continue;

            int alpha = clampAlpha(108F * wetAmt * fade * env(t) * (0.8F + 0.45F * t.puddle));
            if (alpha < 3) continue;

            int color = argb(filmR, filmG, filmB, alpha);
            quad(film, matrix, cam, t, 0.012F, color, color, color, color);
        }
        consumers.draw(FILM_LAYER);

        // ---- 2. Зеркальный глянец: Френель per-vertex + подмес цвета источников света
        VertexConsumer gloss = consumers.getBuffer(GLOSS_LAYER);
        for (Tile t : tiles) {
            if (cam.y < t.y + 0.05) continue;
            float fade = fade(cam, t, radius);
            if (fade <= 0F) continue;

            float shimmer = 1F;
            if (cfgRipples && t.puddle > 0.05F) {
                shimmer = 1F
                        + 0.22F * t.puddle * (float) Math.sin(time * 2.1F + t.x * 0.7F + t.z * 1.1F)
                        + 0.11F * t.puddle * (float) Math.sin(time * 3.7F - t.x * 1.3F + t.z * 0.5F);
            }

            float k = 235F * glossAmt * fade * env(t) * (0.5F + 0.95F * t.puddle) * shimmer;
            if (k < 2F) continue;

            int color = t.blockLight > 0
                    ? mixColor(reflect, 0xFFFFC484, Math.min(0.85F, t.blockLight / 15F * 0.9F))
                    : reflect;

            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

            int a00 = clampAlpha(k * fresnel(cam, t.x, t.y, t.z));
            int a01 = clampAlpha(k * fresnel(cam, t.x, t.y, t.z + 1));
            int a11 = clampAlpha(k * fresnel(cam, t.x + 1, t.y, t.z + 1));
            int a10 = clampAlpha(k * fresnel(cam, t.x + 1, t.y, t.z));
            if (a00 + a01 + a11 + a10 < 6) continue;

            quad(gloss, matrix, cam, t, 0.020F,
                    argb(r, g, b, a00), argb(r, g, b, a01),
                    argb(r, g, b, a11), argb(r, g, b, a10));
        }
        consumers.draw(GLOSS_LAYER);

        // ---- 3. Отражения объектов: вытянутый блик от базы объекта в сторону камеры
        if (cfgObjects) {
            VertexConsumer reflectBuf = consumers.getBuffer(REFLECT_LAYER);
            int objectColor = mixColor(reflect, 0xFFFFFFFF, 0.35F);
            int rr = (objectColor >> 16) & 0xFF, rg = (objectColor >> 8) & 0xFF, rb = objectColor & 0xFF;

            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player || entity.isInvisible()) continue;

                double ex = entity.getX(), ez = entity.getZ();
                double ddx = cam.x - ex, ddz = cam.z - ez;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist > radius || dist < 1.0E-3) continue;

                Tile t = lookup.get(key(MathHelper.floor(ex), MathHelper.floor(ez)));
                if (t == null || Math.abs(entity.getY() - t.y) > 1.5 || cam.y < t.y + 0.05) continue;

                float fade = fade(cam, t, radius);
                if (fade <= 0F) continue;

                double dirX = ddx / dist, dirZ = ddz / dist;
                double perpX = -dirZ, perpZ = dirX;

                double dy = cam.y - t.y;
                float cos = (float) (dy / Math.sqrt(dist * dist + dy * dy));
                float grazing = 1F - cos;

                float length = Math.max(0.6F, entity.getHeight()) * (0.45F + 1.35F * grazing);
                float half = Math.max(0.28F, entity.getWidth() * 0.45F);

                int nearA = clampAlpha(190F * glossAmt * fade * env(t)
                        * fresnel(cam, ex, t.y, ez) * (0.6F + 0.6F * t.puddle));
                if (nearA < 3) continue;

                int near = argb(rr, rg, rb, nearA);
                int far = argb(rr, rg, rb, 0);

                float bx = (float) (ex - cam.x), bz = (float) (ez - cam.z);
                float y = (float) (t.y - cam.y) + 0.026F;

                float n0x = bx + (float) (perpX * half), n0z = bz + (float) (perpZ * half);
                float n1x = bx - (float) (perpX * half), n1z = bz - (float) (perpZ * half);
                float f1x = bx + (float) (dirX * length - perpX * half * 1.7);
                float f1z = bz + (float) (dirZ * length - perpZ * half * 1.7);
                float f0x = bx + (float) (dirX * length + perpX * half * 1.7);
                float f0z = bz + (float) (dirZ * length + perpZ * half * 1.7);

                reflectBuf.vertex(matrix, n0x, y, n0z).texture(0F, 1F).color(near);
                reflectBuf.vertex(matrix, n1x, y, n1z).texture(1F, 1F).color(near);
                reflectBuf.vertex(matrix, f1x, y, f1z).texture(1F, 0F).color(far);
                reflectBuf.vertex(matrix, f0x, y, f0z).texture(0F, 0F).color(far);
            }
            consumers.draw(REFLECT_LAYER);
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f matrix, Vec3d cam, Tile t, float offset,
                             int c00, int c01, int c11, int c10) {
        float x0 = (float) (t.x - cam.x);
        float z0 = (float) (t.z - cam.z);
        float y = (float) (t.y - cam.y) + offset;

        buf.vertex(matrix, x0, y, z0).color(c00);
        buf.vertex(matrix, x0, y, z0 + 1F).color(c01);
        buf.vertex(matrix, x0 + 1F, y, z0 + 1F).color(c11);
        buf.vertex(matrix, x0 + 1F, y, z0).color(c10);
    }

    /** Шлик: F = F0 + (1 - F0) * (1 - cos)^power, нормаль поверхности = (0,1,0). */
    private static float fresnel(Vec3d cam, double x, double y, double z) {
        double dx = cam.x - x, dy = cam.y - y, dz = cam.z - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4) return 1F;

        double base = 1.0 - Math.abs(dy) / len;
        double f;
        if (cfgFresnel == 5F) {
            f = base * base;
            f *= f;
            f *= base;
        } else {
            f = Math.pow(base, cfgFresnel);
        }
        return (float) (cfgF0 + (1.0 - cfgF0) * f);
    }

    private static float fade(Vec3d cam, Tile t, float radius) {
        double dx = cam.x - (t.x + 0.5), dz = cam.z - (t.z + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= radius) return 0F;
        float fade = 1F - (float) (dist / radius);
        return fade * fade;
    }

    private static float env(Tile t) {
        float light = Math.max(t.skyLight * 0.85F, t.blockLight) / 15F;
        return 0.32F + 0.68F * MathHelper.clamp(light, 0F, 1F);
    }

    private static int reflectionColor(MinecraftClient mc, float flash) {
        int base = cfgSkyTint ? skyTone(mc) : cfgTint;
        if (flash > 0.01F) base = mixColor(base, 0xFFEAF3FF, Math.min(1F, flash * 1.2F));
        return base;
    }

    /** Приблизительный цвет отражённого неба по времени суток (без завязки на маппинги неба). */
    private static int skyTone(MinecraftClient mc) {
        float t = (mc.world.getTimeOfDay() % 24000L) / 24000F;

        float[] stops = {0F, 0.08F, 0.44F, 0.52F, 0.58F, 0.94F, 1F};
        int[] colors = {0xFFFFA878, 0xFF9CC4F0, 0xFF9CC4F0, 0xFFFF966A, 0xFF1C284E, 0xFF1C284E, 0xFFFFA878};

        int color = colors[0];
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i]) {
                float span = Math.max(1.0E-4F, stops[i] - stops[i - 1]);
                color = mixColor(colors[i - 1], colors[i], (t - stops[i - 1]) / span);
                break;
            }
        }

        if (mc.world.isRaining()) color = mixColor(color, 0xFF788898, 0.45F);
        return color;
    }

    private static float puddleStrength(int x, int z, float coverage) {
        if (coverage <= 0.001F) return 0F;
        float n = valueNoise(x * 0.21F, z * 0.21F) * 0.65F + valueNoise(x * 0.53F, z * 0.53F) * 0.35F;
        return MathHelper.clamp((n - (1F - coverage)) / 0.22F, 0F, 1F);
    }

    private static float valueNoise(float x, float z) {
        int xi = MathHelper.floor(x), zi = MathHelper.floor(z);
        float fx = x - xi, fz = z - zi;
        float sx = fx * fx * (3F - 2F * fx);
        float sz = fz * fz * (3F - 2F * fz);

        float n0 = MathHelper.lerp(sx, hash(xi, zi), hash(xi + 1, zi));
        float n1 = MathHelper.lerp(sx, hash(xi, zi + 1), hash(xi + 1, zi + 1));
        return MathHelper.lerp(sz, n0, n1);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int mixColor(int base, int overlay, float t) {
        t = MathHelper.clamp(t, 0F, 1F);
        int a = (base >>> 24) & 0xFF;
        int r = (int) (((base >> 16) & 0xFF) + (((overlay >> 16) & 0xFF) - ((base >> 16) & 0xFF)) * t);
        int g = (int) (((base >> 8) & 0xFF) + (((overlay >> 8) & 0xFF) - ((base >> 8) & 0xFF)) * t);
        int b = (int) ((base & 0xFF) + ((overlay & 0xFF) - (base & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampAlpha(float value) {
        return (int) MathHelper.clamp(value, 0F, 255F);
    }

    private static int argb(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
