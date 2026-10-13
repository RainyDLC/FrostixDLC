#version 150

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

const int ITERATIONS = 200;
const float FAR = 16.0;
const float STEP = FAR / float(ITERATIONS);
const float ENTRY_R = 8.0;
const float CAM_DIST = 14.0;
const float INCL = 0.10;
const float EXPOSURE = 0.024;
const float THEME_MIX = 0.5;
const float ZOOM = 2.4;
const float SPIN = 0.16;
const float TAU = 6.28318531;

const float R_FINE = 2.5;
const float Y_BAND = 0.30;
const float STEP_MAX = 0.45;

const float DISC_OUTER = 7.0;
const float IMPACT_CULL = 7.25;

const float DISC_INNER = 0.55;
const float DISC_WIDTH = 5.3;
const float DISC_BAND = 0.29;
const float PCURVE_K = 10.349099641;
const float WARP_K = 5.0 / 200.0;
const float HAZE_K = 2.9 / 200.0;
const float COVER_K = 1200.0 / 200.0;
const float R_SCHW = 0.25;

float gThemeW;
float gTin;
float gSpin;
float gAct;
float gFlare;
vec2 gUV;

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

vec3 blackbody(float T) {
    float t = max(T, 100.0) / 100.0;
    vec3 c;
    if (t <= 66.0) {
        c.r = 1.0;
        c.g = clamp(0.390081579 * log(t) - 0.631841444, 0.0, 1.0);
        c.b = t <= 19.0 ? 0.0 : clamp(0.543206789 * log(t - 10.0) - 1.196254089, 0.0, 1.0);
    } else {
        c.r = clamp(1.292936186 * pow(t - 60.0, -0.1332047592), 0.0, 1.0);
        c.g = clamp(1.129890861 * pow(t - 60.0, -0.0755148492), 0.0, 1.0);
        c.b = 1.0;
    }
    return c * c;
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
    vec2 rg = textureLod(NoiseTex, (uv + 0.5) / 256.0, 0.0).yx;
    return -1.0 + 2.0 * mix(rg.x, rg.y, f.z);
}

vec3 discCoord(vec2 cs, float rad, float freq) {
    return vec3(cs * freq, rad * freq);
}

float octave(vec2 cs, float rad, float freq, float aa) {
    float w = clamp(1.5 - aa * freq * 1.5, 0.0, 1.0);
    if (w <= 0.01) {
        return 0.5;
    }
    return mix(0.5, noise(discCoord(cs, rad, freq)) * 0.5 + 0.5, w);
}

float pcurveDisc(float x) {
    float x2 = x * x;
    return PCURVE_K * x2 * x2 * pow(1.0 - x, 0.9);
}

vec3 encodeHDR(vec3 c) {
    c = max(c, vec3(0.0));
    return pow(c / (1.0 + c), vec3(1.0 / 2.2));
}

vec3 skyTint(float axis, float t, float time) {
    float mode = skyColor.w;
    if (mode > 1.5) {
        float hue = fract(axis * 0.159155 + time * 0.02 + t * 0.15);
        return hsv2rgb(vec3(hue, 0.85, 1.0));
    }
    vec3 c1 = skyColor.rgb;
    if (mode > 0.5) {
        vec3 c2 = skyColor2.rgb;
        float k = clamp(0.5 + 0.5 * sin(axis * 2.0 + time * 0.15) + (t - 0.5) * 0.7, 0.0, 1.0);
        return mix(c1, c2, k);
    }
    return c1;
}

void Haze(inout vec3 color, vec3 pos, float r2, float alpha, vec3 hazeColor, float weight) {
    if (r2 > 36.0 || r2 < 0.25) {
        return;
    }
    vec2 q = vec2(length(pos.xz) - 1.0, pos.y - 0.05);
    float torusDist = abs(length(q) - 0.01);
    float bloomDisc = 1.0 / (torusDist * torusDist + 0.001);
    color += hazeColor * (bloomDisc * HAZE_K * weight * (1.0 - alpha));
}

