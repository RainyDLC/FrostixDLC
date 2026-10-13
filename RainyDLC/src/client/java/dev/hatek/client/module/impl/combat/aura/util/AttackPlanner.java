package dev.hatek.client.module.impl.combat.aura.util;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aura.Attack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

/**
 * Планировщик ударов «через N тиков» (идея: NeuroAttackPlanner из obrti-sources).
 *
 * <p>Проблема, которую чинит: обычный путь каждый тик проверяет каскад гейтов
 * (кулдаун, пейс, зарядка, humanization, смарт-криты). В тик идеального момента
 * какой-нибудь мягкий гейт может ветировать удар — и удар теряется (те самые
 * пропуски 1–2 ударов). Вместо «монетки в тик» планировщик в начале
 * «возможности» (геометрия сошлась: дистанция и луч в цели, мешают только
 * тайминги) вычисляет, через сколько тиков наступит момент удара, и гарантирует
 * удар в этот тик. Если в назначенный тик жёсткие гейты (дистанция, зарядка,
 * луч) не пустили — план сдвигается на 1–3 тика («удар при первой возможности»),
 * а не теряется навсегда.</p>
 *
 * <p>Ожидание крита считается симуляцией падения, а не реакцией на
 * {@code fallDistance}: невозможный крит не задерживает удар, а реальный —
 * планируется заранее.</p>
 */
public final class AttackPlanner {
    /** Причины отказа, которые являются таймингами (а не геометрией). */
    private static final Set<String> TIMING_REASONS = Set.of("lag", "iframes", "cooldown", "pace", "charge", "moment");

    /** Сколько тиков подряд геометрия может рваться, прежде чем план сгорит. */
    private static final int GRACE_TICKS = 3;

    /** Потолок планирования, чтобы не ждать вечность. */
    private static final int MAX_PLAN_TICKS = 30;

    /** Тиков симуляции падения для предсказания крита. */
    private static final int FALL_SIM_TICKS = 30;

    private static LivingEntity planTarget;
    private static int ticksLeft;
    private static boolean matured;
    private static int graceLeft = GRACE_TICKS;
    /** План включал ожидание крита — в момент удара перепроверяем его. */
    private static boolean planCrit;
    /** Сколько тиков уже дополнительно ждём крит сверх плана. */
    private static int critExtra;
    /**
     * План только на задержку человеческой реакции (все гейты уже были зелёные).
     * Если ситуация изменилась (появился тайминговый гейт) — пересчитываем.
     */
    private static boolean planReaction;

    private AttackPlanner() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void cancel() {
        planTarget = null;
        ticksLeft = 0;
        matured = false;
        graceLeft = GRACE_TICKS;
        planCrit = false;
        critExtra = 0;
        planReaction = false;
    }

    public static boolean hasPlan() {
        return planTarget != null && !matured;
    }

