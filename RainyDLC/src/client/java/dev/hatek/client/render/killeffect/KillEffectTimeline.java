package dev.hatek.client.render.killeffect;

import dev.hatek.client.module.impl.render.KillEffect;

/** Immutable, wall-clock animation samples; independent of FPS and Minecraft entity lifetime. */
public final class KillEffectTimeline {
    public static final double DEMON_SECONDS = 3.5;
    public static final double VORTEX_SECONDS = 3.0;
    public static final double SOUL_SECONDS = 3.2;
    public static final double BLACK_HOLE_SECONDS = 3.3;
    public static final double LIGHTNING_SECONDS = 2.6;
    public static final double CRYSTAL_SECONDS = 2.8;

    private KillEffectTimeline() { }

    public record Pose(
            double bodyY,
            double armSpread,
            double pitScale,
            double grip,
            double slip,
            double dustY,
            double dustAlpha,
            double bodyAlpha,
            double bodySpin,
            double bodyTilt,
            double scaleXZ,
            double scaleY,
            double shatter,
            double energy,
            double burst,
            double tintR,
            double tintG,
            double tintB
    ) { }

    public static double smooth(double a, double b, double t) {
        double x = Math.clamp((t - a) / (b - a), 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    public static double easeOutBack(double a, double b, double t) {
        double x = Math.clamp((t - a) / (b - a), 0.0, 1.0);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        double xm1 = x - 1.0;
        return 1.0 + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }

    public static double duration(String mode) {
        if (mode == null) return DEMON_SECONDS;
        return switch (mode) {
            case KillEffect.MODE_VORTEX -> VORTEX_SECONDS;
            case KillEffect.MODE_SOUL -> SOUL_SECONDS;
            case KillEffect.MODE_BLACK_HOLE -> BLACK_HOLE_SECONDS;
            case KillEffect.MODE_LIGHTNING -> LIGHTNING_SECONDS;
            case KillEffect.MODE_CRYSTAL -> CRYSTAL_SECONDS;
            default -> DEMON_SECONDS;
        };
    }

    public static Pose sample(String mode, double seconds) {
        if (mode == null) return demonic(seconds);
        return switch (mode) {
            case KillEffect.MODE_VORTEX -> vortex(seconds);
            case KillEffect.MODE_SOUL -> soul(seconds);
            case KillEffect.MODE_BLACK_HOLE -> blackHole(seconds);
            case KillEffect.MODE_LIGHTNING -> lightning(seconds);
            case KillEffect.MODE_CRYSTAL -> crystal(seconds);
            default -> demonic(seconds);
        };
    }

    public static Pose demon(double seconds) {
        return demonic(seconds);
    }

    /**
     * Upgraded "Демоническая сила":
     * - Portal blooms with runic sigils (0.0..0.55s)
     * - Victim levitates in arcane agony with subtle tremor (0.0..0.82s)
     * - Shadow tendrils yank victim into the abyss (0.85..1.18s)
     * - Victim catches the glowing rim and struggles dynamically (1.10..1.95s)
     * - Final abyssal claw strike breaks their grip (1.98..2.18s) with a crimson shockwave burst!
     */
    public static Pose demonic(double seconds) {
        double lift = easeOutBack(0.0, 0.78, seconds);
        double fall = smooth(0.84, 1.18, seconds);
        double grip = smooth(1.02, 1.20, seconds);
        double struggle = smooth(1.18, 1.95, seconds) * (1.0 - smooth(1.92, 2.05, seconds));
        double slip = Math.clamp((seconds - 1.98) / 0.16, 0.0, 1.0);

        double tremor = Math.sin(seconds * 28.0) * 0.045 * struggle;
        double y = 0.92 * lift - 2.75 * fall - 0.88 * slip + tremor;
        double pit = easeOutBack(0.04, 0.58, seconds) * (1.0 - smooth(2.65, DEMON_SECONDS, seconds));
        double energy = smooth(0.0, 0.45, seconds) * (1.0 - smooth(2.75, DEMON_SECONDS, seconds));
        double burst = smooth(1.96, 2.55, seconds) * (1.0 - smooth(2.55, DEMON_SECONDS, seconds));

        double tilt = -0.26 * lift * (1.0 - fall) + Math.sin(seconds * 19.0) * 0.08 * struggle;
        double heat = smooth(0.6, 2.1, seconds);
        return new Pose(
                y,
                lift * (1.0 - 0.85 * grip) + 0.15 * Math.sin(seconds * 22.0) * struggle,
                Math.max(0.0, pit),
                grip,
                slip,
                0.0,
                0.0,
                1.0 - smooth(2.12, 2.35, seconds),
                0.0,
                tilt,
                1.0,
                1.0,
                0.0,
                energy,
                burst,
                1.0,
                1.0 - 0.42 * heat,
                1.0 - 0.55 * heat
        );
    }

    /**
     * Upgraded "Вихрь":
     * - Continuous 3D storm ribbons & wind blades sweep down and lift the player into a cyclone spin
     * - Player levitates, tilts back, spins with accelerating angular velocity, and compresses into the storm core
     * - Final wind-slash burst scatters glowing energy crescents
     */
    public static Pose vortex(double seconds) {
        double engage = smooth(0.05, 0.75, seconds);
        double ascend = smooth(0.15, 1.75, seconds);
        double collapse = smooth(1.45, 2.15, seconds);
        double dissolve = smooth(1.85, VORTEX_SECONDS, seconds);

        double bodyY = 1.35 * ascend - 0.25 * collapse;
        double spin = seconds * seconds * 4.8 * engage;
        double tilt = 0.32 * engage * (1.0 - 0.5 * collapse);
        double shrink = 1.0 - 0.82 * collapse;
        double bodyAlpha = 1.0 - smooth(1.70, 2.15, seconds);
        double dustAlpha = smooth(0.0, 0.28, seconds) * (1.0 - dissolve);
        double burst = smooth(1.65, 2.65, seconds) * (1.0 - dissolve);

        return new Pose(
                bodyY,
                0.65 * engage,
                0.0,
                0.0,
                0.0,
                2.4 * (1.0 - smooth(0.08, 0.95, seconds)),
                dustAlpha,
                bodyAlpha,
                spin,
                tilt,
                shrink,
                shrink,
                0.0,
                dustAlpha,
                burst,
                1.0 - 0.25 * engage,
                1.0 - 0.05 * engage,
                1.0
        );
    }

    /**
     * New Mode "Вознесение души":
     * - Sacred golden-aqua halo & light pillars appear
     * - Victim's spirit floats gently upward with serene arched pose and slow celestial rotation
     * - Double-helix soul wisps orbit the body before it dissolves into ascending starlight
     */
    public static Pose soul(double seconds) {
        double floatIn = smooth(0.0, 0.65, seconds);
        double rise = smooth(0.10, 2.45, seconds);
        double dissolve = smooth(1.85, 2.65, seconds);
        double fadeOut = smooth(2.25, SOUL_SECONDS, seconds);

        double bodyY = 2.35 * rise;
        double spin = seconds * 1.65 * floatIn;
        double tilt = -0.38 * floatIn;
        double armSpread = 0.58 * floatIn + 0.22 * dissolve;
        double bodyAlpha = (1.0 - dissolve) * 0.92;
        double energy = smooth(0.0, 0.35, seconds) * (1.0 - fadeOut);
        double burst = smooth(1.75, 2.85, seconds) * (1.0 - fadeOut);

        double glow = smooth(0.2, 1.6, seconds);
        return new Pose(
                bodyY,
                armSpread,
                energy,
                0.0,
                0.0,
                0.0,
                energy,
                bodyAlpha,
                spin,
                tilt,
                1.0 + 0.08 * dissolve,
                1.0 + 0.14 * dissolve,
                0.18 * dissolve,
                energy,
                burst,
                1.0,
                1.0 - 0.06 * glow,
                0.82 + 0.18 * glow
        );
    }

    /**
     * New Mode "Черная дыра":
     * - Gravitational singularity & relativistic accretion disk form at chest height (Y = 1.15)
     * - Body is pulled into an orbital spiral, stretched vertically & compressed radially (spaghettification)
     * - At t = 1.90s the singularity implodes and unleashes a supernova shockwave + polar jets
     */
    public static Pose blackHole(double seconds) {
        double open = easeOutBack(0.0, 0.55, seconds);
        double pull = smooth(0.25, 1.85, seconds);
        double spaghettify = smooth(0.95, 1.88, seconds);
        double implode = smooth(1.82, 2.08, seconds);
        double close = smooth(2.45, BLACK_HOLE_SECONDS, seconds);

        double bodyY = 0.48 * pull + 0.15 * Math.sin(seconds * 7.0) * (1.0 - spaghettify);
        double spin = Math.pow(seconds, 2.35) * 3.8 * pull;
        double tilt = 0.75 * pull;
        double scaleXZ = Math.max(0.04, 1.0 - 0.94 * spaghettify);
        double scaleY = 1.0 + 0.85 * spaghettify * (1.0 - implode) - 0.92 * implode;
        double bodyAlpha = 1.0 - smooth(1.68, 1.95, seconds);
        double energy = Math.max(0.0, open * (1.0 - close));
        double burst = smooth(1.88, 2.95, seconds) * (1.0 - close);

        double dark = smooth(0.6, 1.75, seconds);
        return new Pose(
                bodyY,
                0.85 * pull,
                energy,
                0.0,
                implode,
                0.0,
                energy,
                bodyAlpha,
                spin,
                tilt,
                scaleXZ,
                Math.max(0.05, scaleY),
                0.0,
                energy,
                burst,
                1.0 - 0.55 * dark,
                1.0 - 0.72 * dark,
                1.0 - 0.20 * dark
        );
    }

    /**
     * New Mode "Грозовой разряд":
     * - Jagged 3D sky lightning bolt strikes the target (0.0..0.45s) with high-voltage convulsion
     * - Target flashes between bright plasma cyan-white and charred silhouette
     * - At t = 0.95s, target disintegrates into an electric shockwave and flying sparks
     */
    public static Pose lightning(double seconds) {
        double strike = smooth(0.0, 0.18, seconds);
        double shock = strike * (1.0 - smooth(0.85, 1.15, seconds));
        double shatter = smooth(0.88, 1.85, seconds);
        double fadeOut = smooth(1.75, LIGHTNING_SECONDS, seconds);

        double jitter = Math.sin(seconds * 65.0) * 0.08 * shock;
        double bodyY = 0.38 * shock + jitter;
        double tilt = -0.32 * shock + Math.cos(seconds * 52.0) * 0.14 * shock;
        double armSpread = 0.92 * shock + 0.45 * shatter;
        double bodyAlpha = 1.0 - smooth(1.15, 1.75, seconds);
        double energy = smooth(0.0, 0.08, seconds) * (1.0 - fadeOut);
        double burst = smooth(0.12, 1.65, seconds) * (1.0 - fadeOut);

        double flash = 0.5 + 0.5 * Math.sin(seconds * 58.0);
        double r = shock > 0.1 ? (0.45 + 0.55 * flash) : 0.35;
        double g = shock > 0.1 ? (0.75 + 0.25 * flash) : 0.70;
        double b = 1.0;

        return new Pose(
                bodyY,
                armSpread,
                energy,
                0.0,
                0.0,
                0.0,
                energy,
                bodyAlpha,
                jitter * 2.2,
                tilt,
                1.0,
                1.0,
                shatter,
                energy,
                burst,
                r,
                g,
                b
        );
    }

    /**
     * New Mode "Кристальный распад":
     * - Erupting 3D frost/crystal spikes encase and freeze the player (0.0..0.82s)
     * - At t = 0.85s, the crystal prism shatters explosively — detaching and tumbling player limbs
     *   alongside spinning faceted crystal shards and a frost nova shockwave ring
     */
    public static Pose crystal(double seconds) {
        double freeze = smooth(0.0, 0.45, seconds);
        double shatter = smooth(0.82, 2.15, seconds);
        double fadeOut = smooth(1.95, CRYSTAL_SECONDS, seconds);

        double bodyY = 0.18 * freeze * (1.0 - shatter);
        double bodyAlpha = 1.0 - smooth(1.55, 2.25, seconds);
        double energy = easeOutBack(0.0, 0.52, seconds) * (1.0 - fadeOut);
        double burst = smooth(0.80, 2.35, seconds) * (1.0 - fadeOut);

        return new Pose(
                bodyY,
                0.42 * freeze,
                Math.max(0.0, energy),
                0.0,
                0.0,
                0.0,
                Math.max(0.0, energy),
                bodyAlpha,
                0.0,
                -0.18 * freeze,
                1.0,
                1.0,
                shatter,
                Math.max(0.0, energy),
                burst,
                1.0 - 0.45 * freeze,
                1.0 - 0.12 * freeze,
                1.0
        );
    }
}
