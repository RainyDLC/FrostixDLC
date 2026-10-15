package dev.hatek.client.render.particles.dashlines;

import net.minecraft.util.Mth;

/**
 * Один неоновый штрих (speed line). Порт rtx.kimiko DashLine, переписан чистым Java.
 */
public final class DashLine {
    private double posX;
    private double posY;
    private double posZ;
    private float dirX;
    private float dirY;
    private float dirZ = 1.0f;
    private float speed;
    private float length = 1.0f;
    private float halfWidth = 0.02f;
    private float brightness = 1.0f;
    private float bellyBias = 0.5f;
    private float age;
    private float life = 1.0f;

    private static final float FADE_IN = 0.1f;
    private static final float FADE_OUT = 0.38f;

    public double posX() {
        return this.posX;
    }

    public double posY() {
        return this.posY;
    }

    public double posZ() {
        return this.posZ;
    }

    public float dirX() {
        return this.dirX;
    }

    public float dirY() {
        return this.dirY;
    }

    public float dirZ() {
        return this.dirZ;
    }

    public float length() {
        return this.length;
    }

    public float halfWidth() {
        return this.halfWidth;
    }

    public float bellyBias() {
        return this.bellyBias;
    }

    public void launch(double x, double y, double z, float dx, float dy, float dz,
                       float speed, float length, float halfWidth, float brightness,
                       float bellyBias, float life) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.dirX = dx;
        this.dirY = dy;
        this.dirZ = dz;
        this.speed = speed;
        this.length = length;
        this.halfWidth = halfWidth;
        this.brightness = brightness;
        this.bellyBias = bellyBias;
        this.life = Math.max(0.15f, life);
        this.age = 0.0f;
    }

    public void step(float dt) {
        this.age += dt;
        double travel = this.speed * dt;
        this.posX += this.dirX * travel;
        this.posY += this.dirY * travel;
        this.posZ += this.dirZ * travel;
    }

    public boolean expired() {
        return this.age >= this.life;
    }

    public float fade() {
        float t = Mth.clamp(this.age / this.life, 0.0f, 1.0f);
        float rise = Mth.clamp(this.age / FADE_IN, 0.0f, 1.0f);
        float fall = Mth.clamp((1.0f - t) / FADE_OUT, 0.0f, 1.0f);
        return smoothstep(rise) * smoothstep(fall) * this.brightness;
    }

    private static float smoothstep(float value) {
        return value * value * (3.0f - 2.0f * value);
    }
}
