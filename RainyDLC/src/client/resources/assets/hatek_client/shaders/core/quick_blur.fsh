#version 330
#moj_import <minecraft:dynamictransforms.glsl>
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
