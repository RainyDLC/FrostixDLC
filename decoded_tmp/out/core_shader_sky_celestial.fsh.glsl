#version 330

in vec3 skyDir;

out vec4 fragColor;

layout(std140) uniform ShaderSkyData {
    vec4 resolutionTime;
    vec4 settings;
};

mat2 m(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float map(vec3 p, float time, float wave) {
    p.xz *= m(time * 0.4);
    p.xy *= m(time * 0.1);
    vec3 q = p * 2.0 + time;
    float len = length(p);
    return length(p + vec3(wave)) * log(len + 1.0)
        + sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;
}

void main() {
    float time = resolutionTime.z * settings.y;
    float wave = sin(time * 0.7);
    int steps = int(mix(3.0, 5.0, settings.z) + 0.5);
    vec3 dir = normalize(skyDir);
    vec2 a = dir.xz / max(0.25, abs(dir.y) + 0.65);
    a = a * 0.85;
    vec3 ray = normalize(vec3(a, -1.0));
    vec3 cl = vec3(0.0);
    float d = 2.5;

    for (int i = 0; i < 5; i++) {
        if (i >= steps) break;
        vec3 p = vec3(0.0, 0.0, 4.0) + ray * d;
        float rz = map(p, time, wave);
        float f = clamp((rz - map(p + 0.1, time, wave)) * 0.5, -0.1, 1.0);
        vec3 l = vec3(0.1, 0.3, 0.4) + vec3(5.0, 2.5, 3.0) * f;
        cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l;
        d += min(rz, 1.0);
    }

    fragColor = vec4(cl * settings.x, 1.0);
}
