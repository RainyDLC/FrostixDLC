package rtx.kimiko.api.modules.impl.Visuals.emotions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.lang.I18n;
import rtx.kimiko.api.modules.impl.Visuals.Emotions;
import rtx.kimiko.api.ui.BaseScreen;
import rtx.kimiko.api.ui.module.SearchField;
import rtx.kimiko.api.ui.theme.ClientAccent;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.render2d.Render2D;

/**
 * Редактор пользовательских эмоций.
 * Живое превью персонажа, выбор части тела, слайдеры вращения/сдвига
 * по осям X/Y/Z и таймлайн с ромбиками ключевых кадров.
 */
public final class EmotionEditorScreen extends BaseScreen {
    private static final float ROT_MIN = -180.0f;
    private static final float ROT_MAX = 180.0f;
    private static final float OFF_MIN = -3.0f;
    private static final float OFF_MAX = 3.0f;
    private static final float SNAP_PX = 5.0f;

    private final Emotions module;
    private final CustomEmotion editing;
    /** Имя исходной эмоции в хранилище (null — создание новой). */
    private final String originalName;

    private final EmotionPose workPose = new EmotionPose();
    private final SearchField nameField = new SearchField().placeholder(I18n.tr("Название эмоции..."));

    private float scrubTime;
    private float lastSampleTime = -1.0f;
    private boolean playing;
    private int selectedPart;
    private CustomEmotion.Keyframe selectedKey;

    private long lastNanos = System.nanoTime();

    // drag-состояние
    private int dragSlider = -1; // индекс слайдера 0..5 внутри части
    private boolean dragKey;
    private boolean dragScrub;

    // layout (пересчитывается каждый кадр)
    private float hdrY, hdrH;
    private float prevX0, prevY0, prevX1, prevY1;
    private float panelX0, panelY0, panelX1, panelY1;
    private float tlX0, tlY0, tlX1, tlY1;
    private float trackX0, trackX1, trackY;
    private float playCX, playCY;
    private float addKeyX, addKeyY, addKeyW, addKeyH;
    private float delKeyX, delKeyY, delKeyW, delKeyH;
    private float saveX, saveY, saveW, saveH;
    private float closeX, closeY, closeW, closeH;
    private float nameX, nameY, nameW, nameH;
    private float durMinusX, durMinusY, durPlusX, durPlusY, durBox;
    private float deleteX, deleteY, deleteW, deleteH;
    private float sliderX0, sliderX1, sliderY0, sliderRowH;
    private float tabX0, tabY0, tabW, tabH, tabGap;

    public EmotionEditorScreen(@NotNull Emotions module) {
        this(module, null);
    }

    public EmotionEditorScreen(@NotNull Emotions module, @Nullable CustomEmotion source) {
        super(Text.literal("Emotion Editor"));
        this.module = module;
        if (source != null) {
            this.editing = source.copy();
            this.originalName = source.displayName();
        } else {
            this.editing = new CustomEmotion(I18n.tr("Моя эмоция"), 3.0f);
            this.originalName = null;
        }
        this.nameField.setText(this.editing.displayName());
        this.editing.sampleInto(this.workPose, 0.0f);
        this.selectedKey = this.editing.keyframeNear(0.0f, 0.03f);
    }

    // ---------- layout ----------

