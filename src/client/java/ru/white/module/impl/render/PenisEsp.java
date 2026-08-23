package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;

/**
 * Penis Esp: статичный «орган» и яички на теле сущностей.
 *
 * Никакой физики и анимаций — геометрия жёстко привязана к тазу:
 * регулируются только длина органа и размер яичек.
 */
@ModuleInfo(
        name = "Penis Esp",
        desc = "Статичный орган с регулировкой длины",
        category = Category.RENDER
)
public class PenisEsp extends Module implements IMinecraft {

    public SliderSetting length = new SliderSetting(this, "Длина", 0.38F, 0.15F, 0.9F, 0.01F);
    public SliderSetting ballSize = new SliderSetting(this, "Размер яиц", 0.10F, 0.04F, 0.22F, 0.005F);
    public BooleanSetting showSelf = new BooleanSetting(this, "На себе", true);
    public BooleanSetting showOthers = new BooleanSetting(this, "На других", true);
    public ModeSetting colorMode = new ModeSetting(this, "Цвет", "Кожа", "Тема");

    // ── рендер ──────────────────────────────────────────────────────────

    private static final int SHAFT_SEGMENTS = 10;
    private static final int SPHERE_STACKS = 8;
    private static final int SPHERE_SLICES = 12;

    /** Направление «света» для дешёвого шейдинга вершин. */
    private static final double LIGHT_X = -0.35D, LIGHT_Y = 0.85D, LIGHT_Z = -0.40D;

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/penis_esp"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .build()
    );

    private static final RenderLayer LAYER = RenderLayer.of(
            "penis_esp",
            RenderSetup.builder(PIPELINE)
                    .translucent()
                    .expectedBufferSize(16384)
                    .build()
    );

    @EventHandler
    public void onRender(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        float tickDelta = e.getTickDelta();
        MatrixStack pose = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer vc = immediate.getBuffer(LAYER);
        Matrix4f matrix = pose.peek().getPositionMatrix();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof ArmorStandEntity) continue;
            if (living.isSpectator() || living.isInvisible()) continue;

            boolean self = living == mc.player;
            if (self ? !showSelf.getValue() : !showOthers.getValue()) continue;

            if (living.squaredDistanceTo(mc.player) > 48 * 48) continue;

            draw(vc, matrix, living, tickDelta, cameraPos);
        }

        immediate.draw();
    }

    // ── отрисовка ───────────────────────────────────────────────────────

    private void draw(VertexConsumer vc, Matrix4f matrix, LivingEntity entity,
                      float td, Vec3d cameraPos) {
        float r = ballSize.getValue();
        float len = length.getValue();

        Vec3d feet = entity.getLerpedPos(td);
        float yaw = MathHelper.lerpAngleDegrees(td, entity.lastYaw, entity.getYaw());
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double rx = -fz, rz = fx;

        double pelvisY = feet.y + entity.getHeight() * 0.42D;
        double baseX = feet.x + fx * 0.055D - cameraPos.x;
        double baseY = pelvisY - cameraPos.y;
        double baseZ = feet.z + fz * 0.055D - cameraPos.z;

        // яички: неподвижно по бокам чуть ниже базы
        double side = r * 1.02D;
        double lx = baseX + rx * side, ly = baseY - r * 1.05D, lz = baseZ + rz * side;
        double rx2 = baseX - rx * side, ry = baseY - r * 1.18D, rz2 = baseZ - rz * side;

        // кончик органа: торчит вперёд-вниз под фиксированным углом
        double tx = baseX + fx * len * 0.62D;
        double ty = baseY - len * 0.40D;
        double tz = baseZ + fz * len * 0.62D;

        int[] col = baseColor(entity);

        // ствол: конус от базы к кончику
        drawTube(vc, matrix, baseX, baseY, baseZ, tx, ty, tz,
                0.058F, 0.034F, col[0], col[1], col[2]);
        // мошонка: связка от базы вниз между яичками
        drawTube(vc, matrix, baseX, baseY, baseZ,
                (lx + rx2) / 2D, ly + r * 0.35D, (lz + rz2) / 2D,
                0.055F, 0.05F, col[0], col[1], col[2]);

        drawSphere(vc, matrix, lx, ly, lz, r, col[0], col[1], col[2]);
        drawSphere(vc, matrix, rx2, ry, rz2, r, col[0], col[1], col[2]);
    }

    private int[] baseColor(LivingEntity entity) {
        if (colorMode.is("Тема")) {
            int c = ColorUtil.fade((int) (entity.getId() * 47L % 360));
            return new int[]{(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF};
        }
        return new int[]{233, 185, 148};
    }

    /** Трубка (конус) между двумя точками из колец вершин. */
    private void drawTube(VertexConsumer vc, Matrix4f matrix,
                          double ax, double ay, double az, double bx, double by, double bz,
                          float radiusA, float radiusB,
                          int cr, int cg, int cb) {
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double axisLen = Math.sqrt(abx * abx + aby * aby + abz * abz);
        if (axisLen < 1.0E-5D) return;
        abx /= axisLen; aby /= axisLen; abz /= axisLen;

        // перпендикулярный базис кольца
        double ux, uy, uz;
        if (Math.abs(aby) > 0.95D) {
            ux = 1; uy = 0; uz = 0;
        } else {
            // u = normalize(cross(axis, up))
            ux = -abz; uy = 0; uz = abx;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1.0E-5D) { ux = 1; uz = 0; } else { ux /= ul; uz /= ul; }
        }
        double vx = aby * uz - abz * uy;
        double vy = abz * ux - abx * uz;
        double vz = abx * uy - aby * ux;

        double[][] ringA = new double[SHAFT_SEGMENTS][3];
        double[][] ringB = new double[SHAFT_SEGMENTS][3];

        for (int j = 0; j < SHAFT_SEGMENTS; j++) {
            double ang = Math.PI * 2 * j / SHAFT_SEGMENTS;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double ox = ux * ca + vx * sa;
            double oy = uy * ca + vy * sa;
            double oz = uz * ca + vz * sa;

            ringA[j][0] = ax + ox * radiusA;
            ringA[j][1] = ay + oy * radiusA;
            ringA[j][2] = az + oz * radiusA;
            ringB[j][0] = bx + ox * radiusB;
            ringB[j][1] = by + oy * radiusB;
            ringB[j][2] = bz + oz * radiusB;
        }

        for (int j = 0; j < SHAFT_SEGMENTS; j++) {
            int j2 = (j + 1) % SHAFT_SEGMENTS;
            double shadeA = 0.66D + 0.42D * ringShade(ux, uy, uz, vx, vy, vz, j);
            double shadeB = 0.66D + 0.42D * ringShade(ux, uy, uz, vx, vy, vz, j);

            quad(vc, matrix,
                    ringA[j][0], ringA[j][1], ringA[j][2],
                    ringB[j][0], ringB[j][1], ringB[j][2],
                    ringB[j2][0], ringB[j2][1], ringB[j2][2],
                    ringA[j2][0], ringA[j2][1], ringA[j2][2],
                    cr, cg, cb, shadeA, shadeB);
        }

        // шапочка на конце
        fan(vc, matrix, bx, by, bz, ringB, cr, cg, cb);
    }

    private double ringShade(double ux, double uy, double uz, double vx, double vy, double vz, int j) {
        double ang = Math.PI * 2 * j / SHAFT_SEGMENTS;
        double nx = ux * Math.cos(ang) + vx * Math.sin(ang);
        double ny = uy * Math.cos(ang) + vy * Math.sin(ang);
        double nz = uz * Math.cos(ang) + vz * Math.sin(ang);
        double d = nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z;
        return Math.max(0, d) + Math.abs(ny) * 0.15D;
    }

    private void fan(VertexConsumer vc, Matrix4f matrix, double cx, double cy, double cz,
                     double[][] ring, int cr, int cg, int cb) {
        for (int j = 0; j < SHAFT_SEGMENTS; j++) {
            int j2 = (j + 1) % SHAFT_SEGMENTS;
            double[] a = ring[j], b = ring[j2];
            tri(vc, matrix, cx, cy, cz, b[0], b[1], b[2], a[0], a[1], a[2], cr, cg, cb, 0.7D);
        }
    }

    /** Низкополигональная сфера с дешёвым шейдингом по нормали. */
    private void drawSphere(VertexConsumer vc, Matrix4f matrix,
                            double cx, double cy, double cz, float r,
                            int cr, int cg, int cb) {
        for (int i = 0; i < SPHERE_STACKS; i++) {
            double phi0 = Math.PI * i / SPHERE_STACKS;
            double phi1 = Math.PI * (i + 1) / SPHERE_STACKS;
            for (int j = 0; j < SPHERE_SLICES; j++) {
                double th0 = Math.PI * 2 * j / SPHERE_SLICES;
                double th1 = Math.PI * 2 * (j + 1) / SPHERE_SLICES;

                double[] a = sph(cx, cy, cz, r, phi0, th0);
                double[] b = sph(cx, cy, cz, r, phi0, th1);
                double[] c = sph(cx, cy, cz, r, phi1, th1);
                double[] d = sph(cx, cy, cz, r, phi1, th0);

                triN(vc, matrix, a, b, c, cr, cg, cb);
                triN(vc, matrix, a, c, d, cr, cg, cb);
            }
        }
    }

    private double[] sph(double cx, double cy, double cz, double r, double phi, double th) {
        double nx = Math.sin(phi) * Math.cos(th);
        double ny = Math.cos(phi);
        double nz = Math.sin(phi) * Math.sin(th);
        // [x, y, z, nx, ny, nz]
        return new double[]{cx + nx * r, cy + ny * r, cz + nz * r, nx, ny, nz};
    }

    /** Треугольник со шейдингом: нормаль вершины восстанавливается из позиции на юнит-сфере. */
    private void triN(VertexConsumer vc, Matrix4f matrix,
                      double[] a, double[] b, double[] c, int cr, int cg, int cb) {
        vertN(vc, matrix, a, cr, cg, cb);
        vertN(vc, matrix, b, cr, cg, cb);
        vertN(vc, matrix, c, cr, cg, cb);
    }

    private void vertN(VertexConsumer vc, Matrix4f matrix, double[] p, int cr, int cg, int cb) {
        double d = p[3] * LIGHT_X + p[4] * LIGHT_Y + p[5] * LIGHT_Z;
        double shade = 0.62D + 0.48D * Math.max(0, d) + Math.max(0, p[4]) * 0.12D;
        vert(vc, matrix, p[0], p[1], p[2], cr, cg, cb, shade);
    }

    private void quad(VertexConsumer vc, Matrix4f matrix,
                      double ax, double ay, double az,
                      double bx, double by, double bz,
                      double cx, double cy, double cz,
                      double dx, double dy, double dz,
                      int cr, int cg, int cb, double shadeA, double shadeB) {
        vert(vc, matrix, ax, ay, az, cr, cg, cb, shadeA);
        vert(vc, matrix, bx, by, bz, cr, cg, cb, shadeB);
        vert(vc, matrix, cx, cy, cz, cr, cg, cb, shadeB);

        vert(vc, matrix, ax, ay, az, cr, cg, cb, shadeA);
        vert(vc, matrix, cx, cy, cz, cr, cg, cb, shadeB);
        vert(vc, matrix, dx, dy, dz, cr, cg, cb, shadeA);
    }

    private void tri(VertexConsumer vc, Matrix4f matrix,
                     double ax, double ay, double az,
                     double bx, double by, double bz,
                     double cx, double cy, double cz,
                     int cr, int cg, int cb, double shade) {
        vert(vc, matrix, ax, ay, az, cr, cg, cb, shade);
        vert(vc, matrix, bx, by, bz, cr, cg, cb, shade);
        vert(vc, matrix, cx, cy, cz, cr, cg, cb, shade);
    }

    private void vert(VertexConsumer vc, Matrix4f matrix,
                      double x, double y, double z,
                      int cr, int cg, int cb, double shade) {
        int color = ColorUtil.getColor(
                (int) MathHelper.clamp(cr * shade, 0, 255),
                (int) MathHelper.clamp(cg * shade, 0, 255),
                (int) MathHelper.clamp(cb * shade, 0, 255),
                235);
        vc.vertex(matrix, (float) x, (float) y, (float) z).color(color);
    }
}
