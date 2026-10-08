package rainy.fun.module.settings;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean initialValue) {
        super(name, initialValue);
    }

    public void toggle() {
        setValue(!getValue());
    }
}
