package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;

/**
 * Модуль "Interface": HUD клиента.
 * Ватермарка, кейбинды, активные эффекты и кастомный хотбар.
 * Элементы двигаются ЛКМ и масштабируются колесом прямо в игре,
 * только когда открыт чат.
 */
public final class Interface extends Module {
    private static Interface instance;

    private final BoolSetting watermark = new BoolSetting("Watermark", true);
    private final ModeSetting watermarkPos = new ModeSetting("Watermark Position", 0,
            "Left", "Center", "Right");
    private final BoolSetting keybinds = new BoolSetting("Keybinds", true);
    private final BoolSetting effects = new BoolSetting("Effects", true);
    private final BoolSetting hotbar = new BoolSetting("Custom Hotbar", true);
    private final BoolSetting hideVanilla = new BoolSetting("Hide Vanilla Hotbar", true);
    private final SliderSetting hotbarSize = new SliderSetting("Hotbar Size", 1.0, 0.5, 2.0, 0.05);
    private final BoolSetting armorHud = new BoolSetting("Armor HUD", true);
    private final BoolSetting targetHud = new BoolSetting("Target HUD", true);
    private final BoolSetting arrayList = new BoolSetting("ArrayList", true);
    private final BoolSetting staff = new BoolSetting("StaffList", true);
    private final BoolSetting cooldowns = new BoolSetting("CoolDowns", true);
    private final BoolSetting notifications = new BoolSetting("Notifications", true);
    private final BoolSetting ticks = new BoolSetting("Ticks", false);
    private final BoolSetting ping = new BoolSetting("Ping", false);
    private final BoolSetting fps = new BoolSetting("FPS", false);
    private final BoolSetting bps = new BoolSetting("BPS", false);
    private final BoolSetting xyz = new BoolSetting("XYZ", false);
    private final BoolSetting totems = new BoolSetting("TotemCounter", true);
    private final BoolSetting media2d = new BoolSetting("Media 2D", false);
    private final TextSetting mediaFile = new TextSetting("Media File", "logo.gif", "logo.gif");
    private final SliderSetting mediaSize = new SliderSetting("Media Size", 64, 16, 256, 8);
    private final SliderSetting mediaX = new SliderSetting("Media X", 50, 0, 100, 1);
    private final SliderSetting mediaY = new SliderSetting("Media Y", 50, 0, 100, 1);
    private final BoolSetting media3d = new BoolSetting("Media 3D", false);
    private final TextSetting media3dFile = new TextSetting("Media 3D File", "logo.gif", "logo.gif");
    private final SliderSetting media3dSize = new SliderSetting("Media 3D Size", 1.0, 0.25, 4.0, 0.25);
    private final SliderSetting media3dHeight = new SliderSetting("Media 3D Height", 2.5, 0.5, 6.0, 0.25);

    public Interface() {
        super("Interface", "Ватермарка клиента, кейбинды, активные эффекты и кастомный хотбар",
                Category.RENDER);
        instance = this;
        with(this.watermark,
                this.watermarkPos,
                this.keybinds,
                this.effects,
                this.hotbar,
                this.hideVanilla,
                this.hotbarSize,
                this.armorHud,
                this.targetHud, this.arrayList, this.staff, this.cooldowns, this.notifications,
                this.ticks, this.ping, this.fps, this.bps, this.xyz, this.totems,
                this.media2d,
                this.mediaFile,
                this.mediaSize,
                this.mediaX,
                this.mediaY,
                this.media3d,
                this.media3dFile,
                this.media3dSize,
                this.media3dHeight);
        enabled(true);
        InterfaceHud.init();
    }

    public static boolean showExtra(dev.hatek.client.module.impl.render.interf.HudLayout.Element element) {
        Interface m=instance; if(m==null || !m.isEnabled()) return false;
        return switch(element) {
            case ARRAYLIST -> m.arrayList.value(); case STAFF -> m.staff.value();
            case COOLDOWNS -> m.cooldowns.value(); case NOTIFICATIONS -> m.notifications.value();
            case TICKS -> m.ticks.value(); case PING -> m.ping.value(); case FPS -> m.fps.value();
            case BPS -> m.bps.value(); case XYZ -> m.xyz.value(); case TOTEMS -> m.totems.value();
            default -> false;
        };
    }

    public static Interface instance() {
        return instance;
    }

    public static String watermarkPos() {
        Interface module = instance;
        return module == null ? "Left" : module.watermarkPos.value();
    }

    public static boolean showWatermark() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.watermark.value();
    }

    public static boolean showKeybinds() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.keybinds.value();
    }

    public static boolean showEffects() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.effects.value();
    }

    public static boolean showArmor() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.armorHud.value();
    }

    public static boolean showTarget() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.targetHud.value();
    }

    public static double hotbarSize() {
        Interface module = instance;
        return module == null ? 1.0 : module.hotbarSize.value();
    }

    public static boolean showHotbar() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.hotbar.value();
    }

    public static boolean hideVanillaHotbar() {
        Interface module = instance;
        return module != null && module.isEnabled()
                && module.hotbar.value() && module.hideVanilla.value();
    }

    public static boolean showMedia2D() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.media2d.value();
    }

    public static String mediaFile() {
        Interface module = instance;
        return module == null ? "" : module.mediaFile.value();
    }

    public static double mediaSize() {
        Interface module = instance;
        return module == null ? 64 : module.mediaSize.value();
    }

    public static double mediaX() {
        Interface module = instance;
        return module == null ? 50 : module.mediaX.value();
    }

    public static double mediaY() {
        Interface module = instance;
        return module == null ? 50 : module.mediaY.value();
    }

    /** Вызывается из редактора HUD: двигать/масштабировать 2D-медиа. */
    public static void setMediaGeometry(double sizePx, double xPct, double yPct) {
        Interface module = instance;
        if (module == null) {
            return;
        }
        module.mediaSize.value(sizePx);
        module.mediaX.value(xPct);
        module.mediaY.value(yPct);
    }

    public static boolean showMedia3D() {
        Interface module = instance;
        return module != null && module.isEnabled() && module.media3d.value();
    }

    public static String media3dFile() {
        Interface module = instance;
        return module == null ? "" : module.media3dFile.value();
    }

    public static double media3dSize() {
        Interface module = instance;
        return module == null ? 1.0 : module.media3dSize.value();
    }

    public static double media3dHeight() {
        Interface module = instance;
        return module == null ? 2.5 : module.media3dHeight.value();
    }
}
