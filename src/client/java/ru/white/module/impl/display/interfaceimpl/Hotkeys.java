package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import ru.white.Client;
import ru.white.module.api.Module;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Панель Hotkeys второго вида худа — как на референсе:
 * строка с точкой-индикатором состояния и названием модуля.
 */
public class Hotkeys implements element {

    /**
     * Кэш списка модулей: пересобирается максимум раз в 50 мс.
     */
    private final List<Module> cachedModules = new ArrayList<>();
    private long lastRebuildMs;

    private static float S = 1.0F;

    private static float H = 16F * S;
    private static float MIN_W = 50F * S;
    private static float RADIUS = 5F * S;

    private static float TITLE_TEXT = 7F * S;
    private static float TITLE_ICON = 5F * S;
    private static float ROW_TEXT = 6.5F * S;

    private static float TITLE_ICON_X = 5F * S;
    private static float TITLE_ICON_Y = 5.5F * S;
    private static float TITLE_TEXT_X = 12.5F * S;
    private static float TITLE_TEXT_Y = 3.4F * S;

    private static float ROW_HEIGHT = 14F * S;
    private static float ROW_BASE_W = 27F * S;
    private static float ROW_PADDING_X = 5F * S;
    private static float ROW_START_Y = 5F * S;

    private static float DOT_SIZE = 4F * S;
    private static float DOT_GAP = 4F * S;
    private static float SEP_PADDING_X = 4F * S;
    private static float SEP_OFFSET_Y = 3.2F * S;

    private final EaseInOutQuad openAnim = new EaseInOutQuad(300, 1);
    private final EaseInOutQuad chatAnim = new EaseInOutQuad(300, 1);

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        S = InterFace.getInstance().sizeHud.getValue();
        H = 16F * S;
        MIN_W = 50F * S;
        RADIUS = 5F * S;
        TITLE_TEXT = 7F * S;
        TITLE_ICON = 5F * S;
        ROW_TEXT = 6.5F * S;
        TITLE_ICON_X = 5F * S;
        TITLE_ICON_Y = 5.5F * S;
        TITLE_TEXT_X = 12.5F * S;
        TITLE_TEXT_Y = 3.4F * S;
        ROW_HEIGHT = 14F * S;
        ROW_BASE_W = 27F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        DOT_SIZE = 4F * S;
        DOT_GAP = 4F * S;
        SEP_PADDING_X = 4F * S;
        SEP_OFFSET_Y = 3.2F * S;

        long now = System.currentTimeMillis();
        if (now - lastRebuildMs >= 50L) {
            lastRebuildMs = now;
            cachedModules.clear();
            for (Module m : Client.get().moduleManager().values()) {
                if (m.getKey() > 0
                        && !m.getName().equalsIgnoreCase("ClickGui")
                        && (m.isEnabled() || m.getAnimation().getOutput() > 0)) {
                    cachedModules.add(m);
                }
            }
            cachedModules.sort(Comparator.comparingInt((Module m) -> m.getBigName().length()).reversed());
        }
        List<Module> modules = cachedModules;

        boolean isEmpty = modules.isEmpty();

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);

        openAnim.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        chatAnim.setDirection((mc.currentScreen instanceof ChatScreen) && isEmpty ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;

        float alpha = openAnim.getOutput();

        if (closeCondition && alpha == 0.0F) return;

        float alpha2 = chatAnim.getOutput();

        Font font = Fonts.sf_regular;

        // плашка пустого состояния (в чате)
        RenderUtil.Render2D.hudPlate(x, y, MIN_W, H, alpha2, RADIUS, interFace.alphaHUD.getValue());

        Fonts.rainydlc_2.draw("E", x + TITLE_ICON_X, y + TITLE_ICON_Y, TITLE_ICON,
                ColorUtil.replAlpha(ColorUtil.client(), alpha2));
        font.draw("Hotkeys", x + TITLE_TEXT_X, y + TITLE_TEXT_Y, TITLE_TEXT,
                ColorUtil.multAlpha(ColorUtil.getColor(240), alpha2));

        float w = 0;
        float h = 4 * S;

        for (Module m : modules) {
            m.animation.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
            float mAnim = m.getAnimation().getOutput();
            if (mAnim <= 0) continue;

            float rowW = ROW_BASE_W + DOT_SIZE + DOT_GAP + font.getWidth(m.getBigName(), ROW_TEXT);

            w = Math.max(w, rowW * mAnim);
            h += ROW_HEIGHT * mAnim;
        }

        RenderUtil.Render2D.hudPlate(x, y, w, h, alpha, RADIUS, interFace.alphaHUD.getValue());

        float offsetY = y + ROW_START_Y;
        float offsetY2 = 0;
        boolean firstRow = true;

        for (Module m : modules) {
            float mAnim = m.getAnimation().getOutput();
            if (mAnim <= 0) continue;

            // точка-индикатор: залита цветом темы у включённых, серая у выключенных
            float dotCenterY = offsetY + ROW_TEXT / 2F - 0.6F * S;
            int dotColor = m.isEnabled()
                    ? ThemeColor.getHudColor(alpha * mAnim)
                    : ColorUtil.getColor(140, alpha * mAnim);
            RenderUtil.Render2D.rect(x + ROW_PADDING_X, dotCenterY - DOT_SIZE / 2F,
                    DOT_SIZE, DOT_SIZE, dotColor, DOT_SIZE / 2F);

            font.draw(m.getBigName(), x + ROW_PADDING_X + DOT_SIZE + DOT_GAP, offsetY, ROW_TEXT,
                    ColorUtil.getColor(240, alpha * mAnim));

            // линия сепаратора
            if (!firstRow) {
                RenderUtil.Render2D.rect(x + SEP_PADDING_X, offsetY - SEP_OFFSET_Y,
                        w - SEP_PADDING_X * 2F, 0.5F, ColorUtil.getColor(255, 0.05F * alpha * mAnim), 1);
            }
            firstRow = false;

            offsetY += ROW_HEIGHT * mAnim;
            offsetY2 += ROW_HEIGHT * mAnim;
        }

        dragSetting.size.set(Math.max((int) w, (int) MIN_W), (int) Math.max(offsetY2, H));
    }
}
