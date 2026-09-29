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
import rtx.kimiko.api.ui.settings.RenderHelper;
import rtx.kimiko.api.ui.theme.ClientAccent;
import rtx.kimiko.utils.color.ColorEngine;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.others.RectUtil;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.render.util.scissor.ScissorUtil;
import rtx.kimiko.utils.sounds.Sounds;

/**
 * Редактор пользовательских эмоций.
 * Выполнен в фирменном полупрозрачном glassmorphism-дизайне Kimiko (как и колесо эмоций):
 * матовые стеклянные карточки, динамические акценты темы, плавные переходы при наведении,
 * живое превью персонажа с защитным scissor-клиппингом и голографическим пьедесталом,
 * интерактивные слайдеры с индикацией осей X/Y/Z и таймлайн ключевых кадров.
 */
public final class EmotionEditorScreen extends BaseScreen {
    private static final float ROT_MIN = -180.0f;
    private static final float ROT_MAX = 180.0f;
    private static final float OFF_MIN = -3.0f;
    private static final float OFF_MAX = 3.0f;
    private static final float SNAP_PX = 6.0f;

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
    private float animTime = 0.0f;
    private float openAnim = 0.0f;

    // drag-состояние
    private int dragSlider = -1; // индекс слайдера 0..5 внутри части
    private boolean dragKey;
    private boolean dragScrub;

    // Анимации наведения (smooth approach)
    private float hoverClose;
    private float hoverSave;
    private float hoverDelete;
    private float hoverDurMinus;
    private float hoverDurPlus;
    private float hoverPlay;
    private float hoverAddKey;
    private float hoverDelKey;
    private final float[] tabHovers = new float[CustomEmotion.PART_COUNT];
    private final float[] sliderHovers = new float[6];

    // layout (пересчитывается каждый кадр)
    private float hdrX, hdrY, hdrW, hdrH;
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
    private float durPillX, durPillY, durPillW, durPillH;
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

    @Override
    protected void init() {
        super.init();
        Sounds.play("gui_open");
    }

    // ---------- layout ----------

    private void computeLayout() {
        float w = Position.screenWidth();
        float h = Position.screenHeight();
        float pad = 12.0f;

        this.hdrX = pad;
        this.hdrY = 8.0f;
        this.hdrW = w - pad * 2.0f;
        this.hdrH = 34.0f;

        this.closeW = 26.0f;
        this.closeH = 26.0f;
        this.closeX = this.hdrX + this.hdrW - 6.0f - this.closeW;
        this.closeY = this.hdrY + (this.hdrH - this.closeH) * 0.5f;

        this.saveW = 104.0f;
        this.saveH = 24.0f;
        this.saveX = this.closeX - 8.0f - this.saveW;
        this.saveY = this.hdrY + (this.hdrH - this.saveH) * 0.5f;

        if (this.originalName != null) {
            this.deleteW = 88.0f;
            this.deleteH = 24.0f;
            this.deleteX = this.saveX - 6.0f - this.deleteW;
            this.deleteY = this.hdrY + (this.hdrH - this.deleteH) * 0.5f;
        }

        // Duration widget [ − | 3.0с | + ]
        this.durBox = 20.0f;
        float durRight = (this.originalName != null ? this.deleteX : this.saveX) - 10.0f;
        this.durPillW = 96.0f;
        this.durPillH = 24.0f;
        this.durPillX = durRight - this.durPillW;
        this.durPillY = this.hdrY + (this.hdrH - this.durPillH) * 0.5f;

        this.durMinusX = this.durPillX + 2.0f;
        this.durMinusY = this.durPillY + (this.durPillH - this.durBox) * 0.5f;
        this.durPlusX = this.durPillX + this.durPillW - 2.0f - this.durBox;
        this.durPlusY = this.durMinusY;

        // Search / Name field
        float titleSectionW = 175.0f;
        this.nameX = this.hdrX + titleSectionW;
        this.nameY = this.hdrY + (this.hdrH - 24.0f) * 0.5f;
        this.nameW = Math.max(120.0f, Math.min(230.0f, this.durPillX - this.nameX - 12.0f));
        this.nameH = 24.0f;

        float top = this.hdrY + this.hdrH + 8.0f;
        float tlH = 112.0f;
        float bottom = h - tlH - pad;

        this.prevX0 = pad;
        this.prevX1 = Math.round(w * 0.40f);
        this.prevY0 = top;
        this.prevY1 = bottom;

        this.panelX0 = this.prevX1 + 10.0f;
        this.panelX1 = w - pad;
        this.panelY0 = top;
        this.panelY1 = bottom;

        this.tlX0 = pad;
        this.tlX1 = w - pad;
        this.tlY0 = h - pad - tlH;
        this.tlY1 = h - pad;

        // Timeline inside
        this.playCX = this.tlX0 + 26.0f;
        this.playCY = this.tlY0 + 21.0f;

        this.addKeyW = 78.0f;
        this.addKeyH = 22.0f;
        this.addKeyX = this.tlX0 + 48.0f;
        this.addKeyY = this.tlY0 + 10.0f;

        this.delKeyW = 78.0f;
        this.delKeyH = 22.0f;
        this.delKeyX = this.addKeyX + this.addKeyW + 6.0f;
        this.delKeyY = this.tlY0 + 10.0f;

        this.trackX0 = this.tlX0 + 48.0f;
        this.trackX1 = this.tlX1 - 14.0f;
        this.trackY = this.tlY0 + 72.0f;

        // Tabs inside right panel
        this.tabX0 = this.panelX0 + 12.0f;
        this.tabY0 = this.panelY0 + 34.0f;
        this.tabGap = 6.0f;
        int perRow = 4;
        this.tabW = (this.panelX1 - this.panelX0 - 24.0f - this.tabGap * (perRow - 1)) / perRow;
        this.tabH = 24.0f;

        this.sliderX0 = this.panelX0 + 14.0f;
        this.sliderX1 = this.panelX1 - 14.0f;
        this.sliderY0 = this.tabY0 + this.tabH * 2.0f + this.tabGap + 16.0f;
        this.sliderRowH = Math.min(38.0f, (this.panelY1 - 24.0f - this.sliderY0) / 6.0f);
    }

