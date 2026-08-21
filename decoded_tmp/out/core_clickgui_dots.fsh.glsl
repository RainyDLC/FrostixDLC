#version 150

in vec2 pixelCoord;
in vec2 screenSize;
in vec2 mouseCoord;
in float guiScale;
in float globalAlpha;
in float time;
in vec4 dotPrimary;
in vec4 dotSecondary;

out vec4 fragColor;

vec4 grade() {
    vec2 p = pixelCoord.xy / screenSize.xy;
    vec3 col = vec3(abs((p.y - 0.5) * -3.0 * cos(time)));
    vec3 invrt = 1.0 - col;
    return vec4(invrt, 1.0);
}

float rand(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 uv2tri(vec2 uv) {
    float sx = uv.x - uv.y / 2.0;
    float sxf = fract(sx);
    float offs = step(fract(1.0 - uv.y), sxf);
    return vec2(floor(sx) * 2.0 + sxf + offs, uv.y);
}

float triValue(vec2 uv) {
    float sp = 1.2 + 3.3 * rand(floor(uv2tri(uv)));
    return max(0.0, sin(sp * time));
}

vec4 triPattern() {
    vec2 uv = (pixelCoord.xy - screenSize.xy / 2.0) / screenSize.y;

    float t1 = time / 2.0;
    float t2 = t1 + 0.5;

    float c1 = triValue(uv * (2.0 + 4.0 * fract(t1)) + floor(t1));
    float c2 = triValue(uv * (2.0 + 4.0 * fract(t2)) + floor(t2));

    return vec4(mix(c1, c2, abs(1.0 - 2.0 * fract(t1))));
}

void main() {
    vec4 color = mix(dotSecondary, dotPrimary, 0.72);
    color.a *= globalAlpha;

    vec4 result = grade() * (triPattern() * color);
    result.a = clamp(result.a * 0.42, 0.0, 0.34);

    if (result.a < 0.004) {
        discard;
    }

    fragColor = result;
}
