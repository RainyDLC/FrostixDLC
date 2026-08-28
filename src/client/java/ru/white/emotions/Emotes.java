package ru.white.emotions;

import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.List;

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
        float beat = (float) Math.abs(Math.sin(t * 9.0));
        float shimmy = (float) Math.sin(t * 18.0);

        float bend = (0.85F + beat * 0.12F) * r;
        float hipY = 12F * (float) Math.cos(bend);
        float hipZ = 12F * (float) Math.sin(bend);

        m.body.pitch = bend;
        m.body.roll = shimmy * 0.07F * r;
        m.body.yaw = shimmy * 0.05F * r;

        float thigh = -bend * 0.55F;
        m.rightLeg.originY = hipY;
        m.rightLeg.originZ = hipZ;
        m.leftLeg.originY = hipY;
        m.leftLeg.originZ = hipZ;
        m.rightLeg.pitch = (thigh + beat * 0.05F) * r;
        m.leftLeg.pitch = (thigh - beat * 0.05F) * r;
        m.rightLeg.yaw = (-0.22F + shimmy * 0.05F) * r;
        m.leftLeg.yaw = (0.22F - shimmy * 0.05F) * r;

        float drop = 24F - (hipY + 12F * (float) Math.cos(thigh));
        m.head.originY += drop;
        m.body.originY += drop;
        m.rightLeg.originY += drop;
        m.leftLeg.originY += drop;

        float shoulderY = 2F * (float) Math.cos(bend) + drop;
        float shoulderZ = 2F * (float) Math.sin(bend);
        m.rightArm.originY = shoulderY;
        m.rightArm.originZ = shoulderZ;
        m.leftArm.originY = shoulderY;
        m.leftArm.originZ = shoulderZ;

        m.head.pitch = (-0.55F - beat * 0.06F) * r;
        m.head.roll = -shimmy * 0.04F * r;

        m.rightArm.pitch = (-bend - 0.35F) * r;
        m.leftArm.pitch = (-bend - 0.35F) * r;
        m.rightArm.roll = 0.40F * r;
        m.leftArm.roll = -0.40F * r;
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

        float raw = (float) Math.sin(t * 11.0);
        float stroke = (raw - 0.28F * (float) Math.sin(t * 22.0)) * r;

        m.rightArm.pitch = (-0.45F + stroke * 0.28F) * r;
        m.rightArm.yaw = -0.10F * r;
        m.rightArm.roll = -0.45F * r;

        m.leftArm.pitch = 0.25F * r;
        m.leftArm.roll = -0.55F * r;

        m.head.pitch = (0.42F + stroke * 0.05F) * r;
        m.head.roll = (float) (Math.sin(t * 5.5) * 0.05) * r;

        m.body.pitch = (-0.06F + stroke * 0.02F) * r;
        m.rightLeg.pitch = 0.05F * r;
        m.leftLeg.pitch = -0.05F * r;
        m.rightLeg.roll = 0.06F * r;
        m.leftLeg.roll = -0.06F * r;
    }

    private static void blowJob(PlayerEntityModel m, long ms) {
        double t = ms / 1000.0;
        float r = ramp(t, 0.4);
        float bob = (float) Math.sin(t * 7.5);

        float bend = (0.55F + bob * 0.08F) * r;
        float hipY = 12F * (float) Math.cos(bend);
        float hipZ = 12F * (float) Math.sin(bend);
        m.body.pitch = bend;

        float thigh = -bend * 0.5F;
        m.rightLeg.originY = hipY;
        m.rightLeg.originZ = hipZ;
        m.leftLeg.originY = hipY;
        m.leftLeg.originZ = hipZ;
        m.rightLeg.pitch = thigh * r;
        m.leftLeg.pitch = thigh * r;
        m.rightLeg.yaw = -0.08F * r;
        m.leftLeg.yaw = 0.08F * r;

        float drop = 24F - (hipY + 12F * (float) Math.cos(thigh));
        m.head.originY += drop;
        m.body.originY += drop;
        m.rightLeg.originY += drop;
        m.leftLeg.originY += drop;
        float shoulderY = 2F * (float) Math.cos(bend) + drop;
        float shoulderZ = 2F * (float) Math.sin(bend);
        m.rightArm.originY = shoulderY;
        m.rightArm.originZ = shoulderZ;
        m.leftArm.originY = shoulderY;
        m.leftArm.originZ = shoulderZ;

        m.head.pitch = (-0.10F + bob * 0.38F) * r;
        m.head.roll = bob * 0.05F * r;

        m.rightArm.pitch = (-0.95F + bob * 0.06F) * r;
        m.leftArm.pitch = (-0.95F + bob * 0.06F) * r;
        m.rightArm.yaw = -0.30F * r;
        m.leftArm.yaw = 0.30F * r;
        m.rightArm.roll = -0.12F * r;
        m.leftArm.roll = 0.12F * r;
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
