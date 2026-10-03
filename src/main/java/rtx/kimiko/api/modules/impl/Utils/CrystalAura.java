package rtx.kimiko.api.modules.impl.Utils;

import mixin.accessor.MultiPlayerGameModeAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.modules.settings.impl.MultiSelectSetting;
import rtx.kimiko.api.modules.settings.impl.SeparatorSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.utils.storage.friend.FriendUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Feature(value={"crystalaura"})
public final class CrystalAura extends Module {

    // === Основные настройки ===
    private final ModeSetting targetType = (ModeSetting) register(
            new ModeSetting("Режим целей", "Тип используемых взрывов", "Все", "Кристаллы", "Якоря", "Все")
    );
    private final MultiSelectSetting targetEntities = (MultiSelectSetting) register(
            new MultiSelectSetting("Цели", "Кого атаковать кристаллом", "Игроки", "Мобы", "Животные", "Другие")
                    .selected("Игроки", "Мобы")
    );
    private final BooleanSetting ignoreFriends = (BooleanSetting) register(
            new BooleanSetting("Игнорировать друзей", "Не атаковать добавленных в друзья игроков", true)
    );
    private final SliderSetting targetRange = (SliderSetting) register(
            new SliderSetting("Поиск цели", "Радиус поиска цели (в блоках)").range(4.0f, 20.0f).increment(0.5f).setValue(12.0f)
    );
    private final BooleanSetting requireTarget = (BooleanSetting) register(
            new BooleanSetting("Требовать цель", "Взрывать только при наличии цели рядом", false)
    );
    private final ModeSetting priority = (ModeSetting) register(
            new ModeSetting("Приоритет", "Логика выбора точки взрыва", "Меньше урона себе", "Меньше урона себе", "Ближайший", "Больше урона цели")
    );

    // === Установка и взрыв ===
    private final SeparatorSetting actionSep = (SeparatorSetting) register(
            new SeparatorSetting("Действия")
    );
    private final BooleanSetting autoPlace = (BooleanSetting) register(
            new BooleanSetting("Авто-установка", "Автоматически ставить кристаллы на обсидиан/бедрок", true)
    );
    private final BooleanSetting autoBreak = (BooleanSetting) register(
            new BooleanSetting("Авто-взрыв", "Автоматически взрывать кристаллы", true)
    );
    private final BooleanSetting autoObsidian = (BooleanSetting) register(
            new BooleanSetting("Авто-обсидиан", "Ставить обсидиан возле цели, если его нет", true)
    );
    private final ModeSetting switchMode = (ModeSetting) register(
            new ModeSetting("Смена слота", "Режим переключения на кристаллы", "Обычный", "Обычный", "Возврат", "Без смены")
    );
    private final SliderSetting placeRange = (SliderSetting) register(
            new SliderSetting("Дистанция установки", "Максимальная дистанция для установки").range(1.0f, 6.0f).increment(0.1f).setValue(4.5f)
    );
    private final SliderSetting breakRange = (SliderSetting) register(
            new SliderSetting("Дистанция взрыва", "Максимальная дистанция для взрыва").range(1.0f, 6.0f).increment(0.1f).setValue(4.5f)
    );
    private final SliderSetting wallRange = (SliderSetting) register(
            new SliderSetting("Дистанция за стеной", "Максимальная дистанция через блоки").range(1.0f, 5.0f).increment(0.1f).setValue(3.5f)
    );
    private final SliderSetting delayTicks = (SliderSetting) register(
            new SliderSetting("Задержка (тики)", "Задержка между действиями в тиках").range(0, 10).increment(1).setValue(0.0f)
    );
    private final SliderSetting maxPerTick = (SliderSetting) register(
            new SliderSetting("Действий за тик", "Максимальное количество действий за один тик").range(1, 5).increment(1).setValue(2.0f)
    );
    private final BooleanSetting rotate = (BooleanSetting) register(
            new BooleanSetting("Поворот", "Наводить камеру на кристалл/блок", false)
    );
    private final BooleanSetting swing = (BooleanSetting) register(
            new BooleanSetting("Анимация руки", "Воспроизводить взмах руки при действии", true)
    );

