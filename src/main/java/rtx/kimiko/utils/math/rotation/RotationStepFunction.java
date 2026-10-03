package rtx.kimiko.utils.math.rotation;

@FunctionalInterface
public interface RotationStepFunction {
    Rotation returnStep(Rotation from, Rotation to);
}
