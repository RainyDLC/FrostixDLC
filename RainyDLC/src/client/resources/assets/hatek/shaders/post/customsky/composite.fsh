#version 150

uniform sampler2D Sky;
uniform sampler2D Bloom0;
uniform sampler2D Bloom1;
uniform sampler2D Bloom2;
uniform sampler2D Bloom3;
uniform sampler2D Bloom4;
uniform sampler2D Bloom5;
uniform sampler2D NoiseTex;

layout(std140) uniform SkyParams {
    mat4 invViewProj;
    vec4 misc;
    vec4 skyColor;
    vec4 skyColor2;
    vec4 taa;
    mat4 prevViewProj;
    vec4 skyExtra;
    vec4 bhParams;
};

in vec2 texCoord;
out vec4 fragColor;

const float BLOOM_STRENGTH = 0.13;
const float STAR_LENS_E2 = 0.035;
const float STAR_SWIRL = 0.010;
const float NEBULA = 1.0;
const float INCL = 0.10;
const float ZOOM = 2.4;

vec3 gJetAxis;

vec3 holeCenter(vec3 bhDir) {
    vec3 upRef = vec3(0.0, 1.0, 0.0);
    vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
    vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
    vec3 eX = normalize(cross(eY, bhDir));
    vec3 eZ = cross(eX, eY);
    vec3 c = normalize(eY * (-ZOOM * sin(INCL)) + eZ * cos(INCL));
    gJetAxis = normalize(eY - dot(eY, c) * c);
    return c;
}

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

float vnoise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 rg = textureLod(NoiseTex, (uv + 0.5) / 256.0, 0.0).yx;
    return mix(rg.x, rg.y, f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

vec3 nebula(vec3 dir, float time, vec3 themeA, vec3 themeB, float themeW) {
    vec3 gN = normalize(vec3(0.25, 0.35, -0.90));
    float gl = dot(dir, gN);
    float band = exp(-gl * gl * 9.0);
    vec3 q = dir * 2.6 + vec3(0.0, 0.0, time * 0.004);
    float warp = fbm(q * 1.3);
    float n = fbm(q + warp * 1.4);
    float n2 = fbm(q * 2.4 + 11.0 + warp);
    float dust = smoothstep(0.42, 0.72, fbm(q * 3.4 + 5.0 + warp * 0.8));

    vec3 cA = mix(vec3(0.95, 0.42, 0.62), themeA, themeW);
    vec3 cB = mix(vec3(0.25, 0.48, 1.00), themeB, themeW);
    vec3 cC = vec3(1.00, 0.72, 0.45);

    float cloud = smoothstep(0.40, 0.85, n);
    vec3 col = mix(cB, cA, smoothstep(0.35, 0.75, n2)) * cloud * cloud * 0.20;
    col += cC * pow(max(n - 0.45, 0.0) * 2.0, 3.0) * 0.06;
    vec3 milky = mix(vec3(0.60, 0.62, 0.75), cC, 0.35) * band * (0.35 + 0.65 * n) * 0.12;
    col += milky;
    col *= 1.0 - dust * (0.55 + 0.4 * band);
    return col;
}

vec3 bhStars(vec3 dir, float time) {
    vec3 c = vec3(0.0);
    vec3 p = dir * 42.0;
    float dens = 0.05;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        vec3 id = floor(p);
        vec3 q = fract(p) - 0.5;
        vec3 rn = nmzHash33(id);
        float dq = length(q);
        float core = smoothstep(0.30, 0.0, dq);
        float hit = step(rn.x, dens);
        float tw = 0.72 + 0.28 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
        vec3 tint = rn.y < 0.33 ? vec3(1.0, 0.70, 0.45) : (rn.y < 0.8 ? vec3(0.95, 0.95, 1.0) : vec3(0.62, 0.78, 1.0));
        float bright = step(0.82, rn.z) * step(float(i), 1.5);
        float spikes = bright * (exp(-abs(q.x) * 24.0) + exp(-abs(q.y) * 24.0)) * smoothstep(0.5, 0.0, dq) * 0.35;
        c += hit * (core * core + spikes) * tint * (0.35 + 0.65 * rn.z) * (1.0 + bright * 1.5) * tw * amp;
        p = p * 1.63 + 17.0;
        dens *= 0.62;
        amp *= 0.78;
    }
    return c * 0.42;
}

vec3 jetGlow(vec3 rd, vec3 bhDir, float cosT, float theta, float time) {
    vec3 jd = gJetAxis;
    vec3 sd = cross(bhDir, jd);
    vec3 p = rd / cosT - bhDir;
    float u = dot(p, sd);
    float v = dot(p, jd);
    float av = abs(v);

    float w = 0.0035 + av * 0.075;
    float uu = u * u / (w * w);
    float core = exp(-uu);
    float halo = exp(-uu * 0.0625) * 0.18;
    float len = exp(-av * 3.2) * smoothstep(0.09, 0.20, av);

    float act = max(bhParams.y, 0.0);
    float helix = 0.5 + 0.5 * sin(u / w * 2.2 + av * 38.0 - time * (2.2 + 1.5 * act));
    float knots = 0.55 + 0.45 * smoothstep(0.2, 0.9, sin(av * 16.0 - time * (1.4 + act)) * 0.5 + 0.5);
    float flick = 1.0 + 0.15 * act * sin(time * 2.1 + v * 9.0);
    float beam = v > 0.0 ? 1.0 : 0.4 * smoothstep(0.10, 0.17, theta);
    core *= 0.65 + 0.35 * helix;
    halo = exp(-uu * 0.03) * 0.22 + halo * 0.5;

    float I = (core * knots * flick + halo) * len * beam;

    vec3 realCol = vec3(0.55, 0.72, 1.0);
    vec3 themeCol = mix(vec3(1.0), skyColor.rgb, 0.5);
    float palette = skyExtra.y;
    float themeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
    vec3 col = mix(realCol, themeCol, themeW);
    col = mix(col, vec3(0.75, 0.45, 1.0), smoothstep(0.05, 0.4, av) * 0.45 * (1.0 - themeW));
    col = mix(col, vec3(1.0), core * exp(-av * 12.0) * 0.6);
    return col * I * 0.9;
}

