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

float hash2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2D(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm3(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += noise2D(p) * a;
        p = mat2(1.55, 1.15, -1.15, 1.55) * p + 7.13;
        a *= 0.5;
    }
    return v;
}

float fbm4(vec2 p) {
    float v = 0.0;
    float a = 0.53;
    for (int i = 0; i < 4; i++) {
        v += noise2D(p) * a;
        p = mat2(1.48, 1.08, -1.08, 1.48) * p + 3.71;
        a *= 0.5;
    }
    return v;
}

vec2 domeUv(vec3 dir) {
    float k = 1.0 / (1.0 + max(dir.y, -0.35));
    return dir.xz * k * 0.55 + 0.5;
}

// Shared luminous base so every mode stays clearly readable against the world.
vec3 modeBase(vec3 dir) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    return mix(primaryColor.rgb * 0.17, secondaryColor.rgb * 0.34, pow(h, 1.15));
}

vec3 starField(vec3 dir, float amount, float time) {
    if (amount <= 0.001) return vec3(0.0);
    vec2 p = domeUv(dir) * 175.0;
    vec2 cell = floor(p);
    vec2 local = fract(p) - 0.5;
    float seed = hash2(cell);
    float pick = smoothstep(1.0 - amount * 0.070, 1.0 - amount * 0.022, seed);
    float size = mix(0.10, 0.24, fract(seed * 9.17));
    vec2 off = (vec2(hash2(cell + 3.7), hash2(cell + 8.1)) - 0.5) * 0.5;
    float d = length(local - off);
    float shape = smoothstep(size, 0.0, d);
    float halo = exp(-d * (9.0 + fract(seed * 5.3) * 5.0)) * 0.35;
    float twinkle = 0.68 + 0.32 * sin(time * (1.4 + fract(seed * 4.1) * 2.2) + seed * 47.0);
    vec3 tint = mix(vec3(0.74, 0.84, 1.0), vec3(1.0, 0.87, 0.72), fract(seed * 11.7));
    return tint * ((shape * shape + halo * shape) * twinkle * pick * 1.75);
}

vec3 aurora(vec3 dir, float time, float scale, float intensity) {
    vec3 col = modeBase(dir);

    // project onto curtain plane and drift slowly sideways
    vec2 p = dir.xz / max(dir.y * 0.85 + 0.38, 0.20) * scale;
    float drift = time * 0.05;

    float acc = 0.0;
    vec3 tintAcc = vec3(0.0);
    for (int l = 0; l < 3; l++) {
        float fl = float(l);
        vec2 q = p * (1.0 + fl * 0.65) + vec2(drift * (1.0 + fl * 0.4), fl * 3.7);
        float warp = fbm3(q * 1.6) - 0.5;

        // vertical envelope: defined lower edge, long soft top
        float bottom = 0.02 + 0.09 * fl + 0.12 * warp;
        float top = 0.62 + 0.14 * fl + warp * 0.34;
        float env = smoothstep(bottom, bottom + 0.11, dir.y)
                  * exp(-max(dir.y - top, 0.0) * (5.0 - fl * 0.9));
        if (env <= 0.002) continue;

        // vertical rays warped by noise
        float rayU = q.x * 9.0 + warp * 6.5 + drift * 2.2;
        float rays = pow(0.5 + 0.5 * sin(rayU), 1.7);
        rays *= 0.70 + 0.30 * fbm3(vec2(q.x * 21.0, dir.y * 3.5 - time * 0.18));

        float w = env * rays * (1.0 - fl * 0.18);
        acc += w;
        tintAcc += mix(primaryColor.rgb, secondaryColor.rgb,
                       clamp(dir.y * 1.5 - fl * 0.25, 0.0, 1.0)) * w;
    }

    col += tintAcc * intensity * 1.15;

    // glowing lower rim
    float rim = exp(-abs(dir.y - 0.05) * 9.0) * smoothstep(0.05, 0.5, acc);
    col += accentColor.rgb * rim * intensity * 0.28;

    return col;
}

