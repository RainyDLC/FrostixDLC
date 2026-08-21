#version 150

layout(std140) uniform ScanLinesData {
    vec4 screen;  // xy — размер экрана гуи, zw — размер области эффекта
    vec4 params;  // x — шаг строк, y — толщина строки, z — затухание у краёв, w — высота ореола волны
    vec4 tint;    // rgb — цвет, a — общая прозрачность
    vec4 timing;  // x — фаза волны 0..1
};

out vec2 pixelCoord;
out vec4 vParams;
out vec4 vTint;
out float vPhase;
out vec2 vArea;

void main() {
    vec2 positions[6] = vec2[](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 1.0)
    );

    vec2 pos = positions[gl_VertexID % 6];
    vec2 screenPos = pos * screen.zw;

    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    pixelCoord = screenPos;
    vParams = params;
    vTint = tint;
    vPhase = timing.x;
    vArea = screen.zw;
}
