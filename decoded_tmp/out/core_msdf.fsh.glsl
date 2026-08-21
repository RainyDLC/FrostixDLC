#version 150

in vec2 texCoord;
in vec4 charColor;
in float outlineWidth;
in vec4 outColor;
in float pxRange;
in vec2 atlasSize;

out vec4 fragColor;

uniform sampler2D Sampler0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

// Manual bilinear sampling to bypass engine-level nearest filtering
vec4 textureBilinear(sampler2D sampler, vec2 uv, vec2 size) {
    vec2 st = uv * size - 0.5;
    vec2 i_st = floor(st);
    vec2 f_st = fract(st);

    vec4 a = texture(sampler, (i_st + vec2(0.5, 0.5)) / size);
    vec4 b = texture(sampler, (i_st + vec2(1.5, 0.5)) / size);
    vec4 c = texture(sampler, (i_st + vec2(0.5, 1.5)) / size);
    vec4 d = texture(sampler, (i_st + vec2(1.5, 1.5)) / size);

    return mix(mix(a, b, f_st.x), mix(c, d, f_st.x), f_st.y);
}

// MSDF median of RGB; alpha holds a tight true SDF (MTSDF refinement)
float signedDistance(vec2 uv) {
    vec4 mtsdf = textureBilinear(Sampler0, uv, atlasSize);
    return min(median(mtsdf.r, mtsdf.g, mtsdf.b), mtsdf.a);
}

void main() {
    vec2 dx = dFdx(texCoord);
    vec2 dy = dFdy(texCoord);
    float pixelSize = length(dx) + length(dy);

    // sd units -> screen pixels
    float toPx = pixelSize > 0.0
        ? pxRange / (pixelSize * max(atlasSize.x, atlasSize.y))
        : pxRange;
    float spr = max(toPx, 1.0);

    float screenPxDist = spr * (signedDistance(texCoord) - 0.5);

    float opacity;
    if (toPx < 3.0 && pixelSize > 0.0) {
        // Small text: the distance field changes faster than the pixel grid, so a
        // single sample per pixel aliases into staircase edges. 4x rotated-grid
        // supersampling keeps small glyphs smooth without blurring large ones.
        vec2 o1 = 0.125 * dx + 0.375 * dy;
        vec2 o2 = 0.375 * dx - 0.125 * dy;
        opacity  = smoothstep(-0.6, 0.6, spr * (signedDistance(texCoord + o1) - 0.5));
        opacity += smoothstep(-0.6, 0.6, spr * (signedDistance(texCoord - o1) - 0.5));
        opacity += smoothstep(-0.6, 0.6, spr * (signedDistance(texCoord + o2) - 0.5));
        opacity += smoothstep(-0.6, 0.6, spr * (signedDistance(texCoord - o2) - 0.5));
        opacity *= 0.25;
    } else {
        opacity = smoothstep(-0.6, 0.6, screenPxDist);
    }

    if (outlineWidth > 0.0) {
        float outlineDist = screenPxDist + outlineWidth;
        float outlineOpacity = smoothstep(-0.6, 0.6, outlineDist);

        vec4 fill = vec4(charColor.rgb, charColor.a * opacity);
        vec4 outline = vec4(outColor.rgb, outColor.a * max(0.0, outlineOpacity - opacity));

        fragColor = fill + outline * (1.0 - fill.a);
    } else {
        fragColor = vec4(charColor.rgb, charColor.a * opacity);
    }

    if (fragColor.a < 0.004) {
        discard;
    }
}
