package dev.hatek.client.cosmetic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Воксельная 3D-модель кастомного меча.
 * Координаты в пикселях: Y — вдоль меча (0 — низ навершия), X — ширина (центр 0),
 * Z — толщина (центр 0). Части могут менять цвет и двигаться во времени.
 */
public final class SwordModel {
    /** Цвет части: время в секундах и высота центра части. */
    public interface Paint {
        int color(float time, float y);
    }

    /** Смещение части во времени (out = dx, dy, dz). */
    public interface Motion {
        void offset(float time, float[] out);
    }

    public static final class Part {
        public final float x0, y0, z0, x1, y1, z1;
        public final Paint paint;
        public final boolean glow;
        public final Motion motion;

        Part(float x0, float y0, float z0, float x1, float y1, float z1,
             Paint paint, boolean glow, Motion motion) {
            this.x0 = Math.min(x0, x1);
            this.y0 = Math.min(y0, y1);
            this.z0 = Math.min(z0, z1);
            this.x1 = Math.max(x0, x1);
            this.y1 = Math.max(y0, y1);
            this.z1 = Math.max(z0, z1);
            this.paint = paint;
            this.glow = glow;
            this.motion = motion;
        }

        public float centerY() {
            return (this.y0 + this.y1) / 2.0f;
        }
    }

    private final String name;
    private final boolean animated;
    private final List<Part> parts;
    private final int iconColor;

    private SwordModel(String name, boolean animated, List<Part> parts, int iconColor) {
        this.name = name;
        this.animated = animated;
        this.parts = Collections.unmodifiableList(parts);
        this.iconColor = iconColor;
    }

    public String name() {
        return this.name;
    }

    public boolean animated() {
        return this.animated;
    }

    public List<Part> parts() {
        return this.parts;
    }

    /** Основной цвет меча — для подсветки в колесе. */
    public int iconColor() {
        return this.iconColor;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ---------------------------------------------------------------- краски

    public static Paint solid(int argb) {
        return (t, y) -> argb;
    }

    public static Paint pulse(int a, int b, float speed) {
        return (t, y) -> lerp(a, b, 0.5f + 0.5f * (float) Math.sin(t * speed * Math.PI * 2.0));
    }

    /** Градиент a→b, бегущий вдоль меча. */
    public static Paint flow(int a, int b, float speed, float scale) {
        return (t, y) -> lerp(a, b, 0.5f + 0.5f * (float) Math.sin((y * scale - t * speed) * Math.PI * 2.0));
    }

    public static Paint rainbow(float speed, float scale, float saturation, float brightness) {
        return (t, y) -> {
            float hue = y * scale - t * speed;
            hue -= (float) Math.floor(hue);
            return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        };
    }

    /** Мерцание как у пламени/молнии: быстрый шум между цветами. */
    public static Paint flicker(int a, int b, float speed) {
        return (t, y) -> {
            double n = Math.sin(t * speed * 7.3 + y * 1.7) * 0.5 + Math.sin(t * speed * 13.1 - y * 2.9) * 0.5;
            return lerp(a, b, (float) (0.5 + 0.5 * n));
        };
    }

    public static int lerp(int a, int b, float k) {
        float t = Math.max(0.0f, Math.min(1.0f, k));
        int aa = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- движение

    /** Покачивание вверх-вниз. */
    public static Motion bob(float amplitude, float speed, float phase) {
        return (t, out) -> {
            out[0] = 0.0f;
            out[1] = amplitude * (float) Math.sin((t * speed + phase) * Math.PI * 2.0);
            out[2] = 0.0f;
        };
    }

    /** Вращение вокруг клинка по кругу радиуса radius на высоте (центр части задаётся в 0,y,0). */
    public static Motion orbit(float radius, float speed, float phase, float rise) {
        return (t, out) -> {
            double a = (t * speed + phase) * Math.PI * 2.0;
            out[0] = radius * (float) Math.cos(a);
            out[1] = rise * (float) Math.sin(a * 0.5);
            out[2] = radius * (float) Math.sin(a);
        };
    }

    // ---------------------------------------------------------------- билдер

    public static final class Builder {
        private final String name;
        private final List<Part> parts = new ArrayList<>();
        private boolean animated;
        private int iconColor = 0xFFFFFFFF;

        private Builder(String name) {
            this.name = name;
        }

        public Builder accent(int argb) {
            this.iconColor = argb;
            return this;
        }

        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
            return box(x0, y0, z0, x1, y1, z1, solid(argb), false, null);
        }

        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1,
                           Paint paint, boolean glow, Motion motion) {
            this.parts.add(new Part(x0, y0, z0, x1, y1, z1, paint, glow, motion));
            return this;
        }

        /** Симметричный по X и Z брусок: halfW — полуширина, halfT — полутолщина. */
        public Builder bar(float y0, float y1, float halfW, float halfT, int argb) {
            return box(-halfW, y0, -halfT, halfW, y1, halfT, argb);
        }

        public Builder bar(float y0, float y1, float halfW, float halfT, Paint paint, boolean glow) {
            return box(-halfW, y0, -halfT, halfW, y1, halfT, paint, glow, null);
        }

        /** Рукоять с обмоткой из чередующихся цветов. */
        public Builder grip(float y0, float y1, float half, int a, int b) {
            boolean odd = false;
            for (float y = y0; y < y1 - 0.01f; y += 1.0f) {
                bar(y, Math.min(y1, y + 1.0f), half, half, odd ? b : a);
                odd = !odd;
            }
            return this;
        }

        /**
         * Клинок с гранями: тонкие края + утолщённый центральный дол, сужение к острию.
         */
        public Builder blade(float y0, float length, float halfW, float halfT, int tipSteps,
                             Paint edge, Paint core, boolean glow) {
            float straight = length - tipSteps;
            bar(y0, y0 + straight, halfW, halfT * 0.55f, edge, glow);
            bar(y0, y0 + straight, Math.max(0.5f, halfW * 0.35f), halfT, core, glow);
            for (int i = 0; i < tipSteps; i++) {
                float k = 1.0f - (i + 1.0f) / (tipSteps + 1.0f);
                float w = Math.max(0.5f, halfW * k);
                float ys = y0 + straight + i;
                bar(ys, ys + 1.0f, w, halfT * 0.55f, edge, glow);
                if (w > 0.9f) {
                    bar(ys, ys + 1.0f, Math.max(0.5f, w * 0.35f), halfT, core, glow);
                }
            }
            return this;
        }

        public Builder blade(float y0, float length, float halfW, float halfT, int tipSteps,
                             int edge, int core) {
            return blade(y0, length, halfW, halfT, tipSteps, solid(edge), solid(core), false);
        }

        public Builder animated() {
            this.animated = true;
            return this;
        }

        public SwordModel build() {
            return new SwordModel(this.name, this.animated, this.parts, this.iconColor);
        }
    }
}
