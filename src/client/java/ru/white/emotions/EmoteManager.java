package ru.white.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * Активная эмоция локального игрока. Применяется в хуке
 * PlayerEntityModel.setAngles — после ванильного расчёта позы, поэтому
 * эмоция перекрывает ходьбу/махание рукой.
 */
public final class EmoteManager {

    private static Emote active;
    private static long startMs;

    private EmoteManager() {
    }

    public static void play(Emote emote) {
        active = (active == emote) ? null : emote;
        startMs = System.currentTimeMillis();
    }

    public static void stop() {
        active = null;
    }

    public static Emote active() {
        return active;
    }

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (active == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;

        // сброс ТОЛЬКО углов (resetTransform ломает пивоты — модель разваливается)
        resetAngles(model.head);
        resetAngles(model.body);
        resetAngles(model.rightArm);
        resetAngles(model.leftArm);
        resetAngles(model.rightLeg);
        resetAngles(model.leftLeg);

        long ms = System.currentTimeMillis() - startMs;
        if (active.durationMs() > 0 && ms > active.durationMs()) {
            active = null;
            return;
        }
        active.pose().apply(model, ms);
    }

    private static void resetAngles(net.minecraft.client.model.ModelPart part) {
        part.pitch = 0;
        part.yaw = 0;
        part.roll = 0;
    }
}
