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

        // сброс к базе, чтобы эмоция не складывалась с ванильной анимацией
        model.head.resetTransform();
        model.body.resetTransform();
        model.rightArm.resetTransform();
        model.leftArm.resetTransform();
        model.rightLeg.resetTransform();
        model.leftLeg.resetTransform();

        long ms = System.currentTimeMillis() - startMs;
        if (active.durationMs() > 0 && ms > active.durationMs()) {
            active = null;
            return;
        }
        active.pose().apply(model, ms);
    }
}
