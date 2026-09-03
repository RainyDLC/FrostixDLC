package fun.newrar.module.impl.movement;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.MotionEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.utils.other.Instance;

@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(
        name = "Air Stuck",
        category = Category.MOVEMENT,
        desc = "Фиксация позиции персонажа в воздухе с блокировкой вертикального падения"
)
public class AirStuck extends Module {
    public static AirStuck getInstance() {
        return Instance.get(AirStuck.class);
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player != null && !mc.player.isOnGround()) {
            mc.player.setVelocity(0, 0, 0);
        }
    }

    @EventHandler
    public void onMotion(MotionEvent eventMotion) {
        if (mc.player != null && !mc.player.isOnGround()) {
            eventMotion.ground(false);
        }
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (mc.player != null && !mc.player.isOnGround()) {
            if (e.getPacket() instanceof PlayerMoveC2SPacket) {
                e.cancel();
            }
        }
    }
}

