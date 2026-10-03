package rtx.kimiko.api.modules.impl.Utils.neuro;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.utils.math.MathUtils;

/**
 * Stateful aim solver driving one GRU cell per tick. Ported from the Rockstar
 * neuro aim: it predicts a small yaw/pitch delta each tick by sampling the
 * model's mixture-density head, keeping the movement human-like (variable
 * speed, overshoot, idle ticks) instead of snapping instantly onto the target.
 */
public final class NeuroAimSolver {

    private static final int FEATURES = 27;

    public float hitStreak = 20.0f;
    public float yawError = 5.0f;
    public float pitchError = 15.0f;
    public double distance = 3.0;
    public AimStateSnapshot inputState = AimStateSnapshot.EMPTY;

    public float errorYaw;
    public float errorPitch;
    public float prevErrorYaw;
    public float prevErrorPitch;
    public float deltaYaw;
    public float deltaPitch;
    public float prevDeltaYaw;
    public float prevDeltaPitch;
    public boolean onTarget;
    public float idleTicks;

    @Nullable
    private float[] hiddenState;
    private float prevTargetYaw;
    private float prevTargetPitch;
    @Nullable
    private float[] lastOutput;
    private float biasYaw;
    private float biasPitch;
    private float holdYaw;
    private float holdPitch;
    private float prevBiasYaw;
    private float prevBiasPitch;

    public void init(NeuroModel model, float yaw, float pitch, float targetYaw, float targetPitch) {
        this.hiddenState = model.newHiddenState();
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;
        this.idleTicks = 0.0f;
        this.deltaYaw = 0.0f;
        this.deltaPitch = 0.0f;
        this.prevDeltaYaw = 0.0f;
        this.prevDeltaPitch = 0.0f;
        this.errorYaw = this.prevErrorYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        this.errorPitch = this.prevErrorPitch = targetPitch - pitch;
    }

    public void reset() {
        this.hiddenState = null;
        this.idleTicks = 0.0f;
        this.inputState = AimStateSnapshot.EMPTY;
        this.lastOutput = null;
        this.biasYaw = 0.0f;
        this.biasPitch = 0.0f;
        this.prevBiasYaw = 0.0f;
        this.prevBiasPitch = 0.0f;
        this.holdYaw = 0.0f;
        this.holdPitch = 0.0f;
    }

    public boolean isInitialized() {
        return this.hiddenState != null;
    }

    public void setInputState(@Nullable AimStateSnapshot state) {
        this.inputState = state == null ? AimStateSnapshot.EMPTY : state;
    }

    public boolean isOnTarget() {
        return this.onTarget;
    }

    public void incrementHitStreak() {
        this.hitStreak = Math.min(this.hitStreak + 1.0f, 20.0f);
    }

    public void resetHitStreak() {
        this.hitStreak = 0.0f;
    }