void GasDisc(inout vec3 color, inout float alpha, vec3 pos, float distFromCenter, float time, vec3 mainColor,
             float aa, vec3 eyevec) {
    float distFromDisc = pos.y;
    if (distFromCenter > DISC_OUTER || abs(distFromDisc) > DISC_BAND) {
        return;
    }
    float radialGradient = 1.0 - clamp((distFromCenter - DISC_INNER) / DISC_WIDTH * 0.5, 0.0, 1.0);
    float pc = pcurveDisc(radialGradient);
    if (pc <= 0.0) {
        return;
    }

    float discThickness = 0.1 * radialGradient;
    float coverage = pc * clamp(1.0 - abs(distFromDisc) / max(discThickness, 1e-5), 0.0, 1.0);
    coverage = clamp(coverage * 0.7, 0.0, 1.0);

    float fb = abs(distFromCenter - DISC_INNER) + 0.4;
    fb *= fb;
    float fade = fb * fb * 0.04;
    float bloomFactor = 1.0 / (distFromDisc * distFromDisc * 40.0 + fade + 0.00002);
    float mixW = clamp(1.0 - coverage, 0.0, 1.0);
    coverage = clamp(coverage + bloomFactor * bloomFactor * 0.1, 0.0, 1.0);
    if (coverage < 0.01) {
        return;
    }

    vec3 tangent = normalize(vec3(-pos.z, 0.0, pos.x));
    float dx = clamp(1.0 - 0.55 * dot(tangent, eyevec), 0.45, 1.9);
    float dop = 1.0 / (dx * dx * sqrt(dx));
    float og = 1.0 - radialGradient;
    float dustGlow = 1.0 / (og * og * 290.0 + 0.002);
    float bloomK = bloomFactor * sqrt(bloomFactor);

    float ang = atan(-pos.x, -pos.z);
    float rad = (distFromCenter * 1.5 + 0.55 + distFromDisc * 1.5) * 0.95 + time * 0.012;

    float om = 3.2 / max(distFromCenter, 0.75);
    float omega = SPIN * gSpin * om * sqrt(om);
    float angA = ang + mod(time * omega, TAU);
    float angB = ang + mod(time * omega * 0.45, TAU);
    vec2 csA = vec2(cos(angA), sin(angA)) * 1.425;
    vec2 csB = vec2(cos(angB), sin(angB)) * 1.425;

    float bandAng = ang + mod(time * omega * 0.5, TAU);
    vec2 csBand = vec2(cos(bandAng), sin(bandAng)) * 1.425;
    float band = noise(discCoord(csBand, rad, 1.35)) * 0.5 + 0.5;
    float grain = noise(discCoord(csBand, rad + 70.0, 3.46)) * 0.5 + 0.5;

    vec3 litT = vec3(0.0);
    vec3 radT = vec3(1.0);
    vec3 texT = vec3(0.0);
    if (gThemeW > 0.001) {
        vec3 dopTint = mix(vec3(1.30, 0.72, 0.45), vec3(0.80, 0.92, 1.45), clamp((dop - 0.7) * 0.9, 0.0, 1.0));
        litT = mainColor * dopTint;
        radT = mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(radialGradient * radialGradient))
             * mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(sqrt(radialGradient)));
        vec3 tc = mix(vec3(0.95, 0.55, 0.26), vec3(0.42, 0.60, 1.0), band) * (0.45 + 0.80 * grain);
        texT = tc * tc * 4.0;
    }
    vec3 litR = vec3(0.0);
    vec3 texR = vec3(0.0);
    if (gThemeW < 0.999) {
        float rr = max(distFromCenter, DISC_INNER);
        float g = 1.0 / dx;
        float grav = sqrt(max(1.0 - R_SCHW / rr, 0.05));
        float T = gTin * pow(DISC_INNER / rr, 0.75) * (0.85 + 0.30 * band) * g * grav;
        litR = blackbody(T);
        float tg = 0.45 + 0.80 * grain;
        texR = vec3(tg * tg * 3.0);
    }
    vec3 lit = mix(litR, litT, gThemeW);
    vec3 radTint = mix(vec3(1.25), radT, gThemeW);
    vec3 texCol = mix(texR, texT, gThemeW);

    vec3 dustColor = lit * (dop * dustGlow * 8.2);
    vec3 b = lit * (dop * bloomK) * radTint;
    dustColor = mix(dustColor, b * 150.0, mixW);

    float n1 = 1.0;
    n1 *= octave(csA, rad, 3.0, aa);
    n1 *= octave(csB, rad, 6.0, aa);
    n1 *= octave(csA, rad, 12.0, aa);
    n1 *= octave(csB, rad, 24.0, aa);

    float n2 = 2.0;
    float rad2 = rad + 30.0;
    n2 *= octave(csB, rad2, 3.0, aa);
    n2 *= octave(csA, rad2, 6.0, aa);
    n2 *= octave(csB, rad2, 12.0, aa);
    n2 *= octave(csA, rad2, 24.0, aa);
    n2 *= octave(csB, rad2, 48.0, aa);
    n2 *= octave(csA, rad2, 92.0, aa);

    dustColor *= n1 * 0.998 + 0.002;
    coverage *= n2;

    dustColor *= texCol;

    float arm = 0.5 + 0.5 * cos(2.0 * ang + 2.6 * log(max(distFromCenter, 0.3)) + time * SPIN * gSpin * 4.5);
    dustColor *= 0.40 + 1.05 * arm * arm;
    coverage *= 0.78 + 0.22 * arm;

    float dAngA = abs(mod(angA - 1.35, TAU) - 3.14159265);
    float dRadA = distFromCenter - 1.5;
    float dAngB = abs(mod(angB - 4.2, TAU) - 3.14159265);
    float dRadB = distFromCenter - 2.7;
    float actK = 0.25 + 0.75 * gAct;
    float hot = exp(-(dAngA * dAngA * 0.5 + dRadA * dRadA * 7.0)) * 3.4 * gFlare * actK
              + exp(-(dAngB * dAngB * 0.9 + dRadB * dRadB * 5.0)) * 1.8 * actK;
    dustColor *= 1.0 + hot;

    coverage = clamp(coverage * COVER_K, 0.0, 1.0);
    dustColor = max(vec3(0.0), dustColor);

    coverage *= pc;

    color = (1.0 - alpha) * dustColor * coverage + color;
    alpha = (1.0 - alpha) * coverage + alpha;
}

