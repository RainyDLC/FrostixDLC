package ru.white.module.impl.combat;

import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.movement.Sprint;
import ru.white.utils.math.StopWatchP;
import ru.white.utils.notification.NotificationManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.stream.IntStream;

@ModuleInfo(
        name = "Auto Totem",
        desc = "Автоматическое перемещение тотема бессмертия во вторую руку при угрозе здоровью",
        category = Category.COMBAT
)
public class AutoTotem extends Module {

    /** Кнопка свапа в оффхенд в терминах ScreenHandler. */
    private static final int OFFHAND_BUTTON = 40;
    /** Минимальное время ожидания, даже если сервер ответил мгновенно (пакет применяется не сразу). */
    private static final long MIN_CONFIRM_MS = 60L;
    /** Пауза перед повторной попыткой, если сервер откатил свап. */
    private static final long RETRY_MS = 120L;
    /** Пауза после того, как исчерпаны все попытки. */
    private static final long COOLDOWN_MS = 1000L;

    private final BooleanSetting elytraHealthCheck = new BooleanSetting(this, "Здоровье с элитрами", true);
    private final BooleanSetting tntCheck = new BooleanSetting(this, "Динамит", true);
    private final BooleanSetting fallCheck = new BooleanSetting(this, "Падение", false);
    private final BooleanSetting crystalCheck = new BooleanSetting(this, "Эндер-кристалл", false);
    private final BooleanSetting notSwapifEat = new BooleanSetting("Не свап при еде", true);
    private final SliderSetting health = new SliderSetting(this,"Здоровье", 4.0F, 1.0F, 20.0F, 0.5F);
    private final SliderSetting elytraHealth = new SliderSetting(this,"Здоровье на элитре", 9.0F, 0.0F, 20.0F, 0.5F).setVisible(() -> elytraHealthCheck.getValue());
    private final SliderSetting crystalDistance = new SliderSetting(this,"Дистанция до крист", 4.0F, 1.0F, 10.0F, 1.0F).setVisible(() -> crystalCheck.getValue());
    private final SliderSetting tntDistance = new SliderSetting(this,"Дистанция до тнт", 30.0F, 3.0F, 50.0F, 1.0F).setVisible(() -> tntCheck.getValue());
    private final BooleanSetting noBall = new BooleanSetting(this,"Не свапать если шар", false);

    private final BooleanSetting confirmSwap  = new BooleanSetting(this, "Проверять свап", true);
    private final SliderSetting  confirmDelay = new SliderSetting(this, "Задержка проверки", 150.0F, 50.0F, 600.0F, 25.0F)
            .setVisible(() -> confirmSwap.getValue());
    private final SliderSetting  maxAttempts  = new SliderSetting(this, "Попыток", 3.0F, 1.0F, 5.0F, 1.0F)
            .setVisible(() -> confirmSwap.getValue());
    private final SliderSetting  hysteresis   = new SliderSetting(this, "Гистерезис", 1.0F, 0.0F, 5.0F, 0.5F);
    private final BooleanSetting notify       = new BooleanSetting(this, "Уведомления", true);

    private enum Goal { NONE, TOTEM, RETURN }

    /** Защелка: true — тотем нужен в руке. Снимается только с учётом гистерезиса. */
    private boolean active;
    /** Отправлен клик, ждём ответа сервера и проверяем результат. */
    private Goal pendingGoal = Goal.NONE;
    private int pendingSlot = -1;
    private ItemStack pendingStack = ItemStack.EMPTY;
    private final StopWatchP swapWatch = new StopWatchP();
    private boolean inventorySynced;

    private int attempts;
    private long nextAttemptAt;

    private boolean keysReleased;

    /** Слот инвентаря (0..35), куда ушёл предмет из оффхенда, и сам предмет. */
    private int returnSlot = -1;
    private ItemStack returnItem = ItemStack.EMPTY;

    int nonEnchantedTotems;
    private int lastNotifiedTotemSlot = -1;

