package rtx.kimiko.utils.combat;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.utils.player.PlayerWorldHelper;

public final class MotionUtils {
    private MotionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Vec3d getTargetPoint(Entity entity, boolean useBox) {
        return entity.getEntityPos();
    }

    public static boolean shouldSkipFall(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return false;
        }
        return mc.player.isClimbing()
                || (mc.player.isSubmergedInWater() && PlayerWorldHelper.getBlockAtOffset(0.0, 1.0, 0.0) == Blocks.WATER && mc.player.fallDistance <= 0.0f)
                || mc.player.isGliding()
                || mc.player.isTouchingWater()
                || mc.player.getAbilities().flying
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
    }

    public static boolean shouldSkipFallExtended(LivingEntity target, boolean flag) {
        return shouldSkipFall(target);
    }

    public static boolean isBlocking(LivingEntity target) {
        if (!(target instanceof PlayerEntity player)) {
            return false;
        }
        if (!player.isUsingItem()) {
            return false;
        }
        return player.getActiveItem().getItem() instanceof ShieldItem;
    }

    public static boolean breakShield(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (!isBlocking(target)) {
            return false;
        }
        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof AxeItem || stack.isIn(net.minecraft.registry.tag.ItemTags.AXES))) {
                axeSlot = i;
                break;
            }
        }
        if (axeSlot == -1) {
            return false;
        }
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        boolean needSwitch = axeSlot != currentSlot;
        if (needSwitch) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (needSwitch) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
        }
        return true;
    }

    public static boolean isFacingAway(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return false;
        }
        Vec3d lookVec = target.getRotationVector(0.0f, target.getYaw());
        Vec3d toPlayer = new Vec3d(mc.player.getX() - target.getX(), 0.0, mc.player.getZ() - target.getZ());
        double dist = toPlayer.length();
        if (dist < 0.01) {
            return true;
        }
        return toPlayer.dotProduct(lookVec) > 0.0;
    }

    public static int findRiptideTridentSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof TridentItem) {
                return i;
            }
        }
        return -1;
    }
}
