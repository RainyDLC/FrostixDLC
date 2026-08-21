#version 150

in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D BlurSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlassData {
    vec4 resolution;   // x=width, y=height, z=saturation, w=doReflect
    vec4 tintColor;    // rgb tint, a unused
    vec4 settings;     // x=tintIntensity, y=edgeGlowIntensity, z,w unused
    vec4 iceParams;    // x=time(sec), y=iceIntensity, z=frostScale, w=crackIntensity
    vec4 smokeParams;  // x=smokeAmount, y=smokeScale, z=smokeSpeed, w=smokeReach(px)
};

// ---------- noise helpers ----------
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        v += amp * vnoise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return v;
}

// cellular / voronoi distance — used for the cracked-ice facets
vec2 voronoi(vec2 p) {
    vec2 g = floor(p);
    vec2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y));
            vec2 r = o + vec2(hash21(g + o), hash21(g + o + 7.1)) - f;
            float d = dot(r, r);
            if (d < d1) { d2 = d1; d1 = d; }
            else if (d < d2) { d2 = d; }
        }
    }
    return vec2(sqrt(d1), sqrt(d2));
}

vec3 adjustSaturation(vec3 color, float saturation) {
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    return mix(vec3(gray), color, saturation);
}

// soft halo: how close an outside pixel is to the hand edge (0..1)
float edgeHalo(vec2 uv, float reachPx) {
    vec2 px = reachPx / resolution.xy;
    float halo = 0.0;
    for (int r = 1; r <= 3; r++) {
        float rf = float(r) / 3.0;
        for (int a = 0; a < 8; a++) {
            float ang = float(a) * 0.7853981634; // 2pi/8
            vec2 off = vec2(cos(ang), sin(ang)) * px * rf;
            float m = texture(MaskSampler, uv + off).r;
            halo = max(halo, m * (1.0 - rf * 0.85));
        }
    }
    return clamp(halo, 0.0, 1.0);
}

// smoothed mask — averaged disk, used both for the anti-aliased silhouette
// and to build the refraction normal. 12 directions × 2 radii = soft gradient.
float softMask(vec2 uv, float r) {
    vec2 px = r / resolution.xy;
    float s = texture(MaskSampler, uv).r;
    float w = 1.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235987756; // 2pi/12
        vec2 d = vec2(cos(a), sin(a));
        s += texture(MaskSampler, uv + d * px).r * 0.6;
        s += texture(MaskSampler, uv + d * px * 0.55).r * 0.9;
        w += 1.5;
    }
    return s / w;
}

// wide multi-ring blur of the mask — rounds off the blocky silhouette so the
// glass edge is smooth (no stair-stepping). Run only near the hand.
float edgeCoverage(vec2 uv, float r) {
    vec2 px = r / resolution.xy;
    float s = texture(MaskSampler, uv).r;
    float w = 1.0;
    for (int ring = 1; ring <= 4; ring++) {
        float rr = float(ring) / 4.0;
        float wr = 1.0 - rr * 0.45;
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5235987756 + rr * 0.6; // stagger angles per ring
            vec2 d = vec2(cos(a), sin(a)) * px * rr;
            s += texture(MaskSampler, uv + d).r * wr;
            w += wr;
        }
    }
    return s / w;
}

