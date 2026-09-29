package rtx.kimiko.api.ui.mainmenu;

import rtx.kimiko.utils.render.render2d.Render2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MenuParticles {
    private static final Random RANDOM = new Random();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static boolean initialized = false;

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        float alpha;
        float baseAlpha;
        float phase;
        float phaseSpeed;
        int color;

        void reset(float width, float height, int timePhase) {
            this.x = RANDOM.nextFloat() * width;
            this.y = height + 10.0f + RANDOM.nextFloat() * 50.0f;
            this.vx = (RANDOM.nextFloat() - 0.5f) * 12.0f;
            this.vy = -(15.0f + RANDOM.nextFloat() * 28.0f); // upward floating drift
            this.size = 1.2f + RANDOM.nextFloat() * 2.8f;
            this.baseAlpha = 0.35f + RANDOM.nextFloat() * 0.55f;
            this.alpha = this.baseAlpha;
            this.phase = RANDOM.nextFloat() * 6.28f;
            this.phaseSpeed = 1.0f + RANDOM.nextFloat() * 2.5f;

            // Palette depends on time of day (0=Morning, 1=Day, 2=Sunset, 3=Night)
            int palette = RANDOM.nextInt(4);
            if (timePhase == 3) {
                // Night: Magical glowing bioluminescent fireflies and stardust
                this.color = switch (palette) {
                    case 0 -> 0x78E8FF; // luminous ice cyan
                    case 1 -> 0xA0F0FF; // sparkling starlight
                    case 2 -> 0x80C0FF; // ethereal blue
                    default -> 0xE0F7FA; // pure soft moonbeam
                };
            } else if (timePhase == 1) {
                // Day: Sunlight dust motes and golden pollen
                this.color = switch (palette) {
                    case 0 -> 0xFFF9C4; // pale warm sunbeam
                    case 1 -> 0xFFF59D; // dandelion gold
                    case 2 -> 0xFFEE58; // vibrant summer sunlight
                    default -> 0xFFFFFF; // pure sun reflection
                };
            } else if (timePhase == 0) {
                // Morning / Dawn: Soft pastel sunrise sparkles & dawn dew
                this.color = switch (palette) {
                    case 0 -> 0xFFE0B2; // peach dawn
                    case 1 -> 0xFFCCBC; // rosy morning amber
                    case 2 -> 0xFFF3E0; // delicate sunrise cream
                    default -> 0xFFFFFF; // white mist sparkle
                };
            } else {
                // Sunset (Phase 2): Warm fiery golden amber fireflies matching reference screenshot
                this.color = switch (palette) {
                    case 0 -> 0xFFFBE3; // warm bright gold-white
                    case 1 -> 0xFFD878; // golden ember
                    case 2 -> 0xFFA940; // deep warm amber
                    default -> 0xFFE996; // soft twilight sunburst
                };
            }
        }
    }

    public static void updateAndRender(float width, float height, float mouseX, float mouseY, float dt, int targetCount, int timePhase) {
        if (!initialized || PARTICLES.size() != targetCount) {
            PARTICLES.clear();
            for (int i = 0; i < targetCount; i++) {
                Particle p = new Particle();
                p.reset(width, height, timePhase);
                // Scatter initial Y across full screen height
                p.y = RANDOM.nextFloat() * height;
                PARTICLES.add(p);
            }
            initialized = true;
        }

        for (Particle p : PARTICLES) {
            p.phase += dt * p.phaseSpeed;
            // Upward drift + gentle horizontal sinusoidal sway
            p.x += (p.vx + (float) Math.sin(p.phase) * 14.0f) * dt;
            p.y += p.vy * dt;

            // Subtle mouse repulsion waft
            float dx = p.x - mouseX;
            float dy = p.y - mouseY;
            float distSq = dx * dx + dy * dy;
            if (distSq < 6400.0f && distSq > 1.0f) { // 80px radius
                float dist = (float) Math.sqrt(distSq);
                float force = (1.0f - dist / 80.0f) * 45.0f * dt;
                p.x += (dx / dist) * force;
                p.y += (dy / dist) * force;
            }

            // Pulsing twinkling alpha
            float pulse = 0.7f + 0.3f * (float) Math.sin(p.phase * 2.2f);
            p.alpha = Math.max(0.1f, Math.min(1.0f, p.baseAlpha * pulse));

            // Wrap around / respawn when drifting off screen
            if (p.y < -15.0f || p.x < -20.0f || p.x > width + 20.0f) {
                p.reset(width, height, timePhase);
            }

            // Draw glowing halo + bright core
            int aHalo = Math.max(0, Math.min(255, (int) (p.alpha * 48.0f)));
            int aCore = Math.max(0, Math.min(255, (int) (p.alpha * 240.0f)));
            int haloColor = (aHalo << 24) | (p.color & 0x00FFFFFF);
            int coreColor = (aCore << 24) | (p.color & 0x00FFFFFF);

            // Halo
            Render2D.circle(p.x, p.y, p.size * 2.6f, haloColor);
            // Core
            Render2D.circle(p.x, p.y, p.size, coreColor);
        }
    }
}
