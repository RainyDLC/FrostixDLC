package rtx.kimiko.api.modules.impl.Visuals;

import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.ModuleManager;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereRenderer;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.ChromaticStrength;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ColorSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.modules.settings.impl.NumberSetting;

/**
 * Atmosphere: cinematic "camera lens" simulation (lens flare, lens dirt, chromatic aberration, vignette).
 * The module only owns settings; all rendering lives in {@link AtmosphereRenderer}.
 */
public final class Atmosphere extends Module {

    private static final String CA_SUBTLE = "Слабая";
    private static final String CA_MEDIUM = "Средняя";
    private static final String CA_STRONG = "Сильная";

    @Nullable
    private static Atmosphere instance;

    // Lens flare
    private final BooleanSetting lensFlare = (BooleanSetting) register(
            new BooleanSetting("Блики линзы", "Блики и кольца при взгляде на солнце", true));
    private final NumberSetting flareIntensity = (NumberSetting) register(
            new NumberSetting("Сила бликов", "Яркость бликов", 1.0, 0.0, 2.0, 0.05).visibleWhen(lensFlare::getValue));
    private final NumberSetting flareScale = (NumberSetting) register(
            new NumberSetting("Размер бликов", "Масштаб бликов", 1.0, 0.5, 2.0, 0.05).visibleWhen(lensFlare::getValue));
    private final BooleanSetting anamorphic = (BooleanSetting) register(
            new BooleanSetting("Анаморфная полоса", "Горизонтальная полоса света", true).visibleWhen(lensFlare::getValue));
    private final BooleanSetting starburst = (BooleanSetting) register(
            new BooleanSetting("Лучи", "Лучи-звезда вокруг солнца", true).visibleWhen(lensFlare::getValue));
    private final ColorSetting tint = (ColorSetting) register(
            new ColorSetting("Оттенок", "Оттенок бликов и грязи", new Color(255, 255, 255)));

    // Lens dirt
    private final BooleanSetting dirtMask = (BooleanSetting) register(
            new BooleanSetting("Грязь на линзе", "Пыль и царапины, видны против солнца", true));
    private final NumberSetting dirtIntensity = (NumberSetting) register(
            new NumberSetting("Сила грязи", "Насколько заметна грязь на свету", 0.8, 0.0, 1.5, 0.05).visibleWhen(dirtMask::getValue));
    private final NumberSetting dirtBase = (NumberSetting) register(
            new NumberSetting("Грязь без солнца", "Видимость грязи всегда", 0.0, 0.0, 0.3, 0.01).visibleWhen(dirtMask::getValue));

    // Chromatic aberration
    private final BooleanSetting chromatic = (BooleanSetting) register(
            new BooleanSetting("Хроматическая аберрация", "Разделение цветов по краям экрана", true));
    private final ModeSetting chromaticLevel = (ModeSetting) register(
            new ModeSetting("Сила аберрации", "Насколько сильно расходятся цвета", CA_MEDIUM, CA_SUBTLE, CA_MEDIUM, CA_STRONG)
                    .visibleWhen(chromatic::getValue));

    // Vignette
    private final BooleanSetting vignette = (BooleanSetting) register(
            new BooleanSetting("Виньетка", "Затемнение по углам кадра", true));
    private final NumberSetting vigIntensity = (NumberSetting) register(
            new NumberSetting("Сила виньетки", "Насколько темнеют углы", 0.6, 0.0, 1.0, 0.05).visibleWhen(vignette::getValue));
    private final NumberSetting vigRadius = (NumberSetting) register(
            new NumberSetting("Радиус виньетки", "Где начинается затемнение", 0.75, 0.3, 1.2, 0.05).visibleWhen(vignette::getValue));
    private final NumberSetting vigSoftness = (NumberSetting) register(
            new NumberSetting("Мягкость виньетки", "Плавность перехода", 0.6, 0.1, 1.0, 0.05).visibleWhen(vignette::getValue));

    // Behaviour
    private final BooleanSetting occlusion = (BooleanSetting) register(
            new BooleanSetting("Перекрытие блоками", "Блики гаснут, если солнце закрыто", true));
    private final NumberSetting fadeSpeed = (NumberSetting) register(
            new NumberSetting("Скорость затухания", "Как быстро появляются и гаснут блики", 8.0, 1.0, 20.0, 0.5));

    private final AtmosphereRenderer renderer = new AtmosphereRenderer();

    public Atmosphere() {
        super("Atmosphere", "Кинематографичные эффекты объектива как в AAA играх.", Category.VISUALS);
        instance = this;
    }

    @Nullable
    public static Atmosphere getInstance() {
        Atmosphere module = ModuleManager.Companion.get().get(Atmosphere.class);
        return module != null ? module : instance;
    }

    @Override
    public float fadeOutSeconds() {
        return 0.5f;
    }

    /** Right after the world is rendered, before the HUD. See {@code mixin.AtmosphereGameRendererMixin}. */
    public void onWorldRendered() {
        if (isVisuallyActive()) renderer.renderPostWorld(config());
    }

    /** Start of HUD rendering, so the lens sits under hotbar/chat. See {@code mixin.AtmosphereHudMixin}. */
    public void onLensOverlay(DrawContext context, float tickDelta) {
        if (isVisuallyActive()) renderer.renderLensOverlay(context, tickDelta, config());
    }

    private AtmosphereConfig config() {
        return new AtmosphereConfig(
                lensFlare.getValue(), flareIntensity.getFloat(), flareScale.getFloat(), tint.getColor() & 0xFFFFFF,
                anamorphic.getValue(), starburst.getValue(),
                dirtMask.getValue(), dirtIntensity.getFloat(), dirtBase.getFloat(),
                chromatic.getValue(), chromaticStrength(),
                vignette.getValue(), vigIntensity.getFloat(), vigRadius.getFloat(), vigSoftness.getFloat(),
                occlusion.getValue(), fadeSpeed.getFloat(),
                visualAlpha());
    }

    private ChromaticStrength chromaticStrength() {
        if (chromaticLevel.is(CA_SUBTLE)) return ChromaticStrength.SUBTLE;
        if (chromaticLevel.is(CA_STRONG)) return ChromaticStrength.STRONG;
        return ChromaticStrength.MEDIUM;
    }
}
