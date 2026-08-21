#version 150

// До 128 осколков за draw call. Каждый элемент — 4 vec4:
//  0: (x0, y0, x1, y1)
//  1: (x2, y2, x3, y3)
//  2: (x4, y4, x5, y5)
//  3: цвет (r, g, b, a)
// Шесть вершин = две треугольные грани. Вершины уже в пикселях
// фиксированного 2x-GUI: осколок трансформируется на CPU, тут только в NDC.
// Вырожденная вторая грань (три совпадающие вершины) ничего не рисует.
layout(std140) uniform ShardData {
    vec4 screen; // (width, height, guiScale, unused)
    vec4 shards[512]; // 128 * 4
};

out vec4 shardColor;

void main() {
    int shardIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = shardIndex * 4;

    vec4 p01 = shards[base];
    vec4 p23 = shards[base + 1];
    vec4 p45 = shards[base + 2];

    vec2 verts[6] = vec2[](p01.xy, p01.zw, p23.xy, p23.zw, p45.xy, p45.zw);
    vec2 screenPos = verts[vertexIndex];

    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);
    shardColor = shards[base + 3];
}
