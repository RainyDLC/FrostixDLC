#version 150

uniform sampler2D DepthSampler;

layout(std140) uniform ScanData {
    mat4 invViewMat;
    mat4 invProjMat;

    vec4 cameraPos;
    vec4 centerRadius;

    vec4 outerColor;
    vec4 midColor;
    vec4 innerColor;
    vec4 scanlineColor;

    vec4 screenData; // x=width, y=height, z=bandWidth, w=time
    vec4 fadeData;   // x=fade
};

in vec2 texCoord;
out vec4 fragColor;

const float sharpness = 10.0;

float scanlines(float y, float time) {
    return sin(y + time * 10.0) * 0.5 + 0.5;
}

vec3 worldPosFromDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, z, 1.0);
    vec4 viewPos = invProjMat * clipPos;
    viewPos /= max(viewPos.w, 0.0001);
    vec4 worldPos = invViewMat * viewPos;
    return cameraPos.xyz + worldPos.xyz;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;

    if (depth >= 1.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 worldPos = worldPosFromDepth(depth);
    float radius = centerRadius.w;
    float bandWidth = screenData.z;
    float dist = distance(worldPos, centerRadius.xyz);

    if (dist < radius && dist > radius - bandWidth) {
        float diff = 1.0 - (radius - dist) / bandWidth;
        vec4 edge = mix(midColor, outerColor, pow(diff, sharpness));
        vec4 color = mix(innerColor, edge, diff);

        float lines = scanlines(gl_FragCoord.y, screenData.w);
        color += lines * scanlineColor;
        color *= diff;
        color *= fadeData.x;

        fragColor = color;
    } else {
        fragColor = vec4(0.0);
    }
}
