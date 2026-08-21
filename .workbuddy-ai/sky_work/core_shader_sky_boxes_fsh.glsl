#version 330

in vec3 skyDir;

out vec4 fragColor;

layout(std140) uniform ShaderSkyData {
    vec4 resolutionTime;
    vec4 settings;
};

mat2 rot(float a) {
    return mat2(cos(a), -sin(a), sin(a), cos(a));
}

float rbox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

const vec3 CELL = vec3(2.8, 2.0, 2.0);

vec3 getCell(vec3 p) {
    return floor(p / CELL);
}

vec3 getCellCoord(vec3 p) {
    return mod(p, CELL) - CELL * 0.5;
}

float mapScene(vec3 p) {
    vec3 id = getCell(p);
    p = getCellCoord(p);
    float rnd = hash13(id * 663.0) - 1.0;
    p.xz *= rot(rnd * 0.05);
    return rbox(p, vec3(0.33, 0.90, 0.90), 0.0);
}

vec3 normal(vec3 pos) {
    vec2 e = vec2(0.002, -0.002);
    return normalize(
        e.xyy * mapScene(pos + e.xyy) +
        e.yyx * mapScene(pos + e.yyx) +
        e.yxy * mapScene(pos + e.yxy) +
        e.xxx * mapScene(pos + e.xxx)
    );
}

vec3 shadeColor(vec3 ro, vec3 rd, vec3 n, float t) {
    vec3 p = ro + rd * t;
    vec3 lp = ro + vec3(0.0, 0.0, 1.7);
    vec3 ld = normalize(lp - p);
    float dd = length(p - lp);
    float dif = max(dot(n, ld), 0.1);
    float fal = 1.5 / dd;
    float spec = pow(max(dot(reflect(-ld, n), -rd), 0.0), 22.0);
    vec3 id = getCell(p);

    bool l1 = false;
    if (mod(id.y, 2.0) == 0.0) l1 = true;
    if (mod(id.z, 2.0) == 0.0) l1 = !l1;
    if (l1) return vec3(0.05, 0.05, 0.2);

    vec3 objCol = hash33(id);
    objCol *= dif + 0.2;
    objCol += spec * 0.6;
    objCol *= fal;
    return objCol;
}

void main() {
    float time = resolutionTime.z * settings.y * 0.77;
    vec3 rd = normalize(skyDir);
    vec3 ro = vec3(0.0, 7.0, 0.1);
    rd.xy *= rot(-time * 0.05 + 0.5);
    ro.zy += time * 2.0;
    ro.x += cos(time) * 0.05;

    int hits = 0;
    float d = 0.0;
    float t = 0.0;
    vec3 col = vec3(0.0);
    int maxSteps = int(mix(48.0, 96.0, settings.z) + 0.5);
    int maxHits = int(mix(2.0, 4.0, settings.z) + 0.5);

    for (int i = 0; i < 96; i++) {
        if (i >= maxSteps) break;
        d = mapScene(ro + rd * t);
        if (hits >= maxHits || t >= 26.0) break;

        if (abs(d) < 0.001) {
            vec3 p = ro + rd * t;
            vec3 n = normal(p);
            if (d > 0.0 && hits <= 0) {
                rd = refract(rd, n, 1.0 / 1.053);
            }
            col += shadeColor(ro, rd, n, t) * 1.11 * (n * 0.5 + 0.5);
            hits++;
            t += 0.1;
        }

        t += abs(d) * 0.78;
    }

    vec3 fogCol = vec3(0.0, 0.0022, 0.07);
    float fog = 0.00033;
    col = mix(col, fogCol, 1.0 - 1.0 / (1.0 + t * t * t * fog));
    col = 1.0 - (0.15 / (1.12 - exp(-col)));
    fragColor = vec4(col * settings.x, 1.0);
}