    private static int color(int r, int g, int b, int a, float mult) {
        int alpha = Math.max(0, Math.min(255, Math.round((float) a * mult)));
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int col(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1.0f - (float) Math.exp(-dt * speed));
    }

    private float timeToX(float t) {
        float d = Math.max(0.001f, this.editing.duration());
        return this.trackX0 + clamp(t / d, 0.0f, 1.0f) * (this.trackX1 - this.trackX0);
    }

    private float xToTime(float x) {
        float d = this.editing.duration();
        return clamp((x - this.trackX0) / Math.max(1.0f, this.trackX1 - this.trackX0), 0.0f, 1.0f) * d;
    }

    private static boolean hasNonZeroValues(int part, EmotionPose pose) {
        int[] fields = CustomEmotion.PART_FIELD_INDICES[part];
        for (int f : fields) {
            float val = CustomEmotion.getValue(pose, f);
            if (Math.abs(val) > 0.001f) {
                return true;
            }
        }
        return false;
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
        this.animTime += dt;

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

        // Плавная анимация входа (как у колеса эмоций)
        this.openAnim = approach(this.openAnim, 1.0f, dt, 12.0f);
        float alpha = Math.min(1.0f, this.openAnim);

        this.updateHovers(mx, my, dt);

        float w = Position.screenWidth();
        float h = Position.screenHeight();

        // Затемнение фона
        Render2D.rect(-10.0f, -10.0f, w + 20.0f, h + 20.0f, 0.0f, color(0, 0, 0, 115, alpha));

        // Лёгкое масштабирование при открытии
        float scale = 0.98f + 0.02f * alpha;
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        graphics.getMatrices().pushMatrix();
        graphics.getMatrices().translate(cx, cy);
        graphics.getMatrices().scale(scale, scale);
        graphics.getMatrices().translate(-cx, -cy);

        this.renderHeader(graphics, mx, my, dt, alpha);
        this.renderPreview(graphics, mx, my, alpha);
        this.renderPanel(graphics, mx, my, alpha);
        this.renderTimeline(graphics, mx, my, alpha);

        graphics.getMatrices().popMatrix();
    }

    private void updateHovers(float mx, float my, float dt) {
        this.hoverClose = approach(this.hoverClose, hit(mx, my, this.closeX, this.closeY, this.closeW, this.closeH) ? 1.0f : 0.0f, dt, 15.0f);
        this.hoverSave = approach(this.hoverSave, hit(mx, my, this.saveX, this.saveY, this.saveW, this.saveH) ? 1.0f : 0.0f, dt, 15.0f);
        if (this.originalName != null) {
            this.hoverDelete = approach(this.hoverDelete, hit(mx, my, this.deleteX, this.deleteY, this.deleteW, this.deleteH) ? 1.0f : 0.0f, dt, 15.0f);
        }
        this.hoverDurMinus = approach(this.hoverDurMinus, hit(mx, my, this.durMinusX, this.durMinusY, this.durBox, this.durBox) ? 1.0f : 0.0f, dt, 15.0f);
        this.hoverDurPlus = approach(this.hoverDurPlus, hit(mx, my, this.durPlusX, this.durPlusY, this.durBox, this.durBox) ? 1.0f : 0.0f, dt, 15.0f);

        this.hoverPlay = approach(this.hoverPlay, hit(mx, my, this.playCX - 15.0f, this.playCY - 15.0f, 30.0f, 30.0f) ? 1.0f : 0.0f, dt, 15.0f);
        this.hoverAddKey = approach(this.hoverAddKey, hit(mx, my, this.addKeyX, this.addKeyY, this.addKeyW, this.addKeyH) ? 1.0f : 0.0f, dt, 15.0f);
        this.hoverDelKey = approach(this.hoverDelKey, hit(mx, my, this.delKeyX, this.delKeyY, this.delKeyW, this.delKeyH) ? 1.0f : 0.0f, dt, 15.0f);

        for (int part = 0; part < CustomEmotion.PART_COUNT; part++) {
            int row = part / 4;
            int colIdx = part % 4;
            float x = this.tabX0 + colIdx * (this.tabW + this.tabGap);
            float y = this.tabY0 + row * (this.tabH + this.tabGap);
            boolean target = part == this.selectedPart || hit(mx, my, x, y, this.tabW, this.tabH);
            this.tabHovers[part] = approach(this.tabHovers[part], target ? 1.0f : 0.0f, dt, 15.0f);
        }

        int[] fields = CustomEmotion.PART_FIELD_INDICES[this.selectedPart];
        for (int i = 0; i < fields.length; i++) {
            float y = this.sliderY0 + i * this.sliderRowH;
            boolean target = this.dragSlider == i || hit(mx, my, this.sliderX0 - 4.0f, y, (this.sliderX1 - this.sliderX0) + 8.0f, this.sliderRowH);
            this.sliderHovers[i] = approach(this.sliderHovers[i], target ? 1.0f : 0.0f, dt, 15.0f);
        }
    }

