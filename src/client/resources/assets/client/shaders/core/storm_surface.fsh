#version 150

uniform sampler2D DepthTex;
uniform sampler2D SceneTex;

layout(std140) uniform StormData {
    mat4 uInvView;
    mat4 uInvProj;
    mat4 uView;
    mat4 uProj;

    vec4 uCameraPos;
    vec4 uViewport;
    vec4 uWetParams;
    vec4 uStormState;
    vec4 uPlayerPos;
};

in vec2 vTexCoord;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float puddleNoise(vec2 p) {
    float v = valueNoise(p) * 0.56;
    v += valueNoise(p * 2.03 + vec2(17.7, 4.3)) * 0.29;
    v += valueNoise(p * 4.11 + vec2(-8.1, 13.2)) * 0.15;
    return v;
}

vec3 getViewPosition(vec2 uv, float d) {
    vec4 clip = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 viewH = uInvProj * clip;
    float invW = abs(viewH.w) > 0.000001 ? 1.0 / viewH.w : 1.0;
    return viewH.xyz * invW;
}

vec3 getViewNormal(vec2 uv, vec3 centerPos) {
    vec2 texel = 1.0 / max(uViewport.xy, vec2(1.0));

    float dL = texture(DepthTex, clamp(uv - vec2(texel.x, 0.0), 0.001, 0.999)).r;
    float dR = texture(DepthTex, clamp(uv + vec2(texel.x, 0.0), 0.001, 0.999)).r;
    float dD = texture(DepthTex, clamp(uv - vec2(0.0, texel.y), 0.001, 0.999)).r;
    float dU = texture(DepthTex, clamp(uv + vec2(0.0, texel.y), 0.001, 0.999)).r;

    vec3 pL = getViewPosition(uv - vec2(texel.x, 0.0), min(dL, 0.99999));
    vec3 pR = getViewPosition(uv + vec2(texel.x, 0.0), min(dR, 0.99999));
    vec3 pD = getViewPosition(uv - vec2(0.0, texel.y), min(dD, 0.99999));
    vec3 pU = getViewPosition(uv + vec2(0.0, texel.y), min(dU, 0.99999));

    vec3 rightA = pR - centerPos;
    vec3 rightB = centerPos - pL;
    vec3 upA = pU - centerPos;
    vec3 upB = centerPos - pD;

    vec3 tanX = dot(rightA, rightA) < dot(rightB, rightB) ? rightA : rightB;
    vec3 tanY = dot(upA, upA) < dot(upB, upB) ? upA : upB;

    vec3 N = cross(tanX, tanY);
    float len = length(N);
    if (len <= 0.000001) return vec3(0.0, 0.0, 1.0);
    N /= len;
    if (dot(N, -centerPos) < 0.0) N = -N;
    return N;
}

vec2 projectFallback(vec3 pos, vec3 dir, out float valid) {
    float dist = max(3.5, length(pos) * 0.85);
    vec3 targetPos = pos + dir * dist;
    vec4 clip = uProj * vec4(targetPos, 1.0);
    if (clip.w <= 0.0001) {
        valid = 0.0;
        return vTexCoord;
    }
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    float edgeX = smoothstep(0.0, 0.12, min(uv.x, 1.0 - uv.x));
    float edgeY = smoothstep(0.0, 0.18, min(uv.y, 1.0 - uv.y));
    valid = edgeX * edgeY;
    return clamp(uv, 0.001, 0.999);
}

