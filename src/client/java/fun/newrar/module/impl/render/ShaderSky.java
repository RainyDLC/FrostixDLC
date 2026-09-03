package fun.newrar.module.impl.render;

import lombok.Getter;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.ShaderSkyRenderer;

@Getter
@ModuleInfo(name = "Shader Sky", category = Category.RENDER, desc = "Кастомный рендеринг небесного свода с анимированными шейдерными эффектами")
public class ShaderSky extends Module {
    private static ShaderSky instance;

    public ModeSetting mode = new ModeSetting(this, "Режим", "Aurora", "Night", "Snow", "Sky", "Star", "Glow", "Plasma");
    public SliderSetting intensity = new SliderSetting(this, "Сила", 1.0f, 0.1f, 1.0f, 0.05f);
    public SliderSetting speed = new SliderSetting(this, "Скорость", 1.0f, 0.1f, 3.0f, 0.1f);
    public SliderSetting scale = new SliderSetting(this, "Масштаб", 1.1f, 0.45f, 3.0f, 0.05f);
    public SliderSetting stars = new SliderSetting(this, "Звёзды", 0.75f, 0.0f, 1.0f, 0.05f);

    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");
    public ColorSetting color1 = new ColorSetting(this, "Цвет 1", 0xFF79E6FF)
            .setVisible(() -> typeColor.is("Свой"));
    public ColorSetting color2 = new ColorSetting(this, "Цвет 2", 0xFFFF7AE6)
            .setVisible(() -> typeColor.is("Свой"));
    public BooleanSetting hideVanillaSky = new BooleanSetting(this, "Убрать небо", true);

    public ShaderSky() {
        instance = this;
    }

    public static ShaderSky getInstance() {
        return instance;
    }

    public static int geteColor2() {
        if (getInstance().typeColor.is("Тема")) {
            return ColorUtil.multDark(ColorUtil.getClientColor1(1), 0.25F);
        }
        return getInstance().color2.getValue();
    }

    public static int geteColor1() {
        if (getInstance().typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }
        return getInstance().color1.getValue();
    }

    @Override
    protected void onEnable() {
        ShaderSkyRenderer renderer = ShaderSkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        ShaderSkyRenderer.getInstance().setEnabled(false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        ShaderSkyRenderer renderer = ShaderSkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
    }
}

