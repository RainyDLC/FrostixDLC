package ru.white.module.impl.movement;

import ru.white.Client;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.combat.TriggerBot;
import ru.white.utils.aura.TriggerUAttack;
import ru.white.utils.aura.UAttack;
import ru.white.utils.other.Instance;

@ModuleInfo(
        name = "Sprint",
        desc = "Автоматическое поддержание режима бега при любом перемещении вперед",
        category = Category.MOVEMENT
)
public class Sprint extends Module {
    public static Sprint get() {
        return Instance.get(Sprint.class);
    }

    public int tick;

    @EventHandler
    public void onEvent(WorldLoadEvent e) {
        tick += 4;
    }

    @EventHandler
    public void onEvent(EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!mc.player.isAlive()) {
            tick += 2;
        }

        if (tick != 0) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
            tick--;
            return;
        }

        if (AttackAura.get().isEnabled()) {
            if (UAttack.resetSprintTick(AttackAura.target, AttackAura.get().getRanges())) {
                if (AttackAura.get().typeSprint.is("Silent")) {
                    mc.options.sprintKey.setPressed(false);
                    return;
                }
                if (AttackAura.get().typeSprint.is("Packet")) {
                    mc.player.setSprinting(false);
                    mc.options.sprintKey.setPressed(false);
                    return;
                }
            }
        }

        TriggerBot triggerBot = Client.get().moduleManager().get(TriggerBot.class);
        if (triggerBot != null && triggerBot.isEnabled()) {
            if (TriggerUAttack.resetSprintTick(TriggerBot.targets, triggerBot.getRanges())) {
                if (triggerBot.typeSprint.is("Silent")) {
                    mc.options.sprintKey.setPressed(false);
                    return;
                }
                if (triggerBot.typeSprint.is("Packet")) {
                    mc.player.setSprinting(false);
                    mc.options.sprintKey.setPressed(false);
                    return;
                }
            }
        }

        boolean sneaking = mc.player.isSneaking() && !mc.player.isSwimming();
        if (sneaking) return;

        mc.options.sprintKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }
}
