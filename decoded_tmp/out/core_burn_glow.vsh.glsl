#version 150

layout(std140) uniform BurnData {
    vec4 res;        // x=width, y=height, z=time(sec), w=fillMode
    vec4 fire;       // x=radiusPx, y=strength, z=flameSpeed, w=colorMix
    vec4 trailP;     // x=decay, y=flameHeightPx, z=flowSpeed, w=trailStrength
    vec4 fireColor;  // rgb=fire color, w=glowStrength
};

out vec2 texCoord;

void main() {
    vec2 positions[6] = vec2[](
    vec2(-1.0, -1.0),
    vec2( 1.0, -1.0),
    vec2( 1.0,  1.0),
    vec2(-1.0, -1.0),
    vec2( 1.0,  1.0),
    vec2(-1.0,  1.0)
    );

    vec2 uvs[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    texCoord = uvs[gl_VertexID];
}
