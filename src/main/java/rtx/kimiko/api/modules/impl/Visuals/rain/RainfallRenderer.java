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
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;
import rtx.kimiko.api.modules.impl.Visuals.Ambience;
import rtx.kimiko.utils.render.others.pipeline.ClientPipelines;

/**
 * Кастомный дождь Ambience, v2 ("AAA").
 *
 * Реализм:
 *  - ветер с порывами (берётся из настроек ветра Ambience, в грозу сильнее): косые струи,
 *    лёгкие капли сносит сильнее тяжёлых, спавн смещён против ветра, чтобы дождь шёл вокруг игрока;
 *  - motion blur относительно камеры: на бегу струи наклоняются навстречу, как в жизни/AAA;
 *  - мягкие струи: яркая сердцевина и прозрачные края вместо плоской полоски;
 *  - освещение: дождь берёт цвет и яркость неба (ночью тёмный, на закате тёплый, не светится),
 *    а у факелов/фонарей подсвечивается тёплым светом;
 *  - второй дальний слой (14..34 блока) из длинных бледных струй: даёт глубину и «пелену»;
 *  - удары о поверхность: по воде кольца ряби + отскок капли, по земле корона брызг
 *    и короткий всплеск (раньше везде были одинаковые круги), рябь лежит ровно на воде.
 *
 * FPS (дешевле или на уровне старого):
 *  - LOD колец: 18/12/8 сегментов по дистанции (вблизи как было, вдали разницы не видно);
 *  - на суше вместо двух колец + заливки одно маленькое кольцо;
 *  - одна выборка heightmap/света/жидкости на спавн капли, ноль аллокаций за кадр;
 *  - всё за спиной камеры и невидимое по альфе на GPU не уходит.
 */
public final class RainfallRenderer {
    private static final int MAX_DROPS = 1600;
    private static final int MAX_FAR_DROPS = 480;
    private static final float FAR_FRACTION = 0.3f;
    private static final float RADIUS = 16.0f;
    private static final float FAR_INNER = 14.0f;
    private static final float FAR_OUTER = 34.0f;
    private static final float EXPOSURE = 0.026f;
    private static final int RIPPLE_CAP = 340;
    private static final int RAIN_RIPPLE_CAP = 300;
    private static final double RIPPLE_RADIUS_SQ = 196.0;
    private static final float RIPPLE_VIEW_SQ = 676.0f;
    private static final float SPLASH_LIFE = 0.22f;
    private static final int SHARD_CAP = 700;
    private static final double SHARD_RADIUS_SQ = 121.0;
    private static final float SHARD_VIEW_SQ = 484.0f;
    private static final double FOOT_RANGE_SQ = 1600.0;
    private static final double FOOT_STEP_LENGTH = 0.72;
    private static final double FOOT_STEP_SNEAK = 0.45;
    private static final double FOOT_SIDE_OFFSET = 0.11875;
    private static final float BEHIND_MARGIN = -1.5f;
    private static final float WATER_SURFACE_OFFSET = -0.1f;

    private static final int DROP_RGB = 0xBFCEE6;
    private static final int RIPPLE_RGB = 0xD4E2F4;
    private static final int SHARD_RGB = 0xDCE8F8;

    private static final int SHAPE_RAIN = 0;
    private static final int SHAPE_FOOT = 1;
    private static final int SHAPE_GROUND = 2;

    /** LOD-таблицы для колец: 18 (вблизи, как раньше), 12, 8 сегментов. */
    private static final float[][] LOD_COS = new float[3][];
    private static final float[][] LOD_SIN = new float[3][];
    private static final int[] LOD_SEGMENTS = {18, 12, 8};

    static {
        for (int l = 0; l < 3; ++l) {
            int seg = LOD_SEGMENTS[l];
            LOD_COS[l] = new float[seg + 1];
            LOD_SIN[l] = new float[seg + 1];
            for (int i = 0; i <= seg; ++i) {
                double a = i * (Math.PI * 2.0 / seg);
                LOD_COS[l][i] = (float) Math.cos(a);
                LOD_SIN[l][i] = (float) Math.sin(a);
            }
            LOD_COS[l][seg] = 1.0f;
            LOD_SIN[l][seg] = 0.0f;
        }
    }

