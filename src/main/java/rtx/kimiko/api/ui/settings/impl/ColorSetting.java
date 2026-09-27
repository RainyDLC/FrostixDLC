package rtx.kimiko.api.ui.settings.impl;

import org.jetbrains.annotations.NotNull;
import rtx.kimiko.api.ui.settings.Setting;

public class ColorSetting implements Setting {
    private final rtx.kimiko.api.modules.settings.impl.ColorSetting backend;

    public ColorSetting(@NotNull rtx.kimiko.api.modules.settings.impl.ColorSetting backend) {
        this.backend = backend;
    }

    @NotNull
    @Override
    public String name() {
        return this.backend.getName();
    }

    @Override
    public float height() {
        return 14.0f;
    }

    @Override
    public void render(float x, float y, float w, float alpha) {
    }

    @Override
    public boolean click(float x, float y, float w, float mx, float my) {
        return false;
    }

    @Override
    public boolean isVisible() {
        return this.backend.isVisible();
    }
}
