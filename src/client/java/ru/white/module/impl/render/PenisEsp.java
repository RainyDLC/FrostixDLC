package ru.white.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
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
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;

/**
 * Penis ESP (порт из Aurora): статичные яички, ствол-цилиндр и головка на игроках.
 *
 * Регулируются длина, толщина, размер яичек и градация (сглаженность сетки),
 * цвет тела и головки задаются отдельно. Опционально видно сквозь стены.
 */
@ModuleInfo(
        name = "PenisESP",
        desc = "Статичный орган на игроках с настройкой размеров",
        category = Category.RENDER
)
public class PenisEsp extends Module implements IMinecraft {

    public BooleanSetting onlyOwn = new BooleanSetting(this, "Только свой", false);
    public SliderSetting penisLength = new SliderSetting(this, "Длина", 1.5F, 0.1F, 3F, 0.1F);
    public SliderSetting penisThickness = new SliderSetting(this, "Толщина", 0.07F, 0.01F, 0.3F, 0.01F);
    public SliderSetting ballSize = new SliderSetting(this, "Размер яиц", 0.15F, 0.05F, 0.5F, 0.01F);
    public SliderSetting gradation = new SliderSetting(this, "Градация", 30F, 10F, 100F, 1F);
    public BooleanSetting throughWalls = new BooleanSetting(this, "Сквозь стены", false);
    public ColorSetting penisColor = new ColorSetting(this, "Цвет", 0xFFE9B994);
    public ColorSetting headColor = new ColorSetting(this, "Цвет головки", 0xFFE09B85);

