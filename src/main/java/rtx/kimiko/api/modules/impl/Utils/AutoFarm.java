package rtx.kimiko.api.modules.impl.Utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import mixin.accessor.MultiPlayerGameModeAccessor;
import net.minecraft.block.BeetrootsBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarrotsBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.PotatoesBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.events.impl.input.KeyPressEvent;
import rtx.kimiko.api.events.impl.render.HudRenderEvent;
import rtx.kimiko.api.events.impl.render.WorldRenderEvent;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.impl.Interface.NotificationsModule;
import rtx.kimiko.api.modules.settings.Setting;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ColorSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.modules.settings.impl.MultiSelectSetting;
import rtx.kimiko.api.modules.settings.impl.SeparatorSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.api.ui.theme.ClientAccent;
import rtx.kimiko.utils.color.ColorEngine;
import rtx.kimiko.utils.inventory.HotbarSwapper;
import rtx.kimiko.utils.inventory.InventoryItems;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.others.RectUtil;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.render.util.world.WorldShapeRenderer;
import sigil.protect.Level;
import sigil.protect.Protect;

@Feature(value={"autofarm"})
public final class AutoFarm extends Module {

    // === Настройки: Шар огородника ===
    private final SeparatorSetting orbSep = (SeparatorSetting) register(
            new SeparatorSetting("Шар огородника")
    );
    private final SliderSetting cooldown = (SliderSetting) register(
            new SliderSetting("Кулдаун (сек)", "Время перезарядки способности шара огородника")
                    .range(5.0f, 60.0f)
                    .increment(1.0f)
                    .setValue(30.0f)
    );
    private final BooleanSetting autoSwap = (BooleanSetting) register(
            new BooleanSetting("Авто-свап в левую руку", "Автоматически класть шар огородника в оффхенд", true)
    );
    private final BooleanSetting checkName = (BooleanSetting) register(
            new BooleanSetting("Проверять название", "Требовать слово 'огородник' или 'шар' в названии предмета", true)
    );
    private final SliderSetting shiftDuration = (SliderSetting) register(
            new SliderSetting("Удержание Shift (мс)", "Длительность удержания приседания при активации")
                    .range(50.0f, 500.0f)
                    .increment(10.0f)
                    .setValue(150.0f)
    );
    private final BooleanSetting rightClick = (BooleanSetting) register(
            new BooleanSetting("Клик ПКМ при активации", "Дополнительно использовать предмет (ПКМ) при активации", false)
    );
    private final BooleanSetting autoReactivate = (BooleanSetting) register(
            new BooleanSetting("Авто-повтор способности", "Автоматически использовать шар повторно после окончания перезарядки", true)
    );

    // === Настройки: Баритон ===
    private final SeparatorSetting baritoneSep = (SeparatorSetting) register(
            new SeparatorSetting("Баритон")
    );
    private final BooleanSetting baritoneCommand = (BooleanSetting) register(
            new BooleanSetting("Команда Baritone", "Отправлять #farm в чат при включении и #stop при выключении", true)
    );
    private final ModeSetting baritoneCmdType = (ModeSetting) register(
            new ModeSetting("Команда", "Какую команду отправлять баритону", "#farm", new String[]{"#farm", "#mine"})
    );

    // === Настройки: Сбор урожая ===
    private final SeparatorSetting harvestSep = (SeparatorSetting) register(
            new SeparatorSetting("Сбор урожая")
    );
    private final BooleanSetting autoHarvest = (BooleanSetting) register(
            new BooleanSetting("Авто-сбор урожая", "Автоматически ломать созревшие культуры вокруг игрока", true)
    );
    private final SliderSetting harvestRadius = (SliderSetting) register(
            new SliderSetting("Дистанция сбора", "Радиус поиска созревших культур вокруг игрока")
                    .range(1.0f, 6.0f)
                    .increment(0.1f)
                    .setValue(4.5f)
    );
    private final SliderSetting breakDelay = (SliderSetting) register(
            new SliderSetting("Задержка ломания (мс)", "Задержка между сбором блоков")
                    .range(0.0f, 300.0f)
                    .increment(10.0f)
                    .setValue(50.0f)
    );
    private final BooleanSetting rotate = (BooleanSetting) register(
            new BooleanSetting("Поворот камеры", "Поворачивать камеру на добываемый блок", false)
    );
    private final BooleanSetting autoReplant = (BooleanSetting) register(
            new BooleanSetting("Авто-посадка", "Автоматически сажать семена обратно на грядку", true)
    );
    private final MultiSelectSetting crops = (MultiSelectSetting) register(
            new MultiSelectSetting("Культуры", "Какие культуры собирать")
                    .value("Морковь", "Свёкла", "Пшеница", "Картофель", "Адский нарост")
                    .selected("Морковь", "Свёкла", "Пшеница", "Картофель", "Адский нарост")
    );

