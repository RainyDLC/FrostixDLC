package dev.hatek.client.module.impl.player;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Port of the NoFall module (Grim / Vanilla modes).
 *
 * <p>Grim mode desyncs the landing: when the client lands after a damaging fall it
 * cancels the vanilla movement packet and sends a forged on-ground sequence instead
 * (StatusOnly + ClientTickEnd + Pos/PosRot, or a +0.25 y-spoofed packet near boats/shulkers/happy
 * ghasts), then suppresses further movement packets until the server teleports the player
 * back. Vanilla mode simply spoofs the onGround flag on outgoing movement packets once the
 * fall distance is lethal.</p>
 *
 * <p>Edge landings: Grim validates the grounded state itself instead of trusting the
 * client's onGround flag, so a forged position hanging over an edge (e.g. next to a lower
 * block) is treated as airborne and the damage goes through. To counter that, the forged
 * landing position is snapped to the center/top of the supporting block whenever the
 * landing is not cleanly supported on all four bottom corners. Clean landings are sent
 * with the exact client position, as before.</p>
 *
 * <p>After the Grim sequence completes on ground, the player performs a full jump via the
 * forged input. The jump is load-bearing for the bypass (a weaker direct-velocity hop
 * was tried and broke it), so it stays as a real input jump.</p>
 *
 * <p>Hooks live in {@code dev.hatek.mixin.NoFallPlayerMixin} (sendPosition),
 * {@code dev.hatek.mixin.NoFallKeyboardMixin} (jump input) and
 * {@code dev.hatek.mixin.NoFallPacketMixin} (teleport / velocity tracking).</p>
 */
public final class NoFall extends Module {
    private static final String GRIM = "Grim";
    private static final String VANILLA = "Vanilla";

    private static final double VANILLA_SPOOF_FALL = 2.0;
    private static final double MIN_LANDING_DROP = 0.05;
    private static final double SPOOF_OFFSET = 0.25;
    private static final double HARD_ENTITY_RANGE = 5.5;
    private static final double SUPPORT_EPS = 0.01;
    private static final double CORNER_INSET = 1.0e-3;
    private static final int TELEPORT_TIMEOUT = 12;
    private static final int VELOCITY_WAIT = 2;
    private static final int MAX_OWED_TICKS = 4;

    private static NoFall instance;

    private final ModeSetting mode = new ModeSetting("Mode", 0, GRIM, VANILLA);

