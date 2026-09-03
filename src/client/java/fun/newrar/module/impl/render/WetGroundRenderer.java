package fun.newrar.module.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.render.shader.StormSurfacePipeline;

public final class WetGroundRenderer {
    private static final StormSurfacePipeline pipeline = new StormSurfacePipeline();

    private static float cfgWetness = 55F;
    private static float cfgGloss = 65F;
    private static float cfgPuddles = 45F;
    private static float cfgRadius = 32F;
    private static boolean cfgObjects = true;
    private static boolean cfgSkyTint = true;
    private static int cfgColor = 0xFF00FFFF;

    private static boolean active = false;

    private WetGroundRenderer() {
    }

    public static void configure(float wetness, float gloss, float puddles,
                                 float radius, boolean objects,
                                 boolean skyTint, int color) {
        cfgWetness = wetness;
        cfgGloss = gloss;
        cfgPuddles = puddles;
        cfgRadius = Math.max(12F, radius);
        cfgObjects = objects;
        cfgSkyTint = skyTint;
        cfgColor = color;
    }

    public static void update(boolean enabled, int radius, boolean skyOnly, float puddleThreshold) {
        active = enabled;
    }

    public static void render(EventRender3D e) {
        if (!active) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getFramebuffer() == null) return;

        Framebuffer fb = mc.getFramebuffer();
        if (fb.getColorAttachment() == null || fb.getDepthAttachment() == null || fb.getColorAttachmentView() == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Matrix4f viewMatrix = e.getMatrixStack().peek().getPositionMatrix();
        Matrix4f projMatrix = mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue().floatValue());

        StormSurfacePipeline.SurfaceConfig cfg = new StormSurfacePipeline.SurfaceConfig();
        cfg.dampness = Math.max(0.2f, cfgWetness / 48.0f);
        cfg.puddleSpread = Math.max(0.1f, Math.min(1.0f, cfgPuddles / 75.0f));
        cfg.glossiness = Math.max(0.3f, cfgGloss / 45.0f);
        cfg.rippleAmt = 0.0f;
        cfg.range = Math.max(56.0f, cfgRadius * 1.5f);
        cfg.lightningGlint = SkyLightningRenderer.flashLevel();
        cfg.raySteps = cfgObjects ? 16 : 8;

        pipeline.apply(
                fb.getColorAttachmentView(),
                fb.getColorAttachment(),
                fb.getDepthAttachment(),
                fb.textureWidth,
                fb.textureHeight,
                camera,
                mc.player,
                viewMatrix,
                projMatrix,
                cfg
        );
    }

    public static void clear() {
        active = false;
        pipeline.close();
    }
}
