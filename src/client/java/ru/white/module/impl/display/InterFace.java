package ru.white.module.impl.display;

import org.joml.Vector2f;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.MousePressEvent;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.*;
import ru.white.module.impl.display.interfaceimpl.*;
import ru.white.module.impl.player.ClickHelper;
import ru.white.utils.other.Instance;

import java.io.PrintStream;

@ModuleInfo(
        name = "Inter Face",
        desc = "Настройка интерфейса (HUD) клиента",
        category = Category.RENDER
)
public class InterFace extends Module {

    public static InterFace getInstance() {
        return Instance.get(InterFace.class);
    }

    public MultiBooleanSetting element = new MultiBooleanSetting(this, "Элементы",
            new BooleanSetting("Water mark", true),
            new BooleanSetting("Information", true),
            new BooleanSetting("Key Binds", true),
            new BooleanSetting("Potions", true),
            new BooleanSetting("Music Player", true),
            new BooleanSetting("Notifications", true),
            new BooleanSetting("Target Hud", true),
            new BooleanSetting("Item hud",true),
            new BooleanSetting("Use Tracker", true));

    public SliderSetting volume = new SliderSetting(this, "Громкость уведомления", 0.5F, 0.1F, 1.0F, 0.1F);
    public ModeSetting typeNotify = new ModeSetting(this, "Тип уведомления",
            "Первый", "Второй", "Третий");

    /** Вид худа: «Первый» — текущий стиль, «Второй» — стиль как на референсе. */
    public ModeSetting hudStyle = new ModeSetting(this, "Вид худа", "Первый", "Второй");

    public boolean isSecondStyle() {
        return hudStyle.is("Второй");
    }


    public SliderSetting sizeHud = new SliderSetting(this,"Размер интерфейса",1.0F,0.5F,1.5F,0.05F);
    public SliderSetting alphaHUD = new SliderSetting(this,"Прозрачность худа",0.6F,0.0F,0.9F,0.1F);

    public BooleanSetting notifyEffects = new BooleanSetting(this, "эффектах", true);
    public BooleanSetting notifyModules = new BooleanSetting(this, "модулях", true);
    public BooleanSetting notifyArmor = new BooleanSetting(this, "броне", true);

    public DragSetting waterMark = new DragSetting(this, "WaterMark", new Vector2f(10, 10));
    public DragSetting information = new DragSetting(this, "Information", new Vector2f(10, 30));
    public DragSetting keyBind = new DragSetting(this, "Key Binds", new Vector2f(10, 50));
    public DragSetting potion = new DragSetting(this, "Potions", new Vector2f(90, 50));
    public DragSetting music = new DragSetting(this, "Music Player", new Vector2f(10, 400));
    public DragSetting notifications = new DragSetting(this, "Notifications", new Vector2f(0, 200));
    public DragSetting targetHudDrag = new DragSetting(this, "Target Hud", new Vector2f(90, 40));
    public DragSetting itemHud = new DragSetting(this, "Item hud", new Vector2f(200, 90));
    public DragSetting useTracker = new DragSetting(this, "Use Tracker", new Vector2f(200, 120));

    // элементы второго вида худа
    public DragSetting waterMarkTwo = new DragSetting(this, "WaterMark Two", new Vector2f(400, 6), false);
    public DragSetting potionsTwo = new DragSetting(this, "Potions Two", new Vector2f(10, 150), false);
    public DragSetting hotkeysTwo = new DragSetting(this, "Hotkeys", new Vector2f(10, 350), false);
    public DragSetting armorHud = new DragSetting(this, "Armor Hud", new Vector2f(400, 300), false);
    public DragSetting statsHud = new DragSetting(this, "Stats Hud", new Vector2f(700, 200), false);

