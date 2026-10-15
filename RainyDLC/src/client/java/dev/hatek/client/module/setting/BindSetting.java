package dev.hatek.client.module.setting;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Бинд клавиши внутри модуля (например, для ручного броска виндчарда в AutoMace).
 * Левый клик — слушать следующую клавишу, правый — очистить.
 */
public final class BindSetting extends Setting {
    private static final float ICON_GAP = 6.0f;

    private int key;
    private boolean binding;

    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim pressAnim = new Anim(0.0f, 9.0f);

    public BindSetting(String name) {
        this(name, GLFW.GLFW_KEY_UNKNOWN);
    }

    public BindSetting(String name, int key) {
        super(name);
        this.key = key;
    }

    public int key() {
        return this.key;
    }

    public void key(int value) {
        this.key = value;
    }

    public boolean bound() {
        return this.key != GLFW.GLFW_KEY_UNKNOWN;
    }

    public String keyLabel() {
        if (this.key == GLFW.GLFW_KEY_UNKNOWN) {
            return "-";
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(this.key).getDisplayName().getString();
        return name.length() > 3 ? name.substring(0, 3).toUpperCase(Locale.ROOT)
                : name.toUpperCase(Locale.ROOT);
    }

    @Override
    public float bottomOffset() {
        return Style.LABEL_TO_CONTROL + Style.ROW_H;
    }

    @Override
    public boolean capturesInput() {
        return this.binding;
    }

    @Override
    public void closePopups() {
        this.binding = false;
    }

    @Override
    public void render(GuiGraphicsExtractor gg, float x, float baseline, double mx, double my) {
        drawLabel(gg, x, baseline);
        float rowY = controlTop(baseline);
        this.hoverAnim.to(Render2D.hovered(mx, my, x, rowY, Style.ROW_W, Style.ROW_H));
        this.pressAnim.to(0.0f);

        float hover = this.hoverAnim.get();
        float press = this.pressAnim.get();

        Render2D.round(gg, x, rowY, Style.ROW_W, Style.ROW_H, Style.ROW_R,
                Render2D.lerp(Render2D.lerp(Style.WHITE_04, Style.white(0.075f), hover),
                        Style.accent(), this.binding ? 1.0f : press));

        HFont font = Fonts.body();
        String caption = this.binding && (System.currentTimeMillis() / 350L) % 2L == 0L
                ? "..."
                : keyLabel();
        float group = Style.FIELD_ICON + ICON_GAP + font.width(caption);
        float left = x + (Style.ROW_W - group) / 2.0f;
        int tint = this.binding
                ? Style.PANEL
                : Render2D.lerp(Style.WHITE_45, Style.WHITE, hover * 0.5f);

        Render2D.icon(gg, "keyboard", Math.round(left),
                rowY + (Style.ROW_H - Style.FIELD_ICON) / 2.0f,
                Style.FIELD_ICON - 1.0f, Style.FIELD_ICON, tint);
        font.draw(gg, caption, left + Style.FIELD_ICON + ICON_GAP,
                rowY + Style.FIELD_TEXT_BASELINE, tint);
    }

    @Override
    public boolean mouseClicked(float x, float baseline, double mx, double my, int button) {
        if (!Render2D.hovered(mx, my, x, controlTop(baseline), Style.ROW_W, Style.ROW_H)) {
            return false;
        }
        if (button == 0) {
            this.binding = true;
            this.pressAnim.snap(1.0f);
            return true;
        }
        if (button == 1) {
            this.key = GLFW.GLFW_KEY_UNKNOWN;
            this.binding = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!this.binding) {
            return false;
        }
        this.binding = false;
        this.key = key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key;
        return true;
    }

    @Override
    public String serialize() {
        return String.valueOf(this.key);
    }

    @Override
    public void deserialize(String raw) {
        try {
            this.key = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            this.key = GLFW.GLFW_KEY_UNKNOWN;
        }
        this.binding = false;
    }
}
