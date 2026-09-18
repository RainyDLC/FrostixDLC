package fun.newrar.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import fun.newrar.Client;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.Keyboard;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KeyBinds implements element {

    private final List<Module> cachedModules = new ArrayList<>();
    private long lastRebuildMs;

    private float s = 1.0F;
    private float headerH = 16F;
    private float minW = 50F;
    private float radius = 5F;

    private float titleText = 7F;
    private float titleIcon = 5F;
    private float rowText = 6.5F;

    private float titleIconX = 5F;
    private float titleIconY = 4.0F;
    private float titleTextX = 12.5F;
    private float titleTextY = 3.4F;

    private float rowHeight = 14F;
    private float rowBaseW = 27F;
    private float rowPaddingX = 5F;
    private float rowStartY = 5F;
    private float iconPadding = 4F;
    private float iconOffsetY = 0.0F;
    private float sepPaddingX = 4F;
    private float sepOffsetY = 3.2F;

    private final fun.newrar.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    private final fun.newrar.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        s = InterFace.getInstance().sizeHud.getValue();
        headerH = 16F * s;
        minW = 50F * s;
        radius = 5F * s;
        titleText = 7F * s;
        titleIcon = 5F * s;
        rowText = 6.5F * s;
        titleIconX = 5F * s;
        titleIconY = 5.5F * s;
        titleTextX = 12.5F * s;
        titleTextY = 3.4F * s;
        rowHeight = 14F * s;
        rowBaseW = 27F * s;
        rowPaddingX = 5F * s;
        rowStartY = 5F * s;
        iconPadding = 4F * s;
        iconOffsetY = 1.0F * s;
        sepPaddingX = 4F * s;
        sepOffsetY = 3.2F * s;

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
            cachedModules.sort(Comparator.comparingInt((Module m) ->
                    m.getName().length() + Keyboard.keyName(m.getKey()).length()
            ).reversed());
        }
        List<Module> modules = cachedModules;

        boolean isEmpty = modules.isEmpty();

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);

        animation1.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        animation2.setDirection((mc.currentScreen instanceof ChatScreen) && isEmpty ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;

        float alpha = animation1.getOutput();
        if (closeCondition && alpha == 0.0F) return;

        float alpha2 = animation2.getOutput();

        RenderUtil.Render2D.hudPlate(x, y, minW, headerH, alpha2, radius, InterFace.getInstance().alphaHUD.getValue());

        Font font = Fonts.sf_regular;
        Font cat = Fonts.rainydlc_2;

        cat.draw("E", x + titleIconX, y + titleIconY, titleIcon, ColorUtil.replAlpha(ColorUtil.client(), alpha2));
        font.draw("Key binds", x + titleTextX, y + titleTextY, titleText, ColorUtil.multAlpha(ColorUtil.getColor(240), alpha2));

        float totalH = 4 * s;
        float w = 0;

        for (Module m : modules) {
            m.animation.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
            float mAnim = m.getAnimation().getOutput();
            if (mAnim <= 0) continue;

            String name = m.getCategory().getIcon();
            float rowW = rowBaseW + font.getWidth(m.getBigName(), rowText)
                    + font.getWidth(Keyboard.keyName(m.getKey()), rowText)
                    + cat.getWidth(name, rowText);

            w = Math.max(w, rowW * mAnim);
            totalH += rowHeight * mAnim;
        }

        float popScale = 0.90F + 0.10F * (float) Easings.BACK_OUT.ease(MathHelper.clamp(alpha, 0F, 1F));
        RenderUtil.Render2D.hudPlate(x, y, w * popScale, totalH * popScale, alpha, radius, InterFace.getInstance().alphaHUD.getValue());

        float offsetY = y + rowStartY;
        float offsetY2 = 0;

        for (Module m : modules) {
            float mAnim = m.getAnimation().getOutput();
            if (mAnim <= 0) continue;

            float addX = (1.0F - mAnim) * 8F * s;
            String name = m.getCategory().getIcon();

            font.draw(m.getBigName(), x + rowPaddingX + addX, offsetY, rowText, ColorUtil.getColor(240, alpha * mAnim));

            String key = Keyboard.keyName(m.getKey());
            float keyWidth = font.getWidth(key, rowText);
            float iconWidth = cat.getWidth(name, rowText);

            font.draw(key, x - rowPaddingX + w - keyWidth - iconWidth - iconPadding - addX, offsetY, rowText, ColorUtil.getColor(200, alpha * mAnim));
            cat.draw(name, x - rowPaddingX + w - iconWidth - addX, offsetY + iconOffsetY, rowText, ColorUtil.replAlpha(ColorUtil.client(), alpha * mAnim));

            if (modules.getFirst() != m) {
                RenderUtil.Render2D.rect(x + sepPaddingX, offsetY - sepOffsetY, w - (sepPaddingX * 2), 0.5F,
                        ColorUtil.getColor(255, 0.05F * alpha * mAnim), 1);
            }

            offsetY += rowHeight * mAnim;
            offsetY2 += rowHeight * mAnim;
        }

        dragSetting.size.set(ColorUtil.overCol((int) Math.max(w, 20 * s), (int) minW, alpha2),
                ColorUtil.overCol((int) offsetY2, (int) headerH, alpha2));
    }
}
