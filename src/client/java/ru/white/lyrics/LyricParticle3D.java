package ru.white.lyrics;

import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.utils.render.font.Font;

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
     * Рендер частицы: мировая позиция проецируется на экран, размер
     * уменьшается с дистанцией (объект в мире, а не экранный текст),
     * окклюзия через рейкаст — за стенами слова скрываются.
     */
    public void render(Camera camera, Font font, String animMode, int colorRgb, long nowMs, float baseScale) {
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

        MinecraftClientBlock:
        {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            Vec3d camPos = camera.getCameraPos();
            Vec3d worldPos = basePosition.add(0, offsetY, 0);

            // 3D-окклюзия: стена между камерой и словом скрывает его
            RaycastContext rc = new RaycastContext(camPos, worldPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player);
            if (mc.world != null && mc.world.raycast(rc).getType() != HitResult.Type.MISS) {
                break MinecraftClientBlock;
            }

            Vec3d screen = ru.white.utils.other.Projection.worldSpaceToScreenSpace(worldPos);
            if (screen.z <= 0 || screen.z >= 1) break MinecraftClientBlock;

            float dist = (float) camPos.distanceTo(worldPos);
            if (dist < 0.5f) break MinecraftClientBlock;

            // статичный мировой размер: видимый размер обратно пропорционален дистанции
            float fontSize = MathHelper.clamp(baseScale * 45.0f / dist, 2.0f, 30.0f) * animScale;

            float textWidth = font.getWidth(displayText, fontSize);
            float x = (float) screen.x - textWidth / 2.0f;
            float y = (float) screen.y - fontSize * 0.35f;

            int alphaInt = (int) (alpha * 255.0f);
            int finalColor = (alphaInt << 24) | (colorRgb & 0x00FFFFFF);
            int shadowColor = ((int) (alpha * 160.0f) << 24);

            font.draw(displayText, x + fontSize * 0.04f, y + fontSize * 0.04f, fontSize, shadowColor & 0xFF000000);
            font.draw(displayText, x, y, fontSize, finalColor);
        }
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
