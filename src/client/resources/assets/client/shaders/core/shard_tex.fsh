#version 150

// Кусочек настоящего меню: сэмпл панели, умноженный на цвет осколка
// (rgb затемняет для глубины, a - альфа полёта).

in vec4 shardColor;
in vec2 shardUv;

out vec4 fragColor;

uniform sampler2D PanelTex;

void main() {
    if (shardColor.a <= 0.0) {
        discard;
    }
    fragColor = texture(PanelTex, shardUv) * shardColor;
}
