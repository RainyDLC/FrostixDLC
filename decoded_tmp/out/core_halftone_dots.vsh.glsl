#version 150

layout(std140) uniform HalftoneDotsData {
    vec4 screen;  // xy — размер экрана гуи, zw — размер области эффекта
    vec4 params;  // x — шаг сетки, y — мин. радиус, z — макс. радиус, w — радиус влияния курсора
    vec4 pointer; // xy — курсор в координатах гуи
    vec4 tint;    // rgb — цвет точек, a — прозрачность
};

out vec2 pixelCoord;
out vec4 vParams;
out vec2 vPointer;
out vec4 vTint;

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
    vPointer = pointer.xy;
    vTint = tint;
}
