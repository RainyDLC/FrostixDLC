package fun.newrar.module.impl.render;

import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.utils.other.Instance;

@ModuleInfo(
        name = "No Render",
        desc = "Отключение нежелательных оверлеев экрана, тряски камеры, частиц и эффектов",
        category = Category.RENDER
)
public class NoRender extends Module {
    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    public BooleanSetting ignoreFire = new BooleanSetting(this,"Убирать огонь",true);
    public BooleanSetting ignoreLava = new BooleanSetting(this,"Убирать туман лавы",true);
    public BooleanSetting ignoreZalupa = new BooleanSetting(this,"Убирать плохие эффекты",true);
    public BooleanSetting ignoreScoreboard = new BooleanSetting(this,"Убирать скорборд",false);
    public BooleanSetting ignoreBossBar = new BooleanSetting(this,"Убирать боссбар",false);
    public BooleanSetting noCameraClip = new BooleanSetting(this,"Камера сквозь блоки",false);
    public BooleanSetting ignoreTotemPop = new BooleanSetting(this,"Убирать тотем на экране",true);
    public BooleanSetting removeCamreZalupa = new BooleanSetting(this,"Убирать тряску камеры",true);
}