vec3 rayDir(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    // Camera uses reversed Z in 26.2; a finite interior point works on both backends.
    vec4 point = invViewProj * vec4(ndc, 0.5, 1.0);
    return normalize(point.xyz / point.w);
}

vec3 toLocal(vec3 dir, vec3 eX, vec3 eY, vec3 eZ) {
    vec3 local = vec3(dot(dir, eX), dot(dir, eY), dot(dir, eZ));
    local.xy /= ZOOM;
    return normalize(local);
}

void main() {
    vec2 fullRes = max(misc.yz, vec2(1.0));
    int phase = int(taa.x + 0.5) & 3;
    vec2 jit = phase == 0 ? vec2(0.0) : (phase == 1 ? vec2(1.0) : (phase == 2 ? vec2(1.0, 0.0) : vec2(0.0, 1.0)));
    gUV = (floor(gl_FragCoord.xy) * 2.0 + jit + 0.5) / fullRes;
    vec3 rd = rayDir(gUV);

    float time = misc.x;
    float brightness = misc.w;

    float palette = skyExtra.y;
    gThemeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
    gTin = mix(3200.0, 16000.0, clamp(skyExtra.z, 0.0, 1.0));
    gAct = max(bhParams.y, 0.0);
    gSpin = max(bhParams.z, 0.0);

    vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
    vec3 upRef = vec3(0.0, 1.0, 0.0);
    vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
    vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
    vec3 eX = normalize(cross(eY, bhDir));
    vec3 eZ = cross(eX, eY);

    vec3 tint = skyTint(atan(rd.z, rd.x), 0.5, time);
    vec3 mainColor = mix(vec3(1.0), tint, THEME_MIX);

    vec3 hazeColor = mainColor;
    vec3 ringColor = mix(vec3(1.0), mainColor, 0.35);
    if (gThemeW < 0.999) {
        hazeColor = mix(blackbody(gTin * 0.55) * 1.1, mainColor, gThemeW);
        ringColor = mix(mix(vec3(1.0), blackbody(gTin * 0.8), 0.7), ringColor, gThemeW);
    }

    float fl = 0.5 + 0.5 * sin(time * 0.23);
    fl *= fl; fl *= fl; fl *= fl; fl *= fl;
    gFlare = 1.0 + 1.6 * fl * gAct;

    vec3 camWorld = -bhDir * CAM_DIST;
    vec3 pos = vec3(dot(camWorld, eX), dot(camWorld, eY), dot(camWorld, eZ));
    vec3 eyevec = toLocal(rd, eX, eY, eZ);
    vec3 localRd = eyevec;
    float pixAngle = length(toLocal(rayDir(gUV + vec2(2.0, 0.0) / fullRes), eX, eY, eZ) - localRd);
    float aa = pixAngle * CAM_DIST * 1.425;

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    float captured = 0.0;

    vec3 eye0 = eyevec;
    float fell = 0.0;
    float rmin = 1e3;
    float impact = length(cross(pos, eyevec));
    float along = dot(pos, eyevec);

    if (impact < IMPACT_CULL && along < 0.0) {
        float tEnter = -along - sqrt(max(0.0, ENTRY_R * ENTRY_R - impact * impact));
        float dither = fract(hash21(gl_FragCoord.xy) + taa.w);
        vec3 raypos = pos + eyevec * (tEnter + dither * STEP);
        float r2 = dot(raypos, raypos);
        float r = sqrt(r2);
        float bandEdge = Y_BAND + dither * STEP;
        float shellEdge = DISC_OUTER + dither * STEP;
        float traveled = 0.0;

        for (int i = 0; i < ITERATIONS; i++) {
            float sd = r;

            float s = STEP;
            if (sd > R_FINE) {
                float ay = abs(raypos.y);
                float sy = ay > bandEdge ? 0.9 * (ay - bandEdge) / max(abs(eyevec.y), 0.25) : 0.0;
                sy = max(sy, sd - shellEdge);
                s = clamp(min(sd - R_FINE, sy), STEP, STEP_MAX);
            }
            s = min(s, FAR - traveled);
            float w = s / STEP;

            eyevec = normalize(eyevec - raypos * (WARP_K * w / ((r2 + 0.000001) * max(sd, 1e-4))));
            raypos += eyevec * s;
            traveled += s;

            r2 = dot(raypos, raypos);
            r = sqrt(r2);
            GasDisc(color, alpha, raypos, r, time, mainColor, aa, eyevec);
            Haze(color, raypos, r2, alpha, hazeColor, w);

            captured = max(captured, 1.0 - smoothstep(0.32, 0.85, r));
            rmin = min(rmin, r);
            if (alpha > 0.995) {
                break;
            }
            if (r2 > 49.0 && dot(raypos, eyevec) > 0.0) {
                break;
            }
            if (r2 < 0.0625) {
                fell = 1.0;
                break;
            }
            if (traveled >= FAR - 1e-4) {
                break;
            }
        }
    }

    color *= EXPOSURE;

    float horizonWidth = max(aa * 0.5, 0.005);
    float fellSmooth = smoothstep(0.25 + horizonWidth, 0.25 - horizonWidth, rmin);
    fell = max(fell, fellSmooth);
    float esc = 1.0 - fell;

    float rq = clamp(captured * (1.0 - captured) * 4.0, 0.0, 1.0);
    float rq2 = rq * rq;
    float ring6 = rq2 * rq2 * rq2;
    float ringAng = atan(localRd.y, localRd.x);
    float ringSide = localRd.x / max(length(localRd.xy), 1e-4);
    float shimmer = 1.0 - 0.12 * gAct + 0.12 * gAct * sin(ringAng * 5.0 - time * 1.3) * sin(ringAng * 3.0 + time * 0.7);
    color += ringColor * (ring6 * 0.30) * (1.0 + 0.4 * ringSide) * shimmer * (1.0 - alpha);

    float bend = acos(clamp(dot(eye0, eyevec), -1.0, 1.0));
    float pr = clamp((bend - 0.9) / 1.35, 0.0, 1.0);
    float photon = pr * pr * pr * esc;
    float sub = pr * esc * 0.18;
    float beam = 1.0 + 0.85 * ringSide;
    float flick = 1.0 + 0.18 * gAct * sin(ringAng * 7.0 - time * 2.2) * sin(ringAng * 2.0 + time * 0.9);
    vec3 photonCol = mix(ringColor, vec3(1.0), 0.35);
    color += photonCol * (photon * 1.9 + sub) * beam * flick * (1.0 - alpha);

    color *= brightness;

    vec3 cur = encodeHDR(color);
    float shadowVal = fell * (1.0 - alpha);

    fragColor = vec4(max(cur, vec3(0.0)), shadowVal);
}
