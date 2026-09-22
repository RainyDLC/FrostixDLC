package fun.newrar.module.impl.utils;

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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
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

    public final BooleanSetting autoReplant = new BooleanSetting(this, "Автопосадка", true);
    public final BooleanSetting autoShovel = new BooleanSetting(this, "Брать лопату", true);
    public final BooleanSetting swapOffhand = new BooleanSetting(this, "Шар во вторую руку", true);
    public final BooleanSetting rotations = new BooleanSetting(this, "Ротации", true);
    public final BooleanSetting anyHead = new BooleanSetting(this, "Любая голова как шар", false);

    public final BooleanSetting farmPotatoes = new BooleanSetting(this, "Картошка", true);
    public final BooleanSetting farmWheat = new BooleanSetting(this, "Пшеница", true);
    public final BooleanSetting farmCarrots = new BooleanSetting(this, "Морковь", true);
    public final BooleanSetting farmBeetroots = new BooleanSetting(this, "Свёкла", true);
    public final BooleanSetting farmNetherWart = new BooleanSetting(this, "Адский нарост", false);

    private long lastOrbUseTime = 0L;
    private boolean isSneakingForOrb = false;
    private int sneakTicks = 0;
    private int breakCooldownTicks = 0;
    private int plantCooldownTicks = 0;

    private boolean warnedNoOrb = false;
    private boolean warnedNoShovel = false;
    private boolean warnedNoSeeds = false;

    public AutoFarm() {
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        isSneakingForOrb = false;
        sneakTicks = 0;
        breakCooldownTicks = 0;
        plantCooldownTicks = 0;
        warnedNoOrb = false;
        warnedNoShovel = false;
        warnedNoSeeds = false;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.options.sneakKey.setPressed(false);
        }
        isSneakingForOrb = false;
        sneakTicks = 0;
        RotationProcess.currentTask = RotationProcess.RotationTask.IDLE;
        RotationProcess.currentPriority = 0;
        RotationProcess.targetRotation = null;
        FreeLookUtil.active = false;
        super.onDisable();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Обработка зажатия Shift при активации шара огородника
        if (isSneakingForOrb) {
            sneakTicks++;
            mc.options.sneakKey.setPressed(true);

            if (sneakTicks >= 5) {
                mc.options.sneakKey.setPressed(false);
                isSneakingForOrb = false;
                sneakTicks = 0;
                lastOrbUseTime = System.currentTimeMillis();
                NotificationManager.send("Шар огородника активирован! КД " + (int) cooldown.getValue().floatValue() + "с",
                        NotificationManager.Type.INFO, 3000);
            }
            return;
        }

        if (breakCooldownTicks > 0) {
            breakCooldownTicks--;
            return;
        }
        if (plantCooldownTicks > 0) {
            plantCooldownTicks--;
            return;
        }

        handleAutoFarm();
    }

    private void handleAutoFarm() {
        List<BlockPos> matureCrops = new ArrayList<>();
        List<BlockPos> emptyFarmland = new ArrayList<>();
        List<BlockPos> growingCrops = new ArrayList<>();

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
                        matureCrops.add(mutable.toImmutable());
                    } else if (isGrowingCrop(state)) {
                        growingCrops.add(mutable.toImmutable());
                    } else if (isEmptyFarmland(mutable, state)) {
                        emptyFarmland.add(mutable.toImmutable());
                    }
                }
            }
        }

        // Шаг 1: Если есть созревший урожай — ломаем его лопатой
        if (!matureCrops.isEmpty()) {
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

        // Шаг 2: Если есть свободные грядки и включена автопосадка — сажаем
        if (autoReplant.getValue() && !emptyFarmland.isEmpty()) {
            emptyFarmland.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getEntityPos())));
            BlockPos farmPos = emptyFarmland.get(0);

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
                plantCooldownTicks = plantDelay.getValue().intValue();
                return;
            }
        }

        // Шаг 3: Все созревшие собраны, пустые грядки засажены.
        // Если есть растущие культуры, проверяем кулдаун Шара огородника
        long cooldownMs = (long) (cooldown.getValue() * 1000);
        long elapsed = System.currentTimeMillis() - lastOrbUseTime;

        if (!growingCrops.isEmpty() && elapsed >= cooldownMs) {
            triggerOrbActivation();
        }
    }

    private void triggerOrbActivation() {
        if (swapOffhand.getValue()) {
            boolean hasOrbInOffhand = ensureOrbInOffhand();
            if (!hasOrbInOffhand) {
                if (!warnedNoOrb) {
                    NotificationManager.send("Шар огородника не найден!", NotificationManager.Type.WARNING, 3000);
                    warnedNoOrb = true;
                }
                return;
            }
        } else {
            // Если шар не во второй руке, ищем его в хотбаре
            int hotbarOrb = findOrbInHotbar();
            if (hotbarOrb != -1) {
                mc.player.getInventory().setSelectedSlot(hotbarOrb);
            } else if (!isGardenerOrb(mc.player.getOffHandStack())) {
                if (!warnedNoOrb) {
                    NotificationManager.send("Шар огородника не найден!", NotificationManager.Type.WARNING, 3000);
                    warnedNoOrb = true;
                }
                return;
            }
        }

        warnedNoOrb = false;
        // Зажимаем Shift для срабатывания механики на ReallyWorld
        isSneakingForOrb = true;
        sneakTicks = 0;
        mc.options.sneakKey.setPressed(true);
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

    private boolean isGrowingCrop(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.POTATOES && farmPotatoes.getValue()) {
            return state.get(CropBlock.AGE) < 7;
        }
        if (block == Blocks.WHEAT && farmWheat.getValue()) {
            return state.get(CropBlock.AGE) < 7;
        }
        if (block == Blocks.CARROTS && farmCarrots.getValue()) {
            return state.get(CropBlock.AGE) < 7;
        }
        if (block == Blocks.BEETROOTS && farmBeetroots.getValue()) {
            return state.get(BeetrootsBlock.AGE) < 3;
        }
        if (block == Blocks.NETHER_WART && farmNetherWart.getValue()) {
            return state.get(NetherWartBlock.AGE) < 3;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return !crop.isMature(state);
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

        if (anyHead.getValue() && stack.getItem() instanceof PlayerHeadItem) {
            return true;
        }

        String name = stack.getName().getString().toLowerCase();
        if (name.contains("огородник")) return true;

        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().toLowerCase().contains("огородник")) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof ShovelItem) return true;

        String name = stack.getName().getString().toLowerCase();
        if (name.contains("лопата") || name.contains("крушитель") || name.contains("бур")) return true;

        String key = stack.getItem().getTranslationKey().toLowerCase();
        return key.contains("shovel");
    }

    private int findOrbInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (isGardenerOrb(mc.player.getInventory().getStack(i))) {
                return i;
            }
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
}
