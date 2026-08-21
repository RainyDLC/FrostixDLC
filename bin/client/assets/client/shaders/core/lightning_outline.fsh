#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D maskTexture;

layout(std140) uniform LightningOutlineData {
    vec4 lightningColor;
    vec4 params1;
    vec4 params2;
};

/* params1: xy - texel size, z - time, w - alpha
   params2: x - glow radius (px), y - bolt reach (px), z - density, w - swing */

float maskAt(vec2 uv) {
    vec4 value = texture(maskTexture, clamp(uv, vec2(0.0), vec2(1.0)));
    return max(value.r, max(value.g, value.b));
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

float hash11(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float segmentDistance(vec2 point, vec2 start, vec2 finish) {
    vec2 toPoint = point - start;
    vec2 line = finish - start;
    float t = clamp(dot(toPoint, line) / max(dot(line, line), 0.0001), 0.0, 1.0);
    return length(toPoint - line * t);
}

#define BOLT_SEGMENTS 5

void main() {
    vec2 texel = params1.xy;
    float time = params1.z;
    float alpha = params1.w;
    float radius = clamp(params2.x, 1.0, 14.0);
    float reach = clamp(params2.y, 16.0, 150.0);
    float density = clamp(params2.z, 0.05, 1.0);
    float swing = max(params2.w, 0.0);

    /* молнии живут только возле силуэта предмета */
    vec2 normal;
    float band = edgeBand(texCoord, texel, radius, normal);
    if (band < 0.006) discard;

    vec2 pixel = texCoord / texel;

    /* сетка ячеек: чем больше «Кол-во молний», тем плотнее сетка и чаще спавн */
    float cellSize = mix(112.0, 58.0, density);
    vec2 baseCell = floor(pixel / cellSize);

    float coreAcc = 0.0;
    float glowAcc = 0.0;

    for (int cy = -1; cy <= 1; cy++) {
        for (int cx = -1; cx <= 1; cx++) {
            vec2 cellId = baseCell + vec2(float(cx), float(cy));

            float presence = hash21(cellId + 97.13);
            if (presence > 0.22 + density * 0.78) continue;

            float phase = hash21(cellId * 1.618 + 7.31);
            float cycle = 0.5 + hash21(cellId + 3.14) * 0.55;
            float slot = time / cycle + phase;
            float life = floor(slot);
            float local = fract(slot);

            float duty = clamp(0.16 + density * 0.38 + swing * 0.18, 0.08, 0.85);
            if (local > duty) continue;

            float fadeIn = smoothstep(0.0, 0.06, local);
            float fadeOut = 1.0 - smoothstep(duty * 0.55, duty, local);
            float envelope = fadeIn * fadeOut;
            if (envelope < 0.01) continue;

            /* форма разряда меняется при каждом респавне */
            vec2 seed = cellId + vec2(life * 13.73, life * 7.91);
            vec2 rand0 = hash22(seed);
            vec2 rand1 = hash22(seed + 41.37);
            vec2 rand2 = hash22(seed + 87.91);

            vec2 start = (cellId + 0.10 + 0.80 * rand0) * cellSize;
            float angle = rand1.x * 6.28318530;
            vec2 direction = vec2(cos(angle), sin(angle));
            float boltLen = reach * (0.55 + 0.95 * rand1.y);
            vec2 finish = start + direction * boltLen;

            vec2 side = vec2(-direction.y, direction.x);
            float amplitude = cellSize * (0.14 + 0.20 * rand0.y) * (1.0 + swing * 0.8);

            float flicker = 0.72 + 0.28 * hash11(life * 31.7 + hash21(cellId) * 57.0 + floor(time * 26.0));

            float best = 1e6;
            vec2 prev = start;
            for (int i = 1; i <= BOLT_SEGMENTS; i++) {
                float part = float(i) / float(BOLT_SEGMENTS);
                float jitter = hash21(seed + vec2(float(i) * 5.23, float(i) * 2.11)) - 0.5;
                vec2 next = mix(start, finish, part) + side * (jitter * amplitude * sin(part * 3.14159265));
                best = min(best, segmentDistance(pixel, prev, next));
                prev = next;
            }

            /* ответвление от середины основного разряда */
            float branchSign = rand2.x > 0.5 ? 1.0 : -1.0;
            float branchAngle = (0.55 + rand2.y * 0.6) * branchSign;
            float ca = cos(branchAngle);
            float sa = sin(branchAngle);
            vec2 branchDir = vec2(direction.x * ca - direction.y * sa, direction.x * sa + direction.y * ca);
            vec2 branchStart = mix(start, finish, 0.45) + side * (amplitude * 0.35);
            vec2 branchFinish = branchStart + branchDir * (boltLen * 0.38);
            best = min(best, segmentDistance(pixel, branchStart, branchFinish));
            best = min(best, segmentDistance(pixel, mix(branchStart, branchFinish, 0.5) + branchDir * (boltLen * 0.05),
                    branchFinish));

            float core = 1.0 - smoothstep(0.0, 1.6, best);
            float glow = exp(-best / max(radius * 1.6, 2.0));
            coreAcc += envelope * flicker * core;
            glowAcc += envelope * flicker * glow * 0.5;
        }
    }

    if (coreAcc + glowAcc < 0.01) discard;

    float bandMask = smoothstep(0.03, 0.30, band);
    float strength = min(1.0, (coreAcc * 1.35 + glowAcc) * bandMask * alpha * (1.0 + swing * 0.55));
    if (strength < 0.008) discard;

    vec3 color = mix(lightningColor.rgb, vec3(1.0), clamp(coreAcc, 0.0, 1.0) * 0.62);
    fragColor = vec4(color, strength);
}
