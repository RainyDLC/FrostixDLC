package rtx.kimiko.utils.math.rotation;

public class RotationRequest {
    public RotationMode mode = RotationMode.SMOOTH;
    public Rotation rotation;
    public RotationApplyMode config;
    public float yawStep;
    public float pitchStep;
    public int priority;
    public float pitchSpeed;
    public boolean smooth;
    public RotationStepFunction stepFunction;

    public RotationRequest(Rotation rotation, float yawStep, float pitchStep, long timeout, int priority) {
        this(rotation, RotationApplyMode.NONE, yawStep, pitchStep, 180.0f, priority, true);
    }

    public RotationRequest(Rotation rotation, RotationApplyMode config, float yawStep, float pitchStep, float pitchSpeed, int priority, boolean smooth) {
        this.rotation = rotation;
        this.config = config;
        this.yawStep = yawStep;
        this.pitchStep = pitchStep;
        this.priority = priority;
        this.pitchSpeed = pitchSpeed;
        this.smooth = smooth;
    }

    public RotationRequest(Rotation rotation, RotationApplyMode config, float yawStep, float pitchStep, float pitchSpeed, int priority) {
        this(rotation, config, yawStep, pitchStep, pitchSpeed, priority, true);
    }

    public RotationMode getMode() {
        return this.mode;
    }

    public void setMode(RotationMode mode) {
        this.mode = mode;
    }

    public boolean isSmooth() {
        return this.smooth;
    }

    public int getPriority() {
        return this.priority;
    }

    public RotationStepFunction getStepFunction() {
        return this.stepFunction;
    }

    public void setStepFunction(RotationStepFunction stepFunction) {
        this.stepFunction = stepFunction;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public RotationApplyMode getConfig() {
        return this.config;
    }

    public float getYawStep() {
        return this.yawStep;
    }

    public float getPitchStep() {
        return this.pitchStep;
    }

    public float getPitchSpeed() {
        return this.pitchSpeed;
    }

    public void setPitchSpeed(float pitchSpeed) {
        this.pitchSpeed = pitchSpeed;
    }
}
