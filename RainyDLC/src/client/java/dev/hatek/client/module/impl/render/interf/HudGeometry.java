package dev.hatek.client.module.impl.render.interf;

/** Viewport budgeting shared by measurement and hit testing. No game state required. */
final class HudGeometry {
    private HudGeometry() {}

    static int visibleRows(float screenHeight, float top, float rowHeight, float margin) {
        if (!Float.isFinite(screenHeight) || !Float.isFinite(top)
                || !Float.isFinite(rowHeight) || rowHeight <= 0) return 0;
        return Math.max(0, (int) ((screenHeight - top - margin) / rowHeight));
    }
}
