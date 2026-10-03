package rtx.kimiko.utils.player;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PlayerWorldHelper {
    private PlayerWorldHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isInWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null;
    }

    public static Block getBlockAtOffset(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isInWorld()) return Blocks.AIR;
        BlockPos pos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY() + y, mc.player.getZ() + z);
        return mc.world.getBlockState(pos).getBlock();
    }

    public static boolean isHoldingSword() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || item instanceof TridentItem || item instanceof MaceItem;
    }

    public static boolean hasMovementInput() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.input == null) {
            return false;
        }
        return mc.player.forwardSpeed != 0.0f || mc.player.sidewaysSpeed != 0.0f;
    }

    public static boolean isMovingToward(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return false;
        }
        Vec3d targetVel = target.getVelocity();
        double speed = Math.sqrt(targetVel.x * targetVel.x + targetVel.z * targetVel.z);
        if (speed < 0.1) {
            return false;
        }
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.1) {
            return false;
        }
        double dirX = dx / dist;
        double dirZ = dz / dist;
        double dot = (targetVel.x / speed) * dirX + (targetVel.z / speed) * dirZ;
        return dot < -0.15; // Target is moving towards the player
    }
}