    private void renderHeader(DrawContext g, float mx, float my, float dt, float alpha) {
        // Шапка в виде стеклянного контейнера
        RectUtil.drawClientRectFixedRadius(this.hdrX, this.hdrY, this.hdrW, this.hdrH, 10.0f, alpha, 0.0f);
        RenderHelper.drawPanelBg(this.hdrX + 2.0f, this.hdrY + 2.0f, this.hdrW - 4.0f, this.hdrH - 4.0f, 8.0f, alpha);

        // Иконка-искра слева
        float iconCX = this.hdrX + 16.0f;
        float iconCY = this.hdrY + this.hdrH * 0.5f;
        float pulse = 0.8f + 0.2f * (float) Math.sin(this.animTime * 3.0f);
        Render2D.circle(iconCX, iconCY, 3.8f * pulse, ClientAccent.accentSoftAt(255.0f * alpha, iconCX, iconCY));
        Render2D.circleOutline(iconCX, iconCY, 6.0f, 0.8f, ClientAccent.accentBrightAt(180.0f * alpha, iconCX, iconCY));

        Fonts.SEMIBOLD.draw(I18n.tr("Редактор эмоций"), this.hdrX + 26.0f, this.hdrY + 12.0f, 8.5f, color(255, 255, 255, 245, alpha));

        // Поле ввода названия
        this.nameField.render(g, this.nameX, this.nameY, this.nameW, this.nameH, alpha, mx, my, dt);

        // Длительность: [ − | 3.0с | + ]
        RenderHelper.drawPanelBg(this.durPillX, this.durPillY, this.durPillW, this.durPillH, 5.0f, alpha);
        Render2D.outline(this.durPillX, this.durPillY, this.durPillW, this.durPillH, 5.0f, 0.6f, color(255, 255, 255, 18, alpha));

        if (this.hoverDurMinus > 0.01f) {
            Render2D.rect(this.durMinusX, this.durMinusY, this.durBox, this.durBox, 4.0f, ClientAccent.accentFillAt(65.0f * this.hoverDurMinus * alpha, this.durMinusX, this.durMinusY));
        }
        float mw = Fonts.SEMIBOLD.width("−", 7.5f);
        int mCol = ColorEngine.lerpColor(color(255, 255, 255, 180, alpha), ClientAccent.accentBrightAt(255.0f * alpha, this.durMinusX, this.durMinusY), this.hoverDurMinus);
        Fonts.SEMIBOLD.draw("−", this.durMinusX + (this.durBox - mw) * 0.5f, this.durMinusY + 5.0f, 7.5f, mCol);

        if (this.hoverDurPlus > 0.01f) {
            Render2D.rect(this.durPlusX, this.durPlusY, this.durBox, this.durBox, 4.0f, ClientAccent.accentFillAt(65.0f * this.hoverDurPlus * alpha, this.durPlusX, this.durPlusY));
        }
        float pw = Fonts.SEMIBOLD.width("+", 7.5f);
        int pCol = ColorEngine.lerpColor(color(255, 255, 255, 180, alpha), ClientAccent.accentBrightAt(255.0f * alpha, this.durPlusX, this.durPlusY), this.hoverDurPlus);
        Fonts.SEMIBOLD.draw("+", this.durPlusX + (this.durBox - pw) * 0.5f, this.durPlusY + 5.0f, 7.5f, pCol);

        String durVal = String.format("%.1fс", this.editing.duration());
        float dvw = Fonts.SEMIBOLD.width(durVal, 7.5f);
        float centerAreaX = this.durMinusX + this.durBox;
        float centerAreaW = this.durPlusX - centerAreaX;
        Fonts.SEMIBOLD.draw(durVal, centerAreaX + (centerAreaW - dvw) * 0.5f, this.durPillY + 7.5f, 7.5f, color(255, 255, 255, 235, alpha));

        // Кнопка удаления (если редактируется существующая)
        if (this.originalName != null) {
            int delBg = ColorEngine.lerpColor(color(24, 28, 38, 220, alpha), color(175, 45, 60, 220, alpha), this.hoverDelete);
            int delBorder = ColorEngine.lerpColor(color(255, 255, 255, 20, alpha), color(255, 95, 115, 210, alpha), this.hoverDelete);
            Render2D.rect(this.deleteX, this.deleteY, this.deleteW, this.deleteH, 5.0f, delBg);
            Render2D.outline(this.deleteX, this.deleteY, this.deleteW, this.deleteH, 5.0f, 0.7f, delBorder);
            String delText = I18n.tr("Удалить");
            float dtw = Fonts.SEMIBOLD.width(delText, 7.0f);
            Fonts.SEMIBOLD.draw(delText, this.deleteX + (this.deleteW - dtw) * 0.5f, this.deleteY + 7.5f, 7.0f, color(255, 255, 255, 235, alpha));
        }

        // Кнопка Сохранить
        int saveBg = ClientAccent.accentSoftAt((200.0f + 55.0f * this.hoverSave) * alpha, this.saveX, this.saveY);
        int saveBorder = ClientAccent.accentBrightAt((160.0f + 95.0f * this.hoverSave) * alpha, this.saveX, this.saveY);
        Render2D.rect(this.saveX, this.saveY, this.saveW, this.saveH, 5.0f, saveBg);
        Render2D.outline(this.saveX, this.saveY, this.saveW, this.saveH, 5.0f, 0.8f, saveBorder);
        if (this.hoverSave > 0.01f) {
            Render2D.outline(this.saveX - 1.0f, this.saveY - 1.0f, this.saveW + 2.0f, this.saveH + 2.0f, 6.0f, 0.8f, ClientAccent.accentBrightAt(70.0f * this.hoverSave * alpha, this.saveX, this.saveY));
        }
        String saveText = I18n.tr("Сохранить");
        float stw = Fonts.SEMIBOLD.width(saveText, 7.5f);
        Fonts.SEMIBOLD.draw(saveText, this.saveX + (this.saveW - stw) * 0.5f, this.saveY + 7.0f, 7.5f, color(10, 12, 18, 255, alpha));

        // Кнопка Закрыть (✕)
        float closeR = this.closeW * 0.5f;
        float ccx = this.closeX + closeR;
        float ccy = this.closeY + closeR;
        int closeBg = ColorEngine.lerpColor(color(22, 26, 36, 210, alpha), color(42, 50, 68, 240, alpha), this.hoverClose);
        int closeBorder = ColorEngine.lerpColor(color(255, 255, 255, 18, alpha), ClientAccent.accentSoftAt(180.0f * alpha, ccx, ccy), this.hoverClose);
        Render2D.circle(ccx, ccy, closeR, closeBg);
        Render2D.circleOutline(ccx, ccy, closeR, 0.7f, closeBorder);
        float cw = Fonts.SEMIBOLD.width("✕", 7.5f);
        int cCol = ColorEngine.lerpColor(color(255, 255, 255, 190, alpha), color(255, 255, 255, 255, alpha), this.hoverClose);
        Fonts.SEMIBOLD.draw("✕", ccx - cw * 0.5f, ccy - 4.5f, 7.5f, cCol);
    }

