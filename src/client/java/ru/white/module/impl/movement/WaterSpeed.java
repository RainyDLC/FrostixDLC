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
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.MoveUtil;

@ModuleInfo(
        name = "WaterSpeed",
        desc = "Ускоряет движение в воде (под потолком и в открытой воде)",
        category = Category.MOVEMENT
)
public class WaterSpeed extends Module {

    public ModeSetting type = new ModeSetting(this, "Режим", "Везде", "Под потолком");

    public SliderSetting openMultiplier = new SliderSetting(this, "Множитель", 1.03F, 1.0F, 1.06F, 0.001F)
            .setVisible(() -> type.is("Везде"));

    public SliderSetting openSpeedLimit = new SliderSetting(this, "Лимит скорости", 0.25F, 0.1F, 0.5F, 0.01F)
            .setVisible(() -> type.is("Везде"));

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

        boolean ceiling = isTouchingCeiling();
        if (type.is("Под потолком") && !ceiling) return;

        processMovement(ceiling);
        processIdleOscillation(ceiling);
    }

    private boolean isTouchingCeiling() {
        Vec3d headPos = mc.player.getEntityPos().add(0, mc.player.getHeight(), 0);
        BlockPos checkPos = BlockPos.ofFloored(headPos.x, headPos.y + 0.1, headPos.z);

        BlockState state = mc.world.getBlockState(checkPos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private void processMovement(boolean ceiling) {
        if (!MoveUtil.isMoving()) return;

        idleTimer.reset();

        float multiplier;
        if (ceiling) {
            multiplier = calculateCeilingMultiplier();
        } else {
            if (mc.options.jumpKey.isPressed()) return;
            multiplier = openMultiplier.getValue();
        }

        Vec3d velocity = mc.player.getVelocity();
        double x = velocity.x * multiplier;
        double z = velocity.z * multiplier;

        if (!ceiling) {
            double limit = openSpeedLimit.getValue();
            double horizontal = Math.sqrt(x * x + z * z);
            if (horizontal > limit) {
                double scale = limit / horizontal;
                x *= scale;
                z *= scale;
            }
        }

        mc.player.setVelocity(x, velocity.y, z);
    }

    private float calculateCeilingMultiplier() {
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

    private void processIdleOscillation(boolean ceiling) {
        if (!ceiling) return;
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
