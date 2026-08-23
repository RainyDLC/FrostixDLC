package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.*;

import static net.minecraft.client.gui.hud.InGameHud.getEffectTexture;

/**
 * Панель зелий второго вида худа — как на референсе:
 * иконка эффекта, время серым, название белым (вредные — красным).
 */
public class PotionsTwo implements IMinecraft {

    private static float S = 1.0F;

    private static float H = 16F * S;
    private static float MIN_W = 44F * S;
    private static float RADIUS = 5F * S;

    private static float TITLE_TEXT = 7F * S;
    private static float TITLE_ICON = 5F * S;
    private static float ROW_TEXT = 6.5F * S;
    private static float ICON = 7F * S;

    private static float TITLE_ICON_X = 5F * S;
    private static float TITLE_ICON_Y = 5.5F * S;
    private static float TITLE_TEXT_X = 12.5F * S;
    private static float TITLE_TEXT_Y = 3.4F * S;

    private static float ROW_HEIGHT = 14F * S;
    private static float ROW_BASE_W = 27F * S;
    private static float ROW_PADDING_X = 5F * S;
    private static float ROW_START_Y = 5F * S;

    private static float SEP_PADDING_X = 4F * S;
    private static float SEP_OFFSET_Y = 3.2F * S;

    private final EaseInOutQuad openAnim = new EaseInOutQuad(300, 1);
    private final EaseInOutQuad chatAnim = new EaseInOutQuad(300, 1);

    private final Map<String, Row> rows = new LinkedHashMap<>();

    // кэш сортировки — не чаще раза в тик
    private final List<Row> sorted = new ArrayList<>();
    private final List<String> toRemove = new ArrayList<>();
    private long lastSortMs;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        Collection<StatusEffectInstance> currentEffects = mc.player.getStatusEffects();
        rows.values().forEach(row -> row.active = false);

        S = InterFace.getInstance().sizeHud.getValue();
        H = 16F * S;
        MIN_W = 44F * S;
        RADIUS = 5F * S;
        TITLE_TEXT = 7F * S;
        TITLE_ICON = 5F * S;
        ROW_TEXT = 6.5F * S;
        ICON = 7F * S;
        TITLE_ICON_X = 5F * S;
        TITLE_ICON_Y = 5.5F * S;
        TITLE_TEXT_X = 12.5F * S;
        TITLE_TEXT_Y = 3.4F * S;
        ROW_HEIGHT = 14F * S;
        ROW_BASE_W = 27F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        SEP_PADDING_X = 4F * S;
        SEP_OFFSET_Y = 3.2F * S;

