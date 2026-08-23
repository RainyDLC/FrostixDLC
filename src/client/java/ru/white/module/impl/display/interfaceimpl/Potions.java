package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.*;

import static net.minecraft.client.gui.hud.InGameHud.getEffectTexture;

public class Potions implements IMinecraft {

    /** Общий масштаб плашки: один множитель на шрифты, иконки и все отступы. */
    private static float S = 1.0F;

    private static float H = 12F * S;
    private static float MIN_W = 34F * S;
    private static float RADIUS = 4F * S;

    private static float ROW_TEXT = 6F * S;
    private static float ICON = 6.5F * S;

    private static float ROW_HEIGHT = 12F * S;
    private static float PAD_X = 4.5F * S;
    private static float ICON_GAP = 3.5F * S;
    private static float NAME_TIME_GAP = 8F * S;
    private static float ROW_START_Y = 4F * S;
    private static float SCROLL_OFFSET_Y = 4F * S;


    private ru.white.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300,1);
    private ru.white.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300,1);

    private final Map<String, EffectData> displayedEffects = new LinkedHashMap<>();

    // кэши: отсортированный список пересобирается раз в 50 мс, toRemove переиспользуется
    private final List<EffectData> sortedEffects = new ArrayList<>();
    private final List<String> toRemove = new ArrayList<>();
    private long lastSortMs;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        Collection<StatusEffectInstance> currentEffects = mc.player.getStatusEffects();
        displayedEffects.values().forEach(data -> data.active = false);
        S = InterFace.getInstance().sizeHud.getValue();
        H = 12F * S;
        MIN_W = 34F * S;
        RADIUS = 4F * S;
        ROW_TEXT = 6F * S;
        ICON = 6.5F * S;
        ROW_HEIGHT = 12F * S;
        PAD_X = 4.5F * S;
        ICON_GAP = 3.5F * S;
        NAME_TIME_GAP = 8F * S;
        ROW_START_Y = 4F * S;
        SCROLL_OFFSET_Y = 4F * S;

        for (StatusEffectInstance effect : currentEffects) {
            String name = effect.getEffectType().value().getName().getString();
            int amplifier = effect.getAmplifier() + 1;
            boolean isNegative = effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
            EffectData data = displayedEffects.computeIfAbsent(name, k -> new EffectData(name, getDurationString(effect), amplifier, isNegative, effect.getDuration(), effect));

            // строка времени меняется раз в секунду — пересобираем только при смене секунды,
            // а не String.format на каждом кадре
            int secs = effect.isInfinite() ? Integer.MIN_VALUE : effect.getDuration() / 20;
            if (secs != data.lastSeconds) {
                String duration = getDurationString(effect);
                if (!duration.equals(data.duration)) {
                    data.prevDuration = data.duration;
                    data.duration = duration;
                    data.digitAnim.set(0);
                    data.digitAnim.run(1, 0.18, Easings.QUAD_OUT);
                }
                data.lastSeconds = secs;
            }

            data.durationTicks = effect.getDuration();
            data.negative = isNegative;
            data.effectInstance = effect;
            data.active = true;
        }

        boolean isEmpty = displayedEffects.isEmpty();

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);

        animation1.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        animation2.setDirection((mc.currentScreen instanceof ChatScreen) && isEmpty ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;

        float alpha = animation1.getOutput();

        if (closeCondition && alpha == 0.0F) return;

        float alpha2 = animation2.getOutput();

        // Пустое состояние: мини-плашка с глифом, чтобы элемент находился в редакторе HUD
        RenderUtil.Render2D.hudPlate(x, y, MIN_W, H, alpha2, RADIUS, InterFace.getInstance().alphaHUD.getValue());
        Fonts.rainydlc_2.drawCentered("P", x + MIN_W / 2F, y + (H - 5F * S) / 2F, 5F * S,
                ColorUtil.replAlpha(ColorUtil.client(), alpha2 * 0.85F));

        Font font = Fonts.sf_regular;

        // сортировка раз в 50 мс в переиспользуемый список вместо стрима каждый кадр
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSortMs >= 50L) {
            lastSortMs = nowMs;
            sortedEffects.clear();
            sortedEffects.addAll(displayedEffects.values());
            sortedEffects.sort(Comparator.comparingInt((EffectData data) ->
                    data.name.length() + data.duration.length()
            ).reversed());
        }

        float h = ROW_START_Y * 2;
        float w = 0;

        toRemove.clear();

        for (EffectData data : sortedEffects) {
            data.animation.update();
            data.animation.run(data.active ? 1f : 0f, 0.12f, Easings.QUAD_OUT);
            data.digitAnim.update();

            float a = data.animation.get();

            if (a <= 0.01f && !data.active) {
                toRemove.add(data.name);
                continue;
            }

            float rowW = PAD_X * 2 + ICON + ICON_GAP
                    + font.getWidth(label(data), ROW_TEXT)
                    + NAME_TIME_GAP
                    + font.getWidth(data.duration, ROW_TEXT);

            w = Math.max(w, rowW * a);
            h += ROW_HEIGHT * a;
        }

        // Отрисовка фона списка
        RenderUtil.Render2D.hudPlate(x, y, w, h, alpha, RADIUS, InterFace.getInstance().alphaHUD.getValue());

        float offsetY = y + ROW_START_Y;
        float offsetY2 = 0;

        for (EffectData data : sortedEffects) {

            float a = data.animation.get();
            if (a <= 0.01f && !data.active) continue;

            int lvl = data.effectInstance.getAmplifier() + 1;

            // вредные эффекты подсвечиваем красным
            boolean bad = data.negative;

            int nameColor = bad ? ColorUtil.getColor(235, 70, 70, alpha * a) : ColorUtil.getColor(240, alpha * a);
            int lvlColor  = bad ? ColorUtil.getColor(170, 55, 55, alpha * a) : ColorUtil.getColor(150, alpha * a);
            int timeColor = bad ? ColorUtil.getColor(200, 60, 60, alpha * a) : ColorUtil.getColor(185, alpha * a);

            // кэш строки с уровнем: конкатенация только при смене уровня/типа эффекта
            if (data.coloredLabelLvl != lvl || data.coloredLabelBad != bad) {
                data.coloredLabel = data.name + (lvl > 1 ? " " + ColorFormatting.getColor(lvlColor) + lvl : "");
                data.coloredLabelLvl = lvl;
                data.coloredLabelBad = bad;
            }
            String effectname = data.coloredLabel;

            float textY = offsetY + (ROW_HEIGHT - ROW_TEXT) / 2F;

            // иконка эффекта слева
            drawEffectIcon(eventDisplay, data, x + PAD_X, offsetY + (ROW_HEIGHT - ICON) / 2F, alpha * a);

            // название с уровнем
            font.draw(effectname, x + PAD_X + ICON + ICON_GAP, textY, ROW_TEXT, nameColor);

            // время прижато к правому краю
            String key = data.duration;
            float timeWidth = font.getWidth(key, ROW_TEXT);
            drawDuration(font, data, x + w - PAD_X - timeWidth, textY, ROW_TEXT, timeColor);

            offsetY += ROW_HEIGHT * a;
            offsetY2 += ROW_HEIGHT * a;
        }

        toRemove.forEach(displayedEffects::remove);

        dragSetting.size.set(ColorUtil.overCol((int) Math.max(w, 20 * S), (int) MIN_W, alpha2),
                ColorUtil.overCol((int) offsetY2, (int) H, alpha2));
    }

    /**
     * Рисует время посимвольно: изменившийся символ уезжает вверх, новый приезжает снизу.
     * Символы сопоставляются с конца строки, чтобы «1:09» → «1:10» двигало только младшие разряды.
     */
    private void drawDuration(Font font, EffectData data, float x, float y, float size, int color) {
        float t = data.digitAnim.get();

        String now = data.duration;
        String was = data.prevDuration == null ? now : data.prevDuration;

        // таймер не анимируется — рисуем целиком, без посимвольных substring каждый кадр
        if (t >= 1F) {
            font.draw(now, x, y, size, color);
            return;
        }

        float cx = x;

        for (int i = 0; i < now.length(); i++) {
            String ch = now.substring(i, i + 1);

            int j = was.length() - (now.length() - i);
            String old = j >= 0 && j < was.length() ? was.substring(j, j + 1) : null;

            if (t >= 1F || ch.equals(old)) {
                font.draw(ch, cx, y, size, color);
            } else {
                font.draw(ch, cx, y + SCROLL_OFFSET_Y * (1 - t), size, ColorUtil.multAlpha(color, t));

                if (old != null) font.draw(old, cx, y - SCROLL_OFFSET_Y * t, size, ColorUtil.multAlpha(color, 1 - t));
            }

            cx += font.getWidth(ch, size);
        }
    }

    /** Строка без цветовых кодов — для замера ширины (коды каждый кадр засоряют кэш ширин). */
    private String label(EffectData data) {
        int lvl = data.effectInstance.getAmplifier() + 1;
        if (data.plainLabelLvl != lvl) {
            data.plainLabel = lvl > 1 ? data.name + " " + lvl : data.name;
            data.plainLabelLvl = lvl;
        }
        return data.plainLabel;
    }

    private void drawEffectIcon(EventDisplay eventDisplay, EffectData data, float x, float y, float alpha) {
        Identifier effectTex = getEffectTexture(data.effectInstance.getEffectType());

        var matrices = eventDisplay.getDrawContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(x, y);

        // ICON / 12F работает корректно, так как исходный размер текстуры эффекта всегда 12х12
        matrices.scale(ICON / 12F, ICON / 12F);

        eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectTex,
                0, 0, 12, 12, ColorUtil.getColor(255, alpha));

        matrices.popMatrix();
    }

    private String getDurationString(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int t = effect.getDuration() / 20;
        return String.format("%d:%02d", t / 60, t % 60);
    }

    private static class EffectData {
        String name;
        String duration;
        String prevDuration;
        final Animation digitAnim = new Animation();
        Animation animation;
        boolean active;
        boolean negative;
        int durationTicks;
        int lvl;
        StatusEffectInstance effectInstance;

        // кэши для оптимизации: секунда последнего пересчёта строки времени + подписи с уровнем
        int lastSeconds = Integer.MIN_VALUE;
        String plainLabel;
        int plainLabelLvl = -1;
        String coloredLabel;
        int coloredLabelLvl = -1;
        boolean coloredLabelBad;

        public EffectData(String name, String duration, int lvl, boolean negative, int durationTicks, StatusEffectInstance effectInstance) {
            this.name = name;
            this.duration = duration;
            this.prevDuration = duration;
            this.lvl = lvl;
            this.negative = negative;
            this.durationTicks = durationTicks;
            this.animation = new Animation();
            this.active = true;
            this.effectInstance = effectInstance;
        }
    }
}
