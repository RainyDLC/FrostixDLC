package fun.newrar.module.impl.movement;

import fun.newrar.manager.event_impl.EventPacket;
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
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
        name = "Noclip",
        desc = "Phase — проход сквозь стену только при упоре в неё (минимум флагов), Boat — свободный полёт в транспорте, Vanilla — одиночная игра",
        category = Category.MOVEMENT
)
public class Noclip extends Module {
    public static Noclip getInstance() {
        return Instance.get(Noclip.class);
    }

    public ModeSetting mode = new ModeSetting(this, "Режим", "Phase", "Boat", "Vanilla");

    public SliderSetting speed = new SliderSetting(this, "Скорость", 0.42F, 0.1F, 1.0F, 0.02F);

    public SliderSetting vertical = new SliderSetting(this, "Вертикаль", 0.3F, 0.1F, 1.0F, 0.02F)
            .setVisible(() -> !mode.is("Vanilla"));

    public BooleanSetting resetFall = new BooleanSetting(this, "Сбрасывать урон от падения", true);

    public BooleanSetting antiSetback = new BooleanSetting(this, "Игнорировать откат", true)
            .setVisible(() -> !mode.is("Vanilla"));

    public BooleanSetting autoRemount = new BooleanSetting(this, "Автопосадка обратно", true)
            .setVisible(() -> mode.is("Boat"));

    private static boolean phasing;
    private static long lastPhaseAt;

    private boolean warnedNoVehicle;
    private Entity lastVehicle;
    private long nextRemountAt;
    private int remountAttempts;

    public static boolean isPhasing() {
        Noclip noclip = getInstance();
        return noclip != null && noclip.isEnabled() && phasing;
    }

    private static boolean isPhaseWindow() {
        return phasing || System.currentTimeMillis() - lastPhaseAt < 500L;
    }

    @Override
    protected void onEnable() {
        warnedNoVehicle = false;
        lastVehicle = null;
        remountAttempts = 0;
        phasing = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        phasing = false;
        if (mc.player != null && mode.is("Vanilla")) {
            mc.player.noClip = false;
        }
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        phasing = false;
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Phase")) handlePhase();
        else if (mode.is("Boat")) handleVehicle();
        else handleVanilla();
    }

    @EventHandler
    public void onPacket(EventPacket e) {
        if (e.isSend() || mode.is("Vanilla") || !antiSetback.getValue()) return;
        if (mc.player == null) return;

        if (mode.is("Phase")) {
            if (!isPhaseWindow()) return;
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket look
                    && mc.player.getEyePos().distanceTo(look.change().position()) < 32.0) {
                e.cancel();
            }
            return;
        }

        if (!mc.player.hasVehicle()) return;
        Entity vehicle = mc.player.getRootVehicle();
        if (vehicle == null || vehicle == mc.player) return;
        int vehicleId = vehicle.getId();

        switch (e.getPacket()) {
            case PlayerPositionLookS2CPacket look -> {
                if (mc.player.getEyePos().distanceTo(look.change().position()) < 32.0) e.cancel();
            }
            case EntityPassengersSetS2CPacket passengers -> {
                if (passengers.getEntityId() != vehicleId) return;
                if (mc.options.sneakKey.isPressed()) return;
                if (!hasPassenger(passengers.getPassengerIds(), mc.player.getId())) e.cancel();
            }
            case EntityPositionS2CPacket position -> {
                if (position.entityId() == vehicleId) e.cancel();
            }
            case EntityPositionSyncS2CPacket sync -> {
                if (sync.id() == vehicleId) e.cancel();
            }
            default -> {}
        }
    }

    private static boolean hasPassenger(int[] ids, int id) {
        for (int candidate : ids) {
            if (candidate == id) return true;
        }
        return false;
    }

    private void handlePhase() {
        Vec3d input = inputDelta(speed.getValue(), vertical.getValue());
        if (input.lengthSquared() <= 0.0) return;

        Box path = mc.player.getBoundingBox().stretch(input.multiply(2.0, 2.0, 2.0));
        if (!mc.world.getBlockCollisions(mc.player, path).iterator().hasNext()) return;

        phasing = true;
        lastPhaseAt = System.currentTimeMillis();

        mc.player.setVelocity(input);
        if (resetFall.getValue()) mc.player.fallDistance = 0.0;
        mc.player.setOnGround(false);
    }

    private void handleVehicle() {
        Entity vehicle = mc.player.getRootVehicle();
        if (vehicle == null || vehicle == mc.player) {
            if (!warnedNoVehicle) {
                warnedNoVehicle = true;
                ChatUtils.addChatMessage("§7[Noclip] §fBoat: сядь в лодку/транспорт");
            }
            return;
        }
        if (!vehicle.isLogicalSideForUpdatingMovement()) return;

        Vec3d delta = inputDelta(speed.getValue(), vertical.getValue());
        if (delta.lengthSquared() <= 0.0) return;

        if (lastVehicle == null || lastVehicle.getId() != vehicle.getId()) remountAttempts = 0;
        lastVehicle = vehicle;

        vehicle.setVelocity(Vec3d.ZERO);
        vehicle.setPosition(vehicle.getX() + delta.x, vehicle.getY() + delta.y, vehicle.getZ() + delta.z);
        vehicle.fallDistance = 0.0;

        if (resetFall.getValue()) mc.player.fallDistance = 0.0;
        mc.player.setPosition(vehicle.getPassengerRidingPos(mc.player));
    }

    private void handleVanilla() {
        mc.player.setVelocity(inputDelta(speed.getValue(), vertical.getValue()));
        mc.player.fallDistance = 0.0;
        mc.player.setOnGround(false);
    }

    @EventHandler
    public void onRemount(EventUpdate e) {
        if (mc.player == null || !mode.is("Boat") || !autoRemount.getValue()) return;
        if (mc.player.hasVehicle() || lastVehicle == null || lastVehicle.isRemoved() || mc.world == null) return;
        if (remountAttempts >= 8) return;

        Entity vehicle = mc.world.getEntityById(lastVehicle.getId());
        if (vehicle == null) {
            lastVehicle = null;
            return;
        }
        if (mc.player.getEyePos().squaredDistanceTo(vehicle.getEntityPos()) > 20.25) {
            lastVehicle = null;
            return;
        }
        if (System.currentTimeMillis() < nextRemountAt) return;

        nextRemountAt = System.currentTimeMillis() + 600L;
        remountAttempts++;

        ActionResult result = mc.interactionManager.interactEntity(mc.player, vehicle, Hand.MAIN_HAND);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
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
