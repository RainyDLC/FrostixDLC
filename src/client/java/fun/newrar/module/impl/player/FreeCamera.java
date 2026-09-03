package fun.newrar.module.impl.player;

import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import fun.newrar.manager.event_impl.CameraPositionEvent;
import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.InputEvent;
import fun.newrar.manager.event_impl.MoveEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.utils.math.MathUtil;

@ModuleInfo(
        name = "Free Camera",
        desc = "Режим свободной камеры для беспрепятственного осмотра окружения сквозь блоки",
        category = Category.PLAYER
)
public class FreeCamera extends Module {
    public Vec3d pos, prevPos;

    @Override
    public void onEnable() {
        if (mc.getEntityRenderDispatcher().camera != null) {
            prevPos = pos = new Vec3d(mc.getEntityRenderDispatcher().camera.getCameraPos().toVector3f());
        }
        super.onEnable();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        switch (e.getPacket()) {
            case PlayerRespawnS2CPacket respawn -> setEnabled(false);
            case GameJoinS2CPacket join -> setEnabled(false);
            default -> {}
        }
    }

    @EventHandler
    public void onMove(MoveEvent e) {
        e.setMovement(Vec3d.ZERO);
    }

    @EventHandler
    public void onInput(InputEvent e) {
        float speed = 1.5F;
        double[] motion = calculateDirection(e.forward(), e.sideways(), speed);

        prevPos = pos;
        pos = pos.add(motion[0], e.getInput().jump() ? speed : e.getInput().sneak() ? -speed : 0, motion[1]);

        e.inputNone();
    }

    public double[] calculateDirection(float forward, float sideways, double distance) {
        if (mc.player == null) return new double[]{0.0, 0.0};
        float yaw = mc.player.getYaw();
        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += (forward > 0.0f) ? -45 : 45;
            } else if (sideways < 0.0f) {
                yaw += (forward > 0.0f) ? 45 : -45;
            }
            sideways = 0.0f;
            forward = (forward > 0.0f) ? 1.0f : -1.0f;
        }

        double sinYaw = Math.sin(Math.toRadians(yaw + 90.0f));
        double cosYaw = Math.cos(Math.toRadians(yaw + 90.0f));
        double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
        double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;

        return new double[]{xMovement, zMovement};
    }

    @EventHandler
    public void onCameraPosition(CameraPositionEvent e) {
        if (prevPos != null && pos != null) {
            e.setPos(MathUtil.interpolate(prevPos, pos));
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }
}