    // === Настройки: Визуалы ===
    private final SeparatorSetting visualsSep = (SeparatorSetting) register(
            new SeparatorSetting("Визуалы")
    );
    private final BooleanSetting showHud = (BooleanSetting) register(
            new BooleanSetting("HUD Статус", "Отображать карточку с таймером кулдауна на экране", true)
    );
    private final BooleanSetting showRadius = (BooleanSetting) register(
            new BooleanSetting("Отображать радиус", "Рисовать круг радиуса действия на земле", true)
    );
    private final BooleanSetting showEsp = (BooleanSetting) register(
            new BooleanSetting("Подсветка культур", "Подсвечивать созревшие культуры в 3D мире", true)
    );
    private final ColorSetting readyColor = (ColorSetting) register(
            new ColorSetting("Цвет готовности", "Цвет радиуса когда способность готова к использованию", new Color(40, 230, 120, 180))
    );
    private final ColorSetting cooldownColor = (ColorSetting) register(
            new ColorSetting("Цвет перезарядки", "Цвет радиуса когда способность перезаряжается", new Color(255, 95, 75, 180))
    );
    private final ColorSetting cropEspColor = (ColorSetting) register(
            new ColorSetting("Цвет урожая", "Цвет подсветки созревших культур", new Color(60, 240, 100, 120))
    );

    // === Внутреннее состояние ===
    private long lastActivationTime = 0L;
    private long cooldownEndMs = 0L;
    private boolean isShifting = false;
    private long shiftStartTime = 0L;
    private boolean baritoneStarted = false;
    private long lastBreakTime = 0L;
    private BlockPos lastBrokenPos = null;
    private Block lastBrokenBlock = null;
    private final List<Box> matureCropBoxes = new ArrayList<>();
    private long lastOrbMissingAlert = 0L;

    public AutoFarm() {
        super("Auto Farm", "Автоматически использует Шар огородника и фармит созревшие культуры (морковь, свёклу, пшеницу, картофель).", Category.PLAYER);
    }

    @Override
    @Protect(value = Level.CROWN)
    protected void onEnable() {
        this.lastActivationTime = 0L;
        this.cooldownEndMs = 0L;
        this.isShifting = false;
        this.shiftStartTime = 0L;
        this.baritoneStarted = false;
        this.lastBreakTime = 0L;
        this.lastBrokenPos = null;
        this.lastBrokenBlock = null;
        this.lastOrbMissingAlert = 0L;

        // Немедленная попытка активации способности
        this.triggerActivation();
    }

    @Override
    @Protect(value = Level.CROWN)
    protected void onDisable() {
        // Снимаем шифт если удерживался
        if (this.isShifting) {
            this.releaseShift();
        }

        // Останавливаем баритон если запускали
        if (this.baritoneCommand.getValue() && this.baritoneStarted) {
            this.sendChatMessage("#stop");
            this.baritoneStarted = false;
        }

        this.lastBrokenPos = null;
        this.lastBrokenBlock = null;
        this.matureCropBoxes.clear();
    }

