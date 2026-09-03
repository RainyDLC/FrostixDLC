package fun.newrar.utils.aura;

import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.utils.annotation.IMinecraft;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.util.math.Direction;

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

    private static boolean sprintingOnServer;

    public static void setSprintingOnServer(boolean value) {
        sprintingOnServer = value;
    }

    public static boolean isSprintingOnServer() {
        return sprintingOnServer;
    }

    public static boolean isCriticalHit() {
        if (mc.player == null) return false;

        if (mc.player.getAttackCooldownProgress(0.5F) <= 0.9F) return false;

        if (sprintingOnServer) return false;

        return mc.player.fallDistance > 0.0F
                && !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.hasVehicle();
    }

    public static boolean isBestMomentToHit(boolean fallCheck) {
        if (mc.player == null)
            return true;

        if (!fallCheck) return true;

        if (mc.player.getMainHandStack().getItem() == Items.MACE) {
            return true;
        }

        if (AttackAura.get().others.getValue("Только криты")) {
            return isCriticalHit();
        }

        if (AttackAura.get().others.getValue("Умные криты")) {
            if (isCriticalHit()) return true;

            return !mc.options.jumpKey.isPressed() || AttackUtil.hasMovementRestrictions();
        }

        return true;
    }

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

    public static boolean isCharged() {
        return mc.player != null && mc.player.getAttackCooldownProgress(0.5F) >= 0.9F;
    }

    public static boolean chargeReadyIn(int ticks) {
        if (mc.player == null)
            return false;

        double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
        if (attackSpeed <= 0.0D)
            return false;

        float perTick = (float) (attackSpeed / 20.0D);
        return mc.player.getAttackCooldownProgress(0.5F) + perTick * ticks >= 0.9F;
    }

    public static long getMsCooldown() {
        if (mc.player == null)
            return 500L;

        double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);

        if (attackSpeed <= 0.0D)
            return 500L;

        long msCooldown = (long) Math.ceil(1000.0D / attackSpeed);

        return msCooldown;
    }

    public static boolean msCooldownReached(long msOffset) {
        return cooldownTimer.finished(getMsCooldown() + msOffset);
    }

    public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
        if (livingIn != null) {
            boolean ignoreBlocks = AttackAura.get().others.getValue("Бить через блоки");
            return LagCompensation.rayHits(livingIn, (float) range, ignoreBlocks);
        }
        return false;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean distanceCheck,
                                       boolean fallCheck, long cooldownMSOffset, float[] ranges) {
        if (distanceCheck && livingTarget != null && !AuraUtil.validDistance(livingTarget, ranges[0], true))
            return false;

        if (cooldownMSOffset >= 0L) {
            if (!isCharged())
                return false;

            if (mc.player != null
                    && mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack()))
                return false;
        } else if (!UAttack.msCooldownReached(cooldownMSOffset)) {
            return false;
        }

        boolean validNext = UAttack.isBestMomentToHit(fallCheck);

        if (validNext && rayCast && !anyEntityOnRay(livingTarget, ranges[0]))
            validNext = false;

        return validNext;
    }

    public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean fallCheck,
                                       long cooldownMSOffset, float[] ranges) {
        return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
    }

    private static final int SPRINT_LEAD_TICKS = 2;

    public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
        if (targetIn == null || mc.player == null)
            return false;

        if (!chargeReadyIn(SPRINT_LEAD_TICKS))
            return false;

        if (!AuraUtil.validDistance(targetIn, ranges[0] + 0.5F, true))
            return false;

        if (mc.player.isOnGround() || mc.player.isSubmergedIn(FluidTags.WATER))
            return false;

        return mc.player.getVelocity().y <= .0030162615090425808;
    }
}

