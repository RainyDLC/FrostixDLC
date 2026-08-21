#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D HandSampler;   // scene incl. hands (rgb of the hand pixels)
uniform sampler2D MaskSampler;   // hand mask (r channel)

layout(std140) uniform BurnData {
    vec4 res;        // x=width, y=height, z=time(sec), w=fillMode
    vec4 fire;       // x=radiusPx, y=strength, z=flameSpeed, w=colorMix
    vec4 trailP;     // x=decay, y=flameHeightPx, z=flowSpeed, w=trailStrength
    vec4 fireColor;  // rgb=fire color, w=glowStrength
};

const int RINGS = 6;
const int DIRS = 12;
const float TAU = 6.28318530718;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

vec3 itemColor(vec3 color) {
    color = clamp(color, 0.0, 1.0);
    float mx = max(max(color.r, color.g), color.b);
    if (mx < 0.04) {
        return vec3(0.0);
    }

    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    float sat = mx - min(min(color.r, color.g), color.b);
    vec3 vivid = mix(vec3(gray), color, 1.0 + sat * 0.45);
    return clamp(vivid / max(max(max(vivid.r, vivid.g), vivid.b), 0.20), 0.0, 1.0) * min(mx * 1.18, 1.0);
}

void main() {
    vec2 TexelSize = 1.0 / res.xy;
    float Time = res.z;
    float Radius = fire.x;
    float Strength = fire.y;
    float FlameSpeed = fire.z;
    float ColorMix = fire.w;
    vec3 FireColor = fireColor.rgb;

    float centerAlpha = texture(MaskSampler, texCoord).r;
    float outsideMask = 1.0 - smoothstep(0.015, 0.22, centerAlpha);
    float flowTime = Time * FlameSpeed;

    float energy = 0.0;
    float colorWeight = 0.0;
    vec3 colorSum = vec3(0.0);

    for (int ring = 1; ring <= RINGS; ring++) {
        float rp = float(ring) / float(RINGS);
        float distancePx = Radius * rp;
        float ringWeight = pow(1.0 - rp * 0.86, 1.45);

        for (int dir = 0; dir < DIRS; dir++) {
            float dp = float(dir) / float(DIRS);
            float warp = noise(texCoord * 9.0 + vec2(float(ring) * 3.4, float(dir) * 2.2 + flowTime * 0.16));
            float angle = dp * TAU + (warp - 0.5) * 0.34;
            float dist = distancePx * (0.90 + warp * 0.22);
            vec2 offset = vec2(cos(angle), sin(angle)) * TexelSize * dist;
            vec2 coord = texCoord + offset;
            float sampleAlpha = clamp(texture(MaskSampler, coord).r, 0.0, 1.0);
            vec3 sampleRgb = texture(HandSampler, coord).rgb;
            float weight = sampleAlpha * ringWeight;
            energy += weight;
            colorSum += sampleRgb * weight;
            colorWeight += weight;
        }
    }

    energy = energy / float(RINGS * DIRS) * 4.25;
    float softHalo = smoothstep(0.018, 0.58, energy);
    float edgeNoise = noise(texCoord * vec2(15.0, 21.0) + vec2(flowTime * 0.20, -flowTime * 0.34));
    float alpha = softHalo * outsideMask * Strength * (0.86 + edgeNoise * 0.18);
    alpha = clamp(alpha, 0.0, 0.72);

    vec3 color = itemColor(colorSum / max(colorWeight, 0.001));
    color = mix(color, FireColor, clamp(ColorMix, 0.0, 1.0));
    fragColor = vec4(color * (0.88 + alpha * 0.42), alpha);
}
