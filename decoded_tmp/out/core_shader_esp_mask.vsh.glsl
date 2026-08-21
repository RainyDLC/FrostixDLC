#version 150

in vec3 Position;

layout(std140) uniform EspMvpData {
    mat4 mvp;
};

void main() {
    gl_Position = mvp * vec4(Position, 1.0);
}
