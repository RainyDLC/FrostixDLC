package rtx.kimiko.api.modules.impl.Utils;

import mixin.accessor.MultiPlayerGameModeAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
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
import rtx.kimiko.api.events.impl.network.PacketReceiveEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final BooleanSetting instantBreak = (BooleanSetting) register(
            new BooleanSetting("Быстрый взрыв", "Мгновенно взрывать кристалл при получении пакета спавна от сервера", true)
    );
    private final BooleanSetting autoObsidian = (BooleanSetting) register(
            new BooleanSetting("Авто-обсидиан", "Ставить платформу из обсидиана под кристалл, если её нет", true)
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
            new SliderSetting("Действий за тик", "Максимальное количество действий за один тик").range(1, 5).increment(1).setValue(3.0f)
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
            new SliderSetting("Макс. урон себе", "Максимально допустимый урон по себе от взрыва (в HP)").range(1.0f, 36.0f).increment(0.5f).setValue(18.0f)
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
    private final Map<Integer, Long> attackedCrystals = new HashMap<>();
    private final Map<BlockPos, Long> recentlyPlacedObsidian = new HashMap<>();
    private final Map<BlockPos, Long> attackedAnchors = new HashMap<>();

    public CrystalAura() {
        super("Crystal Aura", "Автоматически ставит и взрывает кристаллы/маяки возле цели с защитой от самоубийства и минимизацией урона себе.", Category.UTILS);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        this.delayTimer = 0;
        this.attackedCrystals.clear();
        this.recentlyPlacedObsidian.clear();
        this.attackedAnchors.clear();
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

        // Очищаем старые атакованные кристаллы, якоря и недавно установленный обсидиан
        long currentWorldTime = world.getTime();
        if (!this.attackedCrystals.isEmpty()) {
            this.attackedCrystals.entrySet().removeIf(entry ->
                    currentWorldTime - entry.getValue() > 3 || world.getEntityById(entry.getKey()) == null
            );
        }
        if (!this.recentlyPlacedObsidian.isEmpty()) {
            this.recentlyPlacedObsidian.entrySet().removeIf(entry ->
                    currentWorldTime - entry.getValue() > 10 || world.getBlockState(entry.getKey()).isOf(Blocks.OBSIDIAN)
            );
        }
        if (!this.attackedAnchors.isEmpty()) {
            this.attackedAnchors.entrySet().removeIf(entry ->
                    currentWorldTime - entry.getValue() > 4
            );
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
            // Возврат слота хотбара если нет цели
            if (this.restoreSlotTicks > 0) {
                this.restoreSlotTicks--;
                if (this.restoreSlotTicks == 0 && this.previousSlot != -1) {
                    selectHotbarSlot(this.previousSlot);
                    this.previousSlot = -1;
                }
            }
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
            this.restoreSlotTicks = 2; // Продлеваем активный слот кристаллов во время боя
        } else if (this.restoreSlotTicks > 0) {
            this.restoreSlotTicks--;
            if (this.restoreSlotTicks == 0 && this.previousSlot != -1) {
                selectHotbarSlot(this.previousSlot);
                this.previousSlot = -1;
            }
        }
    }

    /**
     * Мгновенный взрыв кристалла по пакету спавна сущности от сервера
     */
    @EventHandler
    public final void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (!this.isEnabled()) return;
        if (!this.autoBreak.getValue() || !this.instantBreak.getValue()) return;

        EntitySpawnS2CPacket spawn = event.getPacketAs(EntitySpawnS2CPacket.class);
        if (spawn == null) return;
        if (spawn.getEntityType() != EntityType.END_CRYSTAL) return;

        int entityId = spawn.getEntityId();
        Vec3d crystalPos = new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ());

        this.mc.execute(() -> {
            ClientPlayerEntity player = this.mc.player;
            ClientWorld world = this.mc.world;
            ClientPlayerInteractionManager interactionManager = this.mc.interactionManager;
            if (player == null || world == null || interactionManager == null || this.mc.currentScreen != null) return;

            Entity entity = world.getEntityById(entityId);
            if (!(entity instanceof EndCrystalEntity crystal)) return;
            if (crystal.isRemoved() || !crystal.isAlive() || this.attackedCrystals.containsKey(crystal.getId())) return;

            double distSq = player.squaredDistanceTo(crystalPos);
            float maxBreakDist = this.breakRange.getValue();
            if (distSq > maxBreakDist * maxBreakDist) return;

            boolean canSee = player.canSee(crystal);
            if (!canSee && distSq > this.wallRange.getValue() * this.wallRange.getValue()) return;

            LivingEntity target = findTarget(player, world);
            float enemyDamage = 0.0f;
            if (target != null) {
                enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                    return;
                }
            } else if (this.requireTarget.getValue()) {
                return;
            }

            float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);
            if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                return;
            }

            if (this.rotate.getValue()) {
                lookAt(crystalPos);
            }
            if (this.autoSneak.getValue()) {
                this.mc.options.sneakKey.setPressed(true);
                this.sneakResetTicks = 2;
            }
            if (this.autoShield.getValue() && hasShield(player)) {
                this.mc.options.useKey.setPressed(true);
                this.shieldResetTicks = 2;
            }

            player.resetTicksSince();
            this.attackedCrystals.put(crystal.getId(), world.getTime());
            interactionManager.attackEntity(player, crystal);
            crystal.discard();
            if (this.swing.getValue()) {
                player.swingHand(Hand.MAIN_HAND);
            }

            // Сразу после мгновенного взрыва ставим следующий кристалл!
            if (this.autoPlace.getValue()) {
                tryPlaceCrystal(player, world, interactionManager, target, 1);
            }
        });
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
                if (crystal.isRemoved() || !crystal.isAlive() || this.attackedCrystals.containsKey(crystal.getId())) continue;

                Vec3d crystalPos = crystal.getEntityPos();
                double distSq = player.squaredDistanceTo(crystalPos);
                if (distSq > maxBreakDist * maxBreakDist) continue;

                boolean canSee = player.canSee(crystal);
                if (!canSee && distSq > maxWallDist * maxWallDist) continue;

                // Если цель есть, кристалл должен наносить ей урон
                float enemyDamage = 0.0f;
                if (target != null) {
                    double distToTargetSq = target.squaredDistanceTo(crystalPos);
                    if (distToTargetSq > 144.0 && this.requireTarget.getValue()) continue;
                    enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                    if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                        continue;
                    }
                }

                // Расчёт урона себе
                float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);
                if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                    continue;
                }

                boolean feetCovered = isFeetCovered(player, crystalPos, world);
                candidates.add(new BreakCandidate(crystal, null, crystalPos, selfDamage, enemyDamage, feetCovered));
            }
        }

        // 2. Поиск заряженных якорей возрождения / маяков
        if (allowAnchors && !isNether(world)) {
            BlockPos playerPos = player.getBlockPos();
            int r = (int) Math.ceil(maxBreakDist);
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (this.attackedAnchors.containsKey(pos)) continue;

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
                            if (distToTargetSq > 144.0 && this.requireTarget.getValue()) continue;
                            enemyDamage = calculateExplosionDamage(anchorPos, 5.0f, target, world);
                            if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                                continue;
                            }
                        }

                        float selfDamage = calculateExplosionDamage(anchorPos, 5.0f, player, world);
                        if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                            continue;
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
                this.attackedCrystals.put(candidate.crystal().getId(), world.getTime());
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
                Direction hitSide = getInteractableSide(candidate.anchorPos(), world);
                Vec3d hitPos = candidate.pos().add(hitSide.getOffsetX() * 0.5, hitSide.getOffsetY() * 0.5, hitSide.getOffsetZ() * 0.5);
                BlockHitResult hit = new BlockHitResult(hitPos, hitSide, candidate.anchorPos(), false);
                interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                world.setBlockState(candidate.anchorPos(), Blocks.AIR.getDefaultState());
                this.attackedAnchors.put(candidate.anchorPos(), world.getTime());
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

        boolean hasExistingAnchor = allowAnchors && hasNearbyAnchor(player, world, this.placeRange.getValue());

        boolean holdingAnchorOrGlow = player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)
                || player.getOffHandStack().isOf(Items.RESPAWN_ANCHOR)
                || player.getMainHandStack().isOf(Items.GLOWSTONE)
                || player.getOffHandStack().isOf(Items.GLOWSTONE);

        boolean preferAnchors = "Якоря".equals(this.targetType.getSelected()) || hasExistingAnchor || (allowAnchors && holdingAnchorOrGlow);

        if (preferAnchors && allowAnchors) {
            int placedAnchor = tryPlaceOrChargeAnchor(player, world, interactionManager, target, maxAllowed);
            if (placedAnchor > 0) return placedAnchor;
            if (allowCrystals) {
                int placedCrystal = tryPlaceCrystal(player, world, interactionManager, target, maxAllowed);
                if (placedCrystal > 0) return placedCrystal;
            }
        } else {
            // Если рядом УЖЕ есть якорь (например, поставлен вручную) — заряжаем и взрываем его в первую очередь!
            if (hasExistingAnchor) {
                int charged = tryPlaceOrChargeAnchor(player, world, interactionManager, target, maxAllowed);
                if (charged > 0) return charged;
            }
            if (allowCrystals) {
                int placed = tryPlaceCrystal(player, world, interactionManager, target, maxAllowed);
                if (placed > 0) return placed;
            }
            if (allowAnchors) {
                int placedAnchor = tryPlaceOrChargeAnchor(player, world, interactionManager, target, maxAllowed);
                if (placedAnchor > 0) return placedAnchor;
            }
        }

        return 0;
    }

    private int tryPlaceCrystal(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target, int maxAllowed) {
        // Проверяем наличие кристаллов
        Hand crystalHand = getHandWithItem(player, Items.END_CRYSTAL);
        int crystalSlot = -1;
        if (crystalHand == null) {
            if (!"Без смены".equals(this.switchMode.getSelected())) {
                crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
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
                        if (distToTargetSq > 144.0 && this.requireTarget.getValue()) continue;
                        enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                        if (!player.isCreative() && this.requireTarget.getValue() && enemyDamage < this.minEnemyDamage.getValue()) {
                            continue;
                        }
                    }

                    float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);
                    if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                        continue;
                    }

                    boolean feetCovered = isFeetCovered(player, crystalPos, world);
                    candidates.add(new PlaceCandidate(pos, crystalPos, selfDamage, enemyDamage, feetCovered));
                }
            }
        }

        // Если есть готовая позиция на существующем/недавно установленном обсидиане — ставим кристалл!
        if (!candidates.isEmpty()) {
            if (crystalHand == null && crystalSlot == -1) {
                return 0; // Нет кристаллов
            }

            sortPlaceCandidates(candidates, player);
            PlaceCandidate best = candidates.get(0);

            if (crystalHand == null) {
                selectHotbarSlot(crystalSlot);
                crystalHand = Hand.MAIN_HAND;
            }

            Vec3d hitVec = new Vec3d(best.blockPos().getX() + 0.5, best.blockPos().getY() + 1.0, best.blockPos().getZ() + 0.5);
            if (this.rotate.getValue()) {
                lookAt(hitVec);
            }

            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, best.blockPos(), false);
            interactionManager.interactBlock(player, crystalHand, hitResult);
            if (this.swing.getValue()) {
                player.swingHand(crystalHand);
            }
            return 1;
        }

        // Если готового обсидиана нет рядом, но включен авто-обсидиан — ставим платформу под кристалл и сразу кристалл!
        if (target != null && this.autoObsidian.getValue()) {
            return tryPlaceObsidianAndCrystal(player, world, interactionManager, target, crystalHand, crystalSlot, maxAllowed);
        }

        return 0;
    }

    /**
     * Находит лучшую позицию для платформы из обсидиана (НЕ трапит, а создает базу под кристалл у ног цели)
     */
    @Nullable
    private ObsidianCandidate findBestObsidianPlacement(ClientPlayerEntity player, ClientWorld world, LivingEntity target) {
        float maxPlaceDist = this.placeRange.getValue();
        float playerHp = player.getHealth() + player.getAbsorptionAmount();
        BlockPos targetPos = target.getBlockPos();
        Vec3d playerEye = player.getEyePos();

        List<ObsidianCandidate> candidates = new ArrayList<>();

        // Сканируем позиции платформы вокруг цели (горизонтальный радиус 2 блока)
        // dy: -1 (на уровне пола/под ногами цели) или 0 (если цель на уступе/неровной поверхности)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue; // Не ставим в блок, где стоит сама цель

                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos obsPos = targetPos.add(dx, dy, dz);

                    Vec3d obsCenter = Vec3d.ofCenter(obsPos);
                    if (player.squaredDistanceTo(obsCenter) > maxPlaceDist * maxPlaceDist) continue;

                    BlockState obsState = world.getBlockState(obsPos);
                    if (!obsState.isAir() && !obsState.isReplaceable()) continue;

                    // Не ставим блок в хитбокс цели или игрока (с запасом для ближнего боя)
                    Box obsBox = new Box(obsPos).contract(0.12);
                    if (target.getBoundingBox().intersects(obsBox) || player.getBoundingBox().intersects(obsBox)) {
                        continue;
                    }

                    // Проверяем возможность установки кристалла НА этот обсидиан (obsPos.up())
                    BlockPos crystalBlockPos = obsPos.up();
                    BlockState upState = world.getBlockState(crystalBlockPos);
                    if (!upState.isAir() && !upState.isReplaceable()) continue;

                    Box crystalBox = new Box(crystalBlockPos).contract(0.08);

                    // Проверяем коллизии кристалла с сущностями
                    boolean entityBlocked = false;
                    for (Entity e : world.getEntities()) {
                        if (e.isRemoved() || !e.isAlive() || e.isSpectator()) continue;
                        if (e instanceof EndCrystalEntity && this.attackedCrystals.containsKey(e.getId())) continue;
                        if (e.getBoundingBox().intersects(crystalBox)) {
                            entityBlocked = true;
                            break;
                        }
                    }
                    if (entityBlocked) continue;

                    // Ищем соседний твёрдый блок для клика установки
                    BlockPos validNeighbor = null;
                    Direction validSide = null;
                    Vec3d validHitVec = null;

                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = obsPos.offset(dir);
                        BlockState neighborState = world.getBlockState(neighbor);
                        if (neighborState.isAir() || neighborState.isReplaceable()) continue;

                        Direction side = dir.getOpposite();
                        Vec3d hit = Vec3d.ofCenter(neighbor).add(
                                side.getOffsetX() * 0.5,
                                side.getOffsetY() * 0.5,
                                side.getOffsetZ() * 0.5
                        );
                        if (playerEye.squaredDistanceTo(hit) <= maxPlaceDist * maxPlaceDist) {
                            validNeighbor = neighbor;
                            validSide = side;
                            validHitVec = hit;
                            break;
                        }
                    }

                    if (validNeighbor == null) continue;

                    // Расчёт урона от кристалла на этой позиции
                    Vec3d crystalPos = new Vec3d(obsPos.getX() + 0.5, obsPos.getY() + 1.0, obsPos.getZ() + 0.5);
                    float enemyDamage = calculateExplosionDamage(crystalPos, 6.0f, target, world);
                    if (!player.isCreative() && enemyDamage < this.minEnemyDamage.getValue()) {
                        continue;
                    }

                    float selfDamage = calculateExplosionDamage(crystalPos, 6.0f, player, world);
                    if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                        continue;
                    }

                    boolean feetCovered = isFeetCovered(player, crystalPos, world);
                    candidates.add(new ObsidianCandidate(obsPos, validNeighbor, validSide, validHitVec, crystalPos, selfDamage, enemyDamage, feetCovered));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Сортировка кандидатов по приоритету
        String mode = this.priority.getSelected();
        if ("Ближайший".equals(mode)) {
            candidates.sort(Comparator.comparingDouble(c -> target.squaredDistanceTo(c.crystalPos())));
        } else if ("Больше урона цели".equals(mode)) {
            candidates.sort((a, b) -> {
                int cmp = Float.compare(b.enemyDamage(), a.enemyDamage());
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        } else {
            // "Меньше урона себе"
            candidates.sort((a, b) -> {
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

        return candidates.get(0);
    }

    /**
     * Ставит обсидиан как платформу под кристалл и (если позволяет лимит) сразу ставит кристалл сверху
     */
    private int tryPlaceObsidianAndCrystal(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, LivingEntity target, @Nullable Hand crystalHand, int crystalSlot, int maxAllowed) {
        int obsSlot = findItemInHotbar(Items.OBSIDIAN);
        Hand obsHand = getHandWithItem(player, Items.OBSIDIAN);
        if (obsHand == null && (obsSlot == -1 || "Без смены".equals(this.switchMode.getSelected()))) {
            return 0; // Нет обсидиана
        }

        // Если кристаллов вообще нет, не ставим обсидиан впустую
        if (crystalHand == null && (crystalSlot == -1 || "Без смены".equals(this.switchMode.getSelected()))) {
            return 0;
        }

        ObsidianCandidate bestObs = findBestObsidianPlacement(player, world, target);
        if (bestObs == null) {
            return 0;
        }

        // 1. Ставим обсидиановую платформу
        if (obsHand == null) {
            selectHotbarSlot(obsSlot);
            obsHand = Hand.MAIN_HAND;
        }

        if (this.rotate.getValue()) {
            lookAt(bestObs.hitVec());
        }

        BlockHitResult obsHit = new BlockHitResult(bestObs.hitVec(), bestObs.side(), bestObs.neighbor(), false);
        interactionManager.interactBlock(player, obsHand, obsHit);
        if (this.swing.getValue()) {
            player.swingHand(obsHand);
        }

        this.recentlyPlacedObsidian.put(bestObs.obsPos(), world.getTime());

        // 2. Сразу же ставим кристалл на этот новый обсидиан!
        if (maxAllowed > 1) {
            Hand cHand = crystalHand;
            if (cHand == null) {
                int cSlot = findItemInHotbar(Items.END_CRYSTAL);
                if (cSlot != -1) {
                    selectHotbarSlot(cSlot);
                    cHand = Hand.MAIN_HAND;
                }
            }

            if (cHand != null) {
                Vec3d crystalHitVec = new Vec3d(bestObs.obsPos().getX() + 0.5, bestObs.obsPos().getY() + 1.0, bestObs.obsPos().getZ() + 0.5);
                if (this.rotate.getValue()) {
                    lookAt(crystalHitVec);
                }

                BlockHitResult crystalHit = new BlockHitResult(crystalHitVec, Direction.UP, bestObs.obsPos(), false);
                interactionManager.interactBlock(player, cHand, crystalHit);
                if (this.swing.getValue()) {
                    player.swingHand(cHand);
                }
                return 2; // Установлен обсидиан + кристалл!
            }
        }

        return 1;
    }

    /**
     * Быстрая установка, зарядка и детонация якорей возрождения возле цели
     */
    private int tryPlaceOrChargeAnchor(ClientPlayerEntity player, ClientWorld world, ClientPlayerInteractionManager interactionManager, @Nullable LivingEntity target, int maxAllowed) {
        if (target == null) return 0;
        if (isNether(world)) return 0; // Якоря не взрываются в Незере

        float maxR = this.placeRange.getValue();
        float playerHp = player.getHealth() + player.getAbsorptionAmount();

        int glowSlot = findItemInHotbar(Items.GLOWSTONE);
        Hand glowHand = getHandWithItem(player, Items.GLOWSTONE);
        boolean canGlow = glowHand != null || (glowSlot != -1 && !"Без смены".equals(this.switchMode.getSelected()));

        // 1. Быстрая зарядка и детонация уже установленного якоря поблизости
        if (canGlow) {
            int r = (int) Math.ceil(maxR);
            BlockPos pPos = player.getBlockPos();
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = pPos.add(x, y, z);
                        if (this.attackedAnchors.containsKey(pos)) continue;

                        BlockState state = world.getBlockState(pos);
                        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) continue;

                        int currentCharges = state.get(RespawnAnchorBlock.CHARGES);
                        Vec3d center = Vec3d.ofCenter(pos);
                        if (player.squaredDistanceTo(center) > maxR * maxR) continue;

                        float enemyDamage = calculateExplosionDamage(center, 5.0f, target, world);
                        if (!player.isCreative() && enemyDamage < this.minEnemyDamage.getValue()) continue;

                        float selfDamage = calculateExplosionDamage(center, 5.0f, player, world);
                        if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) continue;

                        // Если не заряжен — заряжаем светокамнем
                        if (currentCharges == 0) {
                            Hand gHand = glowHand;
                            if (gHand == null) {
                                selectHotbarSlot(glowSlot);
                                gHand = Hand.MAIN_HAND;
                            }

                            if (this.rotate.getValue()) lookAt(center);
                            Direction hitSide = getInteractableSide(pos, world);
                            Vec3d hitPos = center.add(hitSide.getOffsetX() * 0.5, hitSide.getOffsetY() * 0.5, hitSide.getOffsetZ() * 0.5);
                            BlockHitResult hit = new BlockHitResult(hitPos, hitSide, pos, false);
                            interactionManager.interactBlock(player, gHand, hit);
                            if (this.swing.getValue()) player.swingHand(gHand);

                            // Моментально обновляем состояние блока на клиенте, чтобы следующий клик не возвращал PASS
                            world.setBlockState(pos, state.with(RespawnAnchorBlock.CHARGES, 1));
                        }

                        // Сразу подрываем тем же тиком!
                        int nonGlow = findNonItemSlot(Items.GLOWSTONE);
                        if (nonGlow != -1) {
                            selectHotbarSlot(nonGlow);
                        }
                        if (this.rotate.getValue()) lookAt(center);
                        Direction hitSide = getInteractableSide(pos, world);
                        Vec3d hitPos = center.add(hitSide.getOffsetX() * 0.5, hitSide.getOffsetY() * 0.5, hitSide.getOffsetZ() * 0.5);
                        BlockHitResult hit = new BlockHitResult(hitPos, hitSide, pos, false);
                        interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                        if (this.swing.getValue()) player.swingHand(Hand.MAIN_HAND);

                        // Помечаем взорванным локально
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        this.attackedAnchors.put(pos, world.getTime());
                        return 2;
                    }
                }
            }
        }

        // 2. Установка НОВОГО якоря возрождения
        int anchorSlot = findItemInHotbar(Items.RESPAWN_ANCHOR);
        Hand anchorHand = getHandWithItem(player, Items.RESPAWN_ANCHOR);
        if (anchorHand == null && (anchorSlot == -1 || "Без смены".equals(this.switchMode.getSelected()))) {
            return 0;
        }

        // Без светокамня ставить якорь нет смысла
        if (!canGlow) {
            return 0;
        }

        AnchorCandidate bestAnchor = findBestAnchorPlacement(player, world, target);
        if (bestAnchor == null) {
            return 0;
        }

        // 2.1 Ставим якорь
        Hand aHand = anchorHand;
        if (aHand == null) {
            selectHotbarSlot(anchorSlot);
            aHand = Hand.MAIN_HAND;
        }

        if (this.rotate.getValue()) lookAt(bestAnchor.hitVec());
        BlockHitResult placeHit = new BlockHitResult(bestAnchor.hitVec(), bestAnchor.side(), bestAnchor.neighbor(), false);
        interactionManager.interactBlock(player, aHand, placeHit);
        if (this.swing.getValue()) player.swingHand(aHand);

        // 2.2 Сразу заряжаем светокамнем в том же тике!
        Hand gHand = glowHand;
        if (gHand == null) {
            int gSlot = findItemInHotbar(Items.GLOWSTONE);
            if (gSlot != -1) {
                selectHotbarSlot(gSlot);
                gHand = Hand.MAIN_HAND;
            }
        }

        if (gHand != null) {
            if (this.rotate.getValue()) lookAt(bestAnchor.center());
            Direction chargeSide = getInteractableSide(bestAnchor.pos(), world);
            Vec3d chargeHitPos = bestAnchor.center().add(chargeSide.getOffsetX() * 0.5, chargeSide.getOffsetY() * 0.5, chargeSide.getOffsetZ() * 0.5);
            BlockHitResult chargeHit = new BlockHitResult(chargeHitPos, chargeSide, bestAnchor.pos(), false);
            interactionManager.interactBlock(player, gHand, chargeHit);
            if (this.swing.getValue()) player.swingHand(gHand);

            // Мгновенно ставим CHARGES = 1 в клиентском мире
            world.setBlockState(bestAnchor.pos(), Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, 1));

            // 2.3 Сразу взрываем в том же тике!
            int nonGlow = findNonItemSlot(Items.GLOWSTONE);
            if (nonGlow != -1) {
                selectHotbarSlot(nonGlow);
            }
            interactionManager.interactBlock(player, Hand.MAIN_HAND, chargeHit);
            if (this.swing.getValue()) player.swingHand(Hand.MAIN_HAND);

            // Локально очищаем и помечаем как взорванный
            world.setBlockState(bestAnchor.pos(), Blocks.AIR.getDefaultState());
            this.attackedAnchors.put(bestAnchor.pos(), world.getTime());
            return 3;
        }

        return 1;
    }

    /**
     * Находит лучшую позицию для установки якоря возрождения рядом с целью (маневренно, высокий урон, без суицида)
     */
    @Nullable
    private AnchorCandidate findBestAnchorPlacement(ClientPlayerEntity player, ClientWorld world, LivingEntity target) {
        float maxPlaceDist = this.placeRange.getValue();
        float maxWallDist = this.wallRange.getValue();
        float playerHp = player.getHealth() + player.getAbsorptionAmount();
        BlockPos targetPos = target.getBlockPos();
        Vec3d playerEye = player.getEyePos();

        List<AnchorCandidate> candidates = new ArrayList<>();

        // Динамический радиус вокруг цели для маневренности:
        // dx, dz: [-2, 2], dy: [-2, 1]
        // Учитываем прыжки цели, бег, ступеньки и уступы
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    // Не ставим прямо в тело цели на уровне туловища (dy == 0)
                    if (dx == 0 && dz == 0 && dy == 0) continue;

                    BlockPos pos = targetPos.add(dx, dy, dz);
                    Vec3d center = Vec3d.ofCenter(pos);

                    double distSq = player.squaredDistanceTo(center);
                    if (distSq > maxPlaceDist * maxPlaceDist) continue;

                    boolean canSee = canSeePos(player, center, world);
                    if (!canSee && distSq > maxWallDist * maxWallDist) continue;

                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && !state.isReplaceable()) continue;

                    // Не ставим внутрь хитбоксов игрока или цели (с отступом для ближнего боя)
                    Box box = new Box(pos).contract(0.12);
                    if (target.getBoundingBox().intersects(box) || player.getBoundingBox().intersects(box)) {
                        continue;
                    }

                    // Проверяем коллизии с другими сущностями
                    boolean entityBlocked = false;
                    for (Entity e : world.getEntities()) {
                        if (e.isRemoved() || !e.isAlive() || e.isSpectator()) continue;
                        if (e.getBoundingBox().intersects(box)) {
                            entityBlocked = true;
                            break;
                        }
                    }
                    if (entityBlocked) continue;

                    // Ищем соседний твёрдый блок для привязки установки
                    BlockPos validNeighbor = null;
                    Direction validSide = null;
                    Vec3d validHitVec = null;

                    // Сначала проверяем блок снизу (DOWN), затем стороны
                    Direction[] checkDirs = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
                    for (Direction dir : checkDirs) {
                        BlockPos neighbor = pos.offset(dir);
                        BlockState neighborState = world.getBlockState(neighbor);
                        if (neighborState.isAir() || neighborState.isReplaceable()) continue;

                        Direction side = dir.getOpposite();
                        Vec3d hit = Vec3d.ofCenter(neighbor).add(
                                side.getOffsetX() * 0.5,
                                side.getOffsetY() * 0.5,
                                side.getOffsetZ() * 0.5
                        );
                        if (playerEye.squaredDistanceTo(hit) <= maxPlaceDist * maxPlaceDist) {
                            validNeighbor = neighbor;
                            validSide = side;
                            validHitVec = hit;
                            break;
                        }
                    }

                    if (validNeighbor == null) continue;

                    // Расчёт урона от взрыва якоря (power 5.0)
                    float enemyDamage = calculateExplosionDamage(center, 5.0f, target, world);
                    if (!player.isCreative() && enemyDamage < this.minEnemyDamage.getValue()) {
                        continue;
                    }

                    float selfDamage = calculateExplosionDamage(center, 5.0f, player, world);
                    if (!isSafeExplosion(player, target, selfDamage, enemyDamage)) {
                        continue;
                    }

                    boolean feetCovered = isFeetCovered(player, center, world);
                    candidates.add(new AnchorCandidate(pos, validNeighbor, validSide, validHitVec, center, selfDamage, enemyDamage, feetCovered));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Сортировка по выбранному приоритету
        String mode = this.priority.getSelected();
        if ("Ближайший".equals(mode)) {
            candidates.sort(Comparator.comparingDouble(c -> target.squaredDistanceTo(c.center())));
        } else if ("Больше урона цели".equals(mode)) {
            candidates.sort((a, b) -> {
                int cmp = Float.compare(b.enemyDamage(), a.enemyDamage());
                if (cmp != 0) return cmp;
                return Float.compare(a.selfDamage(), b.selfDamage());
            });
        } else {
            // "Меньше урона себе"
            candidates.sort((a, b) -> {
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

        return candidates.get(0);
    }

    private boolean canPlaceCrystalOn(BlockPos pos, ClientWorld world) {
        BlockState state = world.getBlockState(pos);
        boolean isObsidian = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK) || this.recentlyPlacedObsidian.containsKey(pos);
        if (!isObsidian) {
            return false;
        }
        BlockPos up = pos.up();
        BlockState upState = world.getBlockState(up);
        if (!upState.isAir() && !upState.isReplaceable()) {
            return false;
        }

        // Хитбокс кристалла (1 блок над платформой с запасом от краев)
        Box box = new Box(up).contract(0.08);

        for (Entity e : world.getEntities()) {
            if (e.isRemoved() || !e.isAlive() || e.isSpectator()) continue;
            if (e instanceof EndCrystalEntity && this.attackedCrystals.containsKey(e.getId())) continue;
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

    private static boolean isNether(ClientWorld world) {
        return world.getRegistryKey().getValue().toString().contains("nether") || world.getDimension().coordinateScale() > 1.0;
    }

    private static Direction getInteractableSide(BlockPos pos, ClientWorld world) {
        BlockState upState = world.getBlockState(pos.up());
        if (upState.isAir() || upState.isReplaceable()) {
            return Direction.UP;
        }
        for (Direction dir : Direction.values()) {
            BlockState sideState = world.getBlockState(pos.offset(dir));
            if (sideState.isAir() || sideState.isReplaceable()) {
                return dir;
            }
        }
        return Direction.UP;
    }

    /**
     * Интеллектуальная проверка безопасности взрыва:
     * - При наличии тотема в руках — никогда не блокирует (игрок защищен от смерти)
     * - В ближнем бою не тупит, если урон цели выгоден или летален
     * - Блокирует суицид только если урон убьет игрока без тотема
     */
    private boolean isSafeExplosion(ClientPlayerEntity player, @Nullable LivingEntity target, float selfDamage, float enemyDamage) {
        if (player.isCreative()) return true;

        // 1. Тотем дает бессмертие: разрешаем взрывы в упор
        if (hasTotem(player)) {
            return true;
        }

        float playerHp = player.getHealth() + player.getAbsorptionAmount();

        // 2. Анти-суицид: предотвращаем смерть без тотема
        if (this.antiSuicide.getValue()) {
            float minHp = this.minHealth.getValue();
            if (playerHp - selfDamage < minHp) {
                // Исключение: если взрыв гарантированно убивает противника, а мы остаемся живы (> 1.0 HP) — добиваем!
                if (target != null && (target.getHealth() + target.getAbsorptionAmount()) <= enemyDamage && (playerHp - selfDamage) > 1.0f) {
                    return true;
                }
                return false;
            }
        }

        // 3. Сейф-режим: запрещаем невыгодный высокий урон по себе
        if (this.safeMode.getValue()) {
            if (selfDamage > this.maxSelfDamage.getValue()) {
                // Если противнику наносится БОЛЬШЕ урона, чем себе, или урон летален для врага — не тупим в упор!
                if (target != null && (enemyDamage > selfDamage || (target.getHealth() + target.getAbsorptionAmount()) <= enemyDamage)) {
                    return true;
                }
                return false;
            }
        }

        return true;
    }

    private boolean hasNearbyAnchor(ClientPlayerEntity player, ClientWorld world, float maxDist) {
        int r = (int) Math.ceil(maxDist);
        BlockPos pPos = player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pPos.add(x, y, z);
                    if (this.attackedAnchors.containsKey(pos)) continue;
                    if (world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    private record ObsidianCandidate(
            BlockPos obsPos,
            BlockPos neighbor,
            Direction side,
            Vec3d hitVec,
            Vec3d crystalPos,
            float selfDamage,
            float enemyDamage,
            boolean feetCovered
    ) {}

    private record AnchorCandidate(
            BlockPos pos,
            BlockPos neighbor,
            Direction side,
            Vec3d hitVec,
            Vec3d center,
            float selfDamage,
            float enemyDamage,
            boolean feetCovered
    ) {}
}
