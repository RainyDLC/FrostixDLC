#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    float Radius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 stepSize = vec2(Radius / float(textureSize(InSampler, 0).x), 0.0);
    vec4 color = texture(InSampler, texCoord) * 0.227027;
    color += texture(InSampler, texCoord + stepSize * 1.0) * 0.1945946;
    color += texture(InSampler, texCoord - stepSize * 1.0) * 0.1945946;
    color += texture(InSampler, texCoord + stepSize * 2.0) * 0.1216216;
    color += texture(InSampler, texCoord - stepSize * 2.0) * 0.1216216;
    color += texture(InSampler, texCoord + stepSize * 3.0) * 0.0540540;
    color += texture(InSampler, texCoord - stepSize * 3.0) * 0.0540540;
    color += texture(InSampler, texCoord + stepSize * 4.0) * 0.0162160;
    color += texture(InSampler, texCoord - stepSize * 4.0) * 0.0162160;
    fragColor = color;
}
