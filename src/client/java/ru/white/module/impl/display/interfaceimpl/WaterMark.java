package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WaterMark implements element {

    /** Общий масштаб плашки: один множитель на шрифты, иконки и все отступы. */
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

    /** Цифры прокручиваются при смене значения — как таймеры в Potions. */
    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);

    private static final Identifier LOGO_TEXTURE = Identifier.of("client", "textures/icon.png");

    // кэш значений — строки пересобираются только при изменении
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

        // 1. Время слева от островка
        String time = LocalTime.now().format(TIME_FORMATTER);
        float timeTextSize = TEXT * 1.05F;
        float timeW = fontBold.getWidth(time, timeTextSize);
        float timeY = y + (islandH - fontBold.getHeight(timeTextSize)) / 2.0F - 0.5F * S;

        fontBold.draw(time, x, timeY, timeTextSize, ThemeColor.getTextColor());

        // 2. Островок (Pill)
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

        // Рендер капсулы-островка
        RenderUtil.Render2D.hudPlate(islandStartX, y, islandW, islandH, 1, pillRadius, opacity);

        // Внутренние элементы островка
        float curX = islandStartX + padX;

        // Фирменная R иконка клиента (аккуратный размер)
        RenderUtil.Images.texture(LOGO_TEXTURE, curX, y + (islandH - logoSize) / 2.0F, logoSize, logoSize, ColorUtil.getClientColor(1));
        curX += logoSize + 4.0F * S;

        // Название клиента
        float textY = y + (islandH - fontRegular.getHeight(TEXT)) / 2.0F - 0.5F * S;
        fontBold.draw(brand, curX, textY, TEXT, ThemeColor.getTextColor());
        curX += brandW;

        // Разделитель + FPS
        fontRegular.draw(" • ", curX, textY, TEXT, ThemeColor.getSeparatorColor());
        curX += sepW;
        curX += drawValue(fontRegular, fpsText, "fps", curX, textY);

        // Разделитель + Ping
        fontRegular.draw(" • ", curX, textY, TEXT, ThemeColor.getSeparatorColor());
        curX += sepW;
        drawValue(fontRegular, pingText, "ms", curX, textY);

        // 3. Иконка статуса справа от островка (Не беспокоить / Do Not Disturb)
        float iconStartX = islandStartX + islandW + 6.5F * S;
        float iconY = y + (islandH - ICON) / 2.0F + 0.5F * S;
        Fonts.rainydlc_2.draw("M", iconStartX, iconY, ICON, ColorUtil.getClientColor(1));

        float totalW = (iconStartX + ICON + 2F * S) - x;
        dragSetting.size.set(totalW, islandH);
    }

    private void renderClassic(DragSetting dragSetting, float x, float y, float opacity) {
        Font fonts = Fonts.sf_medium;

        // бейдж-логотип
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

    /**
     * Число рисуется прокруткой, подпись — обычным текстом: RollingText выводит символы по
     * одному, и цветовой код внутри строки вылез бы буквами.
     */
    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, TEXT, ThemeColor.getTextColor());
        float width = value.width(font, TEXT);
        font.draw(suffix, x + width, y, TEXT, ColorUtil.getColor(200));
        return width + font.getWidth(suffix, TEXT);
    }
}
