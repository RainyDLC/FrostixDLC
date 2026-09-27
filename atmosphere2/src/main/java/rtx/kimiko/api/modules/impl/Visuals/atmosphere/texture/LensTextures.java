package rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture;

import java.util.Random;

/** All procedural lens sprites in one place. */
public final class LensTextures {

    private LensTextures() {}

    public static final ProceduralTexture GLOW = new ProceduralTexture("glow", 256, 256, (u, v) -> {
        float r2 = u * u + v * v;
        return (float) (Math.exp(-r2 * 7.0) * 0.8 + Math.exp(-r2 * 60.0) * 0.6);
    });

    public static final ProceduralTexture RING = new ProceduralTexture("ring", 256, 256, (u, v) -> {
        float r = (float) Math.sqrt(u * u + v * v);
        float d = (r - 0.82f) / 0.07f;
        return (float) Math.exp(-d * d);
    });

    /** Hexagonal aperture ghost: soft fill plus a brighter rim, like real 6-blade irises. */
    public static final ProceduralTexture HEX_GHOST = new ProceduralTexture("hex_ghost", 256, 256, (u, v) -> {
        float x = Math.abs(u), y = Math.abs(v);
        float hex = Math.max(y, x * 0.8660254f + y * 0.5f) / 0.9f;
        if (hex > 1f) return 0f;
        float rim = (float) Math.exp(-Math.pow((hex - 0.95f) / 0.05f, 2));
        return 0.35f + 0.65f * rim;
    });

    /** Anamorphic horizontal streak. */
    public static final ProceduralTexture STREAK = new ProceduralTexture("streak", 512, 32, (u, v) -> {
        float falloff = 1f - Math.abs(u);
        return (float) (Math.exp(-v * v * 18.0) * falloff * falloff * falloff);
    });

    /** Diffraction spikes around the light source. */
    public static final ProceduralTexture STARBURST = new ProceduralTexture("starburst", 256, 256, (u, v) -> {
        float r = (float) Math.sqrt(u * u + v * v);
        if (r >= 1f) return 0f;
        double angle = Math.atan2(v, u);
        double spikes = Math.pow(Math.abs(Math.cos(angle * 3.0)), 60.0)
                + 0.5 * Math.pow(Math.abs(Math.cos(angle * 3.0 + Math.PI / 6.0)), 120.0);
        return (float) (spikes * Math.pow(1f - r, 2.5));
    });

    public static final ProceduralTexture DIRT = createDirt();

    /** Smudges, dust specks and fine scratches, generated once from a fixed seed. */
    private static ProceduralTexture createDirt() {
        final int size = 512;
        final float[] alpha = new float[size * size];
        Random random = new Random(0xF205717L);

        for (int i = 0; i < 70; i++) {     // greasy smudges
            stamp(alpha, size, random.nextFloat() * size, random.nextFloat() * size,
                    10 + random.nextFloat() * 55, 0.04f + random.nextFloat() * 0.16f);
        }
        for (int i = 0; i < 180; i++) {    // dust specks
            stamp(alpha, size, random.nextFloat() * size, random.nextFloat() * size,
                    0.8f + random.nextFloat() * 2.2f, 0.35f + random.nextFloat() * 0.5f);
        }
        for (int i = 0; i < 30; i++) {     // hairline scratches
            scratch(alpha, size, random);
        }

        return new ProceduralTexture("dirt", size, size, (u, v) -> {
            int x = Math.min(size - 1, (int) ((u * 0.5f + 0.5f) * size));
            int y = Math.min(size - 1, (int) ((v * 0.5f + 0.5f) * size));
            return alpha[y * size + x];
        });
    }

    private static void stamp(float[] alpha, int size, float cx, float cy, float radius, float strength) {
        int r = (int) Math.ceil(radius * 2);
        for (int y = (int) cy - r; y <= (int) cy + r; y++) {
            for (int x = (int) cx - r; x <= (int) cx + r; x++) {
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                float dx = (x - cx) / radius, dy = (y - cy) / radius;
                float a = strength * (float) Math.exp(-(dx * dx + dy * dy) * 2.0);
                int i = y * size + x;
                alpha[i] = alpha[i] + a * (1f - alpha[i]); // "over" blend keeps it in 0..1
            }
        }
    }

    private static void scratch(float[] alpha, int size, Random random) {
        float x = random.nextFloat() * size, y = random.nextFloat() * size;
        double heading = random.nextDouble() * Math.PI * 2;
        int length = 25 + random.nextInt(140);
        float strength = 0.12f + random.nextFloat() * 0.25f;
        for (int step = 0; step < length; step++) {
            heading += (random.nextDouble() - 0.5) * 0.08; // slight natural curve
            x += (float) Math.cos(heading);
            y += (float) Math.sin(heading);
            float taper = (float) Math.sin(Math.PI * step / length);
            stamp(alpha, size, x, y, 0.6f, strength * taper);
        }
    }
}
