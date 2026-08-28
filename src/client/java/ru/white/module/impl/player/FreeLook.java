package ru.white.module.impl.player;

import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.FreeLookUtil;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(
        name = "Free Look",
        desc = "Позволяет крутить свободно камерой.",
        category = Category.PLAYER
)
public class FreeLook extends Module {
    public ModeSetting type = new ModeSetting(this, "Режим", "Зажать", "Нажать");

    public BindSetting bind = new BindSetting(this, "Клавиша", -1);

    private boolean looking;

    @Override
    public void onDisable() {
        if (looking) stopLook();
    }

    @EventHandler
    public void onKey(EventKey event) {
        int key = bind.get();
        if (key <= 0 || event.getKey() != key) return;

        if (type.is("Нажать")) {
            if (looking) stopLook(); else startLook();
        } else if (!looking) {
            startLook();
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        int key = bind.get();
        if (key <= 0) {
            if (looking) stopLook();
            return;
        }

        if (type.is("Зажать")) {
            boolean down = mc.getWindow() != null
                    && GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
            if (down && !looking) startLook();
            else if (!down && looking) stopLook();
        }

        if (looking) FreeLookUtil.active = true;
    }

    private void startLook() {
        if (mc.player == null) return;
        looking = true;

        FreeLookUtil.freeYaw = mc.player.getYaw();
        FreeLookUtil.freePitch = mc.player.getPitch();

        FreeLookUtil.forced = true;
        FreeLookUtil.active = true;

        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private void stopLook() {
        looking = false;
        FreeLookUtil.forced = false;

        AttackAura aura = AttackAura.get();
        if (aura == null || !aura.isEnabled()) {
            FreeLookUtil.active = false;
        }

        if (mc.options != null) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }
}
