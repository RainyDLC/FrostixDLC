package fun.newrar.module.impl.render;

import net.minecraft.util.hit.HitResult;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.PixelGridSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.screen.CrosshairEditor;
import fun.newrar.screen.Menu;
import fun.newrar.theme.ThemeColor;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.render.RenderUtil;

@ModuleInfo(
        name = "Cross Hair",
        desc = "Пользовательский прицел с возможностью точечного редактирования формы и цвета",
        category = Category.RENDER
)
public class CrossHair extends Module {
    public static final int GRID_SIZE = 21;

    public static CrossHair getInstance() {
        return Instance.get(CrossHair.class);
    }

    public boolean isDrawing() {
        return type.is("Кастомный");
    }

    public boolean isEditorOpen() {
        return CrosshairEditor.getInstance() != null && CrosshairEditor.getInstance().isActive();
    }

    public boolean isEditorSettingVisible() {
        if (!isDrawing()) return false;
        boolean inNormalClickGui = (mc.currentScreen instanceof Menu) && !isEditorOpen();
        return !inNormalClickGui;
    }

    public ButtonSetting openEditor = new ButtonSetting(this, "Редактор прицела", () -> {
        if (mc.currentScreen instanceof Menu menu) menu.openCrosshairEditor();
    }).setVisible(() -> isDrawing() && !isEditorOpen());

    public ModeSetting type = new ModeSetting(this, "Режим", "Кастомный", "Точка", "Кружочек");

    public final PixelGridSetting grid = new PixelGridSetting(this, "Рисунок", GRID_SIZE);

    public final SliderSetting pixelSize = new SliderSetting(this, "Размер пикселя", 1F, 0.5F, 4F, 0.5F)
            .setVisible(this::isEditorSettingVisible);
    public final BooleanSetting pixelOutline = new BooleanSetting(this, "Обводка рисунка", true)
            .setVisible(this::isEditorSettingVisible);
    public final ModeSetting pixelColorMode = new ModeSetting(this, "Цвет рисунка", "Тема", "Свой", "Градиент")
            .setVisible(this::isEditorSettingVisible);
    public final ColorSetting pixelTint = new ColorSetting(this, "Свой цвет", 0xFFFFFFFF)
            .setVisible(() -> isEditorSettingVisible() && "Свой".equalsIgnoreCase(pixelColorMode.getValue()));

    public final ModeSetting gradientMode = new ModeSetting(this, "Режим градиента", "Статический", "Анимированный")
            .setVisible(() -> isEditorSettingVisible() && "Градиент".equalsIgnoreCase(pixelColorMode.getValue()));
    public final ColorSetting gradientColor1 = new ColorSetting(this, "Цвет градиента 1", 0xFFFF0000)
            .setVisible(() -> isEditorSettingVisible() && "Градиент".equalsIgnoreCase(pixelColorMode.getValue()));
    public final ColorSetting gradientColor2 = new ColorSetting(this, "Цвет градиента 2", 0xFF0000FF)
            .setVisible(() -> isEditorSettingVisible() && "Градиент".equalsIgnoreCase(pixelColorMode.getValue()));
    public final SliderSetting gradientSpeed = new SliderSetting(this, "Скорость градиента", 1F, 0.1F, 4F, 0.1F)
            .setVisible(() -> isEditorSettingVisible() && "Градиент".equalsIgnoreCase(pixelColorMode.getValue()) && "Анимированный".equalsIgnoreCase(gradientMode.getValue()));

