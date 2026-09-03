package fun.newrar.module.impl.combat;

import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
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

