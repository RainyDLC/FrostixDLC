package ru.white.module.impl.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BannerItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SmithingTemplateItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.Client;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.InputEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.AnarchySetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.Pathing;
import ru.white.utils.other.TelegramBot;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.MoveUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "Auto Warden", desc = "Автофарм сундуков в Варден зоне. Анархии и пресеты настраиваются в этом меню", category = Category.OTHER)
public class AutoWarden extends Module {
    private static final Pattern CLOCK = Pattern.compile("([0-9]{1,2}):([0-9]{2})");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final long TTL = 7200000L;
    private static final String NL = "\n";

    public final AnarchySetting anarchies = new AnarchySetting(this, "Анархии");
    public final BooleanSetting useSpeed = new BooleanSetting(this, "Использовать скорость", false);
    public final BooleanSetting report = new BooleanSetting(this, "Репортить обидчиков", false);
    public final ModeSetting loot = new ModeSetting(this, "Приоритеты лута", "Низкий", "Средний", "Высокий");
    public final BooleanSetting trash = new BooleanSetting(this, "Выкидывать мусор с пола", true);
    public final SliderSetting lootSpeed = new SliderSetting(this, "Стаков за тик", 4, 1, 27, 1);
    public final SliderSetting waitLimit = new SliderSetting(this, "Ждать сундук, с", 30, 5, 180, 5);
    public final BooleanSetting telegram = new BooleanSetting(this, "Телеграм", false);
    public final StringSetting tgToken = new StringSetting(this, "Токен бота", "").setVisible(telegram::getValue);
    public final StringSetting tgChat = new StringSetting(this, "ID чата", "", true).setVisible(telegram::getValue);
    public final BooleanSetting tgNotify = new BooleanSetting(this, "Уведомления", true).setVisible(telegram::getValue);
    public final SliderSetting tgReport = new SliderSetting(this, "Отчёт, мин", 30, 0, 180, 5).setVisible(telegram::getValue);
    public final BooleanSetting debug = new BooleanSetting(this, "Отладка", false);

    private enum State {
        SAVE, TAKE, COLLECTING, ESCAPE
    }

    private State state = State.SAVE;
    private int wardenAggroUntil;
    private boolean died;
    private Box zone;
    private BlockPos currentChest;
    private String reportTarget;
    private int farmIndex = 1;

    private final Map<BlockPos, Integer> openAttempts = new HashMap<>();
    private final Map<BlockPos, Integer> wardenSpots = new HashMap<>();
    private final Map<String, Long> timers = new HashMap<>();
    private final Map<String, Long> emptied = new HashMap<>();
    private final Map<String, Integer> lootItems = new LinkedHashMap<>();

    private BlockPos recallChest;
    private int recallAnarchy = -1;
    private final TimerUtil hopTimer = new TimerUtil();
    private final TimerUtil switchTimer = new TimerUtil();
    private final TimerUtil homeTimer = new TimerUtil();

    private BlockPos walkTarget;
    private int walkRange;
    private Vec3d lastPos;
    private BlockPos progressTarget;
    private double progressBest;
    private int progressTick;
    private int detourUntil;
    private int detourSide;
    private int detourTries;
    private final Map<BlockPos, Integer> unreachable = new HashMap<>();

    private BlockPos supplyChest;
    private BlockPos depositChest;
    private BlockPos openTarget;
    private BlockPos openClick;
    private int openTick;
    private int lootTick;
    private BlockPos homeSpot;
    private int homeTick;
    private int supplySlot = -1;
    private int containerTick;
    private int swapSource = -1;
    private int swapTarget = -1;
    private int swapTick;
    private int swapTries;
    private int swapBlocked;
    private ItemStack swapStack = ItemStack.EMPTY;
    private final Map<BlockPos, Long> badChests = new HashMap<>();

    private boolean useHeld;
    private boolean useRequested;
    private boolean eating;
    private int eatTick;
    private int eatFood;
    private int eatBlocked;
    private boolean aiming;
    private boolean lootCounted;
    private long period;
    private int looted;
    private int stacksTaken;
    private long startedAt = System.currentTimeMillis();

    private final Map<String, Integer> stored = new LinkedHashMap<>();
    private final Map<Integer, int[]> anarchyStats = new HashMap<>();
    private TelegramBot bot;
    private int storedStacks;
    private int storedChests;
    private int deposits;
    private int tripStacks;
    private int visitAnarchy = -1;
    private long lastReport = System.currentTimeMillis();

    {
        loot.set("Средний");
        startTelegramWatchdog();
    }

    public static AutoWarden get() {
        return Instance.get(AutoWarden.class);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // порядок из клик гуи не трогаем: подхватываем текущую анархию только когда пресет пустой
        if (anarchies.isEmpty() && ServerUtil.anarchy >= 0) anarchies.moveToFront(ServerUtil.anarchy);

        // стоим на анархии из списка фермы — с неё и начинаем, лишний переход ни к чему
        int current = indexOfAnarchy(ServerUtil.anarchy);
        farmIndex = current >= 1 ? current : 1;
        boolean atHome = mc.player != null && onHomeAnarchy();
        state = atHome && (needSupplies() || hasLootToStore()) ? State.SAVE : State.COLLECTING;
        homeSpot = atHome ? mc.player.getBlockPos().toImmutable() : null;
        homeTick = 0;
        supplySlot = -1;
        swapSource = -1;
        swapTarget = -1;
        swapTick = 0;
        swapTries = 0;
        swapBlocked = 0;
        swapStack = ItemStack.EMPTY;
        eatBlocked = 0;
        died = false;
        lootCounted = false;
        looted = 0;
        stacksTaken = 0;
        startedAt = System.currentTimeMillis();
        currentChest = null;
        reportTarget = null;
        zone = null;
        walkTarget = null;
        lastPos = null;
        wardenAggroUntil = 0;
        clearRecall();
        hopTimer.reset();
        wardenSpots.clear();
        openAttempts.clear();
        unreachable.clear();
        badChests.clear();
        openTarget = null;
        openClick = null;
        openTick = 0;
        lootTick = 0;
        containerTick = 0;
        resetProgress();
        lootItems.clear();
        loadTimers();
        loadStorage();

        WardenHelper helper = WardenHelper.get();
        if (helper != null && !helper.isEnabled()) helper.setEnabled(true);

        Pathing.farmMode(true);
        ChatUtils.addChatMessage("§7[AW] §fShift + Пробел §7— быстрое выключение функции"
                + (Pathing.available() ? "" : " §7| §cпасфайндинга нет, иду напрямую"));

        syncTelegram();
        lastReport = System.currentTimeMillis();
        notifyTg("[AW] включён, анархия склада " + anarchies.home() + ", анархий в списке " + anarchies.size());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        saveTimers();
        saveStorage();
        notifyTg("[AW] выключен. " + statsLine());
        releaseUse();
        Pathing.cancel();
        Pathing.farmMode(false);
        walkTarget = null;
        currentChest = null;
        eating = false;
        eatTick = 0;
        clearRecall();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen instanceof DeathScreen && mc.player.deathTime >= 5) mc.player.requestRespawn();
        if (mc.currentScreen instanceof GameMenuScreen) mc.setScreen(null);

        useRequested = false;
        aiming = false;

        checkTeleport();
        periodicReport();

        if (debug.getValue() && mc.player.age % 20 == 0) printDebug();

        if (mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed()) {
            walkTarget = null;
            releaseUse();
            setEnabled(false);
            return;
        }

        for (WardenEntity warden : mc.world.getEntitiesByClass(WardenEntity.class, mc.player.getBoundingBox().expand(256.0), e -> true)) {
            wardenSpots.put(warden.getBlockPos(), mc.player.age + 100);

            if (warden.getAnger() >= 40 || warden.isInPose(EntityPose.ROARING) || warden.isInPose(EntityPose.EMERGING)) {
                wardenAggroUntil = mc.player.age + 100;
            }
        }
        wardenSpots.values().removeIf(expire -> mc.player.age > expire);

        if (reportTarget != null) {
            if (mc.player.age >= 20 && mc.player.age < 30) {
                mc.player.networkHandler.sendChatMessage("/report " + reportTarget + " чит");
                reportTarget = null;
            }
            return;
        }

        if (mc.player.age < 5) {
            wardenAggroUntil = 0;
            openAttempts.clear();
            return;
        }

        // сайдбар после смены сервера приходит не сразу. Раньше тут стоял state = SAVE, и стоило
        // переключиться на ферму, как склад забирал бота обратно, а склад отправлял снова на ферму
        if (ServerUtil.anarchy < 0) {
            if (mc.player.age > 600) switchAnarchy(anarchies.home());
            return;
        }

        // зона живёт в своей четверти карты: после телепорта в другую её надо пересчитать,
        // иначе бот считает, что фермы рядом нет, и вместо фарма жмёт home
        if (zoneStale()) updateZone();

        if (mc.player.hasStatusEffect(StatusEffects.GLOWING) && playerNear(32.0) && !onHomeAnarchy()) {
            flee(false);
            return;
        }

        settleSwap();
        if (trash.getValue()) dropFloorTrash();
        if (mc.player.age % 20 == 0) updateChestMemory();
        if (mc.player.age % 6000 == 0 && mc.player.age > 100) saveTimers();
        if (mc.currentScreen == null) {
            lootCounted = false;
            lootTick = 0;
        }

        // через минуту прощаем сундуки, которые не открылись: помеха могла уйти
        if (mc.player.age % 1200 == 0) openAttempts.clear();

        switch (state) {
            case SAVE -> save();
            case TAKE -> take();
            case COLLECTING -> collect();
            case ESCAPE -> escape();
        }

        if (Pathing.available()) driveBaritone();
        else if (walkTarget != null && !aiming && mc.currentScreen == null) lookAtWalkTarget();

