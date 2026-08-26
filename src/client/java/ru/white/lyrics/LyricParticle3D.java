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
        String displayText = this.text;

        boolean isTypewriter = animMode != null && (
                animMode.equalsIgnoreCase("Печатание") ||
                animMode.equalsIgnoreCase("Typewriter")
        );

        if (isTypewriter) {
            // Режим "Печатание": буквы быстро печатаются одна за другой
            int totalLen = this.text.length();
            // Печать всей строки за первые ~35-40 мс на символ (или макс 40% от длительности строки)
            long typingDurationMs = Math.min((long) (durationMs * 0.40f), Math.max(250L, totalLen * 38L));

            if (elapsed < typingDurationMs) {
                float typeFraction = (float) elapsed / (float) typingDurationMs;
                int visibleChars = MathHelper.clamp((int) Math.ceil(totalLen * typeFraction), 1, totalLen);
                displayText = this.text.substring(0, visibleChars);
                alpha = Math.min(1.0f, (float) elapsed / 60.0f);
                offsetY = progress * (floatHeight * 0.25);
            } else if (progress < 0.82f) {
                displayText = this.text;
                alpha = 1.0f;
                offsetY = progress * (floatHeight * 0.25);
            } else {
                displayText = this.text;
                float fadeProgress = (progress - 0.82f) / 0.18f;
                alpha = 1.0f - easeInCubic(fadeProgress);
                offsetY = progress * (floatHeight * 0.25);
            }
        } else {
            // Режим "Плавное появление": каждое слово плавно появляется, увеличиваясь и проявляясь
            if (progress < 0.20f) {
                float t = progress / 0.20f;
                alpha = easeOutCubic(t);
                offsetY = -0.07 * (1.0f - easeOutCubic(t)) + progress * (floatHeight * 0.25);
                animScale = 0.88f + 0.12f * easeOutBack(t);
            } else if (progress < 0.80f) {
                alpha = 1.0f;
                offsetY = progress * (floatHeight * 0.25);
                animScale = 1.0f;
            } else {
                float t = (progress - 0.80f) / 0.20f;
                alpha = 1.0f - easeInCubic(t);
                offsetY = progress * (floatHeight * 0.25) + 0.03 * easeInCubic(t);
                animScale = 1.0f - 0.03f * t;
            }
        }

        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.005f || displayText == null || displayText.isBlank()) return;

        MinecraftClientBlock:
        {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.player == null) break MinecraftClientBlock;

            Vec3d camPos = camera.getCameraPos();
            Vec3d worldPos = basePosition.add(0, offsetY, 0);

            // 3D-окклюзия: если между камерой и точкой стена, подтягиваем позицию перед стеной
            if (mc.world != null) {
                RaycastContext rc = new RaycastContext(camPos, worldPos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, mc.player);
                HitResult hit = mc.world.raycast(rc);
                if (hit.getType() != HitResult.Type.MISS) {
                    double hitDist = hit.getPos().distanceTo(camPos);
                    if (hitDist < 0.6) {
                        break MinecraftClientBlock;
                    }
                    Vec3d toCam = camPos.subtract(worldPos).normalize();
                    worldPos = hit.getPos().add(toCam.multiply(0.20));
                }
            }

            Vec3d screen = ru.white.utils.other.Projection.worldSpaceToScreenSpace(worldPos);
            if (screen.z <= 0 || screen.z >= 1) break MinecraftClientBlock;

            float dist = (float) camPos.distanceTo(worldPos);
            if (dist < 0.35f) break MinecraftClientBlock;

            // Статичный мировой размер шрифта, обратно пропорциональный дистанции
            float fontSize = MathHelper.clamp(baseScale * 46.0f / dist, 4.0f, 32.0f) * animScale;

            float textWidth = font.getWidth(displayText, fontSize);
            float x = (float) screen.x - textWidth / 2.0f;
            float y = (float) screen.y - fontSize * 0.35f;

            int alphaInt = (int) (alpha * 255.0f);
            int finalColor = (alphaInt << 24) | (colorRgb & 0x00FFFFFF);
            int shadowColor = ((int) (alpha * 170.0f) << 24);

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
