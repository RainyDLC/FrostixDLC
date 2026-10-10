package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Порт модуля GodModeMenu: открывает меню варпов, прячет его (десинк),
 * после удара по игроку спамит клики по слоту варпа каждый тик.
 *
 * Отличия от исходника (real.kartoshka):
 * - EventAttackPre не нужен: хук стоит в MultiPlayerGameModeMixin#attack.
 * - EventPacket(RECEIVE) заменён тиковой детекцией: сервер закрыл меню ->
 *   containerMenu вернулся к inventoryMenu; телепорт -> скачок позиции.
 * - Отмена CloseHandledScreen идёт через ConnectionMixin (shouldCancelSend).
 * - playerIsPVP() заменён эвристикой inPvp(): недавний входящий/исходящий урон.
 */
public final class GodModeMenu extends Module {
    private static GodModeMenu instance;

    private static final long PVP_WINDOW_MS = 10_000L;
    private static final long SCAN_COOLDOWN_MS = 300L;
    private static final double TELEPORT_DIST_SQ = 100.0;

    private final BoolSetting autoOpen = new BoolSetting("Auto Open", true);
    private final BoolSetting onlyPvp = new BoolSetting("Only In PVP", true);
    private final BoolSetting findByNumber = new BoolSetting("Find By Slot", false);
    private final TextSetting menuName = new TextSetting("Menu Name", "Серверные варпы", "Серверные варпы");
    private final TextSetting warpName = new TextSetting("Warp Name", "end", "end");
    private final TextSetting warpCommand = new TextSetting("Warp Command", "warp", "warp");
    private final SliderSetting menuSlot = new SliderSetting("Menu Slot", 21, 0, 53, 1);
    private final SliderSetting endSlot = new SliderSetting("Warp Slot", 11, 0, 53, 1);

    private enum Phase { IDLE, OPENING, ARMED }

    private Phase phase = Phase.IDLE;
    private boolean spamming;
    private boolean fakeClosed;
    private int cachedContainerId = -1;
    private int cachedEndSlot = -1;
    private long lastScanMs;
    private long lastDamageTakenMs;
    private long lastDamageDealtMs;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLastPos;

    public GodModeMenu() {
        super("GodModeMenu", "Десинк меню варпов + спам кликов", Category.COMBAT);
        instance = this;
        with(autoOpen, onlyPvp, findByNumber, menuName, warpName, warpCommand, menuSlot, endSlot);
    }

    public static GodModeMenu instance() {
        return instance;
    }

    public boolean isFakeClosed() {
        return isEnabled() && fakeClosed;
    }

    /** Вызывается из ConnectionMixin. true = отменить отправку пакета. */
    public boolean shouldCancelSend(Packet<?> packet) {
        return isEnabled() && fakeClosed && packet instanceof ServerboundContainerClosePacket;
    }

    /** Вызывается из MultiPlayerGameModeMixin при попытке атаки. */
    public void onAttack(Entity target) {
        if (!isEnabled()) {
            return;
        }
        if (target instanceof LivingEntity) {
            lastDamageDealtMs = System.currentTimeMillis();
            if (phase == Phase.ARMED && !spamming) {
                spamming = true;
                message("спам кликов запущен");
            }
        }
    }

    @Override
    protected void onEnable() {
        reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (autoOpen.value() && !inPvp()) {
            mc.player.connection.sendCommand(warpCommand.value());
            message("открыл /" + warpCommand.value() + ", жду меню...");
        } else {
            message("открой /" + warpCommand.value() + " сам (в бою команды блочатся)");
        }
        phase = Phase.OPENING;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (fakeClosed && cachedContainerId >= 0 && mc.player != null && mc.player.connection != null) {
            try {
                mc.player.connection.send(new ServerboundContainerClosePacket(cachedContainerId));
            } catch (Exception ignored) {
            }
        }
        reset();
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mc.player.hurtTime > 0) {
            lastDamageTakenMs = System.currentTimeMillis();
        }

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (hasLastPos && (spamming || fakeClosed)) {
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ) {
                message("телепорт произошёл — выключен");
                setEnabled(false);
                return;
            }
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPos = true;

        if (mc.player.isDeadOrDying()) {
            setEnabled(false);
            return;
        }

        if (fakeClosed && mc.player.containerMenu == mc.player.inventoryMenu) {
            fakeClosed = false;
            spamming = false;
            phase = Phase.OPENING;
            message("сервер закрыл меню — открой /" + warpCommand.value() + " заново");
            return;
        }

        if (fakeClosed && mc.gui.screen() instanceof InventoryScreen) {
            mc.gui.setScreen(null);
            mc.mouseHandler.grabMouse();
            return;
        }

        if (phase == Phase.OPENING && mc.gui.screen() instanceof ContainerScreen) {
            scanMenu(mc);
        }

        if (spamming && fakeClosed && cachedContainerId >= 0 && cachedEndSlot >= 0) {
            if (mc.gameMode == null) {
                return;
            }
            if (onlyPvp.value() && !inPvp()) {
                return;
            }
            mc.gameMode.handleInventoryMouseClick(cachedContainerId, cachedEndSlot, 0,
                    ClickType.PICKUP, mc.player);
        }
    }

    private void scanMenu(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastScanMs < SCAN_COOLDOWN_MS) {
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) {
            return;
        }
        int containerSize = Math.max(0, menu.slots.size() - 36);

        int end = findSlot(menu, containerSize, true);
        if (end >= 0) {
            cachedContainerId = menu.containerId;
            cachedEndSlot = end;
            fakeClosed = true;
            mc.gui.setScreen(null);
            mc.mouseHandler.grabMouse();
            phase = Phase.ARMED;
            message("готов, ударь игрока (containerId=" + cachedContainerId + " слот=" + end + ")");
            return;
        }

        int target = findSlot(menu, containerSize, false);
        if (target >= 0 && mc.gameMode != null) {
            lastScanMs = now;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, target, 0,
                    ClickType.PICKUP, mc.player);
        }
    }

    private int findSlot(AbstractContainerMenu menu, int containerSize, boolean target) {
        if (findByNumber.value()) {
            int slot = target ? (int) endSlot.value() : (int) menuSlot.value();
            return slot < containerSize ? slot : -1;
        }
        if (target) {
            String warp = warpName.value().toLowerCase();
            for (int i = 0; i < containerSize; i++) {
                ItemStack stack = menu.slots.get(i).getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                String name = stack.getHoverName().getString().toLowerCase();
                if (name.contains("варп") && name.contains(warp)) {
                    return i;
                }
            }
            return -1;
        }
        String needle = menuName.value().toLowerCase();
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getHoverName().getString().toLowerCase().contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    /** Эвристика вместо playerIsPVP() из исходника: недавний урон = бой. */
    private boolean inPvp() {
        long last = Math.max(lastDamageTakenMs, lastDamageDealtMs);
        return System.currentTimeMillis() - last < PVP_WINDOW_MS;
    }

    private void reset() {
        phase = Phase.IDLE;
        spamming = false;
        fakeClosed = false;
        cachedContainerId = -1;
        cachedEndSlot = -1;
        lastScanMs = 0L;
        hasLastPos = false;
    }

    private void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[GodModeMenu] " + text), false);
        }
    }
}
