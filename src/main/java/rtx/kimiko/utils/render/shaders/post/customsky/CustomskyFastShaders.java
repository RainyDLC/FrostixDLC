package rtx.kimiko.utils.render.shaders.post.customsky;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Оптимизированные версии шейдеров кастомного неба. Регистрируются ПОСЛЕ {@link CustomskyShaders}
 * и перезаписывают blackhole + composite. Картинка та же, считается в разы дешевле:
 *
 * blackhole:
 *  - GasDisc: дешёвая проверка «есть ли тут вообще диск/свечение» теперь ДО всех pow/normalize
 *    (раньше доплер, glow и bloom считались на каждом из 200 шагов, даже в пустоте);
 *  - pcurve(x, 4, 0.9): константа посчитана заранее, результат переиспользуется (было 10 pow на шаг);
 *  - длина/квадрат радиуса луча считаются 1 раз на шаг и шарятся между WarpSpace/GasDisc/Haze;
 *  - pow(x, 2/1.5/0.5/-2.5) заменены на умножения/sqrt (математически то же самое);
 *  - луч, упавший за горизонт событий (r < 0.25), больше не гоняется до 200 итераций:
 *    внутри r < 0.5 диск и дымка дают ровно 0, а обратно такие лучи не выходят (проверено симуляцией).
 * composite:
 *  - не читает копию сцены: пиксели с геометрией просто discard (в буфере и так лежит сцена),
 *    поэтому полноэкранная копия цвета каждый кадр больше не нужна.
 */
public final class CustomskyFastShaders {
    private CustomskyFastShaders() {
    }

    public static void register(@NotNull Map<String, String> sources) {
        sources.put("post/customsky/blackhole.glsl", "//!fragment\n" + BLACKHOLE);
        sources.put("post/customsky/blackhole.fsh", BLACKHOLE);
        sources.put("post/customsky/composite.glsl", "//!fragment\n" + COMPOSITE);
        sources.put("post/customsky/composite.fsh", COMPOSITE);
    }

