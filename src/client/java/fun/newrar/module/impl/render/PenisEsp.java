package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.annotation.IMinecraft;

@ModuleInfo(
        name = "PenisESP",
        desc = "Шуточная трехмерная каркасная модель на персонажах с возможностью кастомизации",
        category = Category.RENDER
)
public class PenisEsp extends Module implements IMinecraft {
    public BooleanSetting onlyOwn = new BooleanSetting(this, "Только свой", false);
    public SliderSetting penisLength = new SliderSetting(this, "Длина", 1.5F, 0.1F, 3F, 0.1F);
    public SliderSetting penisThickness = new SliderSetting(this, "Толщина", 0.07F, 0.01F, 0.3F, 0.01F);
    public SliderSetting ballSize = new SliderSetting(this, "Размер яиц", 0.15F, 0.05F, 0.5F, 0.01F);
    public SliderSetting gradation = new SliderSetting(this, "Градация", 30F, 10F, 100F, 1F);
    public ColorSetting penisColor = new ColorSetting(this, "Цвет", 0xFFFFFFFF);
    public ColorSetting headColor = new ColorSetting(this, "Цвет головки", 0xFFFFFFFF);

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/world/penis_esp"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "penis_esp",
            RenderSetup.builder(LINE_PIPELINE)
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
        VertexConsumer vc = immediate.getBuffer(LINE_LAYER);

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player.isSpectator() || player.isInvisible()) continue;
            if (onlyOwn.getValue() && player != mc.player) continue;

            draw(vc, matrix, player, td, cameraPos);
        }

        immediate.draw();
    }

    private void draw(VertexConsumer vc, Matrix4f matrix, AbstractClientPlayerEntity player,
                      float td, Vec3d cameraPos) {
        double len = penisLength.getValue();
        double width = penisThickness.getValue();
        double balls = ballSize.getValue();

        Vec3d feet = player.getLerpedPos(td);

        double yawRad = Math.toRadians(-player.getYaw());
        double sinYaw = Math.sin(yawRad), cosYaw = Math.cos(yawRad);

        double baseY = feet.y + player.getHeight() / 2.4D - cameraPos.y;
        double baseX = feet.x - cameraPos.x;
        double baseZ = feet.z - cameraPos.z;

        double sideOff = balls * 0.9D;
        double leftX = baseX + Math.sin(yawRad - Math.PI / 2) * sideOff;
        double leftY = baseY - balls * 0.1D;
        double leftZ = baseZ + Math.cos(yawRad - Math.PI / 2) * sideOff;
        double rightX = baseX + Math.sin(yawRad + Math.PI / 2) * sideOff;
        double rightY = baseY - balls * 0.1D;
        double rightZ = baseZ + Math.cos(yawRad + Math.PI / 2) * sideOff;

        double offset = MathHelper.clamp(0.1D + balls * 0.5D, 0.1D, 0.25D);
        double startX = baseX + sinYaw * offset;
        double startY = baseY + balls * 0.15D;
        double startZ = baseZ + cosYaw * offset;
        double tipX = startX + sinYaw * len;
        double tipY = startY;
        double tipZ = startZ + cosYaw * len;

        int[] body = argb(penisColor.getValue());
        int[] head = argb(headColor.getValue());

        int grad = gradation.getValue().intValue();

        drawSphere(vc, matrix, leftX, leftY, leftZ, balls, grad, body);
        drawSphere(vc, matrix, rightX, rightY, rightZ, balls, grad, body);
        drawCylinder(vc, matrix, startX, startY, startZ, tipX, tipY, tipZ, width, body);
        drawSphere(vc, matrix, tipX, tipY, tipZ, balls * 0.8D, grad, head);
    }

    private int[] argb(int color) {
        return new int[]{(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF};
    }

    private void drawSphere(VertexConsumer vc, Matrix4f matrix,
                            double cx, double cy, double cz, double radius,
                            int grad, int[] col) {
        double step = Math.PI / grad;
        for (double alpha = 0; alpha < Math.PI; alpha += step) {
            for (double beta = 0; beta < 2.0 * Math.PI; beta += step) {
                double x1 = cx + radius * Math.cos(beta) * Math.sin(alpha);
                double y1 = cy + radius * Math.sin(beta) * Math.sin(alpha);
                double z1 = cz + radius * Math.cos(alpha);

                double sin = Math.sin(alpha + step);
                double x2 = cx + radius * Math.cos(beta) * sin;
                double y2 = cy + radius * Math.sin(beta) * sin;
                double z2 = cz + radius * Math.cos(alpha + step);

                line(vc, matrix, x1, y1, z1, x2, y2, z2, col);
            }
        }
    }

    private void drawCylinder(VertexConsumer vc, Matrix4f matrix,
                              double sx, double sy, double sz,
                              double ex, double ey, double ez,
                              double radius, int[] col) {
        int slices = 20;
        int step = 360 / slices;
        for (int i = 0; i < 360; i += step) {
            double a1 = Math.toRadians(i);
            double a2 = Math.toRadians(i + step);

            double x1s = sx + radius * Math.cos(a1);
            double z1s = sz + radius * Math.sin(a1);
            double x2s = sx + radius * Math.cos(a2);
            double z2s = sz + radius * Math.sin(a2);

            double x1e = ex + radius * Math.cos(a1);
            double z1e = ez + radius * Math.sin(a1);
            double x2e = ex + radius * Math.cos(a2);
            double z2e = ez + radius * Math.sin(a2);

            line(vc, matrix, x1s, sy, z1s, x1e, ey, z1e, col);
            line(vc, matrix, x1s, sy, z1s, x2s, sy, z2s, col);
            line(vc, matrix, x1e, ey, z1e, x2e, ey, z2e, col);
        }
    }

    private void line(VertexConsumer vc, Matrix4f matrix,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      int[] col) {
        vc.vertex(matrix, (float) x1, (float) y1, (float) z1).color(col[0], col[1], col[2], col[3]);
        vc.vertex(matrix, (float) x2, (float) y2, (float) z2).color(col[0], col[1], col[2], col[3]);
    }
}

