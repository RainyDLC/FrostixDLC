package ru.white.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

public final class EmoteManager {
    private static Emote active;
    private static long startMs;

    private static Emote preview;
    private static long previewStart;

    private static boolean pivotsDirty;

    private EmoteManager() {
    }

    public static void play(Emote emote) {
        active = (active == emote) ? null : emote;
        startMs = System.currentTimeMillis();
    }

    public static void startHold(Emote emote) {
        active = emote;
        startMs = System.currentTimeMillis();
    }

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

        if (pivotsDirty) {
            restoreOrigins(model);
            pivotsDirty = false;
        }

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
