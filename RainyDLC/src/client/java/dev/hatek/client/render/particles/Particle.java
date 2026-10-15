package dev.hatek.client.render.particles;

import net.minecraft.resources.Identifier;

/**
 * Одна частица мира: состояние симуляции.
 *
 * Единицы: позиция — блоки, скорость — блоков/тик, время жизни — миллисекунды.
 * Чистая реализация с нуля (не порт декомпила).
 */
public final class Particle {
    /** Текущая позиция. */
    public double x;
    public double y;
    public double z;
    /** Позиция на прошлом тике (для интерполяции рендера). */
    public double px;
    public double py;
    public double pz;
    /** Скорость, блоков/тик. */
    public double vx;
    public double vy;
    public double vz;
    /** Момент спавна, мс. */
    public final long bornMs;
    /** Полное время жизни, мс. */
    public final float lifeMs;
    /** Диаметр спрайта в мире. */
    public final float size;
    /** Текущий угол поворота спрайта, градусы. */
    public float rotationDeg;
    /** Скорость вращения спрайта, градусов/тик. */
    public final float spinDegPerTick;
    /** Текстура-шейп. */
    public final Identifier texture;
    /** Позиция в цветовом градиенте 0..1. */
    public final float tintSeed;
    /** Закручивание потока: радиан/тик со знаком, 0 — нет. */
    public final float swirl;
    /** Прилипла к блоку (не двигается). */
    public boolean stuck;
    /** Помечена на удаление. */
    public boolean dead;

    public Particle(double x, double y, double z,
                    double vx, double vy, double vz,
                    long bornMs, float lifeMs, float size,
                    float rotationDeg, float spinDegPerTick,
                    Identifier texture, float tintSeed, float swirl) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.bornMs = bornMs;
        this.lifeMs = Math.max(1.0f, lifeMs);
        this.size = size;
        this.rotationDeg = rotationDeg;
        this.spinDegPerTick = spinDegPerTick;
        this.texture = texture;
        this.tintSeed = tintSeed;
        this.swirl = swirl;
    }

    /** Возраст, мс. */
    public long ageMs(long now) {
        return now - this.bornMs;
    }

    /** Прогресс жизни 0..1. */
    public float progress(long now) {
        return Math.min(1.0f, Math.max(0.0f, (float) ageMs(now) / this.lifeMs));
    }

    /**
     * Прозрачность 0..1: плавное появление (первые 18% жизни),
     * плато, плавное угасание (последние 45%).
     */
    public float alpha(long now) {
        float t = progress(now);
        if (t <= 0.0f || t >= 1.0f) {
            return 0.0f;
        }
        float fadeIn = smoothstep(t / 0.18f);
        float fadeOut = 1.0f - smoothstep((t - 0.55f) / 0.45f);
        return Math.min(fadeIn, fadeOut);
    }

    public boolean isDead(long now) {
        return this.dead || ageMs(now) >= this.lifeMs;
    }

    /** Запомнить позицию перед шагом физики. */
    public void markPrev() {
        this.px = this.x;
        this.py = this.y;
        this.pz = this.z;
    }

    private static float smoothstep(float t) {
        t = Math.min(1.0f, Math.max(0.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }
}
