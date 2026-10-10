package dev.hatek.client.module.impl.combat.aimassist;

import dev.hatek.client.module.setting.Setting;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Кнопка как {@code ButtonSetting}, но с меняющимися подписью
 * и состоянием (нужна для «Обучить наводку» / «Стоп»).
 */
public final class DynamicButtonSetting extends Setting {
    private static final float ICON_GAP = 6.0f;

    private String caption;
    private boolean enabled = true;
    private Runnable action;

    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim pressAnim = new Anim(0.0f, 9.0f);

    public DynamicButtonSetting(String name, String caption, Runnable action) {
        super(name);
        this.caption = caption;
        this.action = action;
    }

    public void caption(String caption) {
        this.caption = caption;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void action(Runnable action) {
        this.action = action;
    }

    @Override
    public float bottomOffset() {
        return Style.LABEL_TO_CONTROL + Style.ROW_H;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float rowY = controlTop(baseline);
        this.hoverAnim.to(this.enabled
                && Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H));
        this.pressAnim.to(0.0f);

        float hover = this.hoverAnim.get();
        float press = this.pressAnim.get();

        int base = this.enabled ? Style.WHITE_04 : Style.white(0.02f);
        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(base, Style.white(0.075f), hover),
                        Style.accent(), press));

        HFont font = Fonts.body();
        float group = Style.FIELD_ICON + ICON_GAP + font.width(this.caption);
        float left = x + (Style.ROW_W - group) / 2.0f;
        int tint = this.enabled
                ? Render2D.lerp(Render2D.lerp(Style.WHITE_45, Style.WHITE, hover * 0.5f),
                        Style.PANEL, press)
                : Style.WHITE_25;

        Render2D.icon(gg, "button", Math.round(left),
                rowY + (Style.ROW_H - Style.FIELD_ICON) / 2.0f,
                Style.FIELD_ICON - 1.0f, Style.FIELD_ICON, tint);
        font.draw(gg, this.caption, left + Style.FIELD_ICON + ICON_GAP,
                rowY + Style.FIELD_TEXT_BASELINE, tint);
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (button != 0 || !this.enabled) {
            return false;
        }
        if (!Render2D.hovered(mx, my, x, controlTop(baseline), Style.ROW_W, Style.ROW_H)) {
            return false;
        }
        this.pressAnim.snap(1.0f);
        if (this.action != null) {
            this.action.run();
        }
        return true;
    }
}
