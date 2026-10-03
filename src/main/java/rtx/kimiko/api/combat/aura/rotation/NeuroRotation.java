package rtx.kimiko.api.combat.aura.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.api.combat.neuro.AuraHumanStyle;
import rtx.kimiko.api.combat.neuro.NeuroAimSolver;
import rtx.kimiko.api.combat.neuro.NeuroModel;
import rtx.kimiko.utils.combat.PlayerStateSnapshot;
import rtx.kimiko.utils.math.rotation.Rotation;
import rtx.kimiko.utils.math.rotation.RotationApplyMode;
import rtx.kimiko.utils.math.rotation.RotationManager;
import rtx.kimiko.utils.math.rotation.RotationPriority;
import rtx.kimiko.utils.math.rotation.RotationUtil;

import java.util.concurrent.ThreadLocalRandom;

public class NeuroRotation extends RotationMode {
    public NeuroAimSolver rotationEngine = new NeuroAimSolver();
    public NeuroAimSolver silentRotationEngine = new NeuroAimSolver();
    public float[] rotationOutput = new float[2];
    public int lastTargetId = -1;
    public int hitCountdown = -1;
    public int[] attackPrediction = new int[3];
    public int gateWaitTicks;
    public int attackCount;
    public int scheduledCount;
    public int postAttackCooldown;
    public boolean neuralAttackEnabled;
    public int predictedCooldown;
    public int predictedPostDelay;
    public int settleTicks;
    public int refreshCountdown;
    public float reachDistance;
    public boolean pendingAttack;
    public boolean humanErrorEnabled = true;

    public NeuroRotation() {
        super("Нейро");
    }

    public void resetStats() {
        this.gateWaitTicks = 0;
        this.attackCount = 0;
        this.scheduledCount = 0;
    }

    public void tickAttack(boolean canHit) {
        if (this.postAttackCooldown > 0) {
            --this.postAttackCooldown;
        }
        if (!this.neuralAttackEnabled) {
            this.hitCountdown = -1;
            return;
        }
        if (this.hitCountdown < 0) {
            if (!canHit) {
                return;
            }
            NeuroModel model = NeuroModel.getActive();
            if (model == null || !this.rotationEngine.sampleDurations(model, this.attackPrediction)) {
                return;
            }
            this.hitCountdown = this.attackPrediction[0];
            this.predictedCooldown = this.attackPrediction[1];
            this.predictedPostDelay = this.attackPrediction[2];
            ++this.scheduledCount;
        } else if (this.hitCountdown > 0) {
            --this.hitCountdown;
        } else {
            ++this.gateWaitTicks;
        }
    }

    public boolean isHitReady() {
        return this.neuralAttackEnabled && this.hitCountdown == 0;
    }

    public boolean isInAttackWindow() {
        return this.neuralAttackEnabled && this.hitCountdown >= 0 && this.hitCountdown <= 2;
    }

    public boolean isMovementAllowed() {
        if (!this.neuralAttackEnabled) {
            return true;
        }
        if (this.postAttackCooldown > 0) {
            return false;
        }
        return this.hitCountdown < 0 || this.hitCountdown > Math.max(1, this.predictedCooldown);
    }

    public Rotation computeSilentRotation(Rotation from, Rotation to) {
        NeuroModel model = NeuroModel.getActive();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (model == null || mc.player == null) {
            return null;
        }
        float gcd = Math.max(3.0f, RotationUtil.getRotationStep());
        if (Math.abs(MathHelper.wrapDegrees(to.getYaw() - from.getYaw())) <= gcd && Math.abs(to.getPitch() - from.getPitch()) <= gcd) {
            this.silentRotationEngine.reset();
            this.settleTicks = 0;
            return null;
        }
        if (++this.settleTicks > 30) {
            this.silentRotationEngine.reset();
            return null;
        }
        if (!this.silentRotationEngine.isInitialized()) {
            this.silentRotationEngine.init(model, from.getYaw(), from.getPitch(), to.getYaw(), to.getPitch());
        }
        if (!this.silentRotationEngine.solve(model, from.getYaw(), from.getPitch(), to.getYaw(), to.getPitch(),
                5.0f, 15.0f, 3.0, 0.0f, 0, AuraHumanStyle.getInstance().getTemperature(), 2, 2.0f, this.rotationOutput)) {
            return null;
        }
        this.silentRotationEngine.incrementHitStreak();
        return new Rotation(
                from.getYaw() + this.rotationOutput[0],
                MathHelper.clamp(from.getPitch() + this.rotationOutput[1], -90.0f, 90.0f)
        );
    }

