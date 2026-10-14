package dev.hatek.client.module.impl.render.interf.quick;

/** Expanded GLSL for late injection and resource reloads. */
public final class QuickShaderSources {
    private QuickShaderSources() {}
    public static final String MSDF = """
#version 330
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
float median(float r, float g, float b) { return max(min(r,g), min(max(r,g),b)); }
void main() {
    vec3 msdf = texture(Sampler0, texCoord0).rgb;
    vec2 unitRange = vec2(10.0) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord0), vec2(0.000001));
    float screenRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float coverage = clamp(screenRange * (median(msdf.r,msdf.g,msdf.b) - 0.5) + 0.5, 0.0, 1.0);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (fragColor.a <= 0.001) discard;
}

""";
    public static final String BLUR = """
#version 330
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 stepUV = vec2(2.5) / vec2(textureSize(Sampler0, 0));
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) {
        float w = exp(-float(x*x + y*y) / 3.0);
        sum += texture(Sampler0, clamp(texCoord0 + vec2(x,y)*stepUV, vec2(0.0), vec2(1.0))).rgb * w;
        total += w;
    }
    // Main render target may have zero alpha. Coverage belongs to the HUD, not the world.
    fragColor = vec4(sum / total * vertexColor.rgb, vertexColor.a) * ColorModulator;
}

""";
}
