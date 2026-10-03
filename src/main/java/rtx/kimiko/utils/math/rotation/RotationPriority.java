package rtx.kimiko.utils.math.rotation;

public enum RotationPriority {
    NOT_IMPORTANT(-2),
    NORMAL(0),
    TO_TARGET(2),
    USE_ITEM(4),
    OVERRIDE(5),
    MAX(6);

    private final int weight;

    RotationPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return this.weight;
    }
}