    /**
     * Вызывать каждый тик из AttackAura.attackEntity().
     * target == null или смена цели — план сбрасывается.
     */
    public static void onTick(LivingEntity target, float[] ranges) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null || ranges == null) {
            cancel();
            return;
        }
        if (planTarget != null && planTarget != target) {
            cancel();
        }

        // «Возможность»: геометрия сошлась, мешают только тайминги.
        String reason = Attack.shouldAttackReason(target, true, true, true, 0L, ranges);
        if (reason == null) {
            // Все гейты зелёные — удар ждёт только человеческой реакции.
            // Без этого ML-модель античита видит удар ровно в тик готовности
            // кулдауна каждый раз — классическая подпись бота.
            if (planTarget == null) {
                planTarget = target;
                ticksLeft = reactionDelayTicks();
                planCrit = false;
                critExtra = 0;
                planReaction = true;
                graceLeft = GRACE_TICKS;
                matured = ticksLeft <= 0;
            } else if (!matured && --ticksLeft <= 0) {
                matured = true;
            }
            return;
        }
        if (!TIMING_REASONS.contains(reason)) {
            if (graceLeft-- <= 0) {
                cancel();
            }
            return;
        }
        graceLeft = GRACE_TICKS;

        if (planTarget == null || planReaction) {
            // Реакционный план устарел — ситуация изменилась, считаем заново.
            planTarget = target;
            ticksLeft = computeDelayTicks();
            planReaction = false;
            matured = ticksLeft <= 0;
        } else {
            if (AttackAura.extra(AttackAura.SMART_CRITS) && !mc.player.onGround() && !Attack.critImpossibleNow()) {
                if (Attack.critStateReadyNow()) {
                    planCrit = true;
                    ticksLeft = 0;
                    matured = true;
                    return;
                }
                int crit = ticksUntilCrit();
                if (crit > 0) {
                    planCrit = true;
                    critExtra = 0;
                    ticksLeft = Math.max(ticksLeft, crit);
                }
            }
            if (ticksLeft > 0) {
                ticksLeft--;
                if (ticksLeft <= 0) {
                    matured = true;
                }
            } else {
                matured = true;
            }
        }
    }

    /**
     * Человеческая задержка реакции на готовый удар: экспоненциальное
     * распределение со средним из настройки «Hit Delay». 0 мс — бьёт
     * мгновенно, как раньше (максимальная скорость для PvP с читами).
     */
    private static int reactionDelayTicks() {
        float ms = AttackAura.hitDelayMs();
        if (ms <= 0.0F) {
            return 0;
        }
        double meanTicks = ms / 50.0;
        double u = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        int d = (int) (-Math.log(1.0 - u) * meanTicks);
        return Math.min(d, Math.max(6, (int) (meanTicks * 3)));
    }

    /**
     * Если план созрел и жёсткие гейты пропускают — возвращает true.
     * Вызывающий обязан выполнить удар (мягкие гейты уже учтены в плане).
     * Если жёсткие гейты не пустили — план сдвигается на 1–3 тика.
     */
    public static boolean consumePlannedHit(LivingEntity target, float[] ranges) {
        if (!matured || planTarget == null || planTarget != target) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();

        // Строжайший запрет: в воздухе при смарт-критах бить ТОЛЬКО при critStateReadyNow!
        // Никаких ударов на подъёме (vy >= 0) — это гарантированно не крит и сжигает кулдаун.
        if (AttackAura.extra(AttackAura.SMART_CRITS) && mc.player != null && !mc.player.onGround()
                && !Attack.critImpossibleNow() && !Attack.critStateReadyNow()) {
            ticksLeft = 1;
            matured = false;
            return false;
        }

        // Ждали крит, но симуляция ошиблась на тик:
        if (planCrit && critExtra < 8 && mc.player != null && !mc.player.onGround()
                && !Attack.critStateReadyNow() && !Attack.critImpossibleNow()) {
            critExtra++;
            ticksLeft = 1;
            matured = false;
            return false;
        }

        // Строгая геометрия: форсированный удар обязан реально попадать лучом,
        // иначе получим замах с частицами, но без урона (ghost hit).
        if (!AuraUtil.validDistance(target, ranges[0], true)
                || Attack.targetInvulnerableNow(target)
                || Attack.lagBlockedNow()
                || !Attack.chargeReached()
                || !Attack.anyEntityOnRayStrict(target, ranges[0])
                || Attack.holdingMaceSmash()) {
            int push = (mc.player != null && !mc.player.onGround()) ? 1 : 1 + AuraRandom.range(0, 2);
            ticksLeft = push;
            matured = false;
            return false;
        }
        cancel();
        return true;
    }

    private static int computeDelayTicks() {
        int charge = ticksUntilCharge();
        int cooldown = ticksUntilCooldown();
        int pace = Attack.paceRemainingTicks();
        int crit = ticksUntilCrit();
        // Не ждём далёкий крит дольше, чем нужно заряду, — бьём без крита.
        if (crit >= 0 && crit > charge + 12) {
            crit = -1;
        }
        planCrit = crit >= 0;
        critExtra = 0;
        int delay = Math.max(charge, Math.max(cooldown, pace));
        if (planCrit && crit > delay) {
            delay = crit;
        } else if (!planCrit) {
            // Человеческая задержка реакции: только когда НЕ целимся в крит!
            // Добавление рандомных тиков к таймингу крита роняет игрока на землю и сбивает крит.
            delay += reactionDelayTicks();
        }
        return Math.min(delay, MAX_PLAN_TICKS);
    }

    private static int ticksUntilCharge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        float scale = mc.player.getAttackStrengthScale(0.5F);
        float floor = Attack.chargeFloorValue();
        if (scale >= floor) {
            return 0;
        }
        double perTick = mc.player.getAttributeValue(Attributes.ATTACK_SPEED) / 20.0;
        if (perTick <= 0.0) {
            return 0;
        }
        return (int) Math.ceil((floor - scale) / perTick);
    }

    private static int ticksUntilCooldown() {
        long remainMs = Attack.cooldownRemainingMs();
        if (remainMs <= 0L) {
            return 0;
        }
        return (int) Math.ceil(remainMs / 50.0);
    }

    /**
     * Симуляция падения: через сколько тиков наступит крит-состояние.
     * Возвращает -1, если крит не нужен/невозможен/не случится.
     * Возвращает 0, если крит-состояние уже готово сейчас.
     */
    private static int ticksUntilCrit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return -1;
        }
        if (!AttackAura.extra(AttackAura.SMART_CRITS)) {
            return -1;
        }
        if (Attack.critStateReadyNow()) {
            return 0;
        }
        if (Attack.critImpossibleNow()) {
            return -1;
        }
        boolean airborne = !mc.player.onGround();
        boolean jumpHeld = mc.options != null && mc.options.keyJump.isDown();
        if (!airborne && !jumpHeld) {
            return -1;
        }
        double y = mc.player.getY();
        double vy = airborne ? mc.player.getDeltaMovement().y : 0.42;
        double groundY = mc.player.getY();
        if (airborne) {
            net.minecraft.world.phys.Vec3 pos = mc.player.position();
            net.minecraft.world.phys.HitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                    pos, pos.add(0.0, -5.0, 0.0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                groundY = hit.getLocation().y;
            } else {
                groundY = pos.y - 1.5;
            }
        }
        float fall = airborne ? (float) mc.player.fallDistance : 0.0F;
        float need = Attack.critFallHeightValue();
        for (int t = 1; t <= FALL_SIM_TICKS; t++) {
            vy = (vy - 0.08) * 0.98;
            y += vy;
            if (vy < 0.0) {
                fall += (float) -vy;
            }
            if (y <= groundY - 0.001 && vy < 0.0) {
                return -1; // приземлились раньше, чем набрали крит
            }
            if (fall > need && vy < -0.01) {
                return t;
            }
        }
        return -1;
    }
}
