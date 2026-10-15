package dev.hatek.client.render.particles.dashlines;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.render.particles.ParticleColors;
import dev.hatek.client.render.particles.ParticleConstants;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рендер неоновых штрихов: тело (3 полосы: край-ядро-край) + неоновое свечение на острие.
 * Порт rtx.kimiko DashLineRenderer под 26.2: вместо кастомных пайплайнов
 * ClientPipelines.WORLD_PARTICLES_COLOR / DASH_LINES_GLOW используются
 * RenderTypes.entityTranslucent / entityTranslucentEmissive с белой 1×1 текстурой
 * (ParticleConstants.WHITE); вершины красятся per-vertex цветом.
 * Геометрия — камера-относительные координаты, PoseStack не трогаем.
 */
public final class DashLineRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int STEPS = 6;
    private static final float CORE_FRACTION = 0.34f;
    private static final float GLOW_FLARE = 2.1f;

    private final float[] path = new float[21];
    private final float[] sides = new float[21];
    private final float[] widths = new float[7];
    private final float[] alphas = new float[7];

    public void render(PoseStack stack, OrderedSubmitNodeCollector ordered, DashLineField field,
                       Vec3 cameraPos, int headColor, int tailColor, float opacity, float glowStrength) {
        List<DashLine> members = field.alive();
        if (members.isEmpty() || opacity <= 0.002f) {
            return;
        }
        ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucent(ParticleConstants.WHITE),
                (pose, out) -> {
                    Matrix4f matrix = pose.pose();
                    for (DashLine line : members) {
                        float fade = line.fade() * opacity;
                        if (fade <= 0.003f || !this.build(line, cameraPos, fade)) {
                            continue;
                        }
                        this.emitBody(matrix, pose, out, headColor, tailColor);
                    }
                });
        if (glowStrength <= 0.002f) {
            return;
        }
        ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucentEmissive(ParticleConstants.WHITE),
                (pose, out) -> {
                    Matrix4f matrix = pose.pose();
                    for (DashLine line : members) {
                        float fade = line.fade() * glowStrength;
                        if (fade <= 0.003f || !this.build(line, cameraPos, fade)) {
                            continue;
                        }
                        this.emitGlow(matrix, pose, out, headColor, tailColor);
                    }
                });
    }

    /** Строит 7 станций вдоль штриха (камера-относительно), боковые векторы, ширины, альфы. */
    private boolean build(DashLine line, Vec3 cameraPos, float fade) {
        float headX = (float) (line.posX() - cameraPos.x);
        float headY = (float) (line.posY() - cameraPos.y);
        float headZ = (float) (line.posZ() - cameraPos.z);
        boolean valid = false;
        for (int station = 0; station < 7; station++) {
            float t = station / 6.0f;
            float reach = line.length() * t;
            float px = headX - line.dirX() * reach;
            float py = headY - line.dirY() * reach;
            float pz = headZ - line.dirZ() * reach;
            int slot = station * 3;
            this.path[slot] = px;
            this.path[slot + 1] = py;
            this.path[slot + 2] = pz;
            // Боковой вектор = dir × (p - camera).
            float sx = line.dirY() * pz - line.dirZ() * py;
            float sy = line.dirZ() * px - line.dirX() * pz;
            float sz = line.dirX() * py - line.dirY() * px;
            float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-5f) {
                sx = -line.dirY();
                sy = line.dirX();
                sz = 0.0f;
                length = (float) Math.sqrt(sx * sx + sy * sy);
                if (length < 1.0E-5f) {
                    sx = 1.0f;
                    sy = 0.0f;
                    sz = 0.0f;
                    length = 1.0f;
                }
            }
            this.sides[slot] = sx / length;
            this.sides[slot + 1] = sy / length;
            this.sides[slot + 2] = sz / length;
            this.widths[station] = line.halfWidth() * this.profile(t, line.bellyBias());
            this.alphas[station] = Mth.clamp(this.alongAlpha(t) * fade, 0.0f, 1.0f);
            if (this.alphas[station] > 0.003f) {
                valid = true;
            }
        }
        return valid;
    }

    private void emitBody(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out, int headColor, int tailColor) {
        for (int station = 0; station < STEPS; station++) {
            float tTop = station / 6.0f;
            float tBottom = (station + 1) / 6.0f;
            if (this.alphas[station] <= 0.003f && this.alphas[station + 1] <= 0.003f) {
                continue;
            }
            int topColor = ParticleColors.lerpColor(headColor, tailColor, tTop);
            int bottomColor = ParticleColors.lerpColor(headColor, tailColor, tBottom);
            int topCore = ParticleColors.multAlpha(topColor, this.alphas[station]);
            int bottomCore = ParticleColors.multAlpha(bottomColor, this.alphas[station + 1]);
            // Края полностью прозрачные (alpha снят) — мягкий спад, как в оригинале.
            int topEdge = topColor & 0xFFFFFF;
            int bottomEdge = bottomColor & 0xFFFFFF;
            float halfTop = this.widths[station];
            float halfBottom = this.widths[station + 1];
            float coreTop = halfTop * CORE_FRACTION;
            float coreBottom = halfBottom * CORE_FRACTION;
            this.emitStrip(matrix, pose, out, station,
                    -halfTop, -coreTop, -halfBottom, -coreBottom, topEdge, topCore, bottomCore, bottomEdge);
            this.emitStrip(matrix, pose, out, station,
                    -coreTop, coreTop, -coreBottom, coreBottom, topCore, topCore, bottomCore, bottomCore);
            this.emitStrip(matrix, pose, out, station,
                    coreTop, halfTop, coreBottom, halfBottom, topCore, topEdge, bottomEdge, bottomCore);
        }
    }

    private void emitGlow(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out, int headColor, int tailColor) {
        for (int station = 0; station < STEPS; station++) {
            float tTop = station / 6.0f;
            float tBottom = (station + 1) / 6.0f;
            float topGlow = this.alphas[station] * this.headGlow(tTop);
            float bottomGlow = this.alphas[station + 1] * this.headGlow(tBottom);
            if (topGlow <= 0.003f && bottomGlow <= 0.003f) {
                continue;
            }
            int topColor = ParticleColors.multAlpha(
                    ParticleColors.lerpColor(headColor, tailColor, tTop), Mth.clamp(topGlow, 0.0f, 1.0f));
            int bottomColor = ParticleColors.multAlpha(
                    ParticleColors.lerpColor(headColor, tailColor, tBottom), Mth.clamp(bottomGlow, 0.0f, 1.0f));
            int topEdge = topColor & 0xFFFFFF;
            int bottomEdge = bottomColor & 0xFFFFFF;
            float halfTop = this.widths[station] * GLOW_FLARE;
            float halfBottom = this.widths[station + 1] * GLOW_FLARE;
            this.emitStrip(matrix, pose, out, station,
                    -halfTop, 0.0f, -halfBottom, 0.0f, topEdge, topColor, bottomColor, bottomEdge);
            this.emitStrip(matrix, pose, out, station,
                    0.0f, halfTop, 0.0f, halfBottom, topColor, topEdge, bottomEdge, bottomColor);
        }
    }

    private void emitStrip(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out, int station,
                           float offsetA0, float offsetA1, float offsetB0, float offsetB1,
                           int colorA0, int colorA1, int colorB1, int colorB0) {
        int a = station * 3;
        int b = (station + 1) * 3;
        vertex(matrix, pose, out,
                this.path[a] + this.sides[a] * offsetA0,
                this.path[a + 1] + this.sides[a + 1] * offsetA0,
                this.path[a + 2] + this.sides[a + 2] * offsetA0, colorA0);
        vertex(matrix, pose, out,
                this.path[a] + this.sides[a] * offsetA1,
                this.path[a + 1] + this.sides[a + 1] * offsetA1,
                this.path[a + 2] + this.sides[a + 2] * offsetA1, colorA1);
        vertex(matrix, pose, out,
                this.path[b] + this.sides[b] * offsetB1,
                this.path[b + 1] + this.sides[b + 1] * offsetB1,
                this.path[b + 2] + this.sides[b + 2] * offsetB1, colorB1);
        vertex(matrix, pose, out,
                this.path[b] + this.sides[b] * offsetB0,
                this.path[b + 1] + this.sides[b + 1] * offsetB0,
                this.path[b + 2] + this.sides[b + 2] * offsetB0, colorB0);
    }

    private static void vertex(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out,
                               float x, float y, float z, int color) {
        out.addVertex(matrix, x, y, z)
                .setUv(0.0f, 0.0f)
                .setColor(color)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    private float profile(float t, float belly) {
        float rise = Mth.clamp(t / Math.max(0.02f, belly), 0.0f, 1.0f);
        float tip = rise * rise * (3.0f - 2.0f * rise);
        float rest = 1.0f - t;
        return tip * (0.16f + 0.84f * rest * rest);
    }

    private float alongAlpha(float t) {
        float rest = 1.0f - t;
        return rest * (0.3f + 0.7f * rest);
    }

    private float headGlow(float t) {
        float x = Mth.clamp(1.0f - t / 0.55f, 0.0f, 1.0f);
        return x * x;
    }
}
