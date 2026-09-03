package fun.newrar.module.api.settings.impl;

import fun.newrar.module.api.Module;
import fun.newrar.module.api.settings.Setting;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.animation.satoshi.Animation;

import java.util.function.Supplier;

public class ColorSetting extends Setting<Integer> {
    private Integer cachedValue;

    public boolean pickerOpen = false;

    public Animation pickerAnim = new EaseInOutQuad(220, 1);

    public ColorSetting(Module parent, String name) {
        super(parent, name, ColorUtil.RED);
        cachedValue = ColorUtil.RED;
    }

    public ColorSetting(Module parent, String name, Integer value) {
        super(parent, name, value);
        cachedValue = value;
    }

    @Override
    public ColorSetting set(Integer value) {
        ColorSetting set = (ColorSetting) super.set(value);
        this.cachedValue = super.getValue();
        return set;
    }

    @Override
    public ColorSetting setVisible(Supplier<Boolean> value) {
        return (ColorSetting) super.setVisible(value);
    }

    @Override
    public ColorSetting onAction(Runnable action) {
        return (ColorSetting) super.onAction(() -> {
            action.run();
            this.cachedValue = super.getValue();
        });
    }

    @Override
    public ColorSetting onSetVisible(Runnable action) {
        return (ColorSetting) super.onSetVisible(action);
    }

    @Override
    public Integer getValue() {
        if (cachedValue == null) {
            cachedValue = super.getValue();
        }
        return cachedValue;
    }
}