    public final BooleanSetting targetColorToggle = new BooleanSetting(this, "Цвет при наведении", false)
            .setVisible(this::isEditorSettingVisible);
    public final ModeSetting targetColorMode = new ModeSetting(this, "Цвет сущности", "Тема", "Свой", "Градиент")
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue());
    public final ColorSetting targetTint = new ColorSetting(this, "Свой цвет сущности", 0xFFFF0000)
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue() && "Свой".equalsIgnoreCase(targetColorMode.getValue()));
    public final ModeSetting targetGradientMode = new ModeSetting(this, "Градиент сущности", "Статический", "Анимированный")
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue() && "Градиент".equalsIgnoreCase(targetColorMode.getValue()));
    public final ColorSetting targetGradient1 = new ColorSetting(this, "Цвет сущности 1", 0xFFFF0000)
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue() && "Градиент".equalsIgnoreCase(targetColorMode.getValue()));
    public final ColorSetting targetGradient2 = new ColorSetting(this, "Цвет сущности 2", 0xFFFFFF00)
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue() && "Градиент".equalsIgnoreCase(targetColorMode.getValue()));
    public final SliderSetting targetGradientSpeed = new SliderSetting(this, "Скорость сущности", 1F, 0.1F, 4F, 0.1F)
            .setVisible(() -> isEditorSettingVisible() && targetColorToggle.getValue() && "Градиент".equalsIgnoreCase(targetColorMode.getValue()) && "Анимированный".equalsIgnoreCase(targetGradientMode.getValue()));

    public final SliderSetting silaAnim = new SliderSetting(this, "Сила анимации при ударе", 5, 0, 10, 1)
            .setVisible(this::isEditorSettingVisible);

    public Animation animation = new Animation();

    public float expandFor(float animValue) {
        return silaAnim.getValue() * animValue;
    }

    @EventHandler
    public void onRender(EventDisplay eventDisplay) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float x = screenWidth / 2;
        float y = screenHeight / 2;

        if (type.is("Кастомный")) {
            animation.update();

            float cooldown = 1 - mc.player.getAttackCooldownProgress(0);

            animation.run(cooldown, 0.001F);

            drawPixelCrosshair(x, y, expandFor(animation.get()), 1F);
            return;
        }

        if (type.is("Кружочек")) {
            animation.update();

            float cooldown = 1 - mc.player.getAttackCooldownProgress(0);

            animation.run(cooldown, 0.01F);

            RenderUtil.Render2D.roundedCircleProgress(eventDisplay.getDrawContext(),
                    x, y, 4, 1, animation.get(), ThemeColor.getHudColor(), ColorUtil.getColor(0),
                    ColorUtil.getColor(0));
        }

        if (type.is("Точка")) {
            RenderUtil.Render2D.gradientRect(x - 1.5F, y - 1.5F, 3, 3, new int[]{
                    ColorUtil.getColor(255),
                    ColorUtil.getColor(255),
                    ColorUtil.getColor(255),
                    ColorUtil.getColor(255),
            }, 4);

            RenderUtil.Render2D.outline(x - 2, y - 2, 4, 4, 0.5F, ColorUtil.getColor(0), 4);
        }
    }

    public void drawPixelCrosshair(float centerX, float centerY, float expand, float alphaMul) {
        if (alphaMul <= 0.01F) return;

        float ps = Math.max(0.5F, pixelSize.getValue());
        int c = grid.center();

        if (pixelOutline.getValue()) {
            int outline = ColorUtil.getColor(0, alphaMul * 0.85F);
            forEachPixel(centerX, centerY, ps, c, expand, (px, py, gx, gy) ->
                    RenderUtil.Render2D.rect(px - 0.5F, py - 0.5F, ps + 1F, ps + 1F, outline));
        }

        forEachPixel(centerX, centerY, ps, c, expand, (px, py, gx, gy) -> {
            int fill = resolvePixelColor(gx, gy, alphaMul);
            RenderUtil.Render2D.rect(px, py, ps, ps, fill);
        });
    }

    private void forEachPixel(float centerX, float centerY, float ps, int c, float expand, PixelConsumer consumer) {
        for (int gy = 0; gy < grid.size; gy++) {
            for (int gx = 0; gx < grid.size; gx++) {
                if (!grid.get(gx, gy)) continue;

                int dx = gx - c;
                int dy = gy - c;

                float px = centerX + dx * ps + Math.signum(dx) * expand - ps / 2F;
                float py = centerY + dy * ps + Math.signum(dy) * expand - ps / 2F;
                consumer.accept(px, py, gx, gy);
            }
        }
    }

    public int resolvePixelColor(int gx, int gy, float alphaMul) {
        boolean isTarget = targetColorToggle.getValue() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY;
        String mode = isTarget ? targetColorMode.getValue() : pixelColorMode.getValue();
        int base = switch (mode) {
            case "Свой" -> isTarget ? targetTint.getValue() : pixelTint.getValue();
            case "Градиент" -> {
                float pos = (gx + gy) / (float) Math.max(1, (grid.size * 2 - 2));
                float factor;
                String gMode = isTarget ? targetGradientMode.getValue() : gradientMode.getValue();
                if ("Анимированный".equalsIgnoreCase(gMode)) {
                    double speedVal = (isTarget ? targetGradientSpeed : gradientSpeed).getValue().doubleValue();
                    double time = (System.currentTimeMillis() * speedVal) / 1000.0;
                    factor = (float) ((Math.sin((pos + time) * Math.PI * 2) + 1.0) / 2.0);
                } else {
                    factor = pos;
                }
                int c1 = isTarget ? targetGradient1.getValue() : gradientColor1.getValue();
                int c2 = isTarget ? targetGradient2.getValue() : gradientColor2.getValue();
                yield ColorUtil.interpolateColor(c1, c2, factor);
            }
            default -> ColorUtil.getClientColor1(1);
        };
        return ColorUtil.replAlpha(base, alphaMul);
    }

    public int resolvePixelColor(float alphaMul) {
        return resolvePixelColor(grid.center(), grid.center(), alphaMul);
    }

    @FunctionalInterface
    private interface PixelConsumer {
        void accept(float x, float y, int gx, int gy);
    }
}

