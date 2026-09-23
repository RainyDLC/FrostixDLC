package fun.newrar.module.impl.utils;

import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.manager.rotation.FreeLookUtil;
import fun.newrar.manager.rotation.Rotation;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.notification.NotificationManager;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(
        name = "Auto Farm",
        desc = "Автоматический фарм культур на ReallyWorld с Шаром огородника и лопатой",
        category = Category.OTHER
)
public class AutoFarm extends Module {

    public final SliderSetting radius = new SliderSetting(this, "Радиус", 4.2f, 2.0f, 6.0f, 0.1f);
    public final SliderSetting cooldown = new SliderSetting(this, "Кулдаун шара", 30.0f, 5.0f, 60.0f, 1.0f);
    public final SliderSetting breakDelay = new SliderSetting(this, "Задержка ломания", 1.0f, 0.0f, 10.0f, 1.0f);
    public final SliderSetting plantDelay = new SliderSetting(this, "Задержка посадки", 1.0f, 0.0f, 10.0f, 1.0f);

    public final BooleanSetting autoPickup = new BooleanSetting(this, "Подбирать дроп", true);
    public final SliderSetting pickupRadius = new SliderSetting(this, "Радиус подбора", 12.0f, 2.0f, 24.0f, 1.0f);

    public final BooleanSetting autoReplant = new BooleanSetting(this, "Автопосадка", true);
    public final BooleanSetting autoShovel = new BooleanSetting(this, "Брать лопату", true);

    public final BooleanSetting autoChest = new BooleanSetting(this, "Складывать в сундук", true);
    public final SliderSetting chestRadius = new SliderSetting(this, "Дистанция до сундука", 12.0f, 2.0f, 30.0f, 1.0f);
    public final SliderSetting keepCount = new SliderSetting(this, "Оставлять для посадки", 64.0f, 0.0f, 256.0f, 16.0f);

    public final BooleanSetting swapOffhand = new BooleanSetting(this, "Шар во вторую руку", true);
    public final BooleanSetting rotations = new BooleanSetting(this, "Ротации", true);
    public final BooleanSetting anyHead = new BooleanSetting(this, "Любая голова как шар", true);

    public final BooleanSetting farmPotatoes = new BooleanSetting(this, "Картошка", true);
    public final BooleanSetting farmWheat = new BooleanSetting(this, "Пшеница", true);
    public final BooleanSetting farmCarrots = new BooleanSetting(this, "Морковь", true);
    public final BooleanSetting farmBeetroots = new BooleanSetting(this, "Свёкла", true);
    public final BooleanSetting farmNetherWart = new BooleanSetting(this, "Адский нарост", false);

    public enum State {
        HARVEST,
        PICKUP,
        REPLANT,
        GO_TO_CHEST,
        DEPOSITING_CHEST,
        RETURN_TO_FARM,
        WAIT_COOLDOWN,
        GROW_ORB
    }

    private State currentState = State.HARVEST;
    private BlockPos startFarmPos = null;
    private BlockPos targetChestPos = null;

    private long lastOrbUseTime = 0L;
    private boolean isSneakingForOrb = false;
    private int sneakTicks = 0;
    private int harvestWaitTicks = 0;
    private int pickupTimeoutTicks = 0;
    private final Map<BlockPos, Integer> plantFailures = new HashMap<>();
    private int breakCooldownTicks = 0;
    private int plantCooldownTicks = 0;
    private int chestWaitTicks = 0;
    private int chestDepositCooldown = 0;

    private boolean warnedNoOrb = false;
    private boolean warnedNoShovel = false;
    private boolean warnedNoSeeds = false;
    private boolean warnedNoChest = false;

    public AutoFarm() {
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            startFarmPos = mc.player.getBlockPos();
        }