    private static final String BLACKHOLE = """
            #version 150

            #moj_import <kimiko:theme_wave.glsl>

            uniform sampler2D NoiseTex;
            uniform sampler2D History;

            layout(std140) uniform SkyParams {
                mat4 invViewProj;
                vec4 misc;
                vec4 skyColor;
                vec4 skyColor2;
                vec4 taa;
                mat4 prevViewProj;
                vec4 skyExtra;
            };

            in vec2 texCoord;
            out vec4 fragColor;

            const int ITERATIONS = 200;
            const float FAR = 16.0;
            const float ENTRY_R = 8.0;
            const float CAM_DIST = 14.0;
            const float INCL = 0.10;
            const float EXPOSURE = 0.024;
            const float THEME_MIX = 0.5;
            const float ZOOM = 2.4;
            const float TAA_BLEND = 0.85;
            const float SPIN = 0.16;
            const float TAU = 6.28318531;

            const float DISC_INNER = 0.55;
            const float DISC_WIDTH = 5.3;
            const float PCURVE_K = 10.349099641;
            const float WARP_K = 5.0 / 200.0;
            const float HAZE_K = 2.9 / 200.0;
            const float COVER_K = 1200.0 / 200.0;

            vec3 hsv2rgb(vec3 c) {
                vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
                return c.z * mix(vec3(1.0), rgb, c.y);
            }

            float hash21(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            float noise(vec3 x) {
                vec3 p = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
                vec2 rg = textureLod(NoiseTex, (uv + 0.5) / 256.0, 0.0).yx;
                return -1.0 + 2.0 * mix(rg.x, rg.y, f.z);
            }

            vec3 discCoord(float ang, float rad, float freq) {
                float a = 1.425 * freq;
                return vec3(cos(ang) * a, sin(ang) * a, rad * freq);
            }

            float octave(float ang, float rad, float freq, float aa) {
                float w = clamp(1.5 - aa * freq * 1.5, 0.0, 1.0);
                if (w <= 0.01) {
                    return 0.5;
                }
                return mix(0.5, noise(discCoord(ang, rad, freq)) * 0.5 + 0.5, w);
            }

            float pcurveDisc(float x) {
                float x2 = x * x;
                return PCURVE_K * x2 * x2 * pow(1.0 - x, 0.9);
            }

            vec3 encodeHDR(vec3 c) {
                c = max(c, vec3(0.0));
                return pow(c / (1.0 + c), vec3(1.0 / 2.2));
            }

            vec3 skyTint(float axis, float t, float time) {
                float mode = skyColor.w;
                if (mode > 1.5) {
                    float hue = fract(axis * 0.159155 + time * 0.02 + t * 0.15);
                    return hsv2rgb(vec3(hue, 0.85, 1.0));
                }
                vec2 fragXY = kimikoFragXYFromUV(texCoord);
                vec3 c1 = skyExtra.x > 0.5 ? kimikoClientPrimary(fragXY) : skyColor.rgb;
                if (mode > 0.5) {
                    vec3 c2 = skyExtra.x > 0.5 ? kimikoClientSecondary(fragXY) : skyColor2.rgb;
                    float k = clamp(0.5 + 0.5 * sin(axis * 2.0 + time * 0.15) + (t - 0.5) * 0.7, 0.0, 1.0);
                    return mix(c1, c2, k);
                }
                return c1;
            }

            void Haze(inout vec3 color, vec3 pos, float r2, float alpha, vec3 mainColor) {
                if (r2 > 36.0 || r2 < 0.25) {
                    return;
                }
                vec2 q = vec2(length(pos.xz) - 1.0, pos.y - 0.05);
                float torusDist = abs(length(q) - 0.01);
                float bloomDisc = 1.0 / (torusDist * torusDist + 0.001);
                color += mainColor * (bloomDisc * HAZE_K * (1.0 - alpha));
            }

            void GasDisc(inout vec3 color, inout float alpha, vec3 pos, float distFromCenter, float time, vec3 mainColor, float aa, vec3 eyevec) {
                if (distFromCenter > 7.0) {
                    return;
                }
                float distFromDisc = pos.y;
                float radialGradient = 1.0 - clamp((distFromCenter - DISC_INNER) / DISC_WIDTH * 0.5, 0.0, 1.0);
                float pc = pcurveDisc(radialGradient);
                if (pc <= 0.0) {
                    return;
                }

                float discThickness = 0.1 * radialGradient;
                float coverage = pc * clamp(1.0 - abs(distFromDisc) / max(discThickness, 1e-5), 0.0, 1.0);
                coverage = clamp(coverage * 0.7, 0.0, 1.0);

                float fb = abs(distFromCenter - DISC_INNER) + 0.4;
                fb *= fb;
                float fade = fb * fb * 0.04;
                float bloomFactor = 1.0 / (distFromDisc * distFromDisc * 40.0 + fade + 0.00002);
                float mixW = clamp(1.0 - coverage, 0.0, 1.0);
                coverage = clamp(coverage + bloomFactor * bloomFactor * 0.1, 0.0, 1.0);
                if (coverage < 0.01) {
                    return;
                }

                vec3 tangent = normalize(vec3(-pos.z, 0.0, pos.x));
                float dx = clamp(1.0 - 0.55 * dot(tangent, eyevec), 0.45, 1.9);
                float dop = 1.0 / (dx * dx * sqrt(dx));
                vec3 dopTint = mix(vec3(1.30, 0.72, 0.45), vec3(0.80, 0.92, 1.45), clamp((dop - 0.7) * 0.9, 0.0, 1.0));

                vec3 dustColorLit = mainColor * dop * dopTint;
                float og = 1.0 - radialGradient;
                float dustGlow = 1.0 / (og * og * 290.0 + 0.002);
                vec3 dustColor = dustColorLit * (dustGlow * 8.2);

                vec3 b = dustColorLit * (bloomFactor * sqrt(bloomFactor));
                b *= mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(radialGradient * radialGradient));
                b *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(sqrt(radialGradient)));
                dustColor = mix(dustColor, b * 150.0, mixW);

                float ang = atan(-pos.x, -pos.z);
                float rad = (distFromCenter * 1.5 + 0.55 + distFromDisc * 1.5) * 0.95 + time * 0.012;

                float omega = SPIN * pow(3.2 / max(distFromCenter, 0.75), 1.5);
                float angA = ang + mod(time * omega, TAU);
                float angB = ang + mod(time * omega * 0.45, TAU);

                float n1 = 1.0;
                n1 *= octave(angA, rad, 3.0, aa);
                n1 *= octave(angB, rad, 6.0, aa);
                n1 *= octave(angA, rad, 12.0, aa);
                n1 *= octave(angB, rad, 24.0, aa);

                float n2 = 2.0;
                float rad2 = rad + 30.0;
                n2 *= octave(angB, rad2, 3.0, aa);
                n2 *= octave(angA, rad2, 6.0, aa);
                n2 *= octave(angB, rad2, 12.0, aa);
                n2 *= octave(angA, rad2, 24.0, aa);
                n2 *= octave(angB, rad2, 48.0, aa);
                n2 *= octave(angA, rad2, 92.0, aa);

                dustColor *= n1 * 0.998 + 0.002;
                coverage *= n2;

                float bandAng = ang + mod(time * omega * 0.5, TAU);
                float band = noise(discCoord(bandAng, rad, 1.35)) * 0.5 + 0.5;
                float grain = noise(discCoord(bandAng, rad + 70.0, 3.46)) * 0.5 + 0.5;
                vec3 texCol = mix(vec3(0.95, 0.55, 0.26), vec3(0.42, 0.60, 1.0), band) * (0.45 + 0.80 * grain);
                dustColor *= texCol * texCol * 4.0;

                float arm = 0.5 + 0.5 * cos(2.0 * ang + 2.6 * log(max(distFromCenter, 0.3)) + time * SPIN * 4.5);
                dustColor *= 0.40 + 1.05 * arm * arm;
                coverage *= 0.78 + 0.22 * arm;

                float dAngA = abs(mod(angA - 1.35, TAU) - 3.14159265);
                float dRadA = distFromCenter - 1.5;
                float dAngB = abs(mod(angB - 4.2, TAU) - 3.14159265);
                float dRadB = distFromCenter - 2.7;
                float hot = exp(-(dAngA * dAngA * 0.5 + dRadA * dRadA * 7.0)) * 3.4
                          + exp(-(dAngB * dAngB * 0.9 + dRadB * dRadB * 5.0)) * 1.8;
                dustColor *= 1.0 + hot;

                coverage = clamp(coverage * COVER_K, 0.0, 1.0);
                dustColor = max(vec3(0.0), dustColor);

                coverage *= pc;

                color = (1.0 - alpha) * dustColor * coverage + color;
                alpha = (1.0 - alpha) * coverage + alpha;
            }

            vec3 rayDir(vec2 uv) {
                vec2 ndc = uv * 2.0 - 1.0;
                vec4 pFar = invViewProj * vec4(ndc, 1.0, 1.0);
                vec4 pNear = invViewProj * vec4(ndc, -1.0, 1.0);
                return normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);
            }

            vec3 toLocal(vec3 dir, vec3 eX, vec3 eY, vec3 eZ) {
                vec3 local = vec3(dot(dir, eX), dot(dir, eY), dot(dir, eZ));
                local.xy /= ZOOM;
                return normalize(local);
            }

            vec3 sampleHistory(vec2 uv, vec2 res) {
                vec2 samplePos = uv * res;
                vec2 texPos1 = floor(samplePos - 0.5) + 0.5;
                vec2 f = samplePos - texPos1;

                vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
                vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
                vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
                vec2 w3 = f * f * (-0.5 + 0.5 * f);

                vec2 w12 = w1 + w2;
                vec2 offset12 = w2 / w12;

                vec2 p0 = (texPos1 - 1.0) / res;
                vec2 p3 = (texPos1 + 2.0) / res;
                vec2 p12 = (texPos1 + offset12) / res;

                vec3 result = vec3(0.0);
                result += textureLod(History, vec2(p0.x, p0.y), 0.0).rgb * (w0.x * w0.y);
                result += textureLod(History, vec2(p12.x, p0.y), 0.0).rgb * (w12.x * w0.y);
                result += textureLod(History, vec2(p3.x, p0.y), 0.0).rgb * (w3.x * w0.y);
                result += textureLod(History, vec2(p0.x, p12.y), 0.0).rgb * (w0.x * w12.y);
                result += textureLod(History, vec2(p12.x, p12.y), 0.0).rgb * (w12.x * w12.y);
                result += textureLod(History, vec2(p3.x, p12.y), 0.0).rgb * (w3.x * w12.y);
                result += textureLod(History, vec2(p0.x, p3.y), 0.0).rgb * (w0.x * w3.y);
                result += textureLod(History, vec2(p12.x, p3.y), 0.0).rgb * (w12.x * w3.y);
                result += textureLod(History, vec2(p3.x, p3.y), 0.0).rgb * (w3.x * w3.y);

                return clamp(result, vec3(0.0), vec3(1.0));
            }

            void main() {
                float time = misc.x;
                float brightness = misc.w;

                vec2 res = vec2(textureSize(History, 0));

                vec3 rd = rayDir(texCoord);
                vec3 rdJitter = rayDir(texCoord + taa.xy / res);

                vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
                vec3 upRef = vec3(0.0, 1.0, 0.0);
                vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
                vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
                vec3 eX = normalize(cross(eY, bhDir));
                vec3 eZ = cross(eX, eY);

                vec3 tint = skyTint(atan(rd.z, rd.x), 0.5, time);
                vec3 mainColor = mix(vec3(1.0), tint, THEME_MIX);

                vec3 camWorld = -bhDir * CAM_DIST;
                vec3 pos = vec3(dot(camWorld, eX), dot(camWorld, eY), dot(camWorld, eZ));
                vec3 eyevec = toLocal(rdJitter, eX, eY, eZ);
                float pixAngle = length(toLocal(rayDir(texCoord + vec2(1.0, 0.0) / res), eX, eY, eZ)
                        - toLocal(rd, eX, eY, eZ));
                float aa = pixAngle * CAM_DIST * 1.425;

                vec3 color = vec3(0.0);
                float alpha = 0.0;
                float captured = 0.0;

                float impact = length(cross(pos, eyevec));
                float along = dot(pos, eyevec);

                if (impact < ENTRY_R && along < 0.0) {
                    float tEnter = -along - sqrt(max(0.0, ENTRY_R * ENTRY_R - impact * impact));
                    float stepLen = FAR / float(ITERATIONS);
                    float dither = fract(hash21(gl_FragCoord.xy) + taa.w);
                    vec3 raypos = pos + eyevec * (tEnter + dither * stepLen);
                    float r2 = dot(raypos, raypos);

                    for (int i = 0; i < ITERATIONS; i++) {
                        float sd = sqrt(r2);
                        eyevec = normalize(eyevec - raypos * (WARP_K / ((r2 + 0.000001) * max(sd, 1e-4))));
                        raypos += eyevec * stepLen;

                        r2 = dot(raypos, raypos);
                        float r = sqrt(r2);
                        GasDisc(color, alpha, raypos, r, time, mainColor, aa, eyevec);
                        Haze(color, raypos, r2, alpha, mainColor);

                        captured = max(captured, 1.0 - smoothstep(0.32, 0.85, r));
                        if (alpha > 0.995) {
                            break;
                        }
                        if (r2 > 49.0 && dot(raypos, eyevec) > 0.0) {
                            break;
                        }
                        if (r2 < 0.0625) {
                            break;
                        }
                    }
                }

                color *= EXPOSURE;

                float ring = pow(clamp(captured * (1.0 - captured) * 4.0, 0.0, 1.0), 6.0);
                color += mix(vec3(1.0), mainColor, 0.35) * ring * 0.6 * (1.0 - alpha);

                color *= brightness;

                vec3 cur = encodeHDR(color);

                if (taa.z > 0.5) {
                    vec4 pc = prevViewProj * vec4(rd, 0.0);
                    if (pc.w > 1e-5) {
                        vec2 prevUV = pc.xy / pc.w * 0.5 + 0.5;
                        vec2 guard = 1.5 / res;
                        if (all(greaterThan(prevUV, guard)) && all(lessThan(prevUV, 1.0 - guard))) {
                            vec3 hist = clamp(sampleHistory(prevUV, res), cur - 0.35, cur + 0.35);
                            cur = mix(cur, hist, TAA_BLEND);
                        }
                    }
                }

                cur += (fract(hash21(gl_FragCoord.xy + 13.7) + taa.w) - 0.5) / 255.0;

                fragColor = vec4(max(cur, vec3(0.0)), 1.0);
            }
            """;