vec2 traceSSR(vec3 origin, vec3 dir, vec3 surfN, out float hitConfidence) {
    vec3 rayPos = origin + surfN * max(0.025, length(origin) * 0.0015);
    float stepDist = max(0.08, length(origin) * 0.008);
    float stepInc = max(0.012, stepDist * 0.06);
    float traveled = 0.0;
    hitConfidence = 0.0;
    vec2 bestUv = vTexCoord;
    int maxSteps = int(clamp(uStormState.z + 8.0, 16.0, 24.0));

    for (int i = 0; i < 24; i++) {
        if (i >= maxSteps) break;
        rayPos += dir * stepDist;
        traveled += stepDist;
        stepDist += stepInc;

        if (traveled > min(40.0, uViewport.w * 0.85) || rayPos.z > -0.02) break;

        vec4 clip = uProj * vec4(rayPos, 1.0);
        if (clip.w <= 0.0001) break;

        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < -0.02 || uv.x > 1.02 || uv.y < -0.02 || uv.y > 1.05) break;

        vec2 sampleUv = clamp(uv, 0.001, 0.999);
        bestUv = sampleUv;

        float sampleD = texture(DepthTex, sampleUv).r;
        if (sampleD < 0.99999) {
            vec3 geomPos = getViewPosition(sampleUv, sampleD);
            float dz = geomPos.z - rayPos.z;
            float h = dot(geomPos - origin, surfN);
            float thickness = 0.08 + stepDist * 1.5;

            if (h > 0.035 && dz >= 0.0 && dz < thickness) {
                vec3 rA = rayPos - dir * (stepDist - stepInc);
                vec3 rB = rayPos;
                for (int j = 0; j < 3; j++) {
                    vec3 rMid = (rA + rB) * 0.5;
                    vec4 cMid = uProj * vec4(rMid, 1.0);
                    if (cMid.w > 0.0001) {
                        vec2 uvMid = clamp(cMid.xy / cMid.w * 0.5 + 0.5, 0.001, 0.999);
                        float dMid = texture(DepthTex, uvMid).r;
                        vec3 gMid = getViewPosition(uvMid, dMid);
                        if (gMid.z - rMid.z >= 0.0) {
                            rB = rMid;
                        } else {
                            rA = rMid;
                        }
                    }
                }
                vec4 cFinal = uProj * vec4(rB, 1.0);
                vec2 hitUv = clamp(cFinal.xy / cFinal.w * 0.5 + 0.5, 0.001, 0.999);

                float edgeX = smoothstep(0.0, 0.06, min(hitUv.x, 1.0 - hitUv.x));
                float edgeY = smoothstep(0.0, 0.08, min(hitUv.y, 1.0 - hitUv.y));
                float depthFade = 1.0 - smoothstep(0.0, thickness, dz) * 0.35;
                hitConfidence = clamp(edgeX * edgeY * depthFade, 0.0, 1.0);
                return hitUv;
            }
        }
    }
    return bestUv;
}

vec3 sampleBlurred(vec2 uv, float blurPixels) {
    vec2 texel = 1.0 / max(uViewport.xy, vec2(1.0));
    vec2 horiz = vec2(texel.x * blurPixels, 0.0);
    vec2 vert = vec2(0.0, texel.y * blurPixels * 1.85);

    vec3 col = texture(SceneTex, clamp(uv, 0.001, 0.999)).rgb * 0.34;
    col += texture(SceneTex, clamp(uv + horiz, 0.001, 0.999)).rgb * 0.12;
    col += texture(SceneTex, clamp(uv - horiz, 0.001, 0.999)).rgb * 0.12;
    col += texture(SceneTex, clamp(uv + vert, 0.001, 0.999)).rgb * 0.16;
    col += texture(SceneTex, clamp(uv - vert, 0.001, 0.999)).rgb * 0.16;
    col += texture(SceneTex, clamp(uv + vert * 2.15, 0.001, 0.999)).rgb * 0.05;
    col += texture(SceneTex, clamp(uv - vert * 2.15, 0.001, 0.999)).rgb * 0.05;
    return col;
}

