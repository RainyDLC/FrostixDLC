#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D maskTexture;

layout(std140) uniform LightningOutlineData {
    vec4 lightningColor;
    vec4 params1;
    vec4 params2;
};

float maskAt(vec2 uv) {
    vec4 value = texture(maskTexture, clamp(uv, vec2(0.0), vec2(1.0)));
    return max(value.r, max(value.g, value.b));
}

float hash11(float value) {
    return fract(sin(value * 127.1 + 311.7) * 43758.5453123);
}

float noise1(float value) {
    float cell = floor(value);
    float part = fract(value);
    part = part * part * (3.0 - 2.0 * part);
    return mix(hash11(cell), hash11(cell + 1.0), part);
}

float edgeAt(vec2 uv, vec2 texel) {
    float center = maskAt(uv);
    float edge = 0.0;
    edge = max(edge, abs(center - maskAt(uv + vec2( texel.x, 0.0))));
    edge = max(edge, abs(center - maskAt(uv + vec2(-texel.x, 0.0))));
    edge = max(edge, abs(center - maskAt(uv + vec2(0.0,  texel.y))));
    edge = max(edge, abs(center - maskAt(uv + vec2(0.0, -texel.y))));
    edge = max(edge, abs(center - maskAt(uv + vec2( texel.x,  texel.y))));
    edge = max(edge, abs(center - maskAt(uv + vec2(-texel.x,  texel.y))));
    edge = max(edge, abs(center - maskAt(uv + vec2( texel.x, -texel.y))));
    edge = max(edge, abs(center - maskAt(uv + vec2(-texel.x, -texel.y))));
    return smoothstep(0.08, 0.62, edge);
}

float edgeBand(vec2 uv, vec2 texel, float radius, out vec2 edgeDirection) {
    float center = maskAt(uv);
    float result = 0.0;
    edgeDirection = vec2(0.0);
    for (int ring = 1; ring <= 2; ring++) {
        float ringPart = float(ring) * 0.5;
        for (int i = 0; i < 12; i++) {
            float angle = float(i) * 0.5235987756;
            vec2 direction = vec2(cos(angle), sin(angle));
            float changed = abs(center - maskAt(uv + direction * texel * radius * ringPart));
            float weight = changed * (1.15 - ringPart * 0.35);
            result = max(result, weight);
            edgeDirection += direction * weight;
        }
    }
    return smoothstep(0.05, 0.55, result);
}

void main() {
    vec2 texel = params1.xy;
    float time = params1.z;
    float alpha = params1.w;
    float radius = clamp(params2.x, 1.0, 14.0);
    float segmentLength = max(params2.y, 4.0);
    float density = clamp(params2.z, 0.05, 1.0);
    float swing = max(params2.w, 0.0);

    vec2 pixel = texCoord / texel;
    vec2 normal;
    float glow = edgeBand(texCoord, texel, radius, normal);
    if (glow < 0.008) discard;
    if (dot(normal, normal) < 0.000001) normal = vec2(0.0, 1.0);
    normal = normalize(normal);

    vec2 tangent = vec2(-normal.y, normal.x);
    float contourPos = dot(pixel, tangent);
    float jitter = (noise1(contourPos * 0.075 + time * 7.0) - 0.5) * min(radius * 0.85, 4.5);
    vec2 warpedUv = texCoord + normal * texel * jitter;
    float core = edgeAt(warpedUv, texel);

    float travel = contourPos / segmentLength + time * (2.2 + swing * 0.8);
    float local = fract(travel);
    float cell = floor(travel);
    float segment = smoothstep(0.015, 0.08, local)
                  * (1.0 - smoothstep(0.34 + density * 0.20, 0.48 + density * 0.22, local));

    float travel2 = contourPos / max(segmentLength * 0.63, 4.0)
                  - time * (1.45 + swing * 0.35) + 13.71;
    float local2 = fract(travel2);
    float segment2 = smoothstep(0.02, 0.12, local2) * (1.0 - smoothstep(0.28, 0.44, local2));

    float pulse = max(segment, segment2 * (0.58 + density * 0.30));
    if (pulse < 0.01) discard;

    float flicker = 0.78 + 0.22 * hash11(floor(time * 28.0) + cell * 3.17);
    float strength = min(1.0, (core * 1.55 + glow * 0.72) * pulse * flicker * alpha * 1.35);
    if (strength < 0.008) discard;

    vec3 color = mix(lightningColor.rgb, vec3(1.0), core * (0.58 + swing * 0.12));
    fragColor = vec4(color, strength * lightningColor.a);
}