    @EventHandler
    public final void onTick(@NotNull TickEvent event) {
        if (!event.isPre()) {
            return;
        }
        ClientPlayerEntity player = this.mc.player;
        ClientWorld world = this.mc.world;
        ClientPlayerInteractionManager interactionManager = this.mc.interactionManager;
        if (player == null || world == null || interactionManager == null || this.mc.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();

        // 1. Отпускание шифта по истечении длительности нажатия
        if (this.isShifting) {
            if (now - this.shiftStartTime >= (long) this.shiftDuration.getValue()) {
                this.releaseShift();
            }
        }

        // 2. Проверка окончания кулдауна и повторная активация способности
        if (!this.isShifting && this.autoReactivate.getValue()) {
            if (now >= this.cooldownEndMs && this.cooldownEndMs > 0L) {
                this.triggerActivation();
            }
        }

        // 3. Авто-посадка семян на освободившуюся грядку
        if (this.autoReplant.getValue() && this.lastBrokenPos != null) {
            this.tryReplant(player, world, interactionManager);
        }

        // 4. Автоматический сбор созревших культур вокруг игрока
        if (this.autoHarvest.getValue()) {
            if (now - this.lastBreakTime >= (long) this.breakDelay.getValue()) {
                this.harvestNextCrop(player, world, interactionManager, now);
            }
        }
    }

    /**
     * Попытка активации способности «Шара огородника».
     */
    private void triggerActivation() {
        ClientPlayerEntity player = this.mc.player;
        if (player == null) {
            return;
        }

        // Проверяем наличие шара в руках
        if (!this.hasOrbInHand(player)) {
            if (this.autoSwap.getValue()) {
                boolean swapped = HotbarSwapper.swapToOffhand(this::isGardenerOrb);
                if (!swapped && !this.hasOrbInHand(player)) {
                    this.alertOrbMissing();
                    return;
                }
            } else {
                this.alertOrbMissing();
                return;
            }
        }

        // Зажимаем Shift и эмулируем нажатие Right Shift
        this.pressShift();

        // Дополнительный клик ПКМ если включена соответствующая настройка
        if (this.rightClick.getValue() && this.mc.interactionManager != null) {
            this.mc.interactionManager.interactItem(player, Hand.OFF_HAND);
        }

        this.lastActivationTime = System.currentTimeMillis();
        this.cooldownEndMs = this.lastActivationTime + (long) (this.cooldown.getValue() * 1000L);

        NotificationsModule.notify("Шар огородника активирован! (" + (int) this.cooldown.getValue() + "с)", 2500L);

        // Отправка команды Баритону для добычи урожая
        if (this.baritoneCommand.getValue()) {
            this.sendChatMessage(this.baritoneCmdType.getValue());
            this.baritoneStarted = true;
        }
    }

    /**
     * Зажатие шифта (игровой sneak + правый шифт).
     */
    private void pressShift() {
        ClientPlayerEntity player = this.mc.player;
        if (player == null) {
            return;
        }

        this.mc.options.sneakKey.setPressed(true);
        player.setSneaking(true);

        // Отправляем KeyPressEvent с кодом 344 (GLFW_KEY_RIGHT_SHIFT)
        try {
            EventBus.Companion.get().post(new KeyPressEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0, KeyPressEvent.Action.PRESS));
        } catch (Throwable ignored) {}

        this.isShifting = true;
        this.shiftStartTime = System.currentTimeMillis();
    }

    /**
     * Отпускание шифта.
     */
    private void releaseShift() {
        ClientPlayerEntity player = this.mc.player;
        if (player == null) {
            this.isShifting = false;
            return;
        }

        this.mc.options.sneakKey.setPressed(false);
        player.setSneaking(false);

        try {
            EventBus.Companion.get().post(new KeyPressEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0, KeyPressEvent.Action.RELEASE));
        } catch (Throwable ignored) {}