    // === Анти-суицид ===
    private final SeparatorSetting antiSuicideSep = (SeparatorSetting) register(
            new SeparatorSetting("Анти-суицид")
    );
    private final BooleanSetting antiSuicide = (BooleanSetting) register(
            new BooleanSetting("Анти-суицид", "Блокирует взрыв, если расчётный урон убьёт вас или опустит HP ниже порога", true)
    );
    private final SliderSetting minHealth = (SliderSetting) register(
            new SliderSetting("Мин. здоровье", "Минимальный запас HP после взрыва").range(0.5f, 20.0f).increment(0.5f).setValue(3.0f)
    );

    // === Сейфить себя (Защита от урона) ===
    private final SeparatorSetting safeSep = (SeparatorSetting) register(
            new SeparatorSetting("Сейфить себя")
    );
    private final BooleanSetting safeMode = (BooleanSetting) register(
            new BooleanSetting("Сейф-режим", "Запрещает взрывать кристаллы, наносящие слишком много урона игроку", true)
    );
    private final SliderSetting maxSelfDamage = (SliderSetting) register(
            new SliderSetting("Макс. урон себе", "Максимально допустимый урон по себе от взрыва (в HP)").range(1.0f, 36.0f).increment(0.5f).setValue(14.0f)
    );
    private final BooleanSetting safeCover = (BooleanSetting) register(
            new BooleanSetting("Сейф укрытием", "Отдавать приоритет позициям, где ноги закрыты блоком (снижает урон в 2-4 раза)", true)
    );
    private final BooleanSetting autoSneak = (BooleanSetting) register(
            new BooleanSetting("Авто-присед", "Приседать в момент взрыва для гашения отдачи", true)
    );
    private final BooleanSetting autoShield = (BooleanSetting) register(
            new BooleanSetting("Авто-щит", "Поднимать щит в момент детонации (поглощает до 100% урона)", false)
    );
    private final SliderSetting minEnemyDamage = (SliderSetting) register(
            new SliderSetting("Мин. урон цели", "Минимальный урон по цели").range(0.0f, 36.0f).increment(0.5f).setValue(2.0f)
    );

    // Внутреннее состояние
    private int delayTimer = 0;
    private int sneakResetTicks = 0;
    private int shieldResetTicks = 0;
    private int previousSlot = -1;
    private int restoreSlotTicks = 0;
    private final Set<Integer> attackedCrystals = new HashSet<>();

    public CrystalAura() {
        super("Crystal Aura", "Автоматически ставит и взрывает кристаллы/маяки возле цели с защитой от самоубийства и минимизацией урона себе.", Category.UTILS);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        this.delayTimer = 0;
        this.attackedCrystals.clear();
        resetSneakAndShield();
        if (this.previousSlot != -1) {
            selectHotbarSlot(this.previousSlot);
            this.previousSlot = -1;
        }
    }

    private void resetSneakAndShield() {
        if (this.sneakResetTicks > 0) {
            this.mc.options.sneakKey.setPressed(false);
            this.sneakResetTicks = 0;
        }
        if (this.shieldResetTicks > 0) {
            this.mc.options.useKey.setPressed(false);
            this.shieldResetTicks = 0;
        }
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

        // Очищаем старые атакованные кристаллы, которых уже нет в мире
        if (!this.attackedCrystals.isEmpty() && world.getTime() % 10 == 0) {
            this.attackedCrystals.removeIf(id -> world.getEntityById(id) == null);
        }

        // Возврат слота хотбара
        if (this.restoreSlotTicks > 0) {
            this.restoreSlotTicks--;
            if (this.restoreSlotTicks == 0 && this.previousSlot != -1) {
                selectHotbarSlot(this.previousSlot);
                this.previousSlot = -1;
            }
        }

        // Сброс приседа и щита
        if (this.sneakResetTicks > 0) {
            this.sneakResetTicks--;
            if (this.sneakResetTicks == 0) {
                this.mc.options.sneakKey.setPressed(false);
            }
        }
        if (this.shieldResetTicks > 0) {
            this.shieldResetTicks--;
            if (this.shieldResetTicks == 0) {
                this.mc.options.useKey.setPressed(false);
            }
        }

        // Задержка между действиями
        if (this.delayTimer > 0) {
            this.delayTimer--;
            return;
        }

        int maxActions = (int) this.maxPerTick.getValue();
        int actionsDone = 0;

        // 1. Поиск вражеской цели
        LivingEntity target = findTarget(player, world);
        if (target == null && this.requireTarget.getValue()) {
            return;
        }

        // 2. ФАЗА ВЗРЫВА (Break Phase)
        if (this.autoBreak.getValue()) {
            actionsDone += executeBreak(player, world, interactionManager, target, maxActions - actionsDone);
        }

        // 3. ФАЗА УСТАНОВКИ (Place Phase)
        if (actionsDone < maxActions && this.autoPlace.getValue()) {
            actionsDone += executePlace(player, world, interactionManager, target, maxActions - actionsDone);
        }

        if (actionsDone > 0) {
            this.delayTimer = (int) this.delayTicks.getValue();
        }
    }

