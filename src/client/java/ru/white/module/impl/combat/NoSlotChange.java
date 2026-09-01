package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

@ModuleInfo(
        name = "No Slot Change",
        desc = "Блокирует серверные пакеты принудительной смены активного слота хотбара",
        category = Category.COMBAT
)
public class NoSlotChange extends Module {
    @EventHandler
    public void onEvent(EventPacket e) {
        if (e.getPacket() instanceof UpdateSelectedSlotS2CPacket) {
            e.setCancelled(true);
        }
    }
}
