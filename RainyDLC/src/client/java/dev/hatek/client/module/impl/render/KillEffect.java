package dev.hatek.client.module.impl.render;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.killeffect.KillEffectRenderer;
import net.minecraft.client.player.AbstractClientPlayer;

/** Local, cosmetic death cinematics. No attack, movement or network modifications. */
public final class KillEffect extends Module {
    public static final String MODE_DEMON = "Демоническая сила";
    public static final String MODE_VORTEX = "Вихрь";
    public static final String MODE_SOUL = "Вознесение души";
    public static final String MODE_BLACK_HOLE = "Черная дыра";
    public static final String MODE_LIGHTNING = "Грозовой разряд";
    public static final String MODE_CRYSTAL = "Кристальный распад";

    private static KillEffect instance;

    private final ModeSetting mode = new ModeSetting(
            "Mode", 0,
            MODE_DEMON,
            MODE_VORTEX,
            MODE_SOUL,
            MODE_BLACK_HOLE,
            MODE_LIGHTNING,
            MODE_CRYSTAL
    );
    private final SliderSetting speed = new SliderSetting("Animation Speed", 1, 0.5, 2, 0.1);
    private final SliderSetting radius = new SliderSetting("Effect Size", 1, 0.65, 1.5, 0.05);

    public KillEffect() {
        super("KillEffect", "Textured, animated player death cinematics", Category.RENDER);
        instance = this;
        with(mode, speed, radius, new ButtonSetting("Preview", "Play effect", this::preview));
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static void death(AbstractClientPlayer player) {
        if (!active()) return;
        KillEffect m = instance;
        KillEffectRenderer.onDeath(player, m.mode.value(), m.speed.value(), m.radius.value());
    }

    public static boolean hides(int id) {
        return active() && KillEffectRenderer.hides(id);
    }

    @Override
    public void onClientTick() {
        KillEffectRenderer.tick(mode.value(), speed.value(), radius.value());
    }

    private void preview() {
        if (isEnabled()) {
            KillEffectRenderer.preview(mode.value(), speed.value(), radius.value());
        }
    }

    @Override
    protected void onEnable() {
        KillEffectRenderer.clear();
    }

    @Override
    protected void onDisable() {
        KillEffectRenderer.clear();
    }
}
