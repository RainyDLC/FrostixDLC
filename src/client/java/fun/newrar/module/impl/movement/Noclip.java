package fun.newrar.module.impl.movement;

import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.math.ChatUtils;
import fun.newrar.utils.other.Instance;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
        name = "Noclip",
        desc = "Прохождение сквозь блоки: Grim двигает транспорт (позиция транспорта принимается от клиента и не откатывается), Vanilla — одиночная игра",
        category = Category.MOVEMENT
)
public class Noclip extends Module {
    public static Noclip getInstance() {
        return Instance.get(Noclip.class);
    }

    public ModeSetting mode = new ModeSetting(this, "Режим", "Grim", "Vanilla");

    public SliderSetting speed = new SliderSetting(this, "Скорость", 0.6F, 0.1F, 2.0F, 0.05F);

    public SliderSetting vertical = new SliderSetting(this, "Вертикаль", 0.45F, 0.1F, 1.5F, 0.05F)
            .setVisible(() -> mode.is("Grim"));

    public BooleanSetting resetFall = new BooleanSetting(this, "Сбрасывать урон от падения", true);

    private boolean warnedNoVehicle;

    @Override
    protected void onEnable() {
        warnedNoVehicle = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && mode.is("Vanilla")) {
            mc.player.noClip = false;
        }
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Grim")) handleVehicle();
        else handleVanilla();
    }

    private void handleVehicle() {
        Entity vehicle = mc.player.getRootVehicle();
        if (vehicle == null || vehicle == mc.player) {
            if (!warnedNoVehicle) {
                warnedNoVehicle = true;
                ChatUtils.addChatMessage("§7[Noclip] §fСядь в лодку/транспорт: клиент управляет его позицией, Грим транспорт не откатывает");
            }
            return;
        }
        if (!vehicle.isLogicalSideForUpdatingMovement()) return;

        Vec3d delta = inputDelta(speed.getValue(), vertical.getValue());
        if (delta.lengthSquared() <= 0.0) return;

        vehicle.setVelocity(Vec3d.ZERO);
        vehicle.setPosition(vehicle.getX() + delta.x, vehicle.getY() + delta.y, vehicle.getZ() + delta.z);
        vehicle.fallDistance = 0.0;

        if (resetFall.getValue()) mc.player.fallDistance = 0.0;
        mc.player.setPosition(vehicle.getPassengerRidingPos(mc.player));
    }

    private void handleVanilla() {
        mc.player.noClip = true;
        mc.player.setVelocity(inputDelta(speed.getValue(), vertical.getValue()));
        mc.player.fallDistance = 0.0;
        mc.player.setOnGround(false);
    }

    private Vec3d inputDelta(float speed, float vertical) {
        Vec2f move = mc.player.input.getMovementInput();
        Vec3d look = mc.player.getRotationVec(1.0F);
        float yaw = mc.player.getYaw();

        double x = 0.0, y = 0.0, z = 0.0;

        if (move.y != 0.0F) {
            x += look.x * move.y * speed;
            y += look.y * move.y * speed;
            z += look.z * move.y * speed;
        }
        if (move.x != 0.0F) {
            double rad = Math.toRadians(yaw);
            x += Math.cos(rad) * move.x * speed;
            z += Math.sin(rad) * move.x * speed;
        }
        if (mc.options.jumpKey.isPressed()) y += vertical;

        return new Vec3d(x, y, z);
    }
}
