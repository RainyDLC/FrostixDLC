#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec4 params;
in vec4 tint;
in float guiScale;

out vec4 fragColor;

const float PI = 3.14159265359;

float circleAlpha(vec2 p, float radius, float halfThickness) {
    float dist = abs(length(p) - radius) - halfThickness;
    float aa = max(fwidth(dist), 0.55 / guiScale);
    return 1.0 - smoothstep(-aa, aa, dist);
}

float discAlpha(vec2 p, float radius) {
    float dist = length(p) - radius;
    float aa = max(fwidth(dist), 0.55 / guiScale);
    return 1.0 - smoothstep(-aa, aa, dist);
}

void main() {
    float outerRadius = params.x;
    float radius = params.y;
    float thickness = params.z;
    float progress = clamp(params.w, 0.0, 1.0);
    float halfThickness = thickness * 0.5;

    vec2 center = vec2(outerRadius);
    vec2 p = pixelCoord - center;

    float alpha = 0.0;

    if (progress >= 0.999) {
        alpha = circleAlpha(p, radius, halfThickness);
    } else if (progress > 0.0) {
        float angle = atan(p.y, p.x);
        float fromTop = mod(angle + PI * 2.5, PI * 2.0);
        float sweep = progress * PI * 2.0;
        float body = circleAlpha(p, radius, halfThickness) * step(fromTop, sweep);

        vec2 startCap = vec2(0.0, -radius);
        vec2 endCap = vec2(cos(-PI * 0.5 + sweep), sin(-PI * 0.5 + sweep)) * radius;
        float caps = max(discAlpha(p - startCap, halfThickness), discAlpha(p - endCap, halfThickness));

        alpha = max(body, caps);
    }

    alpha *= tint.a;
    if (alpha < 0.01) {
        discard;
    }

    fragColor = vec4(tint.rgb, alpha);
}
