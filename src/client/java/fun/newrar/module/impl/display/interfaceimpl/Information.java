package fun.newrar.module.impl.display.interfaceimpl;

import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.theme.ThemeColor;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.ServerUtil;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.RollingText;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;

public class Information implements element {

    private static final String SEPARATOR = " ; ";

    private float s = 1.0F;
    private float text = 5.5F;
    private float icon = 4.5F;
    private float h = 16F;
    private float radius = 5F;
    private float iconX = 6F;
    private float iconY = 4.4F;
    private float textX = 14F;
    private float textY = 4.4F;
    private float block = 15F;
    private float afterSep = 7F;
    private float afterIcon = 9F;

    private final RollingText coordX = new RollingText(3F);
    private final RollingText coordY = new RollingText(3F);
    private final RollingText coordZ = new RollingText(3F);
    private final RollingText bpsText = new RollingText(3F);
    private final RollingText tpsText = new RollingText(3F);

    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private int lastBpsTenths = Integer.MIN_VALUE;
    private int lastTpsTenths = Integer.MIN_VALUE;

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        s = InterFace.getInstance().sizeHud.getValue() * 1.1F;
        text = 5.5F * s;
        icon = 4.5F * s;
        h = 16F * s;
        radius = 5F * s;
        iconX = 6F * s;
        iconY = 4.4F * s;
        textX = 14F * s;
        textY = 4.4F * s;
        block = 15F * s;
        afterSep = 7F * s;
        afterIcon = 9F * s;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        double dx = mc.player.getX() - mc.player.lastX;
        double dz = mc.player.getZ() - mc.player.lastZ;
        float bps = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0F);

        Font fonts = Fonts.sf_regular;

        int xi = (int) mc.player.getX();
        if (xi != lastX) {
            coordX.set(String.valueOf(xi));
            lastX = xi;
        }
        int yi = (int) mc.player.getY();
        if (yi != lastY) {
            coordY.set(String.valueOf(yi));
            lastY = yi;
        }
        int zi = (int) mc.player.getZ();
        if (zi != lastZ) {
            coordZ.set(String.valueOf(zi));
            lastZ = zi;
        }

        int bpsT = Math.round(bps * 10F);
        if (bpsT != lastBpsTenths) {
            bpsText.set(String.format(java.util.Locale.US, "%.1f", bpsT / 10F));
            lastBpsTenths = bpsT;
        }
        int tpsT = Math.round(ServerUtil.TPS * 10F);
        if (tpsT != lastTpsTenths) {
            tpsText.set(String.format(java.util.Locale.US, "%.1f", tpsT / 10F));
            lastTpsTenths = tpsT;
        }

        float coordsW = coordX.width(fonts, text) + coordY.width(fonts, text) + coordZ.width(fonts, text)
                + fonts.getWidth("xyz", text) + fonts.getWidth(SEPARATOR, text) * 2;

        float bpsW = bpsText.width(fonts, text) + fonts.getWidth("bps", text);
        float tpsW = tpsText.width(fonts, text) + fonts.getWidth("tps", text);

        float w = block + coordsW;
        float w2 = 57F * s + coordsW + bpsW + tpsW;

        RenderUtil.Render2D.hudPlate(x, y, w2, h, 1, radius, InterFace.getInstance().alphaHUD.getValue());

        Fonts.rainydlc_2.draw("W", x + iconX, y + iconY, icon, ColorUtil.getClientColor(1));

        float cursor = x + textX;
        cursor += drawValue(fonts, coordX, "x", cursor, y + textY);
        cursor += drawSeparator(fonts, cursor, y + textY);
        cursor += drawValue(fonts, coordY, "y", cursor, y + textY);
        cursor += drawSeparator(fonts, cursor, y + textY);
        cursor += drawValue(fonts, coordZ, "z", cursor, y + textY);

        float x2 = x + w + 2 * s;
        Fonts.icon.draw("C", x2, y + iconY, icon, ThemeColor.getSeparatorColor());
        x2 += afterSep;
        Fonts.rainydlc_2.draw("H", x2, y + iconY, icon, ThemeColor.getHudColor());
        x2 += afterIcon;
        drawValue(fonts, bpsText, "bps", x2, y + textY);

        float x3 = x + w + 20F * s + bpsW;
        Fonts.icon.draw("C", x3, y + iconY, icon, ThemeColor.getSeparatorColor());
        x3 += afterSep;
        Fonts.rainydlc_2.draw("V", x3, y + iconY, icon, ThemeColor.getHudColor());
        x3 += afterIcon;
        drawValue(fonts, tpsText, "tps", x3, y + textY);

        dragSetting.size.set(w2, h);
    }

    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, text, ThemeColor.getTextColor());
        float width = value.width(font, text);
        font.draw(suffix, x + width, y, text, ColorUtil.getColor(220));
        return width + font.getWidth(suffix, text);
    }

    private float drawSeparator(Font font, float x, float y) {
        font.draw(SEPARATOR, x, y, text, ThemeColor.getDarkTextColor());
        return font.getWidth(SEPARATOR, text);
    }
}
