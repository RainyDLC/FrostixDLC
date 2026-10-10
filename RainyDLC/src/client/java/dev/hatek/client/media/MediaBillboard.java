package dev.hatek.client.media;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.module.impl.render.Interface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 3D-билборд: картинка/GIF, парящая над игроком и всегда
 * повёрнутая к камере. Рендерится через submitCustomGeometry.
 */
public final class MediaBillboard {
    /** Полная яркость, без затемнения от освещения мира. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private MediaBillboard() {
    }

    public static void render(PoseStack poseStack, LevelRenderState state,
                              SubmitNodeCollector collector) {
        if (!Interface.showMedia3D()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        MediaClip clip = MediaManager.get(Interface.media3dFile());
        if (clip == null) {
            return;
        }
        if (!(collector instanceof OrderedSubmitNodeCollector)) {
            return;
        }
        OrderedSubmitNodeCollector ordered = (OrderedSubmitNodeCollector) collector;

        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        Vec3 cam = state.cameraRenderState.pos;
        double x = mc.player.getX() - cam.x;
        double y = mc.player.getY() + Interface.media3dHeight() - cam.y;
        double z = mc.player.getZ() - cam.z;

        float size = (float) Interface.media3dSize();
        float hw = size / 2.0f;
        float hh = hw * frame.height() / (float) frame.width();

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(state.cameraRenderState.orientation);
        ordered.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucentEmissive(frame.texture()),
                (pose, consumer) -> quad(pose, consumer, hw, hh));
        poseStack.popPose();
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer,
                             float hw, float hh) {
        Matrix4f matrix = pose.pose();
        consumer.addVertex(matrix, -hw, -hh, 0.0f).setUv(0.0f, 1.0f)
                .setColor(-1).setLight(FULL_BRIGHT);
        consumer.addVertex(matrix, hw, -hh, 0.0f).setUv(1.0f, 1.0f)
                .setColor(-1).setLight(FULL_BRIGHT);
        consumer.addVertex(matrix, hw, hh, 0.0f).setUv(1.0f, 0.0f)
                .setColor(-1).setLight(FULL_BRIGHT);
        consumer.addVertex(matrix, -hw, hh, 0.0f).setUv(0.0f, 0.0f)
                .setColor(-1).setLight(FULL_BRIGHT);
    }
}
