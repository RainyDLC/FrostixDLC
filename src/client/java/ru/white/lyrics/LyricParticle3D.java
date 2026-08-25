package ru.white.lyrics;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class LyricParticle3D {
    private final String text;
    private final Vec3d basePosition;
    private final long spawnTimeMs;
    private final long durationMs;
    private final float floatHeight;
    private final float tiltSign;
    private boolean forceExpire = false;

    public LyricParticle3D(String text, Vec3d basePosition, long spawnTimeMs, long durationMs, float floatHeight) {
        this.text = text;
        this.basePosition = basePosition;
        this.spawnTimeMs = spawnTimeMs;
        this.durationMs = durationMs;
        this.floatHeight = floatHeight;
        this.tiltSign = Math.random() > 0.5 ? 1.0f : -1.0f;
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
     * 3D-текст в мировом пространстве: статичный мировой размер (объект,
     * а не экранная надпись), полная 3D-окклюзия, анимации появления/ухода.
     */
    public void render(MatrixStack matrices, Camera camera, String animMode, int colorRgb, long nowMs, float baseScale) {
        long elapsed = nowMs - spawnTimeMs;
        if (elapsed < 0 || elapsed > durationMs || forceExpire) return;

        float progress = MathHelper.clamp((float) elapsed / (float) durationMs, 0.0f, 1.0f);

        float alpha = 1.0f;
        double offsetY = 0.0;
        float animScale = 1.0f;
        float rotationTilt = 0.0f;
        String displayText = this.text;

        switch (animMode) {
            case "Typewriter" -> {
                if (progress < 0.30f) {
                    float typeProgress = progress / 0.30f;
                    int visibleChars = Math.min(this.text.length(), (int) Math.ceil(this.text.length() * typeProgress));
                    displayText = this.text.substring(0, Math.max(1, visibleChars));
                    alpha = Math.min(1.0f, progress / 0.08f);
                    offsetY = progress * (floatHeight * 0.2);
                } else if (progress < 0.75f) {
                    alpha = 1.0f;
                    offsetY = progress * (floatHeight * 0.2);
                } else {
                    float eraseProgress = (progress - 0.75f) / 0.25f;
                    int remainingChars = Math.max(0, this.text.length() - (int) Math.floor(this.text.length() * eraseProgress));
                    displayText = this.text.substring(0, remainingChars);
                    alpha = 1.0f - (eraseProgress * 0.7f);
                    offsetY = progress * (floatHeight * 0.2);
                }
            }
            case "PopScale" -> {
                if (progress < 0.18f) {
                    float t = progress / 0.18f;
                    alpha = easeOutCubic(t);
                    animScale = 0.35f + 0.65f * easeOutBack(t);
                    offsetY = -0.04 * (1.0f - easeOutCubic(t));
                } else if (progress < 0.80f) {
                    alpha = 1.0f;
                } else {
                    float t = (progress - 0.80f) / 0.20f;
                    alpha = 1.0f - easeInCubic(t);
                    animScale = 1.0f - 0.05f * t;
                    offsetY = 0.03 * easeInCubic(t);
                }
            }
            case "KineticSlide" -> {
                if (progress < 0.20f) {
                    float t = progress / 0.20f;
                    alpha = easeOutCubic(t);
                    offsetY = -0.15 * (1.0f - easeOutCubic(t));
                    animScale = 0.92f + 0.08f * t;
                    rotationTilt = (1.0f - t) * 4.0f * tiltSign;
                } else if (progress < 0.80f) {
                    alpha = 1.0f;
                } else {
                    float t = (progress - 0.80f) / 0.20f;
                    alpha = 1.0f - easeInCubic(t);
                    offsetY = 0.05 * easeInCubic(t);
                    animScale = 1.0f - 0.04f * t;
                }
            }
            case "Fade" -> {
                if (progress < 0.15f) {
                    alpha = progress / 0.15f;
                } else if (progress < 0.80f) {
                    alpha = 1.0f;
                } else {
                    alpha = 1.0f - ((progress - 0.80f) / 0.20f);
                }
            }
            default -> { // "LyricFlow"
                if (progress < 0.15f) {
                    float t = progress / 0.15f;
                    alpha = easeOutCubic(t);
                    offsetY = -0.06 * (1.0f - easeOutCubic(t));
                    animScale = 0.93f + 0.07f * easeOutBack(t);
                } else if (progress < 0.80f) {
                    alpha = 1.0f;
                } else {
                    float t = (progress - 0.80f) / 0.20f;
                    alpha = 1.0f - easeInCubic(t);
                    offsetY = 0.04 * easeInCubic(t);
                    animScale = 1.0f - 0.03f * t;
                }
            }
        }

        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.001f || displayText == null || displayText.isBlank()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.getTextRenderer();

        Vec3d camPos = camera.getPos();
        double renderX = basePosition.x - camPos.x;
        double renderY = (basePosition.y + offsetY) - camPos.y;
        double renderZ = basePosition.z - camPos.z;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        // билборд: текст всегда лицом к камере
        matrices.multiply(camera.getRotation());

        if (rotationTilt != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationTilt));
        }

        // статичный мировой масштаб — текст не растёт при приближении
        float staticScale = 0.02f * animScale * baseScale;
        matrices.scale(-staticScale, -staticScale, staticScale);

        float fontSize = 24.0f;
        float textWidth = textRenderer.getWidth(displayText);
        float xOffset = -textWidth / 2.0f;
        float yOffset = -fontSize * 0.35f;

        int alphaInt = (int) (alpha * 255.0f);
        int finalColor = (alphaInt << 24) | (colorRgb & 0x00FFFFFF);

        Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(
                new net.minecraft.client.render.BufferAllocator(2048));
        textRenderer.draw(displayText, xOffset, yOffset, finalColor, false, modelMatrix,
                immediate, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        immediate.draw();

        matrices.pop();

        RenderSystem.depthMask(true);
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private static float easeInCubic(float t) {
        return (float) Math.pow(t, 3);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }
}
