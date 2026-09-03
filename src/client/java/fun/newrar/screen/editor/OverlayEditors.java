package fun.newrar.screen.editor;

import fun.newrar.screen.CrosshairEditor;
import fun.newrar.screen.HandsEditor;
import fun.newrar.screen.PreviewEditor;

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

