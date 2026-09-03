#version 150

in vec4 shardColor;
in vec2 shardUv;

out vec4 fragColor;

uniform sampler2D PanelTex;

void main() {
    if (shardColor.a <= 0.0) {
        discard;
    }
    float alpha = clamp(shardColor.a, 0.0, 1.0);
    vec3 rgb = texture(PanelTex, shardUv).rgb * shardColor.rgb;
    fragColor = vec4(rgb, alpha);
}
