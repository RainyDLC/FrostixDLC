#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float guiScale;
in float blurRadius;
in vec2 texelSize;
in vec4 tintColor;
in vec2 resolution;
in vec4 glass;

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    float dist = roundedBoxSDF(center, halfSize, rRadii);

    float pixelWidth = fwidth(dist);
    float smoothing = max(pixelWidth, 0.5 / guiScale);
    float alpha = 1.0 - smoothstep(-smoothing, smoothing, dist);

    if (alpha < 0.01) {
        discard;
    }

    vec2 glassCoord = texCoord;

    if (glass.x > 0.0) {
        // pure edge refraction, exactly like GlassHands: bend the background along
        // the edge normal, strongest at the rim, ~0 in the centre. NO coloured outline.
        float maxNorm   = max(1.0, min(halfSize.x, halfSize.y));
        float distToEdge = abs(dist);
        float edgeGradient = 1.0 - clamp(distToEdge / maxNorm, 0.0, 1.0);

        float fresnelPower = max(1.0, glass.y);
        float fresnel = clamp(pow(edgeGradient, fresnelPower), 0.0, 1.0);

        // edge normal from the SDF gradient (perpendicular to the edge everywhere)
        vec2 grad = vec2(dFdx(dist), dFdy(dist));
        float gmag = length(grad);
        vec2 normal = gmag > 1e-5 ? grad / gmag : vec2(0.0, 1.0);

        glassCoord = texCoord - normal * fresnel * glass.x * texelSize;
    }

    vec4 blurred = texture(Sampler0, clamp(glassCoord, vec2(0.0), vec2(1.0)));

    vec3 finalColor = mix(blurred.rgb, tintColor.rgb, tintColor.a);

    float globalAlpha = clamp(blurRadius, 0.0, 1.0);
    fragColor = vec4(finalColor, alpha * globalAlpha);
}