    private final ArrayList<Drop> drops = new ArrayList<>();
    private final ArrayDeque<Ripple> ripples = new ArrayDeque<>();
    private final ArrayDeque<Ripple> ripplePool = new ArrayDeque<>();
    private final ArrayDeque<Shard> shards = new ArrayDeque<>();
    private final ArrayDeque<Shard> shardPool = new ArrayDeque<>();
    private final HashMap<UUID, FootState> footStates = new HashMap<>();
    private final Random random = new Random();
    private final BlockPos.Mutable mpos = new BlockPos.Mutable();

    private float time;
    private float windX;
    private float windZ;
    private float camVx;
    private float camVy;
    private float camVz;
    private double prevCamX;
    private double prevCamY;
    private double prevCamZ;
    private boolean hasPrevCam;
    private float lastSpeedMul = 1.0f;

    // Освещение, считается раз в кадр.
    private float ambient = 1.0f;
    private float tintR = 1.0f;
    private float tintG = 1.0f;
    private float tintB = 1.0f;

    public void clear() {
        this.drops.clear();
        this.ripples.clear();
        this.shards.clear();
        this.footStates.clear();
        this.hasPrevCam = false;
        this.camVx = this.camVy = this.camVz = 0.0f;
    }

    public void update(@NotNull ClientWorld level, @NotNull Vec3d camPos, float dt, int count, float speedMul, float wetLevel) {
        this.time += dt;
        this.lastSpeedMul = speedMul;
        this.updateCameraVelocity(camPos, dt);
        this.updateWind(level, dt);

        int nearTarget = Math.min(count, MAX_DROPS);
        int farTarget = Math.min((int) (nearTarget * FAR_FRACTION), MAX_FAR_DROPS);
        int total = nearTarget + farTarget;
        while (this.drops.size() < total) {
            this.drops.add(new Drop());
        }
        while (this.drops.size() > total) {
            this.drops.remove(this.drops.size() - 1);
        }
        for (int idx = 0, n = this.drops.size(); idx < n; ++idx) {
            Drop drop = this.drops.get(idx);
            boolean far = idx >= nearTarget;
            if (drop.far != far) {
                drop.far = far;
                drop.alive = false;
            }
            if (!drop.alive) {
                this.respawn(drop, level, camPos, true);
                continue;
            }
            drop.vx = this.windX * drop.windMul + drop.driftX;
            drop.vz = this.windZ * drop.windMul + drop.driftZ;
            drop.vy = -drop.speed * speedMul;
            drop.x += drop.vx * dt;
            drop.y += drop.vy * dt;
            drop.z += drop.vz * dt;
            double dxc = drop.x - camPos.x;
            double dzc = drop.z - camPos.z;
            double horizSq = dxc * dxc + dzc * dzc;
            if (drop.y < drop.groundY) {
                if (!drop.far) {
                    this.impact(drop, horizSq);
                }
                this.respawn(drop, level, camPos, false);
                continue;
            }
            float lim = drop.far ? FAR_OUTER + 6.0f : RADIUS + 6.0f;
            if (drop.y > Math.max(camPos.y, drop.groundY) + 30.0 || horizSq > lim * lim) {
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

    private void updateCameraVelocity(Vec3d camPos, float dt) {
        if (this.hasPrevCam && dt > 1.0E-4f) {
            double dx = camPos.x - this.prevCamX;
            double dy = camPos.y - this.prevCamY;
            double dz = camPos.z - this.prevCamZ;
            if (dx * dx + dy * dy + dz * dz > 25.0) {
                this.camVx = this.camVy = this.camVz = 0.0f; // телепорт
            } else {
                float k = Math.min(1.0f, dt * 12.0f);
                this.camVx += ((float) (dx / dt) - this.camVx) * k;
                this.camVy += ((float) (dy / dt) - this.camVy) * k;
                this.camVz += ((float) (dz / dt) - this.camVz) * k;
                this.camVx = MathHelper.clamp(this.camVx, -30.0f, 30.0f);
                this.camVy = MathHelper.clamp(this.camVy, -30.0f, 30.0f);
                this.camVz = MathHelper.clamp(this.camVz, -30.0f, 30.0f);
            }
        }
        this.prevCamX = camPos.x;
        this.prevCamY = camPos.y;
        this.prevCamZ = camPos.z;
        this.hasPrevCam = true;
    }

    private void updateWind(ClientWorld level, float dt) {
        float strength = 0.3f;
        boolean gusts = true;
        try {
            Ambience ambience = Ambience.Companion.getInstance();
            if (ambience != null && ambience.isWindActive()) {
                strength = ambience.getWindSpeed();
                gusts = ambience.hasWindGusts();
            }
        } catch (Throwable ignored) {
        }
        strength *= 1.0f + 0.8f * level.getThunderGradient(1.0f);
        float t = this.time;
        float gust = gusts ? 0.5f + 0.5f * (float) (Math.sin(t * 0.71) * Math.sin(t * 0.23 + 1.3)) : 0.0f;
        float speed = strength * 4.5f * (1.0f + 0.6f * gust);
        float angle = 0.8f + 0.4f * (float) Math.sin(t * 0.037) + 0.15f * (float) Math.sin(t * 0.19);
        float tx = (float) Math.cos(angle) * speed;
        float tz = (float) Math.sin(angle) * speed;
        float k = Math.min(1.0f, dt * 1.5f);
        this.windX += (tx - this.windX) * k;
        this.windZ += (tz - this.windZ) * k;
    }

    private void impact(Drop drop, double horizSq) {
        if (horizSq >= RIPPLE_RADIUS_SQ) {
            return;
        }
        boolean shardsVisible = horizSq < SHARD_RADIUS_SQ;
        if (drop.water) {
            double sy = drop.groundY + WATER_SURFACE_OFFSET;
            if (this.ripples.size() < RAIN_RIPPLE_CAP) {
                this.spawnRipple(drop.x, sy, drop.z,
                        0.34f + this.random.nextFloat() * 0.26f,
                        0.7f + this.random.nextFloat() * 0.35f,
                        0.65f + this.random.nextFloat() * 0.35f, true, SHAPE_RAIN, drop.light);
            }
            if (shardsVisible) {
                this.spawnShards(drop.x, sy, drop.z, 1 + this.random.nextInt(2), 0.25f, 0.6f, 1.8f, 1.4f, drop.light);
            }
            return;
        }
        double sy = drop.groundY + 0.02;
        if (this.ripples.size() < RAIN_RIPPLE_CAP) {
            this.spawnRipple(drop.x, sy, drop.z,
                    0.12f + this.random.nextFloat() * 0.08f,
                    0.32f + this.random.nextFloat() * 0.12f,
                    0.55f + this.random.nextFloat() * 0.35f, true, SHAPE_GROUND, drop.light);
        }
        if (shardsVisible) {
            // корона брызг: быстрые короткие капельки во все стороны
            this.spawnShards(drop.x, sy, drop.z, 3 + this.random.nextInt(3), 0.7f, 1.6f, 0.9f, 1.3f, drop.light);
        }
    }

    private void spawnShards(double x, double y, double z, int count, float hMin, float hRange, float vMin, float vRange, float light) {
        for (int i = 0; i < count; ++i) {
            if (this.shards.size() >= SHARD_CAP) {
                return;
            }
            Shard shard = this.shardPool.pollFirst();
            if (shard == null) {
                shard = new Shard();
            }
            float angle = this.random.nextFloat() * 6.2832f;
            float horiz = hMin + this.random.nextFloat() * hRange;
            shard.x = x;
            shard.y = y + 0.015;
            shard.z = z;
            shard.vx = (float) Math.cos(angle) * horiz + this.windX * 0.15f;
            shard.vz = (float) Math.sin(angle) * horiz + this.windZ * 0.15f;
            shard.vy = vMin + this.random.nextFloat() * vRange;
            shard.age = 0.0f;
            shard.life = 0.2f + this.random.nextFloat() * 0.16f;
            shard.width = 0.006f + this.random.nextFloat() * 0.006f;
            shard.floorY = y - 0.03;
            shard.light = light;
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
            float light = this.blockLight(level, pos.x, pos.y + 0.5, pos.z);
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
            this.spawnRipple(fx, pos.y + 0.02, fz, size, 0.6f, strength, false, SHAPE_FOOT, light);
            if (sprinting) {
                this.spawnShards(fx, pos.y + 0.02, fz, 2, 0.5f, 1.2f, 1.2f, 1.6f, light);
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
        float light = this.blockLight(level, pos.x, pos.y + 0.5, pos.z);
        double bodyYaw = Math.toRadians(player.bodyYaw);
        double rightX = Math.cos(bodyYaw);
        double rightZ = Math.sin(bodyYaw);
        for (int side = -1; side <= 1; side += 2) {
            this.spawnRipple(pos.x + rightX * FOOT_SIDE_OFFSET * side, pos.y + 0.02, pos.z + rightZ * FOOT_SIDE_OFFSET * side,
                    0.12f + 0.06f * impact, 0.7f, impact, false, SHAPE_FOOT, light);
        }
        this.spawnShards(pos.x, pos.y + 0.02, pos.z, (int) (3.0f + impact * 4.0f), 0.5f, 1.5f, 1.4f, 2.0f, light);
    }

    private boolean onExposedSurface(ClientWorld level, Vec3d pos) {
        int surface = level.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(pos.x), MathHelper.floor(pos.z));
        return pos.y >= surface - 1.5 && pos.y <= surface + 0.6;
    }

    private float blockLight(ClientWorld level, double x, double y, double z) {
        try {
            this.mpos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
            return level.getLightLevel(LightType.BLOCK, this.mpos) / 15.0f;
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private void spawnRipple(double x, double y, double z, float maxR, float life, float strength, boolean splash, int shape, float light) {
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
        ripple.light = light;
        this.ripples.addLast(ripple);
    }

    private void respawn(Drop drop, ClientWorld level, Vec3d camPos, boolean initial) {
        drop.speed = 14.0f + this.random.nextFloat() * 16.0f;
        // крупные быстрые капли ветер сносит меньше
        drop.windMul = 1.15f - (drop.speed - 14.0f) / 16.0f * 0.4f;
        drop.lenExtra = this.random.nextFloat() * 0.08f;
        drop.width = 0.0035f + this.random.nextFloat() * 0.0045f;
        drop.driftX = (this.random.nextFloat() - 0.5f) * 0.12f;
        drop.driftZ = (this.random.nextFloat() - 0.5f) * 0.12f;

        // точка приземления: равномерно по кругу (ближний слой) или кольцу (дальний)
        float angle = this.random.nextFloat() * ((float) Math.PI * 2);
        float radius = drop.far
                ? (float) Math.sqrt(FAR_INNER * FAR_INNER + this.random.nextFloat() * (FAR_OUTER * FAR_OUTER - FAR_INNER * FAR_INNER))
                : (float) Math.sqrt(this.random.nextFloat()) * RADIUS;
        double lx = camPos.x + (float) Math.cos(angle) * radius;
        double lz = camPos.z + (float) Math.sin(angle) * radius;
        int bx = MathHelper.floor(lx);
        int bz = MathHelper.floor(lz);
        int ground = level.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
        drop.groundY = ground;
        if (!drop.far) {
            try {
                this.mpos.set(bx, ground - 1, bz);
                drop.water = level.getFluidState(this.mpos).isIn(FluidTags.WATER);
                this.mpos.set(bx, ground, bz);
                drop.light = level.getLightLevel(LightType.BLOCK, this.mpos) / 15.0f;
            } catch (Throwable ignored) {
                drop.water = false;
                drop.light = 0.0f;
            }
        } else {
            drop.water = false;
            drop.light = 0.0f;
        }

        double base = Math.max(camPos.y, drop.groundY);
        float h = initial ? this.random.nextFloat() * 18.0f : 5.0f + this.random.nextFloat() * 13.0f;
        drop.y = base + h;
        // смещаем спавн против ветра, чтобы капля упала туда, куда целились
        float fallT = h / Math.max(1.0f, drop.speed * this.lastSpeedMul);
        drop.x = lx - (this.windX * drop.windMul + drop.driftX) * fallT;
        drop.z = lz - (this.windZ * drop.windMul + drop.driftZ) * fallT;
        drop.vx = this.windX * drop.windMul;
        drop.vy = -drop.speed;
        drop.vz = this.windZ * drop.windMul;
        drop.alive = true;
    }

    @Nullable
    private static Camera camera() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.gameRenderer == null) {
                return null;
            }
            return mc.gameRenderer.getCamera();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateLighting(@Nullable Camera camera) {
        int sky = 0xFFFFFF;
        if (camera != null) {
            try {
                Object value = camera.getEnvironmentAttributeInterpolator().get(EnvironmentAttributes.SKY_COLOR_VISUAL, 1.0f);
                if (value instanceof Number number) {
                    sky = number.intValue();
                }
            } catch (Throwable ignored) {
            }
        }
        float sr = (sky >> 16 & 0xFF) / 255.0f;
        float sg = (sky >> 8 & 0xFF) / 255.0f;
        float sb = (sky & 0xFF) / 255.0f;
        float lum = 0.299f * sr + 0.587f * sg + 0.114f * sb;
        this.ambient = MathHelper.clamp(0.2f + lum * 1.35f, 0.2f, 1.0f);
        float mx = Math.max(0.001f, Math.max(sr, Math.max(sg, sb)));
        this.tintR = 0.75f + 0.25f * sr / mx;
        this.tintG = 0.75f + 0.25f * sg / mx;
        this.tintB = 0.75f + 0.25f * sb / mx;
    }

    /** Цвет капли/ряби с учётом неба и тёплого блочного света (факелы, фонари). */
    private int lit(int rgb, float light) {
        float l2 = light * light * 0.6f;
        int r = (int) ((rgb >> 16 & 0xFF) * this.ambient * this.tintR + 255.0f * l2);
        int g = (int) ((rgb >> 8 & 0xFF) * this.ambient * this.tintG + 209.0f * l2);
        int b = (int) ((rgb & 0xFF) * this.ambient * this.tintB + 148.0f * l2);
        return clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
    }

    public void render(@NotNull MatrixStack stack, @NotNull VertexConsumerProvider.Immediate provider, @NotNull Vec3d camPos,
                       float dropAlpha, float rippleAlpha, float footAlpha) {
        if (this.drops.isEmpty() && this.ripples.isEmpty() && this.shards.isEmpty()) {
            return;
        }
        Camera camera = camera();
        this.updateLighting(camera);
        Vector3fc forward = null;
        try {
            forward = camera != null ? camera.getHorizontalPlane() : null;
        } catch (Throwable ignored) {
        }
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
                if (drop.alive) {
                    this.renderDrop(consumer, pose, drop, camPos, cull, fx, fy, fz, dropAlpha);
                }
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
                this.renderRipple(consumer, pose, ripple, cx, cy, cz, ripple.shape == SHAPE_FOOT ? footAlpha : rippleAlpha);
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

    private void renderDrop(VertexConsumer consumer, MatrixStack.Entry pose, Drop drop, Vec3d camPos,
                            boolean cull, float fx, float fy, float fz, float dropAlpha) {
        float rx = (float) (drop.x - camPos.x);
        float ry = (float) (drop.y - camPos.y);
        float rz = (float) (drop.z - camPos.z);
        if (cull && rx * fx + ry * fy + rz * fz < BEHIND_MARGIN) {
            return;
        }
        float distSq = rx * rx + ry * ry + rz * rz;
        if (distSq < 0.36f) {
            return;
        }
        float horizSq = rx * rx + rz * rz;
        float fade;
        float alphaBase;
        float widthMul;
        float lenMul;
        if (drop.far) {
            if (horizSq >= FAR_OUTER * FAR_OUTER || horizSq <= FAR_INNER * FAR_INNER) {
                return;
            }
            float horiz = (float) Math.sqrt(horizSq);
            fade = smoothstep(FAR_INNER, 18.0f, horiz) * (1.0f - smoothstep(28.0f, FAR_OUTER, horiz));
            alphaBase = 70.0f;
            widthMul = 2.2f;
            lenMul = 1.35f;
        } else {
            if (horizSq >= RADIUS * RADIUS) {
                return;
            }
            float dist = (float) Math.sqrt(distSq);
            float horiz = (float) Math.sqrt(horizSq);
            fade = (1.0f - smoothstep(13.0f, 16.0f, horiz)) * smoothstep(0.7f, 1.6f, dist);
            alphaBase = 150.0f;
            widthMul = 1.6f; // края прозрачные, поэтому визуально та же толщина, что раньше
            lenMul = 1.0f;
        }
        if (fade <= 0.02f) {
            return;
        }
        // скорость относительно камеры: на бегу струи наклоняются навстречу
        float vx = drop.vx - this.camVx;
        float vy = drop.vy - this.camVy;
        float vz = drop.vz - this.camVz;
        float vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vlen < 0.001f) {
            return;
        }
        float len = Math.min(1.6f, (vlen * EXPOSURE + drop.lenExtra) * lenMul);
        float inv = len / vlen;
        float tx = -vx * inv;
        float ty = -vy * inv;
        float tz = -vz * inv;
        float sx = ty * rz - tz * ry;
        float sy = tz * rx - tx * rz;
        float sz = tx * ry - ty * rx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-4f) {
            return;
        }
        float half = drop.width * widthMul / sl;
        sx *= half;
        sy *= half;
        sz *= half;
        int a = clamp255((int) (dropAlpha * fade * alphaBase));
        if (a <= 2) {
            return;
        }
        int rgb = this.lit(DROP_RGB, drop.light);
        int aTop = (int) (a * 0.12f);
        int bottom = a << 24 | rgb;
        int top = aTop << 24 | rgb;
        // мягкая струя: прозрачные края, яркая сердцевина
        consumer.vertex(pose, rx - sx, ry - sy, rz - sz).color(rgb);
        consumer.vertex(pose, rx, ry, rz).color(bottom);
        consumer.vertex(pose, rx + tx, ry + ty, rz + tz).color(top);
        consumer.vertex(pose, rx - sx + tx, ry - sy + ty, rz - sz + tz).color(rgb);
        consumer.vertex(pose, rx, ry, rz).color(bottom);
        consumer.vertex(pose, rx + sx, ry + sy, rz + sz).color(rgb);
        consumer.vertex(pose, rx + sx + tx, ry + sy + ty, rz + sz + tz).color(rgb);
        consumer.vertex(pose, rx + tx, ry + ty, rz + tz).color(top);
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
        int rgb = this.lit(SHARD_RGB, shard.light);
        int head = a << 24 | rgb;
        int tail = a / 4 << 24 | rgb;
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
        int lod = distSq < 36.0f ? 0 : (distSq < 144.0f ? 1 : 2);
        int rgb = this.lit(RIPPLE_RGB, ripple.light);
        if (ripple.shape == SHAPE_FOOT) {
            float radius = ripple.maxR * (0.3f + 0.7f * ease);
            float width = ripple.maxR * (0.28f - 0.1f * t);
            ring(consumer, pose, lod, cx, cy, cz, radius, width, alpha, rgb);
            return;
        }
        if (ripple.shape == SHAPE_GROUND) {
            // мокрая земля: маленькое быстрое пятно-кольцо + всплеск
            float radius = ripple.maxR * (0.2f + 0.8f * ease);
            float width = ripple.maxR * 0.22f;
            ring(consumer, pose, Math.max(1, lod), cx, cy, cz, radius, width, alpha * 0.55f, rgb);
        } else {
            float radius = ripple.maxR * (0.12f + 0.88f * ease);
            float width = ripple.maxR * (0.24f - 0.1f * t);
            ring(consumer, pose, lod, cx, cy, cz, radius, width, alpha, rgb);
            if (t > 0.22f) {
                ring(consumer, pose, lod, cx, cy, cz, radius * 0.55f, width * 0.8f, alpha * 0.45f, rgb);
            }
            int spotAlpha = clamp255((int) ((1.0f - t * 0.85f) * ripple.strength * alphaMul * 34.0f));
            if (spotAlpha > 2) {
                float spotR = radius + width * 1.6f;
                int spotColor = spotAlpha << 24 | rgb;
                float[] cs = LOD_COS[lod];
                float[] sn = LOD_SIN[lod];
                int seg = LOD_SEGMENTS[lod];
                for (int i = 1; i <= seg; ++i) {
                    consumer.vertex(pose, cx, cy, cz).color(spotColor);
                    consumer.vertex(pose, cx, cy, cz).color(spotColor);
                    consumer.vertex(pose, cx + cs[i] * spotR, cy, cz + sn[i] * spotR).color(rgb);
                    consumer.vertex(pose, cx + cs[i - 1] * spotR, cy, cz + sn[i - 1] * spotR).color(rgb);
                }
            }
        }
        if (ripple.splash && ripple.age < SPLASH_LIFE) {
            float st = ripple.age / SPLASH_LIFE;
            boolean ground = ripple.shape == SHAPE_GROUND;
            float h = (ground ? 0.1f : 0.17f) * (float) Math.sin(st * 3.1416f);
            float w = ground ? 0.022f : 0.028f;
            int sa = clamp255((int) ((1.0f - st) * ripple.strength * alphaMul * 200.0f));
            int srgb = this.lit(SHARD_RGB, ripple.light);
            int col = sa << 24 | srgb;
            int colTop = sa / 4 << 24 | srgb;
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

    private static void ring(VertexConsumer consumer, MatrixStack.Entry pose, int lod, float cx, float cy, float cz,
                             float radius, float width, float alpha, int rgb) {
        int mid = clamp255((int) alpha);
        if (mid <= 0) {
            return;
        }
        int midColor = mid << 24 | rgb;
        float inner = Math.max(0.01f, radius - width);
        float outer = radius + width;
        float[] cs = LOD_COS[lod];
        float[] sn = LOD_SIN[lod];
        int seg = LOD_SEGMENTS[lod];
        for (int i = 1; i <= seg; ++i) {
            float c = cs[i];
            float s = sn[i];
            float pc = cs[i - 1];
            float ps = sn[i - 1];
            consumer.vertex(pose, cx + pc * inner, cy, cz + ps * inner).color(rgb);
            consumer.vertex(pose, cx + c * inner, cy, cz + s * inner).color(rgb);
            consumer.vertex(pose, cx + c * radius, cy, cz + s * radius).color(midColor);
            consumer.vertex(pose, cx + pc * radius, cy, cz + ps * radius).color(midColor);
            consumer.vertex(pose, cx + pc * radius, cy, cz + ps * radius).color(midColor);
            consumer.vertex(pose, cx + c * radius, cy, cz + s * radius).color(midColor);
            consumer.vertex(pose, cx + c * outer, cy, cz + s * outer).color(rgb);
            consumer.vertex(pose, cx + pc * outer, cy, cz + ps * outer).color(rgb);
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
        float lenExtra;
        float width = 0.005f;
        float windMul = 1.0f;
        float driftX;
        float driftZ;
        double groundY = Double.NEGATIVE_INFINITY;
        boolean alive;
        boolean far;
        boolean water;
        float light;
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
        float light;
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
        float light;
    }
}
