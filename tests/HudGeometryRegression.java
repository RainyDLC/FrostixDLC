package dev.hatek.client.module.impl.render.interf;

/** Standalone geometry regression: run without Minecraft or Fabric. */
public final class HudGeometryRegression {
    public static void main(String[] args) {
        check(20, HudGeometry.visibleRows(600, 230, 18, 10), "normal viewport");
        check(10, HudGeometry.visibleRows(600, 230, 36, 10), "2x scale");
        check(1, HudGeometry.visibleRows(600, 572, 18, 10), "one row exactly");
        check(0, HudGeometry.visibleRows(600, 573, 18, 10), "not enough room");
        check(0, HudGeometry.visibleRows(600, 800, 18, 10), "dragged below screen");
        check(0, HudGeometry.visibleRows(600, 10, 0, 10), "zero row height");
        check(0, HudGeometry.visibleRows(600, Float.NaN, 18, 10), "invalid position");
        System.out.println("HUD geometry: 7 checks passed");
    }

    private static void check(int expected, int actual, String context) {
        if (expected != actual) throw new AssertionError(context + ": " + actual + " != " + expected);
    }
}
