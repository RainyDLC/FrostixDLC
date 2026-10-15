package dev.hatek.client.render.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Поведение частиц при столкновении с блоками. Порт rtx.kimiko ParticleCollision,
 * переписан чистым Java (без Kotlin/декомпила).
 *
 * @return true — движение в этом тике отменено (позицию не сдвигать).
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

    private static final double SPEED_LIMIT = 5.0;

    private ParticleCollision() {
    }

    public static boolean apply(Minecraft mc, FadeParticle particle, String mode, float gravityValue) {
        boolean cancelMove = false;
        if (!IGNORE.equals(mode)) {
            if (FATAL.equals(mode) || STUCK.equals(mode)) {
                if (particle.holdCollide || collidesX(mc, particle) || collidesY(mc, particle) || collidesZ(mc, particle)) {
                    particle.motionX = 0.0;
                    particle.motionY = 0.0;
                    particle.motionZ = 0.0;
                    particle.holdCollide = true;
                    if (FATAL.equals(mode)) {
                        particle.dead = true;
                    }
                    cancelMove = true;
                }
            } else {
                boolean ricochet = isRicochet(mode);
                double jumpMul = jumpMultiplier(mode);
                double slideMul = ricochet ? 1.0 : (SLIDE_SLOW.equals(mode) ? 0.875 : 1.0);
                boolean anyCollide = false;
                if (particle.holdCollide || collidesY(mc, particle)) {
                    if (particle.motionY != 0.0) {
                        particle.motionY = ricochet
                                ? -particle.motionY * jumpMul / (Math.max(gravityValue, 0.0) + 1.0)
                                    * Math.min(Math.abs(particle.motionY) / 0.2, 1.0)
                                : 0.0;
                        if (ricochet && particle.motionY > 0.0 && particle.motionY < 0.02) {
                            particle.motionY = 0.0;
                        }
                    }
                    if (!ricochet) {
                        particle.motionX *= slideMul;
                        particle.motionZ *= slideMul;
                    }
                    anyCollide = true;
                }
                if (particle.holdCollide || collidesX(mc, particle)) {
                    particle.motionX = ricochet ? -particle.motionX * jumpMul : 0.0;
                    if (!ricochet) {
                        particle.motionZ *= slideMul;
                    }
                    anyCollide = true;
                }
                if (particle.holdCollide || collidesZ(mc, particle)) {
                    particle.motionZ = ricochet ? -particle.motionZ * jumpMul : 0.0;
                    if (!ricochet) {
                        particle.motionX *= slideMul;
                    }
                    anyCollide = true;
                }
                if (anyCollide) {
                    double speedNow = Math.sqrt(particle.motionX * particle.motionX
                            + particle.motionY * particle.motionY
                            + particle.motionZ * particle.motionZ);
                    particle.holdCollide = speedNow < 0.008;
                }
            }
        }
        particle.motionX = Mth.clamp((float) particle.motionX, (float) -SPEED_LIMIT, (float) SPEED_LIMIT);
        particle.motionY = Mth.clamp((float) particle.motionY, (float) -SPEED_LIMIT, (float) SPEED_LIMIT);
        particle.motionZ = Mth.clamp((float) particle.motionZ, (float) -SPEED_LIMIT, (float) SPEED_LIMIT);
        return cancelMove;
    }

    private static boolean isRicochet(String mode) {
        return RICOCHET.equals(mode) || STRONG_RICOCHET.equals(mode) || VERY_STRONG_RICOCHET.equals(mode)
                || WEAK_RICOCHET.equals(mode) || VERY_WEAK_RICOCHET.equals(mode);
    }

    private static double jumpMultiplier(String mode) {
        if (VERY_WEAK_RICOCHET.equals(mode)) {
            return 0.5;
        }
        if (WEAK_RICOCHET.equals(mode)) {
            return 0.75;
        }
        if (STRONG_RICOCHET.equals(mode)) {
            return 1.5;
        }
        if (VERY_STRONG_RICOCHET.equals(mode)) {
            return 2.0;
        }
        return 1.0;
    }

    private static boolean collidesX(Minecraft mc, FadeParticle particle) {
        return WorldParticleUtil.isSolidCollisionBlock(mc,
                particle.posX + particle.motionX, particle.posY, particle.posZ);
    }

    private static boolean collidesY(Minecraft mc, FadeParticle particle) {
        return WorldParticleUtil.isSolidCollisionBlock(mc,
                particle.posX, particle.posY + particle.motionY, particle.posZ);
    }

    private static boolean collidesZ(Minecraft mc, FadeParticle particle) {
        return WorldParticleUtil.isSolidCollisionBlock(mc,
                particle.posX, particle.posY, particle.posZ + particle.motionZ);
    }
}
