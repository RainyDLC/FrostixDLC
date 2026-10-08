package rainy.fun.module;

import rainy.fun.module.settings.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int keyCode;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() { }

    protected void onDisable() { }

    protected void onTick() { }

    final void tickIfEnabled() {
        if (enabled) onTick();
    }

    protected final <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public final String getName() { return name; }

    public final String getDescription() { return description; }

    public final ModuleCategory getCategory() { return category; }

    public final List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public final boolean isEnabled() { return enabled; }

    public final int getKeyCode() { return keyCode; }

    public final void setKeyCode(int keyCode) { this.keyCode = keyCode; }
}
