package rtx.kimiko.api.combat.neuro;

import net.minecraft.util.math.MathHelper;
import rtx.kimiko.utils.combat.PlayerStateSnapshot;
import rtx.kimiko.utils.math.rotation.RotationUtil;

import java.util.concurrent.ThreadLocalRandom;

public class NeuroAimSolver {
    public float hitStreak = 20.0f;
    public float yawError = 5.0f;
    public float pitchError = 15.0f;
    public double distance = 3.0;
    public PlayerStateSnapshot inputState = PlayerStateSnapshot.EMPTY;

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
    public float[] hiddenState;
    public float prevTargetYaw;
    public float prevTargetPitch;
    public float[] lastOutput;

    public float biasYaw;
    public float biasPitch;
    public float holdYaw;
    public float holdPitch;
    public float prevBiasYaw;
    public float prevBiasPitch;

    private float[] buildFeatures(NeuroModel model, float targetDeltaYaw, float targetDeltaPitch) {
        return new float[]{
                asinhNorm(this.errorYaw),
                asinhNorm(this.errorPitch),
                asinhNorm(MathHelper.wrapDegrees(this.errorYaw - this.prevErrorYaw)),
                asinhNorm(this.errorPitch - this.prevErrorPitch),
                asinhNorm(targetDeltaYaw),
                asinhNorm(targetDeltaPitch),
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
                this.idleTicks / (float) model.getFreezeCut(),
                MathHelper.clamp(this.inputState.attackCooldown(), 0.0f, 1.0f),
                this.inputState.onGround() ? 1.0f : 0.0f,
                this.inputState.inWater() ? 1.0f : 0.0f,
                this.inputState.sprinting() ? 1.0f : 0.0f,
                this.inputState.jumpInput() ? 1.0f : 0.0f,
                this.inputState.sneakInput() ? 1.0f : 0.0f,
                asinhNorm(this.inputState.velocityY() * 5.0f),
                asinhNorm(this.inputState.fallDistance()),
                (float) MathHelper.clamp(this.inputState.hurtTime(), 0, 10) / 10.0f,
                MathHelper.clamp(this.inputState.targetHealth(), 0.0f, 40.0f) / 20.0f
        };
    }

    public boolean step(NeuroModel model, float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                        float halfWidth, float halfHeight, double dist, float reachError, int freezeLimit,
                        float temp, int samples, float speed, float memory, float memory2, float[] output) {
        if (model == null) {
            return false;
        }
        if (this.hiddenState == null) {
            this.init(model, currentYaw, currentPitch, targetYaw, targetPitch);
        }

        this.yawError = halfWidth;
        this.pitchError = halfHeight;
        this.distance = dist;

        float gcd = RotationUtil.getRotationStep();
        float targetDeltaYaw = MathHelper.wrapDegrees(targetYaw - this.prevTargetYaw);
        float targetDeltaPitch = targetPitch - this.prevTargetPitch;
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;

        float diffYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float diffPitch = targetPitch - currentPitch;

        float[] forwardOut = model.forward(this.buildFeatures(model, targetDeltaYaw, targetDeltaPitch), this.hiddenState);
        this.lastOutput = forwardOut;

        float bestStepYaw = 0.0f;
        float bestStepPitch = 0.0f;
        float nextBiasYaw = this.biasYaw;
        float nextBiasPitch = this.biasPitch;
        float nextHoldYaw = this.holdYaw;
        float nextHoldPitch = this.holdPitch;

        boolean dequant = model.isDequantized();
        boolean forceStep = this.idleTicks >= (float) Math.min(freezeLimit, model.getFreezeCut());

        if (forceStep || ThreadLocalRandom.current().nextFloat() >= NeuroModel.sigmoid(forwardOut[0])) {
            float noiseScale = (float) Math.sqrt(Math.max(0.0f, 1.0f - memory * memory - memory2 * memory2));
            float minLoss = Float.MAX_VALUE;
            float g1 = nextGaussian();
            float g2 = nextGaussian();

            for (int s = samples; s > 0; --s) {
                int mixIdx = 1 + 6 * model.sampleMixture(forwardOut, ThreadLocalRandom.current().nextFloat());
                float correlation = NeuroModel.boundedTanh(forwardOut[mixIdx + 5]);
                float orthogonal = (float) Math.sqrt(Math.max(0.0f, 1.0f - correlation * correlation));

                float bYaw = g1;
                float bPitch = correlation * g1 + orthogonal * g2;
                bYaw = memory * this.biasYaw + memory2 * this.prevBiasYaw + noiseScale * bYaw;
                bPitch = memory * this.biasPitch + memory2 * this.prevBiasPitch + noiseScale * bPitch;

                float stepY = model.computeStep(forwardOut[mixIdx + 1], forwardOut[mixIdx + 3], true, bYaw, temp, speed);
                float stepP = model.computeStep(forwardOut[mixIdx + 2], forwardOut[mixIdx + 4], false, bPitch, temp, speed);

                if (dequant) {
                    stepY += this.holdYaw;
                    stepP += this.holdPitch;
                }

                float qYaw = quantize(stepY, gcd);
                float qPitch = quantize(stepP, gcd);

                float loss = Math.abs((float) Math.hypot(
                        MathHelper.wrapDegrees(diffYaw - qYaw) / this.yawError,
                        (diffPitch - qPitch) / this.pitchError
                ) - reachError);

                if (loss < minLoss) {
                    minLoss = loss;
                    bestStepYaw = qYaw;
                    bestStepPitch = qPitch;
                    nextBiasYaw = bYaw;
                    nextBiasPitch = bPitch;
                    nextHoldYaw = stepY - qYaw;
                    nextHoldPitch = stepP - qPitch;
                }
            }

            this.prevBiasYaw = this.biasYaw;
            this.prevBiasPitch = this.biasPitch;
            this.biasYaw = nextBiasYaw;
            this.biasPitch = nextBiasPitch;
            if (dequant) {
                this.holdYaw = nextHoldYaw;
                this.holdPitch = nextHoldPitch;
            }
        }

        if (forceStep && bestStepYaw == 0.0f && bestStepPitch == 0.0f) {
            if (Math.abs(diffYaw) >= Math.abs(diffPitch)) {
                bestStepYaw = Math.copySign(gcd, diffYaw);
            } else {
                bestStepPitch = Math.copySign(gcd, diffPitch);
            }
        }

        this.idleTicks = (bestStepYaw == 0.0f && bestStepPitch == 0.0f) ? this.idleTicks + 1.0f : 0.0f;

        float clampedPitch = MathHelper.clamp(currentPitch + bestStepPitch, -90.0f, 90.0f);
        bestStepPitch = clampedPitch - currentPitch;

        this.prevDeltaYaw = this.deltaYaw;
        this.prevDeltaPitch = this.deltaPitch;
        this.deltaYaw = bestStepYaw;
        this.deltaPitch = bestStepPitch;

        this.prevErrorYaw = this.errorYaw;
        this.prevErrorPitch = this.errorPitch;
        this.errorYaw = MathHelper.wrapDegrees(targetYaw - (currentYaw + bestStepYaw));
        this.errorPitch = targetPitch - clampedPitch;
        this.onTarget = Math.abs(this.errorYaw) <= this.yawError && Math.abs(this.errorPitch) <= this.pitchError;

        output[0] = bestStepYaw;
        output[1] = bestStepPitch;
        return true;
    }

