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
 * Батчинг камера-билбордов частиц для 26.2.
 *
 * Порт rtx.kimiko ParticleRenderer, переписанный под рендер-пайплайн FrostixDLC:
 * вместо VertexConsumerProvider.Immediate + provider.draw(type) используется
 * submitCustomGeometry (как в KillEffectRenderer/MediaBillboard, origin/main).
 * Квады группируются по (текстура, bloom) — один submit на текстуру за кадр.
 *
 * bloom=true  → RenderTypes.entityTranslucentEmissive (замена кастомного
 *                WORLD_PARTICLES_GLOW-пайплайна kimiko; отдельного glow-пайплайна
 *                в FrostixDLC нет).
 * bloom=false → RenderTypes.entityTranslucent.
 */
public final class ParticleRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;

    private record Quad(Vec3 center, float size, float rotationDeg, int color) {
    }

    private record BatchKey(Identifier id, boolean bloom) {
    }

    private final Map<BatchKey, List<Quad>> batches = new LinkedHashMap<>();

    public void clear() {
        this.batches.clear();
    }

    public void drawTexture(Identifier texture, Vec3 center, float size, float rotationDeg, int color, boolean bloom) {
        if (size <= 0.001f || ParticleColors.alphaOf(color) <= 0) {
            return;
        }
        this.batches.computeIfAbsent(new BatchKey(texture, bloom), k -> new ArrayList<>())
                .add(new Quad(center, size, rotationDeg, color));
    }

    /**
     * @param stack          PoseStack из LevelRenderer.submitEntities (камера-относительные координаты).
     * @param camOrientation state.cameraRenderState.orientation.
     */
    public void flush(PoseStack stack, OrderedSubmitNodeCollector ordered, Vec3 cameraPos, Quaternionf camOrientation) {
        if (this.batches.isEmpty()) {
            return;
        }
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camOrientation);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camOrientation);
        Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f).rotate(camOrientation);
        for (Map.Entry<BatchKey, List<Quad>> entry : this.batches.entrySet()) {
            RenderType type = entry.getKey().bloom()
                    ? RenderTypes.entityTranslucentEmissive(entry.getKey().id())
                    : RenderTypes.entityTranslucent(entry.getKey().id());
            List<Quad> quads = entry.getValue();
            ordered.submitCustomGeometry(stack, type, (pose, out) ->
                    emitBatch(pose, out, quads, cameraPos, right, up, forward));
        }
        this.batches.clear();
    }

    private static void emitBatch(PoseStack.Pose pose, VertexConsumer out, List<Quad> quads,
                                  Vec3 cameraPos, Vector3f right, Vector3f up, Vector3f forward) {
        Matrix4f matrix = pose.pose();
        for (Quad quad : quads) {
            float half = quad.size() / 2.0f;
            float rad = (float) Math.toRadians(quad.rotationDeg());
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            // Углы (-h,-h),(h,-h),(h,h),(-h,h); UV (0,0),(1,0),(1,1),(0,1) — как в kimiko.
            float[] cx = {-half, half, half, -half};
            float[] cy = {-half, -half, half, half};
            float[] u = {0.0f, 1.0f, 1.0f, 0.0f};
            float[] v = {0.0f, 0.0f, 1.0f, 1.0f};
            for (int i = 0; i < 4; i++) {
                float rx = cx[i] * cos - cy[i] * sin;
                float ry = cx[i] * sin + cy[i] * cos;
                float vx = (float) (quad.center().x - cameraPos.x) + right.x * rx + up.x * ry;
                float vy = (float) (quad.center().y - cameraPos.y) + right.y * rx + up.y * ry;
                float vz = (float) (quad.center().z - cameraPos.z) + right.z * rx + up.z * ry;
                out.addVertex(matrix, vx, vy, vz)
                        .setUv(u[i], v[i])
                        .setColor(quad.color())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, -forward.x, -forward.y, -forward.z);
            }
        }
    }
}