        this.isShifting = false;
    }

    /**
     * Проверка: является ли предмет «Шаром огородника».
     */
    public boolean isGardenerOrb(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!stack.isOf(Items.PLAYER_HEAD)) {
            return false;
        }
        if (!this.checkName.getValue()) {
            return true;
        }
        return InventoryItems.nameContains(stack, "огородник")
                || InventoryItems.nameContains(stack, "шар")
                || InventoryItems.nameContains(stack, "сфера");
    }

    private boolean hasOrbInHand(@NotNull ClientPlayerEntity player) {
        return this.isGardenerOrb(player.getOffHandStack()) || this.isGardenerOrb(player.getMainHandStack());
    }

    private void alertOrbMissing() {
        long now = System.currentTimeMillis();
        if (now - this.lastOrbMissingAlert >= 5000L) {
            NotificationsModule.notify("Шар огородника не найден в инвентаре!", 3000L);
            this.lastOrbMissingAlert = now;
        }
    }

    /**
     * Поиск и ломание ближайшей созревшей культуры.
     */
    private void harvestNextCrop(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, long now) {
        float maxDist = this.harvestRadius.getValue();
        int r = (int) Math.ceil(maxDist);
        BlockPos playerPos = player.getBlockPos();

        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double dSq = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dSq > (double) (maxDist * maxDist)) {
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);
                    if (this.isMatureCrop(state, pos, world)) {
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestPos = pos;
                        }
                    }
                }
            }
        }

        if (bestPos != null) {
            if (this.rotate.getValue()) {
                this.lookAt(new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5));
            }

            Block targetBlock = world.getBlockState(bestPos).getBlock();
            interactionManager.attackBlock(bestPos, Direction.UP);
            player.swingHand(Hand.MAIN_HAND);

            this.lastBreakTime = now;
            this.lastBrokenPos = bestPos;
            this.lastBrokenBlock = targetBlock;
        }
    }

    /**
     * Попытка пересадить семена на опустевшую грядку.
     */
    private void tryReplant(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager) {
        BlockPos pos = this.lastBrokenPos;
        Block broken = this.lastBrokenBlock;
        if (pos == null || broken == null) {
            return;
        }

        BlockState current = world.getBlockState(pos);
        if (!current.isAir()) {
            return;
        }

        Block floor = world.getBlockState(pos.down()).getBlock();
        if (floor != Blocks.FARMLAND && floor != Blocks.SOUL_SAND) {
            this.lastBrokenPos = null;
            this.lastBrokenBlock = null;
            return;
        }

        Item seed = this.getSeedForCrop(broken);
        if (seed == null) {
            this.lastBrokenPos = null;
            this.lastBrokenBlock = null;
            return;
        }

        // Если семена в левой руке
        if (player.getOffHandStack().isOf(seed)) {
            BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.down(), false);
            interactionManager.interactBlock(player, Hand.OFF_HAND, hit);
            player.swingHand(Hand.OFF_HAND);
            this.lastBrokenPos = null;
            this.lastBrokenBlock = null;
            return;
        }

        // Поиск в хотбаре
        int slot = this.findItemInHotbar(player, seed);
        if (slot != -1) {
            int prev = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            ((MultiPlayerGameModeAccessor) interactionManager).kimiko$ensureHasSentCarriedItem();

            BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.down(), false);
            interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
            player.swingHand(Hand.MAIN_HAND);

            player.getInventory().setSelectedSlot(prev);
            ((MultiPlayerGameModeAccessor) interactionManager).kimiko$ensureHasSentCarriedItem();
        }

        this.lastBrokenPos = null;
        this.lastBrokenBlock = null;
    }

    /**
     * Проверка: созрела ли культура.
     */
    private boolean isMatureCrop(BlockState state, BlockPos pos, ClientWorld world) {
        Block block = state.getBlock();

        if (block instanceof CropBlock crop) {
            if (block instanceof CarrotsBlock && !this.crops.is("Морковь")) {
                return false;
            }
            if (block instanceof BeetrootsBlock && !this.crops.is("Свёкла")) {
                return false;
            }
            if (block instanceof PotatoesBlock && !this.crops.is("Картофель")) {
                return false;
            }
            if (!(block instanceof CarrotsBlock || block instanceof BeetrootsBlock || block instanceof PotatoesBlock)
                    && !this.crops.is("Пшеница")) {
                return false;
            }
            return crop.isMature(state);
        }

        if (block instanceof NetherWartBlock && this.crops.is("Адский нарост")) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }

        if (block instanceof CocoaBlock && this.crops.is("Какао-бобы")) {
            return state.get(CocoaBlock.AGE) >= 2;
        }

        if (block instanceof SugarCaneBlock && this.crops.is("Тростник")) {
            return world.getBlockState(pos.down()).isOf(Blocks.SUGAR_CANE);
        }

        return false;
    }

    @Nullable
    private Item getSeedForCrop(@NotNull Block block) {
        if (block instanceof CarrotsBlock || block == Blocks.CARROTS) {
            return Items.CARROT;
        }
        if (block instanceof PotatoesBlock || block == Blocks.POTATOES) {
            return Items.POTATO;
        }
        if (block instanceof BeetrootsBlock || block == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        }
        if (block instanceof CropBlock || block == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        }
        if (block instanceof NetherWartBlock || block == Blocks.NETHER_WART) {
            return Items.NETHER_WART;
        }
        if (block instanceof CocoaBlock || block == Blocks.COCOA) {
            return Items.COCOA_BEANS;
        }
        return null;
    }

    private int findItemInHotbar(@NotNull ClientPlayerEntity player, @NotNull Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private void lookAt(@NotNull Vec3d target) {
        ClientPlayerEntity player = this.mc.player;
        if (player == null) {
            return;
        }
        double dx = target.x - player.getX();
        double dy = target.y - (player.getY() + (double) player.getStandingEyeHeight());
        double dz = target.z - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) {
            return;
        }

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, dist)), -90.0, 90.0);

        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private void sendChatMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        ClientPlayerEntity player = this.mc.player;
        if (player != null && player.networkHandler != null) {
            if (message.startsWith("/")) {
                player.networkHandler.sendChatCommand(message.substring(1));
            } else {
                player.networkHandler.sendChatMessage(message);
            }
        }
    }

    // === Рендеринг в 3D мире ===
    @EventHandler
    public final void onWorldRender(@NotNull WorldRenderEvent event) {
        if (event.isPortalPass()) {
            return;
        }
        ClientPlayerEntity player = this.mc.player;
        ClientWorld world = this.mc.world;
        if (player == null || world == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        MatrixStack stack = event.getStack();
        VertexConsumerProvider.Immediate provider = this.mc.getBufferBuilders().getEntityVertexConsumers();

        long now = System.currentTimeMillis();
        long left = Math.max(0L, this.cooldownEndMs - now);
        boolean isReady = left == 0L;

        // 1. Отображение радиуса действия шара вокруг игрока
        if (this.showRadius.getValue()) {
            Vec3d center = new Vec3d(player.getX(), player.getY() + 0.02, player.getZ());
            int color = isReady ? this.readyColor.getValue() : this.cooldownColor.getValue();
            int fill = ColorEngine.multAlpha(color, 0.15f);
            int outline = ColorEngine.multAlpha(color, 0.85f);
            WorldShapeRenderer.horizontalFilledCircle(provider, stack, cameraPos, center, this.harvestRadius.getValue(), fill, outline, 1.5f);
        }

        // 2. Подсветка созревших культур
        if (this.showEsp.getValue()) {
            float maxDist = this.harvestRadius.getValue();
            int r = (int) Math.ceil(maxDist);
            BlockPos playerPos = player.getBlockPos();

            this.matureCropBoxes.clear();
            for (int x = -r; x <= r; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        double dSq = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dSq > (double) (maxDist * maxDist)) {
                            continue;
                        }

                        BlockState state = world.getBlockState(pos);
                        if (this.isMatureCrop(state, pos, world)) {
                            this.matureCropBoxes.add(new Box(pos));
                        }
                    }
                }
            }

            if (!this.matureCropBoxes.isEmpty()) {
                int fill = ColorEngine.multAlpha(this.cropEspColor.getValue(), 0.25f);
                int outline = ColorEngine.multAlpha(this.cropEspColor.getValue(), 0.9f);
                WorldShapeRenderer.boxes(provider, stack, cameraPos, this.matureCropBoxes, fill, outline, 1.0f);
            }
        }
    }

    // === Рендеринг HUD виджета на экране ===
    @EventHandler
    public final void onHud(@NotNull HudRenderEvent event) {
        if (!this.showHud.getValue() || this.mc.player == null) {
            return;
        }

        DrawContext graphics = event.getGraphics();
        float screenW = Math.max(1.0f, Position.Companion.screenWidth());
        float screenH = Math.max(1.0f, Position.Companion.screenHeight());

        float cardW = 120.0f;
        float cardH = 30.0f;
        float x = (screenW - cardW) * 0.5f;
        float y = screenH - 68.0f;

        long now = System.currentTimeMillis();
        long left = Math.max(0L, this.cooldownEndMs - now);
        float totalCooldown = this.cooldown.getValue() * 1000.0f;
        float progress = totalCooldown > 0 ? MathHelper.clamp(1.0f - (float) left / totalCooldown, 0.0f, 1.0f) : 1.0f;

        Render2D.beginFrame(graphics);

        // Полупрозрачный стеклянный фон
        RectUtil.drawClientRect(x, y, cardW, cardH, 5.0f, 0.88f);

        // Название модуля
        int titleColor = ClientAccent.gradientAAt(255.0f, x + cardW * 0.5f, y + cardH * 0.5f);
        Fonts.BOLD.msdf("Auto Farm", x + 8.0f, y + 4.5f, 7.5f, titleColor);

        // Текст статуса
        String statusText;
        int statusColor;
        if (this.isShifting) {
            statusText = "Активация...";
            statusColor = 0xFFFFD700; // Gold
        } else if (left > 0L) {
            float secLeft = (float) left / 1000.0f;
            statusText = String.format("%.1fс", secLeft);
            statusColor = 0xFFFF6B6B; // Soft Red
        } else {
            statusText = "Готов";
            statusColor = 0xFF51CF66; // Bright Green
        }

        float statusW = Fonts.MEDIUM.msdfWidth(statusText, 7.0f);
        Fonts.MEDIUM.msdf(statusText, x + cardW - 8.0f - statusW, y + 5.0f, 7.0f, statusColor);

        // Полоса прогресса перезарядки
        float barX = x + 8.0f;
        float barY = y + 19.0f;
        float barW = cardW - 16.0f;
        float barH = 3.5f;

        // Фон полосы
        RectUtil.drawClientRectNoGlow(barX, barY, barW, barH, 2.0f, 0.35f);

        // Заполнение полосы
        float fillW = barW * (left == 0L ? 1.0f : progress);
        if (fillW > 0.5f) {
            int fillCol = left == 0L ? 0xFF51CF66 : ClientAccent.mix(0xFFFF6B6B, 0xFF51CF66, progress);
            RectUtil.drawClientRectNoGlow(barX, barY, fillW, barH, 2.0f, 0.95f);
        }

        Render2D.flush();
    }
}
