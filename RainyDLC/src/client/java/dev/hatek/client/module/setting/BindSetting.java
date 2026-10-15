package dev.hatek.client.module.setting;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * Бинд клавиши или кнопки мыши внутри модуля (например, для ручного броска виндчарда).
 * Левый клик — слушать следующий ввод, правый — очистить.
 * Боковые кнопки мыши (Mouse 4/5) тоже ловятся.
 */
public final class BindSetting extends Setting {
    private static final float ICON_GAP = 6.0f;

    private InputConstants.Type type = InputConstants.Type.KEYSYM;
    private int code = GLFW.GLFW_KEY_UNKNOWN;

    private boolean binding;

    private final Anim hoverAnim = new Anim(0.0f, 18.0f);
    private final Anim pressAnim = new Anim(0.0f, 9.0f);

    public BindSetting(String name) {
        super(name);
    }

    public BindSetting(String name, InputConstants.Type type, int code) {
        super(name);
        this.type = type;
        this.code = code;
    }

    public boolean bound() {
        return this.code != GLFW.GLFW_KEY_UNKNOWN;
    }

    public String keyLabel() {
        if (!bound()) {
            return "-";
        }
        return this.type.getOrCreate(this.code).getDisplayName().getString();
    }

    /** Нажат ли забинженный ввод прямо сейчас (клавиатура или мышь). */
    public boolean isDown(Minecraft mc) {
        if (!bound() || mc == null || mc.getWindow() == null) {
            return false;
        }
        if (this.type == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), this.code) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(mc.getWindow(), this.code);
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
            // Левый клик только входит в режим ожидания, сам биндом не становится.
            this.binding = true;
            this.pressAnim.snap(1.0f);
            return true;
        }
        if (button == 1) {
            clear();
            return true;
        }
        // Любая другая кнопка мыши (включая Mouse 4/5) — только в режиме ожидания.
        if (this.binding) {
            this.type = InputConstants.Type.MOUSE;
            this.code = button;
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
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            clear();
            return true;
        }
        this.type = InputConstants.Type.KEYSYM;
        this.code = key;
        return true;
    }

    private void clear() {
        this.type = InputConstants.Type.KEYSYM;
        this.code = GLFW.GLFW_KEY_UNKNOWN;
        this.binding = false;
    }

    @Override
    public String serialize() {
        return this.type.name() + ":" + this.code;
    }

    @Override
    public void deserialize(String raw) {
        clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        int split = raw.indexOf(':');
        try {
            if (split < 0) {
                // Старый формат — голый код клавиши.
                this.code = Integer.parseInt(raw.trim());
                this.type = InputConstants.Type.KEYSYM;
            } else {
                this.type = InputConstants.Type.valueOf(raw.substring(0, split).trim());
                this.code = Integer.parseInt(raw.substring(split + 1).trim());
            }
        } catch (IllegalArgumentException ignored) {
            clear();
        }
    }
}
