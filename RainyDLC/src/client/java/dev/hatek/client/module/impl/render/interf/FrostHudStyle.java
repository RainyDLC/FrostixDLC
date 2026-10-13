package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Frosted graphite cards: separate from ClickGUI's larger geometry. */
final class FrostHudStyle {
    static final int TEXT = 0xFFF0F5FA;
    static final int MUTED = 0xFFAFBFCE;
    static final int HUD_PANEL = 0xEA111B24;
    static final int HUD_MARGIN = 10;
    static final int HUD_CARD_R = 7;
    static final int HUD_PAD = 8;
    static final int HUD_HEADER_H = 25;
    static final int HUD_ICON = 11;
    static final int HUD_ICON_X = 8;
    static final int HUD_ICON_Y = 7;
    static final int HUD_TITLE_X = 25;
    static final int HUD_TITLE_BASELINE = 17;
    static final int HUD_ROW_H = 17;
    static final int HUD_BOTTOM_PAD = 6;
    static final int HUD_MIN_W = 132;
    static final int HUD_BADGE_H = 14;
    static final int HUD_BADGE_R = 3;
    static final int HUD_BADGE_PAD = 5;
    static final int HUD_STUB_W = 2;
    static final int HUD_STUB_H = 7;
    static final int WM_H = 26;
    static final int WM_R = 7;
    static final int WM_PAD = 9;
    static final int WM_BASELINE = 18;
    static final int WM_SEP_W = 1;
    static final int WM_SEP_H = 9;
    static final int WM_SEP_GAP = 8;
    static final int WM_UNIT_GAP = 3;

    private FrostHudStyle() {}

    static int accent() { return 0xFF8BD5EF; }

    static void panel(GuiGraphicsExtractor gg, float w, float h, float s) {
        float r = HUD_CARD_R * s;
        // Low-cost layered rim, no copied shaders or resources from the reference.
        Render2D.round(gg, 0, 0, w, h, r, 0x603F5E71);
        Render2D.round(gg, s, s, w - 2 * s, h - 2 * s, Math.max(0, r - s), HUD_PANEL);
    }
}
