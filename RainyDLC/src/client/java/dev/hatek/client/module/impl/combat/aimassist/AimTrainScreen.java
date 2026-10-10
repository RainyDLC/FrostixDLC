package dev.hatek.client.module.impl.combat.aimassist;

import dev.hatek.client.module.impl.combat.AimAssist;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.screen.ClickGuiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Менюшка обучения: вводишь название, жмёшь «Начать обучение» —
 * меню само закрывается и сверху появляется прогресс.
 */
public final class AimTrainScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 236;
    private static final int PAD = 20;
    private static final int FIELD_H = 32;
    private static final int BTN_H = 34;
    private static final int MAX_NAME = 24;

    private final AimAssist parent;

    private String name = "";
    private boolean typing = true;

    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim fieldAnim = new Anim(0.0f, 16.0f);
    private final Anim startAnim = new Anim(0.0f, 16.0f);
    private long lastFrame = System.nanoTime();

    public AimTrainScreen(AimAssist parent) {
        super(Component.literal("Обучение наводки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.openAnim.snap(0.0f);
        this.openAnim.to(1.0f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float panelX() {
        return Math.round((Render2D.screenWidth() - PANEL_W) / 2.0f);
    }

    private float panelY() {
        return Math.round((Render2D.screenHeight() - PANEL_H) / 2.0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(gg, mouseX, mouseY, partialTick);

        long now = System.nanoTime();
        Anim.beginFrame((now - this.lastFrame) / 1_000_000_000.0f);
        this.lastFrame = now;

        float eased = Anim.easeOut(this.openAnim.get());
        float x = panelX();
        float y = panelY();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        Render2D.begin(gg);
        Render2D.pushScale(gg, x + PANEL_W / 2.0f, y + PANEL_H / 2.0f, 0.96f + 0.04f * eased);
        Render2D.pushAlpha(eased);

        Render2D.round(gg, x, y, PANEL_W, PANEL_H, Style.PANEL_R, Style.PANEL);

        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        title.draw(gg, "Обучение наводки", x + PAD, y + 30.0f, Style.WHITE);
        body.draw(gg, "Придумай название и нажми «Начать».", x + PAD, y + 52.0f, Style.WHITE_45);
        body.draw(gg, "Записывается только наводка, не удары.", x + PAD, y + 66.0f, Style.WHITE_45);

        // поле ввода
        float fx = x + PAD;
        float fy = y + 84.0f;
        float fw = PANEL_W - PAD * 2;
        boolean fieldHover = Render2D.hovered(mx, my, fx, fy, fw, FIELD_H);
        this.fieldAnim.to(this.typing || fieldHover);
        float k = this.fieldAnim.get();
        Render2D.round(gg, fx, fy, fw, FIELD_H, 9.0f,
                Render2D.lerp(Style.WHITE_04, Style.white(0.085f), k));
        if (this.typing) {
            Render2D.round(gg, fx, fy + FIELD_H - 2.0f, fw, 2.0f, 1.0f,
                    Render2D.withAlpha(Style.accent(), 0.85f));
        }
        boolean empty = this.name.isEmpty();
        String shown = empty && !this.typing ? "Название обучения" : this.name;
        float baseline = body.centeredBaseline(fy, FIELD_H);
        body.draw(gg, shown, fx + 12.0f, baseline, empty ? Style.WHITE_25 : Style.WHITE);
        if (this.typing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caret = fx + 12.0f + body.width(this.name) + 1.0f;
            Render2D.rect(gg, caret, fy + 8.0f, 1.0f, FIELD_H - 16.0f, Style.WHITE_45);
        }

        // кнопка «Начать обучение»
        float bx = x + PAD;
        float by = y + 132.0f;
        float bw = PANEL_W - PAD * 2;
        boolean canStart = !this.name.isBlank();
        boolean startHover = Render2D.hovered(mx, my, bx, by, bw, BTN_H);
        this.startAnim.to(startHover && canStart);
        float a = this.startAnim.get();
        int fill = canStart
                ? Render2D.lerp(Render2D.withAlpha(Style.accent(), 0.85f), Style.accent(), a)
                : Style.WHITE_04;
        Render2D.round(gg, bx, by, bw, BTN_H, 9.0f, fill);
        label.drawCentered(gg, "Начать обучение", bx + bw / 2.0f,
                label.centeredBaseline(by, BTN_H),
                canStart ? Style.PANEL : Style.WHITE_25);

        // «Отмена»
        float cy = by + BTN_H + 12.0f;
        boolean cancelHover = Render2D.hovered(mx, my, bx, cy, bw, 20.0f);
        label.drawCentered(gg, "Отмена", bx + bw / 2.0f, cy + 13.0f,
                cancelHover ? Style.WHITE : Style.WHITE_45);

        Render2D.popAlpha();
        Render2D.popTransform(gg);
        Render2D.end(gg);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        float x = panelX();
        float y = panelY();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        float fx = x + PAD;
        float fy = y + 84.0f;
        float fw = PANEL_W - PAD * 2;
        if (Render2D.hovered(mx, my, fx, fy, fw, FIELD_H)) {
            this.typing = true;
            return true;
        }
        this.typing = false;

        float bx = x + PAD;
        float by = y + 132.0f;
        float bw = PANEL_W - PAD * 2;
        if (Render2D.hovered(mx, my, bx, by, bw, BTN_H)) {
            submit();
            return true;
        }
        float cy = by + BTN_H + 12.0f;
        if (Render2D.hovered(mx, my, bx, cy, bw, 20.0f)) {
            this.minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.typing) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!this.name.isEmpty()) {
                        this.name = this.name.substring(0, this.name.length() - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    submit();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    this.typing = false;
                    return true;
                }
                default -> {
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.typing) {
            return super.charTyped(event);
        }
        char c = (char) event.codepoint();
        boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9' || c == '_' || c == '-' || c == ' '
                || c >= 'а' && c <= 'я' || c >= 'А' && c <= 'Я' || c == 'ё' || c == 'Ё';
        if (allowed && this.name.length() < MAX_NAME) {
            this.name += c;
        }
        return true;
    }

    private void submit() {
        String clean = this.name.trim();
        if (clean.isEmpty()) {
            return;
        }
        this.parent.startTraining(clean);
        // менюшка автоматически закрывается
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(new ClickGuiScreen());
    }
}
