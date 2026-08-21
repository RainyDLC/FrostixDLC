#version 150

// Порт шейдера главного меню из старого клиента (GLSL 120): два источника
// световых лучей, бегущих по экрану. Логика rayStrength/main сохранена как в
// оригинале, изменены только объявления под GLSL 150 и uniform-блок.

in vec2 vResolution;
in vec4 vParams;
in vec4 vColorA;
in vec4 vColorB;
in float vAlpha;

out vec4 fragColor;

float rayStrength(vec2 raySource, vec2 rayRefDirection, vec2 coord,
                  float seedA, float seedB, float speed,
                  float time, float reach, vec2 resolution) {
    vec2 sourceToCoord = coord - raySource;
    float cosAngle = dot(normalize(sourceToCoord), rayRefDirection);

    return clamp(
        (.45 + 0.15 * sin(cosAngle * seedA + time * speed)) +
        (0.3 + 0.2 * cos(-cosAngle * seedB + time * speed)),
        reach, 1.0) *
        clamp((resolution.x - length(sourceToCoord)) / resolution.x, reach, 1.0);
}

void main() {
    vec2 resolution = vResolution;

    float time = vParams.x;
    float intensity = vParams.y;
    float rays = vParams.z;
    float reach = vParams.w;

    vec2 coord = vec2(gl_FragCoord.x, resolution.y - gl_FragCoord.y);
    float speed = rays * 10.0;

    vec2 rayPos1 = vec2(resolution.x * 0.7, resolution.y * -0.4);
    vec2 rayRefDir1 = normalize(vec2(1.0, -0.116));
    float raySeedA1 = 36.2214 * speed;
    float raySeedB1 = 21.11349 * speed;
    float raySpeed1 = 1.5 * speed;

    vec2 rayPos2 = vec2(resolution.x * 0.8, resolution.y * -0.6);
    vec2 rayRefDir2 = normalize(vec2(1.0, 0.241));
    float raySeedA2 = 22.39910 * speed;
    float raySeedB2 = 18.0234 * speed;
    float raySpeed2 = 1.1 * speed;

    vec4 rays1 = rayStrength(rayPos1, rayRefDir1, coord, raySeedA1, raySeedB1, raySpeed1, time, reach, resolution) * vColorA;
    vec4 rays2 = rayStrength(rayPos2, rayRefDir2, coord, raySeedA2, raySeedB2, raySpeed2, time, reach, resolution) * vColorB;

    vec4 color = rays1 + rays2;

    float brightness = 1.0 * reach - (coord.y / resolution.y);
    color *= (brightness + (0.5 + intensity));

    color *= vAlpha;

    float alpha = clamp(color.a, 0.0, 1.0);

    if (alpha < 0.004) {
        discard;
    }

    fragColor = vec4(clamp(color.rgb, 0.0, 1.0), alpha);
}
