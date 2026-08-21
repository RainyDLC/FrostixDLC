#version 150

in vec2 pixelCoord;
in vec2 screenSize;
in float guiScale;
in float globalAlpha;
in float time;

out vec4 fragColor;

float random(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));

    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = rot * p * 2.0 + vec2(time * 0.018, time * 0.012);
        a *= 0.56;
    }

    return v;
}

float contourLine(float h, float width) {
    float cell = fract(h);
    float distToLine = min(cell, 1.0 - cell);
    float aa = max(fwidth(h) * 1.35, 0.0018 / guiScale);
    return 1.0 - smoothstep(width, width + aa, distToLine);
}

void main() {
    vec2 uv = pixelCoord / screenSize;
    vec2 p = (pixelCoord - screenSize * 0.5) / screenSize.y;

    p *= 6.2;
    p.x += sin(time * 0.10) * 0.28;
    p.y += cos(time * 0.08) * 0.16;

    float fieldA = fbm(p * 0.42 + vec2(time * 0.035, -time * 0.018));
    float fieldB = fbm(p * 0.82 - vec2(time * 0.020, time * 0.026));
    float h = (fieldA * 0.72 + fieldB * 0.38) * 9.0;

    float line = contourLine(h, 0.030);
    float softLine = contourLine(h + 0.035, 0.070) * 0.35;

    vec2 grad = vec2(dFdx(h), dFdy(h));
    float flow = smoothstep(0.015, 0.085, length(grad));
    float glow = smoothstep(0.0, 1.0, line + softLine) * (0.34 + flow * 0.42);

    float vignette = smoothstep(0.95, 0.18, length(uv - 0.5));
    float fade = 0.38 + vignette * 0.92;

    vec3 color = vec3(0.0);
    color += vec3(0.18) * softLine * fade;
    color += vec3(0.92) * line * fade;
    color += vec3(0.55) * glow * fade;

    float mist = fbm(uv * 2.2 + vec2(time * 0.012, 0.0));
    color += vec3(mist * 0.025 * vignette);

    float alpha = clamp((line * 0.92 + softLine * 0.28 + glow * 0.38 + mist * 0.025 * vignette) * fade, 0.0, 1.0);
    fragColor = vec4(color, alpha * globalAlpha);
}
