package ru.white.module.impl.movement;

import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.EventType;
import ru.white.manager.event_impl.UsingItemEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;

import ru.white.utils.math.StopWatchShadow;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import ru.white.utils.aura.AuraUtil;


@ModuleInfo(
        name = "No Slow",
        category = Category.MOVEMENT,
        desc = "Позволяет использавать придметы без замедления"
)
public class NoSlow extends Module {

    public ModeSetting type = new ModeSetting(this,"Режим","ФанТайм","Грим","Тики","Обычный");

    private int ticks = 0;
    private int cycleCounter = 0;

    /** Серверу уже отправлен RELEASE_USE_ITEM в текущем цикле использования. */
    private boolean grimReleased = false;

    private boolean crossbowSwapped = false;
    private int savedCrossbowSlot = -1;
    private boolean wasPressingUseWithFood = false;

    private final StopWatchShadow swapWatch = new StopWatchShadow();
    private boolean bypassActive = false;
    private boolean bypassSwapped = false;
    private int pendingSwapSlot = -1;
    private boolean pendingIsRestore = false;


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
        wasPressingUseWithFood = false;
        bypassActive = false;
        bypassSwapped = false;
        pendingSwapSlot = -1;
        grimReleased = false;
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
        if(!mc.player.isSprinting())
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
    
    private void triggerSwap(int slot, boolean isRestore) {
        pendingSwapSlot = slot;
        pendingIsRestore = isRestore;
        bypassActive = true;
        bypassSwapped = false;
        setKey(false);
        swapWatch.reset();
    }


    @EventHandler
    public void onSlow(UsingItemEvent e) {
        Hand first = mc.player.getActiveHand();
        Hand second = first.equals(Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        switch (e.getType()) {
            case EventType.ON -> {
                if(type.is("Обычный")) {
                    e.cancel();
                }
                if(type.is("Тики")) {
                    int[] thresholds = new int[]{2, 2, 2};
                    int threshold = thresholds[cycleCounter % 2];
                    if (ticks >= threshold) {
                        e.cancel();
                        ticks = 0;
                        cycleCounter++;
                    }
                }
                if(type.is("ФанТайм")) {
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
                if(type.is("Грим")) {
                    // Десинк состояния использования: сервер после RELEASE_USE_ITEM
                    // считает предмет отпущенным и не применяет предикт замедления —
                    // каждый тик движения легален (именно так NoSlow-проверка Grim
                    // перестаёт срабатывать). Стоим — ресинк use-пакетами, чтобы
                    // серверная еда доедала нормально.
                    float[] mv = AuraUtil.getMovementFromKeys();
                    boolean moving = mv[0] != 0 || mv[1] != 0;

                    if (moving) {
                        if (!grimReleased) {
                            releaseUseItem();
                            grimReleased = true;
                        }
                        e.cancel();
                    } else if (grimReleased) {
                        interactItem(first);
                        interactItem(second);
                        grimReleased = false;
                    }
                }
            }
        }
    }
    
    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        mc.interactionManager.sendSequencedPacket(mc.world, packetCreator);
    }

    /** Серверный «отпуск» предмета: снимает использование на стороне сервера. */
    private void releaseUseItem() {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        }
    }
    
    public void interactItem(Hand hand) {
        interactItem(hand, new Vec2f(mc.player.getYaw(),mc.player.getPitch()));
    }
    
    public void interactItem(Hand hand, Vec2f angle) {
        sendSequencedPacket(i -> new PlayerInteractItemC2SPacket(hand, i, angle.x, angle.y));
    }

    @EventHandler
    public void onUpdate(EventTick event) {
        if (!mc.player.isUsingRiptide()) {
            if (mc.player.isUsingItem()) {
                ticks++;
            } else {
                ticks = 0;
                cycleCounter = 0;
                grimReleased = false;
            }
        }
        if (type.is("ФанТайм")) {
          //  handleFantimeOffhandSwap();
        }
    }

}
