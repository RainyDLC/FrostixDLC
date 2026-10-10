package dev.hatek.client.media;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Анимированная (GIF) или статичная картинка, загруженная с диска.
 * Каждый кадр уже залит в текстуру; frameAt() выбирает кадр по времени.
 */
public final class MediaClip {
    public record Frame(Identifier texture, int width, int height, int delayMs) {
    }

    private final List<Frame> frames;
    private final int totalMs;

    public MediaClip(List<Frame> frames) {
        this.frames = List.copyOf(frames);
        int total = 0;
        for (Frame frame : frames) {
            total += frame.delayMs();
        }
        this.totalMs = Math.max(1, total);
    }

    public Frame frameAt(long nowMs) {
        if (this.frames.size() == 1) {
            return this.frames.get(0);
        }
        long t = nowMs % this.totalMs;
        for (Frame frame : this.frames) {
            t -= frame.delayMs();
            if (t < 0) {
                return frame;
            }
        }
        return this.frames.get(this.frames.size() - 1);
    }

    public boolean isAnimated() {
        return this.frames.size() > 1;
    }

    public int frameCount() {
        return this.frames.size();
    }
}
