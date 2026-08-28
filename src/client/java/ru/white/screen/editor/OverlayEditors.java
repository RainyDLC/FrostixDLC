package ru.white.screen.editor;

import ru.white.screen.CrosshairEditor;
import ru.white.screen.HandsEditor;
import ru.white.screen.PreviewEditor;

public final class OverlayEditors {
    private static final OverlayEditor[] ALL = {
            HandsEditor.getInstance(),
            PreviewEditor.getInstance(),
            CrosshairEditor.getInstance()
    };

    private OverlayEditors() {
    }

    public static OverlayEditor[] all() {
        return ALL;
    }

    public static OverlayEditor active() {
        for (OverlayEditor editor : ALL) {
            if (editor.isActive()) return editor;
        }
        return null;
    }

    public static boolean anyActive() {
        return active() != null;
    }

    public static void closeAll() {
        for (OverlayEditor editor : all()) {
            if (editor.isActive()) editor.closeFromMenuRemoval();
        }
    }
}
