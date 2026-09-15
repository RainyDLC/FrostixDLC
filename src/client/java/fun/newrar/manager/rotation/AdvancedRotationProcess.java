package fun.newrar.manager.rotation;

import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.utils.aura.GCDUtil;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class AdvancedRotationProcess extends Component {
    public static RotationTask currentTask = RotationTask.IDLE;
    public static int  currentPriority;
    public static int  currentTimeout;
    public static int  idleTicks;
    public static Rotation targetRotation;

    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;

    public static float shakeYaw;
    public static float shakePitch;

    public static float resetShakeAmplitudeYaw;
    public static float resetShakeAmplitudePitch;
    public static int   shakeDuration;
    private static int  resetShakeTicks;

    private static final Random RNG = new Random();

    public static boolean isRotating() {
        return !currentTask.equals(RotationTask.IDLE);
    }

    @EventHandler
    public void onEvent(EventTick event) {
        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
            currentTask = RotationTask.RESET;
            resetShakeTicks = 0;
        }

        if (currentTask.equals(RotationTask.RESET)) {
            resetShakeTicks++;
            doResetRotation();
        }

        idleTicks++;
    }

    private void doResetRotation() {
        Rotation target = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);

        float sYaw = 0f, sPitch = 0f;
        if (shakeDuration > 0 && resetShakeTicks <= shakeDuration) {
            float fade = 1f - (float) resetShakeTicks / shakeDuration;
            sYaw   = (RNG.nextFloat() * 2f - 1f) * resetShakeAmplitudeYaw   * fade;
            sPitch = (RNG.nextFloat() * 2f - 1f) * resetShakeAmplitudePitch * fade;
        }

        boolean reached = updateRotation(target, currentYawReturnSpeed, currentPitchReturnSpeed, sYaw, sPitch);

        if (reached && (shakeDuration <= 0 || resetShakeTicks > shakeDuration)) {
            stopRotation();
        }
    }

    public static void update(AdvancedRotationConfig cfg) {
        if (currentPriority > cfg.priority) return;

        if (currentTask.equals(RotationTask.IDLE) && !cfg.clientRotation) {
            FreeLookUtil.active = true;
        }

        currentYawSpeed         = cfg.yawSpeed;
        currentPitchSpeed       = cfg.pitchSpeed;
        currentYawReturnSpeed   = cfg.yawReturnSpeed;
        currentPitchReturnSpeed = cfg.pitchReturnSpeed;
        currentTimeout          = cfg.timeout;
        currentPriority         = cfg.priority;
        shakeYaw                    = cfg.shakeYaw;
        shakePitch                  = cfg.shakePitch;
        resetShakeAmplitudeYaw      = cfg.resetShakeAmplitudeYaw;
        resetShakeAmplitudePitch    = cfg.resetShakeAmplitudePitch;
        shakeDuration               = cfg.shakeDuration;
        currentTask             = RotationTask.AIM;
        targetRotation          = cfg.target;

        updateRotation(cfg.target, cfg.yawSpeed, cfg.pitchSpeed, cfg.shakeYaw, cfg.shakePitch);
    }

    public static void update(Rotation target, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(new AdvancedRotationConfig(target)
                .speed(turnSpeed, returnSpeed)
                .timeout(timeout)
                .priority(priority));
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed,
                              float yawReturnSpeed, float pitchReturnSpeed,
                              int timeout, int priority, boolean clientRotation) {
        update(new AdvancedRotationConfig(target)
                .speed(yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed)
                .timeout(timeout)
                .priority(priority)
                .clientRotation(clientRotation));
    }

    public static void resetParentTimeout() {
        currentTimeout = 0;
        currentTask    = RotationTask.IDLE;
        currentPriority = 0;
        FreeLookUtil.setActive(false);
    }

    static boolean updateRotation(Rotation targetRot, float yawSpeed, float pitchSpeed,
                                   float sYaw, float sPitch) {
        if (mc.player == null) return false;

        Rotation current = new Rotation(mc.player);

        float yawDelta   = wrapDegrees(targetRot.yaw - current.yaw);
        float pitchDelta = targetRot.pitch - current.pitch;

        float clampedYaw   = Math.min(Math.abs(yawDelta),   yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);

        float deltaYaw = GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)) + sYaw;
        float deltaPitch = GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)) + sPitch;

        float newYaw = mc.player.getYaw() + deltaYaw;
        float newPitch = MathHelper.clamp(mc.player.getPitch() + deltaPitch, -90f, 90f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.headYaw = newYaw;
        mc.player.bodyYaw = newYaw;

        idleTicks = 0;
        return new Rotation(mc.player).getDelta(targetRot) < 1f;
    }

    public void stopRotation() {
        currentTask     = RotationTask.IDLE;
        currentPriority = 0;
        FreeLookUtil.setActive(false);
    }

    public enum RotationTask { AIM, RESET, IDLE }

    public static class AdvancedRotationConfig {
        public Rotation target;
        public float yawSpeed         = 180f;
        public float pitchSpeed       = 180f;
        public float yawReturnSpeed   = 180f;
        public float pitchReturnSpeed = 180f;

        public float shakeYaw                = 0f;
        public float shakePitch              = 0f;

        public float resetShakeAmplitudeYaw  = 0f;
        public float resetShakeAmplitudePitch= 0f;
        public int   shakeDuration           = 0;
        public int   timeout          = 1;
        public int   priority         = 0;
        public boolean clientRotation = false;

        public AdvancedRotationConfig(Rotation target) {
            this.target = target;
        }

        public AdvancedRotationConfig speed(float turnSpeed, float returnSpeed) {
            this.yawSpeed         = turnSpeed;
            this.pitchSpeed       = turnSpeed;
            this.yawReturnSpeed   = returnSpeed;
            this.pitchReturnSpeed = returnSpeed;
            return this;
        }

        public AdvancedRotationConfig speed(float yawSpeed, float pitchSpeed,
                                             float yawReturnSpeed, float pitchReturnSpeed) {
            this.yawSpeed         = yawSpeed;
            this.pitchSpeed       = pitchSpeed;
            this.yawReturnSpeed   = yawReturnSpeed;
            this.pitchReturnSpeed = pitchReturnSpeed;
            return this;
        }

        public AdvancedRotationConfig shake(float yaw, float pitch) {
            this.shakeYaw   = yaw;
            this.shakePitch = pitch;
            return this;
        }

        public AdvancedRotationConfig resetShake(float amplitudeYaw, float amplitudePitch, int durationTicks) {
            this.resetShakeAmplitudeYaw   = amplitudeYaw;
            this.resetShakeAmplitudePitch = amplitudePitch;
            this.shakeDuration            = durationTicks;
            return this;
        }

        public AdvancedRotationConfig timeout(int ticks) {
            this.timeout = ticks;
            return this;
        }

        public AdvancedRotationConfig priority(int p) {
            this.priority = p;
            return this;
        }

        public AdvancedRotationConfig clientRotation(boolean v) {
            this.clientRotation = v;
            return this;
        }
    }
}

