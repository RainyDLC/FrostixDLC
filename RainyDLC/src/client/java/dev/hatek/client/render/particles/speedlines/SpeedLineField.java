package dev.hatek.client.render.particles.speedlines;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Поле неоновых штрихов вокруг камеры: следит за движением игрока,
 * спавнит линии, летящие навстречу движению.
 * Чистая реализация с нуля.
 */
public final class SpeedLineField {
    private final ArrayList<SpeedLine> lines = new ArrayList<>();
    private final ArrayList<SpeedLine> pool = new ArrayList<>();
    private final Random random = new Random();

    /** Сглаженная скорость игрока, блоков/сек. */
    private double velX;
    private double velY;
    private double velZ;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLast;
    /** 0..1 — насколько интенсивно сейчас должны лететь штрихи. */
    private float intensity;

    private static final float MAX_DT = 0.2f;
    private static final int HARD_LIMIT = 300;

    public List<SpeedLine> alive() {
        return this.lines;
    }

    /** Текущая интенсивность 0..1 (для информации). */
    public float intensity() {
        return this.intensity;
    }

    public void clear() {
        this.pool.addAll(this.lines);
        this.lines.clear();
        this.hasLast = false;
        this.velX = 0.0;
        this.velY = 0.0;
        this.velZ = 0.0;
        this.intensity = 0.0f;
    }

    /**
     * @param dtSeconds   время с прошлого кадра, сек
     * @param targetCount целевое число штрихов при полной интенсивности
     * @param radius      радиус оболочки спавна, блоков
     * @param lengthScale множитель длины
     * @param widthScale  множитель толщины
     * @param speedScale  множитель скорости
     * @param spreadDeg   разброс направления, градусы
     * @param motionOnly  показывать только при движении
     */
    public void update(Minecraft mc, Vec3 cameraPos, float dtSeconds, int targetCount,
                       float radius, float lengthScale, float widthScale, float speedScale,
                       float spreadDeg, boolean motionOnly) {
        Player player = mc.player;
        if (mc.level == null || player == null) {
            clear();
            return;
        }
        float dt = Mth.clamp(dtSeconds, 0.0f, MAX_DT);

        trackMotion(player, dt);

        double speed = Math.sqrt(this.velX * this.velX + this.velY * this.velY + this.velZ * this.velZ);
        float want = motionOnly
                ? Mth.clamp((float) ((speed - 1.0) / 3.0), 0.0f, 1.0f)
                : 0.35f + 0.65f * Mth.clamp((float) (speed / 6.0), 0.0f, 1.0f);
        this.intensity += (want - this.intensity) * Mth.clamp(dt * 4.0f, 0.0f, 1.0f);

        stepLines(dt, cameraPos, radius);
        spawnLines(player, cameraPos, targetCount, radius, lengthScale, widthScale, speedScale, spreadDeg);
    }

    private void trackMotion(Player player, float dt) {
        Vec3 pos = player.position();
        if (!this.hasLast) {
            this.lastX = pos.x;
            this.lastY = pos.y;
            this.lastZ = pos.z;
            this.hasLast = true;
            return;
        }
        double dtSafe = Math.max(1.0E-3, dt);
        double vx = (pos.x - this.lastX) / dtSafe;
        double vy = (pos.y - this.lastY) / dtSafe;
        double vz = (pos.z - this.lastZ) / dtSafe;
        this.lastX = pos.x;
        this.lastY = pos.y;
        this.lastZ = pos.z;
        float blend = Mth.clamp(dt * 6.0f, 0.0f, 1.0f);
        this.velX += (vx - this.velX) * blend;
        this.velY += (vy - this.velY) * blend;
        this.velZ += (vz - this.velZ) * blend;
    }

