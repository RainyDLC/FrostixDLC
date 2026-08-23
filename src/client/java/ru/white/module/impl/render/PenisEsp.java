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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Penis Esp: «орган» и яички на теле сущностей с честной физикой.
 *
 * Яички — пружина-демпфер в мировых координатах: точка покоя следует за тазом,
 * при прыжке якорь улетает вверх, яички запаздывают и растягиваются, на приземлении
 * дают отскок от земли и пару затухающих колебаний. При беге качаются в противофазе.
 */
@ModuleInfo(
        name = "Penis Esp",
        desc = "Реалистичная физика яичек при прыжке и беге",
        category = Category.RENDER
)
public class PenisEsp extends Module implements IMinecraft {

    public SliderSetting ballSize = new SliderSetting(this, "Размер яиц", 0.10F, 0.04F, 0.22F, 0.005F);
    public SliderSetting stiffness = new SliderSetting(this, "Упругость", 120F, 20F, 260F, 5F);
    public SliderSetting sag = new SliderSetting(this, "Просадка", 0.4F, 0F, 1F, 0.05F);
    public BooleanSetting showSelf = new BooleanSetting(this, "На себе", true);
    public BooleanSetting showOthers = new BooleanSetting(this, "На других", true);
    public ModeSetting colorMode = new ModeSetting(this, "Цвет", "Кожа", "Тема");

    // ── физика ──────────────────────────────────────────────────────────

    private static class PointMass {
        double x, y, z, vx, vy, vz;

        void teleport(double px, double py, double pz) {
            x = px; y = py; z = pz;
            vx = vy = vz = 0;
        }
    }

    /** Физическое состояние одного игрока: два яичка. */
    private static class EntityPhysics {
        final PointMass left = new PointMass();
        final PointMass right = new PointMass();
        boolean initialized;
        double walkPhase;
        long lastSeenMs = System.currentTimeMillis();
    }

    private final Map<Integer, EntityPhysics> states = new HashMap<>();
    private long lastFrameNanos = System.nanoTime();

    // ── рендер ──────────────────────────────────────────────────────────

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
        if (mc.player == null || mc.world == null) {
            states.clear();
            return;
        }

        long nowNanos = System.nanoTime();
        float dt = MathHelper.clamp((nowNanos - lastFrameNanos) / 1_000_000_000F, 1 / 220F, 1 / 30F);
        lastFrameNanos = nowNanos;