    private void renderPreview(DrawContext g, float mx, float my, float alpha) {
        float pw = this.prevX1 - this.prevX0;
        float ph = this.prevY1 - this.prevY0;
        RectUtil.drawClientRectFixedRadius(this.prevX0, this.prevY0, pw, ph, 12.0f, alpha, 0.0f);
        RenderHelper.drawPanelBg(this.prevX0 + 2.5f, this.prevY0 + 2.5f, pw - 5.0f, ph - 5.0f, 9.5f, alpha);

        // Индикатор "Живое превью" с пульсирующей точкой
        float dotX = this.prevX0 + 16.0f;
        float dotY = this.prevY0 + 15.0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(this.animTime * 4.0f);
        Render2D.circle(dotX, dotY, 3.0f, ClientAccent.accentSoftAt(255.0f * (0.7f + 0.3f * pulse) * alpha, dotX, dotY));
        Render2D.circleOutline(dotX, dotY, 4.5f + pulse * 1.5f, 1.0f, ClientAccent.accentSoftAt(120.0f * (1.0f - pulse) * alpha, dotX, dotY));

        Fonts.SEMIBOLD.draw(I18n.tr("Живое превью"), dotX + 8.0f, this.prevY0 + 11.5f, 7.5f, color(255, 255, 255, 230, alpha));

        // Бейдж текущего времени
        String t = String.format("%.2f / %.1f с", this.scrubTime, this.editing.duration());
        float tw = Fonts.MEDIUM.width(t, 6.5f);
        float tpillW = tw + 12.0f;
        float tpillX = this.prevX1 - 12.0f - tpillW;
        RenderHelper.drawPanelBg(tpillX, this.prevY0 + 8.0f, tpillW, 14.0f, 4.0f, alpha);
        Render2D.outline(tpillX, this.prevY0 + 8.0f, tpillW, 14.0f, 4.0f, 0.6f, color(255, 255, 255, 20, alpha));
        Fonts.MEDIUM.draw(t, tpillX + 6.0f, this.prevY0 + 11.5f, 6.5f, color(255, 255, 255, 180, alpha));

        float pad = 12.0f;
        int x0 = (int) (this.prevX0 + pad);
        int y0 = (int) (this.prevY0 + 26.0f);
        int x1 = (int) (this.prevX1 - pad);
        int y1 = (int) (this.prevY1 - 22.0f);

        // Голографический пьедестал под персонажем
        float pedCX = (x0 + x1) * 0.5f;
        float pedCY = y1 - 12.0f;
        float pedW = (x1 - x0) * 0.52f;
        Render2D.circle(pedCX, pedCY, pedW * 0.5f, ClientAccent.accentFillAt(35.0f * alpha, pedCX, pedCY));
        Render2D.circleOutline(pedCX, pedCY, pedW * 0.5f, 1.0f, ClientAccent.accentBrightAt(60.0f * alpha, pedCX, pedCY));
        Render2D.circleOutline(pedCX, pedCY, pedW * 0.28f, 0.7f, ClientAccent.accentSoftAt(40.0f * alpha, pedCX, pedCY));

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int size = Math.max(20, (int) ((y1 - y0) * 0.36f));
            float cx = (x0 + x1) * 0.5f;
            float cy = (y0 + y1) * 0.5f;
            ScissorUtil.push(this.prevX0 + 3.0f, this.prevY0 + 24.0f, pw - 6.0f, ph - 27.0f);
            try {
                EmotionPlayback.beginPreview(this.editing, this.scrubTime);
                try {
                    InventoryScreen.drawEntity(g, x0, y0, x1, y1, size, 1.0f, cx, cy, (LivingEntity) mc.player);
                } finally {
                    EmotionPlayback.endPreview();
                }
            } finally {
                ScissorUtil.pop();
            }
        }

