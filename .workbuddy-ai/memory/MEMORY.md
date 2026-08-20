# Project Memory — Nightix (FrostixDLC) client

## Шейдерная система (как править GLSL)
- Все клиентские шейдеры лежат НЕ в ресурсах, а в `src/client/java/ru/white/utils/render/shader/ShaderData.java` как Base64 в мапе `SOURCES` (ключ вида `"core/shader_sky|fsh"` = путь + `|vsh|`/`|fsh`).
- Шифрование: XOR с ключом `KEY = "Nightix//shader-veil//2026"` (из `ShaderStore.java`), потом Base64. Это ТОЛЬКО обфускация от grep/strings, не криптозащита. Ключ менять НЕЛЬЗЯ (иначе старые шейдеры не расшифруются); новые шейдеры шифруем ТЕМ ЖЕ ключом.
- `ShaderStore.getSource(id, type)` расшифровывает и резолвит `#moj_import`; `ShaderLoaderMixin` перехватывает загрузку шейдеров namespace `client`.
- Процедура правки шейдера: Base64-decode → XOR(KEY) → получаем GLSL → правим → XOR(KEY) обратно → Base64 → заменяем значение в `SOURCES.put(...)`. Делать скриптом (Python), не руками — иначе ошибка в Base64/XOR сломает компиляцию шейдера в рантайме.
- Рендер: `ShaderSkyRenderer` (пайплайны `client:pipeline/shader_sky` и `shader_sky_blur`) рисует skybox квадом; юниформ-буфер `ShaderSkyData` (std140): `screenTimeOpacity`(w,h,time,alpha), `primaryColor`(color1), `secondaryColor`(color2), `accentColor`(white), `params`(mode,speed,scale,intensity), `extra`(stars,vanillaSky,pad,pad). Главный фрагмент `core/shader_sky` ветвится по `params.x` (mode id): 0 Aurora,1 Night,2 Snow,3 Sky,4 Star,5 Glow.

## Добавлен режим «Plasma» в Shader Sky (2026-08-21)
- Добавлена функция `plasma()` в `core/shader_sky|fsh` (domain-warped fbm + неоновые sine-полосы, микс primary/secondary + accent), режим = `mode < 5.5 ? glow : plasma` (id 6).
- `ShaderSky.java`: ModeSetting `"Режим"` дополнен `"Plasma"`.
- `ShaderSkyRenderer.getModeId`: `case "Plasma" -> 6;`.
- Цвета берутся из `color1`/`color2` (настройки «Режим цвета»: Тема/Свой). `./gradlew compileClientJava` → BUILD SUCCESSFUL.

## ВАЖНО: ShaderStore KEY
- `ShaderStore.java` строка 30 `KEY = "Nightix//shader-veil//2026"` — это XOR-ключ обфускации шейдеров. Не менять. (В отличие от ShaderData crypto-ключа — это тот же ключ; любая правка ломает ВСЕ шейдеры.)
