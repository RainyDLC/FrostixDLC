package dev.hatek.client.render.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Рендер частиц как камера-билбордов через 26.2-пайплайн
 * ({@code submitCustomGeometry}, как в KillEffectRenderer/MediaBillboard).
 * Спрайты группируются по паре (текстура, свечение) — один submit на группу за кадр.
 * Чистая реализация с нуля.
 */
public final class ParticleRenderer {
    /** Полная яркость: частицы не затемняются светом мира. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private record Sprite(Vec3 center, float size, float rotationDeg, int argb) {
    }

    private record BatchKey(Identifier texture, boolean emissive) {
    }

    private final Map<BatchKey, List<Sprite>> batches = new LinkedHashMap<>();

    public void clear() {
        this.batches.clear();
    }

    /**
     * Поставить спрайт в очередь на этот кадр.
     *
     * @param emissive true — неоновое свечение (entityTranslucentEmissive),
     *                 false — обычная полупрозрачность (entityTranslucent)
     */
    public void drawTexture(Identifier texture, Vec3 center, float size,
                            float rotationDeg, int argb, boolean emissive) {
        if (texture == null || size <= 0.001f || ParticleColors.alphaOf(argb) <= 0) {
            return;
        }
        this.batches.computeIfAbsent(new BatchKey(texture, emissive), k -> new ArrayList<>())
                .add(new Sprite(center, size, rotationDeg, argb));
    }

    /**
     * Сбросить все очереди в рендер.
     *
     * @param stack          PoseStack из LevelRenderer.submitEntities (без пред-транcляции)
     * @param cameraPos      state.cameraRenderState.pos
     * @param camOrientation state.cameraRenderState.orientation
     */
    public void flush(PoseStack stack, OrderedSubmitNodeCollector ordered,
                      Vec3 cameraPos, Quaternionf camOrientation) {
        if (this.batches.isEmpty()) {
            return;
        }
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camOrientation);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camOrientation);
        Vector3f view = new Vector3f(0.0f, 0.0f, -1.0f).rotate(camOrientation);
        for (Map.Entry<BatchKey, List<Sprite>> entry : this.batches.entrySet()) {
            BatchKey key = entry.getKey();
            RenderType type = key.emissive()
                    ? RenderTypes.entityTranslucentEmissive(key.texture())
                    : RenderTypes.entityTranslucent(key.texture());
            List<Sprite> sprites = entry.getValue();
            ordered.submitCustomGeometry(stack, type,
                    (pose, out) -> emit(pose, out, sprites, cameraPos, right, up, view));
        }
        this.batches.clear();
    }

    /** Вершины — в камера-относительных координатах, PoseStack не трогаем. */
    private static void emit(PoseStack.Pose pose, VertexConsumer out, List<Sprite> sprites,
                             Vec3 cameraPos, Vector3f right, Vector3f up, Vector3f view) {
        Matrix4f matrix = pose.pose();
        // Нормаль смотрит в камеру (против направления взгляда).
        float nx = -view.x;
        float ny = -view.y;
        float nz = -view.z;
        for (Sprite sprite : sprites) {
            float half = sprite.size() * 0.5f;
            float rad = (float) Math.toRadians(sprite.rotationDeg());
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            // Углы квада: (-h,-h), (h,-h), (h,h), (-h,h); UV: (0,0), (1,0), (1,1), (0,1).
            float[] ox = {-half, half, half, -half};
            float[] oy = {-half, -half, half, half};
            float[] u = {0.0f, 1.0f, 1.0f, 0.0f};
            float[] v = {0.0f, 0.0f, 1.0f, 1.0f};
            for (int i = 0; i < 4; i++) {
                float rx = ox[i] * cos - oy[i] * sin;
                float ry = ox[i] * sin + oy[i] * cos;
                float vx = (float) (sprite.center().x - cameraPos.x) + right.x * rx + up.x * ry;
                float vy = (float) (sprite.center().y - cameraPos.y) + right.y * rx + up.y * ry;
                float vz = (float) (sprite.center().z - cameraPos.z) + right.z * rx + up.z * ry;
                out.addVertex(matrix, vx, vy, vz)
                        .setUv(u[i], v[i])
                        .setColor(sprite.argb())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
