package ru.white.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * Активная эмоция локального игрока. Применяется в хуке
 * PlayerEntityModel.setAngles — после ванильного расчёта позы, поэтому
 * эмоция перекрывает ходьбу/махание рукой.
 *
 * Превью: пока открыто колесо, наведение на карточку проигрывает эмоцию
 * на модели (и в мире, и в превью-рендере в центре колеса), не меняя
 * активную эмоцию.
 */
public final class EmoteManager {

    private static Emote active;
    private static long startMs;

    private static Emote preview;
    private static long previewStart;

    private EmoteManager() {
    }

    /** Клик по карточке: повторный клик по той же эмоции выключает её. */
    public static void play(Emote emote) {
        active = (active == emote) ? null : emote;
        startMs = System.currentTimeMillis();
    }

    /** Hold-эмоции: старт без переключения (остановка — stop()). */
    public static void startHold(Emote emote) {
        active = emote;
        startMs = System.currentTimeMillis();
    }

    public static void stop() {
        active = null;
    }

    public static Emote active() {
        return active;
    }

    /** Наведение на карточку в колесе: показать позу на модели. */
    public static void beginPreview(Emote emote) {
        if (preview == emote) return;
        preview = emote;
        previewStart = System.currentTimeMillis();
    }

    public static void endPreview() {
        preview = null;
    }

    public static Emote preview() {
        return preview;
    }

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        Emote current = preview != null ? preview : active;
        if (current == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;

        // сброс ТОЛЬКО углов (resetTransform ломает пивоты — модель разваливается)
        resetAngles(model.head);
        resetAngles(model.body);
        resetAngles(model.rightArm);
        resetAngles(model.leftArm);
        resetAngles(model.rightLeg);
        resetAngles(model.leftLeg);

        long ms = System.currentTimeMillis() - (preview != null ? previewStart : startMs);
        if (preview == null && current.durationMs() > 0 && ms > current.durationMs()) {
            active = null;
            return;
        }
        current.pose().apply(model, ms);
    }

    private static void resetAngles(net.minecraft.client.model.ModelPart part) {
        part.pitch = 0;
        part.yaw = 0;
        part.roll = 0;
    }
}
