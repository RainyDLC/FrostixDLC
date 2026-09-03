package fun.newrar.module.impl.render;

import net.minecraft.client.MinecraftClient;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.FogEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.other.Instance;

@ModuleInfo(
        name = "World Tweaks",
        desc = "Настройки погодных эффектов, времени суток, плотности тумана и освещения",
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
    public ModeSetting typeColor = new ModeSetting(this, "Режим цвета", "Тема", "Свой");

    public BooleanSetting lightnings = new BooleanSetting(this,"Молнии",false);
    public SliderSetting lightningInterval = new SliderSetting(this,"Интервал молний",4F,1F,15F,0.5F).setVisible(() -> lightnings.getValue());
    public SliderSetting lightningRadius = new SliderSetting(this,"Дальность молний",40F,12F,80F,1F).setVisible(() -> lightnings.getValue());
    public SliderSetting skyFlash = new SliderSetting(this,"Засветка мира",45F,0F,100F,5F).setVisible(() -> lightnings.getValue());

    public BooleanSetting rains = new BooleanSetting(this,"Дождь",false);
    public SliderSetting rainCount = new SliderSetting(this, "Количество капель", 500, 50, 3000, 25).setVisible(() -> rains.getValue());
    public SliderSetting rainRadius = new SliderSetting(this,"Радиус дождя",18F,6F,40F,1F).setVisible(() -> rains.getValue());
    public BooleanSetting winds = new BooleanSetting(this,"Ветер",true).setVisible(() -> rains.getValue());
    public SliderSetting windStrength = new SliderSetting(this,"Сила ветра",40F,0F,100F,5F).setVisible(() -> rains.getValue() && winds.getValue());
    public BooleanSetting splashes = new BooleanSetting(this,"Брызги",true).setVisible(() -> rains.getValue());
    public BooleanSetting mists = new BooleanSetting(this,"Дымка",true).setVisible(() -> rains.getValue());
    public SliderSetting rainAlpha = new SliderSetting(this, "Прозрачность капель", 75F, 5F, 100F, 5F).setVisible(() -> rains.getValue());

    public BooleanSetting wetGround = new BooleanSetting(this, "Мокрая земля", false);
    public SliderSetting wetRadius = new SliderSetting(this, "Радиус земли", 20F, 6F, 40F, 1F).setVisible(() -> wetGround.getValue());
    public SliderSetting wetness = new SliderSetting(this, "Влажность", 55F, 0F, 100F, 5F).setVisible(() -> wetGround.getValue());
    public SliderSetting wetGloss = new SliderSetting(this, "Глянец", 65F, 0F, 100F, 5F).setVisible(() -> wetGround.getValue());
    public SliderSetting wetPuddles = new SliderSetting(this, "Лужи", 45F, 0F, 100F, 5F).setVisible(() -> wetGround.getValue());
    public BooleanSetting wetObjects = new BooleanSetting(this, "Отражения", true).setVisible(() -> wetGround.getValue());
    public BooleanSetting wetSkyOnly = new BooleanSetting(this, "Только под небом", false).setVisible(() -> wetGround.getValue());
    public BooleanSetting wetSkyTint = new BooleanSetting(this, "Цвет неба", true).setVisible(() -> wetGround.getValue());

    public BooleanSetting worldOnly = new BooleanSetting(this,"Только в непогоду",false);

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {
        if (typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }

    public int getEffectiveFogColor() {
        int color = getColor();
        if (lightnings.getValue() && skyFlash.getValue() > 0.5F) {
            float fl = SkyLightningRenderer.flashLevel();
            if (fl > 0.01F) {
                color = mixColor(color, 0xFFEAF3FF, Math.min(1F, fl * skyFlash.getValue() / 100F));
            }
        }
        return color;
    }

    @EventHandler
    public void onFog(FogEvent e) {
        if(fogs.getValue()) {
            e.setDistance(fog.getValue());
            e.setColor(getEffectiveFogColor());
            e.cancel();
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        boolean stormNow = weatherActive();
        SkyLightningRenderer.update(lightnings.getValue() && stormNow, lightningInterval.getValue(),
                lightningRadius.getValue());
        SkyRainRenderer.update(rains.getValue() && stormNow, rainCount.getValue().intValue(),
                rainRadius.getValue(),
                winds.getValue(), windStrength.getValue(),
                splashes.getValue(), mists.getValue(),
                rainAlpha.getValue() / 100F);
        if (wetGround.getValue() && stormNow) {
            WetGroundRenderer.configure(
                    wetness.getValue(),
                    wetGloss.getValue(),
                    wetPuddles.getValue(),
                    wetRadius.getValue(),
                    wetObjects.getValue(),
                    wetSkyTint.getValue(),
                    getColor()
            );
        }
        WetGroundRenderer.update(
                wetGround.getValue() && stormNow,
                wetRadius.getValue().intValue(),
                wetSkyOnly.getValue(),
                wetPuddles.getValue()
        );
    }

    @EventHandler
    public void onRender3D(EventRender3D e) {
        boolean stormNow = weatherActive();
        if (wetGround.getValue() && stormNow) {
            WetGroundRenderer.render(e);
        }
        if (lightnings.getValue() && stormNow) {
            SkyLightningRenderer.render(e);
        }
        if (rains.getValue() && stormNow) {
            SkyRainRenderer.render(e);
        }
    }

    @Override
    protected void onDisable() {
        SkyLightningRenderer.clear();
        SkyRainRenderer.clear();
        WetGroundRenderer.clear();
    }

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

