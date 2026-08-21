#version 330

layout(std140) uniform ShaderSkyData {
    vec4 screenTimeOpacity;
    vec4 primaryColor;
    vec4 secondaryColor;
    vec4 accentColor;
    vec4 params;
    vec4 extra;
};

in vec3 skyDir;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(123.34, 456.21, 321.79));
    p += dot(p, p.yzx + 45.32);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amp;
        p = mat3(
            1.53, 0.72, 0.21,
           -0.64, 1.39, 0.57,
            0.31,-0.48, 1.71
        ) * p;
        amp *= 0.5;
    }
    return value;
}

vec2 domeUv(vec3 dir) {
    dir = normalize(dir);
    float k = 1.0 / (1.0 + max(dir.y, -0.35));
    return dir.xz * k * 0.55 + 0.5;
}

float hash2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        value += noise2(p) * amp;
        p = mat2(1.6, 1.2, -1.2, 1.6) * p;
        amp *= 0.5;
    }
    return value;
}

// Star field with variable size, twinkle speed and color temperature
vec3 starField(vec3 dir, float amount, float time) {
    if (amount <= 0.001) return vec3(0.0);
    vec3 grid = dir * 150.0;
    vec3 cell = floor(grid);
    vec3 local = fract(grid) - 0.5;
    float h = hash(cell);
    float thr = 1.0 - amount * 0.055;
    float w = max((1.0 - thr) * 0.5, 1e-4);
    float pick = smoothstep(thr, thr + w, h);
    if (pick <= 0.0) return vec3(0.0);
    float seed = fract(h * 7.31);
    float size = mix(0.10, 0.26, seed);
    float shape = smoothstep(size, 0.0, length(local));
    float tw = 0.55 + 0.45 * sin(time * (1.2 + seed * 2.6) + h * 61.7);
    vec3 tint = mix(vec3(0.72, 0.82, 1.0), vec3(1.0, 0.86, 0.70), fract(h * 11.7));
    return tint * (shape * shape) * tw * pick * 1.7;
}

// Aurora with vertical curtains, internal rays and a bright lower rim
vec3 aurora(vec3 dir, float time, float scale, float intensity) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.10, secondaryColor.rgb * 0.24, h);

    vec3 acc = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float ph = fi * 2.39996;
        vec2 c = vec2(cos(ph), sin(ph));
        float u = dot(dir.xz, c) * (2.2 * scale);

        float warp = fbm(dir * (1.4 * scale) + vec3(0.0, time * 0.03, fi * 9.1)) - 0.5;

        // curtain folds across the sky
        float folds = 0.5 + 0.5 * sin(u * 3.5 + warp * 5.0 + time * (0.10 + fi * 0.03));
        folds = pow(folds, 2.5);

        // vertical rays stretched along the curtain
        float rays = 0.55 + 0.45 * fbm(vec3(dir.x * 9.0, dir.y * 1.2, dir.z * 9.0)
                          + vec3(time * 0.06, 0.0, fi * 4.7));

        // height window with wavy top and bottom edges
        float bottom = -0.08 + 0.10 * sin(u * 1.7 + time * 0.07 + fi * 1.9);
        float top = 0.62 + 0.22 * fbm(dir * (2.2 * scale) + vec3(fi * 3.3, time * 0.02, 0.0));
        float win = smoothstep(bottom, bottom + 0.16, dir.y) * (1.0 - smoothstep(top - 0.18, top, dir.y));

        float curt = folds * rays * win;

        vec3 cA = mix(primaryColor.rgb, secondaryColor.rgb, 0.35 + 0.3 * fi);
        vec3 cB = mix(secondaryColor.rgb, primaryColor.rgb, 0.4);
        vec3 cc = mix(cA, cB, clamp(dir.y * 1.4, 0.0, 1.0));

        // bright lower rim like a real aurora
        float rim = smoothstep(bottom + 0.30, bottom + 0.02, dir.y) * win;

        acc += cc * curt * (0.75 + 0.5 * intensity);
        acc += accentColor.rgb * rim * folds * 0.30;
    }
    return base + acc * 0.55 * intensity;
}

