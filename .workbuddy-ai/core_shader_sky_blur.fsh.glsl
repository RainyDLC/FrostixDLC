#version 150

in vec2 vUv;

out vec4 fragColor;

uniform sampler2D Sampler0;

layout(std140) uniform ShaderSkyData {
    vec4 screenTimeOpacity;
    vec4 primaryColor;
    vec4 secondaryColor;
    vec4 accentColor;
    vec4 params;
    vec4 extra;
};

void main() {
    vec4 blurred = texture(Sampler0, vUv);
    fragColor = vec4(blurred.rgb, screenTimeOpacity.w);
}
