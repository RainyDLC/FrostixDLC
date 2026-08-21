#version 150

uniform sampler2D DepthTex;
uniform sampler2D MinecraftTex;
uniform sampler2D BlurTex;

layout(std140) uniform FogData {
    vec4 uParamsA; // x=near y=far z=distance w=saturation
    vec4 uParamsB; // x=clientColor
    vec4 uColor1;
    vec4 uColor2;
    vec4 uColor3;
    vec4 uColor4;
};

in vec2 texCoord;
out vec4 fragColor;

#define NOISE (0.5 / 255.0)

float getDepth(vec2 coord, float near, float far) {
    return 2.0 * near * far / (far + near - (2.0 * texture(DepthTex, coord).x - 1.0) * (far - near)) / far;
}

vec3 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4) {
    vec3 color = mix(mix(color1.rgb, color2.rgb, coords.y), mix(color3.rgb, color4.rgb, coords.y), coords.x);
    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

void main() {
    float near = uParamsA.x;
    float far = uParamsA.y;
    float dist = uParamsA.z;
    float saturation = uParamsA.w;
    float clientColor = uParamsB.x;

    float linearDepth = getDepth(texCoord, near, far);

    float smoothness = smoothstep(dist, dist + 0.1, linearDepth);
    vec3 minecraftColor = texture(MinecraftTex, texCoord).rgb;
    vec3 blurColor = texture(BlurTex, texCoord).rgb;
    vec3 finalColor;
    if (clientColor == 1.0) {
        finalColor = mix(minecraftColor, mix(createGradient(texCoord, uColor1, uColor2, uColor3, uColor4), blurColor, saturation), smoothness);
    } else {
        finalColor = mix(minecraftColor, blurColor, smoothness);
    }

    if (linearDepth > dist) {
        fragColor = vec4(finalColor, 1.0);
    } else {
        fragColor = vec4(minecraftColor, 1.0);
    }
}
