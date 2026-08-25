package ru.white.emotions;

import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.List;

/**
 * Каталог эмоций (только английские названия). Каждая — циклическая анимация
 * по времени: руки/ноги/голова/корпус через углы ModelPart.
 */
public final class Emotes {

    public static final List<Emote> ALL = List.of(
            new Emote("Wave", 0, Emotes::wave),
            new Emote("Twerk", 0, Emotes::twerk),
            new Emote("Dance", 0, Emotes::dance),
            new Emote("Facepalm", 0, Emotes::facepalm),
            new Emote("JerkOff", 0, Emotes::jerkOff),
            new Emote("Floss", 0, Emotes::floss),
            new Emote("Salute", 0, Emotes::salute),
            new Emote("Clap", 0, Emotes::clap)
    );

    private Emotes() {
    }

    private static void wave(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        m.rightArm.pitch = -2.6F;
        m.rightArm.yaw = -0.15F;
        m.rightArm.roll = (float) (Math.sin(t * 9.0) * 0.45) - 0.2F;
        m.leftArm.roll = 0.06F;
        m.head.roll = (float) (Math.sin(t * 4.5) * 0.08);
    }

    private static void twerk(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float bounce = (float) Math.abs(Math.sin(t * 8.0));
        m.body.pitch = 0.55F + bounce * 0.18F;
        m.head.pitch = -0.35F - bounce * 0.1F;
        m.rightLeg.pitch = 0.25F - bounce * 0.35F;
        m.leftLeg.pitch = 0.25F + bounce * 0.35F;
        m.rightArm.pitch = -0.5F - bounce * 0.25F;
        m.leftArm.pitch = -0.5F - bounce * 0.25F;
        m.rightArm.roll = -0.25F;
        m.leftArm.roll = 0.25F;
    }

    private static void dance(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float s = (float) Math.sin(t * 7.0);
        float c = (float) Math.cos(t * 7.0);
        m.rightArm.pitch = -1.3F + s * 0.9F;
        m.leftArm.pitch = -1.3F - s * 0.9F;
        m.rightArm.roll = -0.4F - c * 0.3F;
        m.leftArm.roll = 0.4F - c * 0.3F;
        m.head.yaw = s * 0.45F;
        m.head.pitch = 0.1F + c * 0.12F;
        m.rightLeg.pitch = s * 0.35F;
        m.leftLeg.pitch = -s * 0.35F;
        m.body.yaw = -s * 0.15F;
    }

    private static void facepalm(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float shake = (float) Math.sin(t * 3.0) * 0.05F;
        m.rightArm.pitch = -2.25F + shake;
        m.rightArm.yaw = 0.4F;
        m.rightArm.roll = -0.15F;
        m.head.pitch = 0.4F + shake;
        m.leftArm.pitch = -0.1F;
    }

    private static void jerkOff(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        // плавный вход в позу за 0.4с, затем ровный ритм
        float ramp = (float) Math.min(1.0, t / 0.4);
        float stroke = (float) Math.sin(t * 10.0) * ramp;
        float strokeLag = (float) Math.sin(t * 10.0 - 0.9) * ramp;

        // лёгкий наклон назад, голова смотрит вниз на руку
        m.body.pitch = -0.07F * ramp;
        m.head.pitch = (0.5F + stroke * 0.04F) * ramp;
        m.head.yaw = 0.12F * ramp;

        // правая рука: ровные движения вперёд-назад перед корпусом
        m.rightArm.pitch = (-1.52F + stroke * 0.34F) * ramp;
        m.rightArm.yaw = 0.22F * ramp;
        m.rightArm.roll = (-0.1F + strokeLag * 0.05F) * ramp;

        // левая рука на бедре
        m.leftArm.pitch = -0.15F * ramp;
        m.leftArm.yaw = -0.12F * ramp;
        m.leftArm.roll = 0.45F * ramp;

        // стойка: ноги на ширине плеч, лёгкий перенос веса в такт
        m.rightLeg.yaw = 0.17F * ramp;
        m.leftLeg.yaw = -0.17F * ramp;
        m.rightLeg.pitch = (-0.03F + stroke * 0.05F) * ramp;
        m.leftLeg.pitch = (0.03F - stroke * 0.05F) * ramp;
    }

    private static void floss(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float s = (float) Math.sin(t * 9.0);
        float swing = s * 1.1F;
        m.rightArm.pitch = -0.7F;
        m.leftArm.pitch = -0.7F;
        m.rightArm.roll = -0.2F + swing;
        m.leftArm.roll = 0.2F + swing;
        m.body.yaw = s * 0.25F;
        m.head.yaw = -s * 0.3F;
        m.rightLeg.yaw = s * 0.3F;
        m.leftLeg.yaw = -s * 0.3F;
    }

    private static void salute(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float settle = (float) Math.min(1.0, t * 3.0);
        m.rightArm.pitch = -2.4F * settle;
        m.rightArm.yaw = -0.35F * settle;
        m.rightArm.roll = -0.55F * settle;
        m.head.pitch = -0.05F;
        m.leftArm.roll = 0.05F;
    }

    private static void clap(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float meet = 0.55F - (float) Math.abs(Math.sin(t * 8.0)) * 0.5F;
        m.rightArm.pitch = -1.15F;
        m.leftArm.pitch = -1.15F;
        m.rightArm.yaw = -meet;
        m.leftArm.yaw = meet;
        m.rightArm.roll = -0.1F;
        m.leftArm.roll = 0.1F;
        m.head.pitch = 0.08F;
    }
}
