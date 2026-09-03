package fun.newrar.module.api.preview;

import fun.newrar.module.api.Module;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.DelimiterSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.screen.Menu;
import fun.newrar.screen.PreviewEditor;
import fun.newrar.utils.annotation.IMinecraft;

public final class PreviewSettings {
    public final DelimiterSetting title;
    public final SliderSetting distance;
    public final SliderSetting height;
    public final SliderSetting interval;

    private PreviewSettings(Module parent, Float defaultDistance, Float defaultHeight, Float defaultInterval) {
        boolean anySlider = defaultDistance != null || defaultHeight != null || defaultInterval != null;

        title = !anySlider ? null
                : new DelimiterSetting(parent, "Настройки предпоказа")
                .setVisible(() -> isEditing(parent));

        distance = defaultDistance == null ? null
                : new SliderSetting(parent, "Дистанция спавна", defaultDistance, 1.5F, 14F, 0.5F)
                .setVisible(() -> isEditing(parent));

        height = defaultHeight == null ? null
                : new SliderSetting(parent, "Высота спавна", defaultHeight, -2F, 4F, 0.1F)
                .setVisible(() -> isEditing(parent));

        interval = defaultInterval == null ? null
                : new SliderSetting(parent, "Интервал показа", defaultInterval, 0.5F, 10F, 0.5F)
                .setVisible(() -> isEditing(parent));
    }

    public static PreviewSettings of(Module parent, float distance, float height, float interval) {
        return new PreviewSettings(parent, distance, height, interval);
    }

    public static PreviewSettings withoutInterval(Module parent, float distance, float height) {
        return new PreviewSettings(parent, distance, height, null);
    }

    public static PreviewSettings intervalOnly(Module parent, float interval) {
        return new PreviewSettings(parent, null, null, interval);
    }

    public static PreviewSettings none(Module parent) {
        return new PreviewSettings(parent, null, null, null);
    }

    public boolean repeats() {
        return interval != null;
    }

    public long intervalMs() {
        return interval == null ? 0L : (long) (interval.getValue() * 1000F);
    }

    public float distance(float fallback) {
        return distance == null ? fallback : distance.getValue();
    }

    public float height(float fallback) {
        return height == null ? fallback : height.getValue();
    }

    private static boolean isEditing(Module parent) {
        return PreviewEditor.getInstance().isEditing(parent);
    }

    public static ButtonSetting button(Module parent) {
        return new ButtonSetting(parent, "Предпоказ", () -> {
            if (IMinecraft.mc.currentScreen instanceof Menu menu) {
                menu.openPreviewEditor(parent);
            }
        }).setVisible(() -> !isEditing(parent));
    }
}

