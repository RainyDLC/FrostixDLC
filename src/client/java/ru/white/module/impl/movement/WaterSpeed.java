package ru.white.module.impl.movement;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.MoveUtil;

@ModuleInfo(
        name = "WaterSpeed",
        desc = "Ускоряет движение в воде под потолком",
        category = Category.MOVEMENT
)
public class WaterSpeed extends Module {

    private final TimerUtil idleTimer = new TimerUtil();

    private static final float MULTIPLIER_DEPTH_HEAD = 1.048F;
    private static final float MULTIPLIER_DEPTH_ONLY = 1.050F;
    private static final float MULTIPLIER_NO_DEPTH = 1.054F;
    private static final float VERTICAL_DOWN = -0.03F;
    private static final float VERTICAL_UP = 0.019F;
    private static final long IDLE_TIMEOUT_MS = 300L;

    @EventHandler
    public void onEvent(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isTouchingWater()) return;
        if (!isTouchingCeiling()) return;

        processMovement();
        processIdleOscillation();
    }

    private boolean isTouchingCeiling() {
        Vec3d headPos = mc.player.getEntityPos().add(0, mc.player.getHeight(), 0);
        BlockPos checkPos = BlockPos.ofFloored(headPos.x, headPos.y + 0.1, headPos.z);

        BlockState state = mc.world.getBlockState(checkPos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private void processMovement() {
        if (!MoveUtil.isMoving()) return;

        idleTimer.reset();

        float multiplier = calculateMultiplier();
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
    }

    private float calculateMultiplier() {
        if (getDepthStriderLevel() > 0) {
            return hasPlayerHeadInOffhand() ? MULTIPLIER_DEPTH_HEAD : MULTIPLIER_DEPTH_ONLY;
        }
        return MULTIPLIER_NO_DEPTH;
    }

    private int getDepthStriderLevel() {
        ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);
        if (boots.isEmpty()) return 0;

        return mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getEntry(Enchantments.DEPTH_STRIDER.getValue())
                .map(entry -> EnchantmentHelper.getLevel(entry, boots))
                .orElse(0);
    }

    private boolean hasPlayerHeadInOffhand() {
        ItemStack offhand = mc.player.getOffHandStack();
        return !offhand.isEmpty() && offhand.getItem() == Items.PLAYER_HEAD;
    }

    private void processIdleOscillation() {
        if (mc.player.horizontalCollision || MoveUtil.isMoving()) return;
        if (!idleTimer.finished(IDLE_TIMEOUT_MS)) return;

        float verticalDelta = (mc.player.age % 3 == 0) ? VERTICAL_DOWN : VERTICAL_UP;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, velocity.y + verticalDelta, velocity.z);
    }

    @Override
    protected void onEnable() {
        idleTimer.reset();
        super.onEnable();
    }
}
