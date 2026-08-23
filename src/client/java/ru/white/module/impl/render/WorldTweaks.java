package ru.white.module.impl.render;

import net.minecraft.client.MinecraftClient;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.FogEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "World Tweaks",
        desc = "Мелкие настройки отображения мира",
        category = Category.RENDER
)
public class WorldTweaks extends Module {

    public static WorldTweaks get() {
        return Instance.get(WorldTweaks.class);
    }

    public BooleanSetting times = new BooleanSetting(this,"Менять время",true);
    public BooleanSetting fogs = new BooleanSetting(this,"Менять туман",true);
    public SliderSetting time = new SliderSetting(this,"Время", 12,0,24,1).setVisible(() -> times.getValue());
    public SliderSetting fog = new SliderSetting(this,"Дистанция тумана",100, 2,200,1).setVisible(() -> fogs.getValue());
    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public BooleanSetting lightnings = new BooleanSetting(this,"Молнии",false);
    public SliderSetting lightningInterval = new SliderSetting(this,"Интервал молний",4F,1F,15F,0.5F).setVisible(() -> lightnings.getValue());
    public SliderSetting lightningRadius = new SliderSetting(this,"Дальность молний",40F,12F,80F,1F).setVisible(() -> lightnings.getValue());
    public SliderSetting skyFlash = new SliderSetting(this,"Засветка мира",45F,0F,100F,5F).setVisible(() -> lightnings.getValue());

    public BooleanSetting rains = new BooleanSetting(this,"Дождь",false);
    public SliderSetting rainDensity = new SliderSetting(this,"Плотность дождя",80,10,200,5).setVisible(() -> rains.getValue());
    public SliderSetting rainRadius = new SliderSetting(this,"Радиус дождя",18F,6F,40F,1F).setVisible(() -> rains.getValue());
    public BooleanSetting winds = new BooleanSetting(this,"Ветер",true).setVisible(() -> rains.getValue());
    public SliderSetting windStrength = new SliderSetting(this,"Сила ветра",40F,0F,100F,5F).setVisible(() -> rains.getValue() && winds.getValue());
    public BooleanSetting splashes = new BooleanSetting(this,"Брызги",true).setVisible(() -> rains.getValue());
    public BooleanSetting mists = new BooleanSetting(this,"Дымка",true).setVisible(() -> rains.getValue());

    /** Показывать эффекты только когда в мире реально идёт дождь/гроза. */
    public BooleanSetting worldOnly = new BooleanSetting(this,"Только в непогоду",false);

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {

        if(typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }
    @EventHandler
    public void onFog(FogEvent e) {
        if(fogs.getValue()) {
            e.setDistance(fog.getValue());
            e.setColor(getColor());

            // вспышка молний на мгновение озаряет туман — «шейдерная» гроза
            if (lightnings.getValue() && skyFlash.getValue() > 0.5F) {
                float fl = SkyLightningRenderer.flashLevel();
                if (fl > 0.01F) {
                    e.setColor(mixColor(e.getColor(), 0xFFEAF3FF,
                            Math.min(1F, fl * skyFlash.getValue() / 100F)));
                }
            }

            e.cancel();
        }

    }

    @EventHandler
    public void onTick(EventTick e) {
        boolean stormNow = weatherActive();
        SkyLightningRenderer.update(lightnings.getValue() && stormNow, lightningInterval.getValue(),
                lightningRadius.getValue());
        SkyRainRenderer.update(rains.getValue() && stormNow, rainDensity.getValue().intValue(),
                rainRadius.getValue(),
                winds.getValue(), windStrength.getValue(),
                splashes.getValue(), mists.getValue());
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        boolean stormNow = weatherActive();
        if (lightnings.getValue() && stormNow) {
            SkyLightningRenderer.render(e);
        }
        if (rains.getValue() && stormNow) {
            SkyRainRenderer.render(e);
        }
    }

    /** Гейт по погоде: если включено «Только в непогоду» — эффекты лишь в дождь/грозу. */
    public boolean weatherActive() {
        if (!worldOnly.getValue()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && mc.world.isRaining();
    }

    private static int mixColor(int base, int overlay, float t) {
        int a = (base >>> 24) & 0xFF;
        int r = (int) (((base >> 16) & 0xFF) + (((overlay >> 16) & 0xFF) - ((base >> 16) & 0xFF)) * t);
        int g = (int) (((base >> 8) & 0xFF) + (((overlay >> 8) & 0xFF) - ((base >> 8) & 0xFF)) * t);
        int b = (int) ((base & 0xFF) + ((overlay & 0xFF) - (base & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

}
