package fun.newrar.module.impl.display;

import org.joml.Vector2f;
import fun.newrar.RainyDlcLoader;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.MousePressEvent;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.MultiBooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.module.impl.display.interfaceimpl.ArmorHud;
import fun.newrar.module.impl.display.interfaceimpl.Information;
import fun.newrar.module.impl.display.interfaceimpl.KeyBinds;
import fun.newrar.module.impl.display.interfaceimpl.MusicHud;
import fun.newrar.module.impl.display.interfaceimpl.Notify;
import fun.newrar.module.impl.display.interfaceimpl.Potions;
import fun.newrar.module.impl.display.interfaceimpl.TargetHud;
import fun.newrar.module.impl.display.interfaceimpl.UseTrackerHud;
import fun.newrar.module.impl.display.interfaceimpl.WaterMark;
import fun.newrar.module.impl.player.ClickHelper;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.other.UseCooldowns;

@ModuleInfo(
        name = "Interface",
        desc = "Комплексная панель управления графическим интерфейсом и визуальными виджетами",
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
            new BooleanSetting("Armor", true),
            new BooleanSetting("Music Player", true),
            new BooleanSetting("Notifications", true),
            new BooleanSetting("Target Hud", true),
            new BooleanSetting("Item hud", true),
            new BooleanSetting("Use Tracker", true));

    public SliderSetting volume = new SliderSetting(this, "Громкость уведомления", 0.5F, 0.1F, 1.0F, 0.1F);
    public ModeSetting typeNotify = new ModeSetting(this, "Тип уведомления", "Первый", "Второй", "Третий");
    public ModeSetting watermarkMode = new ModeSetting(this, "Тип ватермарки", "Премиум Остров", "Нео-Гласс", "Островок", "Классический");

    public BooleanSetting hudAvatar = new BooleanSetting(this, "Аватар игрока", true)
            .setVisible(() -> watermarkMode.is("Премиум Остров") || watermarkMode.is("Нео-Гласс"));
    public BooleanSetting hudServer = new BooleanSetting(this, "Сервер", true)
            .setVisible(() -> watermarkMode.is("Премиум Остров") || watermarkMode.is("Нео-Гласс"));
    public BooleanSetting hudBps = new BooleanSetting(this, "Скорость BPS", true)
            .setVisible(() -> watermarkMode.is("Премиум Остров") || watermarkMode.is("Нео-Гласс"));
    public BooleanSetting hudGlow = new BooleanSetting(this, "Неоновое свечение", true)
            .setVisible(() -> watermarkMode.is("Премиум Остров") || watermarkMode.is("Нео-Гласс"));

    public ModeSetting armorOrientation = new ModeSetting(this, "Ориентация брони", "Горизонтальная", "Вертикальная");
    public ModeSetting armorDurability = new ModeSetting(this, "Прочность брони", "Проценты", "Полоса", "Числа", "Нет");
    public BooleanSetting armorHands = new BooleanSetting(this, "Предметы в руках", true);
    public BooleanSetting armorEmptySlots = new BooleanSetting(this, "Пустые слоты", false);
    public BooleanSetting armorGlow = new BooleanSetting(this, "Свечение брони", true);

    public SliderSetting sizeHud = new SliderSetting(this, "Размер интерфейса", 1.0F, 0.5F, 1.5F, 0.05F);
    public SliderSetting alphaHUD = new SliderSetting(this, "Прозрачность худа", 0.6F, 0.0F, 0.9F, 0.1F);

    public ModeSetting fontMode = new ModeSetting(this, "Шрифт", "Обычный", "Уникальный");

    public ModeSetting targetHudMode = new ModeSetting(this, "Тип Target Hud", "Классический", "Полный", "Компактный", "Полоса");

    public BooleanSetting notifyEffects = new BooleanSetting(this, "эффектах", true);
    public BooleanSetting notifyModules = new BooleanSetting(this, "модулях", true);
    public BooleanSetting notifyArmor = new BooleanSetting(this, "броне", true);

    public DragSetting waterMark = new DragSetting(this, "WaterMark", new Vector2f(10, 10));
    public DragSetting information = new DragSetting(this, "Information", new Vector2f(10, 30));
    public DragSetting keyBind = new DragSetting(this, "Key Binds", new Vector2f(10, 50));
    public DragSetting potion = new DragSetting(this, "Potions", new Vector2f(90, 50));
    public DragSetting armorDrag = new DragSetting(this, "Armor", new Vector2f(160, 90));
    public DragSetting music = new DragSetting(this, "Music Player", new Vector2f(10, 400));
    public DragSetting notifications = new DragSetting(this, "Notifications", new Vector2f(0, 200));
    public DragSetting targetHudDrag = new DragSetting(this, "Target Hud", new Vector2f(90, 40));
    public DragSetting itemHud = new DragSetting(this, "Item hud", new Vector2f(200, 90));
    public DragSetting useTracker = new DragSetting(this, "Use Tracker", new Vector2f(200, 120));

    private final WaterMark waterMarkElement = new WaterMark();
    private final Information informationElement = new Information();
    private final KeyBinds keyBinds = new KeyBinds();
    private final Potions potions = new Potions();
    private final ArmorHud armorHud = new ArmorHud();
    private final MusicHud musicHud = new MusicHud();
    private final Notify notifyHud = new Notify();
    private final TargetHud targetHud = new TargetHud();
    private final UseTrackerHud useTrackerHud = new UseTrackerHud();

    public InterFace() {
        notifications.lockX = true;
    }

    @Override
    protected void onDisable() {
        musicHud.shutdown();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        UseCooldowns.clear();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;

        RainyDlcLoader.pulseCheck();
        UseCooldowns.tick(event);

        if (element.getValue("Music Player")) musicHud.onTick();
        if (element.getValue("Notifications")) {
            notifyHud.onTick(notifyModules, notifyArmor, notifyEffects);
        }
    }

    @EventHandler
    public void onMousePress(MousePressEvent event) {
        if (!isEnabled()) return;
        if (element.getValue("Music Player")) musicHud.onMouseClick(event);
    }

    @EventHandler
    public void onDisplayEvent(EventDisplay eventDisplay) {
        if (mc.player == null || mc.world == null || !isEnabled()) return;

        if (element.getValue("Potions")) potions.onRender(potion, this, eventDisplay);
        if (element.getValue("Information")) informationElement.onRender(information, this);
        if (element.getValue("Key Binds")) keyBinds.onRender(keyBind, this);
        if (element.getValue("Armor")) armorHud.onRender(armorDrag, this, eventDisplay);
        if (element.getValue("Water mark")) waterMarkElement.onRender(waterMark, this);
        if (element.getValue("Music Player")) musicHud.onRender(music, this);
        if (element.getValue("Notifications")) notifyHud.onRender(notifications, this, eventDisplay);
        if (element.getValue("Target Hud")) targetHud.onRender(targetHudDrag, this, eventDisplay);
        if (element.getValue("Use Tracker")) useTrackerHud.onRender(useTracker, this);
        if (element.getValue("Item hud")) {
            Vector2f size = ClickHelper.getInstance().getHudSize();
            itemHud.size.set(size.x, size.y);
            ClickHelper.getInstance().renderHud(eventDisplay.getDrawContext(), itemHud.position.x, itemHud.position.y);
        }
    }
}
