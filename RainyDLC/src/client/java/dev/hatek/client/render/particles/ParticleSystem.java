package dev.hatek.client.render.particles;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Контейнер живых частиц: спавн, тик физики, удаление мёртвых.
 * Чистая реализация с нуля.
 */
public final class ParticleSystem {
    private final ArrayList<Particle> particles = new ArrayList<>();

    public int size() {
        return this.particles.size();
    }

    public boolean isEmpty() {
        return this.particles.isEmpty();
    }

    public List<Particle> particles() {
        return this.particles;
    }

    public void add(Particle particle) {
        this.particles.add(particle);
    }

    public void clear() {
        this.particles.clear();
    }

    /**
     * Шаг симуляции для всех частиц: физика + удаление умерших.
     *
     * @param gravityPerTick ускорение свободного падения, блоков/тик^2
     * @param collideMode    режим из {@link ParticleCollision#MODES}
     */
    public void tick(Minecraft mc, double gravityPerTick, String collideMode) {
        long now = System.currentTimeMillis();
        for (int i = this.particles.size() - 1; i >= 0; i--) {
            Particle p = this.particles.get(i);
            if (p.isDead(now)) {
                this.particles.remove(i);
                continue;
            }
            p.rotationDeg += p.spinDegPerTick;
            ParticleCollision.step(mc, p, gravityPerTick, collideMode);
            if (p.isDead(now)) {
                this.particles.remove(i);
            }
        }
    }
}
