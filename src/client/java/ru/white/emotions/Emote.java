package ru.white.emotions;

import net.minecraft.client.render.entity.model.PlayerEntityModel;

public record Emote(String name, long durationMs, boolean hold, Pose pose) {
    public Emote(String name, long durationMs, Pose pose) {
        this(name, durationMs, false, pose);
    }

    @FunctionalInterface
    public interface Pose {
        void apply(PlayerEntityModel model, long ms);
    }
}