vec3 night(vec3 dir, float time, float scale, float intensity) {
    vec3 col = modeBase(dir) * 0.85;
    vec2 uv = domeUv(dir) * scale;

    // milky way band with dust structure
    vec3 axis = normalize(vec3(0.62, 0.42, 0.66));
    float md = dot(dir, axis);
    float dust = fbm3(uv * 5.5 + 7.31);
    float mw = exp(-md * md * 20.0) * (0.40 + 0.60 * dust);
    col += mix(primaryColor.rgb, vec3(0.88, 0.93, 1.0), 0.45) * mw * intensity * 0.42;

    // drifting cloud wisps
    vec2 drift = vec2(time * 0.006, sin(time * 0.004) * 0.30);
    float cn = fbm3(uv * 2.4 + drift);
    float wisp = pow(max(0.80 - cn, 0.0), 2.0);
    col += primaryColor.rgb * wisp * intensity * 0.85;

    // dense tiny stars
    vec2 sp = uv * 260.0;
    vec2 cell = floor(sp);
    vec2 f = fract(sp) - 0.5;
    float r1 = hash2(cell);
    float r2 = hash2(cell.yx + 17.0);
    float tw = 0.65 + 0.35 * sin(time * (0.9 + r1 * 2.6) + r1 * 61.0);
    float off = (hash2(cell + 3.1) - 0.5) * 0.6;
    float star = step(0.985, r1) * tw
               * smoothstep(0.22 + abs(off) * 0.1, 0.0, length(f - vec2(off, 0.0)));
    col += vec3(0.82, 0.88, 1.0) * star * 1.4 * intensity;

    // rare bright stars with cross flare
    float flareMask = step(0.9975, r2);
    float flare = flareMask
                * max(0.0, 1.0 - length(f) * 2.4)
                * max(0.0, 1.0 - (abs(f.x) + abs(f.y)) * 1.5)
                * (0.75 + 0.25 * sin(time * 1.7 + r1 * 40.0));
    col += vec3(1.0) * flare * 2.2 * intensity;

    return col;
}

vec3 snow(vec3 dir, float time, float scale, float intensity) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(primaryColor.rgb * 0.13, secondaryColor.rgb * 0.24, h);

    vec2 uv = domeUv(dir) * 2.0 - 1.0;
    float flakes = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float density = (6.0 + fi * 3.5) * scale;
        vec2 p = uv * density;
        p.y -= time * (0.55 + fi * 0.10);                            // fall down
        p.x += sin(time * (0.45 + fi * 0.13) + fi * 2.1) * 0.35;     // gentle sway
        vec2 cell = floor(p);
        vec2 local = fract(p) - 0.5;
        vec2 offset = (vec2(hash2(cell), hash2(cell + 13.7)) - 0.5) * 0.55;
        float depth = 0.45 + 0.55 * hash2(cell + 27.3);
        float d = length(local - offset);
        float size = 0.090 - fi * 0.007;
        float flake = smoothstep(size, 0.0, d);
        flake *= flake;
        float halo = exp(-d * (15.0 - fi * 1.5)) * 0.12;
        flakes += (flake * depth + halo) * (0.42 + fi * 0.13);
    }

    col += mix(primaryColor.rgb, vec3(1.0), 0.72) * flakes * intensity;
    return col;
}

vec3 sky(vec3 dir, float time, float scale, float intensity) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec2 p = domeUv(dir) * scale * 1.1 + vec2(time * 0.020, time * 0.011);

    // domain-warped clouds with softly shaded tops
    vec2 warp = vec2(fbm3(p * 1.8), fbm3(p * 1.8 + 4.7));
    vec2 cp = p * 2.4 + warp * 1.5;
    float density = fbm4(cp);
    float densityLit = fbm4(cp + vec2(-0.07, -0.05));
    float clouds = smoothstep(0.30, 0.78, density);
    float shade = clamp(0.5 + (density - densityLit) * 3.2, 0.0, 1.0);

    vec3 base = mix(primaryColor.rgb * 0.44, secondaryColor.rgb * 0.66, h);
    vec3 cloudCol = mix(accentColor.rgb * 0.92 + secondaryColor.rgb * 0.08,
                        secondaryColor.rgb, density * 0.35);
    cloudCol *= 0.76 + 0.48 * shade;

    vec3 col = mix(base, cloudCol, clouds * min(intensity, 1.2));

    // warm haze along the horizon
    float haze = 1.0 - smoothstep(0.0, 0.38, abs(dir.y));
    col = mix(col, mix(secondaryColor.rgb, accentColor.rgb, 0.40) * 0.74, haze * 0.38);
    return col;
}

