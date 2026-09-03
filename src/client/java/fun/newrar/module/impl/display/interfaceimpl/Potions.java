package fun.newrar.module.impl.display.interfaceimpl;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorFormatting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static net.minecraft.client.gui.hud.InGameHud.getEffectTexture;

public class Potions implements IMinecraft {

    private float s = 1.0F;
    private float h = 12F;
    private float minW = 34F;
    private float radius = 4F;
    private float rowText = 6F;
    private float icon = 6.5F;
    private float rowHeight = 12F;
    private float padX = 4.5F;
    private float iconGap = 3.5F;
    private float nameTimeGap = 8F;
    private float rowStartY = 4F;
    private float scrollOffsetY = 4F;

    private final fun.newrar.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    private final fun.newrar.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);

    private final Map<String, EffectData> displayedEffects = new LinkedHashMap<>();
    private final List<EffectData> sortedEffects = new ArrayList<>();
    private final List<String> toRemove = new ArrayList<>();
    private long lastSortMs;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        Collection<StatusEffectInstance> currentEffects = mc.player.getStatusEffects();
        displayedEffects.values().forEach(data -> data.active = false);

        s = InterFace.getInstance().sizeHud.getValue();
        h = 12F * s;
        minW = 34F * s;
        radius = 4F * s;
        rowText = 6F * s;
        icon = 6.5F * s;
        rowHeight = 12F * s;
        padX = 4.5F * s;
        iconGap = 3.5F * s;
        nameTimeGap = 8F * s;
        rowStartY = 4F * s;
        scrollOffsetY = 4F * s;

        for (StatusEffectInstance effect : currentEffects) {
            String name = effect.getEffectType().value().getName().getString();
            int amplifier = effect.getAmplifier() + 1;
            boolean isNegative = effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;

            EffectData data = displayedEffects.computeIfAbsent(name, k -> new EffectData(name, getDurationString(effect), amplifier, isNegative, effect.getDuration(), effect));

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

        RenderUtil.Render2D.hudPlate(x, y, minW, h, alpha2, radius, InterFace.getInstance().alphaHUD.getValue());
        Fonts.rainydlc_2.drawCentered("P", x + minW / 2F, y + (h - 5F * s) / 2F, 5F * s,
                ColorUtil.replAlpha(ColorUtil.client(), alpha2 * 0.85F));

        Font font = Fonts.sf_regular;

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSortMs >= 50L) {
            lastSortMs = nowMs;
            sortedEffects.clear();
            sortedEffects.addAll(displayedEffects.values());
            sortedEffects.sort(Comparator.comparingInt((EffectData data) ->
                    data.name.length() + data.duration.length()
            ).reversed());
        }

        float totalH = rowStartY * 2;
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

            float rowW = padX * 2 + icon + iconGap
                    + font.getWidth(label(data), rowText)
                    + nameTimeGap
                    + font.getWidth(data.duration, rowText);

            w = Math.max(w, rowW * a);
            totalH += rowHeight * a;
        }

        RenderUtil.Render2D.hudPlate(x, y, w, totalH, alpha, radius, InterFace.getInstance().alphaHUD.getValue());

        float offsetY = y + rowStartY;
        float offsetY2 = 0;

        for (EffectData data : sortedEffects) {
            float a = data.animation.get();
            if (a <= 0.01f && !data.active) continue;

            int lvl = data.effectInstance.getAmplifier() + 1;
            boolean bad = data.negative;

            int nameColor = bad ? ColorUtil.getColor(235, 70, 70, alpha * a) : ColorUtil.getColor(240, alpha * a);
            int lvlColor = bad ? ColorUtil.getColor(170, 55, 55, alpha * a) : ColorUtil.getColor(150, alpha * a);
            int timeColor = bad ? ColorUtil.getColor(200, 60, 60, alpha * a) : ColorUtil.getColor(185, alpha * a);

            if (data.coloredLabelLvl != lvl || data.coloredLabelBad != bad) {
                data.coloredLabel = data.name + (lvl > 1 ? " " + ColorFormatting.getColor(lvlColor) + lvl : "");
                data.coloredLabelLvl = lvl;
                data.coloredLabelBad = bad;
            }
            String effectname = data.coloredLabel;

            float textY = offsetY + (rowHeight - rowText) / 2F;

            drawEffectIcon(eventDisplay, data, x + padX, offsetY + (rowHeight - icon) / 2F, alpha * a);

            font.draw(effectname, x + padX + icon + iconGap, textY, rowText, nameColor);

            String key = data.duration;
            float timeWidth = font.getWidth(key, rowText);
            drawDuration(font, data, x + w - padX - timeWidth, textY, rowText, timeColor);

            offsetY += rowHeight * a;
            offsetY2 += rowHeight * a;
        }

        toRemove.forEach(displayedEffects::remove);

        dragSetting.size.set(ColorUtil.overCol((int) Math.max(w, 20 * s), (int) minW, alpha2),
                ColorUtil.overCol((int) offsetY2, (int) h, alpha2));
    }

    private void drawDuration(Font font, EffectData data, float x, float y, float size, int color) {
        float t = data.digitAnim.get();

        String now = data.duration;
        String was = data.prevDuration == null ? now : data.prevDuration;

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
                font.draw(ch, cx, y + scrollOffsetY * (1 - t), size, ColorUtil.multAlpha(color, t));

                if (old != null) font.draw(old, cx, y - scrollOffsetY * t, size, ColorUtil.multAlpha(color, 1 - t));
            }

            cx += font.getWidth(ch, size);
        }
    }

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

        matrices.scale(icon / 12F, icon / 12F);

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
