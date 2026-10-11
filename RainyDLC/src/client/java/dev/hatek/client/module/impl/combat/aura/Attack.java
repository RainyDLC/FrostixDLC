package dev.hatek.client.module.impl.combat.aura;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
import dev.hatek.client.module.impl.combat.aura.util.AttackPace;
import dev.hatek.client.module.impl.combat.aura.util.AuraProfile;
import dev.hatek.client.module.impl.combat.aura.util.AuraRandom;
import dev.hatek.client.module.impl.combat.aura.util.PlayerStats;
import dev.hatek.client.module.impl.combat.aura.util.AuraTimer;
import dev.hatek.client.module.impl.combat.aura.util.AuraUtil;
import dev.hatek.client.module.impl.combat.aura.util.LagGuard;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import dev.hatek.mixin.accessor.LivingEntityAccessor;
import dev.hatek.mixin.accessor.LocalPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class Attack {
    private static final AuraTimer COOLDOWN_TIMER = new AuraTimer();

    public static final double HIT_TOLERANCE = 0.05;

    private static final float CHARGE = 1.0F;

    public static long hitCounterCPSBypass;

    private static float critFallHeight = nextCritFallHeight();

    private static final float CHARGE_SLACK = 0.04F;

    private static final float CRIT_CHARGE = 0.91F;

    /** Порог сервера: Player.attack() даёт крит/полный урон только при scale > 0.9. */
    private static final float SERVER_CHARGE = 0.9F;

    /**
     * Запас сверх серверного порога. Тикер зарядки сервер считает сам, по своим тикам:
     * джиттер пакета на ±1 тик съедает 1/(тиков кулдауна) зарядки. При 0.91 у меча
     * (0.08/тик) сервер видел 0.83 — крит и часть урона пропадали.
     */
    private static final float SERVER_CHARGE_SLACK = 0.01F;

    private static final float MAX_CHARGE_FLOOR = 0.995F;

    private static float chargeTarget = CHARGE;

    private static final float AURA_GAP_MIN = 205.0F;
    private static final float AURA_GAP_SPREAD = 60.0F;

    private static long auraGap = (long) AURA_GAP_MIN;

    private static final AttackPace PACE = new AttackPace();

    private static boolean auraAttacking;

    private static long extraCooldown;

    private static long sprintLead = nextSprintLead();

    private static boolean missDetected;
    private static int counterTo0PostMissHits;

    private static boolean attackedLastTick;

    private static final int FAR_PAST = Integer.MIN_VALUE / 2;

    private static int lastAirTick = FAR_PAST;

    private static final int GROUND_PATIENCE_TICKS = 14;

    private Attack() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void hitCounterCPSBypassNext() {
        hitCounterCPSBypass++;
    }

    public static void hitCounterCPSBypassReset() {
        hitCounterCPSBypass = 0L;
    }

    public static boolean cpsBypassTrigger() {
        return hitCounterCPSBypass % 7L == 3L;
    }

    public static Player getSelf() {
        return mc().player;
    }

    public static Level getWorld() {
        return mc().level;
    }

    public static int getAxeSlot() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    public static Runnable[] hitShieldBreakTaskForUse(LivingEntity livingIn, boolean enabled) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (!enabled || mc.player == null) {
            return prePost;
        }
        if (livingIn instanceof Player player) {
            if (!player.isBlocking()) {
                return prePost;
            }
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            Item mainItem = main.isEmpty() ? null : main.getItem();
            Item offItem = off.isEmpty() ? null : off.getItem();

            if (mainItem == Items.SHIELD || offItem == Items.SHIELD) {
                int handSlot = mc.player.getInventory().getSelectedSlot();
                int slot = getAxeSlot();

                if (slot != -1 && slot != handSlot) {
                    prePost[0] = () -> {
                        if (mc.getConnection() != null) {
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                        }
                    };
                    prePost[1] = () -> {
                        if (mc.getConnection() != null) {
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(handSlot));
                        }
                    };
                }
            }
        }
        return prePost;
    }

    public static Runnable[] resetShieldSilentTaskForUse(boolean enabled) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (!enabled || mc.player == null) {
            return prePost;
        }
        if (mc.player.isBlocking()) {
            InteractionHand active = mc.player.getUsedItemHand();
            if (active == null) {
                return prePost;
            }
            prePost[0] = () -> mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            prePost[1] = () -> mc.getConnection().send(new ServerboundUseItemPacket(
                    active, 0, mc.player.getYRot(), mc.player.getXRot()));
        }
        return prePost;
    }

    /**
     * Pre/post-hit sprint tasks.
     *
     * <p>Удар выполняется в TAIL тика, после sendPosition(), поэтому
     * {@code wasSprinting} — это ровно то, что сервер считает сейчас.
     * STOP/START шлются через vanilla {@code sendIsSprintingIfNeeded()}: пакет уходит
     * только при реальной смене состояния, а {@code wasSprinting} синхронизируется.
     * Раньше в легит-режиме STOP слался сырым пакетом при каждой попытке крита
     * (даже без спринта), а vanilla следом слала второй STOP — дубли sprint-пакетов
     * античит флагает и отменяет удары.</p>
     *
     * <p>Packet: спринт снимается перед ударом и возвращается после.
     * Legit: только STOP перед критом, возврат делает hold/releaseSprint.</p>
     */
    public static Runnable[] skipSilentSprintingTaskForUse(boolean packetMode, boolean critAttempt) {
        Runnable[] prePost = {() -> {
        }, () -> {
        }};
        Minecraft mc = mc();
        if (mc.player == null || mc.getConnection() == null) {
            return prePost;
        }
        if (mc.player.isEyeInFluid(FluidTags.WATER)) {
            return prePost;
        }
        LocalPlayerAccessor sync = (LocalPlayerAccessor) mc.player;
        boolean serverSprinting = sync.hatek$wasSprinting();
        if (!serverSprinting || !(packetMode || critAttempt)) {
            return prePost;
        }
        boolean restore = packetMode;

        prePost[0] = () -> {
            mc.player.setSprinting(false);
            sync.hatek$sendIsSprintingIfNeeded();
        };
        prePost[1] = () -> {
            if (!restore || mc.player == null) {
                return;
            }
            mc.player.setSprinting(true);
            sync.hatek$sendIsSprintingIfNeeded();
        };
        return prePost;
    }

    public static double getYCapacityOnPlayerPos(int rangeY) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) {
            return 1.0;
        }
        Vec3 eyePos = mc.player.getEyePosition();
        double minDst = rangeY * 2.0;
        double maxY = 320.0;
        double minY = -64.0;
        float selfWD2 = mc.player.getBbWidth() / 2.0F - 0.01F;

        for (Vec3 vec : Arrays.asList(
                eyePos.add(-selfWD2, 0.0, -selfWD2),
                eyePos.add(selfWD2, 0.0, selfWD2),
                eyePos.add(selfWD2, 0.0, -selfWD2),
                eyePos.add(-selfWD2, 0.0, selfWD2))) {
            HitResult first = mc.level.clip(new ClipContext(vec, vec.add(0.0, -rangeY, 0.0),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, mc.player));
            HitResult second = mc.level.clip(new ClipContext(vec, vec.add(0.0, rangeY, 0.0),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, mc.player));

            if (maxY > second.getLocation().y) {
                maxY = second.getLocation().y;
            }
            if (minY < first.getLocation().y) {
                minY = first.getLocation().y;
            }
            double dst = maxY - minY;
            if (minDst > dst) {
                minDst = dst;
            }
        }
        return minDst - mc.player.getBbHeight();
    }

    public static boolean isBestMomentToHit(boolean fallCheck) {
        Minecraft mc = mc();
        if (!fallCheck || mc.player == null) {
            return true;
        }
        if (critStateReady()) {
            return true;
        }
        if (!AttackAura.extra(AttackAura.SMART_CRITS)) {
            return true;
        }
        if (critImpossible()) {
            return true;
        }
        if (mc.player.onGround()) {
            return !fallComing();
        }
        return false;
    }

    private static boolean critStateReady() {
        Minecraft mc = mc();
        return mc.player != null
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < -0.01
                && (mc.player.fallDistance > critFallHeight || mc.player.fallDistance > 0.005F)
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isPassenger()
                && !mc.player.isMobilityRestricted();
    }

    public static boolean critPending() {
        Minecraft mc = mc();
        return mc.player != null && mc.level != null
                && !mc.player.onGround() && !critImpossible();
    }

    private static boolean critImpossible() {
        Minecraft mc = mc();
        boolean isInWeb = mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB);
        boolean jumping = ((LivingEntityAccessor) mc.player).hatek$isJumping();
        boolean badLiquidMoment = !jumping && (mc.player.isInWater() || mc.player.isInLava())
                || mc.player.isEyeInFluid(FluidTags.WATER)
                || mc.player.isEyeInFluid(FluidTags.LAVA)
                || isInWeb;

        return badLiquidMoment
                || mc.player.onClimbable()
                || mc.player.isPassenger()
                || mc.player.isFallFlying()
                || mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.LEVITATION)
                || mc.player.hasEffect(MobEffects.SLOW_FALLING)
                || mc.player.getAbilities().flying
                || getYCapacityOnPlayerPos(2) < 0.1F;
    }

    private static final float SMASH_FALL = 1.6F;

    public static boolean holdingMaceSmash() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return false;
        }
        if (!mc.player.getMainHandItem().is(Items.MACE)) {
            return false;
        }
        if (mc.player.onGround()) {
            return false;
        }
        return !readyToSmash();
    }

    private static boolean readyToSmash() {
        Minecraft mc = mc();
        return mc.player.getY() < mc.player.yo - 0.05 && mc.player.fallDistance > SMASH_FALL;
    }

    public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit,
                                    InteractionHand hand, boolean cpsBypass) {
        return useEntity(livingIn, preHit, postHit, hand, cpsBypass, false);
    }

    /**
     * @param forceGap true — пропустить проверку attackTickGapReached().
     *                 Используется планировщиком: пейс уже учтён в плане,
     *                 повторная проверка в тик удара только вредила бы.
     */
    public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit,
                                    InteractionHand hand, boolean cpsBypass, boolean forceGap) {
        Minecraft mc = mc();
        if (livingIn != null && mc.gameMode != null && mc.player != null
                && (forceGap || attackTickGapReached())) {
            if (preHit != null) {
                preHit.run();
            }
            auraAttacking = true;
            try {
                mc.gameMode.attack(mc.player, livingIn);
            } finally {
                auraAttacking = false;
            }
            attackedLastTick = true;
            LagGuard.onAttack();

            PACE.markAura(mc.player.tickCount);

            if (hand != null) {
                mc.player.swing(hand);
            }
            if (cpsBypass) {
                hitCounterCPSBypassNext();
            } else {
                hitCounterCPSBypassReset();
            }
            COOLDOWN_TIMER.reset();

            critFallHeight = nextCritFallHeight();
            sprintLead = nextSprintLead();
            rollNextHit();

            if (postHit != null) {
                postHit.run();
            }
        }
        return livingIn != null;
    }

    private static float nextCritFallHeight() {
        return AuraRandom.human(0.01F, 0.03F);
    }

    private static void rollNextHit() {
        chargeTarget = CHARGE;

        if (PlayerStats.clickReady()) {
            float sampled = PlayerStats.click(AuraRandom.range(0.0F, 1.0F));
            auraGap = (long) Math.max(AURA_GAP_MIN, sampled);
        } else {
            auraGap = (long) (AuraRandom.human(AURA_GAP_MIN, AURA_GAP_MIN + AURA_GAP_SPREAD)
                    * AuraProfile.tempo());
        }

        if (!AttackAura.chargeSpreadEnabled()) {
            extraCooldown = 0L;
            return;
        }
        extraCooldown = (long) AuraRandom.human(0.0F, 10.0F);
    }

    private static long nextSprintLead() {
        return (long) AuraRandom.human(80.0F, 190.0F);
    }

    public static long getMsCooldown() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return 500L;
        }
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        return (long) (1.0 / attackSpeed * 1000.0 * chargeTarget) + extraCooldown;
    }

    public static boolean chargeReached() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        return mc.player.getAttackStrengthScale(0.5F) > chargeFloor();
    }

    private static float chargeFloor() {
        float safe = serverSafeCharge();
        float full = Math.max(safe, chargeTarget - CHARGE_SLACK);
        return critWindowOpen() ? safe : full;
    }

    /** Порог зарядки с запасом в один тик сервера: 0.9 + attackSpeed/20 + slack. */
    private static float serverSafeCharge() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return CRIT_CHARGE;
        }
        double perTick = mc.player.getAttributeValue(Attributes.ATTACK_SPEED) / 20.0;
        float safe = (float) (SERVER_CHARGE + perTick + SERVER_CHARGE_SLACK);
        return Mth.clamp(safe, CRIT_CHARGE, MAX_CHARGE_FLOOR);
    }

    private static boolean critWindowOpen() {
        return AttackAura.extra(AttackAura.SMART_CRITS) && critStateReady();
    }

    private static long attackInterval() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return Math.max(getMsCooldown(), auraGap);
        }
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        long forCharge = (long) (1.0 / attackSpeed * 1000.0 * chargeFloor()) + extraCooldown;
        if (critWindowOpen()) {
            return forCharge;
        }
        return Math.max(Math.min(getMsCooldown(), forCharge), auraGap);
    }

    public static boolean attackTickGapReached() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        return PACE.auraGapReached(mc.player.tickCount);
    }

    public static boolean wouldRefuseAttack() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return false;
        }
        AttackAura aura = AttackAura.instance();
        if (aura == null || !aura.isEnabled()) {
            return false;
        }
        return PACE.manualRefused(mc.player.tickCount);
    }

    public static boolean allowAttack() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return true;
        }
        if (auraAttacking) {
            if (!attackTickGapReached()) {
                return false;
            }
            PACE.markAura(mc.player.tickCount);
            return true;
        }
        if (wouldRefuseAttack()) {
            return false;
        }
        PACE.markManual(mc.player.tickCount);
        PlayerStats.click();
        return true;
    }

    public static boolean msCooldownReached(long msOffset) {
        return COOLDOWN_TIMER.finished(attackInterval() + msOffset);
    }

    public static boolean msCooldownReached() {
        return msCooldownReached(0L);
    }

    public static boolean msCooldownHasMs(long ms) {
        return COOLDOWN_TIMER.finished(ms);
    }

    public static float msCooldownPC01() {
        return Math.min((float) COOLDOWN_TIMER.elapsedTime() / (float) getMsCooldown(), 1.0F);
    }

    public static float msCooldownReach() {
        return COOLDOWN_TIMER.elapsedTime();
    }

    /**
     * Гейт удара — только реальная геометрия. Эвристика NeuroRotation.aimed()
     * (угол до центра ≤ 7°) сюда больше не допускается: она пропускала удары мимо
     * хитбокса, сервер их отклонял — замах есть, урона нет.
     */
    public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
        return anyEntityOnRayStrict(livingIn, range);
    }

    /** Строгая геометрическая проверка луча по ротации и позиции, известным серверу. */
    public static boolean anyEntityOnRayStrict(LivingEntity livingIn, double range) {
        Minecraft mc = mc();
        if (livingIn == null || mc.player == null) {
            return false;
        }
        return rayHitsEntity(livingIn, range);
    }

    /**
     * Удар выполняется после sendPosition(), так что ServerView = ротация/позиция,
     * с которыми сервер обработает пакет. Проверяем ровно как vanilla-пикинг:
     * реальный хитбокс (+pickRadius, без запаса), длина луча = дальность удара.
     * Раньше допускались: фолбэк на ещё не отправленную ротацию клиента,
     * упреждённый бокс SpaceTime, раздувание 0.12 и луч range+0.5 — всё это
     * давало удары, которые сервер/античит не засчитывал.
     */
    private static boolean rayHitsEntity(LivingEntity livingIn, double range) {
        Minecraft mc = mc();
        Vec3 eyes = ServerView.eyePos(mc.player);
        float yaw = ServerView.yaw(mc.player.getYRot());
        float pitch = ServerView.pitch(mc.player.getXRot());
        AABB box = livingIn.getBoundingBox().inflate(livingIn.getPickRadius());
        if (box.contains(eyes)) {
            return true;
        }
        Vec3 look = RayTraceUtil.getVectorForRotation(pitch, yaw);
        return box.clip(eyes, eyes.add(look.scale(range))).isPresent();
    }

    public static boolean onRay(AABB box, Vec3 eyes, float yaw, float pitch, double range, double tolerance) {
        if (box == null || eyes == null) {
            return false;
        }
        AABB target = box.inflate(tolerance);
        if (target.maxX <= target.minX || target.maxY <= target.minY || target.maxZ <= target.minZ) {
            return false;
        }
        Vec3 look = RayTraceUtil.getVectorForRotation(pitch, yaw);
        return target.contains(eyes)
                || target.clip(eyes, eyes.add(look.scale(range + 0.5))).isPresent();
    }

    public static boolean onRay(AABB box, float yaw, float pitch, double range, double tolerance) {
        Minecraft mc = mc();
        Vec3 eyes = mc.player != null ? ServerView.eyePos(mc.player) : Vec3.ZERO;
        return onRay(box, eyes, yaw, pitch, range, tolerance);
    }

    /**
     * Та же логика, что и {@link #shouldAttack}, но возвращает причину отказа
     * вместо boolean: null — удар разрешён, иначе "dist"/"lag"/"iframes"/"cooldown"/
     * "pace"/"charge"/"moment"/"ray". Используется планировщиком и диагностикой.
     */
    public static String shouldAttackReason(LivingEntity livingTarget, boolean rayCast,
                                            boolean distanceCheck, boolean fallCheck,
                                            long cooldownMSOffset, float[] ranges) {
        if (distanceCheck && livingTarget != null
                && !AuraUtil.validDistance(livingTarget, ranges[0], true)) {
            return "dist";
        }
        if (lagBlockedNow()) {
            return "lag";
        }
        if (targetInvulnerable(livingTarget)) {
            return "iframes";
        }
        if (!msCooldownReached(cooldownMSOffset)) {
            return "cooldown";
        }
        if (!attackTickGapReached()) {
            return "pace";
        }
        if (!chargeReached()) {
            return "charge";
        }
        if (!isBestMomentToHit(fallCheck)) {
            return "moment";
        }
        if (rayCast && !anyEntityOnRay(livingTarget, ranges[0])) {
            return "ray";
        }
        return null;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast,
                                       boolean distanceCheck, boolean fallCheck,
                                       long cooldownMSOffset, float[] ranges) {
        return shouldAttackReason(livingTarget, rayCast, distanceCheck, fallCheck,
                cooldownMSOffset, ranges) == null;
    }

    /**
     * Цель ещё в i-frames на сервере: первые 10 тиков после урона новый удар
     * не проходит (замах есть, урона нет). hurtTime на клиенте отстаёт на
     * пол-пинга, наш пакет летит ещё пол-пинга — вычитаем RTT в тиках (+1 запас,
     * чтобы не терять реальные удары).
     */
    private static boolean targetInvulnerable(LivingEntity livingTarget) {
        if (livingTarget == null || livingTarget.hurtTime <= 0) {
            return false;
        }
        return livingTarget.hurtTime > pingTicks() + 1;
    }

    private static int pingTicks() {
        Minecraft mc = mc();
        if (mc.player == null || mc.getConnection() == null) {
            return 0;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) {
            return 0;
        }
        return (int) Math.ceil(Math.max(0, info.getLatency()) / 50.0);
    }

    /** Лаг/фриз/сетбек или долг зарядки после стопора сервера. */
    public static boolean lagBlockedNow() {
        return LagGuard.blocking() || LagGuard.chargeDebtPending(getMsCooldown());
    }

    /** Удар вот-вот: зарядка почти добрана (или открыто окно крита). */
    public static boolean hitImminent() {
        Minecraft mc = mc();
        if (mc.player == null || AttackAura.target == null) {
            return false;
        }
        return critWindowOpen()
                || mc.player.getAttackStrengthScale(0.5F) >= chargeFloor() - 0.15F;
    }

    public static boolean targetInvulnerableNow(LivingEntity livingTarget) {
        return targetInvulnerable(livingTarget);
    }

    /** Доступы для планировщика ударов. */
    public static float chargeFloorValue() {
        return chargeFloor();
    }

    public static long cooldownRemainingMs() {
        return Math.max(0L, attackInterval() - COOLDOWN_TIMER.elapsedTime());
    }

    public static int paceRemainingTicks() {
        Minecraft mc = mc();
        return mc.player == null ? 0 : PACE.remainingTicks(mc.player.tickCount);
    }

    public static boolean critStateReadyNow() {
        return critStateReady();
    }

    public static boolean critImpossibleNow() {
        return critImpossible();
    }

    public static float critFallHeightValue() {
        return critFallHeight;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast,
                                       boolean fallCheck, long cooldownMSOffset, float[] ranges) {
        return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
    }

    public static boolean resetSprintTickP(LivingEntity targetIn, float[] ranges) {
        Minecraft mc = mc();
        return targetIn != null
                && shouldAttack(targetIn, false, false, -sprintLead - 20L, ranges)
                && !mc.player.onGround()
                && !mc.player.isEyeInFluid(FluidTags.WATER)
                && mc.player.getDeltaMovement().y <= 0.0030162615090425808;
    }

    public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
        Minecraft mc = mc();
        return targetIn != null
                && shouldAttack(targetIn, false, false, -sprintLead, ranges)
                && !mc.player.onGround()
                && !mc.player.isEyeInFluid(FluidTags.WATER)
                && mc.player.getDeltaMovement().y <= 0.42F;
    }

    private static int maxHitsCountOnMiss() {
        return 3;
    }

    public static void antiMissesHittingReset() {
        missDetected = false;
        counterTo0PostMissHits = 0;
    }

    public static void antiMissesHittingUpdate(LivingEntity targetIn, boolean cpsBypass,
                                               boolean rayCastCheck, boolean enabled) {
        Minecraft mc = mc();
        if (targetIn == null || counterTo0PostMissHits == 0 || !enabled || targetIn.hurtTime != 0) {
            antiMissesHittingReset();
        }
        if (enabled && targetIn != null
                && msCooldownHasMs(cpsBypassTrigger() ? 250L : 150L) && mc.player.swinging) {
            if (!missDetected && counterTo0PostMissHits == 0 && targetIn.hurtTime == 0) {
                missDetected = true;
                counterTo0PostMissHits = maxHitsCountOnMiss();
            }
            if (missDetected
                    && counterTo0PostMissHits > 0
                    && (!rayCastCheck || anyEntityOnRay(targetIn, 6.0))
                    && useEntity(targetIn, () -> {
            }, () -> {
            }, InteractionHand.MAIN_HAND, cpsBypass)) {
                counterTo0PostMissHits--;
            }
        }
    }

    public static void observe() {
        Minecraft mc = mc();
        if (mc.player == null) {
            return;
        }
        if (mc.player.tickCount < lastAirTick) {
            lastAirTick = FAR_PAST;
        }
        if (!mc.player.onGround()) {
            lastAirTick = mc.player.tickCount;
        }
    }

    private static boolean fallComing() {
        Minecraft mc = mc();
        if (mc.player == null || mc.options == null) {
            return false;
        }
        if (mc.options.keyJump.isDown()) {
            return true;
        }
        return mc.player.tickCount - lastAirTick <= 2;
    }

    public static void clampCooldownDebt() {
        if (attackedLastTick) {
            attackedLastTick = false;
            return;
        }
        if (mc().player == null) {
            return;
        }
        long cooldown = attackInterval();
        if (COOLDOWN_TIMER.elapsedTime() > cooldown) {
            COOLDOWN_TIMER.setMs(cooldown);
        }
    }

    public static AuraTimer getCooldownTimer() {
        return COOLDOWN_TIMER;
    }
}