    private static final String COMPOSITE = """
            #version 150

            uniform sampler2D DepthTex;
            uniform sampler2D Sky;
            uniform sampler2D Bloom0;
            uniform sampler2D Bloom1;
            uniform sampler2D Bloom2;
            uniform sampler2D Bloom3;
            uniform sampler2D Bloom4;
            uniform sampler2D Bloom5;

            layout(std140) uniform SkyParams {
                mat4 invViewProj;
                vec4 misc;
                vec4 skyColor;
                vec4 skyColor2;
                vec4 taa;
                mat4 prevViewProj;
                vec4 skyExtra;
            };

            in vec2 texCoord;
            out vec4 fragColor;

            const float BLOOM_STRENGTH = 0.13;

            vec3 nmzHash33(vec3 q) {
                uvec3 p = uvec3(ivec3(q));
                p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
                p = p.yzx * (p.zxy ^ (p >> 3U));
                return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
            }

            vec3 bhStars(vec3 dir, float time) {
                vec3 c = vec3(0.0);
                vec3 p = dir * 42.0;
                float dens = 0.05;
                float amp = 1.0;
                for (int i = 0; i < 4; i++) {
                    vec3 id = floor(p);
                    vec3 q = fract(p) - 0.5;
                    vec3 rn = nmzHash33(id);
                    float core = smoothstep(0.30, 0.0, length(q));
                    float hit = step(rn.x, dens);
                    float tw = 0.78 + 0.22 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
                    vec3 tint = mix(vec3(1.0, 0.74, 0.50), vec3(0.72, 0.86, 1.0), rn.y);
                    c += hit * core * core * tint * (0.35 + 0.65 * rn.z) * tw * amp;
                    p = p * 1.63 + 17.0;
                    dens *= 0.62;
                    amp *= 0.78;
                }
                return c * 0.42;
            }

            vec3 decodeHDR(vec3 c) {
                c = pow(min(c, vec3(0.9975)), vec3(2.2));
                return c / max(1.0 - c, vec3(0.0055));
            }

            vec4 cubic(float x) {
                float x2 = x * x;
                float x3 = x2 * x;
                vec4 w;
                w.x = -x3 + 3.0 * x2 - 3.0 * x + 1.0;
                w.y = 3.0 * x3 - 6.0 * x2 + 4.0;
                w.z = -3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0;
                w.w = x3;
                return w / 6.0;
            }

            vec3 bicubic(sampler2D tex, vec2 uv) {
                vec2 resolution = vec2(textureSize(tex, 0));
                vec2 coord = uv * resolution;

                float fx = fract(coord.x);
                float fy = fract(coord.y);
                coord.x -= fx;
                coord.y -= fy;
                fx -= 0.5;
                fy -= 0.5;

                vec4 xcubic = cubic(fx);
                vec4 ycubic = cubic(fy);

                vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
                vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
                vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / s;

                vec3 s0 = texture(tex, vec2(offset.x, offset.z) / resolution).rgb;
                vec3 s1 = texture(tex, vec2(offset.y, offset.z) / resolution).rgb;
                vec3 s2 = texture(tex, vec2(offset.x, offset.w) / resolution).rgb;
                vec3 s3 = texture(tex, vec2(offset.y, offset.w) / resolution).rgb;

                float sx = s.x / (s.x + s.y);
                float sy = s.z / (s.z + s.w);

                return mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
            }

            vec3 blackHole() {
                vec3 color = decodeHDR(bicubic(Sky, texCoord));

                vec3 bloom = decodeHDR(bicubic(Bloom0, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom1, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom2, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom3, texCoord)) * 0.90;
                bloom += decodeHDR(bicubic(Bloom4, texCoord)) * 0.80;
                bloom += decodeHDR(bicubic(Bloom5, texCoord)) * 0.65;

                color += bloom * BLOOM_STRENGTH;

                color = pow(color, vec3(1.5));
                color = color / (1.0 + color);
                color = pow(color, vec3(1.0 / 1.5));
                color = color * color * (3.0 - 2.0 * color);
                color = pow(color, vec3(1.3, 1.20, 1.0));
                color = clamp(color * 1.01, 0.0, 1.0);
                color = pow(color, vec3(0.7 / 2.2));

                vec2 ndc = texCoord * 2.0 - 1.0;
                vec4 pFar = invViewProj * vec4(ndc, 1.0, 1.0);
                vec4 pNear = invViewProj * vec4(ndc, -1.0, 1.0);
                vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);

                vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
                float cosT = clamp(dot(rd, bhDir), -1.0, 1.0);
                float theta = acos(cosT);
                vec3 toHole = bhDir - cosT * rd;
                float tl = length(toHole);
                vec3 rdL = rd;
                if (tl > 1e-4) {
                    rdL = normalize(rd + toHole / tl * (0.010 / max(theta, 0.05)));
                }

                float lum = dot(color, vec3(0.299, 0.587, 0.114));
                vec3 stars = bhStars(rdL, misc.x) * smoothstep(0.10, 0.17, theta);
                color += stars * misc.w * clamp(1.0 - lum * 2.4, 0.0, 1.0);

                return color;
            }

            void main() {
                float depth = texture(DepthTex, texCoord).r;
                if (depth < 0.9999) {
                    discard;
                }

                vec2 dt = 1.0 / vec2(textureSize(DepthTex, 0));
                float dmin = min(
                    min(texture(DepthTex, texCoord + vec2(dt.x, 0.0)).r, texture(DepthTex, texCoord - vec2(dt.x, 0.0)).r),
                    min(texture(DepthTex, texCoord + vec2(0.0, dt.y)).r, texture(DepthTex, texCoord - vec2(0.0, dt.y)).r)
                );
                float edgeDim = 1.0 - 0.6 * step(dmin, 0.9998);

                vec3 result;
                if (skyColor2.w > 0.5 && skyColor2.w < 1.5) {
                    result = blackHole();
                } else {
                    vec3 sky = texture(Sky, texCoord).rgb;
                    vec3 bloom = texture(Bloom0, texCoord).rgb * 1.00
                               + texture(Bloom1, texCoord).rgb * 0.90
                               + texture(Bloom2, texCoord).rgb * 0.75;
                    result = sky + bloom * 0.34;
                }

                fragColor = vec4(result * edgeDim, 1.0);
            }
            """;
}
