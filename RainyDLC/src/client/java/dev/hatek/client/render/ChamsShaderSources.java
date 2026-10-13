package dev.hatek.client.render;

/** Self-contained GLSL 330 sources, also available during LegitBuilder late injection/reload. */
public final class ChamsShaderSources {
    public static final String SHADER_ID = "hatek:core/chams_material";
    private ChamsShaderSources() {}

    public static final String VERTEX = """
            #version 330
            
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
                mat4 TextureMat;
            };
            layout(std140) uniform Projection { mat4 ProjMat; };
            layout(std140) uniform Lighting {
                vec3 Light0_Direction;
                vec3 Light1_Direction;
            };
            
            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            in ivec2 UV1;
            in ivec2 UV2;
            in vec3 Normal;
            uniform sampler2D Sampler1;
            uniform sampler2D Sampler2;
            
            out vec2 texCoord0;
            out vec4 vertexColor;
            out vec4 overlayColor;
            out vec3 lightMapColor;
            out vec3 viewPosition;
            out vec3 viewNormal;
            out float sphericalVertexDistance;
            out float cylindricalVertexDistance;
            
            void main() {
                vec4 view = ModelViewMat * vec4(Position, 1.0);
                gl_Position = ProjMat * view;
                viewPosition = view.xyz;
                // Entity model poses contain rotations/uniform scale; normals are already pose-transformed.
                viewNormal = mat3(ModelViewMat) * Normal;
                texCoord0 = UV0; // TextureMat is a parameter payload, never a UV transform in this pipeline.
                float light = max(dot(Light0_Direction, Normal), 0.0) + max(dot(Light1_Direction, Normal), 0.0);
                vertexColor = vec4(Color.rgb * min(0.6 * light + 0.4, 1.0), Color.a);
                overlayColor = texelFetch(Sampler1, UV1, 0);
                vec2 lightUv = clamp(vec2(UV2) / 256.0 + vec2(0.5 / 16.0), vec2(0.5 / 16.0), vec2(15.5 / 16.0));
                lightMapColor = texture(Sampler2, lightUv).rgb;
                sphericalVertexDistance = length(Position);
                cylindricalVertexDistance = max(length(Position.xz), abs(Position.y));
            }
            """;

