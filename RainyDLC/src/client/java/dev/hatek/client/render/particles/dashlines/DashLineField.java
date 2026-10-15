package dev.hatek.client.render.particles.dashlines;

import dev.hatek.client.render.particles.WorldParticleUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Поле неоновых штрихов вокруг игрока: отслеживает движение, спавнит и двигает линии.
 * Порт rtx.kimiko DashLineField (329 строк Kotlin-декомпила), переписан чистым Java.
 * Маппинги 26.2: getEntityPos→position(), getYaw→getYRot().
 */
public final class DashLineField {
    private final ArrayList<DashLine> lines = new ArrayList<>();
    private final ArrayList<DashLine> pool = new ArrayList<>();
    private final Random random = new Random();

    private float flowX;
    private float flowY;
    private float flowZ = 1.0f;
    private float sideX = 1.0f;
    private float sideY;
    private float sideZ;
    private float upX;
    private float upY = 1.0f;
    private float upZ;

    private float speed;
    private float intensity;
    private double travelAccum;
    private float timeAccum;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLast;
    private float spawnAccum;

    private static final float TAU = (float) Math.PI * 2.0f;
    private static final float MAX_FRAME_SECONDS = 0.2f;
    private static final float SAMPLE_WINDOW = 0.1f;
    private static final float SPEED_BLEND = 0.5f;
    private static final float HEADING_BLEND = 9.0f;
    private static final float HEADING_FLOOR = 0.35f;
    private static final float VERTICAL_DAMP = 0.35f;
    private static final float INTENSITY_BLEND = 5.0f;
    private static final float IDLE_INTENSITY = 0.3f;
    private static final float SPEED_FLOOR = 1.1f;
    private static final float SPEED_FULL = 5.6f;
    private static final float SPEED_BASE = 7.0f;
    private static final float SPEED_GAIN = 2.4f;
    private static final float SPEED_MIN = 0.82f;
    private static final float SPEED_SPAN = 0.42f;
    private static final float LENGTH_BASE = 1.7f;
    private static final float LENGTH_MIN = 0.5f;
    private static final float LENGTH_SPAN = 1.1f;
    private static final float WIDTH_BASE = 0.022f;
    private static final float WIDTH_MIN = 0.55f;
    private static final float WIDTH_SPAN = 0.95f;
    private static final float BRIGHT_MIN = 0.55f;
    private static final float BELLY_MIN = 0.16f;
    private static final float BELLY_SPAN = 0.2f;
    private static final float LATERAL_NEAR = 0.22f;
    private static final float AHEAD_NEAR = 0.55f;
    private static final float AHEAD_SPAN = 1.15f;
    private static final float AHEAD_PAD = 1.6f;
    private static final float LEAVE_FACTOR = 1.45f;
    private static final float LEAVE_PAD = 4.0f;
    private static final float SPAWN_RATE = 5.5f;
    private static final int SPAWN_BURST = 12;
    private static final int HARD_LIMIT = 260;
    private static final float MIN_LIFE = 0.35f;
    private static final float MAX_LIFE = 3.0f;

    public List<DashLine> alive() {
        return this.lines;
    }

    public void clear() {
        this.pool.addAll(this.lines);
        this.lines.clear();
        this.hasLast = false;
        this.speed = 0.0f;
        this.intensity = 0.0f;
        this.travelAccum = 0.0;
        this.timeAccum = 0.0f;
        this.spawnAccum = 0.0f;
    }

    public void update(Minecraft mc, Vec3 center, float frameSeconds, int targetCount,
                       float radius, float lengthScale, float widthScale, float speedScale,
                       float spreadDegrees, boolean motionOnly) {
        Player player = mc.player;
        if (mc.level == null || player == null) {
            this.clear();
            return;
        }
        float dt = Mth.clamp(frameSeconds, 0.0f, MAX_FRAME_SECONDS);
        this.trackMotion(player.position(), player.getYRot(), dt, motionOnly);
        this.stepLines(dt, center, radius);
        this.spawnLines(mc, center, dt, targetCount, radius, lengthScale, widthScale, speedScale, spreadDegrees);
    }

