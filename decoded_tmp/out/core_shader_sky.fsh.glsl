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

float worley2D(vec2 p) {
    vec2 n = floor(p);
    vec2 f = fract(p);
    float d = 1.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = vec2(hash2(n + g), hash2(n + g + vec2(17.3, 43.7)));
            vec2 r = g + o - f;
            d = min(d, dot(r, r));
        }
    }
    return sqrt(d);
}

float worleyFbm(vec2 p) {
    float w1 = 1.0 - worley2D(p);
    float w2 = 1.0 - worley2D(p * 2.15 + 3.14);
    float w3 = 1.0 - worley2D(p * 4.35 + 7.81);
    return w1 * 0.52 + w2 * 0.32 + w3 * 0.16;
}

float billowFbm(vec2 p) {
    float v = 0.0;
    float a = 0.52;
    for (int i = 0; i < 4; i++) {
        float n = noise2D(p);
        v += abs(n * 2.0 - 1.0) * a;
        p = mat2(1.52, 1.12, -1.12, 1.52) * p + 3.71;
        a *= 0.5;
    }
    return v;
}

float ridgedFbm(vec2 p) {
    float v = 0.0;
    float a = 0.52;
    for (int i = 0; i < 4; i++) {
        float n = noise2D(p);
        v += (1.0 - abs(n * 2.0 - 1.0)) * a;
        p = mat2(1.52, 1.12, -1.12, 1.52) * p + 3.71;
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

vec3 overcast(vec3 dir, float time, float scale, float intensity, float flashAmt) {
    // 1. Perspective atmospheric projection onto cloud plane
    float viewH = max(dir.y + 0.13, 0.035);
    vec2 uv = (dir.xz / viewH) * (scale * 0.72);

    // 2. Wind velocities across atmospheric layers (smooth continuous motion)
    vec2 windMain = vec2(time * 0.022, time * 0.008);
    vec2 windTurb = vec2(-time * 0.015, time * 0.020);
    vec2 windScud = vec2(time * 0.048, time * 0.018);

    // 3. Turbulent domain warping (swirling cloud masses)
    vec2 warp1 = vec2(
        fbm4(uv * 0.95 + windMain),
        fbm4(uv * 0.95 + windMain + vec2(5.2, 1.3))
    ) - 0.5;

    vec2 warp2 = vec2(
        fbm4(uv * 1.8 + warp1 * 1.5 + windTurb),
        fbm4(uv * 1.8 + warp1 * 1.5 + windTurb + vec2(3.1, 7.4))
    ) - 0.5;

    vec2 p = uv * 1.75 + warp2 * 1.8 + windMain;

    // 4. Perlin-Worley cloud modeling (cauliflower billows, puffy cumulus tufts)
    float baseFbm = fbm4(p);
    float billowNoise = billowFbm(p * 1.4 + 1.7);
    float worleyBillows = worleyFbm(p * 1.8);
    
    // High frequency erosion (sharp wispy frayed edges instead of blur)
    float edgeErosion = ridgedFbm(p * 3.8 - windTurb * 0.6);
    float microNoise = fbm3(p * 8.5 + windMain * 1.2);

    // Combine into solid, puffy storm cloud density with crisp billowy boundaries
    float rawDensity = baseFbm * 0.40 + billowNoise * 0.25 + worleyBillows * 0.45;
    rawDensity -= (1.0 - edgeErosion) * 0.18 + (1.0 - microNoise) * 0.08;
    
    // Non-linear density shaping (thick storm masses with crisp outlines)
    float cloudDensity = clamp((rawDensity - 0.26) / 0.52, 0.0, 1.0);
    cloudDensity = pow(cloudDensity, 1.25);
    float cloudMask = smoothstep(0.08, 0.75, cloudDensity);

    // 5. Directional lighting & self-shadowing towards the rift
    vec2 lightOffset = normalize(vec2(0.0, -1.0)) * 0.065;
    float litBase = fbm4(p + lightOffset) * 0.40 + worleyFbm((p + lightOffset) * 1.8) * 0.45;
    float litDensity = clamp((litBase - 0.26) / 0.52, 0.0, 1.0);
    
    float lightGrad = (cloudDensity - litDensity) * 7.5;
    float shade = clamp(0.38 + lightGrad * 0.65, 0.0, 1.0);
    // Sharp crisp silver rim on edges facing the light
    float rim = pow(clamp(-lightGrad * 1.4, 0.0, 1.0), 2.2);

    // 6. Low Scud / Fractus Layer (dark, shredded storm cloud fragments drifting fast)
    vec2 scudP = uv * 3.2 + windScud;
    float scudNoise = fbm4(scudP) * (1.0 - worley2D(scudP * 2.0));
    float scudMask = smoothstep(0.32, 0.72, scudNoise) * smoothstep(0.01, 0.35, dir.y) * 0.88;

    // 7. Luminous Rift / Break in the clouds (as in reference screenshot)
    vec3 riftDir = normalize(vec3(0.0, 0.14, -1.0));
    float riftDot = max(dot(dir, riftDir), 0.0);
    float riftCore = pow(riftDot, 28.0) * 2.4;
    float riftMid = pow(riftDot, 8.0) * 1.2;
    float riftWide = pow(riftDot, 2.2) * 0.50;
    float totalRiftGlow = riftCore + riftMid + riftWide;

    // Cloud opening hole near horizon where moonlight bursts through
    float breakOpening = smoothstep(0.72, 0.22, cloudDensity) * smoothstep(-0.06, 0.42, dir.y);

    // 8. Photographic storm color palette (charcoal/slate/navy vs brilliant silver)
    vec3 deepNightSky = mix(vec3(0.014, 0.020, 0.032), vec3(0.032, 0.046, 0.070), clamp(dir.y * 0.8 + 0.2, 0.0, 1.0));
    deepNightSky = mix(deepNightSky, primaryColor.rgb * 0.06, 0.25);

    vec3 riftLight = mix(vec3(0.70, 0.82, 0.94), vec3(0.94, 0.97, 1.0), riftCore * 0.5);
    riftLight = mix(riftLight, secondaryColor.rgb * 0.6 + accentColor.rgb * 0.4, 0.12);

    // Deep dark storm clouds (charcoal base with ambient light on puffy crests)
    vec3 cloudShadow = mix(vec3(0.010, 0.015, 0.024), vec3(0.024, 0.034, 0.048), shade);
    vec3 cloudLit = mix(cloudShadow, vec3(0.09, 0.13, 0.19), shade * shade);
    
    // Crisp silver rim on cloud contours facing the rift break
    vec3 silverRim = riftLight * (rim * (0.55 + riftMid * 1.8));
    cloudLit += silverRim;

    // Sky background with rift moonlight glow
    vec3 skyBg = deepNightSky + (riftLight * totalRiftGlow * 0.48);

    // Direct bright opening through the cloud rift
    vec3 riftBurst = riftLight * (breakOpening * (riftCore * 2.0 + riftMid * 1.1 + 0.22));

    // Composite main cloud deck over sky
    vec3 col = mix(skyBg, cloudLit, cloudMask * 0.96);
    col += riftBurst * (1.0 - cloudMask * 0.60);

    // Composite low dark scud silhouettes drifting in front of the lit rift
    vec3 scudColor = mix(vec3(0.012, 0.016, 0.024), vec3(0.028, 0.038, 0.052), shade * 0.5);
    col = mix(col, scudColor, scudMask);

    // 9. Diagonal storm / rain / virga streaks across upper sky
    vec2 streakUV = vec2(dir.x * 26.0 + dir.y * 13.0 + time * 0.42, dir.y * 38.0 - time * 1.5);
    vec2 sCell = floor(streakUV);
    vec2 sFract = fract(streakUV) - 0.5;
    float sHash = hash2(sCell);
    float sPick = step(0.960, sHash);
    float sLine = smoothstep(0.12, 0.0, abs(sFract.x)) * (0.60 + 0.40 * sin(time * 6.0 + sHash * 45.0));
    float virga = sPick * sLine * smoothstep(0.04, 0.52, dir.y) * 0.36;
    vec3 streakColor = mix(vec3(0.35, 0.45, 0.58), vec3(0.75, 0.85, 0.98), sHash);
    col += virga * streakColor;

    // 10. Low horizon atmospheric mist
    float horizonHaze = 1.0 - smoothstep(0.0, 0.24, abs(dir.y + 0.02));
    vec3 horizonCol = mix(vec3(0.11, 0.15, 0.19), riftLight * 0.40, riftDot * 0.65);
    col = mix(col, horizonCol, horizonHaze * 0.52);

    // 11. Thunderstorm / lightning flash illumination
    float flash = max(flashAmt, 0.0);
    if (flash > 0.001) {
        vec3 flashColor = vec3(0.85, 0.92, 1.0);
        float cloudScatter = 0.80 + 0.70 * (1.0 - cloudMask * 0.35) + shade * 0.65;
        col += flashColor * flash * cloudScatter * 1.5;
    }

    // 12. Contrast enhancement and intensity scale
    col = mix(modeBase(dir) * 0.35 + deepNightSky * 0.65, col, intensity);
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
    } else if (mode < 6.5) {
        color = plasma(dir, time, scale, intensity);
    } else {
        color = overcast(dir, time, scale, intensity, extra.w);
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
