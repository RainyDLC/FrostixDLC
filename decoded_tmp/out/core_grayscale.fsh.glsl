#version 150

uniform sampler2D Sampler0;

layout(std140) uniform GrayscaleData {
    vec4 screen;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 source = texture(Sampler0, texCoord);
    float gray = (source.r + source.g + source.b) / 3.0;
    float state = clamp(screen.w, 0.0, 1.0);
    fragColor = vec4(mix(source.rgb, vec3(gray), state), source.a);
}
