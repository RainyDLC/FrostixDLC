#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D maskTexture;
uniform sampler2D glowTexture;

layout(std140) uniform OutlineData {
    vec4 color;          // fill color (r, g, b, a)
    vec4 outlineColor;   // glow/outline color
    vec4 params1;        // texelX, texelY, outlineWidthF, friendCount
    vec4 params2;        // glowMode, 0, shimmerT, shimmerEnabled
    vec4 params3;        // shimmerWidth, 0, 0, 0
    vec4 friendRects[8]; // u1, v1, u2, v2 in UV [0-1], up to 8 friends
};

float maxChan(vec4 s) {
    return max(s.r, max(s.g, s.b));
}

float getOutlineAlpha() {
    int w = int(params1.z);
    if (w == 0) return 0.0;
    if (maxChan(texture(maskTexture, texCoord)) > 0.5) return 0.0;
    if (maxChan(texture(glowTexture,  texCoord)) < 0.01) return 0.0;

    vec2 ts = params1.xy;
    for (int x = -w; x <= w; x++) {
        for (int y = -w; y <= w; y++) {
            if (x == 0 && y == 0) continue;
            vec2 off = vec2(float(x), float(y)) * ts;
            if (maxChan(texture(maskTexture, texCoord + off)) > 0.5)
                return 1.0;
        }
    }
    return 0.0;
}

// Friends render with 0x55FF55 (G/R ≈ 3.0); non-friends render white (G/R = 1.0)
// Ratio check is scale-invariant — works even at low glow intensities far from the entity
bool isFriendColor(vec4 c) {
    return c.g > c.r * 1.5;
}

bool isFriendPixel() {
    vec4 mask = texture(maskTexture, texCoord);
    if (maxChan(mask) > 0.1) return isFriendColor(mask);
    vec4 glow = texture(glowTexture, texCoord);
    return maxChan(glow) > 0.01 && isFriendColor(glow);
}

void main() {
    float blurredMask = maxChan(texture(glowTexture, texCoord));
    float sharpMask   = maxChan(texture(maskTexture, texCoord));

    int glowMode = int(params2.x);

    float glowAlpha;
    if      (glowMode == 0) glowAlpha = max(0.0, blurredMask - sharpMask);
    else if (glowMode == 1) glowAlpha = max(0.0, sharpMask - blurredMask);
    else                    glowAlpha = abs(blurredMask - sharpMask);

    float outlineAlpha = getOutlineAlpha();
    float finalAlpha   = max(outlineAlpha, glowAlpha);

    if (finalAlpha < 0.01) discard;

    vec4 finalColor = (int(params1.w) > 0 && isFriendPixel())
        ? vec4(0.333, 1.0, 0.333, outlineColor.a)
        : outlineColor;

    float shimmerT       = params2.z;
    float shimmerEnabled = params2.w;
    if (shimmerEnabled > 0.5) {
        float sw      = params3.x;
        float dist    = abs(texCoord.y - shimmerT);
        float shimmer = smoothstep(sw, 0.0, dist) * 0.65;
        finalColor.rgb = mix(finalColor.rgb, vec3(1.0), shimmer);
    }

    fragColor = vec4(finalColor.rgb, finalColor.a * finalAlpha);
}
