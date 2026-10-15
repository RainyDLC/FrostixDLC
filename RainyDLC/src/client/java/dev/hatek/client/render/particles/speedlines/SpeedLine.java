package dev.hatek.client.render.particles.speedlines;

/**
 * Один неоновый штрих: летит вдоль направления, гаснет со временем.
 * Чистая реализация с нуля.
 */
public final class SpeedLine {
    public double x;
    public double y;
    public double z;
    /** Единичное направление полёта. */
    public float dx;
    public float dy;
    public float dz;
    /** Скорость, блоков/сек. */
    public float speed;
    /** Длина шлейфа, блоков. */
    public float length;
    /** Полуширина ленты. */
    public float halfWidth;
    /** Возраст / время жизни, сек. */
    public float age;
    public float life;
    /** Яркость 0..1. */
    public float brightness;

    public void launch(double x, double y, double z,
                       float dx, float dy, float dz,
                       float speed, float length, float halfWidth,
                       float brightness, float life) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.speed = speed;
        this.length = length;
        this.halfWidth = halfWidth;
        this.brightness = brightness;
        this.life = Math.max(0.2f, life);
        this.age = 0.0f;
    }

    public void step(float dt) {
        this.age += dt;
        double travel = this.speed * dt;
        this.x += this.dx * travel;
        this.y += this.dy * travel;
        this.z += this.dz * travel;
    }

    public boolean expired() {
        return this.age >= this.life;
    }

    /** Огибающая яркости: быстрое появление, плавное угасание. */
    public float envelope() {
        float t = Math.min(1.0f, Math.max(0.0f, this.age / this.life));
        float rise = Math.min(1.0f, this.age / 0.12f);
        float fall = 1.0f - t * t;
        return rise * rise * (3.0f - 2.0f * rise) * fall * this.brightness;
    }
}
