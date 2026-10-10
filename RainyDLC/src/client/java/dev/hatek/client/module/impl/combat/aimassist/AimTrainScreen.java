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

import java.util.List;

/**
 * Менюшка обучения наводки.
 *
 * <p>Состояния:
 * <ul>
 *   <li>покой — поле «Название обучения», кнопка «Обучить», список профилей;</li>
 *   <li>запись — кнопка «Стоп»;</li>
 *   <li>готово — кнопка «Обучить нейронку»;</li>
 *   <li>обучение — живой прогресс эпох и loss.</li>
 * </ul>
 */
public final class AimTrainScreen extends Screen {
    private static final int PANEL_W = 400;
    private static final int PAD = 20;
    private static final int FIELD_H = 32;
    private static final int BTN_H = 36;
    private static final int ROW_H = 34;
    private static final int MAX_NAME = 24;
    private static final int MAX_ROWS = 5;

    private final AimAssist parent;

    private String name = "";
    private boolean typing = true;
    private List<AimStore.ProfileInfo> profiles = List.of();

    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim fieldAnim = new Anim(0.0f, 16.0f);
    private final Anim btnAnim = new Anim(0.0f, 16.0f);
    private long lastFrame = System.nanoTime();

    public AimTrainScreen(AimAssist parent) {
        super(Component.literal("Обучение наводки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.openAnim.snap(0.0f);
        this.openAnim.to(1.0f);
        this.profiles = AimStore.listProfiles();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean recording() {
        return AimAssist.training;
    }

    private boolean learning() {
        return AimAssist.learning;
    }

    private boolean ready() {
        return !recording() && !learning()
                && this.parent.datasetSize() >= AimAssist.MIN_SAMPLES;
    }

    private float panelX() {
        return Math.round((Render2D.screenWidth() - PANEL_W) / 2.0f);
    }

    private float panelH() {
        if (recording()) {
            return 252.0f;
        }
        if (learning()) {
            return 218.0f;
        }
        if (ready()) {
            return 292.0f;
        }
        int rows = Math.min(MAX_ROWS, this.profiles.size());
        return 296.0f + rows * (ROW_H + 4.0f);
    }

    private float panelY() {
        return Math.round((Render2D.screenHeight() - panelH()) / 2.0f);
    }

    // ---------------- рендер ----------------

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
        float h = panelH();
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        Render2D.begin(gg);
        Render2D.pushScale(gg, x + PANEL_W / 2.0f, y + h / 2.0f, 0.96f + 0.04f * eased);
        Render2D.pushAlpha(eased);

        Render2D.round(gg, x, y, PANEL_W, h, Style.PANEL_R, Style.PANEL);

        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        if (recording()) {
            drawRecording(gg, x, y, mx, my, title, body, label);
        } else if (learning()) {
            drawLearning(gg, x, y, mx, my, title, body, label);
        } else if (ready()) {
            drawReady(gg, x, y, mx, my, title, body, label);
        } else {
            drawIdle(gg, x, y, h, mx, my, title, body, label);
        }

        Render2D.popAlpha();
        Render2D.popTransform(gg);
        Render2D.end(gg);
    }

    private void drawIdle(GuiGraphicsExtractor gg, float x, float y, float h,
                          double mx, double my, HFont title, HFont body, HFont label) {
        title.draw(gg, "Обучение наводки", x + PAD, y + 30.0f, Style.WHITE);
        body.draw(gg, "Придумай название и нажми «Обучить».", x + PAD, y + 52.0f, Style.WHITE_45);
        body.draw(gg, "Записывается только наводка, не удары.", x + PAD, y + 66.0f, Style.WHITE_45);

        // поле ввода
        float fx = x + PAD;
        float fy = y + 88.0f;
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
        body.draw(gg, shown, fx + 12.0f, body.centeredBaseline(fy, FIELD_H),
                empty ? Style.WHITE_25 : Style.WHITE);
        if (this.typing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caret = fx + 12.0f + body.width(this.name) + 1.0f;
            Render2D.rect(gg, caret, fy + 8.0f, 1.0f, FIELD_H - 16.0f, Style.WHITE_45);
        }

        // кнопка «Обучить»
        float bx = x + PAD;
        float by = y + 132.0f;
        float bw = PANEL_W - PAD * 2;
        boolean canStart = !this.name.isBlank();
        drawButton(gg, bx, by, bw, BTN_H, "Обучить", canStart,
                Render2D.hovered(mx, my, bx, by, bw, BTN_H), label);

        // профили
        float ly = y + 188.0f;
        label.draw(gg, "Сохранённые профили:", x + PAD, ly, Style.WHITE_45);
        float ry = ly + 22.0f;
        int rows = Math.min(MAX_ROWS, this.profiles.size());
        for (int i = 0; i < rows; i++) {
            drawProfileRow(gg, this.profiles.get(i), x + PAD, ry, bw, ROW_H, mx, my, body, label);
            ry += ROW_H + 4.0f;
        }
        if (this.profiles.isEmpty()) {
            body.draw(gg, "Пока пусто — обучи первую наводку.", x + PAD, ry + 10.0f, Style.WHITE_25);
        }

        drawClose(gg, x, y + h - 34.0f, bw, mx, my, label);
    }

    private void drawProfileRow(GuiGraphicsExtractor gg, AimStore.ProfileInfo p,
                                float x, float y, float w, float h,
                                double mx, double my, HFont body, HFont label) {
        boolean hover = Render2D.hovered(mx, my, x, y, w, h);
        boolean active = p.name.equals(this.parent.activeProfile());
        Render2D.round(gg, x, y, w, h, 8.0f,
                hover ? Style.white(0.07f) : Style.WHITE_04);
        if (active) {
            Render2D.round(gg, x, y, 3.0f, h, 1.5f, Style.accent());
        }
        body.draw(gg, p.name, x + 12.0f, body.centeredBaseline(y, h), Style.WHITE);
        String meta = p.samples + " сэмплов · " + (p.trained ? "обучена" : "не обучена");
        if (active) {
            meta += " · активна";
        }
        label.draw(gg, meta, x + 12.0f + body.width(p.name) + 10.0f,
                label.centeredBaseline(y, h), p.trained ? Style.WHITE_45 : Style.WHITE_25);

        // крестик удаления
        float dx = x + w - 26.0f;
        float dy = y + (h - 18.0f) / 2.0f;
        boolean dHover = Render2D.hovered(mx, my, dx - 4.0f, dy - 4.0f, 26.0f, 26.0f);
        label.drawCentered(gg, "✕", dx + 9.0f, label.centeredBaseline(dy, 18.0f),
                dHover ? Style.WHITE : Style.WHITE_25);
    }

    private void drawRecording(GuiGraphicsExtractor gg, float x, float y,
                               double mx, double my, HFont title, HFont body, HFont label) {
        title.draw(gg, "● Идёт запись", x + PAD, y + 30.0f, Style.accent());
        body.draw(gg, "«" + AimAssist.trainingName + "»", x + PAD, y + 54.0f, Style.WHITE);
        long secs = (System.currentTimeMillis() - AimAssist.trainingStartMs) / 1000L;
        body.draw(gg, AimAssist.trainingSamples + " сэмплов · "
                + String.format("%02d:%02d", secs / 60L, secs % 60L),
                x + PAD, y + 72.0f, Style.WHITE_45);
        body.draw(gg, "Наводись и бей — пишется только наводка.", x + PAD, y + 96.0f, Style.WHITE_45);
        body.draw(gg, "Нужно минимум " + AimAssist.MIN_SAMPLES + " сэмплов.", x + PAD, y + 112.0f,
                Style.WHITE_25);

        float bx = x + PAD;
        float by = y + 140.0f;
        float bw = PANEL_W - PAD * 2;
        drawButton(gg, bx, by, bw, BTN_H, "Стоп",
                AimAssist.trainingSamples >= AimAssist.MIN_SAMPLES,
                Render2D.hovered(mx, my, bx, by, bw, BTN_H), label);
        label.drawCentered(gg, "Меню закроется само после «Стоп»",
                bx + bw / 2.0f, by + BTN_H + 22.0f, Style.WHITE_25);
    }

    private void drawReady(GuiGraphicsExtractor gg, float x, float y,
                           double mx, double my, HFont title, HFont body, HFont label) {
        title.draw(gg, "Запись готова", x + PAD, y + 30.0f, Style.WHITE);
        body.draw(gg, "«" + this.parent.datasetName() + "» — "
                + this.parent.datasetSize() + " сэмплов", x + PAD, y + 54.0f, Style.WHITE_45);
        body.draw(gg, "Нейронка научится повторять твою наводку:", x + PAD, y + 78.0f, Style.WHITE_45);
        body.draw(gg, "скорость, плавность и манеру наведения.", x + PAD, y + 94.0f, Style.WHITE_45);

        String err = this.parent.learnStatus();
        if (!err.isEmpty()) {
            body.draw(gg, err, x + PAD, y + 118.0f, Style.WHITE_45);
        }

        float bx = x + PAD;
        float by = y + 148.0f;
        float bw = PANEL_W - PAD * 2;
        drawButton(gg, bx, by, bw, BTN_H, "Обучить нейронку", true,
                Render2D.hovered(mx, my, bx, by, bw, BTN_H), label);
        drawClose(gg, x, by + BTN_H + 16.0f, bw, mx, my, label);
    }

    private void drawLearning(GuiGraphicsExtractor gg, float x, float y,
                              double mx, double my, HFont title, HFont body, HFont label) {
        title.draw(gg, "Нейронка обучается…", x + PAD, y + 30.0f, Style.WHITE);
        body.draw(gg, this.parent.learnStatus(), x + PAD, y + 58.0f, Style.WHITE_45);

        float bx = x + PAD;
        float bw = PANEL_W - PAD * 2;
        float barY = y + 92.0f;
        Render2D.round(gg, bx, barY, bw, 6.0f, 3.0f, Style.WHITE_04);
        float p = (float) Math.min(1.0, Math.max(0.0, AimAssist.learnProgress));
        Render2D.round(gg, bx, barY, bw * p, 6.0f, 3.0f, Style.accent());

        label.drawCentered(gg, "Можно закрыть — обучение продолжится в фоне",
                bx + bw / 2.0f, barY + 30.0f, Style.WHITE_25);
        drawClose(gg, x, y + panelH() - 40.0f, bw, mx, my, label);
    }

    private void drawButton(GuiGraphicsExtractor gg, float x, float y, float w, float h,
                            String text, boolean enabled, boolean hover, HFont label) {
        this.btnAnim.to(hover && enabled);
        float a = this.btnAnim.get();
        int fill = enabled
                ? Render2D.lerp(Render2D.withAlpha(Style.accent(), 0.85f), Style.accent(), a)
                : Style.WHITE_04;
        Render2D.round(gg, x, y, w, h, 9.0f, fill);
        label.drawCentered(gg, text, x + w / 2.0f, label.centeredBaseline(y, h),
                enabled ? Style.PANEL : Style.WHITE_25);
    }

    private void drawClose(GuiGraphicsExtractor gg, float x, float y, float w,
                           double mx, double my, HFont label) {
        boolean hover = Render2D.hovered(mx, my, x, y, w, 20.0f);
        label.drawCentered(gg, "Закрыть", x + w / 2.0f, y + 13.0f,
                hover ? Style.WHITE : Style.WHITE_45);
    }

    // ---------------- ввод ----------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        float x = panelX();
        float y = panelY();
        float h = panelH();
        float bw = PANEL_W - PAD * 2;
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();

        if (recording()) {
            float by = y + 140.0f;
            if (Render2D.hovered(mx, my, x + PAD, by, bw, BTN_H)) {
                this.parent.stopTraining();
                closeToClickGui();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (learning()) {
            if (Render2D.hovered(mx, my, x + PAD, y + h - 40.0f, bw, 20.0f)) {
                closeToClickGui();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (ready()) {
            float by = y + 148.0f;
            if (Render2D.hovered(mx, my, x + PAD, by, bw, BTN_H)) {
                this.parent.startLearning();
                return true;
            }
            if (Render2D.hovered(mx, my, x + PAD, by + BTN_H + 16.0f, bw, 20.0f)) {
                closeToClickGui();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        // покой: поле, кнопка, профили, закрыть
        float fx = x + PAD;
        float fy = y + 88.0f;
        if (Render2D.hovered(mx, my, fx, fy, bw, FIELD_H)) {
            this.typing = true;
            return true;
        }
        this.typing = false;

        float by = y + 132.0f;
        if (Render2D.hovered(mx, my, fx, by, bw, BTN_H)) {
            submit();
            return true;
        }

        float ry = y + 188.0f + 22.0f;
        int rows = Math.min(MAX_ROWS, this.profiles.size());
        for (int i = 0; i < rows; i++) {
            AimStore.ProfileInfo p = this.profiles.get(i);
            if (Render2D.hovered(mx, my, fx, ry, bw, ROW_H)) {
                float dx = fx + bw - 26.0f;
                float dy = ry + (ROW_H - 18.0f) / 2.0f;
                if (Render2D.hovered(mx, my, dx - 4.0f, dy - 4.0f, 26.0f, 26.0f)) {
                    this.parent.deleteProfile(p.name);
                    this.profiles = AimStore.listProfiles();
                } else {
                    this.parent.activateProfile(p.name);
                    this.profiles = AimStore.listProfiles();
                }
                return true;
            }
            ry += ROW_H + 4.0f;
        }

        if (Render2D.hovered(mx, my, fx, y + h - 34.0f, bw, 20.0f)) {
            closeToClickGui();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.typing && !recording() && !learning() && !ready()) {
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
            closeToClickGui();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.typing || recording() || learning() || ready()) {
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
        // менюшка автоматически закрывается, сверху — прогресс
        this.minecraft.gui.setScreen(null);
    }

    private void closeToClickGui() {
        this.minecraft.gui.setScreen(new ClickGuiScreen());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(new ClickGuiScreen());
    }
}