    private void computeLayout() {
        float w = Position.screenWidth();
        float h = Position.screenHeight();
        float pad = 14.0f;

        this.hdrY = 10.0f;
        this.hdrH = 32.0f;

        this.closeW = 30.0f;
        this.closeH = 30.0f;
        this.closeX = w - pad - this.closeW;
        this.closeY = this.hdrY + 1.0f;

        this.saveW = 118.0f;
        this.saveH = 30.0f;
        this.saveX = this.closeX - 8.0f - this.saveW;
        this.saveY = this.hdrY + 1.0f;

        this.nameX = pad + 196.0f;
        this.nameY = this.hdrY + 2.0f;
        this.nameW = 230.0f;
        this.nameH = 28.0f;

        this.durBox = 26.0f;
        this.durPlusX = this.saveX - 10.0f - this.durBox;
        this.durMinusX = this.durPlusX - 4.0f - this.durBox;
        this.durMinusY = this.durPlusY = this.hdrY + 3.0f;

        if (this.originalName != null) {
            this.deleteW = 118.0f;
            this.deleteH = 30.0f;
            this.deleteX = this.durMinusX - 10.0f - this.deleteW;
            this.deleteY = this.hdrY + 1.0f;
        }

        float top = this.hdrY + this.hdrH + 8.0f;
        float tlH = 108.0f;
        float bottom = h - tlH - pad;

        this.prevX0 = pad;
        this.prevX1 = w * 0.42f;
        this.prevY0 = top;
        this.prevY1 = bottom;

        this.panelX0 = this.prevX1 + 12.0f;
        this.panelX1 = w - pad;
        this.panelY0 = top;
        this.panelY1 = bottom;

        this.tlX0 = pad;
        this.tlX1 = w - pad;
        this.tlY0 = h - pad - tlH;
        this.tlY1 = h - pad;

        // таймлайн: строка кнопок + трек
        this.playCX = this.tlX0 + 26.0f;
        this.playCY = this.tlY0 + 24.0f;
        this.addKeyW = 86.0f;
        this.addKeyH = 26.0f;
        this.addKeyX = this.tlX0 + 52.0f;
        this.addKeyY = this.tlY0 + 11.0f;
        this.delKeyW = 86.0f;
        this.delKeyH = 26.0f;
        this.delKeyX = this.addKeyX + this.addKeyW + 8.0f;
        this.delKeyY = this.tlY0 + 11.0f;
        this.trackX0 = this.tlX0 + 52.0f;
        this.trackX1 = this.tlX1 - 10.0f;
        this.trackY = this.tlY0 + 72.0f;

        // правая панель: табы частей + слайдеры
        this.tabX0 = this.panelX0 + 12.0f;
        this.tabY0 = this.panelY0 + 34.0f;
        this.tabGap = 6.0f;
        int perRow = 4;
        this.tabW = (this.panelX1 - this.panelX0 - 24.0f - this.tabGap * (perRow - 1)) / perRow;
        this.tabH = 26.0f;

        this.sliderX0 = this.panelX0 + 12.0f;
        this.sliderX1 = this.panelX1 - 12.0f;
        this.sliderY0 = this.tabY0 + this.tabH * 2.0f + this.tabGap + 30.0f;
        this.sliderRowH = 40.0f;
    }

