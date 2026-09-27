package rtx.kimiko.api.modules.impl.Visuals.atmosphere.util;

public final class MathUtil {

    private MathUtil() {}

    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    /** Frame-rate independent exponential smoothing. */
    public static float approach(float current, float target, float speed, float dtSeconds) {
        return current + (target - current) * (1f - (float) Math.exp(-speed * dtSeconds));
    }
}