    /**
     * Выполняет взрыв кристаллов или маяков
     */
    private int executeBreak(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target, int maxAllowed) {
        int count = 0;
        float maxBreakDist = this.breakRange.getValue();
        float maxWallDist = this.wallRange.getValue();
        float playerHp = player.getHealth() + player.getAbsorptionAmount();
        boolean allowCrystals = !"Якоря".equals(this.targetType.getSelected());
        boolean allowAnchors = !"Кристаллы".equals(this.targetType.getSelected());

        List<BreakCandidate> candidates = new ArrayList<>();

        // 1. Поиск энд-кристаллов для взрыва
        if (allowCrystals) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity crystal)) continue;
                if (crystal.isRemoved() || !crystal.isAlive() || this.attackedCrystals.contains(crystal.getId())) continue;

                Vec3d crystalPos = crystal.getEntityPos();
                double distSq = player.squaredDistanceTo(crystalPos);
                if (distSq > maxBreakDist * maxBreakDist) continue;

                boolean canSee = player.canSee(crystal);
                if (!canSee && distSq > maxWallDist * maxWallDist) continue;

                // Если цель есть, кристалл должен наносить ей урон
                float enemyDamage = 0.0f;
                if (target != null) {
                    double distToTargetSq = target.squaredDistanceTo(crystalPos);
                    if (distToTargetSq > 36.0 && this.requireTarget.getValue()) continue;
                    enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                    if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                        continue;
                    }
                }

                // Расчёт урона себе
                float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);

                // Анти-суицид (если есть тотем — игрок защищен)
                if (this.antiSuicide.getValue() && !player.isCreative() && !hasTotem(player)) {
                    if (playerHp - selfDamage < this.minHealth.getValue()) {
                        continue;
                    }
                }

                // Сейф-режим
                if (this.safeMode.getValue() && !player.isCreative()) {
                    if (selfDamage > this.maxSelfDamage.getValue()) {
                        continue;
                    }
                }

                boolean feetCovered = isFeetCovered(player, crystalPos, world);
                candidates.add(new BreakCandidate(crystal, null, crystalPos, selfDamage, enemyDamage, feetCovered));
            }
        }

        // 2. Поиск заряженных якорей возрождения / маяков
        if (allowAnchors) {
            BlockPos playerPos = player.getBlockPos();
            int r = (int) Math.ceil(maxBreakDist);
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) continue;

                        int charges = state.get(RespawnAnchorBlock.CHARGES);
                        if (charges <= 0) continue;

                        Vec3d anchorPos = Vec3d.ofCenter(pos);
                        double distSq = player.squaredDistanceTo(anchorPos);
                        if (distSq > maxBreakDist * maxBreakDist) continue;

                        boolean canSee = canSeePos(player, anchorPos, world);
                        if (!canSee && distSq > maxWallDist * maxWallDist) continue;

                        float enemyDamage = 0.0f;
                        if (target != null) {
                            double distToTargetSq = target.squaredDistanceTo(anchorPos);
                            if (distToTargetSq > 36.0 && this.requireTarget.getValue()) continue;
                            enemyDamage = calculateExplosionDamage(anchorPos, 5.0f, target, world);
                            if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                                continue;
                            }
                        }

                        float selfDamage = calculateExplosionDamage(anchorPos, 5.0f, player, world);

                        if (this.antiSuicide.getValue() && !player.isCreative() && !hasTotem(player)) {
                            if (playerHp - selfDamage < this.minHealth.getValue()) continue;
                        }
                        if (this.safeMode.getValue() && !player.isCreative()) {
                            if (selfDamage > this.maxSelfDamage.getValue()) continue;
                        }

                        boolean feetCovered = isFeetCovered(player, anchorPos, world);
                        candidates.add(new BreakCandidate(null, pos, anchorPos, selfDamage, enemyDamage, feetCovered));
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return 0;
        }

        // Сортировка кандидатов
        sortBreakCandidates(candidates, player);

        for (BreakCandidate candidate : candidates) {
            if (count >= maxAllowed) break;

            if (this.rotate.getValue()) {
                lookAt(candidate.pos());
            }

            // Авто-присед
            if (this.autoSneak.getValue()) {
                this.mc.options.sneakKey.setPressed(true);
                this.sneakResetTicks = 2;
            }

            // Авто-щит
            if (this.autoShield.getValue() && hasShield(player)) {
                this.mc.options.useKey.setPressed(true);
                this.shieldResetTicks = 2;
            }

            if (candidate.crystal() != null) {
                // Взрыв энд-кристалла
                player.resetTicksSince();
                this.attackedCrystals.add(candidate.crystal().getId());
                interactionManager.attackEntity(player, candidate.crystal());
                candidate.crystal().discard(); // Убираем клиентскую сущность сразу, чтобы не блокировать установку нового кристалла!
                if (this.swing.getValue()) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            } else if (candidate.anchorPos() != null) {
                // Взрыв якоря: если держим светокамень, переключаемся на пустой слот/меч чтобы не зарядить
                if (player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    int nonGlow = findNonItemSlot(Items.GLOWSTONE);
                    if (nonGlow != -1) {
                        selectHotbarSlot(nonGlow);
                    }
                }
                BlockHitResult hit = new BlockHitResult(candidate.pos(), Direction.UP, candidate.anchorPos(), false);
                interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                if (this.swing.getValue()) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            }

            count++;
        }

        return count;
    }

    /**
     * Выполняет установку кристаллов или якорей возле цели
     */
    private int executePlace(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target, int maxAllowed) {
        if (maxAllowed <= 0) return 0;

        boolean allowCrystals = !"Якоря".equals(this.targetType.getSelected());
        boolean allowAnchors = !"Кристаллы".equals(this.targetType.getSelected());

        // 1. Установка энд-кристаллов
        if (allowCrystals) {
            int placed = tryPlaceCrystal(player, world, interactionManager, target);
            if (placed > 0) return placed;
        }

        // 2. Установка / зарядка якорей возрождения
        if (allowAnchors) {
            int placedAnchor = tryPlaceOrChargeAnchor(player, world, interactionManager, target);
            if (placedAnchor > 0) return placedAnchor;
        }

        return 0;
    }

    private int tryPlaceCrystal(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target) {
        // Проверяем наличие кристаллов
        Hand hand = getHandWithItem(player, Items.END_CRYSTAL);
        int crystalSlot = -1;
        if (hand == null) {
            if ("Без смены".equals(this.switchMode.getSelected())) {
                return 0;
            }
            crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
            if (crystalSlot == -1) {
                return 0;
            }
        }

        float maxPlaceDist = this.placeRange.getValue();
        float playerHp = player.getHealth() + player.getAbsorptionAmount();

        List<PlaceCandidate> candidates = new ArrayList<>();
        int r = (int) Math.ceil(maxPlaceDist);
        BlockPos playerPos = player.getBlockPos();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!canPlaceCrystalOn(pos, world)) continue;

                    Vec3d crystalPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    double distToPlayerSq = player.squaredDistanceTo(crystalPos);
                    if (distToPlayerSq > maxPlaceDist * maxPlaceDist) continue;

                    float enemyDamage = 0.0f;
                    if (target != null) {
                        double distToTargetSq = target.squaredDistanceTo(crystalPos);
                        if (distToTargetSq > 36.0 && this.requireTarget.getValue()) continue;
                        enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                        if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                            continue;
                        }
                    }

                    float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);

                    if (this.antiSuicide.getValue() && !player.isCreative() && !hasTotem(player)) {
                        if (playerHp - selfDamage < this.minHealth.getValue()) continue;
                    }
                    if (this.safeMode.getValue() && !player.isCreative()) {
                        if (selfDamage > this.maxSelfDamage.getValue()) continue;
                    }

                    boolean feetCovered = isFeetCovered(player, crystalPos, world);
                    candidates.add(new PlaceCandidate(pos, crystalPos, selfDamage, enemyDamage, feetCovered));
                }
            }
        }

        // Если обсидиана нет рядом с целью, но включен авто-обсидиан — ставим обсидиан
        if (candidates.isEmpty() && target != null && this.autoObsidian.getValue()) {
            if (tryPlaceObsidianNear(player, world, interactionManager, target)) {
                return 1;
            }
            return 0;
        }

        if (candidates.isEmpty()) {
            return 0;
        }

        // Сортируем кандидатов
        sortPlaceCandidates(candidates, player);

        PlaceCandidate best = candidates.get(0);

        // Переключаемся на кристалл если не в руке
        if (hand == null) {
            selectHotbarSlot(crystalSlot);
            hand = Hand.MAIN_HAND;
        }

        Vec3d hitVec = new Vec3d(best.blockPos().getX() + 0.5, best.blockPos().getY() + 1.0, best.blockPos().getZ() + 0.5);
        if (this.rotate.getValue()) {
            lookAt(hitVec);
        }

        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, best.blockPos(), false);
        interactionManager.interactBlock(player, hand, hitResult);
        if (this.swing.getValue()) {
            player.swingHand(hand);
        }

        return 1;
    }

    private boolean tryPlaceObsidianNear(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, LivingEntity target) {
        int obsSlot = findItemInHotbar(Items.OBSIDIAN);
        if (obsSlot == -1 && !player.getOffHandStack().isOf(Items.OBSIDIAN) && !player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            return false;
        }

        BlockPos targetPos = target.getBlockPos();
        BlockPos[] offsets = new BlockPos[]{
                targetPos.down(),
                targetPos.north(),
                targetPos.south(),
                targetPos.east(),
                targetPos.west(),
                targetPos.north().down(),
                targetPos.south().down(),
                targetPos.east().down(),
                targetPos.west().down()
        };

        for (BlockPos candidate : offsets) {
            double distSq = player.squaredDistanceTo(Vec3d.ofCenter(candidate));
            if (distSq > this.placeRange.getValue() * this.placeRange.getValue()) continue;

            BlockState state = world.getBlockState(candidate);
            if (!state.isAir() && !state.isReplaceable()) continue;

            // Ищем твёрдый соседний блок для клика
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = candidate.offset(dir);
                BlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.isAir() || neighborState.isReplaceable()) continue;

                Hand hand = getHandWithItem(player, Items.OBSIDIAN);
                if (hand == null) {
                    selectHotbarSlot(obsSlot);
                    hand = Hand.MAIN_HAND;
                }

                Direction side = dir.getOpposite();
                Vec3d hitVec = Vec3d.ofCenter(neighbor).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
                if (this.rotate.getValue()) {
                    lookAt(hitVec);
                }

                BlockHitResult hit = new BlockHitResult(hitVec, side, neighbor, false);
                interactionManager.interactBlock(player, hand, hit);
                if (this.swing.getValue()) {
                    player.swingHand(hand);
                }
                return true;
            }
        }
        return false;
    }

    private int tryPlaceOrChargeAnchor(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target) {
        if (target == null) return 0;
        BlockPos targetPos = target.getBlockPos();
        float maxR = this.placeRange.getValue();

        // 1. Проверяем незаряженные якоря поблизости для зарядки светокамнем
        int glowSlot = findItemInHotbar(Items.GLOWSTONE);
        Hand glowHand = getHandWithItem(player, Items.GLOWSTONE);
        if (glowHand != null || glowSlot != -1) {
            int r = (int) Math.ceil(maxR);
            BlockPos pPos = player.getBlockPos();
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = pPos.add(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) continue;
                        if (state.get(RespawnAnchorBlock.CHARGES) == 0) {
                            if (glowHand == null) {
                                selectHotbarSlot(glowSlot);
                                glowHand = Hand.MAIN_HAND;
                            }
                            Vec3d center = Vec3d.ofCenter(pos);
                            if (this.rotate.getValue()) lookAt(center);
                            BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
                            interactionManager.interactBlock(player, glowHand, hit);
                            if (this.swing.getValue()) player.swingHand(glowHand);
                            return 1;
                        }
                    }
                }
            }
        }

        // 2. Установка нового якоря возрождения
        int anchorSlot = findItemInHotbar(Items.RESPAWN_ANCHOR);
        Hand anchorHand = getHandWithItem(player, Items.RESPAWN_ANCHOR);
        if (anchorHand != null || anchorSlot != -1) {
            BlockPos underTarget = targetPos.down();
            if (world.getBlockState(underTarget).isAir() || world.getBlockState(underTarget).isReplaceable()) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = underTarget.offset(dir);
                    if (!world.getBlockState(neighbor).isAir()) {
                        if (anchorHand == null) {
                            selectHotbarSlot(anchorSlot);
                            anchorHand = Hand.MAIN_HAND;
                        }
                        Direction side = dir.getOpposite();
                        Vec3d hitVec = Vec3d.ofCenter(neighbor).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
                        if (this.rotate.getValue()) lookAt(hitVec);
                        BlockHitResult hit = new BlockHitResult(hitVec, side, neighbor, false);
                        interactionManager.interactBlock(player, anchorHand, hit);
                        if (this.swing.getValue()) player.swingHand(anchorHand);
                        return 1;
                    }
                }
            }
        }

        return 0;
    }

    private boolean canPlaceCrystalOn(BlockPos pos, ClientWorld world) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
            return false;
        }
        BlockPos up = pos.up();
        BlockState upState = world.getBlockState(up);
        if (!upState.isAir() && !upState.isReplaceable()) {
            return false;
        }

        // Проверяем хитбокс кристалла (2.0 вверх, 1.0 в стороны)
        Box box = new Box(
                up.getX(), up.getY(), up.getZ(),
                up.getX() + 1.0, up.getY() + 2.0, up.getZ() + 1.0
        );

        for (Entity e : world.getEntities()) {
            if (e.isRemoved() || !e.isAlive() || e.isSpectator()) continue;
            if (e instanceof EndCrystalEntity && this.attackedCrystals.contains(e.getId())) continue;
            if (e.getBoundingBox().intersects(box)) {
                return false;
            }
        }
        return true;
    }

    private void sortBreakCandidates(List<BreakCandidate> list, ClientPlayerEntity player) {
        String mode = this.priority.getSelected();
        if ("Ближайший".equals(mode)) {
            list.sort(Comparator.comparingDouble(c -> player.squaredDistanceTo(c.pos())));
        } else if ("Больше урона цели".equals(mode)) {
            list.sort((a, b) -> {
                int cmp = Float.compare(b.enemyDamage(), a.enemyDamage());
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        } else {
            // "Меньше урона себе"
            list.sort((a, b) -> {
                if (this.safeCover.getValue() && a.feetCovered() != b.feetCovered()) {
                    return a.feetCovered() ? -1 : 1;
                }
                float diffA = a.enemyDamage() - a.selfDamage();
                float diffB = b.enemyDamage() - b.selfDamage();
                int cmp = Float.compare(diffB, diffA);
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        }
    }

    private void sortPlaceCandidates(List<PlaceCandidate> list, ClientPlayerEntity player) {
        String mode = this.priority.getSelected();
        if ("Ближайший".equals(mode)) {
            list.sort(Comparator.comparingDouble(c -> player.squaredDistanceTo(c.crystalPos())));
        } else if ("Больше урона цели".equals(mode)) {
            list.sort((a, b) -> {
                int cmp = Float.compare(b.enemyDamage(), a.enemyDamage());
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        } else {
            // "Меньше урона себе"
            list.sort((a, b) -> {
                if (this.safeCover.getValue() && a.feetCovered() != b.feetCovered()) {
                    return a.feetCovered() ? -1 : 1;
                }
                float diffA = a.enemyDamage() - a.selfDamage();
                float diffB = b.enemyDamage() - b.selfDamage();
                int cmp = Float.compare(diffB, diffA);
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        }
    }

    private boolean isValidTarget(@Nullable LivingEntity entity, @NotNull ClientPlayerEntity player) {
        if (entity == null || entity == player || !entity.isAlive() || entity.isDead() || entity.isRemoved() || entity.isSpectator()) {
            return false;
        }

        if (entity instanceof PlayerEntity p) {
            if (!this.targetEntities.isSelected("Игроки")) return false;
            if (p.isCreative()) return false;
            if (this.ignoreFriends.getValue() && FriendUtils.isFriend((Entity) p)) return false;
            return true;
        }

        if (entity instanceof AnimalEntity || entity instanceof PassiveEntity) {
            return this.targetEntities.isSelected("Животные");
        }

        if (entity instanceof MobEntity) {
            return this.targetEntities.isSelected("Мобы");
        }

        return this.targetEntities.isSelected("Другие");
    }

    @Nullable
    private LivingEntity findTarget(ClientPlayerEntity player, ClientWorld world) {
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        double maxTargetDist = this.targetRange.getValue();
        double maxTargetDistSq = maxTargetDist * maxTargetDist;

        boolean allowPlayers = this.targetEntities.isSelected("Игроки");

        for (Entity e : world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (!isValidTarget(living, player)) continue;

            double distSq = player.squaredDistanceTo(living);
            if (distSq > maxTargetDistSq) continue;

            // Если разрешены игроки, отдаем им приоритет над мобами и животными
            if (allowPlayers && living instanceof PlayerEntity) {
                if (best == null || !(best instanceof PlayerEntity) || distSq < bestDistSq) {
                    best = living;
                    bestDistSq = distSq;
                    continue;
                }
            }

            // Если лучший уже игрок, мобы не могут его перебить
            if (allowPlayers && best instanceof PlayerEntity && !(living instanceof PlayerEntity)) {
                continue;
            }

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = living;
            }
        }

        return best;
    }

    private boolean hasTotem(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private void selectHotbarSlot(int slot) {
        ClientPlayerEntity player = this.mc.player;
        if (player == null || this.mc.interactionManager == null) return;
        if (slot < 0 || slot > 8) return;
        if (player.getInventory().getSelectedSlot() == slot) return;

        if (this.previousSlot == -1 && "Возврат".equals(this.switchMode.getSelected())) {
            this.previousSlot = player.getInventory().getSelectedSlot();
            this.restoreSlotTicks = 2;
        }

        player.getInventory().setSelectedSlot(slot);
        ((MultiPlayerGameModeAccessor) this.mc.interactionManager).kimiko$ensureHasSentCarriedItem();
    }

    private int findItemInHotbar(Item item) {
        if (this.mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findNonItemSlot(Item item) {
        if (this.mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (!this.mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private Hand getHandWithItem(ClientPlayerEntity player, Item item) {
        if (player.getOffHandStack().isOf(item)) {
            return Hand.OFF_HAND;
        }
        if (player.getMainHandStack().isOf(item)) {
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private boolean hasShield(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.SHIELD) || player.getOffHandStack().isOf(Items.SHIELD);
    }

    private void lookAt(Vec3d target) {
        if (this.mc.player == null) return;
        double dx = target.x - this.mc.player.getX();
        double dy = target.y - (this.mc.player.getY() + this.mc.player.getStandingEyeHeight());
        double dz = target.z - this.mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return;

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, dist)), -90.0, 90.0);

        this.mc.player.setYaw(yaw);
        this.mc.player.setPitch(pitch);
    }

    private boolean canSeePos(PlayerEntity player, Vec3d pos, ClientWorld world) {
        Vec3d eyePos = player.getEyePos();
        RaycastContext ctx = new RaycastContext(
                eyePos, pos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );
        return world.raycast(ctx).getType() == HitResult.Type.MISS;
    }

    private boolean isFeetCovered(PlayerEntity player, Vec3d source, ClientWorld world) {
        Vec3d feet = player.getEntityPos().add(0, 0.2, 0);
        RaycastContext ctx = new RaycastContext(
                feet, source,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );
        return world.raycast(ctx).getType() != HitResult.Type.MISS;
    }

    /**
     * Точный и безопасный расчёт взрывного урона
     */
    public static float calculateExplosionDamage(Vec3d explosionPos, float power, LivingEntity entity, ClientWorld world) {
        if (entity instanceof PlayerEntity player && player.isCreative()) {
            return 0.0f;
        }

        double maxDist = power * 2.0;
        // Приподнимаем центр взрыва на 0.5 блока, чтобы лучи не упирались в сам обсидиановый блок
        Vec3d blastCenter = new Vec3d(explosionPos.x, explosionPos.y + 0.5, explosionPos.z);
        double dist = Math.sqrt(entity.squaredDistanceTo(blastCenter)) / maxDist;
        if (dist > 1.0) {
            return 0.0f;
        }

        double exposure = getExposure(blastCenter, entity, world);
        // Запасная проверка прямой видимости, если граничные лучи зацепили геометрию
        if (exposure <= 0.0) {
            double dSq = entity.squaredDistanceTo(blastCenter);
            if (dSq <= 16.0) {
                Vec3d eye = entity.getEyePos();
                RaycastContext eyeCtx = new RaycastContext(eye, blastCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity);
                if (world.raycast(eyeCtx).getType() == HitResult.Type.MISS) {
                    exposure = 0.5;
                } else {
                    Vec3d center = entity.getEntityPos().add(0, entity.getHeight() * 0.5, 0);
                    RaycastContext centerCtx = new RaycastContext(center, blastCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity);
                    if (world.raycast(centerCtx).getType() == HitResult.Type.MISS) {
                        exposure = 0.5;
                    }
                }
            }
            if (exposure <= 0.0) {
                return 0.0f;
            }
        }

        double impact = (1.0 - dist) * exposure;
        float rawDamage = (float) ((impact * impact + impact) / 2.0 * 7.0 * maxDist + 1.0);

        float armor = (float) entity.getArmor();
        float toughness = 0.0f;
        if (entity.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS) != null) {
            toughness = (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        }

        float f = 2.0f + toughness / 4.0f;
        float reduction = MathHelper.clamp(armor - rawDamage / f, armor * 0.2f, 20.0f);
        float damage = rawDamage * (1.0f - reduction / 25.0f);

        if (entity.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = entity.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
            damage = damage * Math.max(0.0f, 1.0f - amp * 0.2f);
        }

        // Защита зачарований (Protection / Blast Protection) на броне
        if (armor >= 12.0f) {
            damage *= 0.4f;
        }

        return Math.max(0.0f, damage);
    }

    private static double getExposure(Vec3d source, Entity entity, ClientWorld world) {
        Box box = entity.getBoundingBox();
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        int hits = 0;
        int total = 0;

        // Сэмплируем точки строго ВНУТРИ хитбокса (3x3x3 grid)
        // Смещение 0.167, 0.5, 0.833 гарантирует отсутствие клиппинга в пол и стены ямы (1x1 hole)
        for (int ix = 0; ix < 3; ix++) {
            double fracX = (ix + 0.5) / 3.0;
            double x = MathHelper.lerp(fracX, minX, maxX);

            for (int iy = 0; iy < 3; iy++) {
                double fracY = (iy + 0.5) / 3.0;
                double y = MathHelper.lerp(fracY, minY + 0.05, maxY);

                for (int iz = 0; iz < 3; iz++) {
                    double fracZ = (iz + 0.5) / 3.0;
                    double z = MathHelper.lerp(fracZ, minZ, maxZ);

                    Vec3d target = new Vec3d(x, y, z);
                    RaycastContext context = new RaycastContext(
                            target, source,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            entity
                    );
                    if (world.raycast(context).getType() == HitResult.Type.MISS) {
                        hits++;
                    }
                    total++;
                }
            }
        }
        return total > 0 ? (double) hits / total : 0.0;
    }

    private record BreakCandidate(
            @Nullable EndCrystalEntity crystal,
            @Nullable BlockPos anchorPos,
            Vec3d pos,
            float selfDamage,
            float enemyDamage,
            boolean feetCovered
    ) {}

    private record PlaceCandidate(
            BlockPos blockPos,
            Vec3d crystalPos,
            float selfDamage,
            float enemyDamage,
            boolean feetCovered
    ) {}
}