    public static final String FRAGMENT = """
            #version 330
            
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
                mat4 TextureMat;
            };
            layout(std140) uniform Fog {
                vec4 FogColor;
                float FogEnvironmentalStart;
                float FogEnvironmentalEnd;
                float FogRenderDistanceStart;
                float FogRenderDistanceEnd;
                float FogSkyEnd;
                float FogCloudsEnd;
            };
            uniform sampler2D Sampler0;
            
            in vec2 texCoord0;
            in vec4 vertexColor;
            in vec4 overlayColor;
            in vec3 lightMapColor;
            in vec3 viewPosition;
            in vec3 viewNormal;
            in float sphericalVertexDistance;
            in float cylindricalVertexDistance;
            out vec4 fragColor;
            
            float hash21(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            float noise21(vec2 p) {
                vec2 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x),
                           mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1)), f.x), f.y);
            }
            float fbm(vec2 p) {
                // Three bounded octaves: no expensive ray marching or extra screen passes.
                float n = 0.55 * noise21(p);
                p = mat2(0.8, -0.6, 0.6, 0.8) * p * 2.1 + 9.2;
                n += 0.28 * noise21(p);
                n += 0.14 * noise21(p * 2.1 + 4.7);
                return n;
            }
            float fogAmount(float d, float a, float b) {
                if (d <= a) return 0.0;
                if (d >= b) return 1.0;
                return (d - a) / max(b - a, 0.0001);
            }
            
            void main() {
                vec4 skin = texture(Sampler0, texCoord0);
                // Keep skin/hat cutouts instead of filling transparent parts with a solid silhouette.
                if (skin.a < 0.05) discard;
                vec4 primary = TextureMat[0];
                vec3 secondary = TextureMat[1].rgb;
                float strength = clamp(TextureMat[1].a, 0.0, 1.0);
                float t = TextureMat[2].x;
                int mode = int(TextureMat[2].y + 0.5);
                float scale = TextureMat[2].z;
                float skinDetail = TextureMat[2].w;
                float rimStrength = TextureMat[3].x;
                float pulse = TextureMat[3].y;
                vec3 normal = viewNormal / max(length(viewNormal), 0.0001);
                vec3 eye = -viewPosition / max(length(viewPosition), 0.0001);
                float facing = abs(dot(normal, eye));
                float rim = pow(clamp(1.0 - facing, 0.0, 1.0), 2.2);
                vec2 uv = texCoord0 * (8.0 * scale);
                vec3 material = primary.rgb;
                float energy = 0.0;
                float opacity = 1.0;
            
                if (mode == 0) { // Aurora: layered flowing curtains, not a hue-cycling flat tint.
                    float warp = sin(uv.x * 2.8 + t * 0.42) * 0.65 + sin(uv.x * 5.0 - t * 0.28) * 0.22;
                    float wave = 0.5 + 0.5 * sin(uv.y * 3.2 + warp * 2.4 - t * 0.75);
                    float ribbon = pow(wave, 5.0);
                    material = mix(primary.rgb, secondary, smoothstep(0.15, 0.9, wave));
                    energy = ribbon * 0.45;
                } else if (mode == 1) { // Prism: angle-sensitive interference and a moving spectral caustic.
                    float phase = facing * 3.0 + uv.x * 0.18 + uv.y * 0.12 - t * 0.16;
                    vec3 spectrum = 0.55 + 0.45 * cos(6.2831853 * (phase + vec3(0, 0.33, 0.67)));
                    material = mix(mix(primary.rgb, secondary, 1.0 - facing), spectrum, 0.5);
                    energy = pow(0.5 + 0.5 * sin(uv.x * 3.1 + uv.y * 4.7 + t * 0.7), 9.0) * 0.38;
                } else if (mode == 2) { // Hologram: anti-aliased scanlines, a lattice and a traveling scanner.
                    float phase = uv.y * 12.0 - t * 0.8;
                    float aa = max(fwidth(phase), 0.02);
                    float line = 1.0 - smoothstep(0.06, 0.06 + aa, abs(fract(phase) - 0.5));
                    vec2 gridPos = uv * 3.0;
                    vec2 gridD = abs(fract(gridPos) - 0.5);
                    vec2 gridAA = max(fwidth(gridPos), vec2(0.02));
                    vec2 grid = vec2(1.0) - smoothstep(vec2(0.025), vec2(0.025) + gridAA, gridD);
                    float scanner = pow(0.5 + 0.5 * sin(uv.y * 2.0 - t * 1.3), 18.0);
                    material = mix(primary.rgb, secondary, scanner);
                    energy = line * 0.24 + max(grid.x, grid.y) * 0.16 + scanner * 0.5;
                    opacity = mix(0.75, 1.0, max(line, scanner));
                } else if (mode == 3) { // Nebula: domain-warped clouds with stable, gently twinkling stars.
                    vec2 drift = vec2(t * 0.05, -t * 0.035);
                    vec2 warp = vec2(fbm(uv + drift), fbm(uv + vec2(6.7, 2.3) - drift));
                    float cloud = fbm(uv * 1.7 + warp * 2.5 + drift);
                    material = mix(primary.rgb * 0.42, secondary, smoothstep(0.18, 0.82, cloud));
                    vec2 cells = texCoord0 * (96.0 * scale);
                    float seed = hash21(floor(cells));
                    float dotStar = 1.0 - smoothstep(0.025, 0.22 + max(fwidth(cells.x), fwidth(cells.y)),
                                                  length(fract(cells) - 0.5));
                    float stars = step(0.962, seed) * dotStar * (0.65 + 0.35 * sin(t * 1.4 + seed * 50.0));
                    energy = cloud * cloud * 0.35 + stars * 0.85;
                }
            
                vec3 illumination = mix(vec3(0.65), lightMapColor, 0.35) * mix(vec3(0.7), vertexColor.rgb, 0.3);
                vec3 classic = skin.rgb * primary.rgb * vertexColor.rgb * lightMapColor;
                vec3 tintedSkin = mix(primary.rgb * (0.5 + 0.5 * dot(skin.rgb, vec3(0.2126, 0.7152, 0.0722))),
                                      skin.rgb * primary.rgb, skinDetail);
                vec3 procedural = mix(material, skin.rgb * material, skinDetail) * illumination;
                vec3 color = mix(tintedSkin * illumination, procedural, strength);
                color += material * energy * strength;
                color += mix(primary.rgb, secondary, 0.45) * rim * rimStrength * 0.55;
                if (mode == 4) color = classic;
                color *= pulse * ColorModulator.rgb;
                color = mix(overlayColor.rgb, color, overlayColor.a);
                float fog = max(fogAmount(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd),
                                fogAmount(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd));
                color = mix(color, FogColor.rgb, fog * FogColor.a);
                float alpha = skin.a * primary.a * vertexColor.a * ColorModulator.a * mix(1.0, opacity, strength);
                fragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
            }
            """;
}
