#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in vec4 fragColor;
in float guiScale;
in vec2 texCoord;

uniform sampler2D Sampler0;

out vec4 outColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 radii = min(cornerRadii, vec4(maxRadius));

    vec4 r = vec4(radii.x, radii.y, radii.z, radii.w);

    float dist = roundedBoxSDF(center, halfSize, r);

    float pixelWidth = fwidth(dist);
    float smoothing = max(pixelWidth, 0.5 / guiScale);

    float cornerAlpha = 1.0 - smoothstep(-smoothing, smoothing, dist);

    if (cornerAlpha < 0.01) {
        discard;
    }

    vec4 texColor = texture(Sampler0, texCoord);
    
    vec4 finalColor = texColor * fragColor;
    finalColor.a *= cornerAlpha;

    if (finalColor.a < 0.01) {
        discard;
    }

    outColor = finalColor;
}