    /**
     * Runs one inference step.
     *
     * @param out float[2] receiving {deltaYaw, deltaPitch} in degrees
     * @return false when no model is available
     */
    public boolean solve(@Nullable NeuroModel model,
                         float yaw, float pitch,
                         float targetYaw, float targetPitch,
                         float yawErrorWindow, float pitchErrorWindow,
                         double distanceToTarget,
                         float reachError,
                         int freezeCut,
                         float fovLimit,
                         int samples,
                         float speedFactor,
                         float[] out) {
        if (model == null) {
            return false;
        }
        if (this.hiddenState == null) {
            this.init(model, yaw, pitch, targetYaw, targetPitch);
        }
        if (this.hiddenState == null) {
            return false;
        }

        this.yawError = yawErrorWindow;
        this.pitchError = pitchErrorWindow;
        this.distance = distanceToTarget;

        float rotationStep = (float) MathUtils.computeGcd();
        float targetYawDelta = MathHelper.wrapDegrees(targetYaw - this.prevTargetYaw);
        float targetPitchDelta = targetPitch - this.prevTargetPitch;
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;

        float remainingYaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float remainingPitch = targetPitch - pitch;

        float[] output = model.forward(this.buildFeatures(model, targetYawDelta, targetPitchDelta), this.hiddenState);
        this.lastOutput = output;

        float stepYaw = 0.0f;
        float stepPitch = 0.0f;
        float nextBiasYaw = this.biasYaw;
        float nextBiasPitch = this.biasPitch;
        float nextHoldYaw = this.holdYaw;
        float nextHoldPitch = this.holdPitch;

        boolean frozen = this.idleTicks >= (float) Math.min(freezeCut, model.getFreezeCut());
        boolean sample = !frozen && ThreadLocalRandom.current().nextFloat() < NeuroModel.sigmoid(output[0]);

        if (sample) {
            float corr = (float) Math.sqrt(Math.max(0.0f, 1.0f - model.getMemory() * model.getMemory() - model.getMemory2() * model.getMemory2()));
            float bestError = Float.MAX_VALUE;
            float noiseYaw = nextGaussian();
            float noisePitch = nextGaussian();
            for (int i = samples; i > 0; i--) {
                int base = 1 + 6 * model.sampleMixture(output, ThreadLocalRandom.current().nextFloat());
                float rho = NeuroModel.boundedTanh(output[base + 5]);
                float spread = (float) Math.sqrt(Math.max(0.0f, 1.0f - rho * rho));
                float coupledYaw = model.getMemory() * this.biasYaw + model.getMemory2() * this.prevBiasYaw + corr * noiseYaw;
                float coupledPitch = model.getMemory() * this.biasPitch + model.getMemory2() * this.prevBiasPitch + corr * (rho * noiseYaw + spread * noisePitch);

                float candidateYaw = model.computeStep(output[base + 1], output[base + 3], true, coupledYaw, speedFactor, fovLimit);
                float candidatePitch = model.computeStep(output[base + 2], output[base + 4], false, coupledPitch, speedFactor, fovLimit);
                if (model.isDequantized()) {
                    candidateYaw += this.holdYaw;
                    candidatePitch += this.holdPitch;
                }
                float quantYaw = quantize(candidateYaw, rotationStep);
                float quantPitch = quantize(candidatePitch, rotationStep);

                float score = Math.abs((float) Math.hypot(
                        MathHelper.wrapDegrees(remainingYaw - quantYaw) / this.yawError,
                        (remainingPitch - quantPitch) / this.pitchError) - reachError);
                if (score < bestError) {
                    bestError = score;
                    stepYaw = quantYaw;
                    stepPitch = quantPitch;
                    nextBiasYaw = coupledYaw;
                    nextBiasPitch = coupledPitch;
                    nextHoldYaw = candidateYaw - quantYaw;
                    nextHoldPitch = candidatePitch - quantPitch;
                }
            }
            this.prevBiasYaw = this.biasYaw;
            this.prevBiasPitch = this.biasPitch;
            this.biasYaw = nextBiasYaw;
            this.biasPitch = nextBiasPitch;
            if (model.isDequantized()) {
                this.holdYaw = nextHoldYaw;
                this.holdPitch = nextHoldPitch;
            }
        }

        // Nudge out of a stall so the aim never deadlocks on the target.
        if (frozen && stepYaw == 0.0f && stepPitch == 0.0f) {
            if (Math.abs(remainingYaw) >= Math.abs(remainingPitch)) {
                stepYaw = Math.copySign(rotationStep, remainingYaw);
            } else {
                stepPitch = Math.copySign(rotationStep, remainingPitch);
            }
        }

        this.idleTicks = stepYaw == 0.0f && stepPitch == 0.0f ? this.idleTicks + 1.0f : 0.0f;

        float nextPitch = MathHelper.clamp(pitch + stepPitch, -90.0f, 90.0f);
        stepPitch = nextPitch - pitch;

        this.prevDeltaYaw = this.deltaYaw;
        this.prevDeltaPitch = this.deltaPitch;
        this.deltaYaw = stepYaw;
        this.deltaPitch = stepPitch;
        this.prevErrorYaw = this.errorYaw;
        this.prevErrorPitch = this.errorPitch;
        this.errorYaw = MathHelper.wrapDegrees(targetYaw - (yaw + stepYaw));
        this.errorPitch = targetPitch - nextPitch;
        this.onTarget = Math.abs(this.errorYaw) <= this.yawError && Math.abs(this.errorPitch) <= this.pitchError;

        out[0] = stepYaw;
        out[1] = stepPitch;
        return true;
    }

    /** Combat-model only: predicts the human-like attack gate timings. */
    public boolean sampleDurations(@Nullable NeuroModel model, int[] out) {
        if (this.lastOutput == null || model == null || !model.isCombatModel()) {
            return false;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        out[0] = model.sampleGranular(this.lastOutput, random.nextFloat());
        out[1] = model.sampleFine(this.lastOutput, random.nextFloat());
        out[2] = model.sampleCoarse(this.lastOutput, random.nextFloat());
        return true;
    }

    private float[] buildFeatures(NeuroModel model, float targetYawDelta, float targetPitchDelta) {
        int freezeCut = Math.max(1, model.getFreezeCut());
        return new float[]{
                asinhNorm(this.errorYaw),
                asinhNorm(this.errorPitch),
                asinhNorm(MathHelper.wrapDegrees(this.errorYaw - this.prevErrorYaw)),
                asinhNorm(this.errorPitch - this.prevErrorPitch),
                asinhNorm(targetYawDelta),
                asinhNorm(targetPitchDelta),
                asinhNorm(this.deltaYaw),
                asinhNorm(this.deltaPitch),
                asinhNorm(this.prevDeltaYaw),
                asinhNorm(this.prevDeltaPitch),
                asinhNorm(this.errorYaw / this.yawError),
                asinhNorm(this.errorPitch / this.pitchError),
                (float) Math.log(Math.max(this.distance, 0.05) + 0.5) / 2.0f,
                (float) Math.log(this.yawError) / 3.0f,
                this.onTarget ? 1.0f : 0.0f,
                this.hitStreak / 20.0f,
                this.idleTicks / freezeCut,
                AimStateSnapshot.clamp01(this.inputState.attackCooldown()),
                this.inputState.onGround() ? 1.0f : 0.0f,
                this.inputState.inWater() ? 1.0f : 0.0f,
                this.inputState.sprinting() ? 1.0f : 0.0f,
                this.inputState.jumpInput() ? 1.0f : 0.0f,
                this.inputState.sneakInput() ? 1.0f : 0.0f,
                asinhNorm(this.inputState.velocityY() * 5.0f),
                asinhNorm(this.inputState.fallDistance()),
                MathHelper.clamp(this.inputState.hurtTime(), 0, 10) / 10.0f,
                MathHelper.clamp(this.inputState.targetHealth(), 0.0f, 40.0f) / 20.0f
        };
    }

    private static float asinhNorm(float value) {
        return (float) (Math.log(value + Math.sqrt(value * value + 1.0)) / 3.0);
    }

    private static float nextGaussian() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }

    private static float quantize(float value, float step) {
        if (step <= 0.0f) {
            return value;
        }
        return Math.round(value / step) * step;
    }
}
