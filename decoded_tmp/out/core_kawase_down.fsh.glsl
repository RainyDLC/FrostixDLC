#version 150

in vec2 texCoord;
in vec2 texelSize;
in float offset;

out vec4 fragColor;

uniform sampler2D Sampler0;

void main() {
    vec2 uv = texCoord;
    vec2 halfpixel = texelSize * 0.5 * offset;
    vec2 minUV = texelSize * 0.5;
    vec2 maxUV = 1.0 - texelSize * 0.5;

    vec4 sum = texture(Sampler0, uv) * 4.0;
    sum += texture(Sampler0, clamp(uv - halfpixel,                      minUV, maxUV));
    sum += texture(Sampler0, clamp(uv + halfpixel,                      minUV, maxUV));
    sum += texture(Sampler0, clamp(uv + vec2( halfpixel.x, -halfpixel.y), minUV, maxUV));
    sum += texture(Sampler0, clamp(uv + vec2(-halfpixel.x,  halfpixel.y), minUV, maxUV));

    fragColor = vec4((sum / 8.0).rgb, 1.0);
}
