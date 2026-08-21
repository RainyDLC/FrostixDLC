#version 150

// Батч до 64 ректов за один draw call.
// Каждый рект занимает 14 vec4:
//  0: rect (drawX, drawY, drawW, drawH)
//  1: radii
//  2..10: colors[9]
// 11: sourceRect (x, y, w, h)
// 12: glowData (glowSize, glowStrength, glowSoftness, innerBlur)
// 13: glowColor
layout(std140) uniform RectData {
    vec4 screen; // (width, height, guiScale, unused)
    vec4 rects[896]; // 64 * 14
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out vec4 fragColors[9];
out float guiScale;
out float innerBlur;
out float glowSize;
out float glowStrength;
out float glowSoftness;
out vec4 glow;

void main() {
    int rectIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = rectIndex * 14;

    vec4 rect = rects[base];
    vec4 radii = rects[base + 1];
    vec4 sourceRect = rects[base + 11];
    vec4 glowData = rects[base + 12];
    vec4 glowColor = rects[base + 13];

    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    vec2 pos = positions[vertexIndex];

    vec2 screenPos = rect.xy + pos * rect.zw;
    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord = pos;
    pixelCoord = screenPos - sourceRect.xy;
    rectSize = sourceRect.zw;
    cornerRadii = radii;
    guiScale = screen.z;
    innerBlur = glowData.w;
    glowSize = glowData.x;
    glowStrength = glowData.y;
    glowSoftness = glowData.z;
    glow = glowColor;

    for (int i = 0; i < 9; i++) {
        fragColors[i] = rects[base + 2 + i];
    }
}