    private void trackMotion(Vec3 position, float yaw, float dt, boolean motionOnly) {
        if (!this.hasLast) {
            this.lastX = position.x;
            this.lastY = position.y;
            this.lastZ = position.z;
            this.hasLast = true;
            return;
        }
        double dx = position.x - this.lastX;
        double dy = position.y - this.lastY;
        double dz = position.z - this.lastZ;
        this.lastX = position.x;
        this.lastY = position.y;
        this.lastZ = position.z;
        this.travelAccum += Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.timeAccum += dt;
        if (this.timeAccum >= SAMPLE_WINDOW) {
            float sampled = (float) (this.travelAccum / this.timeAccum);
            this.speed += (sampled - this.speed) * SPEED_BLEND;
            this.travelAccum = 0.0;
            this.timeAccum = 0.0f;
        }
        float moveLength = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (moveLength > 1.0E-4f && this.speed > HEADING_FLOOR) {
            float blend = Mth.clamp(HEADING_BLEND * dt, 0.0f, 1.0f);
            this.flowX += ((float) (dx / moveLength) - this.flowX) * blend;
            this.flowY += ((float) (dy / moveLength) * VERTICAL_DAMP - this.flowY) * blend;
            this.flowZ += ((float) (dz / moveLength) - this.flowZ) * blend;
        } else if (this.speed <= HEADING_FLOOR) {
            double radians = Math.toRadians(-yaw);
            float blend = Mth.clamp(4.5f * dt, 0.0f, 1.0f);
            this.flowX += ((float) Math.sin(radians) - this.flowX) * blend;
            this.flowY += (0.0f - this.flowY) * blend;
            this.flowZ += ((float) Math.cos(radians) - this.flowZ) * blend;
        }
        this.normalizeFlow();
        this.buildBasis();
        float target = Mth.clamp((this.speed - SPEED_FLOOR) / (SPEED_FULL - SPEED_FLOOR), 0.0f, 1.0f);
        float floor = motionOnly ? 0.0f : IDLE_INTENSITY;
        this.intensity += (Math.max(target, floor) - this.intensity) * Mth.clamp(INTENSITY_BLEND * dt, 0.0f, 1.0f);
    }

    private void normalizeFlow() {
        float length = (float) Math.sqrt(this.flowX * this.flowX + this.flowY * this.flowY + this.flowZ * this.flowZ);
        if (length < 1.0E-4f) {
            this.flowX = 0.0f;
            this.flowY = 0.0f;
            this.flowZ = 1.0f;
            return;
        }
        this.flowX /= length;
        this.flowY /= length;
        this.flowZ /= length;
    }

    private void buildBasis() {
        this.sideX = this.flowZ;
        this.sideY = 0.0f;
        this.sideZ = -this.flowX;
        float length = (float) Math.sqrt(this.sideX * this.sideX + this.sideZ * this.sideZ);
        if (length < 1.0E-4f) {
            this.sideX = 1.0f;
            this.sideY = 0.0f;
            this.sideZ = 0.0f;
            length = 1.0f;
        }
        this.sideX /= length;
        this.sideZ /= length;
        this.upX = this.flowY * this.sideZ - this.flowZ * this.sideY;
        this.upY = this.flowZ * this.sideX - this.flowX * this.sideZ;
        this.upZ = this.flowX * this.sideY - this.flowY * this.sideX;
        float upLength = (float) Math.sqrt(this.upX * this.upX + this.upY * this.upY + this.upZ * this.upZ);
        if (upLength < 1.0E-4f) {
            this.upX = 0.0f;
            this.upY = 1.0f;
            this.upZ = 0.0f;
            return;
        }
        this.upX /= upLength;
        this.upY /= upLength;
        this.upZ /= upLength;
    }

    private void stepLines(float dt, Vec3 center, float radius) {
        float leave = radius * LEAVE_FACTOR + LEAVE_PAD;
        float leaveSq = leave * leave;
        int write = 0;
        int n = this.lines.size();
        for (int index = 0; index < n; index++) {
            DashLine line = this.lines.get(index);
            line.step(dt);
            double dx = line.posX() - center.x;
            double dy = line.posY() - center.y;
            double dz = line.posZ() - center.z;
            boolean gone = line.expired() || dx * dx + dy * dy + dz * dz > leaveSq;
            if (gone) {
                this.pool.add(line);
                continue;
            }
            this.lines.set(write, line);
            write++;
        }
        while (this.lines.size() > write) {
            this.lines.remove(this.lines.size() - 1);
        }
    }

