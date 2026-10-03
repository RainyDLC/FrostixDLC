package rtx.kimiko.utils.math.rotation;

public enum RotationApplyMode {
    NONE(false, false, false, false, false),
    DIRECT(true, true, false, false, true),
    STRICT(true, true, false, false, false),
    SILENT(true, true, true, false, false),
    SMOOTH_SILENT(true, true, false, true, false),
    CHANGE_LOOK(true, true, false, false, true),
    TARGETED(true, true, false, false, false);

    private final boolean movementCorrection;
    private final boolean jumpCorrection;
    private final boolean silent;
    private final boolean bodyLock;
    private final boolean movesClientView;

    RotationApplyMode(boolean movementCorrection, boolean jumpCorrection, boolean silent, boolean bodyLock, boolean movesClientView) {
        this.movementCorrection = movementCorrection;
        this.jumpCorrection = jumpCorrection;
        this.silent = silent;
        this.bodyLock = bodyLock;
        this.movesClientView = movesClientView;
    }

    public boolean correctsMovement() {
        return this.movementCorrection;
    }

    public boolean correctsJump() {
        return this.jumpCorrection;
    }

    public boolean locksBody() {
        return this.bodyLock;
    }

    public boolean isSilent() {
        return this.silent;
    }

    public boolean movesClientView() {
        return this.movesClientView;
    }
}
