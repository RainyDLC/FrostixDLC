# Atmosphere для FrostixDLC (RainyDLC / kimiko, 1.21.11 Yarn)

Скопируй src/ поверх проекта (новые файлы, ничего существующего не перезаписывается). Потом 2 правки:

1. src/main/java/rtx/kimiko/api/modules/ModuleManager.java, в init() добавь в массив модулей:
       new Atmosphere(),
   и импорт:
       import rtx.kimiko.api.modules.impl.Visuals.Atmosphere;

2. src/main/resources/kimiko.mixins.json, в "mixins" добавь:
       "AtmosphereGameRendererMixin",
       "AtmosphereHudMixin",

Модуль появится в категории Visuals.

Файлы:
- Visuals/Atmosphere.java               настройки (в стиле FogBlur: register(...), visibleWhen, fadeOutSeconds)
- Visuals/atmosphere/AtmosphereRenderer  порядок эффектов
- Visuals/atmosphere/sun/SunTracker      позиция солнца на экране, raycast перекрытия, дождь/горизонт/вода
- Visuals/atmosphere/effect/*            по классу на эффект
- Visuals/atmosphere/texture/*           процедурные текстуры (PNG не нужны)
- assets/kimiko/shaders/post + post_effect/atmosphere   шейдер аберрации (3 пресета)
- mixin/Atmosphere*Mixin                 хуки рендера (отдельно от твоих GameRendererMixin/GuiMixin)

Если компиль ругнётся, проверь эти места по маппингам:
ClientWorld#getSkyAngleRadians, Camera#getPos, Camera#getSubmersionType,
NativeImageBackedTexture(Supplier<String>, NativeImage), ShaderLoader#loadPostEffect,
DrawContext#drawTexture(RenderPipeline, ...).