    private void spawnLines(Minecraft mc, Vec3 center, float dt, int targetCount, float radius,
                            float lengthScale, float widthScale, float speedScale, float spreadDegrees) {
        int wanted = Math.round(targetCount * this.intensity);
        if (wanted <= 0) {
            this.spawnAccum = 0.0f;
            return;
        }
        int missing = wanted - this.lines.size();
        if (missing <= 0) {
            return;
        }
        this.spawnAccum += missing * SPAWN_RATE * dt;
        int budget = (int) this.spawnAccum;
        if (budget <= 0) {
            return;
        }
        this.spawnAccum -= budget;
        budget = Math.min(budget, Math.min(missing, SPAWN_BURST));
        float streakSpeed = (SPEED_BASE + this.speed * SPEED_GAIN) * speedScale;
        float tilt = (float) Math.tan(Math.toRadians(spreadDegrees));
        int spawned = 0;
        for (int attempts = 0; spawned < budget && attempts < budget * 3 && this.lines.size() < HARD_LIMIT; attempts++) {
            if (this.emit(mc, center, radius, lengthScale, widthScale, streakSpeed, tilt)) {
                spawned++;
            }
        }
    }

    private boolean emit(Minecraft mc, Vec3 center, float radius, float lengthScale,
                         float widthScale, float streakSpeed, float tilt) {
        float angle = this.random.nextFloat() * TAU;
        float lateral = radius * (LATERAL_NEAR + this.random.nextFloat() * (1.0f - LATERAL_NEAR));
        float ahead = radius * (AHEAD_NEAR + this.random.nextFloat() * AHEAD_SPAN) + AHEAD_PAD;
        float offsetCos = (float) Math.cos(angle) * lateral;
        float offsetSin = (float) Math.sin(angle) * lateral;
        double x = center.x + this.flowX * ahead + this.sideX * offsetCos + this.upX * offsetSin;
        double y = center.y + this.flowY * ahead + this.sideY * offsetCos + this.upY * offsetSin;
        double z = center.z + this.flowZ * ahead + this.sideZ * offsetCos + this.upZ * offsetSin;
        if (WorldParticleUtil.isSolidCollisionBlock(mc, x, y, z)) {
            return false;
        }
        float jitterSide = (this.random.nextFloat() * 2.0f - 1.0f) * tilt;
        float jitterUp = (this.random.nextFloat() * 2.0f - 1.0f) * tilt * 0.75f;
        float dx = -this.flowX + this.sideX * jitterSide + this.upX * jitterUp;
        float dy = -this.flowY + this.sideY * jitterSide + this.upY * jitterUp;
        float dz = -this.flowZ + this.sideZ * jitterSide + this.upZ * jitterUp;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4f) {
            return false;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float roll = this.random.nextFloat();
        float streak = LENGTH_BASE * lengthScale * (LENGTH_MIN + roll * LENGTH_SPAN)
                * (0.65f + 0.55f * this.intensity);
        float width = WIDTH_BASE * widthScale * (WIDTH_MIN + this.random.nextFloat() * WIDTH_SPAN);
        float brightness = BRIGHT_MIN + this.random.nextFloat() * (1.0f - BRIGHT_MIN);
        float belly = BELLY_MIN + this.random.nextFloat() * BELLY_SPAN;
        float travel = ahead + radius * LEAVE_FACTOR + streak;
        float life = Mth.clamp(travel / Math.max(1.0f, streakSpeed), MIN_LIFE, MAX_LIFE);
        DashLine line = this.pool.isEmpty() ? new DashLine() : this.pool.remove(this.pool.size() - 1);
        line.launch(x, y, z, dx, dy, dz,
                streakSpeed * (SPEED_MIN + this.random.nextFloat() * SPEED_SPAN),
                streak, width, brightness, belly, life);
        this.lines.add(line);
        return true;
    }
}
