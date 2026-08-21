#version 150

// Плоская заливка грани. Без сглаживания кромок намеренно: в собранном
// состоянии осколки стыкуются пиксель-в-пиксель, а полупрозрачные кромки
// дали бы светлые швы по линиям разлома.
in vec4 shardColor;

out vec4 fragColor;

void main() {
    if (shardColor.a <= 0.0) {
        discard;
    }
    fragColor = shardColor;
}
