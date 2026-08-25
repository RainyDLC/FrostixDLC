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

    /** Позапрошлая поза двигала пивоты — надо восстановить дефолт. */
    private static boolean pivotsDirty;

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

    /** Превью становится активной эмоцией без сброса времени — бесшовно. */
    public static void keepPreviewAsActive() {
        if (preview != null) {
            active = preview;
            startMs = previewStart;
            preview = null;
        }
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

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;

        // если прошлая поза двигала пивоты (twerk и т.п.) — вернуть дефолт
        if (pivotsDirty) {
            restoreOrigins(model);
            pivotsDirty = false;
        }

        // сброс ТОЛЬКО углов (resetTransform трогает лишнее — углы гасим руками)
        resetAngles(model.head);
        resetAngles(model.body);
        resetAngles(model.rightArm);
        resetAngles(model.leftArm);
        resetAngles(model.rightLeg);
        resetAngles(model.leftLeg);

        if (current == null) return;

        long ms = System.currentTimeMillis() - (preview != null ? previewStart : startMs);
        if (preview == null && current.durationMs() > 0 && ms > current.durationMs()) {
            active = null;
            return;
        }
        current.pose().apply(model, ms);
        pivotsDirty = true;
    }

    /** Возврат пивотов шести частей к дефолтным значениям модели. */
    private static void restoreOrigins(PlayerEntityModel model) {
        restoreOrigin(model.head);
        restoreOrigin(model.body);
        restoreOrigin(model.rightArm);
        restoreOrigin(model.leftArm);
        restoreOrigin(model.rightLeg);
        restoreOrigin(model.leftLeg);
    }

    private static void restoreOrigin(net.minecraft.client.model.ModelPart part) {
        net.minecraft.client.model.ModelTransform def = part.getDefaultTransform();
        part.originX = def.x();
        part.originY = def.y();
        part.originZ = def.z();
    }

    private static void resetAngles(net.minecraft.client.model.ModelPart part) {
        part.pitch = 0;
        part.yaw = 0;
        part.roll = 0;
    }
}
