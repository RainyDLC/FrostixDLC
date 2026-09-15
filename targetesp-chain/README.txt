РЕЖИМ "ЦЕПЬ" ДЛЯ TARGET ESP (порт из Relake Client, MC 1.21.11, Fabric, официальные Mojang-маппинги)
===============================================================================================

Что делает: два наклонных вращающихся кольца из звеньев цепи вокруг цели
(наклон покачивается 0-20 градусов, фаза бежит по движению цели).
Доп. настроек НЕ требует: цвет/прозрачность берутся из контекста, при уроне краснеет.
Глоу-цилиндры УБРАНЫ (давали белый "трейл") - остались только сами цепи.

ФАЙЛЫ В ПАПКЕ
--------------
ChainTargetEspRenderer.java - сам рендер режима. Вызывать каждый кадр:
    ChainTargetEspRenderer.render(stack, provider, context);
    ChainTargetEspRenderer.endBatch(provider);
  где stack - PoseStack, уже сдвинутый к цели относительно камеры
  (stack.translate(targetPos - cameraPos)), provider - mc.renderBuffers().bufferSource().
TargetEspRenderContext.java - record с данными кадра:
  (LivingEntity target, float alpha, float partialTicks, long frameTimeMs,
   int primaryColor, int secondaryColor, float hurtProgress, float chainImpactProgress)
ClientPipelines.java - нужен CHAIN_ESP (RenderType для текстуры):
    provider.getBuffer(ClientPipelines.CHAIN_ESP.apply(CHAIN_TEXTURE))
  Пайплайн: шейдеры core/position_tex_color, бленд TRANSLUCENT (обычная альфа!),
  depth LEQUAL, формат POSITION_TEX_COLOR. Именно TRANSLUCENT важен: в аддитиве
  тёмные звенья гаснут, а всё вместе выжигается добела.
RenderLayerFactory.java + MixinRenderType.java - создание RenderType (миксин-Invoker
  к RenderType.create, миксин должен быть прописан в mixins.json клиента!).
RenderCompatibility.java - нужен ClientPipelines (worldBlend для остальных пайплайнов).
ColorUtil.java - используются lerpColor (красная вспышка при уроне) и withAlpha.
chain.png - текстура звеньев 512x384. Положить в:
    src/main/resources/assets/<твой_неймспейс>/textures/features/targetesp/chain.png

ВАЖНО: ЗАМЕНИТЬ НЕЙМСПЕЙС
--------------------------
В ChainTargetEspRenderer.java:
    Identifier.fromNamespaceAndPath("relake", "textures/features/targetesp/chain.png")
"relake" заменить на неймспейс своего клиента, путь должен совпадать с положением chain.png.

ПОДКЛЮЧЕНИЕ В МОДУЛЬ (пример по TargetESP.java)
------------------------------------------------
1. В ModeSetting со списком режимов добавить значение "Цепь".
2. В ветку отрисовки:
    } else if (mode.is("Цепь")) {
        ChainTargetEspRenderer.render(stack, provider, context);
        ChainTargetEspRenderer.endBatch(provider);
    }

НАСТРОЙКА ВНЕШНЕГО ВИДА (константы вверху ChainTargetEspRenderer)
------------------------------------------------------------------
TOTAL_ANGLE = 360 * 2 - длина ленты в градусах (два полных оборота на кольцо)
LINKS_STEP = 18       - шаг сегментов (сегмент = LINKS_STEP/2 = 9 градусов)
CHAIN_SIZE = 4.0F     - тайлинг текстуры (повторов на 360 градусов)
DOWN = 1.0F           - высота ленты звеньев в блоках
Кольца два, наклон второго зеркальный. Радиус = ширина цели * 1.5 * 0.7-0.75,
центр на половине роста цели минус 0.5.

АНИМАЦИЯ (getMovingValue)
---------------------------
Скорость зависит от движения цели: base = (время с запуска) * 0.08 % 360
+ motion * 360, где motion - смещение цели за тик в блоках (getX - xo и т.д.).
Стоящая цель - медленное покачивание, движущаяся - живо реагирующие цепи.
Наклон: gradusX/gradusZ = 20 * (...) - болтанка колец от 0 до 20 градусов.

ЗАМЕЧАНИЯ ИЗ ОПЫТА ПОРТА
--------------------------
- UV текстуры ОБЯЗАТЕЛЬНО держать в [0,1) через fract (семплер клампит, u > 1 даст
  размазанный край текстуры вместо повтора). В коде уже сделано.
- Аддитивный глоу-цилиндр вокруг цепей сносили: в аддитиве он превращается
  в белый "трейл", внутри которого тонут сами цепи. Если захочешь свечение -
  один тонкий слой, альфа ~0.05-0.1, и только поверх TRANSLUCENT-цепей.
- Время анимации - относительное (now - startTimeMs). Абсолютный
  System.currentTimeMillis во float квантуется (шаг 128) - анимация встанет.
