package dev.hatek.client.module.impl.combat.aimassist;

/**
 * Один сэмпл обучения наводки.
 *
 * <p>input (6): [yawErr, pitchErr, dist, targetYawVel, targetPitchVel, part]
 * — угловая ошибка до точки прицеливания (голова/тело), дистанция,
 * угловая скорость цели и флаг части тела (0 — тело, 1 — голова).
 * <p>output (2): [dYaw, dPitch] — доворот, который сделал сам игрок за тик.
 */
public final class AimSample {
    public final float[] input;
    public final float[] output;

    public AimSample(float[] input, float[] output) {
        this.input = input;
        this.output = output;
    }
}