    private final WaterMark waterMarkElemnt = new WaterMark();
    private final Information informationElemnt = new Information();
    private final KeyBinds keyBinds = new KeyBinds();
    private final Potions potions = new Potions();
    private final MusicHud musicHud = new MusicHud();
    private final Notify notifyHud = new Notify();
    private final TargetHud targetHud = new TargetHud();
    private final UseTrackerHud useTrackerHud = new UseTrackerHud();

    // элементы второго вида худа
    private final WaterMarkTwo waterMarkTwoElement = new WaterMarkTwo();
    private final PotionsTwo potionsTwoElement = new PotionsTwo();
    private final Hotkeys hotkeysElement = new Hotkeys();
    private final ArmorHud armorHudElement = new ArmorHud();
    private final StatsHud statsHudElement = new StatsHud();

    public InterFace() {
        notifications.lockX = true;
    }

    @Override
    protected void onDisable() {
        musicHud.shutdown();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;
        if (element.getValue("Music Player")) musicHud.onTick();
        if (element.getValue("Notifications")) {
            notifyHud.onTick(notifyModules, notifyArmor, notifyEffects);
        }
        statsHudElement.onUpdate();
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        statsHudElement.onAttack(event);
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (!isEnabled()) return;
        statsHudElement.onPacket(event);
    }

    @EventHandler
    public void onMousePress(MousePressEvent event) {
        if (!isEnabled()) return;
        if (element.getValue("Music Player")) musicHud.onMouseClick(event);
    }

    @EventHandler
    public void onDisplayEvent(EventDisplay eventDisplay) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;

        if (isSecondStyle()) {
            renderSecondStyle(eventDisplay);
        } else {
            renderFirstStyle(eventDisplay);
        }
    }

    /** Обычный вид худа — как был. */
    private void renderFirstStyle(EventDisplay eventDisplay) {
        waterMarkTwo.active = false;
        potionsTwo.active = false;
        hotkeysTwo.active = false;
        armorHud.active = false;
        statsHud.active = false;

        if (element.getValue("Potions")) potions.onRender(potion, this, eventDisplay);
        if (element.getValue("Information")) informationElemnt.onRender(information, this);
        if (element.getValue("Key Binds")) keyBinds.onRender(keyBind, this);
        if (element.getValue("Water mark")) waterMarkElemnt.onRender(waterMark, this);
        renderShared(eventDisplay);
    }

    /** Второй вид худа — вёрстка как на референсе. */
    private void renderSecondStyle(EventDisplay eventDisplay) {
        waterMarkTwo.active = element.getValue("Water mark");
        potionsTwo.active = element.getValue("Potions");
        hotkeysTwo.active = element.getValue("Key Binds");
        armorHud.active = true;
        statsHud.active = true;

        if (element.getValue("Potions")) potionsTwoElement.onRender(potionsTwo, this, eventDisplay);
        if (element.getValue("Key Binds")) hotkeysElement.onRender(hotkeysTwo, this);
        if (element.getValue("Water mark")) waterMarkTwoElement.onRender(waterMarkTwo, this);
        armorHudElement.onRender(armorHud, this, eventDisplay);
        statsHudElement.onRender(statsHud, this, eventDisplay);

        renderShared(eventDisplay);
    }

    /** Элементы, общие для обоих видов худа. */
    private void renderShared(EventDisplay eventDisplay) {
        if (element.getValue("Music Player")) musicHud.onRender(music, this);
        if (element.getValue("Notifications")) notifyHud.onRender(notifications, this, eventDisplay);
        if (element.getValue("Target Hud")) targetHud.onRender(targetHudDrag, this, eventDisplay);
        if (element.getValue("Use Tracker")) useTrackerHud.onRender(useTracker, this);
        if(element.getValue("Item hud")) {

            Vector2f size = ClickHelper.getInstance().getHudSize();

            itemHud.size.set(size.x,size.y);

            ClickHelper.getInstance().renderHud(eventDisplay.getDrawContext(),itemHud.position.x,itemHud.position.y);
        }
    }
}
