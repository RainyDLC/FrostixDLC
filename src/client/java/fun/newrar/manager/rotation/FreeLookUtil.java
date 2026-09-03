package fun.newrar.manager.rotation;

import fun.newrar.manager.event_impl.EventLook;
import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventRotation;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.utils.math.ServerUtil;
import net.minecraft.util.math.MathHelper;

public class FreeLookUtil extends Component {
    public static boolean active;

    public static boolean forced;

    public static float freeYaw, freePitch;

    public static void setActive(boolean value) {
        if (!value && forced) return;
        active = value;
    }

    @EventHandler
    public void onEvent(EventLook event) {
        if (active) {
            rotateTowards(event.getYaw(), event.getPitch());
            event.cancel();
        }
    }

    @EventHandler
    public void onEvent(EventRotation event) {
        if (active) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }
    @EventHandler
    public void onEvent(EventTick event) {
        ServerUtil.tick();
    }
    @EventHandler
    public void onPacket(EventPacket e) {
        ServerUtil.packet(e);
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = MathHelper.clamp((float) (freePitch + targetPitch * 0.15D), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15D);
    }
}

