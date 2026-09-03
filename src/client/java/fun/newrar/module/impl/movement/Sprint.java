package fun.newrar.module.impl.movement;

import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.TriggerBot;
import fun.newrar.utils.aura.TriggerUAttack;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.other.Instance;

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