// Night sky: nebula clouds + Milky Way band with dust lanes
vec3 night(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = domeUv(dir) * scale;
    vec2 drift = vec2(time * 0.0062379, cos(time * 0.0962379)) * (sin(time * 0.0041839) + 1.1);

    float n1 = fbm2(uv * 2.0 + drift);
    float n2 = fbm2(uv * 6.0 + drift * 1.15 + 12.523);
    float cloud = pow(max(0.8 - n1, 0.0), 2.0);
    float core = pow(max((0.9 - n2) * cloud, 0.0), 1.1);

    // Milky Way band around a tilted great circle
    vec3 mwN = normalize(vec3(0.62, 0.42, 0.66));
    float md = dot(dir, mwN);
    float mw = exp(-md * md * 26.0);
    float mwNoise = fbm(dir * 7.0 * scale + vec3(time * 0.004));
    float mwDust = smoothstep(0.35, 0.75, fbm(dir * 11.0 * scale + 31.7));
    float milky = mw * (0.35 + 0.65 * mwNoise) * (1.0 - 0.55 * mwDust);

    vec2 starCell = floor(uv * 420.0);
    float r1 = hash2(starCell);
    float r2 = hash2(starCell.yx + 17.0);
    float twinkle = sin((time + 10.0) * r1 * 2.7) * 0.8 + 0.5;
    float star = r1 * pow(r2, 20.0) * twinkle * 0.9;

    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.08, secondaryColor.rgb * 0.22, h);

    vec3 mwCol = mix(primaryColor.rgb, vec3(0.9, 0.95, 1.0), 0.5);
    return base
         + mwCol * milky * 0.30 * intensity
         + (primaryColor.rgb * cloud * 0.55 + secondaryColor.rgb * core * 0.95 + accentColor.rgb * star) * intensity;
}

vec3 snow(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = domeUv(dir) * 2.0 - 1.0;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.10, secondaryColor.rgb * 0.18, h);
    float c = smoothstep(1.0, 0.3, clamp(uv.y * 0.3 + 0.8, 0.0, 0.75)) * 0.5;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float sc = (6.0 + fi * 2.5) * scale;
        vec2 p = uv;
        p += time / sc;
        p.y += time * 2.0 / sc;
        p.x += sin(p.y + time * 0.5) / sc;
        p *= sc;
        vec2 cell = floor(p);
        vec2 local = fract(p);
        vec2 flake = 0.5 + 0.35 * sin(11.0 * fract(sin((cell + sc) * mat2(7.0, 3.0, 6.0, 5.0)) * 5.0)) - local;
        c += smoothstep(0.035, 0.0, length(flake)) * (0.45 + fi * 0.08);
    }
    return base + mix(primaryColor.rgb, accentColor.rgb, 0.75) * c * intensity;
}

// Cloud density field (iq-style warped fbm)
float cloudDensity(vec2 p, float time) {
    float q = fbm2(p * 0.5);
    vec2 uv = p - q * 0.6 + time * 0.03;
    float r = 0.0;
    float weight = 0.8;
    for (int i = 0; i < 8; i++) {
        r += abs(weight * noise2(uv));
        uv = mat2(1.6, 1.2, -1.2, 1.6) * uv + time * 0.03;
        weight *= 0.7;
    }
    float f = 0.0;
    uv = p - q * 0.6 + time * 0.03;
    weight = 0.2;
    for (int i = 0; i < 8; i++) {
        f += weight * noise2(uv);
        uv = mat2(1.6, 1.2, -1.2, 1.6) * uv + time * 0.03;
        weight *= 0.6;
    }
    return clamp(0.2 + 8.0 * f * r + r * 0.25, 0.0, 1.0);
}

// Day sky: volumetric-looking clouds with fake sun shading and horizon haze
vec3 sky(vec3 dir, float time, float scale, float intensity) {
    vec2 p = domeUv(dir) * scale * 1.1;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    float d1 = cloudDensity(p, time);
    // sample density slightly offset toward the light for cheap shading
    float d2 = cloudDensity(p - vec2(0.035, 0.05), time);
    float shade = clamp(0.55 + (d2 - d1) * 6.0, 0.15, 1.15);

    vec3 base = mix(primaryColor.rgb * 0.30, secondaryColor.rgb * 0.50, h);

    vec3 cloudBright = mix(accentColor.rgb, secondaryColor.rgb, 0.15);
    vec3 cloud = cloudBright * shade;

    float clouds = clamp(d1 * intensity, 0.0, 1.0);
    vec3 col = mix(base, cloud, clouds);

    // soft horizon haze
    float haze = 1.0 - smoothstep(0.0, 0.35, abs(dir.y));
    col = mix(col, mix(secondaryColor.rgb, accentColor.rgb, 0.35) * 0.55, haze * 0.35);

    return col;
}

