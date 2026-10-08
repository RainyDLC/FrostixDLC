package rainy.fun.module.settings;

import java.util.List;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String initialValue, List<String> modes) {
        super(name, initialValue);
        this.modes = List.copyOf(modes);
        if (this.modes.isEmpty() || !this.modes.contains(initialValue)) {
            throw new IllegalArgumentException("Initial mode must be present in modes");
        }
    }

    @Override
    protected String validate(String value) {
        if (!modes.contains(value)) {
            throw new IllegalArgumentException("Unsupported mode: " + value);
        }
        return value;
    }

    public List<String> getModes() { return modes; }

    public void selectNext() {
        int index = modes.indexOf(getValue());
        setValue(modes.get((index + 1) % modes.size()));
    }
}
