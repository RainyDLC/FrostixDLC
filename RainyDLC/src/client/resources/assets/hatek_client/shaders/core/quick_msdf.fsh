#version 330
#moj_import <minecraft:dynamictransforms.glsl>
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
