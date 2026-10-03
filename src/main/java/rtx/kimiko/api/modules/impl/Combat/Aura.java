package rtx.kimiko.api.modules.impl.Combat;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.api.combat.aura.AuraWallsMode;
import rtx.kimiko.api.combat.aura.rotation.NeuroRotation;
import rtx.kimiko.api.combat.aura.rotation.RotationMode;
import rtx.kimiko.api.combat.aura.rotation.SimpleRotationMode;
import rtx.kimiko.api.combat.target.TargetManager;
import rtx.kimiko.api.combat.target.TargetQuery;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.events.impl.network.PacketSendEvent;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.SubCategory;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.modules.settings.impl.MultiSelectSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.utils.combat.CriticalHitChecker;
import rtx.kimiko.utils.combat.DistanceComparators;
import rtx.kimiko.utils.combat.MotionUtils;
import rtx.kimiko.utils.combat.PendingTargetAction;
import rtx.kimiko.utils.math.MathUtil;
import rtx.kimiko.utils.math.rotation.Rotation;
import rtx.kimiko.utils.math.rotation.RotationApplyMode;
import rtx.kimiko.utils.math.rotation.RotationManager;
import rtx.kimiko.utils.math.rotation.RotationPriority;
import rtx.kimiko.utils.math.rotation.RotationStepFunction;
import rtx.kimiko.utils.math.rotation.RotationUtil;
import rtx.kimiko.utils.player.PlayerWorldHelper;

public class Aura extends Module {
    public static Aura INSTANCE;

    private final ModeSetting rotationModeSetting;
    private final ModeSetting returnModeSetting;
    private final SliderSetting attackDistance;
    private final SliderSetting aimDistance;
    private final BooleanSetting onlyCrits;
    private final BooleanSetting smartCriticals;
    private final ModeSetting wallsSetting;
    private final BooleanSetting rayTrace;
    private final BooleanSetting targeting;
    private final BooleanSetting onlyWeapon;
    private final BooleanSetting autoMace;
    private final BooleanSetting noHitInv;
    private final MultiSelectSetting targetsSetting;
    private final ModeSetting sortingSetting;
    private final ModeSetting moveCorrectionSetting;
    private final ModeSetting styleAttackSetting;
    private final SliderSetting cpsLimiterSetting;
    private final ModeSetting sprintResetSetting;
    private final MultiSelectSetting neuroFightSetting;
    private final MultiSelectSetting utilitiesSetting;

    private final NeuroRotation neuroMode = new NeuroRotation();
    private final SimpleRotationMode simpleMode = new SimpleRotationMode();

    private long lastAttackTime = 0L;
    private boolean maceCharged = false;
    private boolean maceOnCooldown = false;
    private int hitCount = 0;