    private static int col(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float timeToX(float t) {
        float d = Math.max(0.001f, this.editing.duration());
        return this.trackX0 + clamp(t / d, 0.0f, 1.0f) * (this.trackX1 - this.trackX0);
    }

    private float xToTime(float x) {
        float d = this.editing.duration();
        return clamp((x - this.trackX0) / Math.max(1.0f, this.trackX1 - this.trackX0), 0.0f, 1.0f) * d;
    }

    // ---------- render ----------

    @Override
    protected void renderScreen(@NotNull DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        this.computeLayout();
        float mx = Position.mouseX();
        float my = Position.mouseY();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (float) (now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;

        if (this.playing) {
            this.scrubTime += dt;
            if (this.scrubTime > this.editing.duration()) {
                this.scrubTime = 0.0f;
            }
        }
        if (this.scrubTime != this.lastSampleTime) {
            this.editing.sampleInto(this.workPose, this.scrubTime);
            this.lastSampleTime = this.scrubTime;
        }

        float w = Position.screenWidth();
        float h = Position.screenHeight();
        Render2D.rect(0.0f, 0.0f, w, h, 0.0f, col(7, 9, 14, 236));

        this.renderHeader(graphics, mx, my, dt);
        this.renderPreview(graphics, mx, my);
        this.renderPanel(graphics, mx, my);
        this.renderTimeline(graphics, mx, my);
    }

    private void renderHeader(DrawContext g, float mx, float my, float dt) {
        float titleSize = 13.0f;
        Fonts.SEMIBOLD.draw(I18n.tr("Редактор эмоций"), 14.0f, this.hdrY + 8.0f, titleSize, col(255, 255, 255, 240));
        Fonts.MEDIUM.draw(I18n.tr("Название:"), 14.0f + 178.0f, this.hdrY + 13.0f, 7.0f, col(255, 255, 255, 130));
        this.nameField.render(g, this.nameX, this.nameY, this.nameW, this.nameH, 1.0f, mx, my, dt);

        // длительность: − 3.0с +
        String durLabel = I18n.tr("Длит.:");
        Fonts.MEDIUM.draw(durLabel, this.durMinusX - 8.0f - Fonts.MEDIUM.width(durLabel, 7.0f), this.hdrY + 13.0f, 7.0f, col(255, 255, 255, 130));
        this.smallButton(g, this.durMinusX, this.durMinusY, this.durBox, this.durBox, "−", hit(mx, my, this.durMinusX, this.durMinusY, this.durBox, this.durBox), false);
        this.smallButton(g, this.durPlusX, this.durPlusY, this.durBox, this.durBox, "+", hit(mx, my, this.durPlusX, this.durPlusY, this.durBox, this.durBox), false);
        String durVal = String.format("%.1fс", this.editing.duration());
        float dvw = Fonts.SEMIBOLD.width(durVal, 8.0f);
        Fonts.SEMIBOLD.draw(durVal, this.durMinusX + this.durBox + (this.durPlusX - this.durMinusX - this.durBox - dvw) * 0.5f - 2.0f, this.hdrY + 11.0f, 8.0f, col(255, 255, 255, 220));

        if (this.originalName != null) {
            this.smallButton(g, this.deleteX, this.deleteY, this.deleteW, this.deleteH, I18n.tr("Удалить"), hit(mx, my, this.deleteX, this.deleteY, this.deleteW, this.deleteH), false);
        }
        this.smallButton(g, this.saveX, this.saveY, this.saveW, this.saveH, I18n.tr("Сохранить"), hit(mx, my, this.saveX, this.saveY, this.saveW, this.saveH), true);
        this.smallButton(g, this.closeX, this.closeY, this.closeW, this.closeH, "✕", hit(mx, my, this.closeX, this.closeY, this.closeW, this.closeH), false);
    }

    private void renderPreview(DrawContext g, float mx, float my) {
        Render2D.rect(this.prevX0, this.prevY0, this.prevX1 - this.prevX0, this.prevY1 - this.prevY0, 10.0f, col(13, 16, 23, 255));
        Render2D.outline(this.prevX0, this.prevY0, this.prevX1 - this.prevX0, this.prevY1 - this.prevY0, 10.0f, 1.0f, col(255, 255, 255, 18));

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            float pad = 10.0f;
            int x0 = (int) (this.prevX0 + pad);
            int y0 = (int) (this.prevY0 + pad);
            int x1 = (int) (this.prevX1 - pad);
            int y1 = (int) (this.prevY1 - pad);
            int size = Math.max(20, (int) ((y1 - y0) * 0.34f));
            float cx = (x0 + x1) * 0.5f;
            float cy = (y0 + y1) * 0.5f;
            EmotionPlayback.beginPreview(this.editing, this.scrubTime);
            try {
                InventoryScreen.drawEntity(g, x0, y0, x1, y1, size, 1.0f, cx, cy, (LivingEntity) mc.player);
            } finally {
                EmotionPlayback.endPreview();
            }
        }
        String hint = I18n.tr("Живое превью");
        Fonts.MEDIUM.draw(hint, this.prevX0 + 12.0f, this.prevY0 + 10.0f, 7.0f, col(255, 255, 255, 110));
        if (this.playing) {
            String t = String.format("%.2f / %.1f с", this.scrubTime, this.editing.duration());
            float tw = Fonts.MEDIUM.width(t, 7.0f);
            Fonts.MEDIUM.draw(t, this.prevX1 - 12.0f - tw, this.prevY0 + 10.0f, 7.0f, col(255, 255, 255, 140));
        }
    }

    private void renderPanel(DrawContext g, float mx, float my) {
        Render2D.rect(this.panelX0, this.panelY0, this.panelX1 - this.panelX0, this.panelY1 - this.panelY0, 10.0f, col(13, 16, 23, 255));
        Render2D.outline(this.panelX0, this.panelY0, this.panelX1 - this.panelX0, this.panelY1 - this.panelY0, 10.0f, 1.0f, col(255, 255, 255, 18));

        String title = I18n.tr("Часть тела");
        Fonts.SEMIBOLD.draw(title, this.panelX0 + 12.0f, this.panelY0 + 12.0f, 9.0f, col(255, 255, 255, 235));

        // табы частей: 2 ряда по 4
        for (int part = 0; part < CustomEmotion.PART_COUNT; part++) {
            int row = part / 4;
            int colIdx = part % 4;
            float x = this.tabX0 + colIdx * (this.tabW + this.tabGap);
            float y = this.tabY0 + row * (this.tabH + this.tabGap);
            boolean sel = part == this.selectedPart;
            boolean hov = hit(mx, my, x, y, this.tabW, this.tabH);
            int bg = sel ? ClientAccent.accentSoftAt(235.0f, x, y) : hov ? col(30, 36, 48, 255) : col(20, 24, 34, 255);
            Render2D.rect(x, y, this.tabW, this.tabH, 7.0f, bg);
            String name = I18n.tr(CustomEmotion.PART_NAMES[part]);
            float ns = 7.5f;
            float nw = Fonts.SEMIBOLD.width(name, ns);
            int tc = sel ? col(10, 12, 18, 255) : col(255, 255, 255, 200);
            Fonts.SEMIBOLD.draw(name, x + (this.tabW - nw) * 0.5f, y + (this.tabH - ns) * 0.5f - 1.0f, ns, tc);
        }

        // слайдеры выбранной части
        int[] fields = CustomEmotion.PART_FIELD_INDICES[this.selectedPart];
        String[] labels = sliderLabels(this.selectedPart);
        for (int i = 0; i < fields.length; i++) {
            boolean isRot = this.selectedPart != CustomEmotion.PART_UPPER && i < 3;
            this.renderSlider(g, i, fields[i], labels[i], isRot, mx, my);
        }

        String hint = I18n.tr("Двигайте слайдеры — ключ создастся сам. Ромбики на таймлайне можно таскать.");
        Fonts.MEDIUM.draw(hint, this.panelX0 + 12.0f, this.panelY1 - 20.0f, 6.5f, col(255, 255, 255, 100));
    }

    private static String[] sliderLabels(int part) {
        if (part == CustomEmotion.PART_UPPER) {
            return new String[]{"Сдвиг Y", "Сдвиг Z"};
        }
        return new String[]{"Поворот X", "Поворот Y", "Поворот Z", "Сдвиг X", "Сдвиг Y", "Сдвиг Z"};
    }

    private void renderSlider(DrawContext g, int sliderIdx, int fieldIdx, String label, boolean isRot, float mx, float my) {
        float y = this.sliderY0 + sliderIdx * this.sliderRowH;
        float labelW = 92.0f;
        float valueW = 56.0f;
        float tx0 = this.sliderX0 + labelW;
        float tx1 = this.sliderX1 - valueW;
        float cy = y + 20.0f;

        float min = isRot ? ROT_MIN : OFF_MIN;
        float max = isRot ? ROT_MAX : OFF_MAX;
        float value = CustomEmotion.getValue(this.workPose, fieldIdx);

        Fonts.MEDIUM.draw(I18n.tr(label), this.sliderX0, cy - 4.5f, 7.0f, col(255, 255, 255, 190));
        // трек
        Render2D.rect(tx0, cy - 2.0f, tx1 - tx0, 4.0f, 2.0f, col(28, 33, 45, 255));
        float f = clamp((value - min) / (max - min), 0.0f, 1.0f);
        float kx = tx0 + f * (tx1 - tx0);
        boolean hov = this.dragSlider == sliderIdx || hit(mx, my, tx0 - 6.0f, cy - 10.0f, (tx1 - tx0) + 12.0f, 20.0f);
        Render2D.rect(tx0, cy - 2.0f, kx - tx0, 4.0f, 2.0f, ClientAccent.accentSoftAt(220.0f, tx0, cy));
        Render2D.circle(kx, cy, hov ? 7.0f : 5.5f, hov ? ClientAccent.accentSoftAt(255.0f, kx, cy) : col(225, 230, 240, 255));
        // значение
        String vs = isRot ? String.format("%.0f°", value) : String.format("%.2f", value);
        float vw = Fonts.SEMIBOLD.width(vs, 7.5f);
        Fonts.SEMIBOLD.draw(vs, tx1 + valueW - vw, cy - 5.0f, 7.5f, col(255, 255, 255, 220));
    }

    private void renderTimeline(DrawContext g, float mx, float my) {
        Render2D.rect(this.tlX0, this.tlY0, this.tlX1 - this.tlX0, this.tlY1 - this.tlY0, 10.0f, col(13, 16, 23, 255));
        Render2D.outline(this.tlX0, this.tlY0, this.tlX1 - this.tlX0, this.tlY1 - this.tlY0, 10.0f, 1.0f, col(255, 255, 255, 18));

        // play / pause
        boolean playHov = hit(mx, my, this.playCX - 14.0f, this.playCY - 14.0f, 28.0f, 28.0f);
        Render2D.circle(this.playCX, this.playCY, 13.0f, playHov ? col(34, 40, 55, 255) : col(22, 27, 38, 255));
        if (this.playing) {
            Render2D.rect(this.playCX - 5.0f, this.playCY - 6.0f, 4.0f, 12.0f, 1.0f, col(255, 255, 255, 230));
            Render2D.rect(this.playCX + 1.0f, this.playCY - 6.0f, 4.0f, 12.0f, 1.0f, col(255, 255, 255, 230));
        } else {
            this.triangle(this.playCX - 4.0f, this.playCY - 7.0f, this.playCX - 4.0f, this.playCY + 7.0f, this.playCX + 8.0f, this.playCY, col(255, 255, 255, 230));
        }

        this.smallButton(g, this.addKeyX, this.addKeyY, this.addKeyW, this.addKeyH, "+ " + I18n.tr("Ключ"), hit(mx, my, this.addKeyX, this.addKeyY, this.addKeyW, this.addKeyH), false);
        this.smallButton(g, this.delKeyX, this.delKeyY, this.delKeyW, this.delKeyH, "− " + I18n.tr("Ключ"), hit(mx, my, this.delKeyX, this.delKeyY, this.delKeyW, this.delKeyH), false);

        String timeLabel = String.format("%.2f / %.1f с", this.scrubTime, this.editing.duration());
        float tlw = Fonts.MEDIUM.width(timeLabel, 7.5f);
        Fonts.MEDIUM.draw(timeLabel, this.tlX1 - 12.0f - tlw, this.tlY0 + 16.0f, 7.5f, col(255, 255, 255, 170));
        String keyLabel = I18n.tr("Ключей:") + " " + this.editing.keyframes().size();
        Fonts.MEDIUM.draw(keyLabel, this.delKeyX + this.delKeyW + 12.0f, this.tlY0 + 18.0f, 7.0f, col(255, 255, 255, 120));
        if (this.selectedKey != null) {
            String sel = String.format(I18n.tr("Ключ @ %.2fс"), this.selectedKey.time);
            Fonts.MEDIUM.draw(sel, this.delKeyX + this.delKeyW + 12.0f + Fonts.MEDIUM.width(keyLabel, 7.0f) + 14.0f, this.tlY0 + 18.0f, 7.0f, ClientAccent.accentSoftAt(200.0f, 0.0f, 0.0f));
        }

        // трек
        float ty = this.trackY;
        Render2D.rect(this.trackX0, ty - 14.0f, this.trackX1 - this.trackX0, 28.0f, 6.0f, col(18, 22, 32, 255));
        Render2D.line(this.trackX0, ty, this.trackX1, ty, 1.5f, col(255, 255, 255, 40));
        // риски шкалы
        float dur = this.editing.duration();
        float step = dur > 6.0f ? 1.0f : 0.5f;
        for (float t = 0.0f; t <= dur + 0.001f; t += step) {
            float x = this.timeToX(t);
            Render2D.line(x, ty - 8.0f, x, ty - 3.0f, 1.0f, col(255, 255, 255, 70));
            if (Math.abs(t - Math.round(t)) < 0.001f) {
                String s = String.format("%.0f", t);
                Fonts.MEDIUM.draw(s, x - Fonts.MEDIUM.width(s, 6.0f) * 0.5f, ty + 6.0f, 6.0f, col(255, 255, 255, 90));
            }
        }
        // ромбики ключей
        for (CustomEmotion.Keyframe key : this.editing.keyframes()) {
            float x = this.timeToX(key.time);
            boolean sel = key == this.selectedKey;
            boolean hov = Math.abs(mx - x) <= 9.0f && Math.abs(my - ty) <= 11.0f;
            float r = sel ? 6.5f : hov ? 6.0f : 5.0f;
            int c = sel ? ClientAccent.accentSoftAt(255.0f, x, ty) : hov ? col(255, 255, 255, 255) : col(160, 170, 190, 255);
            this.diamond(x, ty, r, c);
            if (sel) {
                Render2D.circleOutline(x, ty, r + 3.5f, 1.2f, ClientAccent.accentSoftAt(160.0f, x, ty));
            }
        }
        // курсор
        float ccx = this.timeToX(this.scrubTime);
        Render2D.line(ccx, ty - 13.0f, ccx, ty + 13.0f, 2.0f, ClientAccent.accentSoftAt(255.0f, ccx, ty));
        Render2D.circle(ccx, ty - 13.0f, 3.0f, ClientAccent.accentSoftAt(255.0f, ccx, ty));
    }

    // ---------- примитивы ----------

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void smallButton(DrawContext g, float x, float y, float w, float h, String label, boolean hovered, boolean accent) {
        int bg = accent ? ClientAccent.accentSoftAt(hovered ? 255.0f : 215.0f, x, y)
                : hovered ? col(34, 40, 55, 255) : col(22, 27, 38, 255);
        Render2D.rect(x, y, w, h, 7.0f, bg);
        float s = 7.5f;
        float lw = Fonts.SEMIBOLD.width(label, s);
        int tc = accent ? col(10, 12, 18, 255) : col(255, 255, 255, 210);
        Fonts.SEMIBOLD.draw(label, x + (w - lw) * 0.5f, y + (h - s) * 0.5f - 1.0f, s, tc);
    }

    /** Залитый ромбик (ключевой кадр). */
    private void diamond(float cx, float cy, float r, int color) {
        int ri = (int) Math.ceil(r);
        for (int i = -ri; i <= ri; i++) {
            float hw = r - Math.abs(i);
            if (hw > 0.4f) {
                Render2D.line(cx - hw, cy + i, cx + hw, cy + i, 1.0f, color);
            }
        }
    }

    /** Залитый треугольник. */
    private void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        float minY = Math.min(y1, Math.min(y2, y3));
        float maxY = Math.max(y1, Math.max(y2, y3));
        for (float y = minY; y <= maxY; y += 1.0f) {
            float[] xs = new float[4];
            int n = 0;
            float[][] pts = {{x1, y1}, {x2, y2}, {x3, y3}};
            for (int e = 0; e < 3; e++) {
                float[] a = pts[e];
                float[] b = pts[(e + 1) % 3];
                if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) {
                    xs[n++] = a[0] + (y - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                }
            }
            if (n >= 2) {
                float xa = Math.min(xs[0], xs[1]);
                float xb = Math.max(xs[0], xs[1]);
                Render2D.line(xa, y, xb, y, 1.0f, color);
            }
        }
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(@NotNull Click event, boolean doubleClick) {
        this.computeLayout();
        float mx = Position.mouseX();
        float my = Position.mouseY();
        int button = event.button();

        if (this.nameField.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 1) {
            // ПКМ по ромбику — удалить ключ
            CustomEmotion.Keyframe key = this.keyAt(mx, my);
            if (key != null) {
                this.editing.removeKeyframe(key);
                if (this.selectedKey == key) {
                    this.selectedKey = null;
                }
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (button != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        // шапка
        if (hit(mx, my, this.closeX, this.closeY, this.closeW, this.closeH)) {
            this.backToWheel();
            return true;
        }
        if (hit(mx, my, this.saveX, this.saveY, this.saveW, this.saveH)) {
            this.saveAndClose();
            return true;
        }
        if (hit(mx, my, this.durMinusX, this.durMinusY, this.durBox, this.durBox)) {
            this.editing.setDuration(this.editing.duration() - 0.5f);
            this.scrubTime = clamp(this.scrubTime, 0.0f, this.editing.duration());
            return true;
        }
        if (hit(mx, my, this.durPlusX, this.durPlusY, this.durBox, this.durBox)) {
            this.editing.setDuration(this.editing.duration() + 0.5f);
            return true;
        }
        if (this.originalName != null && hit(mx, my, this.deleteX, this.deleteY, this.deleteW, this.deleteH)) {
            CustomEmotion old = CustomEmotionStore.get().byName(this.originalName);
            if (old != null) {
                CustomEmotionStore.get().remove(old);
            }
            this.backToWheel();
            return true;
        }

        // табы частей тела
        for (int part = 0; part < CustomEmotion.PART_COUNT; part++) {
            int row = part / 4;
            int colIdx = part % 4;
            float x = this.tabX0 + colIdx * (this.tabW + this.tabGap);
            float y = this.tabY0 + row * (this.tabH + this.tabGap);
            if (hit(mx, my, x, y, this.tabW, this.tabH)) {
                this.selectedPart = part;
                this.dragSlider = -1;
                return true;
            }
        }

        // слайдеры
        int[] fields = CustomEmotion.PART_FIELD_INDICES[this.selectedPart];
        for (int i = 0; i < fields.length; i++) {
            float y = this.sliderY0 + i * this.sliderRowH;
            float tx0 = this.sliderX0 + 92.0f;
            float tx1 = this.sliderX1 - 56.0f;
            float cy = y + 20.0f;
            if (hit(mx, my, tx0 - 8.0f, cy - 11.0f, (tx1 - tx0) + 16.0f, 22.0f)) {
                this.dragSlider = i;
                this.applySlider(i, mx);
                return true;
            }
        }

        // таймлайн: play
        if (hit(mx, my, this.playCX - 14.0f, this.playCY - 14.0f, 28.0f, 28.0f)) {
            this.playing = !this.playing;
            if (this.playing && this.scrubTime >= this.editing.duration() - 0.001f) {
                this.scrubTime = 0.0f;
            }
            return true;
        }
        if (hit(mx, my, this.addKeyX, this.addKeyY, this.addKeyW, this.addKeyH)) {
            this.selectedKey = this.editing.upsertKeyframe(this.scrubTime, CustomEmotion.snapshot(this.workPose));
            return true;
        }
        if (hit(mx, my, this.delKeyX, this.delKeyY, this.delKeyW, this.delKeyH)) {
            if (this.selectedKey != null) {
                this.editing.removeKeyframe(this.selectedKey);
                this.selectedKey = null;
            }
            return true;
        }
        // трек таймлайна
        if (my >= this.trackY - 16.0f && my <= this.trackY + 16.0f && mx >= this.trackX0 - 6.0f && mx <= this.trackX1 + 6.0f) {
            CustomEmotion.Keyframe key = this.keyAt(mx, my);
            if (key != null) {
                this.selectedKey = key;
                this.scrubTime = key.time;
                this.dragKey = true;
            } else {
                float t = this.xToTime(mx);
                // снап к ближайшему ключу в пределах нескольких пикселей
                CustomEmotion.Keyframe snap = null;
                float snapDist = SNAP_PX;
                for (CustomEmotion.Keyframe k : this.editing.keyframes()) {
                    float d = Math.abs(this.timeToX(k.time) - mx);
                    if (d <= snapDist) {
                        snapDist = d;
                        snap = k;
                    }
                }
                if (snap != null) {
                    t = snap.time;
                    this.selectedKey = snap;
                } else {
                    this.selectedKey = this.editing.keyframeNear(t, 0.03f);
                }
                this.scrubTime = t;
                this.dragScrub = true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NotNull Click event, double dragX, double dragY) {
        float mx = Position.mouseX();
        if (this.dragSlider >= 0) {
            this.applySlider(this.dragSlider, mx);
            return true;
        }
        if (this.dragKey && this.selectedKey != null) {
            float t = this.xToTime(mx);
            this.selectedKey.time = clamp(t, 0.0f, this.editing.duration());
            this.scrubTime = this.selectedKey.time;
            // переупорядочить через удаление/вставку
            CustomEmotion.Keyframe key = this.selectedKey;
            this.editing.removeKeyframe(key);
            this.selectedKey = this.editing.upsertKeyframe(key.time, key.values);
            return true;
        }
        if (this.dragScrub) {
            this.scrubTime = this.xToTime(mx);
            this.selectedKey = this.editing.keyframeNear(this.scrubTime, 0.03f);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NotNull Click event) {
        this.dragSlider = -1;
        this.dragKey = false;
        this.dragScrub = false;
        this.nameField.mouseReleased(event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float mx = Position.mouseX();
        float my = Position.mouseY();
        if (my >= this.trackY - 18.0f && my <= this.trackY + 18.0f && mx >= this.trackX0 && mx <= this.trackX1) {
            this.scrubTime = clamp(this.scrubTime + (float) scrollY * 0.2f, 0.0f, this.editing.duration());
            this.selectedKey = this.editing.keyframeNear(this.scrubTime, 0.03f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NotNull KeyInput event) {
        if (this.nameField.isTyping()) {
            if (this.nameField.keyPressed(event)) {
                return true;
            }
        } else {
            int key = event.key();
            if ((key == 261 || key == 259) && this.selectedKey != null) {
                this.editing.removeKeyframe(this.selectedKey);
                this.selectedKey = null;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharInput event) {
        if (this.nameField.isTyping() && this.nameField.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void close() {
        this.backToWheel();
    }

    // ---------- действия ----------

    private void applySlider(int sliderIdx, float mx) {
        int[] fields = CustomEmotion.PART_FIELD_INDICES[this.selectedPart];
        if (sliderIdx < 0 || sliderIdx >= fields.length) {
            return;
        }
        boolean isRot = this.selectedPart != CustomEmotion.PART_UPPER && sliderIdx < 3;
        float min = isRot ? ROT_MIN : OFF_MIN;
        float max = isRot ? ROT_MAX : OFF_MAX;
        float tx0 = this.sliderX0 + 92.0f;
        float tx1 = this.sliderX1 - 56.0f;
        float f = clamp((mx - tx0) / Math.max(1.0f, tx1 - tx0), 0.0f, 1.0f);
        float value = min + f * (max - min);
        if (isRot) {
            value = Math.round(value);
        } else {
            value = Math.round(value * 100.0f) / 100.0f;
        }
        CustomEmotion.setValue(this.workPose, fields[sliderIdx], value);
        // авто-ключ: поза сразу сохраняется в анимацию
        this.selectedKey = this.editing.upsertKeyframe(this.scrubTime, CustomEmotion.snapshot(this.workPose));
        this.lastSampleTime = this.scrubTime;
    }

    private CustomEmotion.Keyframe keyAt(float mx, float my) {
        if (Math.abs(my - this.trackY) > 11.0f) {
            return null;
        }
        CustomEmotion.Keyframe best = null;
        float bestDist = 9.0f;
        for (CustomEmotion.Keyframe key : this.editing.keyframes()) {
            float d = Math.abs(this.timeToX(key.time) - mx);
            if (d <= bestDist) {
                bestDist = d;
                best = key;
            }
        }
        return best;
    }

    private void saveAndClose() {
        String name = this.nameField.getText();
        if (name != null) {
            name = name.trim();
        }
        if (name == null || name.isEmpty()) {
            name = this.editing.displayName();
        }
        this.editing.setName(name);
        CustomEmotionStore store = CustomEmotionStore.get();
        if (this.originalName != null && !this.originalName.equalsIgnoreCase(name)) {
            CustomEmotion old = store.byName(this.originalName);
            if (old != null) {
                store.remove(old);
            }
        }
        store.addOrReplace(this.editing);
        this.backToWheel();
    }

    private void backToWheel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new EmotionWheelScreen(this.module, this.module.wheel()));
    }
}
