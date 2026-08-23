#version 150

// Текстурированные осколки: элемент uniform-массива - 7 vec4:
//  0..5: по вершине (x, y, u, v)
//  6: цвет (r, g, b, a)
// Координаты - пиксели фиксированного 2x-GUI, NDC считается тут.

layout(std140) uniform ShardTexData {
    vec4 screen;       // (width, height, guiScale, unused)
    vec4 items[896];   // 128 * 7
};

out vec4 shardColor;
out vec2 shardUv;

void main() {
    int shardIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = shardIndex * 7;

    vec4 d = items[base + vertexIndex];
    vec2 screenPos = d.xy;

    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);
    shardUv = d.zw;
    shardColor = items[base + 6];
}
