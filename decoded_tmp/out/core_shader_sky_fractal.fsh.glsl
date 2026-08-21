#version 330

in vec3 skyDir;

out vec4 fragColor;

layout(std140) uniform ShaderSkyData {
    vec4 resolutionTime;
    vec4 settings;
};

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

void main() {
    float time = resolutionTime.z * settings.y;
    vec3 dir = normalize(skyDir);
    vec2 uv = dir.xz / max(0.25, abs(dir.y) + 0.65);
    vec3 ray = normalize(vec3(uv * 1.4, 1.0));

    vec3 from = vec3(0.0);
    from += vec3(1.25 * sin(time), -1.03 * time, -2.0);

    float s = 0.1;
    float fade = 0.07;
    vec3 v = vec3(0.4);
    int raySteps = int(mix(6.0, 10.0, settings.z) + 0.5);
    int foldSteps = int(mix(7.0, 11.0, settings.z) + 0.5);

    for (int rr = 0; rr < 10; rr++) {
        if (rr >= raySteps) break;
        vec3 p = from + s * ray * 1.5;
        p = abs(vec3(0.750) - mod(p, vec3(1.500)));
        p.x += float(rr * rr) * 0.01;
        p.y += float(rr) * 0.02;

        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < 11; i++) {
            if (i >= foldSteps) break;
            p = abs(p) / max(dot(p, p), 0.0001) - 0.340;
            float len = length(p);
            a += abs(len - pa * 0.2);
            pa = len;
        }

        a *= a * a * 2.0;
        v += vec3(s, s * s, s * s * s * s) * a * 0.0017 * fade;
        fade *= 0.960;
        s += 0.110;
    }

    v = mix(vec3(length(v)), v, 0.8);
    fragColor = vec4(v * 0.01 * settings.x, 1.0);
}
