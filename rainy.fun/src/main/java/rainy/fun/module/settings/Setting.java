package rainy.fun.module.settings;

import java.util.Objects;

public abstract class Setting<T> {
    private final String name;
    private T value;

    protected Setting(String name, T initialValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = Objects.requireNonNull(initialValue, "initialValue");
    }

    public final String getName() { return name; }

    public final T getValue() { return value; }

    public final void setValue(T value) {
        this.value = validate(Objects.requireNonNull(value, "value"));
    }

    protected T validate(T value) { return value; }
}