        String hint = I18n.tr("● Превью в реальном времени");
        float hw = Fonts.MEDIUM.width(hint, 6.0f);
        Fonts.MEDIUM.draw(hint, this.prevX0 + (pw - hw) * 0.5f, this.prevY1 - 15.0f, 6.0f, color(255, 255, 255, 80, alpha));
    }

    private void renderPanel(DrawContext g, float mx, float my, float alpha) {
        float pw = this.panelX1 - this.panelX0;
        float ph = this.panelY1 - this.panelY0;
        RectUtil.drawClientRectFixedRadius(this.panelX0, this.panelY0, pw, ph, 12.0f, alpha, 0.0f);
        RenderHelper.drawPanelBg(this.panelX0 + 2.5f, this.panelY0 + 2.5f, pw - 5.0f, ph - 5.0f, 9.5f, alpha);

        // Заголовок панели
        String title = I18n.tr("Части тела");
        Fonts.SEMIBOLD.draw(title, this.panelX0 + 14.0f, this.panelY0 + 12.0f, 8.5f, color(255, 255, 255, 240, alpha));
        String sub = I18n.tr("Настройка углов вращения и сдвига");
        Fonts.MEDIUM.draw(sub, this.panelX0 + 14.0f + Fonts.SEMIBOLD.width(title, 8.5f) + 8.0f, this.panelY0 + 13.5f, 6.0f, color(255, 255, 255, 100, alpha));

        // Табы частей тела (2 ряда по 4)
        for (int part = 0; part < CustomEmotion.PART_COUNT; part++) {
            int row = part / 4;
            int colIdx = part % 4;
            float x = this.tabX0 + colIdx * (this.tabW + this.tabGap);
            float y = this.tabY0 + row * (this.tabH + this.tabGap);
            boolean sel = part == this.selectedPart;
            float hov = this.tabHovers[part];

            int bg = sel ? ClientAccent.accentSoftAt(230.0f * alpha, x, y)
                    : ColorEngine.lerpColor(color(20, 24, 34, 210, alpha), color(34, 42, 58, 230, alpha), hov);
            Render2D.rect(x, y, this.tabW, this.tabH, 6.0f, bg);
            int border = sel ? ClientAccent.accentBrightAt(255.0f * alpha, x, y)
                    : color(255, 255, 255, (int)(15.0f + 25.0f * hov), alpha);
            Render2D.outline(x, y, this.tabW, this.tabH, 6.0f, 0.7f, border);

            String name = I18n.tr(CustomEmotion.PART_NAMES[part]);
            float ns = 7.0f;
            float nw = Fonts.SEMIBOLD.width(name, ns);
            int tc = sel ? color(10, 12, 18, 255, alpha) : ColorEngine.lerpColor(color(180, 195, 215, 200, alpha), color(255, 255, 255, 255, alpha), hov);
            Fonts.SEMIBOLD.draw(name, x + (this.tabW - nw) * 0.5f, y + (this.tabH - ns) * 0.5f - 1.0f, ns, tc);

            // Точка-индикатор модифицированной части
            if (hasNonZeroValues(part, this.workPose)) {
                float dotX = x + this.tabW - 6.0f;
                float dotY = y + 6.0f;
                int dotColor = sel ? color(10, 12, 18, 255, alpha) : ClientAccent.accentSoftAt(255.0f * alpha, dotX, dotY);
                Render2D.circle(dotX, dotY, 2.0f, dotColor);
            }
        }

        // Разделитель
        float sepY = this.tabY0 + this.tabH * 2.0f + this.tabGap + 6.0f;
        Render2D.rect(this.panelX0 + 14.0f, sepY, pw - 28.0f, 0.6f, 0.0f, color(255, 255, 255, 12, alpha));

        // Слайдеры выбранной части
        int[] fields = CustomEmotion.PART_FIELD_INDICES[this.selectedPart];
        for (int i = 0; i < fields.length; i++) {
            boolean isRot = this.selectedPart != CustomEmotion.PART_UPPER && i < 3;
            this.renderSlider(g, i, fields[i], isRot, mx, my, alpha);
        }

        String hint = I18n.tr("Двигайте слайдеры — поза сохраняется автоматически в ключ. Ромбики можно таскать.");
        Fonts.MEDIUM.draw(hint, this.panelX0 + 14.0f, this.panelY1 - 15.0f, 6.0f, color(255, 255, 255, 90, alpha));
    }

    private static class AxisInfo {
        final String letter;
        final String label;
        final int badgeColor;

        AxisInfo(String letter, String label, int badgeColor) {
            this.letter = letter;
            this.label = label;
            this.badgeColor = badgeColor;
        }
    }

    private static AxisInfo getAxisInfo(int part, int sliderIdx, boolean isRot) {
        String letter;
        if (part == CustomEmotion.PART_UPPER) {
            letter = sliderIdx == 0 ? "Y" : "Z";
        } else {
            letter = sliderIdx % 3 == 0 ? "X" : sliderIdx % 3 == 1 ? "Y" : "Z";
        }
        String label = isRot ? I18n.tr("Поворот") : I18n.tr("Сдвиг");
        int badgeColor;
        if ("X".equals(letter)) {
            badgeColor = col(245, 95, 110, 255); // coral red
        } else if ("Y".equals(letter)) {
            badgeColor = col(80, 215, 135, 255); // emerald green
        } else {
            badgeColor = col(85, 185, 255, 255); // azure blue
        }
        return new AxisInfo(letter, label, badgeColor);
    }

    private void renderSlider(DrawContext g, int sliderIdx, int fieldIdx, boolean isRot, float mx, float my, float alpha) {
        float y = this.sliderY0 + sliderIdx * this.sliderRowH;
        float labelW = 104.0f;
        float valueW = 56.0f;
        float tx0 = this.sliderX0 + labelW;
        float tx1 = this.sliderX1 - valueW - 6.0f;
        float cy = y + this.sliderRowH * 0.5f;

        float min = isRot ? ROT_MIN : OFF_MIN;
        float max = isRot ? ROT_MAX : OFF_MAX;
        float value = CustomEmotion.getValue(this.workPose, fieldIdx);
        float hov = this.sliderHovers[sliderIdx];

        // Подсветка строки при наведении
        if (hov > 0.01f) {
            Render2D.rect(this.sliderX0 - 4.0f, y + 1.0f, (this.sliderX1 - this.sliderX0) + 8.0f, this.sliderRowH - 2.0f, 6.0f, color(255, 255, 255, (int)(6.0f * hov), alpha));
        }

        // Бейдж оси [X] / [Y] / [Z]
        AxisInfo info = getAxisInfo(this.selectedPart, sliderIdx, isRot);
        float bw = 15.0f;
        float bh = 14.0f;
        float bx = this.sliderX0;
        float by = cy - bh * 0.5f;
        Render2D.rect(bx, by, bw, bh, 3.5f, ColorEngine.multAlpha(info.badgeColor, 0.22f * alpha));
        Render2D.outline(bx, by, bw, bh, 3.5f, 0.6f, ColorEngine.multAlpha(info.badgeColor, 0.70f * alpha));
        float lw = Fonts.SEMIBOLD.width(info.letter, 6.5f);
        Fonts.SEMIBOLD.draw(info.letter, bx + (bw - lw) * 0.5f, cy - 4.2f, 6.5f, ColorEngine.multAlpha(info.badgeColor, alpha));
        Fonts.MEDIUM.draw(info.label, bx + bw + 6.0f, cy - 4.5f, 6.8f, color(230, 235, 245, 215, alpha));

        // Желоб трека
        float trackH = 4.0f;
        Render2D.rect(tx0, cy - trackH * 0.5f, tx1 - tx0, trackH, 2.0f, color(20, 24, 34, 230, alpha));
        Render2D.outline(tx0, cy - trackH * 0.5f, tx1 - tx0, trackH, 2.0f, 0.6f, color(255, 255, 255, 12, alpha));

        // Отметка нейтрального нуля
        float zeroF = (0.0f - min) / (max - min);
        float zx = tx0 + zeroF * (tx1 - tx0);
        Render2D.rect(zx - 0.5f, cy - 3.5f, 1.0f, 7.0f, 0.5f, color(255, 255, 255, 60, alpha));

        // Направленная закраска трека от центра к ползунку
        float f = clamp((value - min) / (max - min), 0.0f, 1.0f);
        float kx = tx0 + f * (tx1 - tx0);
        float barStart = Math.min(zx, kx);
        float barEnd = Math.max(zx, kx);
        if (barEnd - barStart > 0.5f) {
            Render2D.rect(barStart, cy - trackH * 0.5f, barEnd - barStart, trackH, 2.0f, ClientAccent.accentSoftAt(220.0f * alpha, barStart, cy));
        }

        // Ползунок с анимацией масштаба и ореолом
        float knobR = 4.8f + 1.8f * hov;
        if (hov > 0.01f) {
            Render2D.circleOutline(kx, cy, knobR + 2.5f, 1.2f, ClientAccent.accentBrightAt(170.0f * hov * alpha, kx, cy));
        }
        Render2D.circle(kx, cy, knobR, ClientAccent.accentSoftAt(255.0f * alpha, kx, cy));
        Render2D.circle(kx, cy, knobR * 0.45f, color(255, 255, 255, 245, alpha));

        // Цифровое значение в стеклянном бейдже
        String vs = isRot ? String.format("%+.0f°", value) : String.format("%+.2f", value);
        if (isRot && Math.abs(value) < 0.01f) vs = "0°";
        else if (!isRot && Math.abs(value) < 0.001f) vs = "0.00";
        float vPillX = tx1 + 6.0f;
        float vPillY = cy - 8.0f;
        float vPillW = valueW;
        float vPillH = 16.0f;
        RenderHelper.drawPanelBg(vPillX, vPillY, vPillW, vPillH, 4.0f, alpha);
        Render2D.outline(vPillX, vPillY, vPillW, vPillH, 4.0f, 0.6f, color(255, 255, 255, (int)(14.0f + 18.0f * hov), alpha));
        float vw = Fonts.SEMIBOLD.width(vs, 6.8f);
        Fonts.SEMIBOLD.draw(vs, vPillX + (vPillW - vw) * 0.5f, cy - 4.5f, 6.8f, color(255, 255, 255, 230, alpha));
    }

    private void renderTimeline(DrawContext g, float mx, float my, float alpha) {
        float tw = this.tlX1 - this.tlX0;
        float th = this.tlY1 - this.tlY0;
        RectUtil.drawClientRectFixedRadius(this.tlX0, this.tlY0, tw, th, 12.0f, alpha, 0.0f);
        RenderHelper.drawPanelBg(this.tlX0 + 2.5f, this.tlY0 + 2.5f, tw - 5.0f, th - 5.0f, 9.5f, alpha);

        // Круглая кнопка Play/Pause в стиле хаба колеса эмоций
        float r = 13.0f;
        if (this.hoverPlay > 0.01f) {
            Render2D.circleOutline(this.playCX, this.playCY, r + 2.5f, 1.2f, ClientAccent.accentBrightAt(180.0f * this.hoverPlay * alpha, this.playCX, this.playCY));
        }
        RectUtil.drawClientRectFixedRadius(this.playCX - r, this.playCY - r, r * 2.0f, r * 2.0f, r, alpha, 0.0f);
        RenderHelper.drawPanelBg(this.playCX - r + 2.0f, this.playCY - r + 2.0f, (r - 2.0f) * 2.0f, (r - 2.0f) * 2.0f, r - 2.0f, alpha);
        int iconColor = ColorEngine.lerpColor(color(255, 255, 255, 210, alpha), ClientAccent.accentSoftAt(255.0f * alpha, this.playCX, this.playCY), this.hoverPlay);
        if (this.playing) {
            Render2D.rect(this.playCX - 4.5f, this.playCY - 5.0f, 3.0f, 10.0f, 1.0f, iconColor);
            Render2D.rect(this.playCX + 1.5f, this.playCY - 5.0f, 3.0f, 10.0f, 1.0f, iconColor);
        } else {
            this.triangle(this.playCX - 3.5f, this.playCY - 6.0f, this.playCX - 3.5f, this.playCY + 6.0f, this.playCX + 6.0f, this.playCY, iconColor);
        }

        // Кнопки управления ключами
        this.drawTimelineButton(this.addKeyX, this.addKeyY, this.addKeyW, this.addKeyH, "+ " + I18n.tr("Ключ"), this.hoverAddKey, false, true, alpha);
        boolean canDeleteKey = this.selectedKey != null;
        this.drawTimelineButton(this.delKeyX, this.delKeyY, this.delKeyW, this.delKeyH, "− " + I18n.tr("Ключ"), this.hoverDelKey, canDeleteKey, canDeleteKey, alpha);

        // Информационные бейджи в правом верхнем углу таймлайна
        String timeLabel = String.format("%.2f / %.1f с", this.scrubTime, this.editing.duration());
        float tlw = Fonts.MEDIUM.width(timeLabel, 6.8f);
        float tPillW = tlw + 14.0f;
        float tPillX = this.tlX1 - 14.0f - tPillW;
        float pillY = this.tlY0 + 11.0f;
        RenderHelper.drawPanelBg(tPillX, pillY, tPillW, 20.0f, 4.0f, alpha);
        Render2D.outline(tPillX, pillY, tPillW, 20.0f, 4.0f, 0.6f, color(255, 255, 255, 18, alpha));
        Fonts.MEDIUM.draw(timeLabel, tPillX + 7.0f, pillY + 6.0f, 6.8f, color(255, 255, 255, 190, alpha));

        String keyLabel = String.format("%d %s", this.editing.keyframes().size(), I18n.tr("кадр."));
        float klw = Fonts.MEDIUM.width(keyLabel, 6.8f);
        float kPillW = klw + 14.0f;
        float kPillX = tPillX - 6.0f - kPillW;
        RenderHelper.drawPanelBg(kPillX, pillY, kPillW, 20.0f, 4.0f, alpha);
        Render2D.outline(kPillX, pillY, kPillW, 20.0f, 4.0f, 0.6f, color(255, 255, 255, 18, alpha));
        Fonts.MEDIUM.draw(keyLabel, kPillX + 7.0f, pillY + 6.0f, 6.8f, color(255, 255, 255, 160, alpha));

        if (this.selectedKey != null) {
            String sel = String.format(I18n.tr("Ключ: %.2fс"), this.selectedKey.time);
            float slw = Fonts.SEMIBOLD.width(sel, 6.8f);
            float sPillW = slw + 14.0f;
            float sPillX = kPillX - 6.0f - sPillW;
            RenderHelper.drawPanelBg(sPillX, pillY, sPillW, 20.0f, 4.0f, alpha);
            Render2D.outline(sPillX, pillY, sPillW, 20.0f, 4.0f, 0.7f, ClientAccent.accentSoftAt(180.0f * alpha, sPillX, pillY));
            Fonts.SEMIBOLD.draw(sel, sPillX + 7.0f, pillY + 6.0f, 6.8f, ClientAccent.accentSoftAt(255.0f * alpha, sPillX, pillY));
        }

        // Трек таймлайна
        float ty = this.trackY;
        float trackH = 26.0f;
        float trackW = this.trackX1 - this.trackX0;
        Render2D.rect(this.trackX0, ty - trackH * 0.5f, trackW, trackH, 6.0f, color(16, 20, 28, 230, alpha));
        Render2D.outline(this.trackX0, ty - trackH * 0.5f, trackW, trackH, 6.0f, 0.7f, color(255, 255, 255, 14, alpha));

        // Подсветка прогресса воспроизведения
        float ccx = this.timeToX(this.scrubTime);
        if (ccx > this.trackX0) {
            Render2D.rect(this.trackX0, ty - trackH * 0.5f + 1.0f, ccx - this.trackX0, trackH - 2.0f, 5.0f, ClientAccent.accentFillAt(35.0f * alpha, this.trackX0, ty));
        }

        // Осевая линия шкалы
        Render2D.line(this.trackX0 + 4.0f, ty, this.trackX1 - 4.0f, ty, 1.0f, color(255, 255, 255, 30, alpha));

        // Риски шкалы времени
        float dur = this.editing.duration();
        float step = dur > 6.0f ? 1.0f : 0.5f;
        for (float t = 0.0f; t <= dur + 0.001f; t += step) {
            float x = this.timeToX(t);
            boolean isMajor = Math.abs(t - Math.round(t)) < 0.001f;
            float tickH = isMajor ? 5.5f : 3.0f;
            int tickCol = isMajor ? color(255, 255, 255, 80, alpha) : color(255, 255, 255, 35, alpha);
            Render2D.line(x, ty - tickH, x, ty - 1.0f, 1.0f, tickCol);
            if (isMajor) {
                String s = String.format("%.0fс", t);
                float sw = Fonts.MEDIUM.width(s, 5.8f);
                Fonts.MEDIUM.draw(s, x - sw * 0.5f, ty + 6.0f, 5.8f, color(255, 255, 255, 110, alpha));
            }
        }

        // Ромбики ключевых кадров
        CustomEmotion.Keyframe hoveredKf = null;
        float hoveredKfX = 0.0f;
        for (CustomEmotion.Keyframe key : this.editing.keyframes()) {
            float x = this.timeToX(key.time);
            boolean sel = key == this.selectedKey;
            boolean hov = Math.abs(mx - x) <= 8.0f && Math.abs(my - ty) <= 12.0f;
            if (hov) {
                hoveredKf = key;
                hoveredKfX = x;
            }
            float rDiamond = sel ? 6.5f : hov ? 6.0f : 4.8f;
            int fill = sel ? ClientAccent.accentSoftAt(255.0f * alpha, x, ty)
                    : hov ? color(255, 255, 255, 255, alpha)
                    : color(175, 190, 215, 230, alpha);
            int outline = sel ? ClientAccent.accentBrightAt(255.0f * alpha, x, ty)
                    : hov ? color(255, 255, 255, 255, alpha)
                    : color(220, 230, 250, 180, alpha);

            if (sel || hov) {
                Render2D.line(x, ty - 12.0f, x, ty + 12.0f, 1.0f, ClientAccent.accentSoftAt((sel ? 180.0f : 100.0f) * alpha, x, ty));
            }
            this.drawKeyframeDiamond(x, ty, rDiamond, fill, outline, sel, alpha);
        }

        // Всплывающая подсказка над наведённым ключом
        if (hoveredKf != null) {
            String tip = String.format("%.2fс", hoveredKf.time);
            float twKf = Fonts.MEDIUM.width(tip, 6.0f);
            float tpx = hoveredKfX - twKf * 0.5f - 4.0f;
            float tpy = ty - 26.0f;
            Render2D.rect(tpx, tpy, twKf + 8.0f, 12.0f, 3.5f, color(12, 15, 22, 230, alpha));
            Render2D.outline(tpx, tpy, twKf + 8.0f, 12.0f, 3.5f, 0.6f, ClientAccent.accentSoftAt(180.0f * alpha, hoveredKfX, tpy));
            Fonts.MEDIUM.draw(tip, hoveredKfX - twKf * 0.5f, tpy + 2.5f, 6.0f, color(255, 255, 255, 255, alpha));
        }

        // Скруббер (курсор таймлайна)
        Render2D.line(ccx, ty - 13.0f, ccx, ty + 13.0f, 2.0f, ClientAccent.accentSoftAt(255.0f * alpha, ccx, ty));
        float headW = 9.0f;
        float headH = 7.0f;
        float headX = ccx - headW * 0.5f;
        float headY = ty - 16.0f;
        Render2D.rect(headX, headY, headW, headH, 3.0f, ClientAccent.accentSoftAt(255.0f * alpha, ccx, headY));
        Render2D.circle(ccx, headY + headH * 0.5f, 1.8f, color(255, 255, 255, 255, alpha));
        Render2D.circleOutline(ccx, ty, 3.0f, 0.9f, ClientAccent.accentBrightAt(220.0f * alpha, ccx, ty));
    }

    private void drawTimelineButton(float x, float y, float w, float h, String text, float hover, boolean active, boolean enabled, float alpha) {
        float r = 5.0f;
        int bg = !enabled ? color(18, 22, 30, 140, alpha)
                : active ? ColorEngine.lerpColor(color(24, 28, 38, 220, alpha), color(175, 45, 60, 220, alpha), hover)
                : ColorEngine.lerpColor(color(24, 28, 38, 220, alpha), color(36, 44, 60, 240, alpha), hover);
        int border = !enabled ? color(255, 255, 255, 10, alpha)
                : active ? ColorEngine.lerpColor(color(255, 255, 255, 18, alpha), color(255, 90, 110, 200, alpha), hover)
                : ColorEngine.lerpColor(color(255, 255, 255, 18, alpha), ClientAccent.accentSoftAt(180.0f * alpha, x, y), hover);
        Render2D.rect(x, y, w, h, r, bg);
        Render2D.outline(x, y, w, h, r, 0.7f, border);
        float tw = Fonts.SEMIBOLD.width(text, 6.8f);
        int tc = !enabled ? color(255, 255, 255, 70, alpha)
                : ColorEngine.lerpColor(color(210, 220, 235, 220, alpha), color(255, 255, 255, 255, alpha), hover);
        Fonts.SEMIBOLD.draw(text, x + (w - tw) * 0.5f, y + 6.0f, 6.8f, tc);
    }

    // ---------- примитивы ----------

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawKeyframeDiamond(float cx, float cy, float r, int fillColor, int outlineColor, boolean glow, float alpha) {
        if (glow) {
            Render2D.circleOutline(cx, cy, r + 3.5f, 1.2f, ClientAccent.accentBrightAt(200.0f * alpha, cx, cy));
        }
        this.diamond(cx, cy, r, fillColor);
        int ri = (int) Math.ceil(r);
        for (int i = -ri; i <= ri; i++) {
            float hw = r - Math.abs(i);
            if (hw > 0.4f) {
                Render2D.rect(cx - hw, cy + i, 1.0f, 1.0f, 0.0f, outlineColor);
                Render2D.rect(cx + hw - 1.0f, cy + i, 1.0f, 1.0f, 0.0f, outlineColor);
            }
        }
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

    /** Залитый треугольник (Play). */
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
            float labelW = 104.0f;
            float valueW = 56.0f;
            float tx0 = this.sliderX0 + labelW;
            float tx1 = this.sliderX1 - valueW - 6.0f;
            float cy = y + this.sliderRowH * 0.5f;
            if (hit(mx, my, tx0 - 8.0f, cy - 12.0f, (tx1 - tx0) + 16.0f, 24.0f)) {
                this.dragSlider = i;
                this.applySlider(i, mx);
                return true;
            }
        }

        // таймлайн: play
        if (hit(mx, my, this.playCX - 15.0f, this.playCY - 15.0f, 30.0f, 30.0f)) {
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
            // Delete (261) или Backspace (259) — удалить выбранный ключ
            if ((key == 261 || key == 259) && this.selectedKey != null) {
                this.editing.removeKeyframe(this.selectedKey);
                this.selectedKey = null;
                return true;
            }
            // Пробел (32) — переключить воспроизведение/паузу
            if (key == 32) {
                this.playing = !this.playing;
                if (this.playing && this.scrubTime >= this.editing.duration() - 0.001f) {
                    this.scrubTime = 0.0f;
                }
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
        float labelW = 104.0f;
        float valueW = 56.0f;
        float tx0 = this.sliderX0 + labelW;
        float tx1 = this.sliderX1 - valueW - 6.0f;
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
        if (Math.abs(my - this.trackY) > 13.0f) {
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
        Sounds.play("gui_close");
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new EmotionWheelScreen(this.module, this.module.wheel()));
    }
}
