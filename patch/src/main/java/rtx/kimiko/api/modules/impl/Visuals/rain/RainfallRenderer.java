package rtx.kimiko.api.modules.impl.Visuals.rain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;
import rtx.kimiko.utils.render.others.pipeline.ClientPipelines;

/**
 * Кастомный дождь Ambience.
 *
 * PERF (внешний вид тот же):
 *  - sin/cos для колец ряби берутся из готовой таблицы (было ~20к вызовов Math.sin/cos за кадр);
 *  - капли, рябь и брызги за спиной камеры не отправляются на GPU (их и так не видно);
 *  - кольца/пятна/брызги рисуются только если реально видимы по альфе.
 */
public final class RainfallRenderer {
    private static final int MAX_DROPS = 1600;
    private static final float RADIUS = 16.0f;
    private static final int RIPPLE_CAP = 340;
    private static final int RAIN_RIPPLE_CAP = 300;
    private static final double RIPPLE_RADIUS_SQ = 196.0;
    private static final float RIPPLE_VIEW_SQ = 676.0f;
    private static final float SPLASH_LIFE = 0.22f;
    private static final int SHARD_CAP = 600;
    private static final double SHARD_RADIUS_SQ = 121.0;
    private static final float SHARD_VIEW_SQ = 484.0f;
    private static final double FOOT_RANGE_SQ = 1600.0;
    private static final double FOOT_STEP_LENGTH = 0.72;
    private static final double FOOT_STEP_SNEAK = 0.45;
    private static final double FOOT_SIDE_OFFSET = 0.11875;
    private static final int RING_SEGMENTS = 18;
    private static final float SEGMENT_ANGLE = 0.34906584f;
    /** Запас по «за спиной», в блоках: больше самого крупного объекта (капля ~0.9, рябь ~0.6). */
    private static final float BEHIND_MARGIN = -1.5f;
    private static final int RIPPLE_EDGE = 0xD4E2F4;

    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; ++i) {
            double a = i * (double) SEGMENT_ANGLE;
            RING_COS[i] = (float) Math.cos(a);
            RING_SIN[i] = (float) Math.sin(a);
        }
        RING_COS[RING_SEGMENTS] = 1.0f;
        RING_SIN[RING_SEGMENTS] = 0.0f;
    }

    private final ArrayList<Drop> drops = new ArrayList<>();
    private final ArrayDeque<Ripple> ripples = new ArrayDeque<>();
    private final ArrayDeque<Ripple> ripplePool = new ArrayDeque<>();
    private final ArrayDeque<Shard> shards = new ArrayDeque<>();
    private final ArrayDeque<Shard> shardPool = new ArrayDeque<>();
    private final HashMap<UUID, FootState> footStates = new HashMap<>();
    private final Random random = new Random();

    public void clear() {
        this.drops.clear();
        this.ripples.clear();
        this.shards.clear();
        this.footStates.clear();
    }

    public void update(@NotNull ClientWorld level, @NotNull Vec3d camPos, float dt, int count, float speedMul, float wetLevel) {
        int target = Math.min(count, MAX_DROPS);
        while (this.drops.size() < target) {
            this.drops.add(new Drop());
        }
        while (this.drops.size() > target) {
            this.drops.remove(this.drops.size() - 1);
        }
        for (int idx = 0, n = this.drops.size(); idx < n; ++idx) {
            Drop drop = this.drops.get(idx);
            if (!drop.alive) {
                this.respawn(drop, level, camPos, true);
                continue;
            }
            drop.vx = drop.driftX;
            drop.vz = drop.driftZ;
            drop.vy = -drop.speed * speedMul;
            drop.x += drop.vx * dt;
            drop.y += drop.vy * dt;
            drop.z += drop.vz * dt;
            double dxc = drop.x - camPos.x;
            double dzc = drop.z - camPos.z;
            double horizSq = dxc * dxc + dzc * dzc;
            if (drop.y < drop.groundY) {
                if (horizSq < RIPPLE_RADIUS_SQ && this.ripples.size() < RAIN_RIPPLE_CAP) {
                    this.spawnRipple(drop.x, drop.groundY + 0.02, drop.z,
                            0.34f + this.random.nextFloat() * 0.26f,
                            0.7f + this.random.nextFloat() * 0.35f,
                            0.65f + this.random.nextFloat() * 0.35f, true, 0);
                }
                if (horizSq < SHARD_RADIUS_SQ) {
                    this.spawnShards(drop.x, drop.groundY + 0.02, drop.z, 3);
                }
                this.respawn(drop, level, camPos, false);
                continue;
            }
            if (drop.y > Math.max(camPos.y, drop.groundY) + 18.0 + 12.0 || horizSq > 400.0) {
                this.respawn(drop, level, camPos, false);
            }
        }
        if (wetLevel > 0.12f) {
            this.updateFootsteps(level, camPos, dt);
        } else if (!this.footStates.isEmpty()) {
            this.footStates.clear();
        }
        Iterator<Ripple> rippleIt = this.ripples.iterator();
        while (rippleIt.hasNext()) {
            Ripple ripple = rippleIt.next();
            ripple.age += dt;
            if (ripple.age < ripple.life) {
                continue;
            }
            rippleIt.remove();
            if (this.ripplePool.size() < RIPPLE_CAP) {
                this.ripplePool.addLast(ripple);
            }
        }
        Iterator<Shard> shardIt = this.shards.iterator();
        while (shardIt.hasNext()) {
            Shard shard = shardIt.next();
            shard.age += dt;
            shard.vy -= 11.5f * dt;
            shard.x += shard.vx * dt;
            shard.y += shard.vy * dt;
            shard.z += shard.vz * dt;
            if (shard.age < shard.life && shard.y > shard.floorY) {
                continue;
            }
            shardIt.remove();
            if (this.shardPool.size() < SHARD_CAP) {
                this.shardPool.addLast(shard);
            }
        }
    }

    private void spawnShards(double x, double y, double z, int count) {
        for (int i = 0; i < count; ++i) {
            if (this.shards.size() >= SHARD_CAP) {
                return;
            }
            Shard shard = this.shardPool.pollFirst();
            if (shard == null) {
                shard = new Shard();
            }
            float angle = this.random.nextFloat() * 6.2832f;
            float horiz = 0.5f + this.random.nextFloat() * 1.5f;
            shard.x = x;
            shard.y = y + 0.015;
            shard.z = z;
            shard.vx = (float) Math.cos(angle) * horiz;
            shard.vz = (float) Math.sin(angle) * horiz;
            shard.vy = 1.4f + this.random.nextFloat() * 2.0f;
            shard.age = 0.0f;
            shard.life = 0.22f + this.random.nextFloat() * 0.16f;
            shard.width = 0.006f + this.random.nextFloat() * 0.006f;
            shard.floorY = y - 0.03;
            this.shards.addLast(shard);
        }
    }

    private void updateFootsteps(ClientWorld level, Vec3d camPos, float dt) {
        for (AbstractClientPlayerEntity player : level.getPlayers()) {
            Vec3d pos = player.getEntityPos();
            if (pos.squaredDistanceTo(camPos) > FOOT_RANGE_SQ) {
                continue;
            }
            FootState state = this.footStates.computeIfAbsent(player.getUuid(), k -> new FootState());
            Vec3d prev = state.prevPos;
            state.prevPos = pos;
            if (prev == null) {
                state.wasOnGround = player.isOnGround();
                continue;
            }
            double dx = pos.x - prev.x;
            double dz = pos.z - prev.z;
            double dy = pos.y - prev.y;
            boolean onGround = player.isOnGround();
            if (onGround) {
                if (!state.wasOnGround && state.maxFall > 0.22) {
                    float impact = Math.min(1.5f, 0.35f + (float) state.maxFall * 1.6f);
                    this.landingSplash(level, player, pos, impact);
                    state.accum = 0.0;
                }
                state.maxFall = 0.0;
            } else if (dy < -1.0E-4) {
                state.maxFall = Math.max(state.maxFall, -dy);
            }
            state.wasOnGround = onGround;
            if (!onGround) {
                continue;
            }
            double step = Math.sqrt(dx * dx + dz * dz);
            if (step > 2.0) {
                state.accum = 0.0;
                continue;
            }
            boolean sneaking = player.isInSneakingPose();
            state.accum += step;
            if (state.accum < (sneaking ? FOOT_STEP_SNEAK : FOOT_STEP_LENGTH)) {
                continue;
            }
            state.accum = 0.0;
            if (step < 1.0E-4 || player.isTouchingWater() || !this.onExposedSurface(level, pos)) {
                continue;
            }
            double bodyYaw = Math.toRadians(player.bodyYaw);
            double rightX = Math.cos(bodyYaw);
            double rightZ = Math.sin(bodyYaw);
            state.leftFoot = !state.leftFoot;
            double sideMul = state.leftFoot ? 1.0 : -1.0;
            double fx = pos.x + rightX * FOOT_SIDE_OFFSET * sideMul;
            double fz = pos.z + rightZ * FOOT_SIDE_OFFSET * sideMul;
            boolean sprinting = player.isSprinting();
            float size = sneaking ? 0.085f : (sprinting ? 0.14f : 0.11f);
            float strength = sneaking ? 0.4f : (sprinting ? 1.05f : 0.75f);
            this.spawnRipple(fx, pos.y + 0.02, fz, size, 0.6f, strength, false, 1);
            if (sprinting) {
                this.spawnShards(fx, pos.y + 0.02, fz, 1);
            }
        }
        if (this.footStates.size() > 64) {
            this.footStates.clear();
        }
    }

    private void landingSplash(ClientWorld level, PlayerEntity player, Vec3d pos, float impact) {
        if (player.isTouchingWater() || !this.onExposedSurface(level, pos)) {
            return;
        }
        double bodyYaw = Math.toRadians(player.bodyYaw);
        double rightX = Math.cos(bodyYaw);
        double rightZ = Math.sin(bodyYaw);
        for (int side = -1; side <= 1; side += 2) {
            this.spawnRipple(pos.x + rightX * FOOT_SIDE_OFFSET * side, pos.y + 0.02, pos.z + rightZ * FOOT_SIDE_OFFSET * side,
                    0.12f + 0.06f * impact, 0.7f, impact, false, 1);
        }
        this.spawnShards(pos.x, pos.y + 0.02, pos.z, (int) (2.0f + impact * 3.0f));
    }

    private boolean onExposedSurface(ClientWorld level, Vec3d pos) {
        int surface = level.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) pos.x, (int) pos.z);
        return pos.y >= surface - 1.5 && pos.y <= surface + 0.6;
    }

    private void spawnRipple(double x, double y, double z, float maxR, float life, float strength, boolean splash, int shape) {
        while (this.ripples.size() >= RIPPLE_CAP) {
            this.ripples.removeFirst();
        }
        Ripple ripple = this.ripplePool.pollFirst();
        if (ripple == null) {
            ripple = new Ripple();
        }
        ripple.x = x;
        ripple.y = y;
        ripple.z = z;
        ripple.age = 0.0f;
        ripple.life = life;
        ripple.maxR = maxR;
        ripple.strength = strength;
        ripple.splash = splash;
        ripple.shape = shape;
        this.ripples.addLast(ripple);
    }

    private void respawn(Drop drop, ClientWorld level, Vec3d camPos, boolean initial) {
        float angle = this.random.nextFloat() * ((float) Math.PI * 2);
        float radius = (float) Math.sqrt(this.random.nextFloat()) * RADIUS;
        drop.x = camPos.x + (float) Math.cos(angle) * radius;
        drop.z = camPos.z + (float) Math.sin(angle) * radius;
        drop.speed = 14.0f + this.random.nextFloat() * 16.0f;
        drop.len = drop.speed * 0.026f + this.random.nextFloat() * 0.08f;
        drop.width = 0.0035f + this.random.nextFloat() * 0.0045f;
        drop.driftX = (this.random.nextFloat() - 0.5f) * 0.12f;
        drop.driftZ = (this.random.nextFloat() - 0.5f) * 0.12f;
        drop.groundY = level.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) drop.x, (int) drop.z);
        double base = Math.max(camPos.y, drop.groundY);
        drop.y = base + (initial ? this.random.nextFloat() * 18.0f : 5.0f + this.random.nextFloat() * 13.0f);
        drop.vx = 0.0f;
        drop.vy = -drop.speed;
        drop.vz = 0.0f;
        drop.alive = true;
    }

    @Nullable
    private static Vector3fc cameraForward() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.gameRenderer == null) {
                return null;
            }
            Camera camera = mc.gameRenderer.getCamera();
            return camera != null ? camera.getHorizontalPlane() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void render(@NotNull MatrixStack stack, @NotNull VertexConsumerProvider.Immediate provider, @NotNull Vec3d camPos,
                       float dropAlpha, float rippleAlpha, float footAlpha) {
        if (this.drops.isEmpty() && this.ripples.isEmpty() && this.shards.isEmpty()) {
            return;
        }
        Vector3fc forward = cameraForward();
        boolean cull = forward != null;
        float fx = cull ? forward.x() : 0.0f;
        float fy = cull ? forward.y() : 0.0f;
        float fz = cull ? forward.z() : 0.0f;

        RenderLayer renderType = ClientPipelines.WORLD_PARTICLES_COLOR;
        VertexConsumer consumer = provider.getBuffer(renderType);
        stack.push();
        MatrixStack.Entry pose = stack.peek();
        if (dropAlpha > 0.01f) {
            for (int idx = 0, n = this.drops.size(); idx < n; ++idx) {
                Drop drop = this.drops.get(idx);
                if (!drop.alive) {
                    continue;
                }
                float rx = (float) (drop.x - camPos.x);
                float ry = (float) (drop.y - camPos.y);
                float rz = (float) (drop.z - camPos.z);
                if (cull && rx * fx + ry * fy + rz * fz < BEHIND_MARGIN) {
                    continue;
                }
                float distSq = rx * rx + ry * ry + rz * rz;
                if (distSq < 0.36f) {
                    continue;
                }
                float horizSq = rx * rx + rz * rz;
                if (horizSq >= RADIUS * RADIUS) {
                    continue; // fade по горизонтали = 0 после 16 блоков
                }
                float dist = (float) Math.sqrt(distSq);
                float horiz = (float) Math.sqrt(horizSq);
                float fade = (1.0f - smoothstep(13.0f, 16.0f, horiz)) * smoothstep(0.7f, 1.6f, dist);
                if (fade <= 0.02f) {
                    continue;
                }
                float vlen = (float) Math.sqrt(drop.vx * drop.vx + drop.vy * drop.vy + drop.vz * drop.vz);
                if (vlen < 0.001f) {
                    continue;
                }
                float inv = drop.len / vlen;
                float tx = -drop.vx * inv;
                float ty = -drop.vy * inv;
                float tz = -drop.vz * inv;
                float sx = ty * rz - tz * ry;
                float sy = tz * rx - tx * rz;
                float sz = tx * ry - ty * rx;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0E-4f) {
                    continue;
                }
                float half = drop.width / sl;
                sx *= half;
                sy *= half;
                sz *= half;
                int a = clamp255((int) (dropAlpha * fade * 150.0f));
                int aTop = (int) (a * 0.12f);
                int bottom = a << 24 | 0xBFCEE6;
                int top = aTop << 24 | 0xBFCEE6;
                consumer.vertex(pose, rx - sx, ry - sy, rz - sz).color(bottom);
                consumer.vertex(pose, rx + sx, ry + sy, rz + sz).color(bottom);
                consumer.vertex(pose, rx + sx + tx, ry + sy + ty, rz + sz + tz).color(top);
                consumer.vertex(pose, rx - sx + tx, ry - sy + ty, rz - sz + tz).color(top);
            }
        }
        if (rippleAlpha > 0.01f || footAlpha > 0.01f) {
            for (Ripple ripple : this.ripples) {
                float cx = (float) (ripple.x - camPos.x);
                float cy = (float) (ripple.y - camPos.y);
                float cz = (float) (ripple.z - camPos.z);
                if (cull && cx * fx + cy * fy + cz * fz < BEHIND_MARGIN) {
                    continue;
                }
                this.renderRipple(consumer, pose, ripple, cx, cy, cz, ripple.shape == 1 ? footAlpha : rippleAlpha);
            }
        }
        if (dropAlpha > 0.01f) {
            for (Shard shard : this.shards) {
                float rx = (float) (shard.x - camPos.x);
                float ry = (float) (shard.y - camPos.y);
                float rz = (float) (shard.z - camPos.z);
                if (cull && rx * fx + ry * fy + rz * fz < BEHIND_MARGIN) {
                    continue;
                }
                this.renderShard(consumer, pose, shard, rx, ry, rz, dropAlpha);
            }
        }
        stack.pop();
        provider.draw(renderType);
    }

    private void renderShard(VertexConsumer consumer, MatrixStack.Entry pose, Shard shard, float rx, float ry, float rz, float alphaMul) {
        float distSq = rx * rx + ry * ry + rz * rz;
        if (distSq > SHARD_VIEW_SQ || distSq < 0.09f) {
            return;
        }
        float t = clamp01(shard.age / shard.life);
        float fade = 1.0f - t;
        int a = clamp255((int) (alphaMul * fade * fade * 235.0f));
        if (a <= 3) {
            return;
        }
        float vlen = (float) Math.sqrt(shard.vx * shard.vx + shard.vy * shard.vy + shard.vz * shard.vz);
        if (vlen < 0.001f) {
            return;
        }
        float len = 0.035f + vlen * 0.022f;
        float inv = len / vlen;
        float tx = shard.vx * inv;
        float ty = shard.vy * inv;
        float tz = shard.vz * inv;
        float sx = ty * rz - tz * ry;
        float sy = tz * rx - tx * rz;
        float sz = tx * ry - ty * rx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-4f) {
            return;
        }
        float half = shard.width / sl;
        sx *= half;
        sy *= half;
        sz *= half;
        int head = a << 24 | 0xDCE8F8;
        int tail = a / 4 << 24 | 0xDCE8F8;
        consumer.vertex(pose, rx - sx, ry - sy, rz - sz).color(tail);
        consumer.vertex(pose, rx + sx, ry + sy, rz + sz).color(tail);
        consumer.vertex(pose, rx + sx + tx, ry + sy + ty, rz + sz + tz).color(head);
        consumer.vertex(pose, rx - sx + tx, ry - sy + ty, rz - sz + tz).color(head);
    }

    private void renderRipple(VertexConsumer consumer, MatrixStack.Entry pose, Ripple ripple, float cx, float cy, float cz, float alphaMul) {
        float distSq = cx * cx + cy * cy + cz * cz;
        if (distSq > RIPPLE_VIEW_SQ) {
            return;
        }
        float t = clamp01(ripple.age / ripple.life);
        float ease = 1.0f - (1.0f - t) * (1.0f - t);
        float fadeT = 1.0f - t;
        float alpha = fadeT * fadeT * ripple.strength * alphaMul * 175.0f;
        if (alpha < 3.0f) {
            return;
        }
        if (ripple.shape == 1) {
            float radius = ripple.maxR * (0.3f + 0.7f * ease);
            float width = ripple.maxR * (0.28f - 0.1f * t);
            ring(consumer, pose, cx, cy, cz, radius, width, alpha);
            return;
        }
        float radius = ripple.maxR * (0.12f + 0.88f * ease);
        float width = ripple.maxR * (0.24f - 0.1f * t);
        ring(consumer, pose, cx, cy, cz, radius, width, alpha);
        if (t > 0.22f) {
            ring(consumer, pose, cx, cy, cz, radius * 0.55f, width * 0.8f, alpha * 0.45f);
        }
        int spotAlpha = clamp255((int) ((1.0f - t * 0.85f) * ripple.strength * alphaMul * 34.0f));
        if (spotAlpha > 2) {
            float spotR = radius + width * 1.6f;
            int spotColor = spotAlpha << 24 | 0xD4E2F4;
            for (int i = 1; i <= RING_SEGMENTS; ++i) {
                float c = RING_COS[i];
                float s = RING_SIN[i];
                float pc = RING_COS[i - 1];
                float ps = RING_SIN[i - 1];
                consumer.vertex(pose, cx, cy, cz).color(spotColor);
                consumer.vertex(pose, cx, cy, cz).color(spotColor);
                consumer.vertex(pose, cx + c * spotR, cy, cz + s * spotR).color(RIPPLE_EDGE);
                consumer.vertex(pose, cx + pc * spotR, cy, cz + ps * spotR).color(RIPPLE_EDGE);
            }
        }
        if (ripple.splash && ripple.age < SPLASH_LIFE) {
            float st = ripple.age / SPLASH_LIFE;
            float h = 0.17f * (float) Math.sin(st * 3.1416f);
            float w = 0.028f;
            int sa = clamp255((int) ((1.0f - st) * ripple.strength * alphaMul * 200.0f));
            int col = sa << 24 | 0xDCE8F8;
            int colTop = sa / 4 << 24 | 0xDCE8F8;
            consumer.vertex(pose, cx - w, cy, cz).color(col);
            consumer.vertex(pose, cx + w, cy, cz).color(col);
            consumer.vertex(pose, cx + w, cy + h, cz).color(colTop);
            consumer.vertex(pose, cx - w, cy + h, cz).color(colTop);
            consumer.vertex(pose, cx, cy, cz - w).color(col);
            consumer.vertex(pose, cx, cy, cz + w).color(col);
            consumer.vertex(pose, cx, cy + h, cz + w).color(colTop);
            consumer.vertex(pose, cx, cy + h, cz - w).color(colTop);
        }
    }

    private static void ring(VertexConsumer consumer, MatrixStack.Entry pose, float cx, float cy, float cz, float radius, float width, float alpha) {
        int mid = clamp255((int) alpha);
        if (mid <= 0) {
            return;
        }
        int midColor = mid << 24 | 0xD4E2F4;
        float inner = Math.max(0.01f, radius - width);
        float outer = radius + width;
        for (int i = 1; i <= RING_SEGMENTS; ++i) {
            float c = RING_COS[i];
            float s = RING_SIN[i];
            float pc = RING_COS[i - 1];
            float ps = RING_SIN[i - 1];
            consumer.vertex(pose, cx + pc * inner, cy, cz + ps * inner).color(RIPPLE_EDGE);
            consumer.vertex(pose, cx + c * inner, cy, cz + s * inner).color(RIPPLE_EDGE);
            consumer.vertex(pose, cx + c * radius, cy, cz + s * radius).color(midColor);
            consumer.vertex(pose, cx + pc * radius, cy, cz + ps * radius).color(midColor);
            consumer.vertex(pose, cx + pc * radius, cy, cz + ps * radius).color(midColor);
            consumer.vertex(pose, cx + c * radius, cy, cz + s * radius).color(midColor);
            consumer.vertex(pose, cx + c * outer, cy, cz + s * outer).color(RIPPLE_EDGE);
            consumer.vertex(pose, cx + pc * outer, cy, cz + ps * outer).color(RIPPLE_EDGE);
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static final class Drop {
        double x;
        double y;
        double z;
        float speed = 20.0f;
        float len = 0.5f;
        float width = 0.015f;
        float driftX;
        float driftZ;
        double groundY = Double.NEGATIVE_INFINITY;
        boolean alive;
        float vx;
        float vy = -20.0f;
        float vz;
    }

    private static final class Ripple {
        double x;
        double y;
        double z;
        float age;
        float life = 0.85f;
        float maxR = 0.45f;
        float strength = 1.0f;
        boolean splash = true;
        int shape;
    }

    private static final class FootState {
        @Nullable
        Vec3d prevPos;
        double accum;
        boolean leftFoot;
        boolean wasOnGround = true;
        double maxFall;
    }

    private static final class Shard {
        double x;
        double y;
        double z;
        float vx;
        float vy;
        float vz;
        float age;
        float life = 0.3f;
        float width = 0.01f;
        double floorY;
    }
}
