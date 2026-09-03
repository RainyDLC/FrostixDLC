package fun.newrar.theme;

import fun.newrar.utils.colors.ColorUtil;

public final class ThemeColor {
    private ThemeColor() {}

    public static int getVisualColor() {
        return ColorUtil.client();
    }

    public static int getHudColor() {
        return ColorUtil.client();
    }

    public static int getHudColor(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.client(),alpha);
    }

    public static int getTextColor() {
        return ColorUtil.getColor(255);
    }

    public static int getDarkTextColor() {
        return ColorUtil.getColor(175);
    }

    public static int getBackgroundColor() {
        return ThemeManager.get().getActive().get(Theme.BACKGROUND);
    }

    public static int getOutlineColor() {
        return ThemeManager.get().getActive().get(Theme.OUTLINE);
    }

    public static int getSeparatorColor() {
        return ColorUtil.getColor(255,0.1F);
    }

    public static int getLightBackgroundColor() {
        return ThemeManager.get().getActive().get(Theme.LIGHT_BG);
    }

    public static int byIndex(int index) {
        return ThemeManager.get().getActive().get(index);
    }

    public static float getOpacity() {
        return ThemeManager.get().getActive().getSlider(Theme.OPACITY);
    }

    public static int getBlur() {
        return Math.round(ThemeManager.get().getActive().getSlider(Theme.BLUR));
    }

    public static boolean getShadow() {
        return ThemeManager.get().getActive().getBool(Theme.SHADOW);
    }

    public static boolean getDistortion() {
        return ThemeManager.get().getActive().getBool(Theme.DISTORTION);
    }

    public static int getSeparatorColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.SEPARATOR),alpha);
    }

    public static int getTextColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.TEXT),alpha);
    }
    public static int getLightBackgroundColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.LIGHT_BG),alpha);
    }
    public static int getOutlineColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.OUTLINE),alpha);
    }

    public static int getDarkTextColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.TEXT2),alpha);
    }

    public static int getBackgroundColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.BACKGROUND),alpha);
    }
}

