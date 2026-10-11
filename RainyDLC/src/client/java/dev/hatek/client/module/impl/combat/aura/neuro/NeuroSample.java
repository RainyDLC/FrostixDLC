package dev.hatek.client.module.impl.combat.aura.neuro;

/**
 * Один сэмпл обучения нейро-ротаций.
 *
 * <p>input (6): [yawErr, pitchErr, dist, targetYawVel, targetPitchVel, part]
 * — угловая ошибка до центра тела цели, дистанция в блоках,
 * угловая скорость цели в градусах за тик, флаг части тела (0 — тело).
 * <p>output (2): [dYaw, dPitch] — доворот, который сделал сам игрок за тик.
 *
 * <p>Формат полностью совместим с {@code aimnet-v2}:
 * веса, обученные Python-скриптом {@code train_neuro.py}, загружаются
 * тем же классом {@code AimNet}.
 */
public final class NeuroSample {
    public final float[] input;
    public final float[] output;

    public NeuroSample(float[] input, float[] output) {
        this.input = input;
        this.output = output;
    }
}
