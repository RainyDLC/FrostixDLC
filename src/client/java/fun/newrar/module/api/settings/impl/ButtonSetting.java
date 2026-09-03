package fun.newrar.module.api.settings.impl;

import fun.newrar.module.api.Module;
import fun.newrar.module.api.settings.Setting;
import fun.newrar.utils.animation.Animation;

import java.util.function.Supplier;

public class ButtonSetting extends Setting<Boolean> {
    private final Runnable action;

    public final Animation pressAnim = new Animation();

    public ButtonSetting(Module parent, String name, Runnable action) {
        super(parent, name, false);
        this.action = action;
    }

    public void press() {
        if (action != null) action.run();
        pressAnim.set(1F);
        pressAnim.run(0F, 0.45F, fun.newrar.utils.animation.Easings.QUAD_OUT);
    }

    @Override
    public ButtonSetting setVisible(Supplier<Boolean> value) {
        return (ButtonSetting) super.setVisible(value);
    }
}

