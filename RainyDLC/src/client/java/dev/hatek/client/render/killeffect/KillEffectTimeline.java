package dev.hatek.client.render.killeffect;

/** Immutable, wall-clock animation samples; independent of FPS and Minecraft entity lifetime. */
public final class KillEffectTimeline {
    public static final double DEMON_SECONDS = 4.6;
    public static final double VORTEX_SECONDS = 3.0;
    private KillEffectTimeline() { }

    public record Pose(double bodyY, double armSpread, double pitScale, double grip,
                       double slip, double dustY, double dustAlpha, double bodyAlpha) { }

    public static double smooth(double a, double b, double t) {
        double x = Math.clamp((t - a) / (b - a), 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    public static Pose demon(double seconds) {
        double lift = smooth(0.0, 0.85, seconds);
        double fall = smooth(0.94, 1.28, seconds);
        double grip = smooth(1.12, 1.30, seconds);
        // A short, constant-speed release: no slow smoothstep/ease-out on the fall.
        double slip = Math.clamp((seconds - 2.85) / 0.14, 0.0, 1.0);
        double y = 0.7 * lift - 2.65 * fall - 0.8 * slip;
        double pit = smooth(0.1, 0.72, seconds) * (1.0 - smooth(3.65, DEMON_SECONDS, seconds));
        return new Pose(y, lift * (1.0 - grip), pit, grip, slip, 0, 0, 1);
    }

    public static Pose vortex(double seconds) {
        double descend = smooth(0.15, 1.15, seconds);
        double dissolve = smooth(1.85, VORTEX_SECONDS, seconds);
        // Body vanishes only after the dust has covered its full height.
        return new Pose(0, 0, 0, 0, 0, 3.5 * (1 - descend),
                smooth(0, 0.30, seconds) * (1 - dissolve), 1 - smooth(1.2, 1.6, seconds));
    }
}
