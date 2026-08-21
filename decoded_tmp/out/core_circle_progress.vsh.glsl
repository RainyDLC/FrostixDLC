#version 150

layout(std140) uniform CircleData {
    vec4 screen;
    vec4 circles[256]; // 64 * 4
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec4 params;
out vec4 tint;
out float guiScale;

void main() {
    int circleIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = circleIndex * 4;

    vec4 rect = circles[base];
    params = circles[base + 1];
    tint = circles[base + 2];

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
    pixelCoord = pos * rect.zw;
    guiScale = screen.z;
}
