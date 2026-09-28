package rtx.kimiko.utils.render.shaders.post.customsky;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Оптимизированные версии шейдеров кастомного неба. Регистрируются ПОСЛЕ {@link CustomskyShaders}
 * и перезаписывают blackhole + composite.
 *
 * blackhole, PERF v2:
 *  - адаптивный шаг луча (мелкий у дыры и в слое диска, крупный в пустоте): 200 -> ~50-80 итераций;
 *  - ранний выход GasDisc вне слоя диска, cos/sin угла 3 раза на шаг вместо 14.
 *
 * v3, зрелищность и цвет (всё настраивается в Ambience -> Небо):
 *  - Палитра: "Тема" (как было), "Реальная" (физика: температура газа по Шакуре-Сюняеву T ~ r^-3/4,
 *    доплеровский сдвиг температуры (приближающаяся сторона бело-голубая, удаляющаяся оранжевая),
 *    гравитационное красное смещение у внутреннего края, цвет = спектр абсолютно чёрного тела),
 *    "Смешанная" (реальная физика с оттенком цвета темы, чтобы сочеталась с клиентом);
 *  - Температура диска (холодный красно-оранжевый -> горячий бело-голубой);
 *  - Релятивистские джеты из полюсов (ближний ярче из-за доплер-усиления, дальний прячется за тенью),
 *    с бегущими сгустками плазмы;
 *  - Скорость вращения, Активность (вспышки, горячие пятна, мерцание кольца, пульсации джетов),
 *    Линзирование звёзд.
 *
 * v4, "вау"-проход:
 *  - настоящая чёрная тень: blackhole пишет маску захваченных лучей в alpha, composite гасит в ней bloom;
 *  - тонкое фотонное кольцо по реальному углу отклонения луча + доплер-асимметрия и мерцание;
 *  - глубокий космос за дырой: млечный путь с пылевыми прожилками и цветная туманность (fbm),
 *    линзируется вместе со звёздами -> кольцо Эйнштейна;
 *  - звёзды трёх спектральных классов, яркие с дифракционными лучами;
 *  - джеты: спиральные жгуты вместо полос, фиолетовый хвост, начинаются от кольца, выровнены по
 *    видимому центру дыры (камера марша чуть над диском, центр смещён от bhDir);
 *  - анаморфный блик вдоль плоскости диска;
 *  - тонмаппинг с сохранением оттенка + немного насыщенности: яркий диск не выгорает в плоский белый;
 *  - дефолтная температура диска чуть теплее (золото -> бело-голубой).
 *
 * v5, PERF без потери качества:
 *  - composite (полное разрешение!): туманность = 4 fbm x 5 октав = 20 value-noise на пиксель, каждый был
 *    8 хешей + 7 mix. Теперь это одна выборка из NoiseTex (тот же сглаженный value noise, что и в диске);
 *  - марш: лучи с прицельным параметром > 7.25 не маршируются вообще (результат и раньше был ровно 0);
 *  - марш: в пустой оболочке 7 < r < 8 шаг сразу до края диска (с тем же dither);
 *  - sqrt(r2) один раз за шаг вместо двух.
 *
 * v6, шахматный марш:
 *  - каждый кадр маршируется только половина пикселей (шахматка, чётность меняется каждый кадр),
 *    вторая половина берётся из истории TAA с точной репроекцией (небо на бесконечности, репроекция
 *    через prevViewProj без ошибок параллакса). TAA и так смешивает 85% истории, так что каждый пиксель
 *    обновляется раз в 2 кадра — на глаз разницы нет, а самый дорогой проход стал в 2 раза дешевле.
 *    Чётность кадра берётся из знака TAA-джиттера по X (halton base 2: чётный кадр >= 0, нечётный < 0),
 *    поэтому Java-сторона не менялась. Без валидной истории (первый кадр, край экрана) — полный марш.
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
                vec4 bhParams;
            };

            in vec2 texCoord;
            out vec4 fragColor;

            const int ITERATIONS = 200;
            const float FAR = 16.0;
            const float STEP = FAR / float(ITERATIONS);
            const float ENTRY_R = 8.0;
            const float CAM_DIST = 14.0;
            const float INCL = 0.10;
            const float EXPOSURE = 0.024;
            const float THEME_MIX = 0.5;
            const float ZOOM = 2.4;
            const float TAA_BLEND = 0.85;
            const float SPIN = 0.16;
            const float TAU = 6.28318531;

            const float R_FINE = 2.5;
            const float Y_BAND = 0.30;
            const float STEP_MAX = 0.45;

            // за DISC_OUTER ничего не рисуется (GasDisc: r > 7 -> return, Haze: r > 6 -> return)
            const float DISC_OUTER = 7.0;
            // лучи с прицельным параметром больше этого не доходят до r < 7 даже с учётом изгиба (~0.02)
            const float IMPACT_CULL = 7.25;

            const float DISC_INNER = 0.55;
            const float DISC_WIDTH = 5.3;
            const float DISC_BAND = 0.29;
            const float PCURVE_K = 10.349099641;
            const float WARP_K = 5.0 / 200.0;
            const float HAZE_K = 2.9 / 200.0;
            const float COVER_K = 1200.0 / 200.0;
            const float R_SCHW = 0.25;

            // per-pixel настройки (заполняются в main)
            float gThemeW;
            float gTin;
            float gSpin;
            float gAct;
            float gFlare;

            vec3 hsv2rgb(vec3 c) {
                vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
                return c.z * mix(vec3(1.0), rgb, c.y);
            }

            // спектр абсолютно чёрного тела, T в кельвинах -> линейный RGB (макс. компонента = 1)
            vec3 blackbody(float T) {
                float t = max(T, 100.0) / 100.0;
                vec3 c;
                if (t <= 66.0) {
                    c.r = 1.0;
                    c.g = clamp(0.390081579 * log(t) - 0.631841444, 0.0, 1.0);
                    c.b = t <= 19.0 ? 0.0 : clamp(0.543206789 * log(t - 10.0) - 1.196254089, 0.0, 1.0);
                } else {
                    c.r = clamp(1.292936186 * pow(t - 60.0, -0.1332047592), 0.0, 1.0);
                    c.g = clamp(1.129890861 * pow(t - 60.0, -0.0755148492), 0.0, 1.0);
                    c.b = 1.0;
                }
                return c * c;
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

            vec3 discCoord(vec2 cs, float rad, float freq) {
                return vec3(cs * freq, rad * freq);
            }

            float octave(vec2 cs, float rad, float freq, float aa) {
                float w = clamp(1.5 - aa * freq * 1.5, 0.0, 1.0);
                if (w <= 0.01) {
                    return 0.5;
                }
                return mix(0.5, noise(discCoord(cs, rad, freq)) * 0.5 + 0.5, w);
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

            void Haze(inout vec3 color, vec3 pos, float r2, float alpha, vec3 hazeColor, float weight) {
                if (r2 > 36.0 || r2 < 0.25) {
                    return;
                }
                vec2 q = vec2(length(pos.xz) - 1.0, pos.y - 0.05);
                float torusDist = abs(length(q) - 0.01);
                float bloomDisc = 1.0 / (torusDist * torusDist + 0.001);
                color += hazeColor * (bloomDisc * HAZE_K * weight * (1.0 - alpha));
            }

            void GasDisc(inout vec3 color, inout float alpha, vec3 pos, float distFromCenter, float time, vec3 mainColor,
                         float aa, vec3 eyevec) {
                float distFromDisc = pos.y;
                if (distFromCenter > DISC_OUTER || abs(distFromDisc) > DISC_BAND) {
                    return;
                }
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
                float og = 1.0 - radialGradient;
                float dustGlow = 1.0 / (og * og * 290.0 + 0.002);
                float bloomK = bloomFactor * sqrt(bloomFactor);

                float ang = atan(-pos.x, -pos.z);
                float rad = (distFromCenter * 1.5 + 0.55 + distFromDisc * 1.5) * 0.95 + time * 0.012;

                float om = 3.2 / max(distFromCenter, 0.75);
                float omega = SPIN * gSpin * om * sqrt(om);
                float angA = ang + mod(time * omega, TAU);
                float angB = ang + mod(time * omega * 0.45, TAU);
                vec2 csA = vec2(cos(angA), sin(angA)) * 1.425;
                vec2 csB = vec2(cos(angB), sin(angB)) * 1.425;

                float bandAng = ang + mod(time * omega * 0.5, TAU);
                vec2 csBand = vec2(cos(bandAng), sin(bandAng)) * 1.425;
                float band = noise(discCoord(csBand, rad, 1.35)) * 0.5 + 0.5;
                float grain = noise(discCoord(csBand, rad + 70.0, 3.46)) * 0.5 + 0.5;

                vec3 litT = vec3(0.0);
                vec3 radT = vec3(1.0);
                vec3 texT = vec3(0.0);
                if (gThemeW > 0.001) {
                    vec3 dopTint = mix(vec3(1.30, 0.72, 0.45), vec3(0.80, 0.92, 1.45), clamp((dop - 0.7) * 0.9, 0.0, 1.0));
                    litT = mainColor * dopTint;
                    radT = mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(radialGradient * radialGradient))
                         * mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(sqrt(radialGradient)));
                    vec3 tc = mix(vec3(0.95, 0.55, 0.26), vec3(0.42, 0.60, 1.0), band) * (0.45 + 0.80 * grain);
                    texT = tc * tc * 4.0;
                }
                vec3 litR = vec3(0.0);
                vec3 texR = vec3(0.0);
                if (gThemeW < 0.999) {
                    float rr = max(distFromCenter, DISC_INNER);
                    float g = 1.0 / dx;
                    float grav = sqrt(max(1.0 - R_SCHW / rr, 0.05));
                    float T = gTin * pow(DISC_INNER / rr, 0.75) * (0.85 + 0.30 * band) * g * grav;
                    litR = blackbody(T);
                    float tg = 0.45 + 0.80 * grain;
                    texR = vec3(tg * tg * 3.0);
                }
                vec3 lit = mix(litR, litT, gThemeW);
                vec3 radTint = mix(vec3(1.25), radT, gThemeW);
                vec3 texCol = mix(texR, texT, gThemeW);

                vec3 dustColor = lit * (dop * dustGlow * 8.2);
                vec3 b = lit * (dop * bloomK) * radTint;
                dustColor = mix(dustColor, b * 150.0, mixW);

                float n1 = 1.0;
                n1 *= octave(csA, rad, 3.0, aa);
                n1 *= octave(csB, rad, 6.0, aa);
                n1 *= octave(csA, rad, 12.0, aa);
                n1 *= octave(csB, rad, 24.0, aa);

                float n2 = 2.0;
                float rad2 = rad + 30.0;
                n2 *= octave(csB, rad2, 3.0, aa);
                n2 *= octave(csA, rad2, 6.0, aa);
                n2 *= octave(csB, rad2, 12.0, aa);
                n2 *= octave(csA, rad2, 24.0, aa);
                n2 *= octave(csB, rad2, 48.0, aa);
                n2 *= octave(csA, rad2, 92.0, aa);

                dustColor *= n1 * 0.998 + 0.002;
                coverage *= n2;

                dustColor *= texCol;

                float arm = 0.5 + 0.5 * cos(2.0 * ang + 2.6 * log(max(distFromCenter, 0.3)) + time * SPIN * gSpin * 4.5);
                dustColor *= 0.40 + 1.05 * arm * arm;
                coverage *= 0.78 + 0.22 * arm;

                float dAngA = abs(mod(angA - 1.35, TAU) - 3.14159265);
                float dRadA = distFromCenter - 1.5;
                float dAngB = abs(mod(angB - 4.2, TAU) - 3.14159265);
                float dRadB = distFromCenter - 2.7;
                float actK = 0.25 + 0.75 * gAct;
                float hot = exp(-(dAngA * dAngA * 0.5 + dRadA * dRadA * 7.0)) * 3.4 * gFlare * actK
                          + exp(-(dAngB * dAngB * 0.9 + dRadB * dRadB * 5.0)) * 1.8 * actK;
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
                vec2 res = vec2(textureSize(History, 0));
                vec3 rd = rayDir(texCoord);

                // PERF v6: шахматный марш. Половина пикселей в этом кадре берётся из истории с точной
                // репроекцией неба (оно на бесконечности), вторая половина маршируется. Чётность кадра = знак
                // TAA-джиттера по X (halton base 2). Без валидной истории/за краем — обычный полный марш.
                if (taa.z > 0.5) {
                    int parity = taa.x < -1e-4 ? 1 : 0;
                    ivec2 fc = ivec2(gl_FragCoord.xy);
                    if (((fc.x + fc.y) & 1) == parity) {
                        vec4 hc = prevViewProj * vec4(rd, 0.0);
                        if (hc.w > 1e-5) {
                            vec2 prevUV = hc.xy / hc.w * 0.5 + 0.5;
                            vec2 guard = 1.5 / res;
                            if (all(greaterThan(prevUV, guard)) && all(lessThan(prevUV, 1.0 - guard))) {
                                vec3 hist = sampleHistory(prevUV, res);
                                float histShadow = textureLod(History, prevUV, 0.0).a;
                                fragColor = vec4(hist, histShadow);
                                return;
                            }
                        }
                    }
                }

                float time = misc.x;
                float brightness = misc.w;

                float palette = skyExtra.y;
                gThemeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
                gTin = mix(3200.0, 16000.0, clamp(skyExtra.z, 0.0, 1.0));
                gAct = max(bhParams.y, 0.0);
                gSpin = max(bhParams.z, 0.0);

                vec3 rdJitter = rayDir(texCoord + taa.xy / res);

                vec3 bhDir = normalize(vec3(0.34, 0.62, 0.71));
                vec3 upRef = vec3(0.0, 1.0, 0.0);
                vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
                vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
                vec3 eX = normalize(cross(eY, bhDir));
                vec3 eZ = cross(eX, eY);

                vec3 tint = skyTint(atan(rd.z, rd.x), 0.5, time);
                vec3 mainColor = mix(vec3(1.0), tint, THEME_MIX);

                vec3 hazeColor = mainColor;
                vec3 ringColor = mix(vec3(1.0), mainColor, 0.35);
                if (gThemeW < 0.999) {
                    hazeColor = mix(blackbody(gTin * 0.55) * 1.1, mainColor, gThemeW);
                    ringColor = mix(mix(vec3(1.0), blackbody(gTin * 0.8), 0.7), ringColor, gThemeW);
                }

                float fl = 0.5 + 0.5 * sin(time * 0.23);
                fl *= fl; fl *= fl; fl *= fl; fl *= fl;
                gFlare = 1.0 + 1.6 * fl * gAct;

                vec3 camWorld = -bhDir * CAM_DIST;
                vec3 pos = vec3(dot(camWorld, eX), dot(camWorld, eY), dot(camWorld, eZ));
                vec3 eyevec = toLocal(rdJitter, eX, eY, eZ);
                vec3 localRd = toLocal(rd, eX, eY, eZ);
                float pixAngle = length(toLocal(rayDir(texCoord + vec2(1.0, 0.0) / res), eX, eY, eZ) - localRd);
                float aa = pixAngle * CAM_DIST * 1.425;

                vec3 color = vec3(0.0);
                float alpha = 0.0;
                float captured = 0.0;

                vec3 eye0 = eyevec;
                float fell = 0.0;
                float rmin = 1e3;
                float impact = length(cross(pos, eyevec));
                float along = dot(pos, eyevec);

                // PERF: лучи с impact >= IMPACT_CULL и раньше давали ровно 0, поэтому их просто не маршируем.
                if (impact < IMPACT_CULL && along < 0.0) {
                    float tEnter = -along - sqrt(max(0.0, ENTRY_R * ENTRY_R - impact * impact));
                    float dither = fract(hash21(gl_FragCoord.xy) + taa.w);
                    vec3 raypos = pos + eyevec * (tEnter + dither * STEP);
                    float r2 = dot(raypos, raypos);
                    float r = sqrt(r2);
                    float bandEdge = Y_BAND + dither * STEP;
                    // край пустой оболочки с тем же dither, чтобы сетка сэмплов в диске не выстраивалась в кольца
                    float shellEdge = DISC_OUTER + dither * STEP;
                    float traveled = 0.0;

                    for (int i = 0; i < ITERATIONS; i++) {
                        float sd = r;

                        float s = STEP;
                        if (sd > R_FINE) {
                            float ay = abs(raypos.y);
                            float sy = ay > bandEdge ? 0.9 * (ay - bandEdge) / max(abs(eyevec.y), 0.25) : 0.0;
                            // при r > 7 ничего не рисуется, можно шагать до края диска даже внутри слоя
                            sy = max(sy, sd - shellEdge);
                            s = clamp(min(sd - R_FINE, sy), STEP, STEP_MAX);
                        }
                        s = min(s, FAR - traveled);
                        float w = s / STEP;

                        eyevec = normalize(eyevec - raypos * (WARP_K * w / ((r2 + 0.000001) * max(sd, 1e-4))));
                        raypos += eyevec * s;
                        traveled += s;

                        r2 = dot(raypos, raypos);
                        r = sqrt(r2);
                        GasDisc(color, alpha, raypos, r, time, mainColor, aa, eyevec);
                        Haze(color, raypos, r2, alpha, hazeColor, w);

                        captured = max(captured, 1.0 - smoothstep(0.32, 0.85, r));
                        rmin = min(rmin, r);
                        if (alpha > 0.995) {
                            break;
                        }
                        if (r2 > 49.0 && dot(raypos, eyevec) > 0.0) {
                            break;
                        }
                        if (r2 < 0.0625) {
                            fell = 1.0;
                            break;
                        }
                        if (traveled >= FAR - 1e-4) {
                            break;
                        }
                    }
                }

                color *= EXPOSURE;

                float rq = clamp(captured * (1.0 - captured) * 4.0, 0.0, 1.0);
                float rq2 = rq * rq;
                float ring6 = rq2 * rq2 * rq2;
                float ringAng = atan(localRd.y, localRd.x);
                float ringSide = localRd.x / max(length(localRd.xy), 1e-4);
                float shimmer = 1.0 - 0.12 * gAct + 0.12 * gAct * sin(ringAng * 5.0 - time * 1.3) * sin(ringAng * 3.0 + time * 0.7);
                // мягкое гало вокруг тени (как раньше, но слабее, чтобы тень была глубже)
                color += ringColor * (ring6 * 0.30) * (1.0 + 0.4 * ringSide) * shimmer * (1.0 - alpha);

                // фотонное кольцо: лучи, которые обогнули дыру почти по кругу и вырвались, дают тонкое яркое кольцо
                float bend = acos(clamp(dot(eye0, eyevec), -1.0, 1.0));
                float esc = 1.0 - fell;
                float pr = clamp((bend - 0.9) / 1.35, 0.0, 1.0);
                float photon = pr * pr * pr * esc;          // тонкое яркое кольцо у самого края тени
                float sub = pr * esc * 0.18;                // мягкий ореол вокруг него
                float beam = 1.0 + 0.85 * ringSide;                    // доплер: приближающаяся сторона ярче
                float flick = 1.0 + 0.18 * gAct * sin(ringAng * 7.0 - time * 2.2) * sin(ringAng * 2.0 + time * 0.9);
                vec3 photonCol = mix(ringColor, vec3(1.0), 0.35);
                color += photonCol * (photon * 1.9 + sub) * beam * flick * (1.0 - alpha);

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
                // alpha = маска тени (для composite: там гасим bloom, чтобы тень была по-настоящему чёрной)
                fragColor = vec4(max(cur, vec3(0.0)), fell * (1.0 - alpha));
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
            uniform sampler2D NoiseTex;

            layout(std140) uniform SkyParams {
                mat4 invViewProj;
                vec4 misc;
                vec4 skyColor;
                vec4 skyColor2;
                vec4 taa;
                mat4 prevViewProj;
                vec4 skyExtra;
                vec4 bhParams;
            };

            in vec2 texCoord;
            out vec4 fragColor;

            const float BLOOM_STRENGTH = 0.13;
            const float STAR_LENS_E2 = 0.035;
            const float STAR_SWIRL = 0.010;
            const float NEBULA = 1.0;
            const float INCL = 0.10;
            const float ZOOM = 2.4;

            vec3 gJetAxis;

            // видимый центр дыры: камера марша чуть над диском, поэтому центр смещён от bhDir
            vec3 holeCenter(vec3 bhDir) {
                vec3 upRef = vec3(0.0, 1.0, 0.0);
                vec3 tang = normalize(upRef - dot(upRef, bhDir) * bhDir);
                vec3 eY = normalize(tang * cos(INCL) - bhDir * sin(INCL));
                vec3 eX = normalize(cross(eY, bhDir));
                vec3 eZ = cross(eX, eY);
                vec3 c = normalize(eY * (-ZOOM * sin(INCL)) + eZ * cos(INCL));
                gJetAxis = normalize(eY - dot(eY, c) * c);
                return c;
            }

            vec3 nmzHash33(vec3 q) {
                uvec3 p = uvec3(ivec3(q));
                p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
                p = p.yzx * (p.zxy ^ (p >> 3U));
                return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
            }

            // PERF v5: сглаженный value noise [0..1] одной выборкой из NoiseTex вместо 8 хешей + 7 mix
            float vnoise(vec3 x) {
                vec3 p = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                vec2 uv = (p.xy + vec2(37.0, 17.0) * p.z) + f.xy;
                vec2 rg = textureLod(NoiseTex, (uv + 0.5) / 256.0, 0.0).yx;
                return mix(rg.x, rg.y, f.z);
            }

            float fbm(vec3 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 5; i++) {
                    v += a * vnoise(p);
                    p = p * 2.03 + vec3(1.7, 9.2, 4.1);
                    a *= 0.5;
                }
                return v;
            }

            // глубокий космос: млечный путь с пылевыми прожилками + цветные облака туманности
            vec3 nebula(vec3 dir, float time, vec3 themeA, vec3 themeB, float themeW) {
                vec3 gN = normalize(vec3(0.25, 0.35, -0.90));
                float gl = dot(dir, gN);
                float band = exp(-gl * gl * 9.0);
                vec3 q = dir * 2.6 + vec3(0.0, 0.0, time * 0.004);
                float warp = fbm(q * 1.3);
                float n = fbm(q + warp * 1.4);
                float n2 = fbm(q * 2.4 + 11.0 + warp);
                float dust = smoothstep(0.42, 0.72, fbm(q * 3.4 + 5.0 + warp * 0.8));

                vec3 cA = mix(vec3(0.95, 0.42, 0.62), themeA, themeW);
                vec3 cB = mix(vec3(0.25, 0.48, 1.00), themeB, themeW);
                vec3 cC = vec3(1.00, 0.72, 0.45);

                float cloud = smoothstep(0.40, 0.85, n);
                vec3 col = mix(cB, cA, smoothstep(0.35, 0.75, n2)) * cloud * cloud * 0.20;
                col += cC * pow(max(n - 0.45, 0.0) * 2.0, 3.0) * 0.06;
                vec3 milky = mix(vec3(0.60, 0.62, 0.75), cC, 0.35) * band * (0.35 + 0.65 * n) * 0.12;
                col += milky;
                col *= 1.0 - dust * (0.55 + 0.4 * band);
                return col;
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
                    float dq = length(q);
                    float core = smoothstep(0.30, 0.0, dq);
                    float hit = step(rn.x, dens);
                    float tw = 0.72 + 0.28 * sin(time * (0.6 + rn.z * 1.3) + rn.y * 47.0);
                    vec3 tint = rn.y < 0.33 ? vec3(1.0, 0.70, 0.45) : (rn.y < 0.8 ? vec3(0.95, 0.95, 1.0) : vec3(0.62, 0.78, 1.0));
                    float bright = step(0.82, rn.z) * step(float(i), 1.5);
                    float spikes = bright * (exp(-abs(q.x) * 60.0) + exp(-abs(q.y) * 60.0)) * smoothstep(0.5, 0.0, dq) * 0.35;
                    c += hit * (core * core + spikes) * tint * (0.35 + 0.65 * rn.z) * (1.0 + bright * 1.5) * tw * amp;
                    p = p * 1.63 + 17.0;
                    dens *= 0.62;
                    amp *= 0.78;
                }
                return c * 0.42;
            }

            // релятивистские джеты вдоль оси диска (аналитически, в касательной плоскости к направлению на дыру)
            vec3 jetGlow(vec3 rd, vec3 bhDir, float cosT, float theta, float time) {
                vec3 jd = gJetAxis;
                vec3 sd = cross(bhDir, jd);
                vec3 p = rd / cosT - bhDir;
                float u = dot(p, sd);
                float v = dot(p, jd);
                float av = abs(v);

                float w = 0.0035 + av * 0.075;
                float uu = u * u / (w * w);
                float core = exp(-uu);
                float halo = exp(-uu * 0.0625) * 0.18;
                float len = exp(-av * 3.2) * smoothstep(0.09, 0.20, av);

                float act = max(bhParams.y, 0.0);
                // спиральные жгуты плазмы вместо полосатых сгустков
                float helix = 0.5 + 0.5 * sin(u / w * 2.2 + av * 38.0 - time * (2.2 + 1.5 * act));
                float knots = 0.55 + 0.45 * smoothstep(0.2, 0.9, sin(av * 16.0 - time * (1.4 + act)) * 0.5 + 0.5);
                float flick = 1.0 + 0.15 * act * sin(time * 2.1 + v * 9.0);
                float beam = v > 0.0 ? 1.0 : 0.4 * smoothstep(0.10, 0.17, theta);
                core *= 0.65 + 0.35 * helix;
                halo = exp(-uu * 0.03) * 0.22 + halo * 0.5;

                float I = (core * knots * flick + halo) * len * beam;

                vec3 realCol = vec3(0.55, 0.72, 1.0);
                vec3 themeCol = mix(vec3(1.0), skyColor.rgb, 0.5);
                float palette = skyExtra.y;
                float themeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
                vec3 col = mix(realCol, themeCol, themeW);
                col = mix(col, vec3(0.75, 0.45, 1.0), smoothstep(0.05, 0.4, av) * 0.45 * (1.0 - themeW));
                col = mix(col, vec3(1.0), core * exp(-av * 12.0) * 0.6);
                return col * I * 0.9;
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
                vec2 ndc = texCoord * 2.0 - 1.0;
                vec4 pFar = invViewProj * vec4(ndc, 1.0, 1.0);
                vec4 pNear = invViewProj * vec4(ndc, -1.0, 1.0);
                vec3 rd = normalize(pFar.xyz / pFar.w - pNear.xyz / pNear.w);

                vec3 bhDir = holeCenter(normalize(vec3(0.34, 0.62, 0.71)));
                float cosT = clamp(dot(rd, bhDir), -1.0, 1.0);
                float theta = acos(cosT);

                vec3 color = decodeHDR(bicubic(Sky, texCoord));
                float shadow = texture(Sky, texCoord).a;

                vec3 bloom = decodeHDR(bicubic(Bloom0, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom1, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom2, texCoord)) * 1.00;
                bloom += decodeHDR(bicubic(Bloom3, texCoord)) * 0.90;
                bloom += decodeHDR(bicubic(Bloom4, texCoord)) * 0.80;
                bloom += decodeHDR(bicubic(Bloom5, texCoord)) * 0.65;

                // внутри тени bloom почти гасим: горизонт событий должен быть чёрным провалом
                color += bloom * BLOOM_STRENGTH * (1.0 - 0.85 * shadow);
                color *= 1.0 - 0.6 * shadow;

                float jets = skyExtra.w;
                if (jets > 0.001 && cosT > 0.5) {
                    color += jetGlow(rd, bhDir, cosT, theta, misc.x) * (jets * misc.w * (1.0 - 0.9 * shadow));
                }

                // анаморфный блик: тонкая горизонтальная полоса света через ядро, как в кино
                if (cosT > 0.2) {
                    vec3 jd = gJetAxis;
                    vec3 sdir = cross(bhDir, jd);
                    vec3 p = rd / cosT - bhDir;
                    float su = dot(p, sdir);
                    float sv = dot(p, jd);
                    float streak = exp(-sv * sv * 9000.0) * exp(-abs(su) * 5.0) * smoothstep(0.05, 0.14, abs(su));
                    float pulse = 0.85 + 0.15 * sin(misc.x * 0.7);
                    vec3 streakCol = mix(vec3(0.55, 0.70, 1.0), skyColor.rgb, skyExtra.y < 0.5 ? 0.6 : 0.2);
                    color += streakCol * streak * 0.9 * pulse * misc.w;
                }

                // тонмаппинг с сохранением оттенка: яркие места не выгорают в плоский белый
                float peak = max(max(color.r, color.g), color.b);
                vec3 hue = color / max(peak, 1e-5);
                float tp = pow(peak, 1.5);
                tp = tp / (1.0 + tp);
                tp = pow(tp, 1.0 / 1.5);
                tp = tp * tp * (3.0 - 2.0 * tp);
                vec3 filmic = pow(color, vec3(1.5));
                filmic = filmic / (1.0 + filmic);
                filmic = pow(filmic, vec3(1.0 / 1.5));
                filmic = filmic * filmic * (3.0 - 2.0 * filmic);
                color = mix(filmic, hue * tp, 0.55);
                color = pow(max(color, vec3(0.0)), vec3(1.22, 1.14, 1.0));
                color = clamp(color * 1.02, 0.0, 1.0);
                color = pow(color, vec3(0.7 / 2.2));
                // чуть больше насыщенности, чтобы диск переливался цветом, а не был просто белым
                float gray = dot(color, vec3(0.299, 0.587, 0.114));
                color = clamp(mix(vec3(gray), color, 1.18), 0.0, 1.0);

                float lum = dot(color, vec3(0.299, 0.587, 0.114));
                float starMask = smoothstep(0.10, 0.17, theta) * clamp(1.0 - lum * 2.4, 0.0, 1.0) * misc.w;
                if (starMask > 0.002) {
                    float lens = max(bhParams.x, 0.0);
                    vec3 rdL = rd;
                    float mag = 1.0;
                    vec3 toHole = bhDir - cosT * rd;
                    float tl = length(toHole);
                    if (tl > 1e-4 && lens > 0.001) {
                        float e2 = STAR_LENS_E2 * lens;
                        float th = max(theta, 0.05);
                        rdL = normalize(rd + toHole / tl * (e2 / th));
                        float phi = STAR_SWIRL * lens / (theta * theta + 0.008);
                        float cp = cos(phi);
                        float sp = sin(phi);
                        rdL = rdL * cp + cross(bhDir, rdL) * sp + bhDir * (dot(bhDir, rdL) * (1.0 - cp));
                        mag = clamp(1.0 + 0.5 * e2 / (th * th), 1.0, 2.2);
                    }
                    float neb = NEBULA;
                    vec3 bg = bhStars(rdL, misc.x);
                    if (neb > 0.001) {
                        float palette = skyExtra.y;
                        float themeW = palette < 0.5 ? 1.0 : (palette < 1.5 ? 0.0 : 0.35);
                        bg += nebula(rdL, misc.x, skyColor.rgb, skyColor2.rgb, themeW) * neb;
                    }
                    // кольцо Эйнштейна: фон вокруг дыры усилен линзой
                    color += bg * (starMask * mag);
                }
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
