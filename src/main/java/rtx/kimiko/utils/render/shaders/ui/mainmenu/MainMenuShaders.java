package rtx.kimiko.utils.render.shaders.ui.mainmenu;

import java.util.Map;

/** Шейдер главного меню: трассировка лучей в метрике Шварцшильда + Млечный Путь с иконками. */
public final class MainMenuShaders {
    private MainMenuShaders() {
    }

    public static void register(Map<String, String> sources) {
        sources.put("ui/mainmenu/space.vsh", VERTEX);
        sources.put("ui/mainmenu/space.fsh", FRAGMENT);
    }

    public static final String VERTEX = """
#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in float LineWidth;

out vec2 vUv;
flat out vec4 vParams;
flat out vec2 vHover;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    vUv = Color.rg;
    // x: suck progress / galaxy angle, y: inside progress (-1 = outside), z: aspect, w: time
    vParams = vec4(Position.z, UV0.x, UV0.y, LineWidth);
    vHover = vec2(floor(Color.b * 255.0 + 0.5) - 1.0, Color.a);
}
""";

    public static final String FRAGMENT = """
#version 150

in vec2 vUv;
flat in vec4 vParams;
flat in vec2 vHover;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

// ------------------------------------------------------------ hashing / noise

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash32(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yzz) * p3.zyx);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float vnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise2(p);
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(3.1, 7.7);
        a *= 0.5;
    }
    return s;
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm3(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

mat2 rot2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

// Tanner Helland blackbody fit, T in Kelvin
vec3 blackbody(float T) {
    T = clamp(T, 1000.0, 40000.0) / 100.0;
    vec3 c;
    c.r = T <= 66.0 ? 1.0 : clamp(1.29293618606 * pow(T - 60.0, -0.1332047592), 0.0, 1.0);
    c.g = T <= 66.0 ? clamp(0.39008157876 * log(T) - 0.63184144378, 0.0, 1.0)
                    : clamp(1.12989086089 * pow(T - 60.0, -0.0755148492), 0.0, 1.0);
    c.b = T >= 66.0 ? 1.0 : (T <= 19.0 ? 0.0 : clamp(0.54320678911 * log(T - 10.0) - 1.19625408914, 0.0, 1.0));
    return c * c; // to linear
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// ------------------------------------------------------------ sky (stars + milky way on a sphere)

vec3 starLayer(vec3 d, float scale, float density, float px) {
    vec3 p = d * scale;
    vec3 base = floor(p - 0.5);
    vec3 col = vec3(0.0);
    for (int z = 0; z <= 1; z++)
    for (int y = 0; y <= 1; y++)
    for (int x = 0; x <= 1; x++) {
        vec3 id = base + vec3(float(x), float(y), float(z));
        vec3 h = hash33(id);
        if (h.x > density) continue;
        vec3 sp = normalize(id + 0.2 + 0.6 * hash33(id + 19.19));
        float dist = length(d - sp);
        float size = px * (0.55 + 0.8 * h.y * h.y);
        float b = pow(hash13(id + 7.7), 9.0) * 2.2 + 0.03;
        float core = exp(-dist * dist / (size * size));
        vec3 tint = blackbody(mix(2800.0, 14000.0, pow(h.z, 1.4)));
        col += tint * b * core;
    }
    return col;
}

vec3 milkyWaySky(vec3 d, float px) {
    vec3 n = normalize(vec3(0.35, 0.86, -0.37));
    vec3 coreDir = normalize(cross(n, vec3(0.0, 0.0, 1.0)));
    float lat = dot(d, n);
    float band = exp(-lat * lat / 0.022);
    float warp = (fbm3(d * 3.0) - 0.5) * 0.12;
    float latw = lat + warp;
    float bandW = exp(-latw * latw / 0.012);
    float clouds = fbm3(d * 5.0 + 2.0);
    float fine = fbm3(d * 16.0 + 7.0);
    float lane = exp(-(latw + 0.01) * (latw + 0.01) / 0.0018);
    float dust = lane * smoothstep(0.35, 0.7, fbm3(d * 9.0 + 11.0));
    float core = pow(max(dot(d, coreDir), 0.0), 5.0);

    vec3 col = vec3(0.45, 0.52, 0.75) * bandW * (0.25 + clouds * clouds * 1.3);
    col += vec3(1.0, 0.78, 0.52) * core * bandW * 1.4;
    col += vec3(0.95, 0.32, 0.45) * smoothstep(0.62, 0.82, fine) * bandW * 0.35;
    col *= 1.0 - dust * 0.9;
    col *= 0.22;
    col += vec3(0.006, 0.008, 0.014) * (0.6 + band);

    float starBoost = 1.0 + band * 2.5;
    col += starLayer(d, 70.0, 0.10, px) * 0.9;
    col += starLayer(d, 160.0, 0.035 * starBoost, px) * 0.45;
    col += starLayer(d, 380.0, 0.012 * starBoost, px) * 0.25;
    return col;
}

// ------------------------------------------------------------ accretion disk

const float R_IN = 2.6;
const float R_OUT = 13.0;

vec4 diskSample(vec3 p, vec3 rd, float t, float boost) {
    float r = length(p.xz);
    if (r < R_IN * 0.92 || r > R_OUT) return vec4(0.0);
    float phi = atan(p.z, p.x);
    float omega = sqrt(0.5 / (r * r * r)) * 2.2;

    // flow noise: two phases blended to avoid over-winding
    float T = 9.0;
    float ph0 = fract(t / T);
    float ph1 = fract(t / T + 0.5);
    float w0 = 1.0 - abs(ph0 * 2.0 - 1.0);
    float a0 = phi + omega * ph0 * T * (1.0 + boost * 6.0);
    float a1 = phi + omega * ph1 * T * (1.0 + boost * 6.0);
    float lr = log(r);
    vec2 c0 = vec2(cos(a0), sin(a0));
    vec2 c1 = vec2(cos(a1), sin(a1));
    float n0 = fbm2(vec2(lr * 9.0, 0.0) + c0 * 2.5 + vec2(0.0, lr * 3.0)) * 0.6 + fbm2(vec2(lr * 30.0, 5.0) + c0 * 6.0) * 0.4;
    float n1 = fbm2(vec2(lr * 9.0, 0.0) + c1 * 2.5 + vec2(0.0, lr * 3.0) + 13.7) * 0.6 + fbm2(vec2(lr * 30.0, 5.0) + c1 * 6.0 + 4.1) * 0.4;
    float n = mix(n1, n0, w0);

    float edgeIn = smoothstep(R_IN * 0.92, R_IN * 1.25, r);
    float edgeOut = 1.0 - smoothstep(R_OUT * 0.45, R_OUT, r);
    float rings = 0.88 + 0.12 * sin(lr * 42.0 + n * 9.0);
    float density = edgeIn * edgeOut * (0.25 + 1.5 * n * n) * rings;

    // Novikov-Thorne-ish temperature profile
    float x = max(1.0 - sqrt(R_IN / r), 0.0);
    float temp = 9000.0 * pow(R_IN / r, 0.75) * pow(x, 0.25) * 1.15;

    // relativistic Doppler + gravitational redshift
    vec3 vdir = normalize(vec3(-p.z, 0.0, p.x));
    float beta = min(sqrt(0.5 / max(r - 1.0, 0.2)), 0.8);
    float gamma = inversesqrt(1.0 - beta * beta);
    float cosT = dot(vdir, -rd);
    float g = 1.0 / (gamma * (1.0 - beta * cosT));
    g *= sqrt(max(1.0 - 1.0 / r, 0.0));

    float gc = mix(1.0, g, 0.55);
    vec3 emit = blackbody(temp * gc) * pow(gc, 3.0) * density * 2.6;
    float alpha = clamp(density * 0.9, 0.0, 0.97);
    return vec4(emit, alpha);
}

// ------------------------------------------------------------ black hole scene (outside)

vec3 renderBlackHole(vec2 q, float s, float t, float px, out vec2 holeScreen) {
    float fall = pow(s, 1.7);
    float D = mix(26.0, 0.35, fall);
    float az = 0.55 + t * 0.018 + fall * 3.2;
    float el = 0.085 + 0.05 * sin(t * 0.05) + s * 0.12;
    vec3 ro = D * vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az));

    // shake + roll while falling
    float shake = s * s * 0.012;
    vec2 jitter = vec2(sin(t * 61.0) + sin(t * 37.0), cos(t * 53.0) + sin(t * 71.0)) * 0.5 * shake;
    float roll = 0.06 + 0.03 * sin(t * 0.07) + pow(s, 2.5) * 2.4;
    float tanHalf = mix(0.40, 1.15, pow(s, 2.0));

    vec3 fw = normalize(-ro);
    vec3 rt = normalize(cross(fw, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(rt, fw);
    vec2 qq = rot2(roll) * (q + jitter - vec2(0.0, 0.075 * (1.0 - s)));
    holeScreen = rot2(-roll) * vec2(0.0) + vec2(0.0, 0.075 * (1.0 - s)) - jitter;
    vec3 rd = normalize(fw + (qq.x * rt + qq.y * up) * 2.0 * tanHalf);

    vec3 p = ro;
    vec3 v = rd;
    vec3 hv = cross(p, v);
    float h2 = dot(hv, hv);
    vec3 col = vec3(0.0);
    float trans = 1.0;
    bool captured = D < 1.0;
    float minR = 1e5;

    if (!captured) {
        for (int i = 0; i < 260; i++) {
            float r = length(p);
            float dt = clamp(0.085 * r, 0.012, 1.6);
            vec3 acc = -1.5 * h2 * p / (r * r * r * r * r);
            v += acc * dt;
            vec3 pn = p + v * dt;
            if (p.y * pn.y < 0.0) {
                float f = p.y / (p.y - pn.y);
                vec3 c = mix(p, pn, f);
                vec4 dsk = diskSample(c, normalize(v), t, s);
                col += trans * dsk.rgb * dsk.a;
                trans *= 1.0 - dsk.a;
                if (trans < 0.01) break;
            }
            p = pn;
            float rn = length(p);
            minR = min(minR, rn);
            if (rn < 1.0) { captured = true; break; }
            if (rn > 45.0 && dot(p, v) > 0.0) break;
        }
    }

    if (!captured) {
        col += trans * milkyWaySky(normalize(v), px * 2.0 * tanHalf);
    }
    // photon-sphere haze (cheap bloom)
    float ring = exp(-pow((minR - 1.5) * 3.2, 2.0));
    col += vec3(1.0, 0.72, 0.45) * ring * 0.06 * trans;

    // fade into the horizon
    col *= 1.0 - smoothstep(0.84, 0.97, s);
    return col;
}

// ------------------------------------------------------------ icons (SDF, crisp at any resolution)

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float sdPerson(vec2 p, float k) {
    float head = length(p - vec2(0.0, 0.2 * k)) - 0.17 * k;
    vec2 bp = p - vec2(0.0, -0.36 * k);
    float body = sdBox(bp, vec2(0.30, 0.2) * k, 0.2 * k);
    body = max(body, p.y + 0.36 * k - 0.16 * k - 0.0);
    body = max(body, -(p.y + 0.52 * k));
    return min(head, body);
}

float iconSdf(int id, vec2 p) {
    float w = 0.065;
    if (id == 0) { // одиночная игра: человек
        return sdPerson(p * 1.05, 1.0);
    }
    if (id == 1) { // мультиплеер: глобус
        float d = abs(length(p) - 0.42) - w * 0.5;
        d = min(d, abs(length(p / vec2(0.19, 0.42)) - 1.0) * 0.19 - w * 0.5);
        d = min(d, sdSeg(p, vec2(-0.42, 0.0), vec2(0.42, 0.0)) - w * 0.5);
        d = min(d, sdSeg(p, vec2(-0.36, 0.21), vec2(0.36, 0.21)) - w * 0.5);
        d = min(d, sdSeg(p, vec2(-0.36, -0.21), vec2(0.36, -0.21)) - w * 0.5);
        return max(d, length(p) - 0.42 - w * 0.5);
    }
    if (id == 2) { // аккаунты: два человека
        float back = sdPerson((p - vec2(0.17, 0.07)) * 1.25, 1.0) / 1.25;
        float front = sdPerson((p - vec2(-0.1, -0.05)) * 1.05, 1.0) / 1.05;
        float gapFront = front - 0.05;
        return min(front, max(back, -gapFront));
    }
    if (id == 3) { // настройки: шестерня
        float a = atan(p.y, p.x);
        float teeth = smoothstep(-0.25, 0.25, cos(a * 8.0));
        float d = length(p) - (0.3 + 0.1 * teeth);
        return max(d, -(length(p) - 0.13));
    }
    // выход: power
    float a = atan(p.x, p.y);
    float arc = abs(length(p) - 0.36) - w * 0.5;
    arc = max(arc, 0.62 - abs(a));
    float bar = sdSeg(p, vec2(0.0, 0.02), vec2(0.0, 0.46)) - w * 0.5;
    return min(arc, bar);
}

// ------------------------------------------------------------ galaxy scene (inside)

const float GAL_S = 0.80;
const float GAL_COSI = 0.46;
const float GAL_ROLL = -0.16;
const vec2 GAL_C = vec2(0.0, -0.03);
const float ICON_R = 0.72;

vec3 galaxy(vec2 g, float ang, float pxPlane) {
    float r = length(g);
    vec2 pg = rot2(-ang) * g;          // pattern coordinates (rotate with the galaxy)
    float th = atan(pg.y, pg.x);
    float lr = log(max(r, 0.015));

    float warp = (fbm2(pg * 3.0) - 0.5) * 0.9;
    float k = 3.6;
    float arm2 = pow(0.5 + 0.5 * cos(2.0 * (th - k * lr) + warp), 3.0);
    float arm4 = pow(0.5 + 0.5 * cos(4.0 * (th - k * lr) + warp * 1.4 + 1.3), 5.0);
    float arms = (arm2 * 0.85 + arm4 * 0.45) * smoothstep(0.1, 0.28, r);

    float disk = exp(-r / 0.26) * (1.0 - smoothstep(0.75, 1.1, r));
    float clump = fbm2(pg * 7.0 + 3.0);
    float fine = fbm2(pg * 22.0);

    vec2 bq = rot2(0.5) * pg;
    float bar = exp(-(bq.x * bq.x / 0.022 + bq.y * bq.y / 0.0028));
    float bulge = exp(-pow(r / 0.09, 1.3)) + bar * 0.6;

    float dustArm = pow(0.5 + 0.5 * cos(2.0 * (th - k * lr) + warp - 0.55), 8.0);
    float dust = dustArm * smoothstep(0.55, 0.8, fbm2(pg * 11.0 + 5.0) + 0.35) * smoothstep(0.08, 0.25, r) * (1.0 - smoothstep(0.8, 1.0, r));
    float hii = smoothstep(0.66, 0.86, fine) * arms * smoothstep(0.15, 0.3, r);

    vec3 col = vec3(0.0);
    col += vec3(1.0, 0.82, 0.58) * bulge * 2.4;
    col += vec3(0.95, 0.86, 0.76) * disk * 0.55;
    col += vec3(0.55, 0.70, 1.0) * arms * disk * (0.6 + 1.4 * clump * clump) * 2.2;
    col += vec3(1.0, 0.36, 0.55) * hii * disk * 3.5;
    col *= 1.0 - dust * 0.85;
    col += vec3(0.35, 0.45, 0.8) * exp(-r / 0.55) * 0.04;

    // resolved stars that rotate with the disk
    vec2 sg = pg * 140.0;
    vec2 cell = floor(sg);
    vec3 h = hash32(cell);
    float starDens = clamp((arms * 1.6 + disk * 2.0) * (1.0 - smoothstep(0.85, 1.15, r)), 0.0, 1.0);
    if (h.x < starDens * 0.1) {
        vec2 sp = cell + 0.2 + 0.6 * h.yz;
        float dd = length(sg - sp) / 140.0;
        float sz = max(pxPlane * 1.1, 0.0006);
        float b = pow(hash12(cell + 3.3), 7.0) * 1.8 + 0.03;
        col += blackbody(mix(3500.0, 15000.0, h.y)) * b * exp(-dd * dd / (sz * sz));
    }
    return col;
}

vec2 galToScreen(vec2 g, float scale) {
    return GAL_C + rot2(GAL_ROLL) * (vec2(g.x, g.y * GAL_COSI) * GAL_S * scale);
}

vec3 renderInside(vec2 q, float ang, float p1, float t, float aa, float aspect) {
    float e = 1.0 - pow(1.0 - clamp(p1, 0.0, 1.0), 3.0);
    float scale = mix(0.03, 1.0, e);

    // void background: sparse, very distant stars
    vec3 col = vec3(0.0);
    vec2 sg = q * 260.0;
    vec2 cell = floor(sg);
    vec3 h = hash32(cell + 91.0);
    if (h.x < 0.08) {
        vec2 sp = cell + 0.2 + 0.6 * h.yz;
        float dd = length(sg - sp) / 260.0;
        float sz = aa * 0.9;
        float tw = 0.75 + 0.25 * sin(t * (1.0 + h.y * 3.0) + h.z * 20.0);
        col += blackbody(mix(3500.0, 12000.0, h.z)) * pow(hash12(cell), 5.0) * 1.2 * tw * exp(-dd * dd / (sz * sz));
    }

    // galaxy plane
    vec2 v = rot2(-GAL_ROLL) * (q - GAL_C) / (GAL_S * scale);
    vec2 g = vec2(v.x, v.y / GAL_COSI);
    float pxPlane = aa / (GAL_S * scale);
    col += galaxy(g, ang, pxPlane) * smoothstep(0.0, 0.6, p1) * 0.9;

    // orbit ellipse the icons ride on
    float rg = length(g);
    float orbit = exp(-pow((rg - ICON_R) * GAL_S * scale * GAL_COSI / (aa * 1.2), 2.0));
    float iconsIn = smoothstep(0.55, 0.9, p1);
    col += vec3(0.55, 0.7, 1.0) * orbit * 0.07 * iconsIn;

    // passing-through flash
    float fr = length(q);
    float flash = exp(-p1 * 14.0) * exp(-fr * fr * 18.0) * 2.5;
    float shock = exp(-pow((fr - p1 * 2.2) * 14.0, 2.0)) * exp(-p1 * 5.0) * 0.22;
    col += vec3(0.75, 0.85, 1.0) * (flash + shock);

    // icons (drawn far-to-near)
    int hoverId = int(vHover.x + 0.5);
    if (vHover.x < -0.5) hoverId = -1;
    for (int k2 = 0; k2 < 5; k2++) {
        float a = ang + float(k2) * TAU / 5.0;
        vec2 gp = ICON_R * vec2(cos(a), sin(a));
        vec2 sp = galToScreen(gp, scale);
        float persp = 1.0 - 0.22 * sin(a);
        float hov = (k2 == hoverId) ? vHover.y : 0.0;
        float R = 0.052 * persp * (1.0 + 0.18 * hov) * iconsIn;
        if (R < 0.0005) continue;
        vec2 lp = (q - sp) / R;
        float d = length(lp);
        float aaL = aa / R;
        vec3 tint = mix(vec3(0.55, 0.72, 1.0), vec3(0.85, 0.7, 1.0), float(k2) / 4.0);

        // outer glow
        col += tint * exp(-max(d - 1.0, 0.0) * 2.6) * (0.05 + 0.22 * hov) * iconsIn * step(1.0, d);

        float cover = 1.0 - smoothstep(1.0 - aaL, 1.0 + aaL, d);
        if (cover > 0.0) {
            float nz = sqrt(max(1.0 - d * d, 0.0));
            vec3 nrm = vec3(lp, nz);
            float fres = pow(1.0 - nz, 2.5);
            vec3 L = normalize(vec3(-0.45, 0.6, 0.65));
            float spec = pow(max(dot(reflect(-L, nrm), vec3(0.0, 0.0, 1.0)), 0.0), 36.0);
            vec3 orb = col * 0.28 + vec3(0.012, 0.016, 0.03);
            orb += tint * fres * (0.55 + 1.1 * hov);
            orb += vec3(1.0) * spec * 0.55;
            orb += tint * 0.05 * (1.0 - d) * (1.0 + hov);

            float gd = iconSdf(k2, lp * 1.28);
            float glyph = 1.0 - smoothstep(-aaL * 1.28, aaL * 1.28, gd);
            vec3 gcol = mix(vec3(0.82, 0.88, 1.0), vec3(1.0), hov) * (1.05 + 0.9 * hov);
            orb = mix(orb, gcol, glyph);

            col = mix(col, orb, cover * iconsIn);
        }

        // hover ring
        float ringR = 1.28 + 0.06 * sin(t * 4.0);
        float ringD = abs(d - ringR) - 0.018;
        col += tint * hov * (1.0 - smoothstep(0.0, aaL * 1.5, ringD)) * 0.9;
    }

    // soft vignette of the void
    col *= 1.0 - 0.55 * smoothstep(0.35, 1.0, length(q / vec2(aspect * 0.55, 0.55)));
    col *= smoothstep(0.0, 0.18, p1);
    return col;
}

float sdPlay(vec2 p) {
    p.x += 0.05;
    vec2 p0 = vec2(-0.20, -0.30);
    vec2 p1 = vec2(0.28, 0.0);
    vec2 p2 = vec2(-0.20, 0.30);
    vec2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
    vec2 v0 = p - p0, v1 = p - p1, v2 = p - p2;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y) - 0.035;
}

// ------------------------------------------------------------ play button (outside)

vec3 playButton(vec3 col, vec2 q, float s, float aa, float t, float hov, vec2 holeScreen) {
    float k = smoothstep(0.0, 0.35, s);
    if (k >= 0.999) return col;
    vec2 c = mix(vec2(0.0, -0.32), holeScreen, k * k);
    float sc = 1.0 - k;
    if (sc <= 0.001) return col;
    vec2 p = (q - c) / sc;
    p = rot2(k * 2.5) * p;
    float aap = aa / sc;

    // Glass orb icon button (matching the Milky Way style)
    float R = 0.052 * (1.0 + 0.15 * hov);
    vec2 lp = p / R;
    float d = length(lp);
    float aaL = aap / R;
    vec3 tint = vec3(0.65, 0.80, 1.0);
    float fade = 1.0 - k;

    // Outer glow
    col += tint * exp(-max(d - 1.0, 0.0) * 2.6) * (0.06 + 0.28 * hov) * step(1.0, d) * fade;

    float cover = (1.0 - smoothstep(1.0 - aaL, 1.0 + aaL, d)) * fade;
    if (cover > 0.0) {
        float nz = sqrt(max(1.0 - d * d, 0.0));
        vec3 nrm = vec3(lp, nz);
        float fres = pow(1.0 - nz, 2.5);
        vec3 L = normalize(vec3(-0.45, 0.6, 0.65));
        float spec = pow(max(dot(reflect(-L, nrm), vec3(0.0, 0.0, 1.0)), 0.0), 36.0);
        vec3 orb = col * 0.28 + vec3(0.012, 0.016, 0.03);
        orb += tint * fres * (0.55 + 1.1 * hov);
        orb += vec3(1.0) * spec * 0.55;
        orb += tint * 0.05 * (1.0 - d) * (1.0 + hov);

        float gd = sdPlay(lp * 1.30);
        float glyph = 1.0 - smoothstep(-aaL * 1.30, aaL * 1.30, gd);
        vec3 gcol = mix(vec3(0.85, 0.90, 1.0), vec3(1.0), hov) * (1.1 + 0.9 * hov);
        orb = mix(orb, gcol, glyph);

        col = mix(col, orb, cover);
    }

    // Hover orbit ring
    float ringR = 1.28 + 0.05 * sin(t * 3.5);
    float ringD = abs(d - ringR) - 0.018;
    col += tint * hov * (1.0 - smoothstep(0.0, aaL * 1.5, ringD)) * 0.9 * fade;

    return col;
}

// ------------------------------------------------------------ main

void main() {
    float aspect = vParams.z;
    float t = vParams.w;
    vec2 q = (vUv - 0.5) * vec2(aspect, 1.0);
    float aa = fwidth(vUv.y);
    vec3 col;

    if (vParams.y < -0.5) {
        float s = clamp(vParams.x, 0.0, 1.0);
        vec2 holeScreen;
        col = renderBlackHole(q, s, t, aa, holeScreen);
        float hov = (vHover.x > 4.5) ? vHover.y : 0.0;
        col = playButton(col, q, s, aa, t, hov, holeScreen);
        col *= 1.0 - 0.45 * smoothstep(0.4, 1.1, length(q / vec2(aspect * 0.5, 0.5)));
    } else {
        col = renderInside(q, vParams.x, vParams.y, t, aa, aspect);
    }

    col = aces(col * 1.15);
    col = pow(col, vec3(1.0 / 2.2));
    col += (hash12(gl_FragCoord.xy + fract(t) * 91.7) - 0.5) / 255.0; // dither, no banding
    fragColor = vec4(col, 1.0);
}
""";
}
