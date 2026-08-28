package ru.white.module.api.preview;

import ru.white.module.api.Module;
import ru.white.screen.PreviewEditor;

public interface ModulePreview {
    PreviewSettings previewSettings();

    default boolean isPreviewActive() {
        return this instanceof Module module && PreviewEditor.getInstance().isEditing(module);
    }

    void previewSpawn(PreviewContext ctx);

    default boolean previewNeedsDummy() {
        return false;
    }

    default boolean previewControlsDummy() {
        return false;
    }

    default void previewStart(PreviewContext ctx) {
    }

    default void previewTick(PreviewContext ctx) {
    }

    default void previewStop() {
    }
}