    @EventHandler
    public void update(EventUpdate eventUpdate) {
        if (mc.player == null || !mc.player.isAlive() || mc.world == null) {
            reset(true);
            return;
        }

        this.nonEnchantedTotems = (int) IntStream.range(0, 36)
                .mapToObj((i) -> mc.player.getInventory().getStack(i))
                .filter((s) -> s.getItem() == Items.TOTEM_OF_UNDYING && !s.hasGlint())
                .count();

        // Порог здоровья с гистерезисом: иначе на границе значения модуль
        // каждый тик дёргает инвентарь туда-обратно и сервер откатывает клики.
        updateLatch();

        if (pendingGoal != Goal.NONE) {
            if (!confirmSwap.getValue()) {
                finishPending(true);
                return;
            }

            long waited = swapWatch.getTime();
            long need   = confirmDelay.getValue().longValue();
            // Ждём либо полный таймаут, либо ответ сервера (но не меньше MIN_CONFIRM_MS).
            if (waited < need && !(inventorySynced && waited >= MIN_CONFIRM_MS)) {
                return;
            }
            finishPending(verify());
            return;
        }

        if (checkToAttack()) return;
        if (System.currentTimeMillis() < nextAttemptAt) return;

        if (active) {
            if (!isTotemInHands()) {
                tryTakeTotem();
            }
        } else {
            tryReturnItem();
        }
    }

