package fun.newrar.module.impl.movement;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.EventType;
import fun.newrar.manager.event_impl.UsingItemEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ModeSetting;

@ModuleInfo(
        name = "No Slow",
        category = Category.MOVEMENT,
        desc = "Устранение замедления при использовании предметов, еды и стрельбе из лука"
)
public class NoSlow extends Module {
    public ModeSetting type = new ModeSetting(this, "Режим", "ФанТайм", "Тики", "Обычный");

    private int ticks = 0;
    private int cycleCounter = 0;

    private boolean crossbowSwapped = false;
    private int savedCrossbowSlot = -1;

    @Override
    public void onDisable() {
        if (crossbowSwapped && mc.player != null) {
            swapSlotWithOffhand(savedCrossbowSlot);
            mc.player.networkHandler.sendPacket(
                    new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId)
            );
        }
        setKey(true);
        crossbowSwapped = false;
        savedCrossbowSlot = -1;
    }

    private int findCrossbowInInventory() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getMainStacks().get(i).getItem() instanceof CrossbowItem)
                return i;
        }
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getMainStacks().get(i).getItem() instanceof CrossbowItem)
                return i;
        }
        return -1;
    }

    private void swapSlotWithOffhand(int inventoryMainSlot) {
        int screenSlot = inventoryMainSlot < 9 ? 36 + inventoryMainSlot : inventoryMainSlot;
        if (!mc.player.isSprinting())
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    screenSlot,
                    40,
                    SlotActionType.SWAP,
                    mc.player
            );
    }

    private void setKey(boolean state) {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
        };
        for (KeyBinding key : movementKeys) {
            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), key.getDefaultKey().getCode());
            key.setPressed(pressed);
        }
    }

    @EventHandler
    public void onSlow(UsingItemEvent e) {
        if (e.getType() == EventType.ON) {
            if (type.is("Обычный")) {
                e.cancel();
            } else if (type.is("Тики")) {
                int[] thresholds = new int[]{2, 2, 2};
                int threshold = thresholds[cycleCounter % 2];
                if (ticks >= threshold) {
                    e.cancel();
                    ticks = 0;
                    cycleCounter++;
                }
            } else if (type.is("ФанТайм")) {
                if (ticks > 0F && mc.player.getItemUseTime() > 1F) {
                    boolean mainHandCrossbow = mc.player.getMainHandStack().getItem() instanceof CrossbowItem;
                    boolean offHandCrossbow = mc.player.getOffHandStack().getItem() instanceof CrossbowItem;
                    BlockPos feetPos = new BlockPos((int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
                    BlockState blockState = mc.world.getBlockState(feetPos);
                    Block block = blockState.getBlock();

                    if (block == Blocks.SNOW && mc.player.isOnGround()) {
                        e.cancel();
                    }

                    if (mainHandCrossbow || offHandCrossbow) {
                        e.cancel();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onUpdate(EventTick event) {
        if (!mc.player.isUsingRiptide()) {
            if (mc.player.isUsingItem()) {
                ticks++;
            } else {
                ticks = 0;
                cycleCounter = 0;
            }
        }
    }
}