vec3 starMode(vec3 dir, float time, float scale, float intensity) {
    vec3 col = modeBase(dir) * 0.65;

    // faint static sprinkle between meteors
    vec2 bp = domeUv(dir) * 220.0 * scale;
    vec2 bc = floor(bp);
    float br = hash2(bc);
    vec2 bf = fract(bp) - 0.5;
    float bstar = step(0.993, br) * smoothstep(0.16, 0.0, length(bf))
                * (0.60 + 0.40 * sin(time * 2.0 + br * 50.0));
    col += vec3(0.85, 0.90, 1.0) * bstar * 0.9 * intensity;

    // meteors falling in swaying columns
    vec2 uv = (domeUv(dir) - 0.5) * 2.0 * scale;
    float sway = sin(0.2 + uv.y * 0.8) * 0.5;
    float cols = 26.0 / max(scale, 0.4);
    float cx = floor((uv.x + sway) * cols);
    float fx = fract((uv.x + sway) * cols) - 0.5;

    float seed = hash2(vec2(cx, 5.0));
    float gate = hash2(vec2(cx, 31.0));
    if (gate > 0.35) {
        float period = 1.6 + seed * 2.4;
        float phase = fract(time / period + seed * 7.0);
        float yHead = 1.15 - phase * 1.3;
        float y = uv.y * 0.5 + 0.5;
        float d = yHead - y;
        float len = 0.10 + seed * 0.16;
        float life = smoothstep(0.0, 0.07, phase) * (1.0 - smoothstep(0.92, 1.0, phase));
        float body = exp(-max(d, 0.0) * (1.0 / len)) * step(0.0, d);
        body += exp(-abs(d) * 24.0) * 0.9;      // hot head
        float lateral = exp(-fx * fx * 22.0);
        vec3 mcol = mix(primaryColor.rgb, vec3(1.0), 0.65);
        col += mcol * body * lateral * life * intensity * 1.6;
    }
    return col;
}

vec3 glow(vec3 dir, float time, float scale, float intensity) {
    vec3 col = modeBase(dir) * 0.55;

    vec2 p = (domeUv(dir) - 0.5) * 2.0 * scale;
    for (float i = 1.0; i < 6.0; i++) {
        p.x += 0.55 / i * cos(i * 2.2 * p.y + time * 1.1);
        p.y += 0.50 / i * cos(i * 1.4 * p.x - time * 0.8);
    }

    float bands = 0.5 + 0.5 * sin(p.x * 2.0 + p.y * 3.0 + time * 0.6);
    float silk = pow(bands, 2.2);
    float lines = pow(max(sin((p.x + p.y) * 5.0 + time), 0.0), 12.0) * 0.35;

    col += mix(primaryColor.rgb, secondaryColor.rgb, silk) * silk * intensity * 1.35;
    col += accentColor.rgb * lines * intensity;
    return col;
}

vec3 plasma(vec3 dir, float time, float scale, float intensity) {
    vec3 col = modeBase(dir) * 0.60;

    vec2 uv = (domeUv(dir) - 0.5) * 1.6 * scale;
    float t = time * 0.55;
    float ca = cos(t * 0.05);
    float sa = sin(t * 0.05);
    uv = mat2(ca, -sa, sa, ca) * uv;

    vec2 q = vec2(fbm3(uv * 1.6 + vec2(0.0, t * 0.22)),
                  fbm3(uv * 1.6 + vec2(5.2, 1.3) - t * 0.17));
    float field = fbm4(uv * 2.1 + q * 3.4);
    float filaments = pow(clamp(field * 1.5 - 0.25, 0.0, 1.0), 1.6);

    vec3 neb = mix(primaryColor.rgb, secondaryColor.rgb, clamp(q.x * 1.2, 0.0, 1.0));
    neb += accentColor.rgb * pow(filaments, 3.0) * 0.9;

    col += neb * (0.35 + filaments * 0.95) * intensity;
    return col;
}

void main() {
    float time = screenTimeOpacity.z;
    float opacity = screenTimeOpacity.w;
    float mode = params.x;
    float scale = max(params.z, 0.01);
    float intensity = params.w;
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

    color += starField(dir, extra.x, time) * smoothstep(-0.12, 0.55, dir.y);

    // soft atmospheric band at the horizon
    float horizon = 1.0 - smoothstep(0.0, 0.30, abs(dir.y + 0.03));
    color += mix(secondaryColor.rgb, accentColor.rgb, 0.45) * horizon * 0.10;

    // mild zenith falloff — keeps every mode readable low near the horizon too
    color *= 0.86 + 0.26 * smoothstep(-0.55, 0.85, dir.y);
    // filmic-style curve with higher exposure than before
    color = (color * 1.32) / (1.0 + 0.22 * color);

    float dith = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (dith - 0.5) * (1.6 / 255.0);
    // The procedural dome fully replaces Minecraft's sky; alpha stays opaque.
    fragColor = vec4(color, opacity);
}
