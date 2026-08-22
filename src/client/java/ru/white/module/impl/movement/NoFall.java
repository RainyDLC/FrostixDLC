package ru.white.module.impl.movement;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;

@ModuleInfo(
        name = "NoFall",
        desc = "Отключает урон от падения через смену слота",
        category = Category.MOVEMENT
)
public class NoFall extends Module {

    @EventHandler
    public void onEvent(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.fallDistance >= 2.5F && mc.player.getVelocity().y < -0.1) {
            double distToGround = getDistanceToGround();

            double fallSpeed = Math.abs(mc.player.getVelocity().y);
            double triggerThreshold = Math.max(1.5, fallSpeed + 0.5);

            if (distToGround <= triggerThreshold) {
                int prevSlot = mc.player.getInventory().getSelectedSlot();
                int targetSlot = (prevSlot + 1) % 9;

                mc.player.getInventory().setSelectedSlot(targetSlot);
                mc.interactionManager.syncSelectedSlot();
                mc.player.setVelocity(0.0, 1.0, 0.0);
                mc.player.getInventory().setSelectedSlot(prevSlot);
                mc.interactionManager.syncSelectedSlot();
                mc.player.fallDistance = 0.0F;
            }
        }
    }

    private double getDistanceToGround() {
        if (mc.player == null || mc.world == null) return 999.0;

        Vec3d start = mc.player.getEntityPos();
        Vec3d end = start.add(0, -6.0, 0);

        BlockHitResult result = mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return start.y - result.getPos().y;
        }

        return 999.0;
    }
}
