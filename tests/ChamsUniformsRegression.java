package dev.hatek.client.render;

import org.joml.Matrix4f;

/** Standalone checks for the exact CPU/std140 column contract used by the GLSL shader. */
public final class ChamsUniformsRegression {
    public static void main(String[] args) {
        Matrix4f m = ChamsUniforms.pack(0x80402010, 0xFFAABBCC, 12.5f,
                ChamsUniforms.NEBULA, 0.65f, 1.25f, 0.4f, 0.7f, 0.88f);
        check(64 / 255f, m.m00(), "primary red");
        check(32 / 255f, m.m01(), "primary green");
        check(16 / 255f, m.m02(), "primary blue");
        check(128 / 255f, m.m03(), "primary opacity");
        check(170 / 255f, m.m10(), "secondary red");
        check(187 / 255f, m.m11(), "secondary green");
        check(204 / 255f, m.m12(), "secondary blue");
        check(0.65f, m.m13(), "strength, not secondary alpha");
        check(12.5f, m.m20(), "time");
        check(3, m.m21(), "Nebula mode");
        check(1.25f, m.m22(), "scale");
        check(0.4f, m.m23(), "skin detail");
        check(0.7f, m.m30(), "rim");
        check(0.88f, m.m31(), "pulse");
        float[] gpu = m.get(new float[16]);
        check(128 / 255f, gpu[3], "GLSL TextureMat[0].a");
        check(0.65f, gpu[7], "GLSL TextureMat[1].a");
        check(12.5f, gpu[8], "GLSL TextureMat[2].x");
        check(0.7f, gpu[12], "GLSL TextureMat[3].x");
        Matrix4f safe = ChamsUniforms.pack(0, 0, Float.NaN, 999, -2, 0, 4, Float.NaN, 2);
        check(0, safe.m20(), "invalid time");
        check(4, safe.m21(), "invalid preset");
        check(0, safe.m13(), "negative strength");
        check(0.25f, safe.m22(), "minimum scale");
        check(1, safe.m23(), "maximum skin detail");
        check(0, safe.m30(), "invalid rim");
        check(1, safe.m31(), "maximum pulse");
        System.out.println("Chams uniforms: 25 checks passed");
    }

    private static void check(float expected, float actual, String name) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.00001f)
            throw new AssertionError(name + ": " + actual + " != " + expected);
    }
}
