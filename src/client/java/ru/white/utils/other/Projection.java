package ru.white.utils.other;


import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.render.RenderUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import org.joml.*;

import java.lang.Math;

import static java.lang.Math.hypot;
import static java.lang.Math.toDegrees;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@UtilityClass
public class Projection implements IMinecraft {

    private static final double NEAR_PLANE = 0.05;

    public Vec3d worldSpaceToScreenSpace(Vec3d pos) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return Vec3d.ZERO;

        int displayHeight = mc.getWindow().getFramebufferHeight();
        // glGetIntegerv(GL_VIEWPORT) — синхронный запрос к драйверу, стопорит пайплайн;
        // в момент наших рендер-ивентов вьюпорт всегда равен фреймбуферу
        int[] viewport = {0, 0, mc.getWindow().getFramebufferWidth(), displayHeight};
        Vector3f target = new Vector3f();

        double deltaX = pos.x - camera.getCameraPos().x;
        double deltaY = pos.y - camera.getCameraPos().y;
        double deltaZ = pos.z - camera.getCameraPos().z;

        Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.0F);
        transformedCoordinates.mul(RenderUtil.Render3D.lastWorldSpaceMatrix);

        Matrix4f matrixProj = new Matrix4f(RenderUtil.Render3D.lastProjMat);
        Matrix4f matrixModel = new Matrix4f(RenderUtil.Render3D.lastModMat);

        matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);

        return new Vec3d(
                target.x / 2.0,
                (displayHeight - target.y) / 2.0,
                target.z
        );
    }

    private Matrix4d toMatrix4d(Matrix4f mat) {
        Matrix4d result = new Matrix4d();
        result.m00(mat.m00()).m01(mat.m01()).m02(mat.m02()).m03(mat.m03());
        result.m10(mat.m10()).m11(mat.m11()).m12(mat.m12()).m13(mat.m13());
        result.m20(mat.m20()).m21(mat.m21()).m22(mat.m22()).m23(mat.m23());
        result.m30(mat.m30()).m31(mat.m31()).m32(mat.m32()).m33(mat.m33());
        return result;
    }

    private double getViewZ(Vec3d pos, Vec3d cameraPos) {
        return getViewZ(pos, cameraPos, toMatrix4d(RenderUtil.Render3D.lastWorldSpaceMatrix), new Vector4d());
    }

    /** Вариант с уже сконвертированной матрицей и переиспользуемым вектором. */
    private double getViewZ(Vec3d pos, Vec3d cameraPos, Matrix4d worldSpace, Vector4d view) {
        view.set(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z, 1.0);
        worldSpace.transform(view);
        return -view.z;
    }

    /**
     * Клип-координаты угла бокса. Матрицы (worldSpace и combined = proj * model)
     * передаются готовыми: раньше они конвертировались и перемножались заново
     * на КАЖДЫЙ из 8 углов, т.е. 9 умножений матриц 4x4 и 32 аллокации Matrix4d
     * на одну сущность на кадр. Значения при этом ровно те же.
     */
    private void worldSpaceToClipSpaceDouble(Vec3d pos, Vec3d cameraPos,
                                             Matrix4d worldSpace, Matrix4d combined,
                                             Vector4d view, double[] out, int offset) {
        view.set(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z, 1.0);
        worldSpace.transform(view);

        out[offset]     = combined.m00() * view.x + combined.m10() * view.y + combined.m20() * view.z + combined.m30() * view.w;
        out[offset + 1] = combined.m01() * view.x + combined.m11() * view.y + combined.m21() * view.z + combined.m31() * view.w;
        out[offset + 2] = combined.m02() * view.x + combined.m12() * view.y + combined.m22() * view.z + combined.m32() * view.w;
        out[offset + 3] = combined.m03() * view.x + combined.m13() * view.y + combined.m23() * view.z + combined.m33() * view.w;
        out[offset + 4] = -view.z;
    }

    /**
     * Клип -> экран. Пишет x,y в {@code out} (z вызывающим не используется),
     * чтобы не плодить Vec3d на каждый угол и каждое ребро.
     */
    private void clipToScreenDouble(double clipX, double clipY, double clipW,
                                    int[] viewport, int displayHeight, double scale,
                                    double[] out) {
        if (Math.abs(clipW) < 1e-14) {
            out[0] = viewport[2] / scale / 2.0;
            out[1] = displayHeight / scale / 2.0;
            return;
        }

        double invW = 1.0 / clipW;
        double ndcX = clipX * invW;
        double ndcY = clipY * invW;

        out[0] = (ndcX * 0.5 + 0.5) * viewport[2] / scale;
        out[1] = (displayHeight - (ndcY * 0.5 + 0.5) * viewport[3]) / scale;
    }

    public static double getDistanceToGround() {
        if (mc.player == null || mc.world == null) return 256;
        for (double y = mc.player.getY(); y > 0; y -= 0.1) {
            if (!mc.world.getBlockState(mc.player.getBlockPos().down((int) ((mc.player.getY() - y) + 1))).isAir()) {
                return mc.player.getY() - y;
            }
        }
        return 256;
    }

    public Vec3d interpolate(Entity entity) {
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        return entity.getLerpedPos(tickDelta);
    }

    public Vec3d interpolate(Entity entity, float tickDelta) {
        return entity.getLerpedPos(tickDelta);
    }

    public Vector4d getVector4D(Entity ent) {
        return getVector4D(ent, mc.getRenderTickCounter().getTickProgress(true));
    }

    public Vector4d getVector4D(Entity ent, float tickDelta) {
        if (ent == null) return null;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return null;

        Vec3d cameraPos = camera.getCameraPos();
        Vec3d interp = ent.getLerpedPos(tickDelta);
        Vec3d entityPos = ent.getEntityPos();
        Box box = ent.getBoundingBox().offset(interp.subtract(entityPos));

        Vec3d boxCenter = box.getCenter();

        // Матрицы конвертируются и перемножаются ОДИН раз на сущность,
        // а не заново на каждый из 8 углов бокса
        Matrix4d worldSpace = toMatrix4d(RenderUtil.Render3D.lastWorldSpaceMatrix);
        Matrix4d combined = toMatrix4d(RenderUtil.Render3D.lastProjMat)
                .mul(toMatrix4d(RenderUtil.Render3D.lastModMat));
        Vector4d view = new Vector4d();

        double centerViewZ = getViewZ(boxCenter, cameraPos, worldSpace, view);
        if (centerViewZ < -5.0) {
            return null;
        }

        int displayHeight = mc.getWindow().getFramebufferHeight();
        int[] viewport = VIEWPORT;
        viewport[2] = mc.getWindow().getFramebufferWidth();
        viewport[3] = displayHeight;
        double scale = 2.0;

        double screenW = mc.getWindow().getFramebufferWidth() / 2.0;
        double screenH = mc.getWindow().getFramebufferHeight() / 2.0;

        Vec3d[] corners = CORNERS;
        corners[0] = new Vec3d(box.minX, box.minY, box.minZ);
        corners[1] = new Vec3d(box.minX, box.minY, box.maxZ);
        corners[2] = new Vec3d(box.maxX, box.minY, box.minZ);
        corners[3] = new Vec3d(box.maxX, box.minY, box.maxZ);
        corners[4] = new Vec3d(box.minX, box.maxY, box.minZ);
        corners[5] = new Vec3d(box.minX, box.maxY, box.maxZ);
        corners[6] = new Vec3d(box.maxX, box.maxY, box.minZ);
        corners[7] = new Vec3d(box.maxX, box.maxY, box.maxZ);

        // clips[i * 5 + {0..4}] = {x, y, z, w, viewZ} — плоский массив вместо
        // массива record'ов ClipResult
        double[] clips = CLIPS;
        for (int i = 0; i < 8; i++) {
            worldSpaceToClipSpaceDouble(corners[i], cameraPos, worldSpace, combined, view, clips, i * 5);
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        int visibleCount = 0;
        double[] screen = SCREEN;

        for (int i = 0; i < 8; i++) {
            if (clips[i * 5 + 4] > NEAR_PLANE) {
                visibleCount++;
                clipToScreenDouble(clips[i * 5], clips[i * 5 + 1], clips[i * 5 + 3],
                        viewport, displayHeight, scale, screen);
                double px = clampScreenX(screen[0], screenW);
                double py = clampScreenY(screen[1], screenH);

                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
        }

        for (int[] edge : EDGES) {
            int o0 = edge[0] * 5;
            int o1 = edge[1] * 5;

            double viewZ0 = clips[o0 + 4];
            double viewZ1 = clips[o1 + 4];

            boolean v0 = viewZ0 > NEAR_PLANE;
            boolean v1 = viewZ1 > NEAR_PLANE;

            if (v0 != v1) {
                double denom = viewZ1 - viewZ0;
                if (Math.abs(denom) < 1e-14) continue;

                double t = (NEAR_PLANE - viewZ0) / denom;
                t = Math.max(0.0, Math.min(1.0, t));

                double clippedX = clips[o0] + t * (clips[o1] - clips[o0]);
                double clippedY = clips[o0 + 1] + t * (clips[o1 + 1] - clips[o0 + 1]);
                double clippedW = clips[o0 + 3] + t * (clips[o1 + 3] - clips[o0 + 3]);

                clipToScreenDouble(clippedX, clippedY, clippedW,
                        viewport, displayHeight, scale, screen);
                double px = clampScreenX(screen[0], screenW);
                double py = clampScreenY(screen[1], screenH);

                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
        }

        if (visibleCount == 0 && minX == Double.MAX_VALUE) {
            return null;
        }

        if (maxX <= minX || maxY <= minY) return null;

        minX = Math.max(-screenW, minX);
        minY = Math.max(-screenH, minY);
        maxX = Math.min(screenW * 2, maxX);
        maxY = Math.min(screenH * 2, maxY);

        return new Vector4d(minX, minY, maxX, maxY);
    }

    // Константа — раньше пересоздавалась (13 массивов) на каждую сущность
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {1, 3}, {2, 3},
            {4, 5}, {4, 6}, {5, 7}, {6, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    // Скретч-буферы: getVector4D вызывается для каждой сущности каждый кадр
    // из одного (рендерного) потока и не вложен сам в себя
    private static final int[] VIEWPORT = new int[4];
    private static final Vec3d[] CORNERS = new Vec3d[8];
    private static final double[] CLIPS = new double[8 * 5];
    private static final double[] SCREEN = new double[2];

    private double clampScreenX(double x, double screenW) {
        return Math.max(-screenW * 2, Math.min(screenW * 3, x));
    }

    private double clampScreenY(double y, double screenH) {
        return Math.max(-screenH * 2, Math.min(screenH * 3, y));
    }

    private boolean isPointInFrontDouble(Vec3d point, Vec3d cameraPos, Camera camera) {
        double toPointX = point.x - cameraPos.x;
        double toPointY = point.y - cameraPos.y;
        double toPointZ = point.z - cameraPos.z;

        double yaw = camera.getYaw();
        double pitch = camera.getPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double cosPitch = Math.cos(pitchRad);
        double lookX = -Math.sin(yawRad) * cosPitch;
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * cosPitch;

        double dot = lookX * toPointX + lookY * toPointY + lookZ * toPointZ;

        return dot > -10.0;
    }

    public boolean canSee(Vec3d vec3d) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return false;
        Vec2f angle = fromVec3d(vec3d.subtract(camera.getCameraPos()));
        return (Math.abs(MathHelper.wrapDegrees(angle.x - camera.getYaw())) < 90 &&
                Math.abs(MathHelper.wrapDegrees(angle.y - camera.getPitch())) < 60) ||
                canSee(new Box(BlockPos.ofFloored(vec3d)));
    }
    public Vec2f fromVec3d(Vec3d vector) {
        return new Vec2f((float) wrapDegrees(toDegrees(Math.atan2(vector.z, vector.x)) - 90), (float) wrapDegrees(toDegrees(-Math.atan2(vector.y, hypot(vector.x, vector.z)))));
    }
    public boolean canSee(Box box) {
        if (box == null || mc.gameRenderer == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return false;

        Vec3d cameraPos = camera.getCameraPos();

        Matrix4f viewMatrix = new Matrix4f().rotation(camera.getRotation());
        Matrix4f projectionMatrix = mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue().floatValue());

        Frustum frustum = new Frustum(viewMatrix, projectionMatrix);
        frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);

        return frustum.isVisible(box);
    }

    public boolean cantSee(Vector4d vec) {
        if (vec == null) return true;

        double screenWidth  = mc.getWindow().getFramebufferWidth()  / 2.0;
        double screenHeight = mc.getWindow().getFramebufferHeight() / 2.0;

        if (Double.isNaN(vec.x) || Double.isNaN(vec.y) || Double.isNaN(vec.z) || Double.isNaN(vec.w)) return true;
        if (Double.isInfinite(vec.x) || Double.isInfinite(vec.y) || Double.isInfinite(vec.z) || Double.isInfinite(vec.w)) return true;

        if (vec.z < -screenWidth || vec.x > screenWidth * 2) return true;
        if (vec.w < -screenHeight || vec.y > screenHeight * 2) return true;

        return false;
    }

    public double centerX(Vector4d vec) {
        return vec.x + (vec.z - vec.x) / 2;
    }

    public boolean isInFrontOfCamera(Vec3d worldPos) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return false;

        return isPointInFrontDouble(worldPos, camera.getCameraPos(), camera);
    }
}