    public Aura() {
        super("Aura", "Automatically attacks nearby targets.", Category.COMBAT, SubCategory.PVP);
        INSTANCE = this;

        this.rotationModeSetting = (ModeSetting) register(new ModeSetting("Rotation Mode", "Aim rotation calculation mode", "Neuro", "Neuro", "Simple", "None"));
        this.returnModeSetting = (ModeSetting) register(new ModeSetting("Return Mode", "Camera return mode", "Smooth", "Smooth", "Camera", "None"));
        this.attackDistance = (SliderSetting) register(new SliderSetting("Attack Distance", "Maximum attack reach").range(1.0f, 6.0f).increment(0.1f).setValue(3.0f));
        this.aimDistance = (SliderSetting) register(new SliderSetting("Aim Distance", "Aim tracking distance").range(1.0f, 9.0f).increment(0.1f).setValue(3.5f));
        this.onlyCrits = (BooleanSetting) register(new BooleanSetting("Only Crits", "Only attack when critical strike is possible", true));
        this.smartCriticals = (BooleanSetting) register(new BooleanSetting("Smart Criticals", "Adaptive critical timing based on movement", true));
        this.wallsSetting = (ModeSetting) register(new ModeSetting("Walls", "Wall attack mode", "None", "None", "All", "Doors", "FT", "RW"));
        this.rayTrace = (BooleanSetting) register(new BooleanSetting("Raytrace", "Verify line of sight and bounding box intersection", true));
        this.targeting = (BooleanSetting) register(new BooleanSetting("Targeting", "Prioritize and lock targets", true));
        this.onlyWeapon = (BooleanSetting) register(new BooleanSetting("Only Weapon", "Only attack when holding a weapon", false));
        this.autoMace = (BooleanSetting) register(new BooleanSetting("Auto Mace", "Automatically strike with mace when falling", true));
        this.noHitInv = (BooleanSetting) register(new BooleanSetting("No Hit In GUI", "Do not attack while container GUI is open", true));

        this.targetsSetting = (MultiSelectSetting) register(new MultiSelectSetting("Targets", "Target types", "Players", "Animals", "Mobs", "Invisibles", "Naked Players", "Friends"));
        this.targetsSetting.value("Players", "Animals", "Mobs", "Invisibles", "Naked Players");

        this.sortingSetting = (ModeSetting) register(new ModeSetting("Sorting", "Target sorting order", "Distance", "Distance", "Health", "FOV"));
        this.moveCorrectionSetting = (ModeSetting) register(new ModeSetting("Move Correction", "Silent movement correction mode", "Silent", "Silent", "Direct", "None"));
        this.styleAttackSetting = (ModeSetting) register(new ModeSetting("Attack Style", "Combat timing style", "1.9", "1.9", "1.8"));
        this.cpsLimiterSetting = (SliderSetting) register(new SliderSetting("CPS", "Attack speed for 1.8 style").range(1.0f, 20.0f).increment(1.0f).setValue(12.0f).visible(() -> this.styleAttackSetting.is("1.8")));
        this.sprintResetSetting = (ModeSetting) register(new ModeSetting("Sprint Reset", "Sprint reset method", "Smart", "Smart", "Normal", "Packet", "None"));

        this.neuroFightSetting = (MultiSelectSetting) register(new MultiSelectSetting("Neuro Fight", "Neuro aimbot sub-features", "Timing", "Sprint", "Human Error").visibleWhen(() -> this.rotationModeSetting.is("Neuro")));
        this.neuroFightSetting.value("Timing", "Sprint", "Human Error");

        this.utilitiesSetting = (MultiSelectSetting) register(new MultiSelectSetting("Utilities", "Combat helper utilities", "Use Hit", "Sync TPS", "Shield Breaker"));
        this.utilitiesSetting.value("Use Hit", "Shield Breaker");
    }

    public static Aura getInstance() {
        return INSTANCE;
    }

    @Override
    protected void onEnable() {
        this.neuroMode.enabled();
        this.lastAttackTime = 0L;
        this.maceCharged = false;
        this.maceOnCooldown = false;
        this.hitCount = 0;
        EventBus.Companion.get().subscribe(RotationManager.INSTANCE);
    }

    @Override
    protected void onDisable() {
        EventBus.Companion.get().unsubscribe(RotationManager.INSTANCE);
        RotationManager.INSTANCE.setActiveRequest(null);
        RotationManager.INSTANCE.setState(rtx.kimiko.utils.math.rotation.RotationState.IDLE);
        RotationManager.INSTANCE.setSentRotation(null);
        this.neuroMode.targetNull();
        PendingTargetAction.clearTarget(this.mc.player);
        TargetManager.INSTANCE.resetTarget();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }

        if (this.aimDistance.getValue() < this.attackDistance.getValue()) {
            this.aimDistance.setValue(this.attackDistance.getValue());
        }

        RotationManager.INSTANCE.onTick();

        this.neuroMode.humanErrorEnabled = this.neuroFightSetting.isSelected("Human Error");

        float searchRange = Math.max(this.aimDistance.getValue(), this.attackDistance.getValue());
        TargetQuery query = new TargetQuery()
                .range(searchRange)
                .players(this.targetsSetting.isSelected("Players"))
                .animals(this.targetsSetting.isSelected("Animals"))
                .mobs(this.targetsSetting.isSelected("Mobs"))
                .invisibles(this.targetsSetting.isSelected("Invisibles"))
                .nakedPlayers(this.targetsSetting.isSelected("Naked Players"))
                .friends(this.targetsSetting.isSelected("Friends"));

        if (this.sortingSetting.is("Health")) {
            query.comparator(DistanceComparators.BY_HEALTH);
        } else if (this.sortingSetting.is("FOV")) {
            query.comparator(DistanceComparators.BY_FOV);
        } else {
            query.comparator(DistanceComparators.BY_DISTANCE);
        }

        Entity targetEntity = TargetManager.INSTANCE.getTargetEntity();
        if (!this.targeting.getValue() || targetEntity == null || !query.matches(targetEntity) || !targetEntity.isAlive()) {
            TargetManager.INSTANCE.updateTarget(query);
            targetEntity = TargetManager.INSTANCE.getTargetEntity();
        }

