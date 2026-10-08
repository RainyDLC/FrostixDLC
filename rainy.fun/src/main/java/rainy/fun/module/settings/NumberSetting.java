package rainy.fun.module.settings;

public final class NumberSetting extends Setting<Double> {
    private final double minimum;
    private final double maximum;
    private final double step;

    public NumberSetting(String name, double initialValue, double minimum, double maximum, double step) {
        super(name, initialValue);
        if (minimum > maximum || step <= 0.0) {
            throw new IllegalArgumentException("Invalid number setting bounds");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        setValue(initialValue);
    }

    @Override
    protected Double validate(Double value) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        double steps = Math.round((clamped - minimum) / step);
        return Math.max(minimum, Math.min(maximum, minimum + steps * step));
    }

    public double getMinimum() { return minimum; }

    public double getMaximum() { return maximum; }

    public double getStep() { return step; }
}