    // ── рендер ──────────────────────────────────────────────────────────

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline SOLID_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/penis_esp"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .build()
    );

    private static final RenderPipeline WALLHACK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/penis_esp_wallhack"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderLayer SOLID_LAYER = RenderLayer.of(
            "penis_esp",
            RenderSetup.builder(SOLID_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 18)
                    .build()
    );

    private static final RenderLayer WALLHACK_LAYER = RenderLayer.of(
            "penis_esp_wallhack",
            RenderSetup.builder(WALLHACK_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 18)
                    .build()
    );

    @EventHandler
    public void onRender(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        float td = e.getTickDelta();
        MatrixStack pose = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f matrix = pose.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer vc = immediate.getBuffer(throughWalls.getValue() ? WALLHACK_LAYER : SOLID_LAYER);

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player.isSpectator() || player.isInvisible()) continue;
            if (onlyOwn.getValue() && player != mc.player) continue;

            draw(vc, matrix, player, td, cameraPos);
        }

        immediate.draw();
    }

    // ── геометрия ───────────────────────────────────────────────────────

    private void draw(VertexConsumer vc, Matrix4f matrix, AbstractClientPlayerEntity player,
                      float td, Vec3d cameraPos) {
        double len = penisLength.getValue();
        double width = penisThickness.getValue();
        double balls = ballSize.getValue();

        Vec3d feet = player.getLerpedPos(td);

        double yawRad = Math.toRadians(player.getYaw());
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double lx = -fz, lz = fx; // ось влево

        double baseY = feet.y + player.getHeight() / 2.4D - cameraPos.y;
        double baseX = feet.x - cameraPos.x;
        double baseZ = feet.z - cameraPos.z;

        // яички по бокам чуть ниже базы
        double sideOff = balls * 0.9D;
        double drop = balls * 0.1D;
        double leftX = baseX + lx * sideOff, leftY = baseY - drop, leftZ = baseZ + lz * sideOff;
        double rightX = baseX - lx * sideOff, rightY = baseY - drop, rightZ = baseZ - lz * sideOff;

        // ствол начинается впереди базы и тянется вперёд на длину
        double offset = MathHelper.clamp(0.1D + balls * 0.5D, 0.1D, 0.25D);
        double startX = baseX + fx * offset, startY = baseY + balls * 0.15D, startZ = baseZ + fz * offset;
        double tipX = startX + fx * len, tipY = startY, tipZ = startZ + fz * len;

        int[] body = argb(penisColor.getValue());
        int[] head = argb(headColor.getValue());

        // качество сетки из градации
        int g = gradation.getValue().intValue();
        int stacks = Math.max(3, g / 4);
        int slices = Math.max(6, g * 2 / 5);
        int tubeSegs = MathHelper.clamp(g / 3, 6, 24);

        drawSphere(vc, matrix, leftX, leftY, leftZ, balls, stacks, slices, body);
        drawSphere(vc, matrix, rightX, rightY, rightZ, balls, stacks, slices, body);
        drawTube(vc, matrix, startX, startY, startZ, tipX, tipY, tipZ, (float) width, tubeSegs, body);
        drawSphere(vc, matrix, tipX, tipY, tipZ, balls * 0.8D, stacks, slices, head);
    }

    private int[] argb(int color) {
        return new int[]{(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF};
    }

    /** Трубка-цилиндр между двумя точками из колец вершин. */
    private void drawTube(VertexConsumer vc, Matrix4f matrix,
                          double ax, double ay, double az, double bx, double by, double bz,
                          float radius, int segs, int[] col) {
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double axisLen = Math.sqrt(abx * abx + aby * aby + abz * abz);
        if (axisLen < 1.0E-5D) return;
        abx /= axisLen; aby /= axisLen; abz /= axisLen;

        // перпендикулярный базис кольца
        double ux, uy, uz;
        if (Math.abs(aby) > 0.95D) {
            ux = 1; uy = 0; uz = 0;
        } else {
            ux = -abz; uy = 0; uz = abx;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1.0E-5D) { ux = 1; uz = 0; } else { ux /= ul; uz /= ul; }
        }
        double vx = aby * uz - abz * uy;
        double vy = abz * ux - abx * uz;
        double vz = abx * uy - aby * ux;

        double[][] ringA = new double[segs][3];
        double[][] ringB = new double[segs][3];

        for (int j = 0; j < segs; j++) {
            double ang = Math.PI * 2 * j / segs;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double ox = ux * ca + vx * sa;
            double oy = uy * ca + vy * sa;
            double oz = uz * ca + vz * sa;

            ringA[j][0] = ax + ox * radius;
            ringA[j][1] = ay + oy * radius;
            ringA[j][2] = az + oz * radius;
            ringB[j][0] = bx + ox * radius;
            ringB[j][1] = by + oy * radius;
            ringB[j][2] = bz + oz * radius;
        }

        for (int j = 0; j < segs; j++) {
            int j2 = (j + 1) % segs;
            double shadeA = 0.66D + 0.42D * ringShade(ux, uy, uz, vx, vy, vz, j, segs);
            quad(vc, matrix,
                    ringA[j][0], ringA[j][1], ringA[j][2],
                    ringB[j][0], ringB[j][1], ringB[j][2],
                    ringB[j2][0], ringB[j2][1], ringB[j2][2],
                    ringA[j2][0], ringA[j2][1], ringA[j2][2],
                    col, shadeA);
        }

        // заглушки торцов
        fan(vc, matrix, ax, ay, az, ringA, col, 0.55D);
        fan(vc, matrix, bx, by, bz, ringB, col, 0.75D);
    }

    private double ringShade(double ux, double uy, double uz, double vx, double vy, double vz,
                             int j, int segs) {
        double ang = Math.PI * 2 * j / segs;
        double nx = ux * Math.cos(ang) + vx * Math.sin(ang);
        double ny = uy * Math.cos(ang) + vy * Math.sin(ang);
        double nz = uz * Math.cos(ang) + vz * Math.sin(ang);
        double d = nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z;
        return Math.max(0, d) + Math.abs(ny) * 0.15D;
    }

    private void fan(VertexConsumer vc, Matrix4f matrix, double cx, double cy, double cz,
                     double[][] ring, int[] col, double shade) {
        int segs = ring.length;
        for (int j = 0; j < segs; j++) {
            int j2 = (j + 1) % segs;
            double[] a = ring[j], b = ring[j2];
            tri(vc, matrix, cx, cy, cz, b[0], b[1], b[2], a[0], a[1], a[2], col, shade);
        }
    }

    /** Сферическая сетка с дешёвым шейдингом по нормали. */
    private void drawSphere(VertexConsumer vc, Matrix4f matrix,
                            double cx, double cy, double cz, double r,
                            int stacks, int slices, int[] col) {
        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI * i / stacks;
            double phi1 = Math.PI * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                double th0 = Math.PI * 2 * j / slices;
                double th1 = Math.PI * 2 * (j + 1) / slices;

                double[] a = sph(cx, cy, cz, r, phi0, th0);
                double[] b = sph(cx, cy, cz, r, phi0, th1);
                double[] c = sph(cx, cy, cz, r, phi1, th1);
                double[] d = sph(cx, cy, cz, r, phi1, th0);

                triN(vc, matrix, a, b, c, col);
                triN(vc, matrix, a, c, d, col);
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

    /** Направление «света» для дешёвого шейдинга вершин. */
    private static final double LIGHT_X = -0.35D, LIGHT_Y = 0.85D, LIGHT_Z = -0.40D;

    private void triN(VertexConsumer vc, Matrix4f matrix,
                      double[] a, double[] b, double[] c, int[] col) {
        vertN(vc, matrix, a, col);
        vertN(vc, matrix, b, col);
        vertN(vc, matrix, c, col);
    }

    private void vertN(VertexConsumer vc, Matrix4f matrix, double[] p, int[] col) {
        double d = p[3] * LIGHT_X + p[4] * LIGHT_Y + p[5] * LIGHT_Z;
        double shade = 0.62D + 0.48D * Math.max(0, d) + Math.max(0, p[4]) * 0.12D;
        vert(vc, matrix, p[0], p[1], p[2], col, shade);
    }

    private void quad(VertexConsumer vc, Matrix4f matrix,
                      double ax, double ay, double az,
                      double bx, double by, double bz,
                      double cx, double cy, double cz,
                      double dx, double dy, double dz,
                      int[] col, double shade) {
        vert(vc, matrix, ax, ay, az, col, shade);
        vert(vc, matrix, bx, by, bz, col, shade);
        vert(vc, matrix, cx, cy, cz, col, shade);

        vert(vc, matrix, ax, ay, az, col, shade);
        vert(vc, matrix, cx, cy, cz, col, shade);
        vert(vc, matrix, dx, dy, dz, col, shade);
    }

    private void tri(VertexConsumer vc, Matrix4f matrix,
                     double ax, double ay, double az,
                     double bx, double by, double bz,
                     double cx, double cy, double cz,
                     int[] col, double shade) {
        vert(vc, matrix, ax, ay, az, col, shade);
        vert(vc, matrix, bx, by, bz, col, shade);
        vert(vc, matrix, cx, cy, cz, col, shade);
    }

    private void vert(VertexConsumer vc, Matrix4f matrix,
                      double x, double y, double z,
                      int[] col, double shade) {
        int color = ColorUtil.getColor(
                (int) MathHelper.clamp(col[0] * shade, 0, 255),
                (int) MathHelper.clamp(col[1] * shade, 0, 255),
                (int) MathHelper.clamp(col[2] * shade, 0, 255),
                col[3]);
        vc.vertex(matrix, (float) x, (float) y, (float) z).color(color);
    }
}
