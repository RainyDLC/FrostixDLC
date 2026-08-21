#version 330

in vec3 skyDir;

out vec4 fragColor;

layout(std140) uniform ShaderSkyData {
    vec4 resolutionTime;
    vec4 settings;
};

mat2 mm2(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

float tri(float x) {
    return clamp(abs(fract(x) - 0.0), 0.0, 0.49);
}

vec2 tri2(vec2 p) {
    return vec2(tri(p.x) + tri(p.y), tri(p.y + tri(p.x)));
}

float triNoise2d(vec2 p, float spd, float time) {
    int noiseSteps = int(mix(2.0, 4.0, settings.z) + 0.5);
    float z = 1.8;
    float z2 = 2.5;
    float rz = 0.0;
    p *= mm2(p.x * 0.06);
    vec2 bp = p;

    mat2 drift = mm2(time * 10.0 * spd);
    for (float i = 0.0; i < 4.0; i++) {
        if (i >= float(noiseSteps)) break;
        vec2 dg = tri2(bp * 1.85) * 0.75;
        dg *= drift;
        p -= dg / z2;
        bp *= 1.3;
        z2 *= 0.45;
        z *= 0.42;
        p *= 1.21 + (rz - 1.0) * 0.02;
        rz += tri(p.x + tri(p.y)) * z;
        p = vec2(-p.y, p.x) * 0.29552;
    }

    return clamp(1.0 / pow(rz * 29.0, 1.3), 0.0, 0.55);
}

float hash21(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

vec4 aurora(vec3 ro, vec3 rd, float time) {
    vec4 col = vec4(0.0);
    vec4 avgCol = vec4(0.0);
    float jitter = 0.006 * hash21(gl_FragCoord.xy);
    int auroraSteps = int(mix(16.0, 30.0, settings.z) + 0.5);

    for (float i = 0.0; i < 30.0; i++) {
        if (i >= float(auroraSteps)) break;
        float of = jitter * smoothstep(0.0, 12.0, i);
        float pt = ((0.8 + pow(i, 1.4) * 0.002) - ro.y) / (rd.y * 2.0 + 0.4);
        pt -= of;
        vec3 bpos = ro + pt * rd;
        float rzt = triNoise2d(bpos.zx, 0.06, time);
        vec4 col2 = vec4(0.0, 0.0, 0.0, rzt);
        col2.rgb = (sin(1.0 - vec3(2.15, -0.5, 1.2) + i * 0.043) * 0.5 + 0.5) * rzt;
        avgCol = mix(avgCol, col2, 0.5);
        col += avgCol * exp2(-i * 0.065 - 2.5) * smoothstep(0.0, 5.0, i);
    }

    col *= clamp(rd.y * 15.0 + 0.4, 0.0, 1.0);
    return col * 1.8;
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p.zxy, p.yxz + 19.27);
    return fract(vec3(p.x * p.y, p.z * p.x, p.y * p.z));
}

vec3 stars(vec3 p) {
    vec3 c = vec3(0.0);
    float res = resolutionTime.x;
    int starSteps = int(mix(1.0, 3.0, settings.z) + 0.5);

    for (float i = 0.0; i < 3.0; i++) {
        if (i >= float(starSteps)) break;
        vec3 q = fract(p * (0.15 * res)) - 0.5;
        vec3 id = floor(p * (0.15 * res));
        vec2 rn = hash33(id).xy;
        float c2 = 1.0 - smoothstep(0.0, 0.6, length(q));
        c2 *= step(rn.x, 0.0005 + i * i * 0.001);
        c += c2 * (mix(vec3(1.0, 0.49, 0.1), vec3(0.75, 0.9, 1.0), rn.y) * 0.1 + 0.9);
        p *= 1.3;
    }

    return c * c * 0.8;
}

vec3 bg(vec3 rd) {
    float sd = dot(normalize(vec3(-0.5, -0.6, 0.9)), rd) * 0.5 + 0.5;
    sd = pow(sd, 5.0);
    return mix(vec3(0.05, 0.1, 0.2), vec3(0.1, 0.05, 0.2), sd) * 0.63;
}

void main() {
    float time = resolutionTime.z * settings.y;
    vec3 ro = vec3(0.0, 0.0, -6.7);
    vec3 rd = normalize(skyDir);
    rd.xz *= mm2(sin(time * 0.05) * 0.2);

    vec3 brd = rd;
    float fade = smoothstep(0.0, 0.01, abs(brd.y)) * 0.1 + 0.9;
    vec3 col;

    if (rd.y > 0.0) {
        col = bg(rd) * fade;
        vec4 aur = smoothstep(0.0, 1.5, aurora(ro, rd, time)) * fade;
        col += stars(rd);
        col = col * (1.0 - aur.a) + aur.rgb;
    } else {
        rd.y = abs(rd.y);
        col = bg(rd) * fade * 0.6;
        vec4 aur = smoothstep(0.0, 2.5, aurora(ro, rd, time));
        col += stars(rd) * 0.1;
        col = col * (1.0 - aur.a) + aur.rgb;
        vec3 pos = ro + ((0.5 - ro.y) / rd.y) * rd;
        float nz2 = triNoise2d(pos.xz * vec2(0.5, 0.7), 0.0, time);
        col += mix(vec3(0.2, 0.25, 0.5) * 0.08, vec3(0.3, 0.3, 0.5) * 0.7, nz2 * 0.4);
    }

    fragColor = vec4(col * settings.x, 1.0);
}
