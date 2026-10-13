package dev.hatek.client.render;

import org.joml.Matrix4f;

/**
 * Parameters travel in DynamicTransforms.TextureMat, which the entity renderer already binds.
 * This pipeline uses UV0 unchanged, so no texture transform is sacrificed. GLSL indexes columns.
 */
public final class ChamsUniforms {
    public static final int AURORA = 0;
    public static final int PRISM = 1;
    public static final int HOLOGRAM = 2;
    public static final int NEBULA = 3;
    public static final int CLASSIC = 4;

    private ChamsUniforms() {}

    public static Matrix4f pack(int primary, int secondary, float time, int preset,
                               float strength, float scale, float skinDetail, float rim, float pulse) {
        return new Matrix4f().zero()
                .m00(channel(primary, 16)).m01(channel(primary, 8)).m02(channel(primary, 0)).m03(channel(primary, 24))
                .m10(channel(secondary, 16)).m11(channel(secondary, 8)).m12(channel(secondary, 0)).m13(unit(strength))
                .m20(Float.isFinite(time) ? time : 0).m21(Math.max(AURORA, Math.min(CLASSIC, preset)))
                .m22(Float.isFinite(scale) ? Math.max(0.25f, Math.min(3, scale)) : 1).m23(unit(skinDetail))
                .m30(unit(rim)).m31(unit(pulse));
    }

    private static float channel(int color, int shift) {
        return ((color >>> shift) & 255) / 255.0f;
    }

    private static float unit(float v) {
        return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;
    }
}