        long nowMs = System.currentTimeMillis();
        float tickDelta = e.getTickDelta();
        MatrixStack pose = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        Iterator<Map.Entry<Integer, EntityPhysics>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMs - it.next().getValue().lastSeenMs > 2000L) it.remove();
        }

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

            EntityPhysics phys = states.computeIfAbsent(living.getId(), k -> new EntityPhysics());
            phys.lastSeenMs = nowMs;

            simulate(phys, living, tickDelta, dt);
            draw(vc, matrix, phys, living, tickDelta, cameraPos);
        }

        immediate.draw();
    }

    // ── симуляция ───────────────────────────────────────────────────────

    private void simulate(EntityPhysics phys, LivingEntity entity, float td, float dt) {
        float r = ballSize.getValue();

        Vec3d feet = entity.getLerpedPos(td);
        float yaw = MathHelper.lerpAngleDegrees(td, entity.lastYaw, entity.getYaw());
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double rx = -fz, rz = fx;

        double height = entity.getHeight();
        double pelvisY = feet.y + height * 0.42D;

        // база крепления чуть впереди таза
        double bx = feet.x + fx * 0.055D;
        double by = pelvisY;
        double bz = feet.z + fz * 0.055D;

        // бег: фаза раскачки от горизонтальной скорости
        double hSpeed = Math.hypot(entity.getX() - entity.lastX, entity.getZ() - entity.lastZ) * 20D;
        phys.walkPhase += hSpeed * dt * 2.6D;
        double sway = Math.sin(phys.walkPhase) * Math.min(hSpeed, 0.28D) * 0.16D;

        // точки покоя
        double side = r * 1.02D;
        double hangL = r * (1.05D), hangR = r * (1.18D);

        double lRestX = bx + rx * side + fx * sway;
        double lRestY = by - hangL;
        double lRestZ = bz + rz * side + fz * sway;

        double rRestX = bx - rx * side - fx * sway;
        double rRestY = by - hangR;
        double rRestZ = bz - rz * side - fz * sway;

        double gravity = 13D + sag.getValue() * 26D;
        double k = stiffness.getValue();
        double damping = Math.pow(0.84D, dt * 20D);
        double groundY = feet.y;

        if (!phys.initialized) {
            phys.initialized = true;
            phys.left.teleport(lRestX, lRestY, lRestZ);
            phys.right.teleport(rRestX, rRestY, rRestZ);
            return;
        }

        integrate(phys.left, lRestX, lRestY, lRestZ, r * 0.92D, k, gravity, damping, groundY, dt);
        integrate(phys.right, rRestX, rRestY, rRestZ, r * 0.92D, k, gravity, damping, groundY, dt);

        // привязка к телу: физика остаётся, но в локальных осях игрока
        // яйца не уезжают назад за спину и не разбегаются далеко в стороны
        constrainToBody(phys.left, lRestX, lRestY, lRestZ, fx, fz, rx, rz, r * 1.5D, -r * 0.55D, r * 2.2D);
        constrainToBody(phys.right, rRestX, rRestY, rRestZ, fx, fz, rx, rz, r * 1.5D, -r * 0.55D, r * 2.2D);
    }

    /**
     * Локальные границы относительно точки покоя: вперёд/назад по оси взгляда,
     * вбок по оси права. При выходе за границу позиция возвращается, а скорость,
     * толкавшая наружу, гасится (упругая стенка) — колебание сохраняется.
     */
    private void constrainToBody(PointMass p, double restX, double restY, double restZ,
                                 double fx, double fz, double rx, double rz,
                                 double maxFwd, double minFwd, double maxSide) {
        double dx = p.x - restX, dz = p.z - restZ;

        double fwd = dx * fx + dz * fz;
        double side = dx * rx + dz * rz;

        double newFwd = MathHelper.clamp(fwd, minFwd, maxFwd);
        double newSide = MathHelper.clamp(side, -maxSide, maxSide);

        if (newFwd != fwd || newSide != side) {
            // возврат позиции в границы
            p.x += fx * (newFwd - fwd) + rx * (newSide - side);
            p.z += fz * (newFwd - fwd) + rz * (newSide - side);

            // гашение скорости, толкавшей за границу
            double vFwd = p.vx * fx + p.vz * fz;
            double vSide = p.vx * rx + p.vz * rz;

            if ((fwd < minFwd && vFwd < 0) || (fwd > maxFwd && vFwd > 0)) vFwd *= -0.25D;
            if ((side < -maxSide && vSide < 0) || (side > maxSide && vSide > 0)) vSide *= -0.25D;

            p.vx = vFwd * fx + vSide * rx;
            p.vz = vFwd * fz + vSide * rz;
        }
    }

    /**
     * Пружина-демпфер: тянет точку к точке покоя, гравитация просаживает вниз,
     * пол отбивает с затуханием. Прыжок/приземление игрока двигают якорь —
     * точка запаздывает и начинает колебаться сама.
     */
    private void integrate(PointMass p, double restX, double restY, double restZ,
                           double radius, double k, double gravity, double damping,
                           double groundY, float dt) {
        p.vx += (restX - p.x) * k * dt;
        p.vy += (restY - p.y) * k * dt - gravity * dt;
        p.vz += (restZ - p.z) * k * dt;

        p.vx *= damping;
        p.vy *= damping;
        p.vz *= damping;

        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.z += p.vz * dt;

        // ограничение растяжения — «кожа» не резиновая до бесконечности
        double dx = p.x - restX, dy = p.y - restY, dz = p.z - restZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double maxStretch = radius * 3.2D;
        if (dist > maxStretch && dist > 1.0E-4D) {
            double f = maxStretch / dist;
            p.x = restX + dx * f;
            p.y = restY + dy * f;
            p.z = restZ + dz * f;
        }

        // столкновение с землёй: отскок + трение
        double floor = groundY + radius * 0.55D;
        if (p.y < floor) {
            p.y = floor;
            if (p.vy < 0) p.vy = -p.vy * 0.45D;
            p.vx *= 0.72D;
            p.vz *= 0.72D;
        }
    }

    // ── отрисовка ───────────────────────────────────────────────────────

    private void draw(VertexConsumer vc, Matrix4f matrix, EntityPhysics phys,
                      LivingEntity entity, float td, Vec3d cameraPos) {
        float r = ballSize.getValue();

        int[] col = baseColor(entity);

        double lx = phys.left.x - cameraPos.x, ly = phys.left.y - cameraPos.y, lz = phys.left.z - cameraPos.z;
        double rx2 = phys.right.x - cameraPos.x, ry = phys.right.y - cameraPos.y, rz2 = phys.right.z - cameraPos.z;

        // только яички — сам орган не рисуется
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
