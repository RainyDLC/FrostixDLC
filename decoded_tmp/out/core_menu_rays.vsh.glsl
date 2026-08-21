#version 150

layout(std140) uniform MenuRaysData {
    vec4 screen; // xy — разрешение кадра, w — общая прозрачность
    vec4 params; // x — время, y — intensity, z — rays, w — reach
    vec4 colorA;
    vec4 colorB;
};

out vec2 vResolution;
out vec4 vParams;
out vec4 vColorA;
out vec4 vColorB;
out float vAlpha;

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

    vec2 ndcPos = pos * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    vResolution = screen.xy;
    vParams = params;
    vColorA = colorA;
    vColorB = colorB;
    vAlpha = screen.w;
}