    /** Сервер прислал синхронизацию инвентаря — значит ответ на наш клик уже дошёл. */
    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof InventoryS2CPacket
                || packet instanceof ScreenHandlerSlotUpdateS2CPacket
                || packet instanceof SetPlayerInventoryS2CPacket) {
            inventorySynced = true;
        }
    }

    // ------------------------------------------------------------------ логика

    private void updateLatch() {
        if (isDanger()) {
            active = true;
        } else if (mc.player.getHealth() + this.getAbsorption() >= this.health.getValue() + this.hysteresis.getValue()) {
            active = false;
            lastNotifiedTotemSlot = -1;
        }
    }

    private void tryTakeTotem() {
        int slot = this.findNonEnchantedTotemSlot();
        if (slot < 0) return;

        // Запоминаем, что лежало в оффхенде, чтобы потом вернуть.
        ItemStack offhand = mc.player.getOffHandStack();
        if (returnSlot < 0 && !offhand.isEmpty() && !isTotem(offhand)) {
            returnSlot = toInventoryIndex(slot);
            returnItem = offhand.copy();
        }

        int invIndex = toInventoryIndex(slot);
        pendingStack = mc.player.getInventory().getStack(invIndex).copy();

        if (!sendSwap(slot, Goal.TOTEM)) {
            pendingStack = ItemStack.EMPTY;
        }
    }

    private void tryReturnItem() {
        if (returnSlot < 0 || returnItem.isEmpty()) return;

        ItemStack offhand = mc.player.getOffHandStack();
        if (!isTotem(offhand)) {
            clearReturn();
            return;
        }

        if (returnSlot < 0 || returnSlot > 35) {
            clearReturn();
            return;
        }

        // Предмет мог переложить/выбросить — тогда возвращать уже нечего.
        ItemStack stored = mc.player.getInventory().getStack(returnSlot);
        if (!ItemStack.areItemsAndComponentsEqual(stored, returnItem)) {
            clearReturn();
            return;
        }

        sendSwap(toContainerIndex(returnSlot), Goal.RETURN);
    }

    /**
     * Единственная точка, где реально уходит клик.
     * Спринт глушится здесь же, поэтому гонка "модуль спринта успел включить спринт обратно"
     * больше не может отменить свап.
     */
    private boolean sendSwap(int containerSlot, Goal goal) {
        if (mc.player == null || mc.interactionManager == null) return false;

        // Открыт чужой контейнер (сундук и т.п.) — инвентарные индексы не совпадают.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return false;

        releaseKeys();

        AttackAura.stoptick = 3;

        Sprint sprint = Sprint.get();
        if (sprint != null) {
            sprint.tick += 2;
        }
        mc.player.setSprinting(false);

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                containerSlot,
                OFFHAND_BUTTON,
                SlotActionType.SWAP,
                mc.player
        );
        mc.player.networkHandler.sendPacket(
                new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId)
        );

        pendingGoal = goal;
        pendingSlot = containerSlot;
        inventorySynced = false;
        swapWatch.reset();
        return true;
    }

    private void finishPending(boolean ok) {
        Goal goal = pendingGoal;
        int slot = pendingSlot;
        ItemStack stack = pendingStack;

        pendingGoal = Goal.NONE;
        pendingSlot = -1;
        pendingStack = ItemStack.EMPTY;
        inventorySynced = false;
        restoreKeys();

        if (ok) {
            attempts = 0;
            nextAttemptAt = 0L;

            if (goal == Goal.TOTEM) {
                if (notify.getValue() && !stack.isEmpty() && lastNotifiedTotemSlot != slot) {
                    lastNotifiedTotemSlot = slot;
                    NotificationManager.send("Тотем подложен", NotificationManager.Type.MODULE, stack.copy(), 2000);
                }
            } else if (goal == Goal.RETURN) {
                clearReturn();
            }
            return;
        }

        // Сервер откатил свап (или клик потерялся) — пробуем ещё, но не чаще,
        // чем раз в RETRY_MS, и не бесконечно.
        attempts++;
        int limit = Math.max(1, this.maxAttempts.getValue().intValue());
        if (attempts >= limit) {
            attempts = 0;
            nextAttemptAt = System.currentTimeMillis() + COOLDOWN_MS;
        } else {
            nextAttemptAt = System.currentTimeMillis() + RETRY_MS;
        }
    }

    private boolean verify() {
        if (pendingGoal == Goal.TOTEM) {
            return isTotemInHands();
        }
        return !isTotem(mc.player.getOffHandStack());
    }

    // ------------------------------------------------------------------ поиск

    private int findNonEnchantedTotemSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING && !stack.hasGlint()) {
                return i < 9 ? i + 36 : i;
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return i < 9 ? i + 36 : i;
            }
        }

        return -1;
    }

    public boolean isTotemInHands() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (mainHand.getItem() == Items.TOTEM_OF_UNDYING) {
            return !mainHand.hasGlint() || this.nonEnchantedTotems <= 0;
        }

        if (offHand.getItem() == Items.TOTEM_OF_UNDYING) {
            return !offHand.hasGlint() || this.nonEnchantedTotems <= 0;
        }

        return false;
    }

    private static boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private static int toInventoryIndex(int containerSlot) {
        return containerSlot >= 36 ? containerSlot - 36 : containerSlot;
    }

    private static int toContainerIndex(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    // ------------------------------------------------------------------ условия

    private boolean checkToAttack() {
        return (mc.player.isUsingItem() && notSwapifEat.getValue()
                && ( !(mc.player.getActiveItem().getItem() instanceof ShieldItem)));
    }

    private boolean isDanger() {
        return this.elytraCheck()
                || this.checkCrystal()
                || this.checkTnt()
                || this.checkFall()
                || this.checkHealthThreshold();
    }

    private boolean checkHealthThreshold() {
        return mc.player.getHealth() + this.getAbsorption() <= this.health.getValue();
    }

    private boolean elytraCheck() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean elytra = chestStack.getItem() == Items.ELYTRA && elytraHealthCheck.getValue();
        return elytra && this.checkHealth();
    }

    private boolean checkFall() {
        if (!fallCheck.getValue()) {
            return false;
        } else {
            return (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.8F);
        }
    }

    private boolean checkHealth() {
        return mc.player.getHealth() + this.getAbsorption() <= this.elytraHealth.getValue();
    }

    private boolean checkCrystal() {
        if (!crystalCheck.getValue()) {
            return false;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && mc.player.distanceTo(entity) <= this.crystalDistance.getValue()) {
                return !(mc.player.getOffHandStack().getItem() instanceof PlayerHeadItem) || !this.noBall.getValue();
            }
        }
        return false;
    }

    private boolean checkTnt() {
        if (!tntCheck.getValue()) {
            return false;
        }

        for (Entity entity : mc.world.getEntities()) {
            float distance = mc.player.distanceTo(entity);
            if ((entity instanceof TntEntity || entity instanceof TntMinecartEntity) &&
                    distance <= this.tntDistance.getValue()) {
                return true;
            }
        }
        return false;
    }

    private float getAbsorption() {
        return mc.player.getAbsorptionAmount();
    }

    // ------------------------------------------------------------------ состояние

    private void clearReturn() {
        returnSlot = -1;
        returnItem = ItemStack.EMPTY;
    }

    private void releaseKeys() {
        setKey(false);
        keysReleased = true;
    }

    private void restoreKeys() {
        if (!keysReleased) return;
        keysReleased = false;
        setKey(true);
    }

    private void reset(boolean restoreKeys) {
        pendingGoal = Goal.NONE;
        pendingSlot = -1;
        pendingStack = ItemStack.EMPTY;
        inventorySynced = false;
        attempts = 0;
        nextAttemptAt = 0L;
        active = false;
        lastNotifiedTotemSlot = -1;
        clearReturn();
        if (restoreKeys) {
            restoreKeys();
        } else {
            keysReleased = false;
        }
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        reset(false);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset(true);
    }

    private void setKey(boolean state) {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
        };

        for (KeyBinding keyBinding : movementKeys) {
            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);
        }
    }
}