vec3 starMode(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = (domeUv(dir) - 0.5) * vec2(2.0, 2.0) * scale;
    uv.x += sin(0.2 + uv.y * 0.8) * 0.5;
    uv.x *= 50.0;
    float dx = fract(uv.x);
    uv.x = floor(uv.x);
    uv.y *= 0.15;
    float o = sin(uv.x * 215.4);
    float s = cos(uv.x * 33.1) * 0.3 + 0.7;
    float trail = mix(95.0, 35.0, s);
    float yv = fract(uv.y + time * 0.4 * s + o) * trail;
    yv = 1.0 / max(yv, 0.001);
    yv = smoothstep(0.0, 1.0, yv * yv);
    yv = sin(yv * 3.14159) * (s * 5.0);
    float d2 = sin(dx * 3.14159);
    yv *= d2 * d2;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.05, secondaryColor.rgb * 0.18, h);
    return base + mix(primaryColor.rgb, secondaryColor.rgb, s) * sqrt(max(yv, 0.0)) * intensity;
}

vec3 glow(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = (domeUv(dir) - 0.5) * 2.0 * scale;
    for (float i = 1.0; i < 10.0; i++) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + time);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + time);
    }
    float wave = 0.1 / max(abs(sin(time - uv.y - uv.x)), 0.035);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.04, secondaryColor.rgb * 0.10, h);
    return base + mix(primaryColor.rgb, secondaryColor.rgb, smoothstep(0.0, 1.2, wave)) * wave * intensity;
}

vec3 plasma(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = (domeUv(dir) - 0.5) * 1.6 * scale;
    float t = time * 0.6;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // domain-warped fractal noise -> organic plasma blobs
    vec2 q = vec2(fbm2(uv * 1.6 + vec2(0.0, t * 0.20)),
                  fbm2(uv * 1.6 + vec2(5.2, 1.3) - t * 0.15));
    vec2 r = vec2(fbm2(uv * 2.0 + 4.0 * q + vec2(1.7, 9.2) + t * 0.13),
                  fbm2(uv * 2.0 + 4.0 * q + vec2(8.3, 2.8) - t * 0.11));
    float f = fbm2(uv * 2.4 + 4.0 * r);

    // neon plasma sine bands
    float bands = sin((uv.x + uv.y) * 6.2831 + t * 2.0 + f * 6.2831)
                + sin((uv.x - uv.y) * 5.0 - t * 1.5 + f * 6.2831)
                + sin(length(uv) * 11.0 - t * 3.0);
    bands *= 0.3333;

    float m = clamp(0.5 + 0.5 * f + 0.30 * bands, 0.0, 1.0);
    vec3 pcol = mix(primaryColor.rgb, secondaryColor.rgb, m);
    pcol += accentColor.rgb * pow(max(bands, 0.0), 3.0) * 0.30;

    vec3 base = mix(primaryColor.rgb * 0.05, secondaryColor.rgb * 0.12, h);
    return base + pcol * intensity;
}

void main() {
    // time is pre-integrated with speed on the CPU side (no jump on slider change)
    float time = screenTimeOpacity.z;

    float opacity = screenTimeOpacity.w;
    float mode = params.x;
    float scale = max(params.z, 0.01);
    float intensity = params.w;
    float starAmount = extra.x;
    float vanillaBlend = extra.y;

    vec3 dir = normalize(skyDir);
    vec3 color;

    if (mode < 0.5) {
        color = aurora(dir, time, scale, intensity);
    } else if (mode < 1.5) {
        color = night(dir, time, scale, intensity);
    } else if (mode < 2.5) {
        color = snow(dir, time, scale, intensity);
    } else if (mode < 3.5) {
        color = sky(dir, time, scale, intensity);
    } else if (mode < 4.5) {
        color = starMode(dir, time, scale, intensity);
    } else if (mode < 5.5) {
        color = glow(dir, time, scale, intensity);
    } else {
        color = plasma(dir, time, scale, intensity);
    }

    // stars over every mode, fading toward the horizon
    color += starField(dir, starAmount, time) * smoothstep(-0.12, 0.55, dir.y);

    // gentle light near the horizon
    float horizon = 1.0 - smoothstep(0.0, 0.28, abs(dir.y + 0.02));
    color += mix(secondaryColor.rgb, accentColor.rgb, 0.5) * horizon * 0.06;

    // slightly darker below the horizon
    color *= 0.68 + 0.40 * smoothstep(-0.55, 0.75, dir.y);

    // soft highlight rolloff (no ugly clipping on bright bands)
    color *= 1.10;
    color = color / (1.0 + 0.14 * color);

    // dithering to kill gradient banding
    float dith = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (dith - 0.5) * (1.6 / 255.0);

    float alpha = opacity * mix(1.0, 0.72, clamp(vanillaBlend, 0.0, 1.0));
    fragColor = vec4(color, alpha);
}
