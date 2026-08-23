package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.network.ServerInfo;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

/**
 * Верхняя плашка второго вида худа — как на референсе:
 * [лого] Ник • 233 FPS • 12:34 • x y z • 51 MS • адрес сервера
 */
public class WaterMarkTwo implements element {

    /** Общий масштаб плашки. */
    private static float S = 1.0F;

    private static float TEXT = 5.5F * S;
    private static float ICON = 5F * S;
    private static float H = 16F * S;
    private static float RADIUS = 5F * S;

    private static float PAD_X = 6F * S;
    private static float ICON_Y = 5.5F * S;
    private static float TEXT_Y = 4.4F * S;
    private static float SEP_GAP = 7F * S;

    /** Время сессии считается от загрузки класса. */
    private static final long SESSION_START = System.currentTimeMillis();

    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);
    private final RollingText coordX = new RollingText(3F);
    private final RollingText coordY = new RollingText(3F);
    private final RollingText coordZ = new RollingText(3F);

    private int lastFps = Integer.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;

    private String cachedServer = "";
    private String cachedTime = "";

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {

        S = InterFace.getInstance().sizeHud.getValue() * 1.1F;
        TEXT = 5.5F * S;
        ICON = 5F * S;
        H = 16F * S;
        RADIUS = 5F * S;
        PAD_X = 6F * S;
        ICON_Y = 5.5F * S;
        TEXT_Y = 4.4F * S;
        SEP_GAP = 7F * S;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        Font font = Fonts.sf_medium;
        Font glyphs = Fonts.rainydlc_2;

        float opacity = InterFace.getInstance().alphaHUD.getValue();

        // кэш значений
        int fps = mc.getCurrentFps();
        if (fps != lastFps) { fpsText.set(String.valueOf(fps)); lastFps = fps; }

        int pings = 0;
        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                pings = entry.getLatency();
            }
        }
        if (pings != lastPing) { pingText.set(String.valueOf(pings)); lastPing = pings; }

        int xi = (int) mc.player.getX();
        int yi = (int) mc.player.getY();
        int zi = (int) mc.player.getZ();
        if (xi != lastX) { coordX.set(String.valueOf(xi)); lastX = xi; }
        if (yi != lastY) { coordY.set(String.valueOf(yi)); lastY = yi; }
        if (zi != lastZ) { coordZ.set(String.valueOf(zi)); lastZ = zi; }

        String server = resolveServer();

        String nick = mc.getSession().getUsername();

        // время сессии HH:mm
        long elapsed = (System.currentTimeMillis() - SESSION_START) / 1000L;
        String time = String.format("%02d:%02d", elapsed / 3600, elapsed / 60 % 60);
        if (!time.equals(cachedTime)) cachedTime = time;

        // ---- расчёт ширины ----
        float w = PAD_X;                      // левый отступ
        w += glyphs.getWidth("N", ICON) + 8F * S;   // лого
        w += font.getWidth(nick, TEXT);

        w += SEP_GAP;                          // точка-разделитель
        w += font.getWidth(time, TEXT);

        w += SEP_GAP;
        w += fpsText.width(font, TEXT) + font.getWidth("FPS", TEXT);

        w += SEP_GAP;
        w += coordX.width(font, TEXT) + coordY.width(font, TEXT) + coordZ.width(font, TEXT)
                + 6F * S;

        w += SEP_GAP;
        w += pingText.width(font, TEXT) + font.getWidth("MS", TEXT);

        w += SEP_GAP;
        w += font.getWidth(server, TEXT);
        w += PAD_X;                            // правый отступ

        RenderUtil.Render2D.hudPlate(x, y, w, H, 1, RADIUS, opacity);

        float cursor = x + PAD_X;

        Fonts.rainydlc_2.draw("N", cursor, y + ICON_Y, ICON, ColorUtil.getClientColor(1));
        cursor += glyphs.getWidth("N", ICON) + 8F * S;

        font.draw(nick, cursor, y + TEXT_Y, TEXT, ThemeColor.getTextColor());
        cursor += font.getWidth(nick, TEXT);

        cursor = drawDot(cursor, y);
        font.draw(time, cursor, y + TEXT_Y, TEXT, ColorUtil.getColor(200));
        cursor += font.getWidth(time, TEXT);

        cursor = drawDot(cursor, y);
        cursor += drawValue(font, fpsText, "FPS", cursor, y + TEXT_Y);

        cursor = drawDot(cursor, y);
        float coordGap = 3F * S;
        coordX.draw(font, cursor, y + TEXT_Y, TEXT, ColorUtil.getColor(200));
        coordY.draw(font, cursor + coordX.width(font, TEXT) + coordGap, y + TEXT_Y, TEXT, ColorUtil.getColor(200));
        coordZ.draw(font, cursor + coordX.width(font, TEXT) + coordY.width(font, TEXT) + coordGap * 2F,
                y + TEXT_Y, TEXT, ColorUtil.getColor(200));
        cursor += coordX.width(font, TEXT) + coordY.width(font, TEXT) + coordZ.width(font, TEXT) + coordGap * 2F;

        cursor = drawDot(cursor, y);
        cursor += drawValue(font, pingText, "MS", cursor, y + TEXT_Y);

        cursor = drawDot(cursor, y);
        font.draw(server, cursor, y + TEXT_Y, TEXT, ColorUtil.getColor(200));

        dragSetting.size.set(w, H);
    }

    /** Точка-разделитель между блоками. */
    private float drawDot(float cursor, float y) {
        float d = 1.6F * S;
        RenderUtil.Render2D.rect(cursor + SEP_GAP / 2F - d / 2F, y + H / 2F - d / 2F, d, d,
                ThemeColor.getHudColor(), d / 2F);
        return cursor + SEP_GAP;
    }

    private String resolveServer() {
        ServerInfo info = mc.getCurrentServerEntry();
        String server = info == null || info.address == null ? "Одиночная игра" : info.address;
        if (!server.equals(cachedServer)) cachedServer = server;
        return cachedServer;
    }

    /**
     * Число рисуется прокруткой, подпись — обычным текстом.
     */
    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, TEXT, ThemeColor.getTextColor());

        float width = value.width(font, TEXT);

        font.draw(suffix, x + width, y, TEXT, ColorUtil.getColor(200));

        return width + font.getWidth(suffix, TEXT);
    }
}
