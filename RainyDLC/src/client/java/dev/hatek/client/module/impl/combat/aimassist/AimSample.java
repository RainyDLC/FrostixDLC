package dev.hatek.client.module.impl.combat.aimassist;

/**
 * Один сэмпл обучения наводки.
 *
 * <p>Обучается ТОЛЬКО наводка: вход — где цель относительно прицела
 * (отдельно для хитбоксов головы и тела), выход — как игрок реально
 * довернул мышь в этом тике. Сами удары (атаки) не записываются и не
 * используются.
 */
public final class AimSample {
    /** Входные признаки, размер {@link AimBrain#INPUT_SIZE}:
     * [dYawHead, dPitchHead, dYawBody, dPitchBody, distance, targetSpeed]. */
    public final float[] input;
    /** Реальный доворот игрока за тик: [yawDelta, pitchDelta]. */
    public final float[] output;

    public AimSample(float[] input, float[] output) {
        this.input = input;
        this.output = output;
    }
}
