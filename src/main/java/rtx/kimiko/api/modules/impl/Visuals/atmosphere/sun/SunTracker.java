package rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun;

import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.MathUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Projects the sun onto the screen and computes a smoothed "how much light hits the lens" factor. */
public final class SunTracker {

    private static final double OCCLUSION_RAY_LENGTH = 256.0;
    private static final float EDGE_FADE_START = 1.0f;  // NDC
    private static final float EDGE_FADE_END = 1.35f;   // flares linger a bit past the frame edge

    private float visibility;
    private float lastX;
    private float lastY;
    private float lastAlignment;
    private long lastFrameNanos = System.nanoTime();

    public SunState update(MinecraftClient mc, float tickDelta, int width, int height, AtmosphereConfig cfg) {
        ClientWorld world = mc.world;
        Camera camera = mc.gameRenderer.getCamera();

        Vector3f sunDir = sunDirection(camera, tickDelta);
        Vector3f view = new Vector3f(sunDir).rotate(new Quaternionf(camera.getRotation()).conjugate());

        float target = 0f;
        if (view.z < 0f) { // camera looks down -Z
            float tanHalfFov = (float) Math.tan(Math.toRadians(mc.options.getFov().getValue()) * 0.5);
            float aspect = (float) width / height;
            float ndcX = view.x / (-view.z * tanHalfFov * aspect);
            float ndcY = view.y / (-view.z * tanHalfFov);

            lastX = (ndcX * 0.5f + 0.5f) * width;
            lastY = (1f - (ndcY * 0.5f + 0.5f)) * height;
            lastAlignment = 1f - MathUtil.clamp01((float) Math.hypot(ndcX, ndcY) / 1.414f);

            float edge = 1f - MathUtil.smoothstep(EDGE_FADE_START, EDGE_FADE_END, Math.max(Math.abs(ndcX), Math.abs(ndcY)));
            float horizon = MathUtil.smoothstep(-0.05f, 0.12f, sunDir.y);
            float weather = 1f - world.getRainGradient(tickDelta) * 0.95f;
            float blocked = cfg.occlusionCheck() && isOccluded(mc, camera, sunDir) ? 0f : 1f;
            float underwater = camera.getSubmersionType() == CameraSubmersionType.NONE ? 1f : 0f;

            target = edge * horizon * weather * blocked * underwater;
        }

        visibility = MathUtil.approach(visibility, target, cfg.fadeSpeed(), frameSeconds());
        return new SunState(lastX, lastY, visibility, lastAlignment);
    }

    /** Vanilla sky: sun is rotated -90 around Y then by the sky angle around X. */
    private static Vector3f sunDirection(Camera camera, float tickDelta) {
        float angle = camera.getEnvironmentAttributeInterpolator().get(
                net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE_VISUAL, tickDelta) * 0.017453292f;
        return new Vector3f((float) -Math.sin(angle), (float) Math.cos(angle), 0f);
    }

    private static boolean isOccluded(MinecraftClient mc, Camera camera, Vector3f sunDir) {
        Vec3d start = camera.getCameraPos();
        Vec3d end = start.add(sunDir.x * OCCLUSION_RAY_LENGTH, sunDir.y * OCCLUSION_RAY_LENGTH, sunDir.z * OCCLUSION_RAY_LENGTH);
        HitResult hit = mc.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY, mc.player));
        return hit.getType() != HitResult.Type.MISS;
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(dt, 0.1f); // avoid pops after alt-tab / lag spikes
    }
}
