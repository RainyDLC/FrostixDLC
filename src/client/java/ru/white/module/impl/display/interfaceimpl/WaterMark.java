package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.util.Identifier;

import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WaterMark implements element {
    private static float S = 1.0F;

    private static float TEXT = 5.5F * S;
    private static float ICON = 5F * S;
    private static float LOGO = 6F * S;

    private static float H = 16F * S;
    private static float RADIUS = 5F * S;

    private static float ICON_X = 6F * S;
    private static float ICON_Y = 5.5F * S;
    private static float TEXT_X = 14F * S;
    private static float TEXT_Y = 4.4F * S;

    private static float BLOCK = 15F * S;
    private static float AFTER_SEP = 7F * S;
    private static float AFTER_ICON = 9F * S;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);

    private static final Identifier LOGO_TEXTURE = Identifier.of("client", "textures/icon.png");

    private int lastFps = Integer.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        S = InterFace.getInstance().sizeHud.getValue() * 1.1F;
        TEXT = 5.5F * S;
        ICON = 5F * S;
        LOGO = 6F * S;
        H = 16F * S;
        RADIUS = 5F * S;
        ICON_X = 6F * S;
        ICON_Y = 5.5F * S;
        TEXT_X = 14F * S;
        TEXT_Y = 4.4F * S;
        BLOCK = 15F * S;
        AFTER_SEP = 7F * S;
        AFTER_ICON = 9F * S;

        float opacity = InterFace.getInstance().alphaHUD.getValue();

        int pings = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                pings = entry.getLatency();
            }
        }

        int fps = mc.getCurrentFps();
        if (fps != lastFps) { fpsText.set(String.valueOf(fps)); lastFps = fps; }
        if (pings != lastPing) { pingText.set(String.valueOf(pings)); lastPing = pings; }

        if (interFace.watermarkMode != null && interFace.watermarkMode.is("Островок")) {
            renderIsland(dragSetting, x, y, opacity);
        } else {
            renderClassic(dragSetting, x, y, opacity);
        }
    }

    private void renderIsland(DragSetting dragSetting, float x, float y, float opacity) {
        Font fontRegular = Fonts.sf_medium;
        Font fontBold = Fonts.sf_bold;

        float islandH = 16.5F * S;
        float pillRadius = islandH / 2.0F;

        String time = LocalTime.now().format(TIME_FORMATTER);
        float timeTextSize = TEXT * 1.05F;
        float timeW = fontBold.getWidth(time, timeTextSize);
        float timeY = y + (islandH - fontBold.getHeight(timeTextSize)) / 2.0F - 0.5F * S;

        fontBold.draw(time, x, timeY, timeTextSize, ThemeColor.getTextColor());

        float islandStartX = x + timeW + 7.5F * S;
        float padX = 6.0F * S;
        float logoSize = 6.5F * S;

        String brand = "RainyDLC";
        float brandW = fontBold.getWidth(brand, TEXT);
        float fpsW = fpsText.width(fontRegular, TEXT) + fontRegular.getWidth("fps", TEXT);
        float pingW = pingText.width(fontRegular, TEXT) + fontRegular.getWidth("ms", TEXT);
        float sepW = fontRegular.getWidth(" • ", TEXT);

        float innerContentW = logoSize + 4.0F * S + brandW + sepW + fpsW + sepW + pingW;
        float islandW = padX * 2.0F + innerContentW;

        RenderUtil.Render2D.hudPlate(islandStartX, y, islandW, islandH, 1, pillRadius, opacity);

        float curX = islandStartX + padX;

        RenderUtil.Images.texture(LOGO_TEXTURE, curX, y + (islandH - logoSize) / 2.0F, logoSize, logoSize, ColorUtil.getClientColor(1));
        curX += logoSize + 4.0F * S;

        float textY = y + (islandH - fontRegular.getHeight(TEXT)) / 2.0F - 0.5F * S;
        fontBold.draw(brand, curX, textY, TEXT, ThemeColor.getTextColor());
        curX += brandW;

        fontRegular.draw(" • ", curX, textY, TEXT, ThemeColor.getSeparatorColor());
        curX += sepW;
        curX += drawValue(fontRegular, fpsText, "fps", curX, textY);

        fontRegular.draw(" • ", curX, textY, TEXT, ThemeColor.getSeparatorColor());
        curX += sepW;
        drawValue(fontRegular, pingText, "ms", curX, textY);

        float iconSize = 7.0F * S;
        float iconStartX = islandStartX + islandW + 6.5F * S;
        float iconY = y + (islandH - iconSize) / 2.0F;

        drawSilentBell(iconStartX, iconY, iconSize, ThemeColor.getTextColor(), ColorUtil.getClientColor(1));

        float totalW = (iconStartX + iconSize + 2F * S) - x;
        dragSetting.size.set(totalW, islandH);
    }

    private void renderClassic(DragSetting dragSetting, float x, float y, float opacity) {
        Font fonts = Fonts.sf_medium;

        RenderUtil.Render2D.hudPlate(x, y, H, H, 1, RADIUS, opacity);

        float logoS = Math.min(H - 6F * S, LOGO * 1.2F);
        RenderUtil.Images.texture(LOGO_TEXTURE,
                x + (H - logoS) / 2F, y + (H - logoS) / 2F, logoS, logoS, ColorUtil.getClientColor(1));

        x += 18.5F * S;

        String user = "User";
        float fpsW = fpsText.width(fonts, TEXT) + fonts.getWidth("fps", TEXT);
        float pingW = pingText.width(fonts, TEXT) + fonts.getWidth("ms", TEXT);

        float w = BLOCK + fonts.getWidth(user, TEXT);
        float w2 = 57F * S + fonts.getWidth(user, TEXT) + fpsW + pingW;

        RenderUtil.Render2D.hudPlate(x, y, w2, H, 1, RADIUS, opacity);

        Fonts.rainydlc_2.draw("N", x + ICON_X, y + ICON_Y, ICON, ColorUtil.getClientColor(1));
        fonts.draw(user, x + TEXT_X, y + TEXT_Y, TEXT, ThemeColor.getTextColor());

        float x2 = x + w + 2 * S;
        Fonts.icon.draw("C", x2, y + ICON_Y, ICON, ThemeColor.getSeparatorColor());
        x2 += AFTER_SEP;
        Fonts.rainydlc_2.draw("C", x2, y + ICON_Y, ICON, ThemeColor.getHudColor());
        x2 += AFTER_ICON;
        drawValue(fonts, fpsText, "fps", x2, y + TEXT_Y);

        float x3 = x + w + 20F * S + fpsW;
        Fonts.icon.draw("C", x3, y + ICON_Y, ICON, ThemeColor.getSeparatorColor());
        x3 += AFTER_SEP;
        Fonts.rainydlc_2.draw("S", x3, y + ICON_Y, ICON, ThemeColor.getHudColor());
        x3 += AFTER_ICON;
        drawValue(fonts, pingText, "ms", x3, y + TEXT_Y);

        dragSetting.size.set(18.5F * S + w2, H);
    }

    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, TEXT, ThemeColor.getTextColor());
        float width = value.width(font, TEXT);
        font.draw(suffix, x + width, y, TEXT, ColorUtil.getColor(200));
        return width + font.getWidth(suffix, TEXT);
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
