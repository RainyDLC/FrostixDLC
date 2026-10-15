package dev.hatek.client.render.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Столкновения частиц с блоками: движение проверяется по осям отдельно,
 * при ударе применяется поведение выбранного режима.
 * Чистая реализация с нуля.
 */
public final class ParticleCollision {
    public static final String RICOCHET = "Отскок";
    public static final String STRONG_RICOCHET = "Сильный отскок";
    public static final String VERY_STRONG_RICOCHET = "Очень сильный отскок";
    public static final String WEAK_RICOCHET = "Слабый отскок";
    public static final String VERY_WEAK_RICOCHET = "Очень слабый отскок";
    public static final String IGNORE = "Игнорировать";
    public static final String STUCK = "Прилипание";
    public static final String FATAL = "Исчезновение";
    public static final String SLIDE = "Скольжение";
    public static final String SLIDE_SLOW = "Медленное скольжение";

    public static final String[] MODES = {
            RICOCHET, STRONG_RICOCHET, VERY_STRONG_RICOCHET, WEAK_RICOCHET, VERY_WEAK_RICOCHET,
            IGNORE, STUCK, FATAL, SLIDE, SLIDE_SLOW
    };

    /** Предел скорости, блоков/тик (защита от туннелирования сквозь блоки). */
    private static final double SPEED_LIMIT = 3.0;
    /** Ниже этой скорости отскок считается остановкой. */
    private static final double REST_SPEED = 0.012;

    private ParticleCollision() {
    }

    /**
     * Один шаг физики: гравитация, закручивание, движение по осям с коллизиями.
     *
     * @param gravityPerTick ускорение свободного падения, блоков/тик^2 (может быть отрицательным)
     */
    public static void step(Minecraft mc, Particle p, double gravityPerTick, String mode) {
        if (p.dead || p.stuck) {
            return;
        }
        p.markPrev();

        if (p.swirl != 0.0f) {
            double cos = Math.cos(p.swirl);
            double sin = Math.sin(p.swirl);
            double vx = p.vx * cos - p.vz * sin;
            double vz = p.vx * sin + p.vz * cos;
            p.vx = vx;
            p.vz = vz;
        }

        p.vy -= gravityPerTick;
        clampSpeed(p);

        if (IGNORE.equals(mode)) {
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            return;
        }

        moveAxis(mc, p, mode, 0);
        if (p.dead || p.stuck) {
            return;
        }
        moveAxis(mc, p, mode, 1);
        if (p.dead || p.stuck) {
            return;
        }
        moveAxis(mc, p, mode, 2);
    }

    /** Движение вдоль одной оси (0=X, 1=Y, 2=Z) с проверкой блока назначения. */
    private static void moveAxis(Minecraft mc, Particle p, String mode, int axis) {
        double nx = p.x;
        double ny = p.y;
        double nz = p.z;
        if (axis == 0) {
            nx += p.vx;
        } else if (axis == 1) {
            ny += p.vy;
        } else {
            nz += p.vz;
        }
        if (!WorldParticleUtil.isSolidCollisionBlock(mc, nx, ny, nz)) {
            p.x = nx;
            p.y = ny;
            p.z = nz;
            return;
        }
        onImpact(p, mode, axis);
    }

    /** Реакция на удар о блок вдоль оси. */
    private static void onImpact(Particle p, String mode, int axis) {
        if (STUCK.equals(mode)) {
            p.vx = 0.0;
            p.vy = 0.0;
            p.vz = 0.0;
            p.stuck = true;
            return;
        }
        if (FATAL.equals(mode)) {
            p.dead = true;
            return;
        }
        if (SLIDE.equals(mode) || SLIDE_SLOW.equals(mode)) {
            setAxis(p, axis, 0.0);
            if (SLIDE_SLOW.equals(mode)) {
                // Гасим касательную скорость — «медленное» скольжение.
                if (axis != 0) {
                    p.vx *= 0.9;
                }
                if (axis != 1) {
                    p.vy *= 0.9;
                }
                if (axis != 2) {
                    p.vz *= 0.9;
                }
            }
            return;
        }
        // Отскоки: отражаем компоненту с коэффициентом реституции.
        double restitution = restitution(mode);
        double v = -getAxis(p, axis) * restitution;
        if (Math.abs(v) < REST_SPEED) {
            v = 0.0;
        }
        setAxis(p, axis, v);
    }

    private static double restitution(String mode) {
        if (VERY_STRONG_RICOCHET.equals(mode)) {
            return 1.0;
        }
        if (STRONG_RICOCHET.equals(mode)) {
            return 0.85;
        }
        if (WEAK_RICOCHET.equals(mode)) {
            return 0.4;
        }
        if (VERY_WEAK_RICOCHET.equals(mode)) {
            return 0.2;
        }
        return 0.65; // RICOCHET
    }

    private static double getAxis(Particle p, int axis) {
        if (axis == 0) {
            return p.vx;
        }
        if (axis == 1) {
            return p.vy;
        }
        return p.vz;
    }

    private static void setAxis(Particle p, int axis, double v) {
        if (axis == 0) {
            p.vx = v;
        } else if (axis == 1) {
            p.vy = v;
        } else {
            p.vz = v;
        }
    }

    private static void clampSpeed(Particle p) {
        p.vx = Mth.clamp(p.vx, -SPEED_LIMIT, SPEED_LIMIT);
        p.vy = Mth.clamp(p.vy, -SPEED_LIMIT, SPEED_LIMIT);
        p.vz = Mth.clamp(p.vz, -SPEED_LIMIT, SPEED_LIMIT);
    }
}
