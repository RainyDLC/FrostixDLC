#version 150

layout(std140) uniform MainMenuGridData {
    vec4 screen;
    vec4 timeData;
};

out vec2 pixelCoord;
out vec2 screenSize;
out float guiScale;
out float globalAlpha;
out float time;

void main() {
    vec2 positions[6] = vec2[](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 1.0)
    );

    vec2 pos = positions[gl_VertexID];
    pixelCoord = pos * screen.xy;
    screenSize = screen.xy;
    guiScale = screen.z;
    globalAlpha = screen.w;
    time = timeData.x;

    vec2 ndcPos = pos * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;
    gl_Position = vec4(ndcPos, 0.0, 1.0);
}
