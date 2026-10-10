package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;

public final class NoRender extends Module {
    private static NoRender instance;

    private final BoolSetting fire = new BoolSetting("Fire", true);
    private final BoolSetting hurtCam = new BoolSetting("Hurt Camera", true);
    private final BoolSetting viewBobbing = new BoolSetting("View Bobbing", false);
    private final BoolSetting vignette = new BoolSetting("Vignette", true);
    private final BoolSetting nausea = new BoolSetting("Nausea", true);
    private final BoolSetting portal = new BoolSetting("Portal", true);
    private final BoolSetting spyglass = new BoolSetting("Spyglass", false);
    private final BoolSetting sleep = new BoolSetting("Sleep", false);
    private final BoolSetting bossBar = new BoolSetting("Boss Bar", true);
    private final BoolSetting scoreboard = new BoolSetting("Scoreboard", true);
    private final BoolSetting titles = new BoolSetting("Titles", false);
    private final BoolSetting effectIcons = new BoolSetting("Effect Icons", false);
    private final BoolSetting underwater = new BoolSetting("Underwater", false);
    private final BoolSetting inWall = new BoolSetting("In Wall", false);
    private final BoolSetting totemPop = new BoolSetting("Totem Pop", false);
    private final BoolSetting crosshair = new BoolSetting("Crosshair", false);

    public NoRender() {
        super("NoRender", "Hides screen overlays and effects: fire, hurt shake, vignette, boss bar, scoreboard and more", Category.RENDER);
        instance = this;
        with(this.fire,
                this.hurtCam,
                this.viewBobbing,
                this.vignette,
                this.nausea,
                this.portal,
                this.spyglass,
                this.sleep,
                this.bossBar,
                this.scoreboard,
                this.titles,
                this.effectIcons,
                this.underwater,
                this.inWall,
                this.totemPop,
                this.crosshair);
    }

    public static NoRender instance() {
        return instance;
    }

    public static boolean hideFire() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.fire.value();
    }

    public static boolean noHurtCam() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.hurtCam.value();
    }

    public static boolean noViewBobbing() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.viewBobbing.value();
    }

    public static boolean hideVignette() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.vignette.value();
    }

    public static boolean hideNausea() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.nausea.value();
    }

    public static boolean hidePortal() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.portal.value();
    }

    public static boolean hideSpyglass() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.spyglass.value();
    }

    public static boolean hideSleep() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.sleep.value();
    }

    public static boolean hideBossBar() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.bossBar.value();
    }

    public static boolean hideScoreboard() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.scoreboard.value();
    }

    public static boolean hideTitles() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.titles.value();
    }

    public static boolean hideEffectIcons() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.effectIcons.value();
    }

    public static boolean hideUnderwater() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.underwater.value();
    }

    public static boolean hideInWall() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.inWall.value();
    }

    public static boolean hideTotemPop() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.totemPop.value();
    }

    public static boolean hideCrosshair() {
        NoRender module = instance;
        return module != null && module.isEnabled() && module.crosshair.value();
    }
}