        if (!useRequested) releaseUse();
        updateWalkProgress();
    }

    @EventHandler
    public void onPacket(EventPacket event) {
        if (mc.player == null || event.isSend()) return;

        if (event.getPacket() instanceof PlaySoundS2CPacket sound) {
            checkWardenSound(sound.getSound());
            return;
        }

        if (event.getPacket() instanceof PlaySoundFromEntityS2CPacket sound) {
            checkWardenSound(sound.getSound());
            return;
        }

        if (!(event.getPacket() instanceof GameMessageS2CPacket message)) return;

        String text = message.content().getString();
        if (!text.contains("Помянем. Вы погибли")) return;

        died = true;

        if (!report.getValue() || !text.contains("Вас убил")) return;

        StringBuilder effects = new StringBuilder();
        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            effects.append(effect.getEffectType().value().getName().getString()).append(", ");
        }
        ChatUtils.addChatMessage("§7[AW] эффекты при смерти: §f" + (effects.isEmpty() ? "нет" : effects.substring(0, effects.length() - 2)));

        if (!mc.player.hasStatusEffect(StatusEffects.GLOWING) && !chestNear(2.0)) {
            reportTarget = text.split("Вас убил ")[1].split(",")[0].trim();
        }
    }

    private void checkWardenSound(RegistryEntry<SoundEvent> entry) {
        String path = entry.getKey().map(key -> key.getValue().getPath()).orElse("");

        if (path.contains("warden.roar") || path.contains("warden.angry") || path.contains("warden.sonic")) {
            wardenAggroUntil = mc.player.age + 100;
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.isOnGround() && !mc.player.isClimbing()) event.setJumping(false);

        if (walkTarget != null && mc.currentScreen == null && !Pathing.available()) {
            Vec3d relative = walkVector();
            PlayerInput input = MoveUtil.getDirectionalInputForDegrees(event.getInput(),
                    MoveUtil.getDegreesRelativeToView(relative, mc.player.getYaw()), 20.0F);

            boolean sprint = input.forward() && !input.backward() && mc.player.getHungerManager().getFoodLevel() > 6 && !eating;

            event.setInput(new PlayerInput(input.forward(), input.backward(), input.left(), input.right(),
                    input.jump() || needJump(relative), input.sneak(), input.sprint() || sprint));
        }

        if (stuck() && mc.player.getMainHandStack().isEmpty() && !chestNear(3.0)) {
            boolean side = mc.player.age % 40 < 20;
            event.setDirectional(true, false, side, !side);
            event.setJumping(mc.player.isOnGround());
        }
    }

    private boolean needJump(Vec3d relative) {
        if (!mc.player.isOnGround()) return false;
        if (mc.world.getBlockState(mc.player.getBlockPos()).isIn(BlockTags.CANDLES)) return true;

        double length = relative.horizontalLength();
        if (relative.y > 0.6 && length < 2.0) return true;
        if (length < 0.001) return false;

        double step = 0.6 / length;
        BlockPos ahead = BlockPos.ofFloored(mc.player.getX() + relative.x * step, mc.player.getY() + 0.1, mc.player.getZ() + relative.z * step);
        if (ahead.equals(mc.player.getBlockPos())) return false;

        return !mc.world.getBlockState(ahead).getCollisionShape(mc.world, ahead).isEmpty()
                && mc.world.getBlockState(ahead.up()).getCollisionShape(mc.world, ahead.up()).isEmpty()
                && mc.world.getBlockState(ahead.up(2)).getCollisionShape(mc.world, ahead.up(2)).isEmpty();
    }

    private void lookAtWalkTarget() {
        Vec3d relative = Vec3d.ofCenter(walkTarget).subtract(mc.player.getEyePos());
        if (relative.horizontalLengthSquared() < 0.25) return;

        RotationProcess.update(new Rotation((float) Math.toDegrees(Math.atan2(-relative.x, relative.z)), 20.0F), 25.0F, 25.0F, 2, 1);
    }

    /**
     * Ставит точку хода. range — допуск: сколько блоков не дойти считается «дошли».
     * Точки, посчитанные формулой (круг, отход), почти никогда не годятся как «встать ровно тут»,
     * и без допуска Baritone доходит до ближайшего места, а потом бросает цель и бот стоит.
     */
    private void walkTo(BlockPos spot, int range) {
        walkRange = range;

        if (spot == null) {
            walkTarget = null;
            return;
        }
        // зона есть только на фарме: на складе clamp увёл бы цель к её границе, за две тысячи блоков
        if (onHomeAnarchy()) {
            walkTarget = spot.toImmutable();
            return;
        }

        walkTarget = new BlockPos(clampX(spot.getX()), spot.getY(), clampZ(spot.getZ()));
    }

    /**
     * Ведёт Baritone к walkTarget. Цель переставляем не чаще раза в 10 тиков
     * (и раз в 5, если стоим на месте), иначе он всё время пересчитывает путь.
     */
    private void driveBaritone() {
        if (walkTarget == null || mc.currentScreen != null) {
            Pathing.cancel();
            return;
        }

        if (Pathing.hasGoal(walkTarget, walkRange) && Pathing.busy()) return;
        if (mc.player.age % 10 == 0 || (!isMoving() && mc.player.age % 5 == 0)) Pathing.goTo(walkTarget, walkRange);
    }

    /** Направление хода: обычно прямо на цель, при обходе препятствия — вбок вдоль стены. */
    private Vec3d walkVector() {
        Vec3d relative = Vec3d.ofCenter(walkTarget).subtract(mc.player.getEntityPos());
        if (detourSide == 0 || mc.player.age >= detourUntil) return relative;

        double length = relative.horizontalLength();
        if (length < 0.001) return relative;

        double dirX = relative.x / length;
        double dirZ = relative.z / length;
        return new Vec3d(-dirZ * detourSide * 3.0 + dirX, relative.y, dirX * detourSide * 3.0 + dirZ);
    }

    /**
     * Следит за прогрессом к цели: если полторы секунды не приближаемся, значит уперлись.
     * Вместо дрожания на месте обходим препятствие боком по свободной стороне,
     * а после трёх неудачных попыток бросаем цель и берём другой сундук.
     */
    private void updateWalkProgress() {
        unreachable.values().removeIf(expire -> mc.player.age > expire);

        if (walkTarget == null || mc.currentScreen != null || wardenAggro() || isDrinking()) {
            resetProgress();
            return;
        }

        double distance = Math.hypot(walkTarget.getX() + 0.5 - mc.player.getX(),
                walkTarget.getZ() + 0.5 - mc.player.getZ());

        if (progressTarget == null || progressTarget.getSquaredDistance(walkTarget) > 9.0) {
            progressTarget = walkTarget;
            progressBest = distance;
            progressTick = mc.player.age;
            detourUntil = 0;
            detourSide = 0;
            detourTries = 0;
            return;
        }

        if (distance < progressBest - 0.4) {
            progressBest = distance;
            progressTick = mc.player.age;
            detourTries = 0;
            return;
        }

        if (mc.player.age < detourUntil) return;

        long idle = mc.player.age - progressTick;

        // Baritone мог повести в обход, поэтому пока он идёт или считает — терпим, но не дольше 30 секунд
        if (Pathing.busy()) {
            if (idle >= 600) giveUpTarget();
            return;
        }

        // цель есть, а Baritone не идёт и не считает: значит он её бросил — дойти нельзя, ждать нечего
        if (Pathing.available()) {
            if (idle < 60) return;

            // сундук пропускаем и берём другой; на складе и в отходе цель не сундук — просто ждём дальше
            if (state == State.COLLECTING && currentChest != null) giveUpTarget();
            else progressTick = mc.player.age;
            return;
        }

        if (idle < 30) return;

        progressBest = distance;
        progressTick = mc.player.age;
        detourTries++;

        if (detourTries > 3) {
            giveUpTarget();
            return;
        }

        detourSide = Pathing.available() ? 0 : pickDetourSide();
        detourUntil = mc.player.age + 20 + detourTries * 10;

        if (debug.getValue()) ChatUtils.addChatMessage("§7[AW] не приближаюсь к цели §7(попытка "
                + detourTries + ")" + (detourSide == 0 ? "" : ", обхожу §f" + (detourSide > 0 ? "вправо" : "влево")));
    }

    private void resetProgress() {
        progressTarget = null;
        detourUntil = 0;
        detourSide = 0;
        detourTries = 0;
    }

    /**
     * Ловит телепорт по разрыву в позиции. У Baritone после него остаётся путь из старого места,
     * и бывает, что новый он не считает, пока не пройдёшь пешком руками. Пометки сундуков тоже
     * сбрасываем: зоны анархий стоят на одних координатах, и «недостижим» с прошлой там врёт.
     */
    private void checkTeleport() {
        Vec3d pos = mc.player.getEntityPos();

        if (lastPos != null && lastPos.squaredDistanceTo(pos) > 64.0) {
            Pathing.cancel();
            walkTarget = null;
            currentChest = null;
            resetProgress();
            progressTick = mc.player.age;
            unreachable.clear();
            openAttempts.clear();
            updateZone();

            if (debug.getValue()) ChatUtils.addChatMessage("§7[AW] телепорт, сбрасываю путь и пометки");
        }
        lastPos = pos;
    }

    /** Цель недостижима: помечаем сундук на минуту и идём искать другой. */
    private void giveUpTarget() {
        if (currentChest != null) {
            unreachable.put(currentChest, mc.player.age + 1200);
            if (debug.getValue()) ChatUtils.addChatMessage("§7[AW] сундук недостижим, пропускаю §f"
                    + currentChest.toShortString());
        }
        currentChest = null;
        walkTarget = null;
        resetProgress();
    }

    /** Сторона обхода: та, где вбок больше свободного места. */
    private int pickDetourSide() {
        Vec3d relative = Vec3d.ofCenter(walkTarget).subtract(mc.player.getEntityPos());
        double length = relative.horizontalLength();
        if (length < 0.001) return 1;

        double dirX = relative.x / length;
        double dirZ = relative.z / length;
        int right = openSteps(-dirZ, dirX);
        int left = openSteps(dirZ, -dirX);

        if (right == left) return detourSide != 0 ? -detourSide : (mc.player.age % 2 == 0 ? 1 : -1);
        return right > left ? 1 : -1;
    }

    /** Сколько шагов вбок свободно, максимум четыре. */
    private int openSteps(double x, double z) {
        for (int step = 1; step <= 4; step++) {
            BlockPos pos = BlockPos.ofFloored(mc.player.getX() + x * step,
                    mc.player.getY() + 0.1, mc.player.getZ() + z * step);
            if (!passable(pos)) return step - 1;
        }
        return 4;
    }

    private boolean passable(BlockPos pos) {
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
                && mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty();
    }

    /**
     * Расталкивание имеет смысл, только если идём куда-то и не можем сдвинуться уже секунду.
     * Стоять у сундука и ждать таймер — это не «застрял», и дёргаться там нечего.
     */
    private boolean stuck() {
        // пока идём, пьём или сидим в сундуке, дёргаться нечего: этим мы только рвём расчёт пути
        if (mc.currentScreen != null || isMoving() || isDrinking()) return false;

        // в свече бот вязнет намертво, и оттуда выпрыгиваем даже без цели хода
        if (mc.world.getBlockState(mc.player.getBlockPos()).isIn(BlockTags.CANDLES)
                || mc.world.getBlockState(mc.player.getBlockPos().down()).isIn(BlockTags.CANDLES)) return true;

        return walkTarget != null && mc.player.age - progressTick > 20 && !Pathing.busy()
                && !wardenAggro() && state == State.COLLECTING && inFarmZone() && insideBlock();
    }

    private boolean insideBlock() {
        Box box = mc.player.getBoundingBox().expand(0.05, 0.0, 0.05);

        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (!mc.world.getBlockState(pos).isAir()) return true;
        }
        return false;
    }

    private boolean isMoving() {
        return mc.player.getVelocity().horizontalLengthSquared() > 0.0025;
    }

    private boolean isDrinking() {
        return mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.POTION);
    }

    /** Смена анархии: не чаще раза в пять секунд, иначе на каждый лишний an сервер отвечает «вы уже подключены». */
    private boolean switchAnarchy(int number) {
        if (number < 0 || mc.player.age < 40 || !switchTimer.every(5000L)) return false;

        mc.player.networkHandler.sendChatCommand("an" + number);
        if (debug.getValue()) ChatUtils.addChatMessage("§7[AW] перехожу на анархию §f" + number);
        return true;
    }

    private void save() {
        int home = anarchies.home();

        if (home < 0) {
            if (mc.player.age % 60 == 0) {
                ChatUtils.addChatMessage("§7[AW] §cдобавь анархии в настройках модуля: первая — склад, остальные — ферма");
            }
            return;
        }

        if (ServerUtil.anarchy != home && !ServerUtil.isPvp()) switchAnarchy(home);

        if (onHomeAnarchy()) {
            if (holdHome()) return;
            freeHand();
            container(hasLootToStore(), true, State.TAKE);
        }
    }

    /**
     * На складе держим точку, где включили модуль: иначе остаётся цель с фарма
     * и Baritone уводит бота от «своих» сундуков к соседним.
     */
    private boolean holdHome() {
        if (homeSpot == null) {
            homeSpot = mc.player.getBlockPos().toImmutable();
            if (debug.getValue()) ChatUtils.addChatMessage("§7[AW] точка склада §f" + homeSpot.toShortString());
        }

        if (mc.currentScreen instanceof GenericContainerScreen) {
            walkTarget = null;
            return false;
        }

        if (mc.player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(homeSpot)) > 16.0) {
            if (homeTick == 0) homeTick = mc.player.age;

            // не дошли за 20 секунд — точка недостижима, закрепляемся там, где стоим
            if (mc.player.age - homeTick > 400) {
                homeSpot = mc.player.getBlockPos().toImmutable();
                homeTick = 0;
                walkTarget = null;
                return false;
            }

            walkTarget = homeSpot;
            walkRange = 1;
            return true;
        }

        homeTick = 0;
        walkTarget = null;
        return false;
    }

    private void take() {
        if (died && anarchies.size() > 1) {
            int next = recallIndex();
            farmIndex = next >= 1 ? next : nextFarmIndex();
            died = false;
        }

        wardenAggroUntil = 0;
        openAttempts.clear();
        visitAnarchy = -1;

        if (debug.getValue() && mc.player.age % 40 == 0) {
            StringBuilder missing = new StringBuilder();
            if (invisCount() < 1) missing.append("зелье невидимости, ");
            if (countItem(Items.GOLDEN_CARROT) < 3) missing.append("золотая морковь, ");
            if (useSpeed.getValue() && findSlot(this::isSpeedPotion) < 0) missing.append("зелье скорости, ");

            if (!missing.isEmpty()) {
                ChatUtils.addChatMessage("§7[AW] собираем (возможно не хватает) §f" + missing.substring(0, missing.length() - 2));
            }
        }

        if (onHomeAnarchy()) {
            if (holdHome()) return;
            container(needSupplies() || cursorBusy(), false, State.COLLECTING);
        }
    }

    private void collect() {
        if (checkEscape()) return;

        eatIfNeeded();

        // пока жуём — стоим: шаг или смена слота срывают укус, и морковь уходит впустую
        if (busyEating()) {
            walkTarget = null;
            return;
        }

        if (isDrinking()) {
            walkTarget = null;
            useRequested = true;
            return;
        }

        if (anarchies.size() <= 1) {
            if (mc.player.age % 60 == 0) {
                ChatUtils.addChatMessage("§7[AW] §cсписок анархий пустой — добавь их в настройках модуля (минимум 2)");
            }
            return;
        }

        if (farmIndex >= anarchies.size()) farmIndex = 1;

        int target = anarchies.at(farmIndex);
        if (ServerUtil.anarchy != target) {
            switchAnarchy(target);
            return;
        }

        if (mc.player.age > 5) prepare();
    }

    private void prepare() {
        StatusEffectInstance invisibility = mc.player.getStatusEffect(StatusEffects.INVISIBILITY);
        boolean ready = mc.player.hasStatusEffect(StatusEffects.GLOWING) || (invisibility != null && invisibility.getDuration() >= 400);

        if (!ready && invisibility == null && invisCount() < 1 && mc.player.age % 5 == 0 && !ServerUtil.isPvp()) {
            state = State.ESCAPE;
            return;
        }

        if (!ready) {
            int slot = findSlot(this::isInvisPotion);
            if (slot >= 0 && mc.player.age > 20) useSlot(slot);
        }

        // на телепорт домой стоим смирно: шаг в этот момент сервер считает попыткой сбежать и отменяет его
        if (!inFarmZone()) {
            walkTarget = null;
            if (mc.player.age > 40 && homeTimer.every(5000L)) mc.player.networkHandler.sendChatCommand("home");
            return;
        }

        // зелье не переложить в хотбар — не стоим столбом десять секунд, фармим и пробуем позже
        if (!ready && mc.player.age >= swapBlocked) return;

        int speedSlot = useSpeed.getValue() && mc.player.getStatusEffect(StatusEffects.SPEED) == null ? findSlot(this::isSpeedPotion) : -1;
        if (speedSlot < 0) routine();
        else useSlot(speedSlot);
    }

    private int scaled(int base) {
        if (loot.is("Низкий")) return (int) (base * 1.5);
        if (loot.is("Высокий")) return (int) (base * 0.8);
        return base;
    }

    private boolean checkEscape() {
        boolean aggro = wardenAggro();
        long blocked = openAttempts.values().stream().filter(count -> count >= 2).count();

        if ((aggro || inventoryCount() > scaled(20) || mc.player.getHungerManager().getFoodLevel() < 8
                || (blocked >= 3 && mc.player.age % 30 == 0)) && mc.player.age > 100) {
            if (aggro) died = true;
            state = State.ESCAPE;
            return true;
        }

        if (!ServerUtil.isPvp() && inventoryCount() > scaled(8)) {
            state = State.ESCAPE;
            return true;
        }

        int seconds = pvpSeconds();
        if (seconds >= 0 && seconds < 7 && !playerNear(14.0) && inventoryCount() > scaled(7)) {
            state = State.ESCAPE;
            return true;
        }
        return false;
    }

    private void escape() {
        if (onHomeAnarchy()) {
            state = State.SAVE;
            return;
        }

        if (wardenAggro() && ServerUtil.isPvp()) {
            flee(true);
            return;
        }

        BlockPos near = pickChest();
        if (mc.currentScreen instanceof GenericContainerScreen || nearOpening(near) || nearOpening(currentChest)) {
            routine();
            return;
        }

        if (ServerUtil.isPvp()) {
            if (inventoryCount() >= 23 || playerNear(2.0) || pvpSeconds() <= 16 || near == null) flee(true);
            else routine();
            return;
        }

        state = State.SAVE;
    }

    private void flee(boolean warden) {
        closeContainer();
        freeHand();

        BlockPos best = null;
        double bestScore = -1.0;
        int y = mc.player.getBlockPos().getY();

        for (int angle = 0; angle < 360; angle += 30) {
            int x = clampX((int) (mc.player.getX() + Math.cos(Math.toRadians(angle)) * 25.0));
            int z = clampZ((int) (mc.player.getZ() + Math.sin(Math.toRadians(angle)) * 25.0));
            double score = safety(x, z, warden);

            if (score > bestScore) {
                bestScore = score;
                best = new BlockPos(x, y, z);
            }
        }

        walkTo(best, 4);
    }

    private double safety(int x, int z, boolean warden) {
        double min = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof PlayerEntity) && !(warden && entity instanceof WardenEntity)) continue;
            min = Math.min(min, Math.hypot(entity.getX() - x, entity.getZ() - z));
        }
        return min;
    }

    private boolean wardenAggro() {
        if (mc.player.age >= wardenAggroUntil) return false;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof WardenEntity warden)) continue;

            double distSq = mc.player.squaredDistanceTo(warden);
            if (distSq < 900.0 && facingMe(warden) && (distSq < 16.0 || approaching(warden))) return true;
        }
        return false;
    }

    private boolean approaching(WardenEntity warden) {
        return (mc.player.getX() - warden.getX()) * (warden.getX() - warden.lastX)
                + (mc.player.getZ() - warden.getZ()) * (warden.getZ() - warden.lastZ) > 0.01;
    }

    private boolean facingMe(WardenEntity warden) {
        double yawToMe = Math.toDegrees(Math.atan2(-(mc.player.getX() - warden.getX()), mc.player.getZ() - warden.getZ()));
        return Math.abs(MathHelper.wrapDegrees((float) (warden.getBodyYaw() - yawToMe))) < 10.0;
    }

    private int inventoryCount() {
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (!stack.isEmpty()) count++;
        }
        return count;
    }

    private void updateZone() {
        double centerX = (mc.player.getX() < 0 ? -1 : 1) * 2000.0;
        double centerZ = (mc.player.getZ() < 0 ? -1 : 1) * 2000.0;
        double y = mc.player.getY();
        zone = new Box(centerX - 75.0, y, centerZ - 75.0, centerX + 75.0, y, centerZ + 75.0);
    }

    /** Зона привязана к своей четверти карты, и после телепорта в другую четверть она врёт. */
    private boolean zoneStale() {
        if (zone == null) return true;

        return (zone.minX + zone.maxX < 0.0) != (mc.player.getX() < 0.0)
                || (zone.minZ + zone.maxZ < 0.0) != (mc.player.getZ() < 0.0);
    }

    private boolean inFarmZone() {
        return ServerUtil.getWorldType().equals("overworld") && inZoneBox(mc.player.getX(), mc.player.getZ());
    }

    private boolean inZoneBox(double x, double z) {
        if (zone == null) updateZone();
        return x >= zone.minX && x <= zone.maxX && z >= zone.minZ && z <= zone.maxZ;
    }

    private int clampX(int x) {
        if (zone == null) updateZone();
        return (int) MathHelper.clamp(x, zone.minX + 10, zone.maxX - 10);
    }

    private int clampZ(int z) {
        if (zone == null) updateZone();
        return (int) MathHelper.clamp(z, zone.minZ + 10, zone.maxZ - 10);
    }

    private void routine() {
        if (inFarmZone() && visitAnarchy != ServerUtil.anarchy && ServerUtil.anarchy >= 0) {
            visitAnarchy = ServerUtil.anarchy;
            anarchyStats.computeIfAbsent(visitAnarchy, key -> new int[2])[1]++;
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            // сундук открылся — значит попытки были не напрасны, счёт обнуляем
            if (openClick != null) openAttempts.remove(openClick);
            lootChest(screen);
            return;
        }

        BlockPos pick = pickChest();
        if (pick == null) pick = pickSoonest();
        if (holdCurrent(pick)) pick = currentChest;

        currentChest = chooseChest(pick);

        BlockPos target = currentChest;
        if (target == null) {
            if (travelToRecall()) return;

            if (mc.player.age % 40 == 0) {
                state = State.ESCAPE;
                died = true;
            }
            return;
        }

        long remaining = chestRemaining(target);
        long threshold = waitMs();

        boolean ready = chestReady(target);

        if (!ready && remaining > threshold && mc.player.age % 20 == 0 && hopTimer.hasTimeElapsed(12000)) {
            Recall recall = soonestRecall(threshold);
            if (recall != null) {
                hopTo(recall);
                return;
            }
            // столько ждать мы не подписывались: идём на следующую анархию по списку
            if (hopNext()) return;
        }

        double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));

        // ждать столько не готовы, а уйти некуда — крутимся рядом, пока остаток не влезет в порог.
        // вплотную не крутимся: бегать от сундука и обратно — это и есть то самое топтание на месте
        if (!ready && remaining > Math.max(6000L, threshold) && distSq > 64.0) {
            if (!travelToRecall()) walkTo(orbitSpot(target), 4);
            return;
        }

        if (distSq <= 20.0) {
            walkTarget = null;

            // караулим: стоим у сундука со сведённым прицелом, чтобы нажать в первый же тик готовности
            if (!ready) {
                aimChest(target);
                return;
            }

            if (openAttempts.getOrDefault(target, 0) < 3 && openChest(target, 12)) {
                openAttempts.merge(target, 1, Integer::sum);
            }
            return;
        }

        if (distSq > 10.0) freeHand();
        walkTo(sideSpot(target), 1);
    }

    private BlockPos orbitSpot(BlockPos chest) {
        double angle = (mc.player.age / 40) * 2.4;
        return new BlockPos(chest.getX() + (int) (Math.cos(angle) * 10.0), chest.getY(), chest.getZ() + (int) (Math.sin(angle) * 10.0));
    }

    private BlockPos sideSpot(BlockPos chest) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                BlockPos side = chest.add(dx, 0, dz);
                if (mc.world.getBlockState(side).isAir() && mc.world.getBlockState(side.up()).isAir()
                        && !mc.world.getBlockState(side.down()).isAir() && visibleFrom(side, chest)) return side;
            }
        }

        BlockPos up = chest.up();
        if (mc.world.getBlockState(up).isAir() && mc.world.getBlockState(up.up()).isAir() && visibleFrom(up, chest)) return up;
        return null;
    }

    private boolean visibleFrom(BlockPos from, BlockPos chest) {
        Vec3d eye = Vec3d.ofCenter(from).add(0.0, mc.player.getEyeHeight(mc.player.getPose()) - 0.5, 0.0);
        return visiblePoint(eye, chest) != null;
    }

    private Vec3d visiblePoint(Vec3d eye, BlockPos chest) {
        Vec3d center = Vec3d.ofCenter(chest);
        Vec3d best = null;
        double bestSq = Double.MAX_VALUE;

        for (double dx = -0.4; dx <= 0.41; dx += 0.4) {
            for (double dy = -0.4; dy <= 0.41; dy += 0.4) {
                for (double dz = -0.4; dz <= 0.41; dz += 0.4) {
                    Vec3d point = center.add(dx, dy, dz);
                    double sq = point.squaredDistanceTo(center);
                    if (sq >= bestSq) continue;

                    BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, point,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                    if (hit.getBlockPos().equals(chest)) {
                        bestSq = sq;
                        best = point;
                    }
                }
            }
        }
        return best;
    }

    private Rotation rotationTo(Vec3d eye, Vec3d aim) {
        Vec3d diff = aim.subtract(eye);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        return new Rotation(yaw, pitch);
    }

    /** Ведёт прицел в сундук. Возвращает точку клика, когда голова уже доведена, иначе null. */
    private Vec3d aimChest(BlockPos chest) {
        if (chest == null) return null;

        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = visiblePoint(eye, chest);
        if (aim == null) return null;

        Rotation target = rotationTo(eye, aim);
        float time = mc.player.age + mc.getRenderTickCounter().getTickProgress(false);
        float sway = (float) ((Math.sin(time * 0.31F) * 0.5 + Math.sin(time * 0.73F + 1.1F) * 0.3 + Math.sin(time * 1.7F + 2.6F) * 0.2) * 3.0);
        Rotation swayed = new Rotation(target.getYaw() + sway, MathHelper.clamp(target.getPitch() + sway / 4.0F, -90.0F, 90.0F));

        aiming = true;
        RotationProcess.update(swayed, 120.0F, 120.0F, 1, 1);

        // сверяемся с той ротацией, куда реально ведём, иначе дрожание само же и блокирует клик
        return new Rotation(mc.player).getDelta(swayed) > 4.0F ? null : aim;
    }

    /** Тыкает в сундук. delay — сколько тиков ждём ответа сервера, прежде чем повторить клик. */
    private boolean openChest(BlockPos chest, int delay) {
        if (chest == null || mc.currentScreen instanceof GenericContainerScreen) return false;

        Vec3d aim = aimChest(chest);
        if (aim == null) return false;

        if (!chest.equals(openClick)) {
            openClick = chest.toImmutable();
            openTick = 0;
        }

        // GUI приходит не в тот же тик: пока сервер молчит, повторный клик только жжёт попытки
        if (openTick > 0 && mc.player.age - openTick < delay) return false;

        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, aim,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (!hit.getBlockPos().equals(chest)) return false;

        openTick = mc.player.age;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Голограмма обновляется раз в секунду, поэтому у самого конца верим её отсутствию, а не остатку. */
    private boolean chestReady(BlockPos chest) {
        long remaining = chestRemaining(chest);
        return remaining < 0 || (remaining <= 2500 && !helper().hasHologram(chest));
    }

    /** Сколько готовы ждать сундук: и караулим, и уходим с анархии по этому порогу. */
    private long waitMs() {
        return (long) (waitLimit.getValue() * 1000.0F);
    }

    /** Стоим вплотную к сундуку, который откроется через секунды: уходить на склад рано. */
    private boolean nearOpening(BlockPos chest) {
        if (chest == null || mc.player == null) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(chest)) > 36.0) return false;
        if (!reachable(chest) || unreachable.containsKey(chest)) return false;
        if (openAttempts.getOrDefault(chest, 0) >= 3) return false;

        long remaining = chestRemaining(chest);
        if (remaining < 0) return true;
        // чужие рядом больше не повод уйти домой: сундук вот-вот откроется, уйдём — заберут его
        if (wardenAggro() || wardenNear(chest)) return false;

        return inventoryCount() < 34 && remaining <= Math.max(8000L, Math.min(waitMs(), 15000L));
    }

    /** Пока караулим сундук — не бросаем его из-за соседнего, иначе бот пляшет между ними. */
    private boolean holdCurrent(BlockPos pick) {
        if (currentChest == null || pick == null || pick.equals(currentChest)) return false;
        if (!nearCurrentChest(5.0) || !reachable(currentChest)) return false;
        if (wardenNear(currentChest) || unreachable.containsKey(currentChest)) return false;
        if (openAttempts.getOrDefault(currentChest, 0) >= 3) return false;

        long remaining = chestRemaining(currentChest);
        if (remaining > waitMs()) return false;

        // но караулить дольше, чем сходить за лучшим сундуком, смысла нет
        return chestCost(currentChest) <= chestCost(pick) + 2000L;
    }

    private BlockPos pickChest() {
        BlockPos best = null;
        long bestCost = Long.MAX_VALUE;

        for (BlockPos chest : helper().getChests()) {
            if (!chestUsable(chest)) continue;

            long cost = chestCost(chest);
            if (cost < bestCost) {
                bestCost = cost;
                best = chest;
            }
        }
        return best;
    }

    /**
     * Годится ли сундук в цель: досягаем, не занят, не в чёрных списках и ждать его не дольше порога.
     * Чужие игроки рядом ни на что не влияют: к спорным сундукам идём и караулим их наравне со всеми.
     */
    private boolean chestUsable(BlockPos chest) {
        if (chest == null || !reachable(chest) || unreachable.containsKey(chest)) return false;
        if (openAttempts.getOrDefault(chest, 0) >= 3) return false;

        long remaining = chestRemaining(chest);
        // раньше тут стояли зашитые пятнадцать секунд, и сундуки в пороге ожидания бот проходил мимо
        if (remaining > waitMs()) return false;

        boolean atChest = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(chest)) <= 20.0;

        // рядом с варденом берём только то, что готово и уже под носом
        return !wardenNear(chest) || (remaining < 0 && atChest);
    }

    /**
     * Через сколько миллисекунд сундук реально дадут облутать: что дольше — дорога до него или
     * остаток таймера. Так сундук с таймером в пороге не пропускается, но и не выбирается вместо
     * готового, до которого дойти быстрее, чем дождаться этого.
     */
    private long chestCost(BlockPos chest) {
        long walk = (long) (Math.sqrt(chestWeight(chest)) * 250.0);
        long wait = Math.max(chestRemaining(chest), 0L);

        // при равном ожидании берём ближний: стоять у сундука лучше, чем бежать за таким же дальним
        return Math.max(walk, wait) + walk / 4;
    }

    /** Дистанция для выбора: лезть наверх дороже, чем пройти по прямой. */
    private double chestWeight(BlockPos chest) {
        double up = Vec3d.ofCenter(chest).y - mc.player.getEyeY();
        double dx = chest.getX() + 0.5 - mc.player.getX();
        double dz = chest.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dz * dz + (up > 0.0 ? 2 : 1) * up * up;
    }

    /**
     * Держимся выбранной цели, пока она не хуже новой: на равных дистанциях выбор прыгал
     * каждый тик, Baritone пересчитывал путь, и бот топтался между двумя сундуками.
     */
    private BlockPos chooseChest(BlockPos pick) {
        if (pick == null || currentChest == null || pick.equals(currentChest)) return pick;
        if (!chestUsable(currentChest)) return pick;

        // цель меняем только ради заметно лучшего сундука, иначе выбор прыгает каждый тик
        return chestCost(pick) + 2000L < chestCost(currentChest) ? pick : currentChest;
    }

    /** Ничего в пороге ожидания нет: берём самый скорый, чтобы было у чего караулить или от чего уйти. */
    private BlockPos pickSoonest() {
        BlockPos best = null;
        long bestMs = Math.max(45000L, waitMs());

        for (BlockPos chest : helper().getChests()) {
            long remaining = chestRemaining(chest);
            if (remaining >= 0 && remaining < bestMs && reachable(chest)
                    && !wardenNear(chest) && !unreachable.containsKey(chest)) {
                bestMs = remaining;
                best = chest;
            }
        }
        return best;
    }

    private boolean reachable(BlockPos chest) {
        return sideSpot(chest) != null;
    }

    private boolean wardenNear(BlockPos pos) {
        for (BlockPos spot : wardenSpots.keySet()) {
            if (spot.getSquaredDistance(pos) < 25.0) return true;
        }
        return false;
    }

    private boolean playerNear(double range) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player) || player == mc.player) continue;
            if (mc.player.squaredDistanceTo(player) < range * range) return true;
        }
        return false;
    }

    private boolean chestNear(double range) {
        if (nearCurrentChest(range)) return true;

        for (BlockPos chest : helper().getChests()) {
            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(chest)) < range * range) return true;
        }
        return false;
    }

    private boolean nearCurrentChest(double range) {
        return currentChest != null && mc.player.squaredDistanceTo(Vec3d.ofCenter(currentChest)) < range * range;
    }

    private WardenHelper helper() {
        return WardenHelper.get();
    }

    private void container(boolean active, boolean hopper, State next) {
        if (!active) {
            if (closeContainer()) {
                if (hopper) finishDeposit();
                state = next;
            }
            containerTick = 0;
            supplySlot = -1;
            return;
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            if (containerTick == 0) containerTick = mc.player.age;

            if (hopper ? storeLoot(screen) : takeSupplies(screen)) {
                containerTick = mc.player.age;
                if (openTarget != null) {
                    badChests.remove(openTarget);
                    if (hopper) depositChest = openTarget;
                    else supplyChest = openTarget;
                }
                return;
            }

            // ничего не берётся и не кладётся — открыт сундук соседа, метим его и идём к следующему
            if (mc.player.age - containerTick > 24 && !cursorBusy()) {
                if (openTarget != null) {
                    badChests.put(openTarget, System.currentTimeMillis() + 120000L);
                    if (openTarget.equals(supplyChest)) supplyChest = null;
                    if (openTarget.equals(depositChest)) depositChest = null;

                    if (debug.getValue()) {
                        ChatUtils.addChatMessage("§7[AW] сундук §f" + openTarget.toShortString()
                                + " §7не тот, пробую соседний");
                    }
                }
                containerTick = 0;
                closeContainer();
            }
            return;
        }

        containerTick = 0;
        openTarget = findNearbyChest(hopper);

        // все сундуки в округе помечены как «не те» — снимаем метки и пробуем заново
        if (openTarget == null) {
            if (!badChests.isEmpty() && mc.player.age % 100 == 0) badChests.clear();
            return;
        }

        openChest(openTarget, 10);
    }

    private void lootChest(GenericContainerScreen screen) {
        // после отмены пути игрока ещё тащит по инерции — ждём только реальный разгон, а не любой сдвиг
        if (mc.player.getVelocity().horizontalLengthSquared() > 0.02) {
            walkTarget = null;
            return;
        }

        if (lootTick == 0) lootTick = mc.player.age;

        int batch = Math.max(1, lootSpeed.getValue().intValue());

        for (int index = 0; index < batch; index++) {
            Slot slot = bestLootSlot(screen);
            if (slot == null) {
                // содержимое приходит отдельным пакетом, а на плагинных сундуках ещё и с задержкой:
                // пустой сундук в первую секунду значит «сервер ещё не прислал», а не «пусто»
                if (containerEmpty(screen) && mc.player.age - lootTick < 20) return;

                // брать нечего — сундук уже выгребли: вешаем таймер, иначе будем открывать его по кругу
                markEmptied();
                closeContainer();
                return;
            }

            ItemStack taken = slot.getStack().copy();
            click(screen, slot, 0, SlotActionType.QUICK_MOVE);
            stacksTaken++;
            anarchyStats.computeIfAbsent(ServerUtil.anarchy, key -> new int[2])[0]++;

            if (!taken.isEmpty()) lootItems.merge(taken.getName().getString(), taken.getCount(), Integer::sum);
            if (!lootCounted) {
                lootCounted = true;
                looted++;
                storedChests++;
                markEmptied();
            }

            // стак не ушёл — в инвентаре нет места, дальше долбить бессмысленно
            if (!slot.getStack().isEmpty()) {
                closeContainer();
                return;
            }
        }
    }

    /** Сундук пуст целиком: либо и правда пустой, либо содержимое ещё в пути. */
    private boolean containerEmpty(GenericContainerScreen screen) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (!isPlayerSlot(screen, slot) && !slot.getStack().isEmpty()) return false;
        }
        return true;
    }

    /** Сначала дорогое: соседние автовардены выгребают сундук за пару секунд. */
    private Slot bestLootSlot(GenericContainerScreen screen) {
        Slot best = null;
        int bestRank = 99;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (isPlayerSlot(screen, slot)) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || isJunk(stack)) continue;

            int rank = lootRank(stack);
            if (rank < bestRank) {
                bestRank = rank;
                best = slot;
                if (rank == 0) break;
            }
        }
        return best;
    }

    private int lootRank(ItemStack stack) {
        if (stack.isOf(Items.ELYTRA) || stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.isOf(Items.NETHERITE_INGOT) || stack.isOf(Items.NETHERITE_SCRAP) || stack.isOf(Items.ANCIENT_DEBRIS)
                || stack.isOf(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) || stack.isOf(Items.NETHER_STAR)
                || stack.isOf(Items.NETHERITE_SWORD) || stack.isOf(Items.NETHERITE_HELMET)
                || stack.isOf(Items.NETHERITE_CHESTPLATE) || stack.isOf(Items.NETHERITE_LEGGINGS)
                || stack.isOf(Items.NETHERITE_BOOTS)
                || (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock)) return 0;

        if (stack.isOf(Items.DIAMOND) || stack.isOf(Items.DIAMOND_BLOCK) || stack.isOf(Items.GOLDEN_APPLE)
                || stack.isOf(Items.END_CRYSTAL) || stack.isOf(Items.RESPAWN_ANCHOR) || stack.isOf(Items.BEACON)
                || stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.GOLDEN_CARROT)
                || isInvisPotion(stack) || isSpeedPotion(stack)) return 1;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        return enchants != null && !enchants.isEmpty() ? 1 : 2;
    }

    private boolean storeLoot(GenericContainerScreen screen) {
        if (mc.player.age % 2 != 0) return false;

        boolean keptPotion = false, keptCarrot = false;
        int moved = 0;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (moved >= 4) return true;

            ItemStack stack = slot.getStack();
            if (!isPlayerSlot(screen, slot) || stack.isEmpty()) continue;
            if (useSpeed.getValue() && isSpeedPotion(stack)) continue;

            if (!keptPotion && isInvisPotion(stack)) keptPotion = true;
            else if (!keptCarrot && stack.isOf(Items.GOLDEN_CARROT)) keptCarrot = true;
            else {
                ItemStack moving = stack.copy();
                click(screen, slot, 0, SlotActionType.QUICK_MOVE);
                moved++;
                storedStacks++;
                tripStacks++;
                if (!moving.isEmpty()) stored.merge(moving.getName().getString(), moving.getCount(), Integer::sum);
            }
        }
        return moved > 0;
    }

    private boolean takeSupplies(GenericContainerScreen screen) {
        if (mc.player.age % 2 != 0) return false;

        ItemStack cursor = screen.getScreenHandler().getCursorStack();
        Predicate<ItemStack> same = stack -> stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack, cursor);

        if (!cursor.isEmpty()) {
            // нужное кладём по одному правым кликом, а не всем стаком
            if (wantSupply(cursor)) return click(screen, findSlot(screen, true, same), 1, SlotActionType.PICKUP);

            // остаток возвращаем в тот же слот, откуда взяли: так стак не расползается по сундуку
            Slot back = slotById(screen, supplySlot);
            if (back == null || !same.test(back.getStack())) back = findSlot(screen, false, same);

            // сундук забит — лучше унести лишнее с собой, чем уронить на пол при закрытии
            if (back == null) back = findSlot(screen, true, ItemStack::isEmpty);

            supplySlot = -1;
            return click(screen, back, 0, SlotActionType.PICKUP);
        }

        Slot source = smallestSupplySlot(screen);
        if (source == null) return false;

        supplySlot = source.id;
        return click(screen, source, 0, SlotActionType.PICKUP);
    }

    /** Берём самый маленький подходящий стак: на курсоре не будет висеть пачка зелий, которую можно потерять. */
    private Slot smallestSupplySlot(GenericContainerScreen screen) {
        Slot best = null;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (isPlayerSlot(screen, slot) || !wantSupply(slot.getStack())) continue;
            if (best == null || slot.getStack().getCount() < best.getStack().getCount()) best = slot;
            if (best.getStack().getCount() <= 1) break;
        }
        return best;
    }

    private Slot slotById(GenericContainerScreen screen, int id) {
        if (id < 0 || id >= screen.getScreenHandler().slots.size()) return null;
        return screen.getScreenHandler().slots.get(id);
    }

    private Slot findSlot(GenericContainerScreen screen, boolean player, Predicate<ItemStack> match) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (isPlayerSlot(screen, slot) == player && match.test(slot.getStack())) return slot;
        }
        return null;
    }

    private boolean isPlayerSlot(GenericContainerScreen screen, Slot slot) {
        return slot.id >= screen.getScreenHandler().getRows() * 9;
    }

    private boolean click(GenericContainerScreen screen, Slot slot, int button, SlotActionType type) {
        if (slot == null) return false;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, button, type, mc.player);
        return true;
    }

    private boolean cursorBusy() {
        return mc.currentScreen instanceof GenericContainerScreen screen && !screen.getScreenHandler().getCursorStack().isEmpty();
    }

    private boolean closeContainer() {
        if (mc.currentScreen instanceof GenericContainerScreen) mc.player.closeHandledScreen();
        return !(mc.currentScreen instanceof GenericContainerScreen);
    }

    private boolean wantSupply(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isInvisPotion(stack)) return invisCount() < 1;
        if (stack.isOf(Items.GOLDEN_CARROT)) return countItem(Items.GOLDEN_CARROT) < 3;
        return useSpeed.getValue() && isSpeedPotion(stack) && findSlot(this::isSpeedPotion) < 0;
    }

    private boolean needSupplies() {
        return invisCount() < 1 || countItem(Items.GOLDEN_CARROT) < 3
                || (useSpeed.getValue() && findSlot(this::isSpeedPotion) < 0);
    }

    private int invisCount() {
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (isInvisPotion(stack)) total++;
        }
        return total;
    }

    private int countItem(net.minecraft.item.Item item) {
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.isOf(item)) total += stack.getCount();
        }
        return total;
    }

    private int findSlot(Predicate<ItemStack> match) {
        for (int slot = 0; slot < 36; slot++) {
            if (match.test(mc.player.getInventory().getStack(slot))) return slot;
        }
        return -1;
    }

    private boolean hasLootToStore() {
        boolean keptPotion = false, keptCarrot = false;

        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (stack.isEmpty()) continue;
            if (useSpeed.getValue() && isSpeedPotion(stack)) continue;

            if (!keptPotion && isInvisPotion(stack)) keptPotion = true;
            else if (!keptCarrot && stack.isOf(Items.GOLDEN_CARROT)) keptCarrot = true;
            else return true;
        }
        return false;
    }

    private void useSlot(int slot) {
        if (slot < 0 || mc.currentScreen != null) return;

        if (slot >= 9) {
            swapToHotbar(slot, true);
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        holdUse();
    }

    private void holdUse() {
        useRequested = true;
        useHeld = true;
        mc.options.useKey.setPressed(true);
    }

    private void releaseUse() {
        if (!useHeld) return;
        useHeld = false;
        eating = false;
        eatTick = 0;
        mc.options.useKey.setPressed(false);
    }

    /** Морковь в руке или уже во рту — трогать слоты и шагать нельзя, укус сорвётся. */
    private boolean busyEating() {
        if (!eating || mc.player == null) return false;
        if (mc.player.isUsingItem()) return mc.player.getActiveItem().isOf(Items.GOLDEN_CARROT);
        return mc.player.getMainHandStack().isOf(Items.GOLDEN_CARROT);
    }

    private void eatIfNeeded() {
        // начатый укус доводим до конца: прерванный морковь не тратит, но и голод не лечит
        boolean chewing = busyEating();
        int food = mc.player.getHungerManager().getFoodLevel();

        if (mc.currentScreen != null || isDrinking() || (!chewing && mc.player.age < eatBlocked)
                || (!chewing && (nearCurrentChest(5.0) || food >= 17))) {
            eating = false;
            eatTick = 0;
            return;
        }

        int slot = findSlot(stack -> stack.isOf(Items.GOLDEN_CARROT));
        if (slot < 0) {
            eating = false;
            eatTick = 0;
            return;
        }

        if (eatTick == 0 || food != eatFood) {
            eatTick = mc.player.age;
            eatFood = food;
        } else if (mc.player.age - eatTick > 140) {
            // семь секунд возимся без единицы сытости — сервер укус не проводит, не тормозим фарм
            eating = false;
            eatTick = 0;
            eatBlocked = mc.player.age + 200;
            return;
        }

        eating = true;
        walkTarget = null;

        // морковь уже в руке — только держим кнопку, переключение слота сорвало бы укус
        if (mc.player.getMainHandStack().isOf(Items.GOLDEN_CARROT)) {
            holdUse();
            return;
        }

        useSlot(slot);
    }

    private void freeHand() {
        if (busyEating()) return;
        if (mc.player.getMainHandStack().isEmpty()) return;

        for (int slot = 0; slot < 9; slot++) {
            if (!mc.player.getInventory().getStack(slot).isEmpty()) continue;

            if (mc.player.getInventory().getSelectedSlot() != slot) {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            return;
        }

        // весь хотбар занят: убираем предмет в инвентарь, но только стоя — на бегу клик откатится
        for (int slot = 9; slot < 36; slot++) {
            if (!mc.player.getInventory().getStack(slot).isEmpty()) continue;
            if (isMoving() || mc.player.age % 8 != 0) return;

            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot,
                    mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
            return;
        }
    }

    private boolean isJunk(ItemStack stack) {
        if (loot.is("Низкий")) return false;
        return junkBase(stack) || (loot.is("Высокий") && junkHigh(stack));
    }

    private boolean junkBase(ItemStack stack) {
        return stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.AXES)
                || stack.getItem() instanceof BannerItem
                || (stack.getItem() instanceof SmithingTemplateItem && !stack.isOf(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE))
                || stack.isOf(Items.BLAZE_ROD) || stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.TRIDENT)
                || stack.isOf(Items.NAME_TAG) || stack.isOf(Items.SCULK) || stack.isOf(Items.SCULK_SENSOR)
                || stack.isOf(Items.ENDER_CHEST) || stack.isOf(Items.REINFORCED_DEEPSLATE) || stack.isOf(Items.PUFFERFISH)
                || stack.isOf(Items.HONEY_BOTTLE) || stack.isOf(Items.FERMENTED_SPIDER_EYE) || stack.isOf(Items.ANVIL)
                || stack.isOf(Items.COOKED_PORKCHOP);
    }

    private boolean junkHigh(ItemStack stack) {
        return stack.isIn(ItemTags.ARROWS) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES)
                || stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.DISC_FRAGMENT_5) || stack.isOf(Items.NAUTILUS_SHELL)
                || stack.isOf(Items.BOOKSHELF) || stack.isOf(Items.COOKED_MUTTON) || stack.isOf(Items.SKELETON_SPAWN_EGG)
                || stack.isOf(Items.CREEPER_SPAWN_EGG) || stack.isOf(Items.ZOMBIE_SPAWN_EGG) || stack.isOf(Items.VINDICATOR_SPAWN_EGG)
                || stack.isOf(Items.PIGLIN_SPAWN_EGG) || stack.isOf(Items.VEX_SPAWN_EGG) || stack.isOf(Items.ENDERMITE_SPAWN_EGG)
                || stack.isOf(Items.CAT_SPAWN_EGG) || stack.isOf(Items.FIRE_CHARGE) || stack.isOf(Items.LEATHER)
                || stack.isOf(Items.SHULKER_SHELL) || stack.isOf(Items.EXPERIENCE_BOTTLE) || stack.isOf(Items.WITHER_ROSE)
                || stack.isOf(Items.EMERALD) || stack.isOf(Items.SUGAR) || hasFtid(stack, "potion-popper")
                || stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE) || stack.isOf(Items.GHAST_TEAR)
                || stack.isOf(Items.DRAGON_BREATH) || stack.isOf(Items.ENCHANTING_TABLE) || stack.isOf(Items.DIAMOND_HELMET)
                || stack.isOf(Items.DIAMOND_CHESTPLATE) || stack.isOf(Items.DIAMOND_LEGGINGS) || stack.isOf(Items.DIAMOND_BOOTS);
    }

    private boolean hasFtid(ItemStack stack, String id) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && id.equals(data.copyNbt().getCompoundOrEmpty("PublicBukkitValues").getString("minecraft:ftid", ""));
    }

    private boolean isInvisPotion(ItemStack stack) {
        RegistryEntry<Potion> potion = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).potion().orElse(null);
        return potion != null && (potion.equals(Potions.INVISIBILITY) || potion.equals(Potions.LONG_INVISIBILITY));
    }

    private boolean isSpeedPotion(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) return false;

        for (StatusEffectInstance effect : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
            if (effect.getEffectType().equals(StatusEffects.SPEED)) return true;
        }
        return false;
    }

    /** Ближайший подходящий сундук. Тот, из которого уже брали, в приоритете; «не те» помечены на 2 минуты. */
    private BlockPos findNearbyChest(boolean hopper) {
        BlockPos origin = mc.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos remembered = hopper ? depositChest : supplyChest;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;

        long now = System.currentTimeMillis();
        badChests.values().removeIf(expire -> expire < now);

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CHEST) || isHopperChest(pos) != hopper) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;

                    boolean known = pos.equals(remembered);
                    double distSq = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));

                    if (!known && (distSq >= bestSq || badChests.containsKey(pos))) continue;
                    if (!visibleChest(pos)) continue;

                    if (known) return pos.toImmutable();
                    bestSq = distSq;
                    best = pos.toImmutable();
                }
            }
        }
        return best;
    }

    private boolean visibleChest(BlockPos pos) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getBlockPos().equals(pos);
    }

    private boolean isHopperChest(BlockPos pos) {
        if (mc.world.getBlockState(pos.down()).isOf(Blocks.HOPPER)) return true;

        BlockState state = mc.world.getBlockState(pos);
        if (state.get(Properties.CHEST_TYPE) == ChestType.SINGLE) return false;

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos partner = pos.offset(direction);
            BlockState other = mc.world.getBlockState(partner);

            if (other.isOf(Blocks.CHEST) && other.get(Properties.CHEST_TYPE) != ChestType.SINGLE
                    && other.get(Properties.CHEST_TYPE) != state.get(Properties.CHEST_TYPE)
                    && other.get(Properties.HORIZONTAL_FACING) == state.get(Properties.HORIZONTAL_FACING)
                    && mc.world.getBlockState(partner.down()).isOf(Blocks.HOPPER)) return true;
        }
        return false;
    }

    private BlockPos hopperChestFar() {
        BlockPos origin = mc.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CHEST) || !isHopperChest(pos)) continue;

                    double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private void dropFloorTrash() {
        if (!trash.getValue() || mc.currentScreen != null || mc.player.age % 4 != 0) return;
        if (busyEating() || isDrinking()) return;

        // мусор из хотбара выкидываем как руками: берём в руку и жмём Q
        for (int slot = 0; slot < 9; slot++) {
            if (!isFloorTrash(mc.player.getInventory().getStack(slot))) continue;

            if (mc.player.getInventory().getSelectedSlot() != slot) {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                return;
            }

            mc.player.dropSelectedItem(true);
            return;
        }

        // остальное сначала поднимаем в хотбар — без остановки, мусор не стоит потерянного времени
        for (int slot = 9; slot < 36; slot++) {
            if (!isFloorTrash(mc.player.getInventory().getStack(slot))) continue;

            swapToHotbar(slot, false);
            return;
        }
    }

    /**
     * Тащит предмет из инвентаря в хотбар. Клик по слотам проходит только когда стоим,
     * а целевой слот перебираем после каждой неудачи: обмен с занятым слотом сервер может не принять.
     */
    private boolean swapToHotbar(int slot, boolean hold) {
        if (mc.player == null || slot < 9 || slot > 35 || mc.currentScreen != null) return false;
        if (mc.player.age < swapBlocked) return false;

        // чужой обмен ещё не разрешился: одного клика в работе достаточно
        if (swapSource >= 0 && swapSource != slot) return false;
        if (hold) walkTarget = null;

        // на бегу такие клики сервер часто откатывает назад, так что сначала останавливаемся
        if (isMoving()) return false;
        if (swapTarget >= 0) return false;
        if (swapTick > 0 && mc.player.age - swapTick < 8) return false;

        if (swapSource != slot) {
            swapSource = slot;
            swapTries = 0;
        }

        swapTarget = hotbarTarget(swapTries);
        swapTick = mc.player.age;
        swapStack = mc.player.getInventory().getStack(slot).copy();
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot,
                swapTarget, SlotActionType.SWAP, mc.player);
        return true;
    }

    /**
     * Разбирает прошлый обмен: предмет ушёл — забываем о нём, застрял — считаем неудачу
     * и в следующий раз пробуем другой слот хотбара. Пять неудач — слот не трогаем десять секунд.
     */
    private void settleSwap() {
        if (mc.player == null || swapSource < 0 || swapTarget < 0) return;
        if (mc.player.age - swapTick < 8) return;

        swapTarget = -1;

        if (!ItemStack.areItemsAndComponentsEqual(mc.player.getInventory().getStack(swapSource), swapStack)) {
            swapSource = -1;
            swapTries = 0;
            return;
        }

        if (++swapTries < 5) return;

        if (debug.getValue()) {
            ChatUtils.addChatMessage("§7[AW] §cслот §f" + swapSource + " §cне перекладывается в хотбар");
        }

        swapSource = -1;
        swapTries = 0;
        swapBlocked = mc.player.age + 200;
    }

    /** Слот хотбара под обмен: сначала пустые, дальше по кругу без зелий и моркови. */
    private int hotbarTarget(int attempt) {
        int[] pool = new int[9];
        int count = 0;

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) pool[count++] = slot;
        }

        if (count == 0) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (!isInvisPotion(stack) && !isSpeedPotion(stack) && !stack.isOf(Items.GOLDEN_CARROT)) pool[count++] = slot;
            }
        }

        if (count == 0) return mc.player.getInventory().getSelectedSlot();
        return pool[Math.floorMod(attempt, count)];
    }

    private boolean isFloorTrash(ItemStack stack) {
        return !stack.isEmpty() && isJunk(stack) && !isInvisPotion(stack) && !isSpeedPotion(stack) && !stack.isOf(Items.GOLDEN_CARROT);
    }

    private void updateChestMemory() {
        int anarchy = ServerUtil.anarchy;
        if (anarchy < 0) return;

        long now = System.currentTimeMillis();
        for (BlockPos chest : helper().getChests()) {
            long remaining = helper().getRemaining(chest);
            if (remaining <= 1000) continue;

            String key = chestKey(anarchy, chest);
            timers.put(key, now + remaining);
            emptied.remove(key);
            if (remaining > period && remaining <= TTL) period = remaining;
        }
    }

    private long chestRemaining(BlockPos chest) {
        if (chest == null) return -1L;

        long remaining = helper().getRemaining(chest);
        if (remaining >= 0) return remaining;

        String key = chestKey(ServerUtil.anarchy, chest);
        Long readyAt = timers.get(key);

        if (readyAt == null) {
            Long openedAt = emptied.get(key);
            if (openedAt == null || period <= 60000L) return -1L;
            readyAt = openedAt + period;
        }

        long left = readyAt - System.currentTimeMillis();
        return left > 0 ? left : -1L;
    }

    private String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private BlockPos parsePos(String value) {
        try {
            String[] parts = value.split(",");
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String chestKey(int anarchy, BlockPos chest) {
        return anarchy + ";" + chest.getX() + "," + chest.getY() + "," + chest.getZ();
    }

    private void markEmptied() {
        int anarchy = ServerUtil.anarchy;
        BlockPos chest = openedChest();
        if (anarchy < 0 || chest == null) return;

        String key = chestKey(anarchy, chest);
        long now = System.currentTimeMillis();
        emptied.put(key, now);
        if (period > 60000L) timers.put(key, now + period);
    }

    /** Пока сервер открывал GUI, цель могла сменить: таймер ставим тому сундуку, в который тыкали. */
    private BlockPos openedChest() {
        if (openClick != null && mc.player.squaredDistanceTo(Vec3d.ofCenter(openClick)) < 36.0
                && helper().getChests().contains(openClick)) return openClick;

        return currentChest;
    }

    private Path timersFile() {
        return Client.get().configManager().getConfigDir().getParent().resolve("warden_timers.json");
    }

    private void saveTimers() {
        try {
            long now = System.currentTimeMillis();
            timers.entrySet().removeIf(entry -> entry.getValue() + TTL < now);
            emptied.entrySet().removeIf(entry -> entry.getValue() + TTL < now);

            JsonObject root = new JsonObject();
            root.addProperty("period", period);

            JsonObject chests = new JsonObject();
            timers.forEach(chests::addProperty);
            JsonObject opened = new JsonObject();
            emptied.forEach(opened::addProperty);

            root.add("chests", chests);
            root.add("opened", opened);
            if (supplyChest != null) root.addProperty("supply", posKey(supplyChest));
            if (depositChest != null) root.addProperty("deposit", posKey(depositChest));
            Files.writeString(timersFile(), root.toString());
        } catch (Exception ignored) {
        }
    }

    private void loadTimers() {
        try {
            Path file = timersFile();
            if (!Files.exists(file)) return;

            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            period = root.has("period") ? root.get("period").getAsLong() : 0L;
            if (root.has("supply")) supplyChest = parsePos(root.get("supply").getAsString());
            if (root.has("deposit")) depositChest = parsePos(root.get("deposit").getAsString());
            long now = System.currentTimeMillis();

            JsonObject chests = root.getAsJsonObject("chests");
            if (chests != null) {
                for (String key : chests.keySet()) {
                    long readyAt = chests.get(key).getAsLong();
                    if (readyAt + TTL >= now) timers.put(key, readyAt);
                }
            }

            JsonObject opened = root.getAsJsonObject("opened");
            if (opened != null) {
                for (String key : opened.keySet()) {
                    long at = opened.get(key).getAsLong();
                    if (at + TTL >= now) emptied.put(key, at);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private double stacksPerVisit(int anarchy) {
        int[] stats = anarchyStats.get(anarchy);
        return stats == null || stats[1] <= 0 ? 0.0 : (double) stats[0] / stats[1];
    }

    /** Уходит с выработанной анархии на следующую по списку: порядок задаётся в клик гуи. */
    private boolean hopNext() {
        if (anarchies.size() <= 2) return false;

        int next = nextFarmIndex();
        if (next == farmIndex) return false;

        hopTimer.reset();
        clearRecall();
        notifyTg("[AW] перехожу на анархию " + anarchies.at(next));

        if (hasLootToStore()) {
            died = true;
            state = State.ESCAPE;
            return true;
        }

        goFarm(next);
        return true;
    }

    /** Идёт к запомненному сундуку на другой анархии: с лутом — сначала через склад. */
    private void hopTo(Recall recall) {
        hopTimer.reset();
        recallChest = recall.chest();
        recallAnarchy = recall.anarchy();

        long seconds = recall.delay() / 1000L;
        notifyTg("[AW] возвращаюсь на анархию " + recall.anarchy() + " к сундуку "
                + recall.chest().getX() + " " + recall.chest().getY() + " " + recall.chest().getZ()
                + (seconds > 0 ? ", будет готов через " + seconds + "с" : ", он уже готов"));

        if (hasLootToStore()) {
            died = true;
            state = State.ESCAPE;
            return;
        }
        goFarm(recall.index());
    }

    private void goFarm(int index) {
        farmIndex = index;
        currentChest = null;
        walkTarget = null;
        visitAnarchy = -1;
        openAttempts.clear();
        state = State.COLLECTING;
    }

    /** Пока запомненный сундук далеко, идём прямо к нему: ESP его ещё не видит. */
    private boolean travelToRecall() {
        if (recallChest == null || ServerUtil.anarchy != recallAnarchy) return false;

        if (!inZoneBox(recallChest.getX(), recallChest.getZ())
                || mc.player.squaredDistanceTo(Vec3d.ofCenter(recallChest)) <= 400.0
                || unreachable.containsKey(recallChest)
                || helper().getChests().contains(recallChest)) {
            clearRecall();
            return false;
        }

        walkTo(recallChest, 2);
        return true;
    }

    private void clearRecall() {
        recallChest = null;
        recallAnarchy = -1;
    }

    /** Индекс анархии, где нас ждёт запомненный сундук, либо -1. */
    private int recallIndex() {
        return recallChest == null ? -1 : indexOfAnarchy(recallAnarchy);
    }

    /** Следующая анархия фермы по кругу: нулевой в списке стоит склад, поэтому его пропускаем. */
    private int nextFarmIndex() {
        if (anarchies.size() <= 1) return 1;
        return farmIndex + 1 >= anarchies.size() ? 1 : farmIndex + 1;
    }

    private int indexOfAnarchy(int anarchy) {
        if (anarchy < 0) return -1;

        for (int index = 0; index < anarchies.size(); index++) {
            if (anarchies.at(index) == anarchy) return index;
        }
        return -1;
    }

    /** Запомненный сундук на другой анархии, чей таймер уже дошёл до порога ожидания. */
    private Recall soonestRecall(long threshold) {
        int current = ServerUtil.anarchy;
        long now = System.currentTimeMillis();
        Recall best = null;

        for (Map.Entry<String, Long> entry : timers.entrySet()) {
            long left = entry.getValue() - now;
            if (left > threshold) continue;

            Recall recall = parseTimerKey(entry.getKey(), left);
            if (recall == null || recall.anarchy() == current) continue;
            if (best == null || recall.delay() < best.delay()
                    || (recall.delay() == best.delay() && recall.left() > best.left())) best = recall;
        }
        return best;
    }

    /** Разбирает ключ таймера в цель, если такая анархия есть в списке фермы. */
    private Recall parseTimerKey(String key, long left) {
        int split = key.indexOf(';');
        if (split <= 0) return null;

        try {
            int index = indexOfAnarchy(Integer.parseInt(key.substring(0, split)));
            if (index < 1) return null;

            String[] parts = key.substring(split + 1).split(",");
            if (parts.length != 3) return null;

            BlockPos chest = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return new Recall(index, anarchies.at(index), chest, left);
        } catch (Exception error) {
            return null;
        }
    }

    /** Сколько ждать самый скорый запомненный сундук анархии, -1 если таймеров нет. */
    private long soonestOn(int anarchy) {
        String prefix = anarchy + ";";
        long now = System.currentTimeMillis();
        long best = -1L;

        for (Map.Entry<String, Long> entry : timers.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;

            long wait = Math.max(0L, entry.getValue() - now);
            if (best < 0 || wait < best) best = wait;
        }
        return best;
    }

    /** left — сырой остаток: отрицательный значит сундук уже стоит готовым. */
    private record Recall(int index, int anarchy, BlockPos chest, long left) {
        long delay() {
            return Math.max(0L, left);
        }
    }

    public String statsLine() {
        long minutes = Math.max(1L, (System.currentTimeMillis() - startedAt) / 60000L);
        StringBuilder line = new StringBuilder("сундуков вскрыто §f" + looted + " §7| стеков лута §f" + stacksTaken
                + " §7(~§f" + (stacksTaken * 60L / minutes) + "§7/час) | таймеров в памяти §f" + timers.size()
                + " §7| период возрождения §f" + (period / 1000L) + "с");

        if (!lootItems.isEmpty()) {
            line.append(" §7| лут: §f").append(topLoot(6));
        }
        return line.toString();
    }

    public String topLoot(int limit) {
        return top(lootItems, limit);
    }

    public String topStored(int limit) {
        return top(stored, limit);
    }

    private String top(Map<String, Integer> source, int limit) {
        StringBuilder items = new StringBuilder();
        int shown = 0;

        for (Map.Entry<String, Integer> entry : source.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted((first, second) -> Integer.compare(second.getValue(), first.getValue())).toList()) {
            if (shown++ >= limit) {
                items.append("...");
                break;
            }
            items.append(entry.getKey()).append(" x").append(entry.getValue()).append(", ");
        }

        if (shown <= limit && items.length() > 2) items.setLength(items.length() - 2);
        return items.toString();
    }

    private void printDebug() {
        ChatUtils.addChatMessage("§7[AW] состояние §f" + state + " §7| анархия §f" + ServerUtil.anarchy
                + "§7, нужна §f" + (anarchies.isEmpty() ? "нет" : String.valueOf(anarchies.at(farmIndex)))
                + " §7(список: §f" + anarchies.size() + "§7) | в зоне фермы §f" + inFarmZone()
                + " §7| сундуков у ESP §f" + helper().getChests().size() + " §7| варден §f" + wardenAggro()
                + " §7| пвп §f" + ServerUtil.isPvp() + " §7| цель §f" + (walkTarget == null ? "нет" : String.valueOf(walkTarget))
                + " §7| baritone: §f" + Pathing.status());

        ChatUtils.addChatMessage("§7[AW] застрял §c" + stuck() + " §7| двигаюсь §f" + isMoving() + " §7| в блоке §f" + insideBlock()
                + " §7| свечи §f" + (mc.world.getBlockState(mc.player.getBlockPos()).isIn(BlockTags.CANDLES)
                || mc.world.getBlockState(mc.player.getBlockPos().down()).isIn(BlockTags.CANDLES))
                + " §7| обход §f" + detourTries + "/3 §7| недоступных §f" + unreachable.size());

        BlockPos reach = findNearbyChest(true);
        BlockPos far = hopperChestFar();
        ChatUtils.addChatMessage("§7[AW] лут в инвентаре §f" + hasLootToStore() + " §7| приёмник в руке §f"
                + (reach == null ? "нет" : String.valueOf(reach)) + " §7| ближайший приёмник §f"
                + (far == null ? "не найден в радиусе 16" : far + " (" + (int) Math.sqrt(mc.player.squaredDistanceTo(Vec3d.ofCenter(far))) + " бл.)")
                + " §7| экран §f" + (mc.currentScreen == null ? "нет" : mc.currentScreen.getClass().getSimpleName()));

        String chest = "нет";
        if (currentChest != null) {
            long left = chestRemaining(currentChest);
            chest = currentChest.toShortString() + " (" + (left < 0 ? "готов" : left / 1000L + "с")
                    + ", ждать всего " + chestCost(currentChest) / 1000L + "с)";
        }

        ChatUtils.addChatMessage("§7[AW] выбранный сундук §f" + chest
                + " §7| порог ожидания §f" + waitLimit.getValue().intValue() + "с"
                + " §7| в пороге сундуков §f" + helper().getChests().stream().filter(this::chestUsable).count());

        ChatUtils.addChatMessage("§7[AW] сундук с зельями §f" + (supplyChest == null ? "не найден" : supplyChest.toShortString())
                + " §7| приёмник §f" + (depositChest == null ? "не найден" : depositChest.toShortString())
                + " §7| помечено «не те» §f" + badChests.size()
                + " §7| точка склада §f" + (homeSpot == null ? "нет" : homeSpot.toShortString())
                + " §7| караулю §f" + (currentChest != null && !chestReady(currentChest) && nearCurrentChest(5.0))
                + " §7| стаков за тик §f" + lootSpeed.getValue().intValue());

        ChatUtils.addChatMessage("§7[AW] " + statsLine());

        Recall recall = soonestRecall((long) (waitLimit.getValue() * 1000.0F));
        ChatUtils.addChatMessage("§7[AW] цель возврата §f" + (recallChest == null ? "нет" : recallChest + " @ " + recallAnarchy)
                + " §7| скорый сундук на другой анархии §f"
                + (recall == null ? "нет" : recall.chest() + " @ " + recall.anarchy() + " через " + recall.delay() / 1000L + "с"));
    }

    private int pvpSeconds() {
        for (var bar : mc.inGameHud.getBossBarHud().bossBars.values()) {
            String name = bar.getName().getString().toLowerCase();
            if (!name.contains("pvp") && !name.contains("пвп")) continue;

            Matcher clock = CLOCK.matcher(name);
            if (clock.find()) return Integer.parseInt(clock.group(1)) * 60 + Integer.parseInt(clock.group(2));

            Matcher number = NUMBER.matcher(name);
            if (number.find()) return Integer.parseInt(number.group());
        }
        return -1;
    }

    private boolean onHomeAnarchy() {
        int home = anarchies.home();
        return home >= 0 && ServerUtil.anarchy == home;
    }

    /** Держит бота в согласии с настройками, даже когда сам модуль выключен. */
    private void startTelegramWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    syncTelegram();
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }, "warden-telegram-watchdog");

        watchdog.setDaemon(true);
        watchdog.start();
    }

    /** Пересоздаёт бота при смене токена или чата и гасит его, когда настройка выключена. */
    private synchronized void syncTelegram() {
        String token = tgToken.getValue() == null ? "" : tgToken.getValue().trim();
        String chat = tgChat.getValue() == null ? "" : tgChat.getValue().trim();

        if (!telegram.getValue() || token.isEmpty() || chat.isEmpty()) {
            if (bot != null) {
                bot.stop();
                bot = null;
            }
            return;
        }

        if (bot != null && bot.isRunning() && bot.token().equals(token) && bot.chatId().equals(chat)) return;

        if (bot != null) bot.stop();
        bot = new TelegramBot(token, chat, text -> mc.execute(() -> handleCommand(text)));
        bot.start();
        bot.send("Auto Warden на связи. /help — список команд.");
    }

    private void notifyTg(String text) {
        if (tgNotify.getValue()) sendTg(text);
    }

    private void sendTg(String text) {
        TelegramBot current = bot;
        if (current != null) current.send(text);
    }

    /** Автоотчёт раз в N минут, 0 — выключено. */
    private void periodicReport() {
        int minutes = tgReport.getValue().intValue();
        if (minutes <= 0 || !tgNotify.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastReport < minutes * 60000L) return;

        lastReport = now;
        sendTg("[AW] отчёт" + NL + statsLine() + NL + storageLine());
    }

    /** Разбор команды из телеграма. Уже в потоке клиента, поэтому можно трогать игру. */
    private void handleCommand(String raw) {
        if (raw == null) return;

        String text = raw.trim();
        if (text.startsWith("/")) text = text.substring(1);

        int at = text.indexOf('@');
        int space = text.indexOf(' ');
        if (at > 0 && (space < 0 || at < space)) text = text.substring(0, at) + (space < 0 ? "" : text.substring(space));

        String[] parts = text.trim().split(" +", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "help", "start", "помощь", "команды" -> sendTg(helpText());
            case "stats", "стат", "статы" -> sendTg("[AW] " + statsLine());
            case "storage", "склад" -> sendTg("[AW] " + storageLine());
            case "loot", "лут" -> sendTg(lootText());
            case "anarchy", "анархия", "анархии" -> anarchyCommand(argument);
            case "where", "где" -> sendTg(whereText());
            case "inv", "инв", "инвентарь" -> sendTg(inventoryText());
            case "on", "вкл", "включить" -> {
                if (mc.player == null) sendTg("[AW] сейчас не в игре, включать нечего.");
                else if (isEnabled()) sendTg("[AW] уже работаю.");
                else {
                    setEnabled(true);
                    sendTg("[AW] включил.");
                }
            }
            case "off", "выкл", "выключить" -> {
                if (!isEnabled()) sendTg("[AW] уже выключен.");
                else {
                    setEnabled(false);
                    sendTg("[AW] выключил.");
                }
            }
            case "reset", "сброс" -> {
                stored.clear();
                storedStacks = 0;
                storedChests = 0;
                deposits = 0;
                tripStacks = 0;
                saveStorage();
                sendTg("[AW] статистику склада обнулил.");
            }
            case "debug", "отладка" -> {
                debug.set(!debug.getValue());
                sendTg("[AW] отладка " + (debug.getValue() ? "включена" : "выключена") + ".");
            }
            default -> sendTg("[AW] не знаю команду " + command + ", посмотри /help.");
        }
    }

    private String helpText() {
        return "Auto Warden — команды:" + NL
                + "/stats — вскрытые сундуки и лут за сессию" + NL
                + "/storage — сводка по складу" + NL
                + "/loot — полный список лута на складе" + NL
                + "/anarchy — список анархий и что про них известно, /anarchy 305 — идти на неё" + NL
                + "/where — где я, состояние, хп" + NL
                + "/inv — что лежит в инвентаре" + NL
                + "/on, /off — включить или выключить фарм" + NL
                + "/reset — обнулить статистику склада" + NL
                + "/debug — отладка в игровой чат" + NL
                + "Русские слова тоже понимаю: статы, склад, лут, анархия, где, инв, вкл, выкл, сброс.";
    }

    private void anarchyCommand(String argument) {
        if (argument.isEmpty()) {
            StringBuilder text = new StringBuilder("[AW] склад: " + anarchies.home() + ", сейчас: " + ServerUtil.anarchy);

            for (int index = 1; index < anarchies.size(); index++) {
                int anarchy = anarchies.at(index);

                text.append(NL).append(index == farmIndex ? "> " : "  ").append(anarchy)
                        .append(" — стеков за визит ").append(String.format("%.1f", stacksPerVisit(anarchy)))
                        .append(", сундуков в памяти ").append(knownChests(anarchy))
                        .append(waitText(soonestOn(anarchy)));
            }

            if (anarchies.size() < 2) text.append(NL).append("список фермы пуст, добавь анархии в клик гуи.");
            sendTg(text.toString());
            return;
        }

        String digits = argument.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            sendTg("[AW] нужен номер анархии, например /anarchy 305");
            return;
        }

        int target = Integer.parseInt(digits);
        int index = indexOfAnarchy(target);

        if (index < 0) {
            sendTg("[AW] анархии " + target + " нет в списке, добавь её в клик гуи.");
            return;
        }
        if (index == 0) {
            sendTg("[AW] " + target + " — это анархия склада, фармить там нечего.");
            return;
        }

        clearRecall();
        hopTimer.reset();
        goFarm(index);
        sendTg("[AW] иду фармить анархию " + target + ".");
    }

    private String waitText(long wait) {
        if (wait < 0) return "";
        return wait == 0 ? ", сундук готов" : ", ближайший через " + wait / 1000L + "с";
    }

    private int knownChests(int anarchy) {
        String prefix = anarchy + ";";
        int count = 0;

        for (String key : timers.keySet()) {
            if (key.startsWith(prefix)) count++;
        }
        return count;
    }

    private String whereText() {
        if (mc.player == null) return "[AW] сейчас не в игре.";

        BlockPos pos = mc.player.getBlockPos();
        return "[AW] " + (isEnabled() ? "работаю" : "выключен") + ", состояние " + state
                + ", анархия " + ServerUtil.anarchy
                + " (нужна " + (anarchies.isEmpty() ? "нет" : String.valueOf(anarchies.at(farmIndex))) + ")" + NL
                + "хп " + (int) mc.player.getHealth() + ", позиция " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + ", в зоне фермы " + inFarmZone() + NL
                + "сундуков у ESP " + helper().getChests().size() + ", варден рядом " + wardenAggro()
                + ", пвп " + ServerUtil.isPvp()
                + (recallChest == null ? "" : NL + "жду сундук " + recallChest.getX() + " " + recallChest.getY() + " "
                + recallChest.getZ() + " на анархии " + recallAnarchy + waitText(soonestOn(recallAnarchy)));
    }

    private String inventoryText() {
        if (mc.player == null) return "[AW] сейчас не в игре.";

        Map<String, Integer> items = new LinkedHashMap<>();
        for (ItemStack stack : mc.player.getInventory().getMainStacks()) {
            if (!stack.isEmpty()) items.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
        }

        if (items.isEmpty()) return "[AW] инвентарь пуст.";
        return "[AW] в инвентаре: " + top(items, 12);
    }

    /** Закрывает рейс на склад: считает его в статистику и пишет в телеграм. */
    private void finishDeposit() {
        if (tripStacks <= 0) return;

        deposits++;
        notifyTg("[AW] сдал на склад " + tripStacks + " стеков, рейс #" + deposits + "." + NL + storageLine());
        tripStacks = 0;
        saveStorage();
    }

    public String storageLine() {
        StringBuilder line = new StringBuilder("на складе §f" + storedStacks + " §7стеков за §f" + deposits
                + " §7рейсов, вскрыто §f" + storedChests + " §7сундуков");

        if (!stored.isEmpty()) line.append(" §7| §f").append(topStored(8));
        return line.toString();
    }

    private String lootText() {
        if (stored.isEmpty()) return "[AW] склад пуст, на него ещё ничего не сдавал.";

        StringBuilder text = new StringBuilder("[AW] лут на складе (" + storedStacks + " стеков за " + deposits + " рейсов):");
        stored.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted((first, second) -> Integer.compare(second.getValue(), first.getValue()))
                .limit(30)
                .forEach(entry -> text.append(NL).append("- ").append(entry.getKey()).append(" x").append(entry.getValue()));

        if (stored.size() > 30) text.append(NL).append("...и ещё ").append(stored.size() - 30).append(" видов предметов");
        return text.toString();
    }

    private Path storageFile() {
        return Client.get().configManager().getConfigDir().getParent().resolve("warden_storage.json");
    }

    private void saveStorage() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("stacks", storedStacks);
            root.addProperty("chests", storedChests);
            root.addProperty("deposits", deposits);

            JsonObject items = new JsonObject();
            stored.forEach(items::addProperty);
            root.add("items", items);

            JsonObject anarchy = new JsonObject();
            anarchyStats.forEach((key, value) -> anarchy.addProperty(String.valueOf(key), value[0] + ":" + value[1]));
            root.add("anarchy", anarchy);

            Files.writeString(storageFile(), root.toString());
        } catch (Exception ignored) {
        }
    }

    private void loadStorage() {
        try {
            Path file = storageFile();
            if (!Files.exists(file)) return;

            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            storedStacks = root.has("stacks") ? root.get("stacks").getAsInt() : 0;
            storedChests = root.has("chests") ? root.get("chests").getAsInt() : 0;
            deposits = root.has("deposits") ? root.get("deposits").getAsInt() : 0;

            stored.clear();
            JsonObject items = root.getAsJsonObject("items");
            if (items != null) {
                for (String key : items.keySet()) stored.put(key, items.get(key).getAsInt());
            }

            anarchyStats.clear();
            JsonObject anarchy = root.getAsJsonObject("anarchy");
            if (anarchy != null) {
                for (String key : anarchy.keySet()) {
                    String[] parts = anarchy.get(key).getAsString().split(":");
                    if (parts.length != 2) continue;
                    anarchyStats.put(Integer.parseInt(key), new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                }
            }
        } catch (Exception ignored) {
        }
    }
}
