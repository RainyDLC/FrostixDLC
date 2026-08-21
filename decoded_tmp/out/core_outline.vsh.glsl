#version 150

// Батч до 64 обводок за один draw call.
// Каждая обводка занимает 13 vec4:
//  0: rect (x, y, w, h)
//  1: radii
//  2..9: colors[8]
// 10: thicknesses (0..3)
// 11: thicknesses2 (4..7)
// 12: misc (smoothness, 0, 0, 0)
layout(std140) uniform OutlineData {
    vec4 screen; // (width, height, guiScale, unused)
    vec4 outlines[832]; // 64 * 13
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out vec4 fragColors[8];
out float fragThicknesses[8];
out float fragSmoothness;
out float guiScale;
out float maxThickness;

void main() {
    int outlineIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = outlineIndex * 13;

    vec4 rect = outlines[base];
    vec4 radii = outlines[base + 1];
    vec4 thicknesses = outlines[base + 10];
    vec4 thicknesses2 = outlines[base + 11];
    vec4 misc = outlines[base + 12];

    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    vec2 pos = positions[vertexIndex];

    float maxT = max(
        max(max(thicknesses.x, thicknesses.y), max(thicknesses.z, thicknesses.w)),
        max(max(thicknesses2.x, thicknesses2.y), max(thicknesses2.z, thicknesses2.w))
    );

    float padding = maxT + 2.0;

    vec2 expandedPos = rect.xy - padding + pos * (rect.zw + padding * 2.0);
    vec2 ndcPos = (expandedPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord = pos;
    pixelCoord = pos * (rect.zw + padding * 2.0) - padding;
    rectSize = rect.zw;
    cornerRadii = radii;
    fragSmoothness = misc.x;
    guiScale = screen.z;
    maxThickness = maxT;

    for (int i = 0; i < 8; i++) {
        fragColors[i] = outlines[base + 2 + i];
    }

    fragThicknesses[0] = thicknesses.x;
    fragThicknesses[1] = thicknesses.y;
    fragThicknesses[2] = thicknesses.z;
    fragThicknesses[3] = thicknesses.w;
    fragThicknesses[4] = thicknesses2.x;
    fragThicknesses[5] = thicknesses2.y;
    fragThicknesses[6] = thicknesses2.z;
    fragThicknesses[7] = thicknesses2.w;
}
