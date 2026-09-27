#version 330

uniform sampler2D InSampler;

layout(std140) uniform ChromaticConfig {
    float Strength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 fromCenter = texCoord - 0.5;
    // Quadratic falloff: clean center, fringing only toward the edges like real glass.
    vec2 offset = fromCenter * dot(fromCenter, fromCenter) * Strength;

    float r = texture(InSampler, texCoord + offset).r;
    float g = texture(InSampler, texCoord).g;
    float b = texture(InSampler, texCoord - offset).b;

    fragColor = vec4(r, g, b, 1.0);
}
