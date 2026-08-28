package ru.white.module.api.settings.impl;

import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.utils.animation.Animation;

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
        pressAnim.run(0F, 0.45F, ru.white.utils.animation.Easings.QUAD_OUT);
    }

    @Override
    public ButtonSetting setVisible(Supplier<Boolean> value) {
        return (ButtonSetting) super.setVisible(value);
    }
}