    private void stepLines(float dt, Vec3 cameraPos, float radius) {
        double maxDist = radius * 3.0;
        double maxDistSq = maxDist * maxDist;
        for (int i = this.lines.size() - 1; i >= 0; i--) {
            SpeedLine line = this.lines.get(i);
            line.step(dt);
            double dx = line.x - cameraPos.x;
            double dy = line.y - cameraPos.y;
            double dz = line.z - cameraPos.z;
            if (line.expired() || dx * dx + dy * dy + dz * dz > maxDistSq) {
                this.lines.remove(i);
                this.pool.add(line);
            }
        }
    }

    private void spawnLines(Player player, Vec3 cameraPos, int targetCount, float radius,
                            float lengthScale, float widthScale, float speedScale, float spreadDeg) {
        int want = Math.round(targetCount * this.intensity);
        int budget = Math.min(24, Math.max(0, want - this.lines.size()));
        if (budget <= 0 || this.lines.size() >= HARD_LIMIT) {
            return;
        }
        // Базовое направление штрихов — навстречу движению; на месте — по взгляду.
        double speed = Math.sqrt(this.velX * this.velX + this.velY * this.velY + this.velZ * this.velZ);
        double baseX;
        double baseY;
        double baseZ;
        if (speed > 0.6) {
            baseX = -this.velX / speed;
            baseY = -this.velY / speed;
            baseZ = -this.velZ / speed;
        } else {
            Vec3 look = player.getLookAngle();
            baseX = look.x;
            baseY = look.y;
            baseZ = look.z;
        }
        for (int i = 0; i < budget && this.lines.size() < HARD_LIMIT; i++) {
            // Точка в сферической оболочке вокруг камеры.
            double theta = this.random.nextDouble() * Math.PI * 2.0;
            double cosPhi = this.random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            double dist = radius * (0.55 + 0.45 * this.random.nextDouble());
            double sx = cameraPos.x + Math.cos(theta) * sinPhi * dist;
            double sy = cameraPos.y + cosPhi * dist * 0.7;
            double sz = cameraPos.z + Math.sin(theta) * sinPhi * dist;
            // Разброс направления.
            double[] dir = jitterDirection(baseX, baseY, baseZ, spreadDeg);
            SpeedLine line = this.pool.isEmpty() ? new SpeedLine() : this.pool.remove(this.pool.size() - 1);
            line.launch(sx, sy, sz,
                    (float) dir[0], (float) dir[1], (float) dir[2],
                    (float) ((4.0 + this.random.nextDouble() * 4.0) * speedScale),
                    (float) ((0.8 + this.random.nextDouble() * 1.4) * lengthScale),
                    0.02f * widthScale,
                    0.7f + this.random.nextFloat() * 0.3f,
                    0.9f + this.random.nextFloat() * 1.6f);
            this.lines.add(line);
        }
    }

    /** Случайный поворот базового направления в конусе spreadDeg. */
    private double[] jitterDirection(double bx, double by, double bz, float spreadDeg) {
        if (spreadDeg <= 0.01f) {
            return new double[]{bx, by, bz};
        }
        double maxRad = Math.toRadians(spreadDeg) * this.random.nextDouble();
        double ang = this.random.nextDouble() * Math.PI * 2.0;
        // Ортогональный базис вокруг base.
        double ux = -bz;
        double uy = 0.0;
        double uz = bx;
        double ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1.0E-4) {
            ux = 1.0;
            uy = 0.0;
            uz = 0.0;
            ul = 1.0;
        }
        ux /= ul;
        uz /= ul;
        double vx = by * uz - bz * uy;
        double vy = bz * ux - bx * uz;
        double vz = bx * uy - by * ux;
        double sin = Math.sin(maxRad);
        double cos = Math.cos(maxRad);
        double dx = bx * cos + (ux * Math.cos(ang) + vx * Math.sin(ang)) * sin;
        double dy = by * cos + (uy * Math.cos(ang) + vy * Math.sin(ang)) * sin;
        double dz = bz * cos + (uz * Math.cos(ang) + vz * Math.sin(ang)) * sin;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            return new double[]{bx, by, bz};
        }
        return new double[]{dx / l, dy / l, dz / l};
    }
}
