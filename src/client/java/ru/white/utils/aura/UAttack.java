package ru.white.utils.aura;


import ru.white.module.impl.combat.AttackAura;
import ru.white.utils.annotation.IMinecraft;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;


@UtilityClass
public class UAttack implements IMinecraft {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static int getAxeSlot() {
        if (mc.player == null)
            return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }


    public static Runnable[] hitShieldBreakTaskForUse(LivingEntity livingIn, boolean enabled) {
        final Runnable[] pre$post = new Runnable[] { () -> {
        }, () -> {
        } };

        if (!enabled || mc.player == null)
            return pre$post;

        if (livingIn instanceof PlayerEntity player) {

            if (!player.isBlocking())
                return pre$post;

            final ItemStack main = player.getMainHandStack();
            final ItemStack off = player.getOffHandStack();
            final Item mainItem = main.isEmpty() ? null : main.getItem();
            final Item offItem = off.isEmpty() ? null : off.getItem();


            if (mainItem == Items.SHIELD || offItem == Items.SHIELD) {
                final int axeSlot = getAxeSlot();
                final int handSlot = mc.player.getInventory().getSelectedSlot();

                if (axeSlot != -1 && axeSlot != handSlot) {

                    pre$post[0] = () -> {
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
                        }
                    };


                    pre$post[1] = () -> {
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(handSlot));
                        }
                    };
                }
            }
        }

        return pre$post;
    }


    public static Runnable[] resetShieldSilentTaskForUse(boolean enabled) {
        final Runnable[] pre$post = new Runnable[] { () -> {
        }, () -> {
        } };
        if (!enabled || mc.player == null)
            return pre$post;

        if (mc.player.isBlocking()) {
            Hand active = mc.player.getActiveHand();

            if (active == null)
                return pre$post;

            pre$post[0] = () -> mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

            pre$post[1] = () -> mc.getNetworkHandler()
                    .sendPacket(new PlayerInteractItemC2SPacket(active, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
        return pre$post;
    }

    public static Runnable[] skipSilentSprintingTaskForUse(boolean enabled) {
        final Runnable[] pre$post = new Runnable[] { () -> {
        }, () -> {
        } };
        if (!enabled || mc.player == null)
            return pre$post;

        if (mc.player.isSprinting() && !mc.player.isOnGround()  && !AttackUtil.hasMovementRestrictions()) {
            pre$post[0] = () -> {
                // «Silent» трогает только клавишу: снимать сам флаг спринта без
                // пакета STOP_SPRINTING бессмысленно — сервер всё равно считает нас
                // спринтующими и крит не даст, а клиент нарисует частицы крита,
                // которого не было.
                if (AttackAura.get().typeSprint.is("Silent")) {
                    mc.options.sprintKey.setPressed(false);
                } else {
                    mc.options.sprintKey.setPressed(false);
                    mc.player.setSprinting(false);
                }
            };
            pre$post[1] = () -> {
                mc.options.sprintKey.setPressed(true);
                mc.player.setSprinting(true);
            };
        }
        return pre$post;
    }

    /** Касание земли произойдёт в пределах указанных тиков (падаем с отрицательной скоростью). */
    private static boolean isLandingWithinTicks(int ticks) {
        Vec3d v = mc.player.getVelocity();
        if (v.y >= -0.05F) return false;
        Box box = mc.player.getBoundingBox();
        double dy = v.y;
        for (int i = 0; i < ticks; i++) {
            Box shifted = box.offset(0, dy, 0);
            if (!mc.world.isSpaceEmpty(mc.player, shifted)) return true;
            box = shifted;
            dy = Math.max(dy * 0.98 - 0.06, -3.92);
        }
        return false;
    }

    public static boolean isBestMomentToHit(boolean fallCheck) {
        if (mc.player == null)
            return true;

        if (mc.player.getMainHandStack().getItem() == Items.MACE) {
            return true;
        }

        if (!fallCheck) return true;

        boolean landingSoon = isLandingWithinTicks(2);
        if (AttackUtil.isPlayerInCriticalState() || landingSoon) {
            return true;
        }

        if (AttackAura.get().others.getValue("Только криты")) {
            return false;
        }

        if (AttackAura.get().others.getValue("Умные криты")) {
            return !mc.options.jumpKey.isPressed() || AttackUtil.hasMovementRestrictions();
        }

        return true;
    }

    /**
     * Отправка удара. Порядок повторяет ванильный MinecraftClient.doAttack():
     * attackEntity (пакет + player.attack, который считает крит и сбрасывает заряд),
     * затем swingHand.
     */
    public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit, Hand hand) {
        if (preHit != null)
            preHit.run();
        if (livingIn != null && mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.attackEntity(mc.player, livingIn);
            if (hand != null)
                mc.player.swingHand(hand);

            cooldownTimer.reset();
        }
        if (postHit != null)
            postHit.run();
        return livingIn != null;
    }

    @Getter
    private static final StopWatch cooldownTimer = new StopWatch();

    /**
     * Заряд атаки по ванильному счётчику. Порог 0.9 — ровно тот, при котором ваниль
     * разрешает крит и свип; урон при нём ≈88%, а до 100% пришлось бы ждать ещё тик.
     * Бить сильно раньше бессмысленно: множитель урона равен 0.2 + p²·0.8, то есть
     * при половинном заряде удар отнимает пятую часть от нормального.
     */
    public static boolean isCharged() {
        return mc.player != null && mc.player.getAttackCooldownProgress(0.5F) >= 0.9F;
    }

    /** Интервал полного заряда в мс — для предсказания удара (пре-наведение). */
    public static long getMsCooldown() {
        if (mc.player == null)
            return 500L;

        double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);


        if (attackSpeed <= 0.0D)
            return 500L;

        // полный заряд = 1/attackSpeed секунды — ровно то, что считает ванильный
        // счётчик lastAttackedTicks. Бить раньше = неполный урон и никаких критов.
        long msCooldown = (long) Math.ceil(1000.0D / attackSpeed);





        return msCooldown;
    }

    public static boolean msCooldownReached(long msOffset) {
        return cooldownTimer.finished(getMsCooldown() + msOffset);
    }

    public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
        // рейкаст по хитбоксу цели — ровно так удар валидирует античит
        if (livingIn != null) {
            boolean ignoreBlocks = AttackAura.get().others.getValue("Бить через блоки");
            return LagCompensation.rayHits(livingIn, (float) range, ignoreBlocks);
        }
        return false;
    }


    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean distanceCheck,
                                       boolean fallCheck, long cooldownMSOffset, float[] ranges) {
        // dst - validDistance кастомный метод? Если стандартный - distanceTo
        // Предполагаем, что IMinecraft или миксин добавляет validDistance. Если нет,
        // замените на livingTarget.distanceTo(mc.player) <= ranges[0]
        if (distanceCheck && livingTarget != null && !AuraUtil.validDistance(livingTarget, ranges[0], true))
            return false;

        // Настоящий удар (offset >= 0) — по ванильному счётчику заряда.
        // Порог 0.9 — тот же, что требует ваниль для крита и свипа: удар проходит
        // через 11 тиков после предыдущего, а серверный инвуль цели длится 10,
        // поэтому «фотки» из-за собственного удара невозможны и без проверки
        // hurtTime. Отдельная проверка hurtTime здесь только добавляла задержку:
        // клиент узнаёт о попадании лишь через пинг, и инвуль на клиенте гас
        // позже, чем накапливался заряд.
        if (cooldownMSOffset >= 0L) {
            if (!isCharged())
                return false;
            // предмет на откате (мейс после смэша, жемчуг) — удар уйдёт впустую
            if (mc.player != null
                    && mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack()))
                return false;
        } else if (!UAttack.msCooldownReached(cooldownMSOffset)) {
            return false;
        }

        // best moment
        boolean validNext = UAttack.isBestMomentToHit(fallCheck);


        // ray cast rule
        if (validNext && rayCast && !anyEntityOnRay(livingTarget, ranges[0]))
            validNext = false;

        return validNext;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean fallCheck,
                                       long cooldownMSOffset, float[] ranges) {
        return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
    }

    public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
        if (targetIn != null && shouldAttack(targetIn, false, false, -50L, ranges)) {
            if (!mc.player.isOnGround() && !mc.player.isSubmergedIn(FluidTags.WATER)) {
                if (mc.player.getVelocity().y <= .0030162615090425808)
                    return true;
            }
        }
        return false;
    }
}
