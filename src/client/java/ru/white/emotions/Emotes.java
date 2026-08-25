package ru.white.emotions;

import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.List;

/**
 * Каталог эмоций (только английские названия). Каждая — циклическая анимация
 * по времени: руки/ноги/голова/корпус через углы ModelPart.
 *
 * Соглашения по модели:
 *  - pitch руки: 0 = вниз, -1.55 = вперёд горизонтально, -2.6 = поднята вверх;
 *  - yaw/roll правой руки отрицательные = кисть к центру тела (см. salute);
 *  - head.pitch: + вниз, - вверх; body.pitch: + наклон вперёд.
 */
public final class Emotes {

    public static final List<Emote> ALL = List.of(
            new Emote("Wave", 0, Emotes::wave),
            new Emote("Twerk", 0, Emotes::twerk),
            new Emote("Dance", 0, Emotes::dance),
            new Emote("Facepalm", 0, Emotes::facepalm),
            new Emote("JerkOff", 0, Emotes::jerkOff),
            new Emote("Blowjob (Hold)", 0, true, Emotes::blowJob),
            new Emote("Floss", 0, Emotes::floss),
            new Emote("Salute", 0, Emotes::salute),
            new Emote("Clap", 0, Emotes::clap)
    );

    private Emotes() {
    }

    /** Плавный вход в позу за ~0.4с (smoothstep). */
    private static float ramp(double t, double dur) {
        float x = (float) Math.min(1.0, t / dur);
        return x * x * (3 - 2 * x);
    }

    private static void wave(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.3);
        m.rightArm.pitch = -2.6F * r;
        m.rightArm.yaw = -0.15F * r;
        m.rightArm.roll = ((float) (Math.sin(t * 9.0) * 0.45) - 0.2F) * r;
        m.leftArm.roll = 0.06F;
        m.head.roll = (float) (Math.sin(t * 4.5) * 0.08) * r;
        m.body.yaw = (float) (Math.sin(t * 4.5) * 0.05) * r;
    }

    private static void twerk(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.45);
        float beat = (float) Math.abs(Math.sin(t * 9.0));   // отскок таза
        float shimmy = (float) Math.sin(t * 18.0);          // быстрая тряска

        // наклон вперёд, корпус пружинит и трясётся бёдрами
        m.body.pitch = (0.78F + beat * 0.14F) * r;
        m.body.roll = shimmy * 0.07F * r;
        m.body.yaw = shimmy * 0.05F * r;

        // голова поднята — смотрит перед собой, слегка качается
        m.head.pitch = (-0.62F - beat * 0.06F) * r;
        m.head.roll = -shimmy * 0.04F * r;

        // широкая стойка в полуприседе, пятки поочерёдно пружинят
        m.rightLeg.yaw = (-0.26F + shimmy * 0.05F) * r;
        m.leftLeg.yaw = (0.26F - shimmy * 0.05F) * r;
        m.rightLeg.pitch = (0.20F - beat * 0.16F) * r;
        m.leftLeg.pitch = (-0.04F + beat * 0.16F) * r;

        // руки упираются в колени
        m.rightArm.pitch = (-0.62F + beat * 0.12F) * r;
        m.leftArm.pitch = (-0.62F + beat * 0.12F) * r;
        m.rightArm.roll = -0.45F * r;
        m.leftArm.roll = 0.45F * r;
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
        m.body.roll = c * 0.08F;
        m.body.pitch = (float) Math.abs(Math.sin(t * 7.0)) * 0.05F;
    }

    private static void facepalm(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.3);
        float shake = (float) Math.sin(t * 3.0) * 0.05F;
        m.rightArm.pitch = -2.25F * r + shake;
        m.rightArm.yaw = 0.4F * r;
        m.rightArm.roll = -0.15F * r;
        m.head.pitch = (0.4F + shake * 0.6F) * r;
        m.head.roll = shake * 0.4F * r;
        m.body.pitch = 0.1F * r;
        m.leftArm.roll = 0.05F;
    }

    private static void jerkOff(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.4);

        // асимметричный штрих: вверх резче, вниз плавнее
        float raw = (float) Math.sin(t * 11.0);
        float stroke = (raw - 0.28F * (float) Math.sin(t * 22.0)) * r;

        // правая рука: кисть на уровне паха перед корпусом, ходит вдоль ствола
        m.rightArm.pitch = (-0.55F + stroke * 0.32F) * r;
        m.rightArm.yaw = -0.28F * r;
        m.rightArm.roll = 0.10F * r;

        // левая рука лежит на бедре
        m.leftArm.pitch = 0.28F * r;
        m.leftArm.roll = 0.85F * r;

        // смотрит вниз на "работу", голова чуть покачивается
        m.head.pitch = (0.42F + stroke * 0.05F) * r;
        m.head.roll = (float) (Math.sin(t * 5.5) * 0.05) * r;
        m.head.yaw = -0.10F * r;

        // корпус чуть откинут и дышит в такт, бёдра слегка подаются навстречу
        m.body.pitch = (-0.07F + stroke * 0.02F) * r;
        m.rightLeg.pitch = 0.05F * r;
        m.leftLeg.pitch = -0.05F * r;
        m.rightLeg.roll = 0.06F * r;
        m.leftLeg.roll = -0.06F * r;
    }

    private static void blowJob(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.4);
        float bob = (float) Math.sin(t * 7.5);   // кивки головой

        // наклонена вперёд, корпус качается в ритме кивков
        m.body.pitch = (0.50F + bob * 0.08F) * r;

        // голова работает: глубокий кивок вниз и обратно
        m.head.pitch = (-0.10F + bob * 0.38F) * r;
        m.head.roll = bob * 0.05F * r;

        // руки вытянуты вперёд, держат за бёдра
        m.rightArm.pitch = (-0.95F + bob * 0.06F) * r;
        m.leftArm.pitch = (-0.95F + bob * 0.06F) * r;
        m.rightArm.yaw = -0.30F * r;
        m.leftArm.yaw = 0.30F * r;
        m.rightArm.roll = -0.12F * r;
        m.leftArm.roll = 0.12F * r;

        // колени слегка прогнулись
        m.rightLeg.pitch = -0.06F * r;
        m.leftLeg.pitch = 0.06F * r;
        m.rightLeg.yaw = -0.08F * r;
        m.leftLeg.yaw = 0.08F * r;
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
        m.body.pitch = (float) Math.abs(Math.sin(t * 9.0)) * 0.04F;
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
        m.body.pitch = (float) Math.abs(Math.sin(t * 8.0)) * 0.03F;
    }
}
