package dev.hatek.client.render.particles.speedlines;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.render.particles.ParticleColors;
import dev.hatek.client.render.particles.ParticleConstants;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Рендер неоновых штрихов: камера-развёрнутые ленты с градиентом
 * (яркая голова → прозрачный хвост), два прохода — тело и свечение.
 * Чистая реализация с нуля, 26.2-пайплайн как в KillEffectRenderer.
 */
public final class SpeedLineRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Во сколько раз шире лента свечения относительно тела. */
    private static final float GLOW_WIDTH = 2.4f;

    /**
     * @param headColor  цвет головы штриха, ARGB (альфа игнорируется)
     * @param tailColor  цвет хвоста штриха, ARGB (альфа игнорируется)
     * @param opacity    общая прозрачность 0..1 («Прозрачность»)
     * @param glow       сила свечения 0..1 («Свечение»); 0 — второй проход пропущен
     */
    public void render(PoseStack stack, OrderedSubmitNodeCollector ordered, SpeedLineField field,
                       Vec3 cameraPos, int headColor, int tailColor, float opacity, float glow) {
        List<SpeedLine> lines = field.alive();
        if (lines.isEmpty() || opacity <= 0.002f) {
            return;
        }
        ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucent(ParticleConstants.WHITE),
                (pose, out) -> {
                    Matrix4f matrix = pose.pose();
                    for (SpeedLine line : lines) {
                        float a = line.envelope() * opacity;
                        if (a > 0.004f) {
                            emitRibbon(matrix, pose, out, line, cameraPos, headColor, tailColor, a, 1.0f);
                        }
                    }
                });
        if (glow <= 0.002f) {
            return;
        }
        ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucentEmissive(ParticleConstants.WHITE),
                (pose, out) -> {
                    Matrix4f matrix = pose.pose();
                    for (SpeedLine line : lines) {
                        float a = line.envelope() * opacity * glow;
                        if (a > 0.004f) {
                            emitRibbon(matrix, pose, out, line, cameraPos, headColor, tailColor, a, GLOW_WIDTH);
                        }
                    }
                });
    }

    /**
     * Лента от головы (line.x/y/z) назад по направлению, ширина сужается к хвосту.
     * Координаты — камера-относительные. Рисуем с обеих сторон (двусторонняя).
     */
    private static void emitRibbon(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out,
                                   SpeedLine line, Vec3 cameraPos,
                                   int headColor, int tailColor, float alpha, float widthMul) {
        float hx = (float) (line.x - cameraPos.x);
        float hy = (float) (line.y - cameraPos.y);
        float hz = (float) (line.z - cameraPos.z);
        // Боковой вектор: перпендикулярен направлению и взгляду из головы в камеру.
        float vx = -hx;
        float vy = -hy;
        float vz = -hz;
        float sx = line.dy * vz - line.dz * vy;
        float sy = line.dz * vx - line.dx * vz;
        float sz = line.dx * vy - line.dy * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-5f) {
            // Штрих летит точно в камеру — берём мировой X как боковой.
            sx = 1.0f;
            sy = 0.0f;
            sz = 0.0f;
            sl = 1.0f;
        }
        sx /= sl;
        sy /= sl;
        sz /= sl;

        float headHalf = line.halfWidth * widthMul;
        float tailHalf = headHalf * 0.25f;
        float tx = hx - line.dx * line.length;
        float ty = hy - line.dy * line.length;
        float tz = hz - line.dz * line.length;

        int head = ParticleColors.multAlpha(headColor, alpha);
        int tail = ParticleColors.multAlpha(tailColor, alpha * 0.12f);

        // Четыре угла ленты.
        float h0x = hx + sx * headHalf;
        float h0y = hy + sy * headHalf;
        float h0z = hz + sz * headHalf;
        float h1x = hx - sx * headHalf;
        float h1y = hy - sy * headHalf;
        float h1z = hz - sz * headHalf;
        float t0x = tx + sx * tailHalf;
        float t0y = ty + sy * tailHalf;
        float t0z = tz + sz * tailHalf;
        float t1x = tx - sx * tailHalf;
        float t1y = ty - sy * tailHalf;
        float t1z = tz - sz * tailHalf;

        // Лицевая сторона.
        quad(matrix, pose, out, h0x, h0y, h0z, head, h1x, h1y, h1z, head, t1x, t1y, t1z, tail, t0x, t0y, t0z, tail);
        // Обратная сторона.
        quad(matrix, pose, out, h1x, h1y, h1z, head, h0x, h0y, h0z, head, t0x, t0y, t0z, tail, t1x, t1y, t1z, tail);
    }

    private static void quad(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer out,
                             float ax, float ay, float az, int ca,
                             float bx, float by, float bz, int cb,
                             float cx, float cy, float cz, int cc,
                             float dx, float dy, float dz, int cd) {
        vertex(matrix, pose, out, ax, ay, az, ca);
        vertex(matrix, pose, out, bx, by, bz, cb);
        vertex(matrix, pose, out, cx, cy, cz, cc);
        vertex(matrix, pose, out, dx, dy, dz, cd);
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
}
