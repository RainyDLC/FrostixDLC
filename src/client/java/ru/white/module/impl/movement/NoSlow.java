package ru.white.module.impl.movement;

import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.EventType;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.event_impl.UsingItemEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;

import ru.white.utils.aura.AuraUtil;
import ru.white.utils.math.StopWatchShadow;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;


@ModuleInfo(
        name = "No Slow",
        category = Category.MOVEMENT,
        desc = "Позволяет использавать придметы без замедления"
)
public class NoSlow extends Module {

    public ModeSetting type = new ModeSetting(this,"Режим","ФанТайм","Грим","Тики","Обычный");

    private int ticks = 0;
    private int cycleCounter = 0;

    /** Предыдущий тик был с движением (для перехода «бежал -> стою»). */
    private boolean wasMoving = false;

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
        wasMoving = false;
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
        switch (e.getType()) {
            case EventType.ON -> {
                if (type.is("Обычный") || type.is("Грим")) {
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
            }
        }
    }

    /**
     * Grim: каждый тик ПЕРЕД movement-пакетом воздействуем на его модель
     * замедления (slowedByUsingItem).
     *
     * Пустая вторая рука: USE-пакет с ПРАВИЛЬНЫМ sequence — Grim в
     * handleUseItem видит item == null и снимает свой slowed-флаг
     * (BadPacketsH ждёт lastSequence+1, поэтому шлём только через
     * канонический sendSequencedPacket, sequence=0 мгновенно флагается).
     * Ванильный сервер на PASS-интеракции пустой рукой состояние
     * использования не трогает (проверено по байткоду 1.21.11: ранний
     * return до interactionManager): еда доедает по-настоящему, лук
     * натягивается.
     *
     * Занятая вторая рука (тотем и т.п.): Grim для неюзабельного предмета
     * состояние НЕ меняет (canUse == false), флип невозможен — тогда
     * RELEASE только в движении: полная скорость в беге, а при остановке
     * использование чисто перезапускается и еда доедает стоя.
     */
    @EventHandler
    public void onMotion(MotionEvent event) {
        if (!type.is("Грим")) return;
        if (mc.player == null || mc.world == null || !mc.player.isUsingItem()) return;
        if (mc.interactionManager == null) return;

        Hand hand = mc.player.getActiveHand();
        Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;

        float[] mv = AuraUtil.getMovementFromKeys();
        boolean moving = mv[0] != 0 || mv[1] != 0;

        if (mc.player.getStackInHand(other).isEmpty()) {
            sendSequencedPacket(i -> new PlayerInteractItemC2SPacket(
                    other, i, mc.player.getYaw(), mc.player.getPitch()));
            wasMoving = moving;
            return;
        }

        if (moving) {
            releaseUseItem();
            wasMoving = true;
        } else if (wasMoving) {
            // переход «бежал -> стою»: чисто бросаем использование, чтобы
            // серверная еда началась заново и доела без десинка-цикла
            wasMoving = false;
            if (mc.player.isUsingItem()) {
                mc.player.stopUsingItem();
            }
        }
    }

    /** Серверный «отпуск» предмета: безусловно снимает slowed у Grim. */
    private void releaseUseItem() {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        }
    }

    
    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        mc.interactionManager.sendSequencedPacket(mc.world, packetCreator);
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
            }
        }
        if (type.is("ФанТайм")) {
          //  handleFantimeOffhandSwap();
        }
    }

}
