package fun.newrar.module.api.preview;

import fun.newrar.module.api.Module;
import fun.newrar.screen.PreviewEditor;

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

