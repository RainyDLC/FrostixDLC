package fun.newrar.module.impl.display;

import org.lwjgl.glfw.GLFW;
import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventKey;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.MultiBooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;

@ModuleInfo(
        name = "Click Gui",
        category = Category.RENDER,
        key = GLFW.GLFW_KEY_RIGHT_SHIFT,
        desc = "Графический интерфейс для управления всеми функциями и визуальными эффектами клиента",
        autoEnabled = true,
        allowDisable = false
)
public class ClickGui extends Module {
    public MultiBooleanSetting effect = new MultiBooleanSetting(this, "Эффекты",
            new BooleanSetting("Серый фон", false),
            new BooleanSetting("Затемнять фон", true),
            new BooleanSetting("Размывать фон", true),
            new BooleanSetting("Шейдер", false),
            new BooleanSetting("Частицы", true),
            new BooleanSetting("Скан линии", true),
            new BooleanSetting("Свечение", true),
            new BooleanSetting("Точки", true),
            new BooleanSetting("Сборка", true));

    public SliderSetting size = new SliderSetting(this,"Размер",1.0F,0.5F,1.5F,0.1F);

    public ModeSetting dotsPattern = new ModeSetting(this, "Узор фона", "Сетка", "Соты")
            .setVisible(() -> effect.getValue("Точки"));

    @EventHandler
    public void onKey(EventKey event) {
        if (event.getKey() == getKey()) {
            mc.setScreen(Client.get.clickGuiScreen());
        }
    }
}