        // Приоритет состояний при включении:
        if (!findMatureCrops().isEmpty()) {
            currentState = State.HARVEST;
        } else if (hasDroppedFarmItems()) {
            currentState = State.PICKUP;
        } else if (autoReplant.getValue() && !findEmptyFarmland().isEmpty() && findSeedSlot() != -1) {
            currentState = State.REPLANT;
        } else if (isCooldownReady()) {
            currentState = State.GROW_ORB;
        } else {
            currentState = State.WAIT_COOLDOWN;
        }

        targetChestPos = null;
        isSneakingForOrb = false;
        sneakTicks = 0;
        harvestWaitTicks = 0;
        pickupTimeoutTicks = 0;
        plantFailures.clear();
        breakCooldownTicks = 0;
        plantCooldownTicks = 0;
        chestWaitTicks = 0;
        chestDepositCooldown = 0;
        warnedNoOrb = false;
        warnedNoShovel = false;
        warnedNoSeeds = false;
        warnedNoChest = false;
    }

    @Override
    protected void onDisable() {
        stopMovement();
        if (mc.player != null) {
            applySneakState(false);
        }
        isSneakingForOrb = false;
        sneakTicks = 0;
        harvestWaitTicks = 0;
        pickupTimeoutTicks = 0;
        plantFailures.clear();
        RotationProcess.currentTask = RotationProcess.RotationTask.IDLE;
        RotationProcess.currentPriority = 0;
        RotationProcess.targetRotation = null;
        FreeLookUtil.active = false;
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (startFarmPos == null) {
            startFarmPos = mc.player.getBlockPos();
        }

        // Если активна фаза приседания для активации шара огородника
        if (isSneakingForOrb) {
            sneakTicks++;
            applySneakState(true);

            // На 2-м тике отправляем также клик предметом (на случай если сервер требует Shift+ПКМ)
            if (sneakTicks == 2) {
                Hand orbHand = isGardenerOrb(mc.player.getMainHandStack()) ? Hand.MAIN_HAND : Hand.OFF_HAND;
                mc.interactionManager.interactItem(mc.player, orbHand);
                mc.player.swingHand(orbHand);
            }

            // Держим приседание 24 тика (~1.2 секунды) для гарантированного срабатывания плагина ReallyWorld
            if (sneakTicks >= 24) {
                applySneakState(false);
                isSneakingForOrb = false;
                sneakTicks = 0;
                lastOrbUseTime = System.currentTimeMillis();
                harvestWaitTicks = 12; // даем 12 тиков на приход пакетов обновления грядок
                NotificationManager.send("Шар огородника активирован! КД " + (int) cooldown.getValue().floatValue() + "с",
                        NotificationManager.Type.INFO, 3000);
                currentState = State.HARVEST;
            }
            return;
        }

        // Если на земле уже лежит неубранная картошка/пшеница и мы не заняты сундуком или шаром — собираем её!
        if (autoPickup.getValue() && hasDroppedFarmItems() &&
                currentState != State.GROW_ORB &&
                currentState != State.GO_TO_CHEST &&
                currentState != State.DEPOSITING_CHEST &&
                currentState != State.PICKUP &&
                !isSneakingForOrb) {
            // Если в радиусе досягаемости нет созревших культур прямо сейчас, сразу идем подбирать
            if (findMatureCrops().isEmpty()) {
                currentState = State.PICKUP;
            }
        }

        if (breakCooldownTicks > 0) {
            breakCooldownTicks--;
            return;
        }
        if (plantCooldownTicks > 0) {
            plantCooldownTicks--;
            return;
        }

        switch (currentState) {
            case HARVEST -> handleHarvest();
            case PICKUP -> handlePickup();
            case REPLANT -> handleReplant();
            case GO_TO_CHEST -> handleGoToChest();
            case DEPOSITING_CHEST -> handleDepositingChest();
            case RETURN_TO_FARM -> handleReturnToFarm();
            case WAIT_COOLDOWN -> handleWaitCooldown();
            case GROW_ORB -> handleGrowOrb();
        }
    }

    private void handleHarvest() {
        stopMovement();
        List<BlockPos> matureCrops = findMatureCrops();

        if (!matureCrops.isEmpty()) {
            harvestWaitTicks = 0;
            warnedNoSeeds = false;
            matureCrops.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getEntityPos())));
            BlockPos target = matureCrops.get(0);

            if (autoShovel.getValue()) {
                int shovelSlot = ensureShovelInHotbar();
                if (shovelSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(shovelSlot);
                    warnedNoShovel = false;
                } else if (!warnedNoShovel) {
                    NotificationManager.send("Лопата не найдена!", NotificationManager.Type.WARNING, 3000);
                    warnedNoShovel = true;
                }
            }

            if (!rotateTo(target, Direction.UP)) {
                return;
            }

            mc.interactionManager.attackBlock(target, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
            breakCooldownTicks = breakDelay.getValue().intValue();
            return;
        }

        // Если только что активировали шар, ждем несколько тиков появления созревших культур
        if (harvestWaitTicks > 0) {
            harvestWaitTicks--;
            return;
        }

        // Созревшие культуры собраны -> проверяем упавший дроп
        if (autoPickup.getValue() && hasDroppedFarmItems()) {
            currentState = State.PICKUP;
        } else if (autoReplant.getValue() && !findEmptyFarmland().isEmpty() && findSeedSlot() != -1) {
            currentState = State.REPLANT;
        } else {
            currentState = isCooldownReady() ? State.GROW_ORB : State.WAIT_COOLDOWN;
        }
    }

    private void handlePickup() {
        // Если инвентарь полон, идем выгружаться в сундук
        if (isInventoryFull() && autoChest.getValue() && hasItemsToDeposit()) {
            stopMovement();
            BlockPos chest = findNearestChest();
            if (chest != null) {
                targetChestPos = chest;
                currentState = State.GO_TO_CHEST;
                return;
            }
        }

        List<ItemEntity> droppedItems = getDroppedFarmItems();

        if (droppedItems.isEmpty()) {
            stopMovement();
            pickupTimeoutTicks = 0;
            currentState = State.REPLANT;
            return;
        }

        pickupTimeoutTicks++;
        if (pickupTimeoutTicks > 100) { // 5 сек таймаут
            stopMovement();
            pickupTimeoutTicks = 0;
            currentState = State.REPLANT;
            return;
        }

        // Находим ближайший упавший предмет
        droppedItems.sort(Comparator.comparingDouble(it -> it.squaredDistanceTo(mc.player)));
        ItemEntity nearestDrop = droppedItems.get(0);

        // Идем прямо к упавшему предмету
        moveTo(nearestDrop.getEntityPos(), 0.5);
    }

    private void handleReplant() {
        stopMovement();

        if (autoReplant.getValue()) {
            List<BlockPos> emptyFarmland = findEmptyFarmland();

            if (!emptyFarmland.isEmpty()) {
                emptyFarmland.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getEntityPos())));
                
                BlockPos farmPos = null;
                for (BlockPos p : emptyFarmland) {
                    if (plantFailures.getOrDefault(p, 0) < 4) {
                        farmPos = p;
                        break;
                    }
                }

                if (farmPos != null) {
                    int seedSlot = findSeedSlot();
                    if (seedSlot == -1) {
                        if (!warnedNoSeeds) {
                            NotificationManager.send("Нет семян для посадки!", NotificationManager.Type.WARNING, 3000);
                            warnedNoSeeds = true;
                        }
                    } else {
                        warnedNoSeeds = false;
                        mc.player.getInventory().setSelectedSlot(seedSlot);

                        if (!rotateTo(farmPos, Direction.UP)) {
                            return;
                        }

                        Vec3d hitVec = new Vec3d(farmPos.getX() + 0.5, farmPos.getY() + 1.0, farmPos.getZ() + 0.5);
                        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, farmPos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        plantFailures.put(farmPos, plantFailures.getOrDefault(farmPos, 0) + 1);
                        plantCooldownTicks = plantDelay.getValue().intValue();
                        return;
                    }
                }
            }
        }

        plantFailures.clear();

        // Если после посадки все еще лежит неубранный дроп — подбираем его
        if (autoPickup.getValue() && hasDroppedFarmItems()) {
            currentState = State.PICKUP;
            return;
        }

        // Если есть урожай на выгрузку в сундук — идем к сундуку
        if (autoChest.getValue() && hasItemsToDeposit()) {
            BlockPos chest = findNearestChest();
            if (chest != null) {
                targetChestPos = chest;
                currentState = State.GO_TO_CHEST;
                warnedNoChest = false;
                return;
            } else if (!warnedNoChest) {
                NotificationManager.send("Сундук не найден поблизости!", NotificationManager.Type.WARNING, 3000);
                warnedNoChest = true;
            }
        }

        // Как только посадка завершена, проверяем кулдаун: если готов - СРАЗУ на GROW_ORB!
        if (isCooldownReady()) {
            currentState = State.GROW_ORB;
        } else {
            currentState = State.WAIT_COOLDOWN;
        }
    }

    private void handleGoToChest() {
        if (targetChestPos == null || mc.world.getBlockState(targetChestPos).isAir()) {
            targetChestPos = findNearestChest();
            if (targetChestPos == null) {
                currentState = State.RETURN_TO_FARM;
                return;
            }
        }

        Vec3d chestCenter = Vec3d.ofCenter(targetChestPos);
        double distSq = mc.player.getEyePos().squaredDistanceTo(chestCenter);

        if (distSq > 3.0 * 3.0) {
            moveTo(chestCenter, 2.2);
        } else {
            stopMovement();
            if (!rotateTo(targetChestPos, Direction.UP)) {
                return;
            }

            BlockHitResult hit = new BlockHitResult(chestCenter, Direction.UP, targetChestPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            chestWaitTicks = 0;
            chestDepositCooldown = 3;
            currentState = State.DEPOSITING_CHEST;
        }
    }

    private void handleDepositingChest() {
        stopMovement();

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            chestWaitTicks++;
            if (chestWaitTicks > 45) { // 2.25 сек таймаут
                currentState = State.RETURN_TO_FARM;
                chestWaitTicks = 0;
            }
            return;
        }

        chestWaitTicks = 0;
        if (chestDepositCooldown > 0) {
            chestDepositCooldown--;
            return;
        }

        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        int totalSlots = screen.getScreenHandler().slots.size();
        boolean moved = false;

        for (int i = containerSize; i < totalSlots; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (slot.hasStack()) {
                ItemStack stack = slot.getStack();
                if (shouldDepositItem(stack)) {
                    mc.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId,
                            i,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                    );
                    chestDepositCooldown = 2;
                    moved = true;
                    return;
                }
            }
        }

        if (!moved) {
            if (mc.player != null) {
                mc.player.closeHandledScreen();
            }
            mc.setScreen(null);
            NotificationManager.send("Урожай выгружен в сундук!", NotificationManager.Type.INFO, 2500);
            currentState = State.RETURN_TO_FARM;
        }
    }

    private void handleReturnToFarm() {
        if (startFarmPos == null) {
            currentState = isCooldownReady() ? State.GROW_ORB : State.WAIT_COOLDOWN;
            return;
        }

        Vec3d farmCenter = Vec3d.ofCenter(startFarmPos);
        double distSq = mc.player.getEyePos().squaredDistanceTo(farmCenter);

        if (distSq > 1.8 * 1.8) {
            moveTo(farmCenter, 1.0);
        } else {
            stopMovement();
            currentState = isCooldownReady() ? State.GROW_ORB : State.WAIT_COOLDOWN;
        }
    }

    public boolean isCooldownReady() {
        if (lastOrbUseTime == 0L) return true;
        long cooldownMs = (long) (cooldown.getValue() * 1000);
        long elapsed = System.currentTimeMillis() - lastOrbUseTime;
        return elapsed >= cooldownMs;
    }

    private void handleWaitCooldown() {
        stopMovement();

        // 1. Проверяем кулдаун Шара огородника В ПЕРВУЮ ОЧЕРЕДЬ!
        if (isCooldownReady()) {
            // Если перед использованием шара есть пустые грядки и есть семена — сначала сажаем их!
            if (autoReplant.getValue() && !findEmptyFarmland().isEmpty() && findSeedSlot() != -1) {
                currentState = State.REPLANT;
                return;
            }
            currentState = State.GROW_ORB;
            return;
        }

        // 2. Если на земле лежит неубранный дроп — идем подбирать!
        if (autoPickup.getValue() && hasDroppedFarmItems()) {
            currentState = State.PICKUP;
            return;
        }

        // 3. Если есть созревший урожай — собираем
        List<BlockPos> mature = findMatureCrops();
        if (!mature.isEmpty()) {
            currentState = State.HARVEST;
            return;
        }

        // 4. Если есть пустые грядки и семена — сажаем
        if (autoReplant.getValue() && !findEmptyFarmland().isEmpty() && findSeedSlot() != -1) {
            currentState = State.REPLANT;
            return;
        }

        // 5. Если в инвентаре накопился урожай для сундука — складываем
        if (autoChest.getValue() && hasItemsToDeposit()) {
            BlockPos chest = findNearestChest();
            if (chest != null) {
                targetChestPos = chest;
                currentState = State.GO_TO_CHEST;
                return;
            }
        }
    }

    private void handleGrowOrb() {
        stopMovement();

        boolean ready = false;

        // 1. Сначала находим и выбираем шар в хотбаре / главной руке
        int hotbarSlot = ensureOrbInHotbar();
        if (hotbarSlot != -1) {
            mc.player.getInventory().setSelectedSlot(hotbarSlot);
            ready = true;
        }

        // 2. Если включена опция "Шар во вторую руку", перемещаем шар во вторую руку
        if (swapOffhand.getValue()) {
            if (ensureOrbInOffhand()) {
                ready = true;
            }
        }

        // 3. Если шар во второй руке, это тоже готово
        if (!ready && isGardenerOrb(mc.player.getOffHandStack())) {
            ready = true;
        }

        if (!ready) {
            if (!warnedNoOrb) {
                NotificationManager.send("Шар огородника не найден в инвентаре/руке!", NotificationManager.Type.WARNING, 3000);
                warnedNoOrb = true;
            }
            currentState = State.WAIT_COOLDOWN;
            return;
        }

        warnedNoOrb = false;
        isSneakingForOrb = true;
        sneakTicks = 0;
        NotificationManager.send("Активация шара огородника...", NotificationManager.Type.INFO, 1500);
        applySneakState(true);
    }

    private void applySneakState(boolean sneaking) {
        if (mc.player == null) return;
        // Нажимаем клавишу приседания
        mc.options.sneakKey.setPressed(sneaking);
        // Эмулируем физическое нажатие Правого Shift и Левого Shift
        KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_RIGHT_SHIFT), sneaking);
        KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_SHIFT), sneaking);

        mc.player.setSneaking(sneaking);
        if (mc.player.input != null) {
            mc.player.input.playerInput = new PlayerInput(false, false, false, false, false, sneaking, false);
        }
        sendSneakPacket(sneaking);
    }

    private void sendSneakPacket(boolean sneaking) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        PlayerInput input = new PlayerInput(false, false, false, false, false, sneaking, false);
        mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(input));
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (event.isSend()) return;

        var packet = event.getPacket();
        Text textComponent = null;

        if (packet instanceof GameMessageS2CPacket chat) {
            textComponent = chat.content();
        } else if (packet instanceof OverlayMessageS2CPacket overlay) {
            textComponent = overlay.text();
        } else if (packet instanceof TitleS2CPacket title) {
            textComponent = title.text();
        } else if (packet instanceof SubtitleS2CPacket subtitle) {
            textComponent = subtitle.text();
        }

        if (textComponent == null) return;
        String text = clean(textComponent.getString());
        if (text.isEmpty()) return;

        // Если сообщение содержит информацию о перезарядке / кд
        if (text.contains("перезаряд") || text.contains("кд") || text.contains("подождите") || text.contains("cooldown")) {
            // Проверяем, завершилась ли перезарядка
            if (text.contains("готов") || text.contains("заверш") || text.contains("оконч") || text.contains("прошл")) {
                lastOrbUseTime = 0L;
                NotificationManager.send("Шар огородника готов к использованию!", NotificationManager.Type.INFO, 2000);
                return;
            }

            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(text);
            if (m.find()) {
                int remainingSec = Integer.parseInt(m.group(1));
                if (remainingSec == 0) {
                    lastOrbUseTime = 0L;
                    NotificationManager.send("Шар огородника готов к использованию!", NotificationManager.Type.INFO, 2000);
                } else {
                    long cooldownTotalMs = (long) (cooldown.getValue() * 1000);
                    lastOrbUseTime = System.currentTimeMillis() - (cooldownTotalMs - (remainingSec * 1000L));
                    NotificationManager.send("Шар на перезарядке: " + remainingSec + "с", NotificationManager.Type.INFO, 1500);
                }
            }
        } else if ((text.contains("огородник") || text.contains("шар")) &&
                   (text.contains("готов") || text.contains("можно") || text.contains("активир"))) {
            lastOrbUseTime = 0L;
            NotificationManager.send("Шар огородника готов к использованию!", NotificationManager.Type.INFO, 2000);
        }
    }

    // --- Перемещение к точке/предмету ---
    private void moveTo(Vec3d target, double stopDist) {
        if (mc.player == null) return;

        double dx = target.x - mc.player.getX();
        double dy = target.y - mc.player.getY();
        double dz = target.z - mc.player.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq <= stopDist * stopDist) {
            stopMovement();
            return;
        }

        // Точный расчет угла поворота в системе координат Minecraft
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        if (rotations.getValue()) {
            Rotation targetRot = new Rotation(targetYaw, mc.player.getPitch());
            RotationProcess.update(targetRot, 140f, 140f, 140f, 140f, 2, 20, false);
        }
        mc.player.setYaw(targetYaw);
        mc.player.headYaw = targetYaw;
        mc.player.bodyYaw = targetYaw;

        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);

        // Прыжок при препятствии или перепаде высоты
        if ((mc.player.horizontalCollision || dy > 0.4) && mc.player.isOnGround()) {
            mc.options.jumpKey.setPressed(true);
        } else {
            mc.options.jumpKey.setPressed(false);
        }
    }

    private void stopMovement() {
        if (mc.player == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    // --- Поиск дропа ---
    private boolean hasDroppedFarmItems() {
        return !getDroppedFarmItems().isEmpty();
    }

    private List<ItemEntity> getDroppedFarmItems() {
        if (mc.world == null || mc.player == null) return new ArrayList<>();
        double r = pickupRadius.getValue();
        Box box = mc.player.getBoundingBox().expand(r, 4.0, r);
        return mc.world.getEntitiesByClass(ItemEntity.class, box, item -> {
            return item.isAlive() && isFarmItem(item.getStack());
        });
    }

    public boolean isFarmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.POTATO ||
                item == Items.POISONOUS_POTATO ||
                item == Items.CARROT ||
                item == Items.WHEAT ||
                item == Items.WHEAT_SEEDS ||
                item == Items.BEETROOT ||
                item == Items.BEETROOT_SEEDS ||
                item == Items.NETHER_WART) {
            return true;
        }

        String name = clean(stack.getName().getString());
        return name.contains("карто") ||
                name.contains("пшен") ||
                name.contains("морков") ||
                name.contains("свекл") ||
                name.contains("свёкл") ||
                name.contains("семен") ||
                name.contains("potato") ||
                name.contains("wheat") ||
                name.contains("carrot") ||
                name.contains("beetroot");
    }

    // --- Поиск сундука ---
    private BlockPos findNearestChest() {
        if (mc.player == null || mc.world == null) return null;
        int r = (int) Math.ceil(chestRadius.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearest = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    BlockState state = mc.world.getBlockState(mutable);
                    Block b = state.getBlock();
                    if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) {
                        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(mutable));
                        if (dist < bestDist) {
                            bestDist = dist;
                            nearest = mutable.toImmutable();
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private boolean hasItemsToDeposit() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (shouldDepositItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldDepositItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();

        // Складываем несажаемые культуры полностью
        if (item == Items.POISONOUS_POTATO || item == Items.WHEAT || item == Items.BEETROOT) {
            return true;
        }

        // Для картошки, моркови и семян оставляем запас для посадки
        if (item == Items.POTATO || item == Items.CARROT || item == Items.NETHER_WART ||
                item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS) {
            return countPlantableItems() > keepCount.getValue().intValue();
        }

        return false;
    }

    private int countPlantableItems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAllowedSeed(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // --- Сканирование блоков грядок ---
    private List<BlockPos> findMatureCrops() {
        List<BlockPos> list = new ArrayList<>();
        int r = (int) Math.ceil(radius.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    if (!isWithinReach(mutable)) continue;
                    BlockState state = mc.world.getBlockState(mutable);
                    if (isMatureCrop(state)) {
                        list.add(mutable.toImmutable());
                    }
                }
            }
        }
        return list;
    }

    private List<BlockPos> findEmptyFarmland() {
        List<BlockPos> list = new ArrayList<>();
        int r = (int) Math.ceil(radius.getValue());
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    if (!isWithinReach(mutable)) continue;
                    BlockState state = mc.world.getBlockState(mutable);
                    if (isEmptyFarmland(mutable, state)) {
                        list.add(mutable.toImmutable());
                    }
                }
            }
        }
        return list;
    }

    private boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.POTATOES && farmPotatoes.getValue()) {
            return state.get(CropBlock.AGE) >= 7;
        }
        if (block == Blocks.WHEAT && farmWheat.getValue()) {
            return state.get(CropBlock.AGE) >= 7;
        }
        if (block == Blocks.CARROTS && farmCarrots.getValue()) {
            return state.get(CropBlock.AGE) >= 7;
        }
        if (block == Blocks.BEETROOTS && farmBeetroots.getValue()) {
            return state.get(BeetrootsBlock.AGE) >= 3;
        }
        if (block == Blocks.NETHER_WART && farmNetherWart.getValue()) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMature(state);
        }
        return false;
    }

    private boolean isEmptyFarmland(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.FARMLAND) {
            BlockState above = mc.world.getBlockState(pos.up());
            return above.isAir() || above.isReplaceable();
        }
        if (block == Blocks.SOUL_SAND && farmNetherWart.getValue()) {
            BlockState above = mc.world.getBlockState(pos.up());
            return above.isAir() || above.isReplaceable();
        }
        return false;
    }

    public boolean isGardenerOrb(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // 1. Если включена опция "любая голова" (по умолчанию true)
        if (anyHead.getValue() && stack.getItem() instanceof PlayerHeadItem) {
            return true;
        }

        // 2. Если игрок держит голову в руке или во второй руке
        if (stack.getItem() instanceof PlayerHeadItem && mc.player != null &&
                (stack == mc.player.getOffHandStack() || stack == mc.player.getMainHandStack())) {
            return true;
        }

        String name = clean(stack.getName().getString());
        if (name.contains("огород") || name.contains("сад") || name.contains("ферм") ||
                name.contains("рост") || name.contains("урожай") ||
                name.contains("шар") || name.contains("сфер") || name.contains("талисман")) {
            return true;
        }

        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String l = clean(line.getString());
                if (l.contains("огород") || l.contains("сад") || l.contains("ферм") ||
                        l.contains("выращ") || l.contains("урожай") || l.contains("рост") ||
                        l.contains("шар") || l.contains("сфер")) {
                    return true;
                }
            }
        }

        return stack.getItem() instanceof PlayerHeadItem;
    }

    public boolean isShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof ShovelItem) return true;

        String name = clean(stack.getName().getString());
        if (name.contains("лопата") || name.contains("крушитель") || name.contains("бур")) return true;

        String key = stack.getItem().getTranslationKey().toLowerCase();
        return key.contains("shovel");
    }

    private int ensureOrbInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (isGardenerOrb(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (isGardenerOrb(mc.player.getInventory().getStack(i))) {
                int targetHotbar = mc.player.getInventory().getSelectedSlot();
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        targetHotbar,
                        SlotActionType.SWAP,
                        mc.player
                );
                return targetHotbar;
            }
        }

        if (isGardenerOrb(mc.player.getOffHandStack())) {
            int targetHotbar = mc.player.getInventory().getSelectedSlot();
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    45, // offhand container slot
                    targetHotbar,
                    SlotActionType.SWAP,
                    mc.player
            );
            return targetHotbar;
        }

        return -1;
    }

    private boolean ensureOrbInOffhand() {
        if (isGardenerOrb(mc.player.getOffHandStack())) {
            return true;
        }

        int orbSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (isGardenerOrb(mc.player.getInventory().getStack(i))) {
                orbSlot = i;
                break;
            }
        }

        if (orbSlot == -1) return false;

        int containerSlot = orbSlot < 9 ? orbSlot + 36 : orbSlot;
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                containerSlot,
                40, // Offhand button
                SlotActionType.SWAP,
                mc.player
        );
        mc.player.networkHandler.sendPacket(
                new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId)
        );
        return true;
    }

    private int ensureShovelInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (isShovel(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }

        // Поиск в инвентаре (9-35) и перемещение в хотбар
        for (int i = 9; i < 36; i++) {
            if (isShovel(mc.player.getInventory().getStack(i))) {
                int targetHotbar = mc.player.getInventory().getSelectedSlot();
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        targetHotbar,
                        SlotActionType.SWAP,
                        mc.player
                );
                return targetHotbar;
            }
        }
        return -1;
    }

    private int findSeedSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAllowedSeed(stack)) return i;
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAllowedSeed(stack)) {
                int targetHotbar = mc.player.getInventory().getSelectedSlot();
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        targetHotbar,
                        SlotActionType.SWAP,
                        mc.player
                );
                return targetHotbar;
            }
        }
        return -1;
    }

    private boolean isAllowedSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.POTATO && farmPotatoes.getValue()) return true;
        if (item == Items.CARROT && farmCarrots.getValue()) return true;
        if (item == Items.WHEAT_SEEDS && farmWheat.getValue()) return true;
        if (item == Items.BEETROOT_SEEDS && farmBeetroots.getValue()) return true;
        if (item == Items.NETHER_WART && farmNetherWart.getValue()) return true;
        return false;
    }

    private boolean isWithinReach(BlockPos pos) {
        if (mc.player == null) return false;
        double maxReach = radius.getValue();
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= maxReach * maxReach;
    }

    private boolean rotateTo(BlockPos pos, Direction side) {
        if (!rotations.getValue()) return true;

        Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5 + side.getOffsetX() * 0.4,
                pos.getY() + 0.5 + side.getOffsetY() * 0.4,
                pos.getZ() + 0.5 + side.getOffsetZ() * 0.4
        );

        Rotation targetRotation = calculateRotation(hitVec);
        RotationProcess.update(targetRotation, 80f, 80f, 80f, 80f, 2, 20, false);
        return new Rotation(mc.player).getDelta(targetRotation) <= 12.0F;
    }

    private Rotation calculateRotation(Vec3d target) {
        if (mc.player == null) return new Rotation(0, 0);
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new Rotation(yaw, pitch);
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase();
    }
}
