#version 150

in vec2 pixelCoord;
in vec4 vParams;
in vec4 vTint;
in float vPhase;
in vec2 vArea;

out vec4 fragColor;

void main() {
    float step_ = vParams.x;
    float lineWidth = vParams.y;
    float edgeFade = vParams.z;
    float tower = vParams.w;

    // расстояние до ближайшей строки сетки
    float distToLine = abs(mod(pixelCoord.y, step_) - step_ * 0.5);
    float half_ = lineWidth * 0.5;

    // сглаженный край строки: одна строка вместо отдельного рект-вызова на каждую
    float aa = max(fwidth(pixelCoord.y), 0.5);
    float line = 1.0 - smoothstep(half_ - aa, half_ + aa, distToLine);

    if (line <= 0.001) {
        discard;
    }

    // бегущая сверху вниз волна яркости
    float waveY = -tower + vPhase * (vArea.y + tower * 2.0);
    float wave = 1.0 - clamp(abs(pixelCoord.y - waveY) / max(tower, 0.001), 0.0, 1.0);
    float bright = mix(0.2, 1.0, wave);

    // затухание у левого и правого краёв
    float edge = 1.0;
    if (edgeFade > 0.001) {
        edge = clamp(pixelCoord.x / edgeFade, 0.0, 1.0)
             * clamp((vArea.x - pixelCoord.x) / edgeFade, 0.0, 1.0);
    }

    float alpha = line * bright * edge * vTint.a;

    if (alpha < 0.004) {
        discard;
    }

    fragColor = vec4(vTint.rgb, alpha);
}
