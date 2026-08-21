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

// Compact value-noise helpers. The previous sky used several 5-8 octave,
// nested 3D fields per pixel; all animated modes now share this cheaper 2D field.
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

float fbmFast(vec2 p) {
    float value = 0.0;
    float amp = 0.57;
    for (int i = 0; i < 3; i++) {
        value += noise2D(p) * amp;
        p = mat2(1.55, 1.15, -1.15, 1.55) * p + 7.13;
        amp *= 0.5;
    }
    return value;
}

float fbmCloud(vec2 p) {
    float value = 0.0;
    float amp = 0.56;
    for (int i = 0; i < 4; i++) {
        value += noise2D(p) * amp;
        p = mat2(1.48, 1.08, -1.08, 1.48) * p + 3.71;
        amp *= 0.5;
    }
    return value;
}

vec2 domeUv(vec3 dir) {
    float k = 1.0 / (1.0 + max(dir.y, -0.35));
    return dir.xz * k * 0.55 + 0.5;
}

vec3 starField(vec3 dir, float amount, float time) {
    if (amount <= 0.001) return vec3(0.0);
    vec2 p = domeUv(dir) * 175.0;
    vec2 cell = floor(p);
    vec2 local = fract(p) - 0.5;
    float seed = hash2(cell);
    float pick = smoothstep(1.0 - amount * 0.065, 1.0 - amount * 0.020, seed);
    float size = mix(0.10, 0.22, fract(seed * 9.17));
    float shape = smoothstep(size, 0.0, length(local));
    float twinkle = 0.70 + 0.30 * sin(time * (1.4 + fract(seed * 4.1) * 2.0) + seed * 47.0);
    vec3 tint = mix(vec3(0.72, 0.82, 1.0), vec3(1.0, 0.86, 0.70), fract(seed * 11.7));
    return tint * shape * shape * twinkle * pick * 1.45;
}

vec3 aurora(vec3 dir, float time, float scale, float intensity) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.10, secondaryColor.rgb * 0.24, h);
    vec2 p = dir.xz * scale;
    float warp = fbmFast(p * 2.2 + vec2(time * 0.035, -time * 0.020)) - 0.5;
    float rays = 0.72 + 0.28 * fbmFast(p * 7.0 + vec2(time * 0.060, 4.7));

    float u0 = dot(dir.xz, vec2(1.0, 0.0)) * (7.0 * scale);
    float u1 = dot(dir.xz, vec2(-0.737, 0.675)) * (7.0 * scale);
    float u2 = dot(dir.xz, vec2(0.087, -0.996)) * (7.0 * scale);
    float folds = pow(0.5 + 0.5 * sin(u0 + warp * 4.5 + time * 0.10), 2.4);
    folds += pow(0.5 + 0.5 * sin(u1 + warp * 4.0 + time * 0.13), 2.4) * 0.78;
    folds += pow(0.5 + 0.5 * sin(u2 + warp * 3.6 + time * 0.16), 2.4) * 0.58;
    folds = clamp(folds, 0.0, 1.35);

    float bottom = -0.08 + 0.06 * sin(p.x * 2.3 + time * 0.08);
    float top = 0.62 + warp * 0.24;
    float window = smoothstep(bottom, bottom + 0.15, dir.y) * (1.0 - smoothstep(top - 0.18, top, dir.y));
    float rim = smoothstep(bottom + 0.28, bottom + 0.02, dir.y) * window;
    vec3 curtain = mix(primaryColor.rgb, secondaryColor.rgb, h * 0.72 + 0.14);
    return base + curtain * folds * rays * window * intensity * 0.78
                + accentColor.rgb * rim * folds * intensity * 0.22;
}

vec3 night(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = domeUv(dir) * scale;
    vec2 drift = vec2(time * 0.006, sin(time * 0.004) * 0.35);
    float cloudNoise = fbmFast(uv * 2.5 + drift);
    float cloud = pow(max(0.76 - cloudNoise, 0.0), 2.0);
    float bandNoise = fbmFast(uv * 7.0 + drift * 1.4 + 12.52);
    float md = dot(dir, vec3(0.62, 0.42, 0.66));
    float milky = exp(-md * md * 25.0) * (0.30 + 0.70 * bandNoise);

    vec2 starCell = floor(uv * 360.0);
    float r1 = hash2(starCell);
    float r2 = hash2(starCell.yx + 17.0);
    float star = r1 * pow(r2, 18.0) * (0.65 + 0.35 * sin(time * r1 * 2.7 + 3.0));
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.08, secondaryColor.rgb * 0.22, h);
    vec3 mwCol = mix(primaryColor.rgb, vec3(0.9, 0.95, 1.0), 0.5);
    return base + mwCol * milky * intensity * 0.28
         + (primaryColor.rgb * cloud * 0.72 + accentColor.rgb * star) * intensity;
}