        for (StatusEffectInstance effect : currentEffects) {
            String name = effect.getEffectType().value().getName().getString();
            boolean negative = effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
            Row row = rows.computeIfAbsent(name, k -> new Row(name));

            int secs = effect.isInfinite() ? Integer.MIN_VALUE : effect.getDuration() / 20;
            if (secs != row.lastSeconds) {
                row.duration = formatDuration(effect);
                row.lastSeconds = secs;
            }
            row.negative = negative;
            row.effectInstance = effect;
            row.active = true;
        }

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean isEmpty = rows.isEmpty();
        boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);
        boolean chatGhost = (mc.currentScreen instanceof ChatScreen) && isEmpty;

        openAnim.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        chatAnim.setDirection(chatGhost ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;

        float alpha = openAnim.getOutput();
        if (closeCondition && alpha == 0.0F) return;

        float alpha2 = chatAnim.getOutput();

        Font font = Fonts.sf_regular;

        // плашка пустого состояния (в чате)
        RenderUtil.Render2D.hudPlate(x, y, MIN_W, H, alpha2, RADIUS, interFace.alphaHUD.getValue());

        Fonts.rainydlc_2.draw("P", x + TITLE_ICON_X, y + TITLE_ICON_Y, TITLE_ICON,
                ColorUtil.replAlpha(ColorUtil.client(), alpha2));
        font.draw("Potions", x + TITLE_TEXT_X, y + TITLE_TEXT_Y, TITLE_TEXT,
                ColorUtil.multAlpha(ColorUtil.getColor(240), alpha2));

        // сортировка — раз в 50 мс в переиспользуемый список
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSortMs >= 50L) {
            lastSortMs = nowMs;
            sorted.clear();
            sorted.addAll(rows.values());
            sorted.sort(Comparator.comparingInt((Row r) ->
                    r.name.length() + r.duration.length()
            ).reversed());
        }

        float w = 0;
        float h = 4 * S;

        toRemove.clear();

        for (Row row : sorted) {
            row.anim.update();
            row.anim.run(row.active ? 1f : 0f, 0.12f, Easings.QUAD_OUT);
            float a = row.anim.get();

            if (a <= 0.01f && !row.active) {
                toRemove.add(row.name);
                continue;
            }

            float rowW = ROW_BASE_W + ICON + font.getWidth(row.duration, ROW_TEXT)
                    + font.getWidth(row.label(), ROW_TEXT) + levelWidth(font, row);

            w = Math.max(w, rowW * a);
            h += ROW_HEIGHT * a;
        }

        RenderUtil.Render2D.hudPlate(x, y, w, h, alpha, RADIUS, interFace.alphaHUD.getValue());

        float offsetY = y + ROW_START_Y;
        float offsetY2 = 0;
        boolean firstRow = true;

        for (Row row : sorted) {
            float a = row.anim.get();
            if (a <= 0.01f && !row.active) continue;

            int lvl = row.effectInstance.getAmplifier() + 1;

            // вредные эффекты подсвечены красным
            int timeColor = ColorUtil.getColor(200, alpha * a);
            int nameColor = row.negative ? ColorUtil.getColor(235, 70, 70, alpha * a) : ColorUtil.getColor(240, alpha * a);
            int lvlColor = row.negative ? ColorUtil.getColor(170, 55, 55, alpha * a) : ColorUtil.getColor(150, alpha * a);

            // иконка эффекта
            drawEffectIcon(eventDisplay, row, x + ROW_PADDING_X,
                    offsetY + (ROW_HEIGHT - ICON) / 2F - 1.4F * S, alpha * a);

            float cursorX = x + ROW_PADDING_X + ICON + 4F * S;

            // время серым
            font.draw(row.duration, cursorX, offsetY, ROW_TEXT, timeColor);
            cursorX += font.getWidth(row.duration, ROW_TEXT) + 3F * S;

            // название (+ уровень цветом)
            String label = lvl > 1 ? row.name + " " : row.name;
            font.draw(label, cursorX, offsetY, ROW_TEXT, nameColor);
            cursorX += font.getWidth(label, ROW_TEXT);
            if (lvl > 1) {
                font.draw(String.valueOf(lvl), cursorX, offsetY, ROW_TEXT, lvlColor);
            }

            // разделитель
            if (!firstRow) {
                RenderUtil.Render2D.rect(x + SEP_PADDING_X, offsetY - SEP_OFFSET_Y,
                        w - SEP_PADDING_X * 2F, 0.5F, ColorUtil.getColor(255, 0.05F * alpha * a), 1);
            }
            firstRow = false;

            offsetY += ROW_HEIGHT * a;
            offsetY2 += ROW_HEIGHT * a;
        }

        toRemove.forEach(rows::remove);

        dragSetting.size.set(Math.max((int) w, (int) MIN_W), (int) Math.max(offsetY2, H));
    }

    private float levelWidth(Font font, Row row) {
        int lvl = row.effectInstance == null ? 1 : row.effectInstance.getAmplifier() + 1;
        return lvl > 1 ? font.getWidth(" " + lvl, ROW_TEXT) : 0;
    }

    private void drawEffectIcon(EventDisplay eventDisplay, Row row, float x, float y, float alpha) {
        var texture = getEffectTexture(row.effectInstance.getEffectType());

        var matrices = eventDisplay.getDrawContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(ICON / 12F, ICON / 12F);

        eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 12, 12, ColorUtil.getColor(255, alpha));

        matrices.popMatrix();
    }

    private String formatDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int t = effect.getDuration() / 20;
        return String.format("%d:%02d", t / 60, t % 60);
    }

    private static class Row {
        final String name;
        String duration = "";
        boolean active;
        boolean negative;
        StatusEffectInstance effectInstance;

        final ru.white.utils.animation.Animation anim = new ru.white.utils.animation.Animation();
        int lastSeconds = Integer.MIN_VALUE;

        Row(String name) {
            this.name = name;
            this.active = true;
        }

        String label() {
            int lvl = effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
            return lvl > 1 ? name + " " + lvl : name;
        }
    }
}
