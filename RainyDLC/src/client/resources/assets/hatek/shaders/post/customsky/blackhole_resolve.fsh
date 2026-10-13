#version 150

uniform sampler2D Current;
uniform sampler2D History;

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

const float HISTORY_BLEND = 0.5;
const float CLAMP_RANGE = 0.35;

vec4 sampleHistory(vec2 uv, vec2 res) {
    vec2 samplePos = uv * res;
    vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - texPos1;

    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);

    vec2 w12 = w1 + w2;
    vec2 offset12 = w2 / w12;

    vec2 p0 = (texPos1 - 1.0) / res;
    vec2 p3 = (texPos1 + 2.0) / res;
    vec2 p12 = (texPos1 + offset12) / res;

    vec4 result = vec4(0.0);
    result += textureLod(History, vec2(p0.x, p0.y), 0.0) * (w0.x * w0.y);
    result += textureLod(History, vec2(p12.x, p0.y), 0.0) * (w12.x * w0.y);
    result += textureLod(History, vec2(p3.x, p0.y), 0.0) * (w3.x * w0.y);
    result += textureLod(History, vec2(p0.x, p12.y), 0.0) * (w0.x * w12.y);
    result += textureLod(History, vec2(p12.x, p12.y), 0.0) * (w12.x * w12.y);
    result += textureLod(History, vec2(p3.x, p12.y), 0.0) * (w3.x * w12.y);
    result += textureLod(History, vec2(p0.x, p3.y), 0.0) * (w0.x * w3.y);
    result += textureLod(History, vec2(p12.x, p3.y), 0.0) * (w12.x * w3.y);
    result += textureLod(History, vec2(p3.x, p3.y), 0.0) * (w3.x * w3.y);

    return clamp(result, vec4(0.0), vec4(1.0));
}

void main() {
    vec2 res = vec2(textureSize(History, 0));
    ivec2 ip = ivec2(gl_FragCoord.xy);
    int phase = int(taa.x + 0.5) & 3;
    ivec2 off = phase == 0 ? ivec2(0, 0) : (phase == 1 ? ivec2(1, 1) : (phase == 2 ? ivec2(1, 0) : ivec2(0, 1)));
    ivec2 csize = textureSize(Current, 0);
    vec4 cur = texelFetch(Current, clamp(ip / 2, ivec2(0), csize - 1), 0);
    bool fresh = (ip.x - (ip.x / 2) * 2) == off.x && (ip.y - (ip.y / 2) * 2) == off.y;

    bool haveHist = false;
    vec4 hist = vec4(0.0);
    if (taa.z > 0.5) {
        vec2 ndc = texCoord * 2.0 - 1.0;
        // Camera uses reversed Z in 26.2; a finite interior point works on both backends.
    vec4 point = invViewProj * vec4(ndc, 0.5, 1.0);
    vec3 rd = normalize(point.xyz / point.w);
        vec4 pc = prevViewProj * vec4(rd, 0.0);
        if (pc.w > 1e-5) {
            vec2 prevUV = pc.xy / pc.w * 0.5 + 0.5;
            vec2 guard = 1.5 / res;
            if (all(greaterThan(prevUV, guard)) && all(lessThan(prevUV, 1.0 - guard))) {
                hist = sampleHistory(prevUV, res);
                haveHist = true;
            }
        }
    }

    if (!haveHist) {
        fragColor = fresh ? cur : texture(Current, texCoord);
        return;
    }
    if (fresh) {
        vec3 h = clamp(hist.rgb, cur.rgb - CLAMP_RANGE, cur.rgb + CLAMP_RANGE);
        fragColor = vec4(mix(cur.rgb, h, HISTORY_BLEND), mix(cur.a, hist.a, HISTORY_BLEND));
    } else {
        fragColor = hist;
    }
}