void main() {
    vec4 baseColor = texture(SceneTex, vTexCoord);
    float depth = texture(DepthTex, vTexCoord).r;
    if (depth >= 0.99999) {
        fragColor = baseColor;
        return;
    }

    vec3 viewPos = getViewPosition(vTexCoord, depth);
    float dist = length(viewPos);
    if (dist > uViewport.w || dist < 0.20) {
        fragColor = baseColor;
        return;
    }

    vec3 geomN = getViewNormal(vTexCoord, viewPos);
    vec3 worldN = normalize(mat3(uInvView) * geomN);

    float up = smoothstep(0.58, 0.88, worldN.y);
    if (up <= 0.002) {
        fragColor = baseColor;
        return;
    }

    vec3 worldPos = (uInvView * vec4(viewPos, 1.0)).xyz;

    float macroNoise = puddleNoise(worldPos.xz * 0.067);
    float detailNoise = valueNoise(worldPos.xz * 0.285 + vec2(19.4, -7.8));
    float puddleField = macroNoise * 0.78 + detailNoise * 0.22;
    float threshold = mix(0.73, 0.27, clamp(uWetParams.y, 0.0, 1.0));
    float puddle = smoothstep(threshold - 0.095, threshold + 0.075, puddleField);

    float rangeFade = 1.0 - smoothstep(uViewport.w * 0.72, uViewport.w, dist);
    float nearFade = smoothstep(0.25, 0.75, dist);
    float wetFilm = 0.44 + puddle * 0.56;
    float wetMask = clamp(up * rangeFade * nearFade * wetFilm * uWetParams.x, 0.0, 1.0);
    if (wetMask <= 0.002) {
        fragColor = baseColor;
        return;
    }

    vec3 incident = normalize(viewPos);
    vec3 reflNormal = geomN;
    vec3 reflectDir = normalize(reflect(incident, reflNormal));

    float fbValid = 0.0;
    vec2 fbUv = projectFallback(viewPos, reflectDir, fbValid);

    float rayHit = 0.0;
    vec2 rayUv = traceSSR(viewPos, reflectDir, reflNormal, rayHit);

    vec2 reflUv = mix(fbUv, rayUv, clamp(rayHit * 1.5, 0.0, 1.0));
    float reflValid = clamp(max(rayHit, fbValid * 0.70), 0.0, 1.0);

    float border = min(min(reflUv.x, reflUv.y), min(1.0 - reflUv.x, 1.0 - reflUv.y));
    reflValid *= smoothstep(0.0, 0.04, border);

    float roughness = mix(3.4, 1.35, puddle);
    vec3 reflectedScene = sampleBlurred(reflUv, roughness);
    reflectedScene = mix(reflectedScene, reflectedScene * vec3(0.93, 0.98, 1.08), 0.34);

    float darkAmount = clamp(wetMask * (0.18 + puddle * 0.14), 0.0, 0.75);
    vec3 wetBase = baseColor.rgb * (1.0 - darkAmount);
    float wetLuma = dot(wetBase, vec3(0.2126, 0.7152, 0.0722));
    wetBase = mix(vec3(wetLuma), wetBase, 1.10);
    wetBase *= vec3(0.96, 0.985, 1.035);

    vec3 viewToCam = normalize(-viewPos);
    float noV = clamp(dot(reflNormal, viewToCam), 0.0, 1.0);
    float fresnel = pow(clamp(1.0 - noV, 0.0, 1.0), 2.2);

    vec3 ambientSky = mix(wetBase * 1.15, vec3(0.58, 0.70, 0.85) * (wetLuma + 0.25), clamp(reflectDir.y * 0.5 + 0.5, 0.0, 1.0));
    vec3 finalReflection = mix(ambientSky, reflectedScene, reflValid);

    float reflectivePuddle = smoothstep(0.30, 0.72, puddle);
    float reflAmount = wetMask
            * reflectivePuddle
            * (0.24 + fresnel * 0.24)
            * clamp(uWetParams.z / 1.15, 0.5, 1.0);
    reflAmount = clamp(reflAmount, 0.0, 0.44);

    vec3 color = mix(baseColor.rgb, wetBase, clamp(wetMask * 0.90, 0.0, 1.0));
    color = mix(color, finalReflection, reflAmount);

    float reflLight = smoothstep(0.58, 1.0, dot(finalReflection, vec3(0.2126, 0.7152, 0.0722)));
    color += finalReflection * reflLight * wetMask * (0.02 + fresnel * 0.06);
    color += vec3(0.035, 0.052, 0.075) * (0.16 + fresnel * 0.84) * wetMask * (0.25 + puddle * 0.75);

    if (uStormState.x > 0.01) {
        color += vec3(0.85, 0.92, 1.0) * (uStormState.x * 0.35 * wetMask * (0.2 + reflectivePuddle * 0.8));
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), baseColor.a);
}
