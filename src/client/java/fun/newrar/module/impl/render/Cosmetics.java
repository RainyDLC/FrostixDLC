package fun.newrar.module.impl.render;

import fun.newrar.cosmetics.LocalCosmetics;
import fun.newrar.cosmetics.ui.CosmeticsScreen;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.utils.other.Instance;
import net.minecraft.client.MinecraftClient;

@ModuleInfo(
        name = "Cosmetics",
        desc = "Кастомная 3D косметика для персонажа (крылья, питомцы, шляпы, плащи и др.)",
        category = Category.RENDER
)
public class Cosmetics extends Module {
    public final ButtonSetting openMenu = new ButtonSetting(this, "Открыть меню", this::openCosmeticsMenu);

    public Cosmetics() {
        LocalCosmetics.init();
    }

    public static Cosmetics getInstance() {
        return Instance.get(Cosmetics.class);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        LocalCosmetics.init();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            mc.setScreen(new CosmeticsScreen());
        }
    }

    public void openCosmeticsMenu() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new CosmeticsScreen());
    }
}