// fire mist source: how much of the hand silhouette lies below this pixel,
// with a slight sideways wobble so the rising column isn't perfectly straight.
float smokeSource(vec2 uv, float reachPx, float t, float spd) {
    float src = 0.0;
    for (int i = 0; i <= 8; i++) {
        float f = float(i) / 8.0;
        float wob = (vnoise(vec2(uv.x * 40.0, t * spd * 1.5 + f * 4.0)) - 0.5)
                  * reachPx * 0.30;
        vec2 off = vec2(wob / resolution.x, -(f * reachPx) / resolution.y);
        float m = texture(MaskSampler, uv + off).r;
        src = max(src, m * (1.0 - f * 0.80));
    }
    return src;
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float maskValue = texture(MaskSampler, texCoord).r;

    float time = iceParams.x;
    float iceIntensity = iceParams.y;
    float frostScale = max(1.0, iceParams.z);
    float crackIntensity = iceParams.w;
    float smokeAmount = smokeParams.x;
    float smokeScale = max(0.5, smokeParams.y);
    float smokeSpeed = smokeParams.z;
    float smokeReach = smokeParams.w;

    float saturation = resolution.z;
    float doReflect = resolution.w;
    float tintIntensity = settings.x;

    float refractStrength = iceParams.y;          // how hard the edges bend
    float refractReach = max(2.0, iceParams.z);   // width of the distortion band (px)

    // edge-smoothing radius in px, driven from Java (settings.z).
    // 0 = silhouette copies the item exactly, larger = molten glass rim.
    float edgeSmoothPx = max(0.0, settings.z);

    // the refraction normal always needs a wide radius, otherwise the gradient is noise
    float gradPx = max(edgeSmoothPx, 8.0);

    // ---- cheap early-out: skip work for pixels well away from the hand ----
    float dilatePx = (refractStrength > 0.001 ? gradPx : max(edgeSmoothPx, 1.0)) + 2.0;
    vec2 dpx = vec2(dilatePx) / resolution.xy;
    float md = maskValue;
    md = max(md, texture(MaskSampler, texCoord + vec2(dpx.x, 0.0)).r);
    md = max(md, texture(MaskSampler, texCoord - vec2(dpx.x, 0.0)).r);
    md = max(md, texture(MaskSampler, texCoord + vec2(0.0, dpx.y)).r);
    md = max(md, texture(MaskSampler, texCoord - vec2(0.0, dpx.y)).r);

    float fireSrc = 0.0;
    if (smokeAmount > 0.001) {
        fireSrc = smokeSource(texCoord, smokeReach, time, smokeSpeed);
    }

    if (md < 0.01 && fireSrc < 0.004) {
        fragColor = scene;
        return;
    }

    // silhouette coverage (0 = scene, 1 = full glass).
    // tiny radius -> 1px anti-alias only, so corners stay corners
    float cov = edgeSmoothPx <= 0.75
              ? softMask(texCoord, 1.0)
              : edgeCoverage(texCoord, edgeSmoothPx);
    float coverage = smoothstep(0.45, 0.55, cov);

    // ---------------- refractive glass (lens) ----------------
    // gradient of the smoothed coverage -> the surface normal of the glass.
    // large near the rim (strong bend), ~0 in the centre (clear glass).
    // skipped entirely without refraction — that is 4 wide taps per pixel saved
    vec2 grad = vec2(0.0);
    if (refractStrength > 0.001) {
        vec2 px = vec2(gradPx) / resolution.xy;
        float sx = edgeCoverage(texCoord + vec2(px.x, 0.0), gradPx)
                 - edgeCoverage(texCoord - vec2(px.x, 0.0), gradPx);
        float sy = edgeCoverage(texCoord + vec2(0.0, px.y), gradPx)
                 - edgeCoverage(texCoord - vec2(0.0, px.y), gradPx);
        grad = vec2(sx, sy);
    }
    vec2 disp = grad * refractStrength * 0.06;

    vec2 blurUV = texCoord;
    if (doReflect > 0.5) {
        vec2 center = vec2(0.5, 0.5);
        vec2 offset = texCoord - center;
        blurUV = center - offset * 0.3 + offset;
    }
    blurUV += disp;

    vec4 blur = texture(BlurSampler, blurUV);
    vec3 glassColor = blur.rgb;
    glassColor = adjustSaturation(glassColor, saturation);

    if (tintIntensity > 0.001) {
        glassColor = mix(glassColor, tintColor.rgb, tintIntensity);
    }

    // soft glassy highlight along the refracting rim
    float rim = smoothstep(0.0, 1.0, length(grad) * refractStrength * 1.5);
    glassColor += rim * 0.12 * vec3(0.85, 0.92, 1.0);
    glassColor = clamp(glassColor, vec3(0.0), vec3(1.0));

    // feathered blend background <-> glass: smooth rounded edge, no jaggies
    fragColor = vec4(mix(scene.rgb, glassColor, coverage), 1.0);

    // ---------------- fire mist (rising smoke) ----------------
    if (smokeAmount > 0.001) {
        // mist also licks over the item itself, not only above it
        float src = max(fireSrc, maskValue * 0.55);
        if (src > 0.004) {
            float aspect = resolution.x / resolution.y;
            vec2 nuv = vec2(texCoord.x * aspect, texCoord.y);
            float t1 = time * smokeSpeed;

            // two fbm layers scrolling up at different speeds = live, curling wisps
            float n1 = fbm(nuv * smokeScale + vec2(0.0, -t1 * 1.4));
            float n2 = fbm(nuv * smokeScale * 2.1 + vec2(4.7, -t1 * 2.4));
            float wisp = clamp(n1 * 0.62 + n2 * 0.55, 0.0, 1.0);
            wisp = smoothstep(0.28, 0.88, wisp);

            float mist = smokeAmount * src * wisp;

            // hot core near the silhouette, cooler tinted haze further away
            vec3 haze = mix(vec3(0.80, 0.88, 1.00), tintColor.rgb, 0.65);
            vec3 core = mix(vec3(1.0), tintColor.rgb, 0.35);
            vec3 mistCol = mix(haze, core, clamp(src * src, 0.0, 1.0));

            fragColor.rgb = clamp(fragColor.rgb + mistCol * mist, 0.0, 1.0);
        }
    }
}
