package ru.white.module.impl.render.lyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class LyricParticle3D {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String text;
    private final Vec3d basePosition;
    private final long spawnTimeMs;
    private final long durationMs;
    private final float floatHeight;
    private boolean forceExpire = false;

    public LyricParticle3D(String text, Vec3d basePosition, long spawnTimeMs, long durationMs, float floatHeight) {
        this.text = text;
        this.basePosition = basePosition;
        this.spawnTimeMs = spawnTimeMs;
        this.durationMs = durationMs;
        this.floatHeight = floatHeight;
    }

    public Vec3d getBasePosition() {
        return basePosition;
    }

    public void dismiss() {
        this.forceExpire = true;
    }

    public boolean isDead(long nowMs) {
        return forceExpire || (nowMs - spawnTimeMs >= durationMs);
    }

    public String getText() {
        return text;
    }

    /**
     * Renders 3D lyric text in world space with billboarding, depth occlusion, and selected animation.
     */
    public void render(MatrixStack matrices,
                       Camera camera,
                       VertexConsumerProvider.Immediate immediate,
                       String animMode,
                       int colorRgb,
                       boolean throughWalls,
                       boolean shadow,
                       float sizeScale,
                       long nowMs) {
        long elapsed = nowMs - spawnTimeMs;
        if (elapsed < 0 || elapsed > durationMs || forceExpire) return;

        // Lifecycle progress in range [0.0 ... 1.0]
        float progress = MathHelper.clamp((float) elapsed / (float) durationMs, 0.0f, 1.0f);

        float alpha = 1.0f;
        double offsetY = 0.0;
        float animScale = 1.0f;
        String displayText = this.text;

        if ("Печатание".equalsIgnoreCase(animMode)) {
            // Режим 1: Быстрое печатание букв по очереди
            if (progress < 0.35f) {
                float typeProgress = progress / 0.35f;
                int visibleChars = Math.min(this.text.length(), (int) Math.ceil(this.text.length() * typeProgress));
                displayText = this.text.substring(0, Math.max(1, visibleChars));
                alpha = Math.min(1.0f, progress / 0.06f);
                offsetY = progress * floatHeight;
            } else if (progress < 0.75f) {
                displayText = this.text;
                alpha = 1.0f;
                offsetY = progress * floatHeight;
            } else {
                float eraseProgress = (progress - 0.75f) / 0.25f;
                displayText = this.text;
                alpha = 1.0f - easeInCubic(eraseProgress);
                offsetY = (progress * floatHeight) + (0.04 * easeInCubic(eraseProgress));
                animScale = 1.0f - (0.04f * eraseProgress);
            }
        } else {
            // Режим 2: Плавное появление (Smooth Fade / Appearance)
            if (progress < 0.20f) {
                float t = progress / 0.20f;
                alpha = easeOutCubic(t);
                offsetY = -0.10 * (1.0f - easeOutCubic(t)) + (progress * floatHeight);
                animScale = 0.90f + (0.10f * easeOutCubic(t));
            } else if (progress < 0.75f) {
                alpha = 1.0f;
                offsetY = progress * floatHeight;
                animScale = 1.0f;
            } else {
                float t = (progress - 0.75f) / 0.25f;
                alpha = 1.0f - easeInCubic(t);
                offsetY = (progress * floatHeight) + (0.04 * easeInCubic(t));
                animScale = 1.0f - (0.04f * t);
            }
        }

        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.005f || displayText == null || displayText.isBlank()) return;

        // Camera-Relative Translation (World Space -> Camera View Space)
        Vec3d camPos = camera.getCameraPos();
        double renderX = basePosition.x - camPos.x;
        double renderY = (basePosition.y + offsetY) - camPos.y;
        double renderZ = basePosition.z - camPos.z;

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        // Strict Billboarding to face camera
        matrices.multiply(camera.getRotation());

        // Static world scale with setting multiplier and animation scale
        float finalScale = 0.025f * sizeScale * animScale;
        matrices.scale(finalScale, -finalScale, finalScale);

        // Text width and centered alignment
        float textWidth = mc.textRenderer.getWidth(displayText);
        float xOffset = -textWidth / 2.0f;
        float yOffset = -mc.textRenderer.fontHeight / 2.0f;

        int alphaInt = (int) (alpha * 255.0f);
        int finalColor = (alphaInt << 24) | (colorRgb & 0x00FFFFFF);

        TextRenderer.TextLayerType layerType = throughWalls
                ? TextRenderer.TextLayerType.SEE_THROUGH
                : TextRenderer.TextLayerType.NORMAL;

        mc.textRenderer.draw(
                displayText,
                xOffset,
                yOffset,
                finalColor,
                shadow,
                matrices.peek().getPositionMatrix(),
                immediate,
                layerType,
                0,
                0xF000F0
        );

        matrices.pop();
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private static float easeInCubic(float t) {
        return (float) Math.pow(t, 3);
    }
}
