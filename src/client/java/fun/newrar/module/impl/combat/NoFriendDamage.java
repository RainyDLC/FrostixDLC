package fun.newrar.module.impl.combat;

import fun.newrar.Client;
import fun.newrar.manager.event_impl.AttackEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;

@ModuleInfo(
        name = "No Friend Damage",
        desc = "Блокирует нанесение урона игрокам, добавленным в список друзей",
        category = Category.COMBAT
)
public class NoFriendDamage extends Module {
    @EventHandler
    public void onEvent(AttackEvent event) {
        if(Client.get().friendManager().isFriend(event.getTarget().getName().getString())) {
            event.cancel();
        }
    }
}

