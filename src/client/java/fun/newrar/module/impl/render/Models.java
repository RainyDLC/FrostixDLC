package fun.newrar.module.impl.render;

import fun.newrar.Client;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.other.Instance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

import java.awt.Color;

@ModuleInfo(
        name = "Models",
        desc = "Кастомные 3D-модели для персонажа: коляска, тянка, фурри, демонесса, зайка",
        category = Category.RENDER
)
public class Models extends Module {
    public static Models getInstance() {
        return Instance.get(Models.class);
    }

    public final ModeSetting modelPreset = new ModeSetting(this, "Модель",
            "Коляска", "Тянка", "Фурри", "Демонесса", "Зайка", "Кастом");

    public final BooleanSetting onlySelf = new BooleanSetting(this, "Только себе", true);
    public final BooleanSetting friends = new BooleanSetting(this, "Друзьям", false)
            .setVisible(() -> !onlySelf.getValue());

    public final ModeSetting colorMode = new ModeSetting(this, "Режим цвета", "Свой", "Тема", "Радуга");
    public final ColorSetting primaryColor = new ColorSetting(this, "Основной цвет", 0xFFFF5599)
            .setVisible(() -> colorMode.is("Свой"));
    public final ColorSetting secondaryColor = new ColorSetting(this, "Второй цвет", 0xFFFFFFFF)
            .setVisible(() -> colorMode.is("Свой"));

    public final SliderSetting scale = new SliderSetting(this, "Масштаб", 1.0f, 0.6f, 1.4f, 0.05f);
    public final BooleanSetting physics = new BooleanSetting(this, "Анимация и физика", true);
    public final BooleanSetting sittingPose = new BooleanSetting(this, "Поза для коляски", true);
    public final BooleanSetting cutePose = new BooleanSetting(this, "Милая стойка", true);

    // Custom toggles
    public final BooleanSetting customWheelchair = new BooleanSetting(this, "Коляска", false)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final BooleanSetting customEars = new BooleanSetting(this, "Ушки", true)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final ModeSetting customEarsType = new ModeSetting(this, "Тип ушек", "Кошачьи", "Лисьи", "Заячьи")
            .setVisible(() -> modelPreset.is("Кастом") && customEars.getValue());
    public final BooleanSetting customTail = new BooleanSetting(this, "Хвост", true)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final ModeSetting customTailType = new ModeSetting(this, "Тип хвоста", "Кошачий", "Лисий", "Демон", "Заячий")
            .setVisible(() -> modelPreset.is("Кастом") && customTail.getValue());
    public final BooleanSetting customSkirt = new BooleanSetting(this, "Юбка", true)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final BooleanSetting customWings = new BooleanSetting(this, "Крылья", false)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final BooleanSetting customHorns = new BooleanSetting(this, "Рожки", false)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final BooleanSetting customMuzzle = new BooleanSetting(this, "Мордочка", false)
            .setVisible(() -> modelPreset.is("Кастом"));
    public final BooleanSetting customBows = new BooleanSetting(this, "Бантики", true)
            .setVisible(() -> modelPreset.is("Кастом"));

    public boolean isWheelchairActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Коляска")) return true;
        if (modelPreset.is("Кастом") && customWheelchair.getValue()) return true;
        return false;
    }

    public boolean isEarsActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Тянка") || modelPreset.is("Фурри") || modelPreset.is("Зайка")) return true;
        if (modelPreset.is("Кастом") && customEars.getValue()) return true;
        return false;
    }

    public String getEarsType() {
        if (modelPreset.is("Тянка")) return "Кошачьи";
        if (modelPreset.is("Фурри")) return "Лисьи";
        if (modelPreset.is("Зайка")) return "Заячьи";
        if (modelPreset.is("Кастом")) return customEarsType.getValue();
        return "Кошачьи";
    }

    public boolean isTailActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Тянка") || modelPreset.is("Фурри") || modelPreset.is("Демонесса") || modelPreset.is("Зайка")) return true;
        if (modelPreset.is("Кастом") && customTail.getValue()) return true;
        return false;
    }

    public String getTailType() {
        if (modelPreset.is("Тянка")) return "Кошачий";
        if (modelPreset.is("Фурри")) return "Лисий";
        if (modelPreset.is("Демонесса")) return "Демон";
        if (modelPreset.is("Зайка")) return "Заячий";
        if (modelPreset.is("Кастом")) return customTailType.getValue();
        return "Кошачий";
    }

    public boolean isSkirtActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Тянка")) return true;
        if (modelPreset.is("Кастом") && customSkirt.getValue()) return true;
        return false;
    }

    public boolean isWingsActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Демонесса")) return true;
        if (modelPreset.is("Кастом") && customWings.getValue()) return true;
        return false;
    }

    public boolean isHornsActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Демонесса")) return true;
        if (modelPreset.is("Кастом") && customHorns.getValue()) return true;
        return false;
    }

    public boolean isMuzzleActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Фурри")) return true;
        if (modelPreset.is("Кастом") && customMuzzle.getValue()) return true;
        return false;
    }

    public boolean isBowsActive() {
        if (!isEnabled()) return false;
        if (modelPreset.is("Тянка") || modelPreset.is("Зайка")) return true;
        if (modelPreset.is("Кастом") && customBows.getValue()) return true;
        return false;
    }

    public int getPrimaryColor() {
        if (colorMode.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        } else if (colorMode.is("Радуга")) {
            float hue = (float) ((System.currentTimeMillis() % 4000L) / 4000.0);
            return Color.HSBtoRGB(hue, 0.75f, 1.0f) | 0xFF000000;
        }
        return primaryColor.getValue();
    }

    public int getSecondaryColor() {
        if (colorMode.is("Тема")) {
            return ColorUtil.getClientColor(1);
        } else if (colorMode.is("Радуга")) {
            float hue = (float) (((System.currentTimeMillis() + 1500L) % 4000L) / 4000.0);
            return Color.HSBtoRGB(hue, 0.65f, 1.0f) | 0xFF000000;
        }
        return secondaryColor.getValue();
    }

    public boolean isTarget(PlayerEntityRenderState state) {
        if (!isEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        boolean isLocal = false;
        if (state.id == mc.player.getId()) {
            isLocal = true;
        } else if (state.playerName != null && mc.player.getName() != null) {
            isLocal = state.playerName.getString().equals(mc.player.getName().getString());
        }

        if (isLocal) {
            return true;
        }

        if (!onlySelf.getValue() && friends.getValue()) {
            if (mc.world != null) {
                net.minecraft.entity.Entity entity = mc.world.getEntityById(state.id);
                if (entity instanceof net.minecraft.entity.player.PlayerEntity p && Client.get() != null && Client.get().friendManager() != null) {
                    return Client.get().friendManager().isFriend(p.getName().getString());
                }
            }
        }

        return false;
    }
}