    public static double distanceToBox(Vec3d eyePos, Box box) {
        double dx = Math.max(Math.max(box.minX - eyePos.x, 0.0), eyePos.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eyePos.y, 0.0), eyePos.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eyePos.z, 0.0), eyePos.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float getSpeedFactor() {
        float slack = AuraHumanStyle.getInstance().getSlack();
        return this.humanErrorEnabled ? slack : slack * 0.5f;
    }

    private void resetState() {
        this.neuralAttackEnabled = false;
        this.refreshCountdown = 0;
        this.hitCountdown = -1;
        this.postAttackCooldown = 0;
        this.predictedPostDelay = 0;
        this.predictedCooldown = 0;
    }

    @Override
    public void enabled() {
        NeuroModel.invalidate();
        this.rotationEngine.reset();
        this.silentRotationEngine.reset();
        this.lastTargetId = -1;
        this.resetState();
    }

    @Override
    public void rotate(RotationManager rotationManager, float attackRange, boolean raycastMode, boolean rayTrace, RotationApplyMode applyMode, LivingEntity target) {
        this.neuralAttackEnabled = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return;
        }
        NeuroModel model = NeuroModel.getActive();
        if (model == null) {
            rotationManager.applyRotation(RotationUtil.getRotationToVector(target.getBoundingBox().getCenter()), applyMode, 180.0f, 180.0f, 180.0f, RotationPriority.TO_TARGET);
            return;
        }

        Box box = target.getBoundingBox();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d diff = box.getCenter().subtract(eyePos);
        double horizDist = Math.max(Math.hypot(diff.x, diff.z), 0.05);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizDist)));
        float halfYaw = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthX() / 2.0, horizDist)), 0.5f);
        float halfPitch = Math.max((float) Math.toDegrees(Math.atan2(box.getLengthY() / 2.0, horizDist)), 0.5f);
        double distToBox = distanceToBox(eyePos, box);

        Rotation current = rotationManager.getTargetRotation();
        if (target.getId() != this.lastTargetId || !this.rotationEngine.isInitialized()) {
            this.rotationEngine.init(model, current.getYaw(), current.getPitch(), targetYaw, targetPitch);
            this.lastTargetId = target.getId();
        }

        this.settleTicks = 0;
        this.rotationEngine.setInputState(PlayerStateSnapshot.capture(mc.player, target));

        if (--this.refreshCountdown <= 0) {
            this.reachDistance = this.getSpeedFactor() * model.sampleErrorCurve(ThreadLocalRandom.current().nextFloat());
            this.refreshCountdown = model.getHold();
        }

        float temp = AuraHumanStyle.getInstance().getTemperature();
        float speed = AuraHumanStyle.getInstance().getSpeed();

        if (!this.rotationEngine.solve(model, current.getYaw(), current.getPitch(), targetYaw, targetPitch,
                halfYaw, halfPitch, distToBox, this.reachDistance, 12, temp, 2, speed, this.rotationOutput)) {
            return;
        }

        this.neuralAttackEnabled = model.isCombatModel();
        rotationManager.applyRotation(
                new Rotation(current.getYaw() + this.rotationOutput[0], MathHelper.clamp(current.getPitch() + this.rotationOutput[1], -90.0f, 90.0f)),
                applyMode, 180.0f, 180.0f, 180.0f, RotationPriority.TO_TARGET
        );

        if (this.pendingAttack) {
            this.pendingAttack = false;
            this.rotationEngine.resetHitStreak();
        } else {
            this.rotationEngine.incrementHitStreak();
        }
    }

    public boolean isNeuralAttackActive() {
        return this.neuralAttackEnabled && NeuroModel.getActive() != null;
    }

    @Override
    public void targetNull() {
        this.rotationEngine.reset();
        this.lastTargetId = -1;
        this.pendingAttack = false;
        this.resetState();
    }

    @Override
    public void attack() {
        this.pendingAttack = true;
        this.hitCountdown = -1;
        ++this.attackCount;
        this.postAttackCooldown = this.predictedPostDelay;
    }
}
