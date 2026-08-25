package ru.white.emotions;

import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Эмоция: имя, длительность, hold-флаг и аниматор, который каждый кадр
 * задаёт углы частей модели. Все углы в радианах, время — миллисекунды
 * от старта эмоции.
 */
public record Emote(String name, long durationMs, boolean hold, Pose pose) {

    public Emote(String name, long durationMs, Pose pose) {
        this(name, durationMs, false, pose);
    }

    @FunctionalInterface
    public interface Pose {
        void apply(PlayerEntityModel model, long ms);
    }
}
