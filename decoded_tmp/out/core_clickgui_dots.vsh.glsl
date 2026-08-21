#version 150

layout(std140) uniform ClickGuiDotsData {
    vec4 rect;
    vec4 screen;
    vec4 mouse;
    vec4 primaryColor;
    vec4 secondaryColor;
};

out vec2 pixelCoord;
out vec2 screenSize;
out vec2 mouseCoord;
out float guiScale;
out float globalAlpha;
out float time;
out vec4 dotPrimary;
out vec4 dotSecondary;

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
    vec2 screenPos = rect.xy + pos * rect.zw;
    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    pixelCoord = screenPos;
    screenSize = screen.xy;
    guiScale = screen.z;
    globalAlpha = screen.w;
    mouseCoord = mouse.xy;
    time = mouse.z;
    dotPrimary = primaryColor;
    dotSecondary = secondaryColor;
}
