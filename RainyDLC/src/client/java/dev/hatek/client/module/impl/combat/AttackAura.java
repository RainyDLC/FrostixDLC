package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aura.Attack;
import dev.hatek.client.module.impl.combat.aura.neuro.NeuroRotation;
import dev.hatek.client.module.impl.combat.aura.neuro.NeuroTrainScreen;
import dev.hatek.client.module.impl.combat.aura.rotation.ComponentManager;
import dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.SpaceTime;
import dev.hatek.client.module.impl.combat.aura.util.AuraProfile;
import dev.hatek.client.module.impl.combat.aura.util.AuraRandom;
import dev.hatek.client.module.impl.combat.aura.util.AuraUtil;
import dev.hatek.client.module.impl.combat.aura.util.MoveUtil;
import dev.hatek.client.module.impl.combat.aura.util.MovementManager;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import dev.hatek.client.module.impl.combat.aimassist.DynamicButtonSetting;
import dev.hatek.client.module.impl.movement.AutoSprint;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AttackAura extends Module {
    public static final String MOVE_DEFAULT = "Default";

    public static final String MOVE_FREE = "Free";

    public static final String SPACE_TIME = "SpaceTime";

    public static final String ROT_SPACETIME = "SpaceTime";
    public static final String ROT_NEURO = "Нейро";

    public static final String LEGIT_SPRINT = "Legit Sprint";
    public static final String SMART_CRITS = "Smart Crits";
    public static final String SLOW_TURN = "Slow Turn";

    public static final String THROUGH_WALLS = "Through Walls";
    public static final String WEAPON_ONLY = "Weapon Only";
    public static final String NO_EATING = "Not While Eating";
    public static final String NO_CONTAINER = "Not In Container";
    public static final String BREAK_SHIELD = "Break Shield";
    public static final String DROP_SHIELD = "Drop Shield";

    public static final String PLAYERS = "Players";
    public static final String NAKED = "Naked";
    public static final String MOBS = "Mobs";

    private static AttackAura instance;

    private final SliderSetting attackRange = new SliderSetting("Attack Range", 3.0, 3.0, 6.0, 0.1);
    private final SliderSetting preRange = new SliderSetting("Detect Range", 1.0, 0.0, 5.0, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 5.0);

    private final ModeSetting movementType =
            new ModeSetting("Movement", 0, MOVE_DEFAULT, MOVE_FREE);
    private final MultiSetting targets = new MultiSetting("Targets",
            List.of(PLAYERS, NAKED, MOBS), PLAYERS, NAKED, MOBS);
    private final MultiSetting misc = new MultiSetting("Checks",
            List.of(THROUGH_WALLS, WEAPON_ONLY, NO_EATING, NO_CONTAINER, BREAK_SHIELD, DROP_SHIELD),
            NO_EATING);
    private final MultiSetting extras = new MultiSetting("Extra",
            List.of(LEGIT_SPRINT, SMART_CRITS, SLOW_TURN),
            LEGIT_SPRINT, SMART_CRITS, SLOW_TURN);

    private final BoolSetting chargeSpread = new BoolSetting("Charge Spread", true);

    private final ModeSetting rotations =
            new ModeSetting("Ротации", 0, ROT_SPACETIME, ROT_NEURO);
    private final DynamicButtonSetting neuroTrain =
            new DynamicButtonSetting("Нейро-обучение", "Обучить ротации",
                    this::onNeuroTrainButton);

    private static final double LOOK_RANGE = 32.0;

    private static final float KEEP_MARGIN = 1.5F;

    public static LivingEntity target;
    public static boolean canSwap;

    private static LivingEntity preferred;

    private static boolean serverSprinting;

    private static boolean sprintHeld;

    public AttackAura() {
        super("AttackAura", "Automatically attacks the enemy near you, targeting you",
                Category.COMBAT);
        instance = this;
        keybind(GLFW.GLFW_KEY_R);
        with(this.attackRange, this.preRange, this.fov, this.movementType, this.targets, this.misc,
                this.extras, this.chargeSpread, this.rotations, this.neuroTrain);

        ComponentManager.getInstance().init();

        NeuroRotation.init();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static AttackAura instance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public static float attackRange() {
        return instance == null ? 3.0F : (float) instance.attackRange.value();
    }

    public static float preRange() {
        return instance == null ? 1.0F : (float) instance.preRange.value();
    }

    public static boolean extra(String name) {
        return instance != null && instance.extras.isSelected(name);
    }

    public static boolean misc(String name) {
        return instance != null && instance.misc.isSelected(name);
    }

    public static boolean target(String name) {
        return instance != null && instance.targets.isSelected(name);
    }

    public static boolean chargeSpreadEnabled() {
        return instance == null || instance.chargeSpread.value();
    }

    /** Выбран ли режим нейро-ротаций. */
    public static boolean neuroMode() {
        return instance != null && instance.rotations.is(ROT_NEURO);
    }

    /** Активна ли прямо сейчас нейро-ротация (режим + обученная сеть + не идёт запись). */
    public static boolean neuroActive() {
        return neuroMode() && instance.isEnabled()
                && !NeuroRotation.isTraining() && NeuroRotation.hasBrain();
    }

    /** Выбран ли режим свободного движения. */
    public static boolean isFreeMove() {
        return instance != null && instance.isEnabled() && instance.movementType.is(MOVE_FREE);
    }

    private void onNeuroTrainButton() {
        if (NeuroRotation.isTraining()) {
            NeuroRotation.stopTraining();
            return;
        }
        mc().gui.setScreen(new NeuroTrainScreen(this));
    }

    public static float[] getRanges() {
        return new float[]{attackRange(), preRange()};
    }

    public void onTick() {
        Minecraft mc = mc();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        Attack.clampCooldownDebt();

        Attack.observe();

        AuraProfile.tick();

        if (mc.gui.screen() != null) {
            target = null;
            MoveUtil.restoreSprintKey();
            MoveUtil.restoreMovementKeys();
            return;
        }

        NeuroRotation.onClientTick();
        this.neuroTrain.caption(
                NeuroRotation.isTraining() ? "Стоп" : "Обучить ротации");

        // При обучении игрок наводится и двигается 100% сам — аура не перехватывает управление
        if (NeuroRotation.isTraining()) {
            target = null;
            MoveUtil.restoreSprintKey();
            MoveUtil.restoreMovementKeys();
            return;
        }

        onUpdate();

        if (target != null) {
            updateRotation();
        } else if (neuroMode()) {
            NeuroRotation.setTarget(null);
        }
    }

    private void onUpdate() {
        Minecraft mc = mc();
        if (!mc.player.isAlive()) {
            setEnabled(false);
            return;
        }

        serverSprinting = mc.player.isSprinting();

        if (preferred != null && (!preferred.isAlive() || preferred.isRemoved())) {
            preferred = null;
        }

        boolean keepPreferred = preferred != null
                && (preferred == target ? canKeep(preferred) : isValidTarget(preferred));

        if (keepPreferred) {
            target = preferred;
        } else if (target == null || !canKeep(target)) {
            updateTarget();
        }

        if (target == null) {
            reset();
            return;
        }

        if (!MovementManager.getInstance().isMovementLocked()) {
            if (this.movementType.is(MOVE_FREE)) {
                MoveUtil.restoreMovementKeys();
            } else {
                boolean steer = this.movementType.is(MOVE_DEFAULT) && target != null;
                MoveUtil.fixMovement(steer ? bearingToTarget() : FreeLookUtil.freeYaw);
            }
        }

        boolean nearEnough = target != null
                && AuraUtil.getStrictDistance(target) <= attackRange() + 0.6F;

        boolean airNoSprint = nearEnough
                && !packetSprint()
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y <= 0.08
                && !mc.player.isInWater()
                && !mc.player.isUnderWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable();

        boolean shouldHoldSprint = !this.movementType.is(MOVE_FREE)
                && (!packetSprint() && Attack.resetSprintTick(target, getRanges()) || airNoSprint);

        if (shouldHoldSprint) {
            holdSprint();
        } else {
            releaseSprint();

            MoveUtil.restoreSprintKey();
        }

        if (!checkToAttack() && !NeuroRotation.isTraining()) {
            attackEntity();
        }
    }

    private static final double STANDOFF = 1.0;

    private static int circleSide = 1;
    private static long nextFlip;

    private float bearingToTarget() {
        Minecraft mc = mc();
        if (mc.player == null || target == null) {
            return FreeLookUtil.freeYaw;
        }
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        float direct = (float) Math.toDegrees(Math.atan2(-dx, dz));

        double flat = Math.hypot(dx, dz);
        double outer = STANDOFF * 1.7;
        if (flat >= outer) {
            return direct;
        }

        long now = System.currentTimeMillis();
        if (mc.options.keyLeft.isDown()) {
            circleSide = -1;
        } else if (mc.options.keyRight.isDown()) {
            circleSide = 1;
        } else if (now >= nextFlip) {
            circleSide = AuraRandom.chance(0.5F) ? 1 : -1;
            nextFlip = now + (long) AuraRandom.range(2600.0F, 7000.0F);
        }

        float bend;
        if (flat >= STANDOFF) {
            bend = 90.0F * (float) ((outer - flat) / (outer - STANDOFF));
        } else {
            bend = 90.0F + 28.0F * (float) Mth.clamp(1.0 - flat / STANDOFF, 0.0, 1.0);
        }
        return Mth.wrapDegrees(direct + bend * circleSide);
    }

    private void holdSprint() {
        Minecraft mc = mc();
        if (!sprintHeld) {
            AutoSprint.pushPause(0L);
            sprintHeld = true;
        }
        mc.player.setSprinting(false);
        mc.options.keySprint.setDown(false);
    }

    private void releaseSprint() {
        Minecraft mc = mc();
        if (!sprintHeld) {
            return;
        }
        sprintHeld = false;
        AutoSprint.popPause();

        if (!AutoSprint.isSprinting() || mc.player == null || mc.options == null) {
            return;
        }
        if (!mc.player.onGround() || !mc.options.keyUp.isDown()) {
            return;
        }
        mc.options.keySprint.setDown(true);
        mc.player.setSprinting(true);
    }

    private boolean packetSprint() {
        Minecraft mc = mc();
        return mc.player != null
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger()
                && !extra(LEGIT_SPRINT);
    }

    public static boolean spaceTime() {
        return instance != null;
    }

    public static float targetAngularHalf() {
        Minecraft mc = mc();
        if (mc.player == null || instance == null || instance.target == null) {
            return 0.0F;
        }
        AABB box = instance.target.getBoundingBox();
        double distance = Math.max(0.35, mc.player.getEyePosition().distanceTo(box.getCenter()));
        double half = Math.max(box.getXsize(), box.getZsize()) * 0.5;
        return (float) Math.toDegrees(Math.atan2(half, distance));
    }

    public boolean isRayCastRuleToAttack() {
        return true;
    }

    /** Наведена ли ротация на цель — зависит от выбранного режима. */
    private boolean aimedNow() {
        if (neuroMode() && NeuroRotation.hasBrain()) {
            return NeuroRotation.aimed();
        }
        return SpaceTime.aimed() && SpaceTime.settled();
    }

    public void attackEntity() {
        Minecraft mc = mc();
        if (target == null || AuraUtil.getStrictDistance(target) >= attackRange()) {
            return;
        }

        float[] base = getRanges();
        float[] ranges = new float[]{base[0], base[1], base[0] + base[1]};

        boolean canPacket = packetSprint();

        Attack.antiMissesHittingUpdate(target, true, isRayCastRuleToAttack(), false);

        boolean canAttack = Attack.shouldAttack(target, isRayCastRuleToAttack(), true,
                true, 0L, ranges);

        if (!canAttack) {
            return;
        }

        if (!aimedNow()) {
            return;
        }

        Runnable[] shieldBreak = Attack.hitShieldBreakTaskForUse(target, misc(BREAK_SHIELD));
        Runnable[] shieldPressBypass = Attack.resetShieldSilentTaskForUse(true);
        Runnable[] skipSilentSprint = Attack.skipSilentSprintingTaskForUse(canPacket, serverSprinting);

        Runnable preHit = () -> {
            skipSilentSprint[0].run();
            shieldPressBypass[0].run();
            shieldBreak[0].run();
        };
        Runnable postHit = () -> {
            shieldBreak[1].run();
            shieldPressBypass[1].run();
            skipSilentSprint[1].run();
        };

        if (misc(DROP_SHIELD) && mc.player.getUseItem().getItem().equals(Items.SHIELD)
                && mc.player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }

        Attack.useEntity(target, preHit, postHit, InteractionHand.MAIN_HAND, true);
    }

    private void updateRotation() {
        if (NeuroRotation.isTraining()) {
            // Во время обучения игрок наводится сам — ротации не трогаем
            return;
        }
        if (neuroMode() && NeuroRotation.hasBrain()) {
            NeuroRotation.setTarget(target);
            return;
        }

        float[] base = getRanges();
        float[] ranges = new float[]{base[0], base[1], base[0] + base[1]};

        boolean canAttack = Attack.shouldAttack(target, false, true, 0L, ranges);
        SpaceTime.onRotation(target, canAttack);
    }

    private void updateTarget() {
        Minecraft mc = mc();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) {
                continue;
            }
            if (this.fov.value() < 360.0 && AuraUtil.calculateFOVFromCamera(living) > this.fov.value()) {
                continue;
            }
            double distance = mc.player.distanceToSqr(living);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }

        target = best;
    }

    private LivingEntity lookingAt() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isAllowedTarget(living)) {
                continue;
            }
            double distance = mc.player.distanceTo(living);
            if (distance > LOOK_RANGE || distance >= bestDistance) {
                continue;
            }
            if (!RayTraceUtil.rayTraceEntity(yaw, pitch, LOOK_RANGE, living)) {
                continue;
            }
            bestDistance = distance;
            best = living;
        }
        return best;
    }

    private float auraDist() {
        return attackRange() + preRange();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return AuraUtil.getStrictDistance(entity) <= auraDist() && isAllowedTarget(entity);
    }

    private boolean canKeep(LivingEntity entity) {
        return AuraUtil.getStrictDistance(entity) <= auraDist() + KEEP_MARGIN
                && isAllowedTarget(entity);
    }

    private boolean isAllowedTarget(LivingEntity entity) {
        Minecraft mc = mc();
        if (entity instanceof LocalPlayer) {
            return false;
        }
        if (!misc(THROUGH_WALLS) && !mc.player.hasLineOfSight(entity)) {
            return false;
        }
        if (entity instanceof Player && !target(PLAYERS)) {
            return false;
        }
        if (entity instanceof Player && entity.getArmorValue() == 0 && !target(NAKED)) {
            return false;
        }
        if (entity instanceof Player player && player.isCreative()) {
            return false;
        }
        if ((entity instanceof Monster || entity instanceof Slime || entity instanceof Villager
                || entity instanceof Animal) && !target(MOBS)) {
            return false;
        }
        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStand);
    }

    @Override
    protected void onEnable() {
        AuraProfile.roll();

        SpaceTime.reshuffleAll();

        Attack.getCooldownTimer().reset();

        reset();

        preferred = lookingAt();
        updateTarget();
    }

    @Override
    protected void onDisable() {
        reset();

        NeuroRotation.reset();

        RotationProcess.requestReset();
    }

    private void reset() {
        Minecraft mc = mc();
        target = null;
        preferred = null;

        SpaceTime.forget();

        releaseSprint();

        if (mc.player != null) {
            MoveUtil.restoreSprintKey();

            MoveUtil.restoreMovementKeys();
        }

        MovementManager.getInstance().unlockMovement("Aura");
    }

    private boolean checkToAttack() {
        Minecraft mc = mc();
        return Attack.holdingMaceSmash()
                || mc.player.isUsingItem() && misc(NO_EATING)
                        && !(mc.player.getUseItem().getItem() instanceof ShieldItem)
                || mc.gui.screen() != null && misc(NO_CONTAINER)
                || !mc.player.getMainHandItem().is(ItemTags.SWORDS)
                        && !mc.player.getMainHandItem().is(ItemTags.AXES)
                        && misc(WEAPON_ONLY);
    }
}
