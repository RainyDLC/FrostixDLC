package fun.newrar.module.impl.display.interfaceimpl;

import net.minecraft.util.Identifier;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.theme.ThemeColor;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.RollingText;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WaterMark implements element {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Identifier LOGO_TEXTURE = Identifier.of("client", "textures/icon.png");

    private float s = 1.0F;
    private float text = 5.5F;
    private float icon = 5F;
    private float logo = 6F;
    private float h = 16F;
    private float radius = 5F;
    private float iconX = 6F;
    private float iconY = 5.5F;
    private float textX = 14F;
    private float textY = 4.4F;
    private float block = 15F;
    private float afterSep = 7F;
    private float afterIcon = 9F;

    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);

    private int lastFps = Integer.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        s = InterFace.getInstance().sizeHud.getValue() * 1.1F;
        text = 5.5F * s;
        icon = 5F * s;
        logo = 6F * s;
        h = 16F * s;
        radius = 5F * s;
        iconX = 6F * s;
        iconY = 5.5F * s;
        textX = 14F * s;
        textY = 4.4F * s;
        block = 15F * s;
        afterSep = 7F * s;
        afterIcon = 9F * s;

        float opacity = InterFace.getInstance().alphaHUD.getValue();

        int pings = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                pings = entry.getLatency();
            }
        }

        int fps = mc.getCurrentFps();
        if (fps != lastFps) {
            fpsText.set(String.valueOf(fps));
            lastFps = fps;
        }
        if (pings != lastPing) {
            pingText.set(String.valueOf(pings));
            lastPing = pings;
        }

        if (interFace.watermarkMode != null && interFace.watermarkMode.is("Островок")) {
            renderIsland(dragSetting, x, y, opacity);
        } else {
            renderClassic(dragSetting, x, y, opacity);
        }
    }

    private void renderIsland(DragSetting dragSetting, float x, float y, float opacity) {
        Font fontRegular = Fonts.sf_medium;
        Font fontBold = Fonts.sf_bold;

        float islandH = 16.5F * s;
        float pillRadius = islandH / 2.0F;

        String time = LocalTime.now().format(TIME_FORMATTER);
        float timeTextSize = text * 1.05F;
        float timeW = fontBold.getWidth(time, timeTextSize);
        float timeY = y + (islandH - fontBold.getHeight(timeTextSize)) / 2.0F - 0.5F * s;

        fontBold.draw(time, x, timeY, timeTextSize, ThemeColor.getTextColor());

        float islandStartX = x + timeW + 7.5F * s;
        float padX = 6.0F * s;
        float logoSize = 6.5F * s;

        String brand = "RainyDLC";
        float brandW = fontBold.getWidth(brand, text);
        float fpsW = fpsText.width(fontRegular, text) + fontRegular.getWidth("fps", text);
        float pingW = pingText.width(fontRegular, text) + fontRegular.getWidth("ms", text);
        float sepW = fontRegular.getWidth(" • ", text);

        float innerContentW = logoSize + 4.0F * s + brandW + sepW + fpsW + sepW + pingW;
        float islandW = padX * 2.0F + innerContentW;

        RenderUtil.Render2D.hudPlate(islandStartX, y, islandW, islandH, 1, pillRadius, opacity);

        float curX = islandStartX + padX;

        RenderUtil.Images.texture(LOGO_TEXTURE, curX, y + (islandH - logoSize) / 2.0F, logoSize, logoSize, ColorUtil.getClientColor(1));
        curX += logoSize + 4.0F * s;

        float currentTextY = y + (islandH - fontRegular.getHeight(text)) / 2.0F - 0.5F * s;
        fontBold.draw(brand, curX, currentTextY, text, ThemeColor.getTextColor());
        curX += brandW;

        fontRegular.draw(" • ", curX, currentTextY, text, ThemeColor.getSeparatorColor());
        curX += sepW;
        curX += drawValue(fontRegular, fpsText, "fps", curX, currentTextY);

        fontRegular.draw(" • ", curX, currentTextY, text, ThemeColor.getSeparatorColor());
        curX += sepW;
        drawValue(fontRegular, pingText, "ms", curX, currentTextY);

        float iconSize = 7.0F * s;
        float iconStartX = islandStartX + islandW + 6.5F * s;
        float iconStartY = y + (islandH - iconSize) / 2.0F;

        drawSilentBell(iconStartX, iconStartY, iconSize, ThemeColor.getTextColor(), ColorUtil.getClientColor(1));

        float totalW = (iconStartX + iconSize + 2F * s) - x;
        dragSetting.size.set(totalW, islandH);
    }

    private void renderClassic(DragSetting dragSetting, float x, float y, float opacity) {
        Font fonts = Fonts.sf_medium;

        RenderUtil.Render2D.hudPlate(x, y, h, h, 1, radius, opacity);

        float logoS = Math.min(h - 6F * s, logo * 1.2F);
        RenderUtil.Images.texture(LOGO_TEXTURE,
                x + (h - logoS) / 2F, y + (h - logoS) / 2F, logoS, logoS, ColorUtil.getClientColor(1));

        x += 18.5F * s;

        String user = (mc.player != null && mc.player.getName() != null)
                ? mc.player.getName().getString()
                : (mc.getSession() != null ? mc.getSession().getUsername() : "User");
        float fpsW = fpsText.width(fonts, text) + fonts.getWidth("fps", text);
        float pingW = pingText.width(fonts, text) + fonts.getWidth("ms", text);

        float w = block + fonts.getWidth(user, text);
        float w2 = 57F * s + fonts.getWidth(user, text) + fpsW + pingW;

        RenderUtil.Render2D.hudPlate(x, y, w2, h, 1, radius, opacity);

        Fonts.rainydlc_2.draw("N", x + iconX, y + iconY, icon, ColorUtil.getClientColor(1));
        fonts.draw(user, x + textX, y + textY, text, ThemeColor.getTextColor());

        float x2 = x + w + 2 * s;
        Fonts.icon.draw("C", x2, y + iconY, icon, ThemeColor.getSeparatorColor());
        x2 += afterSep;
        Fonts.rainydlc_2.draw("C", x2, y + iconY, icon, ThemeColor.getHudColor());
        x2 += afterIcon;
        drawValue(fonts, fpsText, "fps", x2, y + textY);

        float x3 = x + w + 20F * s + fpsW;
        Fonts.icon.draw("C", x3, y + iconY, icon, ThemeColor.getSeparatorColor());
        x3 += afterSep;
        Fonts.rainydlc_2.draw("S", x3, y + iconY, icon, ThemeColor.getHudColor());
        x3 += afterIcon;
        drawValue(fonts, pingText, "ms", x3, y + textY);

        dragSetting.size.set(18.5F * s + w2, h);
    }

    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, text, ThemeColor.getTextColor());
        float width = value.width(font, text);
        font.draw(suffix, x + width, y, text, ColorUtil.getColor(200));
        return width + font.getWidth(suffix, text);
    }

    private void drawSilentBell(float x, float y, float size, int bellColor, int slashColor) {
        float cx = x + size / 2.0F;

        float loopW = size * 0.22F;
        float loopH = size * 0.14F;
        Draw.rect(cx - loopW / 2.0F, y + size * 0.04F, loopW, loopH, bellColor, loopH / 2.0F);

        float domeW = size * 0.44F;
        float domeH = size * 0.36F;
        Draw.rect(cx - domeW / 2.0F, y + size * 0.16F, domeW, domeH, bellColor, domeW / 2.0F, domeW / 2.0F, 0, 0);

        float flareW = size * 0.62F;
        float flareH = size * 0.28F;
        Draw.rect(cx - flareW / 2.0F, y + size * 0.44F, flareW, flareH, bellColor, size * 0.08F);

        float rimW = size * 0.76F;
        float rimH = size * 0.12F;
        Draw.rect(cx - rimW / 2.0F, y + size * 0.68F, rimW, rimH, bellColor, rimH / 2.0F);

        float clapperW = size * 0.20F;
        float clapperH = size * 0.14F;
        Draw.rect(cx - clapperW / 2.0F, y + size * 0.78F, clapperW, clapperH, bellColor, 0, 0, clapperW / 2.0F, clapperW / 2.0F);

        float x0 = x + size * 0.08F;
        float y0 = y + size * 0.06F;
        float x1 = x + size * 0.92F;
        float y1 = y + size * 0.94F;

        float thick = Math.max(1.1F, size * 0.14F);
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            float px = x0 + (x1 - x0) * t;
            float py = y0 + (y1 - y0) * t;
            Draw.rect(px - thick / 2.0F, py - thick / 2.0F, thick, thick, slashColor, thick / 2.0F);
        }
    }
}