    public boolean solve(NeuroModel model, float currentYaw, float currentPitch, float targetYaw, float targetPitch,
                         float halfWidth, float halfHeight, double dist, float reachError, int freezeLimit,
                         float temp, int samples, float speed, float[] output) {
        return this.step(model, currentYaw, currentPitch, targetYaw, targetPitch, halfWidth, halfHeight, dist,
                reachError, freezeLimit, temp, samples, speed,
                model == null ? 0.0f : model.getMemory(),
                model == null ? 0.0f : model.getMemory2(), output);
    }

    public boolean sampleDurations(NeuroModel model, int[] durations) {
        if (this.lastOutput == null || !model.isCombatModel()) {
            return false;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        durations[0] = model.sampleGranular(this.lastOutput, rnd.nextFloat());
        durations[1] = model.sampleFine(this.lastOutput, rnd.nextFloat());
        durations[2] = model.sampleCoarse(this.lastOutput, rnd.nextFloat());
        return true;
    }

    private static float asinhNorm(float x) {
        return (float) (Math.log((double) x + Math.sqrt((double) (x * x) + 1.0)) / 3.0);
    }

    private static float nextGaussian() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }

    private static float quantize(float val, float step) {
        return (float) Math.round(val / step) * step;
    }

    public void init(NeuroModel model, float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        this.hiddenState = model.newHiddenState();
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;
        this.idleTicks = 0.0f;
        this.prevDeltaPitch = 0.0f;
        this.prevDeltaYaw = 0.0f;
        this.deltaPitch = 0.0f;
        this.deltaYaw = 0.0f;
        this.errorYaw = this.prevErrorYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        this.errorPitch = this.prevErrorPitch = targetPitch - currentPitch;
    }

    public void reset() {
        this.hiddenState = null;
        this.idleTicks = 0.0f;
        this.inputState = PlayerStateSnapshot.EMPTY;
        this.lastOutput = null;
        this.prevBiasPitch = 0.0f;
        this.prevBiasYaw = 0.0f;
        this.biasPitch = 0.0f;
        this.biasYaw = 0.0f;
        this.holdPitch = 0.0f;
        this.holdYaw = 0.0f;
    }

    public boolean isInitialized() {
        return this.hiddenState != null;
    }

    public void setInputState(PlayerStateSnapshot snapshot) {
        this.inputState = snapshot == null ? PlayerStateSnapshot.EMPTY : snapshot;
    }

    public void resetHitStreak() {
        this.hitStreak = 0.0f;
    }

    public void incrementHitStreak() {
        this.hitStreak = Math.min(this.hitStreak + 1.0f, 20.0f);
    }

    public boolean isOnTarget() {
        return this.onTarget;
    }
}