vec3 decodeHDR(vec3 c) {
    c = pow(min(c, vec3(0.9975)), vec3(2.2));
    return c / max(1.0 - c, vec3(0.0055));
}

vec3 blackHole() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    // Camera uses reversed Z in 26.2; a finite interior point works on both backends.
    vec4 point = invViewProj * vec4(ndc, 0.5, 1.0);
    vec3 rd = normalize(point.xyz / point.w);

    vec3 bhDir = holeCenter(normalize(vec3(0.34, 0.62, 0.71)));
    float cosT = clamp(dot(rd, bhDir), -1.0, 1.0);
    float theta = acos(cosT);

    vec4 skySample = texture(Sky, texCoord);
    vec3 color = decodeHDR(skySample.rgb);
    float shadow = skySample.a;

    vec3 bloom = decodeHDR(texture(Bloom0, texCoord).rgb) * 1.00;
    bloom += decodeHDR(texture(Bloom1, texCoord).rgb) * 1.00;
    bloom += decodeHDR(texture(Bloom2, texCoord).rgb) * 1.00;
    bloom += decodeHDR(texture(Bloom3, texCoord).rgb) * 0.90;
    bloom += decodeHDR(texture(Bloom4, texCoord).rgb) * 0.80;
    bloom += decodeHDR(texture(Bloom5, texCoord).rgb) * 0.65;

    color += bloom * BLOOM_STRENGTH * (1.0 - 0.85 * shadow);
    color *= 1.0 - 0.6 * shadow;

    float jets = skyExtra.w;
    if (jets > 0.001 && cosT > 0.5) {
        color += jetGlow(rd, bhDir, cosT, theta, misc.x) * (jets * misc.w * (1.0 - 0.9 * shadow));
    }

    if (cosT > 0.2) {
        vec3 jd = gJetAxis;
        vec3 sdir = cross(bhDir, jd);
        vec3 p = rd / cosT - bhDir;
        float su = dot(p, sdir);
        float sv = dot(p, jd);
        float streak = exp(-sv * sv * 9000.0) * exp(-abs(su) * 5.0) * smoothstep(0.05, 0.14, abs(su));
        float pulse = 0.85 + 0.15 * sin(misc.x * 0.7);
        vec3 streakCol = mix(vec3(0.55, 0.70, 1.0), skyColor.rgb, skyExtra.y < 0.5 ? 0.6 : 0.2);
        color += streakCol * streak * 0.9 * pulse * misc.w;
    }

    float peak = max(max(color.r, color.g), color.b);
    vec3 hue = color / max(peak, 1e-5);
    float tp = pow(peak, 1.5);
    tp = tp / (1.0 + tp);
    tp = pow(tp, 1.0 / 1.5);
    tp = tp * tp * (3.0 - 2.0 * tp);
    vec3 filmic = pow(color, vec3(1.5));
    filmic = filmic / (1.0 + filmic);
    filmic = pow(filmic, vec3(1.0 / 1.5));
    filmic = filmic * filmic * (3.0 - 2.0 * filmic);
    color = mix(filmic, hue * tp, 0.55);
    color = pow(max(color, vec3(0.0)), vec3(1.22, 1.14, 1.0));
    color = clamp(color * 1.02, 0.0, 1.0);
    color = pow(color, vec3(0.7 / 2.2));
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    color = clamp(mix(vec3(gray), color, 1.18), 0.0, 1.0);

    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    float starMask = smoothstep(0.10, 0.17, theta) * clamp(1.0 - lum * 2.4, 0.0, 1.0) * misc.w;
    if (starMask > 0.002) {
        float lens = max(bhParams.x, 0.0);
        vec3 rdL = rd;
        float mag = 1.0;
        vec3 toHole = bhDir - cosT * rd;
        float tl = length(toHole);
        if (tl > 1e-4 && lens > 0.001) {
            float e2 = STAR_LENS_E2 * lens;
            float th = max(theta, 0.05);
            rdL = normalize(rd + toHole / tl * (e2 / th));
            float phi = STAR_SWIRL * lens / (theta * theta + 0.008);
            float cp = cos(phi);
            float sp = sin(phi);
            rdL = rdL * cp + cross(bhDir, rdL) * sp + bhDir * (dot(bhDir, rdL) * (1.0 - cp));
            mag = clamp(1.0 + 0.5 * e2 / (th * th), 1.0, 2.2);
        }
        float neb = NEBULA;
        vec3 bg = bhStars(rdL, misc.x);
        if (neb > 0.001) {
            float palette = skyExtra.y;
            float themeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
            bg += nebula(rdL, misc.x, skyColor.rgb, skyColor2.rgb, themeW) * neb;
        }
        color += bg * (starMask * mag);
    }
    return color;
}

void main() {
    vec3 result = blackHole();
    fragColor = vec4(result, 1.0);
}