    private double lastFallDistance;
    private int waitTicks = -1;
    private boolean teleported;
    private boolean velocityApplied;
    private int velocityWait;
    private boolean jumpNow;
    private int owedTicks;
    private boolean movedThisTick;
    private boolean vanillaSpoofArmed;
    private ClientLevel lastLevel;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.PLAYER);
        instance = this;
        with(this.mode);
    }

    public static NoFall instance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        reset();
        this.jumpNow = false;
        this.owedTicks = 0;
    }

    /** Called every client tick from {@code ClientEvents.playerTick()} (LocalPlayer.tick HEAD). */
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        this.jumpNow = false;
        if (self == null || level == null) {
            reset();
            return;
        }
        if (level != this.lastLevel) {
            this.lastLevel = level;
            reset();
            this.owedTicks = 0;
        }

        if (this.waitTicks >= 0) {
            if (this.teleported && (this.velocityApplied || ++this.velocityWait > VELOCITY_WAIT)) {
                this.jumpNow = self.onGround();
                reset();
            } else if (++this.waitTicks > TELEPORT_TIMEOUT) {
                reset();
            }
        }

        this.lastFallDistance = self.fallDistance;
    }

    /**
     * Called from {@code NoFallPlayerMixin} at the head of {@code LocalPlayer.sendPosition()}.
     *
     * @param sentYaw   last yaw actually sent to the server (LocalPlayer.yRotLast)
     * @param sentPitch last pitch actually sent to the server (LocalPlayer.xRotLast)
     * @return {@code null} to let vanilla sendPosition run, an empty list to cancel it
     * without sending anything, or the replacement packets to send instead.
     */
    public static List<Packet<?>> replacementFor(LocalPlayer self, float sentYaw, float sentPitch) {
        NoFall module = instance;
        if (module == null || !module.isEnabled() || self == null) {
            return null;
        }

        if (module.mode.is(VANILLA)) {
            module.vanillaSpoofArmed = self.fallDistance > VANILLA_SPOOF_FALL && !self.isFallFlying();
            return null;
        }
        module.vanillaSpoofArmed = false;

        if (module.waitTicks >= 0) {
            return List.of();
        }
        if (!module.landingHurts(self)) {
            return null;
        }

        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        float yaw = self.getYRot();
        float pitch = self.getXRot();
        boolean horizontalCollision = self.horizontalCollision;

        // On edge landings Grim re-checks the grounded state itself and rejects positions
        // hanging over the edge, so snap the forged position onto solid support.
        Vec3 snap = module.landingSnap(self);
        double forgeX = snap != null ? snap.x : x;
        double forgeY = snap != null ? snap.y : y;
        double forgeZ = snap != null ? snap.z : z;

        boolean rotated = yaw != sentYaw || pitch != sentPitch;
        ServerboundMovePlayerPacket landing = rotated
                ? new ServerboundMovePlayerPacket.PosRot(forgeX, forgeY, forgeZ, yaw, pitch, true,
                horizontalCollision)
                : new ServerboundMovePlayerPacket.Pos(forgeX, forgeY, forgeZ, true, horizontalCollision);

        List<Packet<?>> replacement;
        if (module.nearHardEntity(self)) {
            ServerboundMovePlayerPacket spoofed = rotated
                    ? new ServerboundMovePlayerPacket.PosRot(forgeX, forgeY + SPOOF_OFFSET, forgeZ, yaw, pitch,
                    true, horizontalCollision)
                    : new ServerboundMovePlayerPacket.Pos(forgeX, forgeY + SPOOF_OFFSET, forgeZ, true,
                    horizontalCollision);
            replacement = List.of(spoofed);
        } else {
            replacement = List.of(
                    new ServerboundMovePlayerPacket.StatusOnly(true, horizontalCollision),
                    ServerboundClientTickEndPacket.INSTANCE,
                    landing);
            module.owedTicks = Math.min(MAX_OWED_TICKS, module.owedTicks + 1);
        }

        module.waitTicks = 0;
        module.teleported = false;
        module.velocityApplied = false;
        module.velocityWait = 0;
        return replacement;
    }

    /** Called from the onGround redirect inside sendPosition (Vanilla mode). */
    public static boolean spoofOnGround() {
        NoFall module = instance;
        return module != null && module.isEnabled() && module.vanillaSpoofArmed;
    }

    /** Clears the transient vanilla spoof flag after sendPosition finishes. */
    public static void disarmSpoof() {
        NoFall module = instance;
        if (module != null) {
            module.vanillaSpoofArmed = false;
        }
    }

    /**
     * Called from {@code ConnectionMixin} for every outgoing packet.
     *
     * @return {@code true} if the packet must be dropped.
     */
    public static boolean onPacketSend(Packet<?> packet) {
        NoFall module = instance;
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (packet instanceof ServerboundMovePlayerPacket) {
            module.movedThisTick = true;
            return false;
        }
        if (!(packet instanceof ServerboundClientTickEndPacket)) {
            return false;
        }
        boolean cancel = !module.movedThisTick && module.owedTicks > 0;
        if (cancel) {
            module.owedTicks--;
        }
        module.movedThisTick = false;
        return cancel;
    }

    /** Called from {@code NoFallKeyboardMixin}; forces a jump on the forged input. */
    public static boolean consumeJump() {
        NoFall module = instance;
        if (module == null || !module.isEnabled() || !module.jumpNow) {
            return false;
        }
        module.jumpNow = false;
        return true;
    }

    /** Called from {@code NoFallPacketMixin} after the server teleports the player. */
    public static void onTeleportApplied() {
        NoFall module = instance;
        if (module != null && module.isEnabled() && module.waitTicks >= 0) {
            module.teleported = true;
            module.velocityApplied = false;
        }
    }

    /** Called from {@code NoFallPacketMixin} after the server sets an entity's motion. */
    public static void onMotionApplied(int entityId) {
        NoFall module = instance;
        Minecraft mc = Minecraft.getInstance();
        if (module != null && module.teleported && mc.player != null && entityId == mc.player.getId()) {
            module.velocityApplied = true;
        }
    }

    /**
     * Returns a snapped landing spot (center/top of the supporting block) when the player
     * is landing on an edge, or {@code null} when all four bottom corners are cleanly
     * supported and the exact client position can be used.
     */
    private Vec3 landingSnap(LocalPlayer self) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        AABB box = self.getBoundingBox();
        double feetY = box.minY;
        double bestTop = Double.NEGATIVE_INFINITY;
        BlockPos best = null;
        boolean clean = true;
        double[] xs = {box.minX + CORNER_INSET, box.maxX - CORNER_INSET};
        double[] zs = {box.minZ + CORNER_INSET, box.maxZ - CORNER_INSET};
        for (double cx : xs) {
            for (double cz : zs) {
                BlockPos pos = BlockPos.containing(cx, feetY - 0.5, cz);
                BlockState state = level.getBlockState(pos);
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) {
                    clean = false;
                    continue;
                }
                double top = pos.getY() + shape.max(Direction.Axis.Y);
                if (Math.abs(top - feetY) <= SUPPORT_EPS) {
                    if (top > bestTop) {
                        bestTop = top;
                        best = pos;
                    }
                } else {
                    clean = false;
                }
            }
        }
        if (clean || best == null) {
            return null;
        }
        return new Vec3(best.getX() + 0.5, bestTop, best.getZ() + 0.5);
    }

    private boolean landingHurts(LocalPlayer self) {
        if (!self.onGround()
                || self.isPassenger()
                || self.isFallFlying()
                || self.isInWater()
                || self.isInLava()
                || self.getAbilities().flying
                || self.getAbilities().invulnerable) {
            return false;
        }
        return this.lastFallDistance > self.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
                && self.yo - self.getY() > MIN_LANDING_DROP;
    }

    private boolean nearHardEntity(LocalPlayer self) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        AABB area = self.getBoundingBox().inflate(HARD_ENTITY_RANGE);
        for (Entity entity : mc.level.getEntities(self, area)) {
            if (entity instanceof AbstractBoat || entity instanceof Shulker || entity instanceof HappyGhast) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        this.waitTicks = -1;
        this.teleported = false;
        this.velocityApplied = false;
        this.velocityWait = 0;
    }
}