        if (targetEntity instanceof LivingEntity livingTarget) {
            applyRotations(livingTarget);

            if (isNeuroMode() && this.neuroFightSetting.isSelected("Timing")) {
                this.neuroMode.tickAttack(canHit(livingTarget));
            }

            if (handlePreAttackSprintReset()) {
                return;
            }

            if (canAttack(livingTarget)) {
                handleSprintReset(livingTarget);
                performAttack(livingTarget);
            }
        } else {
            this.neuroMode.targetNull();
            TargetManager.INSTANCE.resetTarget();
        }
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        RotationManager.INSTANCE.onPacketSend(event);
    }

    private void applyRotations(LivingEntity target) {
        if (this.onlyWeapon.getValue() && !PlayerWorldHelper.isHoldingSword()) {
            return;
        }

        RotationApplyMode applyMode;
        if (this.moveCorrectionSetting.is("Direct")) {
            applyMode = RotationApplyMode.DIRECT;
        } else if (this.moveCorrectionSetting.is("Silent")) {
            applyMode = RotationApplyMode.SILENT;
        } else {
            applyMode = RotationApplyMode.NONE;
        }

        String mode = this.rotationModeSetting.getSelected();
        if ("None".equals(mode)) {
            return;
        }

        RotationMode rotMode = "Neuro".equals(mode) ? this.neuroMode : this.simpleMode;
        AuraWallsMode walls = getWallsMode();

        rotMode.rotate(
                RotationManager.INSTANCE,
                this.attackDistance.getValue(),
                walls.isRaycastMode(),
                this.rayTrace.getValue(),
                applyMode,
                target
        );

        if (RotationManager.INSTANCE.getActiveRequest() != null) {
            String returnMode = this.returnModeSetting.getSelected();
            if ("Camera".equals(returnMode)) {
                RotationManager.INSTANCE.getActiveRequest().setMode(rtx.kimiko.utils.math.rotation.RotationMode.CAMERA);
            } else if ("None".equals(returnMode)) {
                RotationManager.INSTANCE.getActiveRequest().setMode(rtx.kimiko.utils.math.rotation.RotationMode.NONE);
            } else {
                RotationManager.INSTANCE.getActiveRequest().setMode(rtx.kimiko.utils.math.rotation.RotationMode.SMOOTH);
            }

            if (rotMode instanceof NeuroRotation) {
                RotationStepFunction stepFn = this.neuroMode::computeSilentRotation;
                RotationManager.INSTANCE.getActiveRequest().setStepFunction(stepFn);
            }
        }
    }

    private boolean canHit(LivingEntity target) {
        if (target == null || this.mc.player == null || !target.isAlive()) {
            return false;
        }
        double distSq = this.mc.player.getEyePos().squaredDistanceTo(target.getEyePos());
        double reach = this.attackDistance.getValue();
        if (distSq > reach * reach) {
            return false;
        }
        if (this.rayTrace.getValue() && !MathUtil.canReach(reach, RotationManager.INSTANCE.getCurrentRotation().getYaw(), RotationManager.INSTANCE.getCurrentRotation().getPitch(), this.mc.player, target, getWallsMode())) {
            return false;
        }
        return true;
    }

    private boolean canAttack(LivingEntity target) {
        if (this.mc.player == null || target == null || !target.isAlive()) {
            return false;
        }
        if (this.onlyWeapon.getValue() && !PlayerWorldHelper.isHoldingSword()) {
            return false;
        }
        if (this.noHitInv.getValue() && this.mc.currentScreen instanceof HandledScreen) {
            return false;
        }
        if (isUsingItem()) {
            return false;
        }
        if (!canHit(target)) {
            return false;
        }
        if (!isAttackTimingReady()) {
            return false;
        }
        if (this.onlyCrits.getValue() && !isCritCondition()) {
            return false;
        }
        return true;
    }

    private boolean isCritCondition() {
        if (this.mc.player == null) return false;
        if (this.smartCriticals.getValue()) {
            if (!this.mc.player.isOnGround()) {
                return CriticalHitChecker.isCritical(this.mc.player);
            }
            return !this.mc.options.jumpKey.isPressed();
        }
        return CriticalHitChecker.isCritical(this.mc.player);
    }

    private boolean isAttackTimingReady() {
        if (this.mc.player == null) {
            return false;
        }

        if (this.autoMace.getValue() && isMaceReady()) {
            return true;
        }

        if (isNeuroMode() && this.neuroFightSetting.isSelected("Timing")) {
            if (!this.neuroMode.isHitReady()) {
                return false;
            }
            if (this.styleAttackSetting.is("1.8")) {
                return System.currentTimeMillis() - this.lastAttackTime >= (long) (1000.0f / this.cpsLimiterSetting.getValue());
            }
            return this.mc.player.getAttackCooldownProgress(0.0f) >= 0.85f;
        }

        if (this.styleAttackSetting.is("1.8")) {
            long delay = (long) (1000.0f / Math.max(1.0f, this.cpsLimiterSetting.getValue()));
            return System.currentTimeMillis() - this.lastAttackTime >= delay;
        }

        float progress = this.mc.player.getAttackCooldownProgress(0.0f);
        return progress >= 0.85f;
    }

    private boolean isMaceReady() {
        if (this.mc.player == null) return false;
        ItemStack main = this.mc.player.getMainHandStack();
        return main.getItem() instanceof MaceItem && this.mc.player.fallDistance > 1.5f && this.mc.player.getVelocity().y < 0.0;
    }

    private boolean isUsingItem() {
        if (this.mc.player == null || !this.mc.player.isUsingItem()) {
            return false;
        }
        if (!this.utilitiesSetting.isSelected("Use Hit")) {
            return false;
        }
        ItemStack active = this.mc.player.getActiveItem();
        return active.getItem() instanceof ShieldItem;
    }

    private boolean handlePreAttackSprintReset() {
        if (this.mc.player == null) return false;
        if (isNeuroMode() && this.neuroFightSetting.isSelected("Sprint") && !this.neuroMode.isMovementAllowed()) {
            return true;
        }
        return false;
    }

    private void handleSprintReset(LivingEntity target) {
        if (this.mc.player == null) return;
        if (this.sprintResetSetting.is("None")) return;
        if (isNeuroMode() && this.neuroFightSetting.isSelected("Sprint")) return;

        // Strictly do NOT reset sprint in mid-air (jumping/falling)
        // Modifying sprint state in air causes instant speed / friction flags on anti-cheats (GrimAC, Vulcan)
        if (!this.mc.player.isOnGround()) {
            return;
        }

        if (!this.mc.player.isSprinting()) {
            return;
        }

        if (this.sprintResetSetting.is("Packet")) {
            if (this.mc.getNetworkHandler() != null) {
                this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            this.mc.player.setSprinting(false);
        } else if (this.sprintResetSetting.is("Normal")) {
            this.mc.player.setSprinting(false);
        } else if (this.sprintResetSetting.is("Smart")) {
            if (canHit(target)) {
                if (this.mc.getNetworkHandler() != null) {
                    this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
                this.mc.player.setSprinting(false);
            }
        }
    }

    private void performAttack(LivingEntity target) {
        if (this.mc.player == null || this.mc.interactionManager == null || target == null) {
            return;
        }

        if (this.utilitiesSetting.isSelected("Shield Breaker") && MotionUtils.isBlocking(target)) {
            MotionUtils.breakShield(target);
        }

        this.mc.interactionManager.attackEntity(this.mc.player, target);
        this.mc.player.swingHand(Hand.MAIN_HAND);

        // Re-sprint after attack if on ground and moving forward
        if (this.mc.player.isOnGround() && !this.sprintResetSetting.is("None") && !this.mc.player.isSprinting()) {
            if (this.mc.player.input != null && this.mc.player.input.hasForwardMovement() || this.mc.options.sprintKey.isPressed()) {
                if (this.sprintResetSetting.is("Packet") || this.sprintResetSetting.is("Smart")) {
                    if (this.mc.getNetworkHandler() != null) {
                        this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                    }
                }
                this.mc.player.setSprinting(true);
            }
        }

        this.lastAttackTime = System.currentTimeMillis();
        this.hitCount++;

        String mode = this.rotationModeSetting.getSelected();
        if ("Neuro".equals(mode)) {
            this.neuroMode.attack();
        } else if ("Simple".equals(mode)) {
            this.simpleMode.attack();
        }
    }

    public boolean shouldSprintReset() {
        if (isNeuroMode() && this.neuroFightSetting.isSelected("Sprint")) {
            return !this.neuroMode.isMovementAllowed();
        }
        return false;
    }

    public AuraWallsMode getWallsMode() {
        return switch (this.wallsSetting.getSelected()) {
            case "All" -> AuraWallsMode.ALL;
            case "Doors" -> AuraWallsMode.DOORS;
            case "FT" -> AuraWallsMode.FT;
            case "RW" -> AuraWallsMode.RW;
            default -> AuraWallsMode.NONE;
        };
    }

    public boolean isNeuroMode() {
        return "Neuro".equals(this.rotationModeSetting.getSelected());
    }

    public NeuroRotation getNeuroRotation() {
        return this.neuroMode;
    }

    public int getHitCount() {
        return this.hitCount;
    }
}
