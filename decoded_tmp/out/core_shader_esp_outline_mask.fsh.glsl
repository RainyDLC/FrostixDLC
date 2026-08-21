#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 raw = texture(Sampler0, texCoord);
    float rgbPower = max(max(raw.r, raw.g), raw.b);
    float power = max(rgbPower, raw.a);

    if (power < 0.02) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 color = rgbPower > 0.001 ? raw.rgb / rgbPower : vec3(1.0);
    fragColor = vec4(color, 1.0);
}
