package fun.newrar.module.impl.render;

import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.manager.events.orbit.EventPriority;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.FogBlurPipeline;

@ModuleInfo(name = "Fog Blur", category = Category.RENDER, desc = "Художественное размытие фонового окружения за пределами дистанции тумана")
public final class FogBlur extends Module {
    private final SliderSetting distance = new SliderSetting(this, "Дистанция", 0.05F, 0.001F, 0.5F, 0.001F);
    private final SliderSetting saturation = new SliderSetting(this, "Насыщенность", 0.5F, 0.05F, 0.95F, 0.05F);
    private final BooleanSetting clientColor = new BooleanSetting(this, "Цвет клиента", false);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRender(EventRender3D e) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        int color1 = ColorUtil.getClientColor1(1);
        int color2 = ColorUtil.getClientColor1(1);
        int color3 = ColorUtil.getClientColor1(270);
        int color4 = ColorUtil.getClientColor1(270);

        FogBlurPipeline.draw(
                distance.getValue(),
                Math.max(0.0F, Math.min(1.0F, 1.0F - saturation.getValue())),
                clientColor.getValue(),
                color1, color2, color3, color4
        );
    }
}

