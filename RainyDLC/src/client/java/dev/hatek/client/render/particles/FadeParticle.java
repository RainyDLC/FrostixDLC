package dev.hatek.client.render.particles;

import net.minecraft.world.phys.Vec3;

/**
 * Одна затухающая частица: позиция, движение, время жизни, текстура.
 * Чистый порт rtx.kimiko FadeParticle (декомпил CFR убран, Kotlin-зависимости вычищены).
 */
public final class FadeParticle {
    public final float rotationDeg;
    public final net.minecraft.resources.Identifier texture;
    public final float gradientT;

    public double posX;
    public double posY;
    public double posZ;
    public double prevX;
    public double prevY;
    public double prevZ;
    public double motionX;
    public double motionY;
    public double motionZ;

    public final long spawnMs;
    public final float lifetimeMs;
    public boolean dead;
    public boolean holdCollide;
    public float twist;

    public FadeParticle(Vec3 pos, Vec3 motion, float rotationDeg, float lifetimeMs,
                        net.minecraft.resources.Identifier texture, float gradientT) {
        this.rotationDeg = rotationDeg;
        this.texture = texture;
        this.gradientT = gradientT;
        this.posX = pos.x;
        this.posY = pos.y;
        this.posZ = pos.z;
        this.prevX = pos.x;
        this.prevY = pos.y;
        this.prevZ = pos.z;
        this.motionX = motion.x;
        this.motionY = motion.y;
        this.motionZ = motion.z;
        this.spawnMs = System.currentTimeMillis();
        this.lifetimeMs = Math.max(1.0f, lifetimeMs);
    }

    public void beginStep() {
        this.prevX = this.posX;
        this.prevY = this.posY;
        this.prevZ = this.posZ;
    }

    /** 0→1 появление за lifetimeMs, затем 1→0 угасание за вторые lifetimeMs. */
    public float alpha01(long now) {
        float t = (float) (now - this.spawnMs) / this.lifetimeMs;
        if (t <= 0.0f) {
            return 0.0f;
        }
        if (t <= 1.0f) {
            return t;
        }
        return Math.max(0.0f, 2.0f - t);
    }

    public boolean isDead(long now) {
        return this.dead || (float) (now - this.spawnMs) >= this.lifetimeMs * 2.0f;
    }

    public Vec3 renderPos(float partialTicks) {
        return new Vec3(
                this.prevX + (this.posX - this.prevX) * partialTicks,
                this.prevY + (this.posY - this.prevY) * partialTicks,
                this.prevZ + (this.posZ - this.prevZ) * partialTicks);
    }
}