vec3 snow(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = domeUv(dir) * 2.0 - 1.0;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.10, secondaryColor.rgb * 0.18, h);
    float flakes = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float density = (7.0 + fi * 3.0) * scale;
        vec2 p = uv * density + vec2(time * (0.35 + fi * 0.06), time * (0.62 + fi * 0.08));
        vec2 cell = floor(p);
        vec2 local = fract(p) - 0.5;
        vec2 offset = vec2(hash2(cell), hash2(cell + 13.7)) - 0.5;
        float flake = smoothstep(0.095 - fi * 0.008, 0.0, length(local - offset * 0.55));
        flakes += flake * (0.48 + fi * 0.10);
    }
    return base + mix(primaryColor.rgb, accentColor.rgb, 0.75) * flakes * intensity;
}

vec3 sky(vec3 dir, float time, float scale, float intensity) {
    vec2 p = domeUv(dir) * scale * 1.1 + vec2(time * 0.018, time * 0.010);
    float density = fbmCloud(p * 2.7);
    float clouds = smoothstep(0.34, 0.88, density) * intensity;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.30, secondaryColor.rgb * 0.50, h);
    vec3 cloud = mix(accentColor.rgb, secondaryColor.rgb, density * 0.26);
    vec3 col = mix(base, cloud, clouds);
    float haze = 1.0 - smoothstep(0.0, 0.35, abs(dir.y));
    return mix(col, mix(secondaryColor.rgb, accentColor.rgb, 0.35) * 0.55, haze * 0.30);
}

vec3 starMode(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = (domeUv(dir) - 0.5) * 2.0 * scale;
    uv.x += sin(0.2 + uv.y * 0.8) * 0.5;
    uv.x *= 50.0;
    float dx = fract(uv.x);
    uv.x = floor(uv.x);
    uv.y *= 0.15;
    float seed = sin(uv.x * 215.4);
    float width = cos(uv.x * 33.1) * 0.3 + 0.7;
    float trail = fract(uv.y + time * 0.4 * width + seed) * mix(95.0, 35.0, width);
    trail = sin(smoothstep(0.0, 1.0, 1.0 / max(trail * trail, 0.001)) * 3.14159) * width * 5.0;
    trail *= sin(dx * 3.14159) * sin(dx * 3.14159);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(primaryColor.rgb * 0.05, secondaryColor.rgb * 0.18, h);
    return base + mix(primaryColor.rgb, secondaryColor.rgb, width) * sqrt(max(trail, 0.0)) * intensity;
}

vec3 glow(vec3 dir, float time, float scale, float intensity) {
    vec2 uv = (domeUv(dir) - 0.5) * 2.0 * scale;
    for (float i = 1.0; i < 6.0; i++) {
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
    vec2 q = vec2(fbmFast(uv * 1.7 + vec2(0.0, t * 0.20)),
                  fbmFast(uv * 1.7 + vec2(5.2, 1.3) - t * 0.15));
    float field = fbmFast(uv * 2.35 + q * 3.2 + vec2(t * 0.12, -t * 0.08));
    float bands = sin((uv.x + uv.y) * 6.2831 + t * 2.0 + field * 6.2831)
                + sin(length(uv) * 10.0 - t * 2.6 + q.x * 4.0);
    bands *= 0.5;
    float mask = clamp(0.5 + field * 0.58 + bands * 0.26, 0.0, 1.0);
    vec3 color = mix(primaryColor.rgb, secondaryColor.rgb, mask);
    color += accentColor.rgb * pow(max(bands, 0.0), 3.0) * 0.24;
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    return mix(primaryColor.rgb * 0.05, secondaryColor.rgb * 0.12, h) + color * intensity;
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
    float horizon = 1.0 - smoothstep(0.0, 0.28, abs(dir.y + 0.02));
    color += mix(secondaryColor.rgb, accentColor.rgb, 0.5) * horizon * 0.06;
    color *= 0.68 + 0.40 * smoothstep(-0.55, 0.75, dir.y);
    color = (color * 1.10) / (1.0 + 0.14 * color);

    float dith = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (dith - 0.5) * (1.6 / 255.0);
    // The procedural dome must fully replace Minecraft's sky. Keep its alpha
    // opaque in every non-Blur mode; vanillaSky remains only a module setting
    // for compatibility and no longer reveals the vanilla sky underneath.
    fragColor = vec4(color, opacity);
}
