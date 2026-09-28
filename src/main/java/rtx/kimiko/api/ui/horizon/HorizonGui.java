package rtx.kimiko.api.ui.horizon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.ModuleManager;
import rtx.kimiko.api.modules.impl.Interface.ClickGui;
import rtx.kimiko.api.modules.impl.Interface.InterfaceModule;
import rtx.kimiko.api.modules.settings.Setting;
import rtx.kimiko.api.modules.settings.impl.BindSetting;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ButtonSetting;
import rtx.kimiko.api.modules.settings.impl.ColorSetting;
import rtx.kimiko.api.modules.settings.impl.MultiSelectSetting;
import rtx.kimiko.api.modules.settings.impl.PositionSettings;
import rtx.kimiko.api.modules.settings.impl.RangeSliderSetting;
import rtx.kimiko.api.modules.settings.impl.SelectSetting;
import rtx.kimiko.api.modules.settings.impl.SeparatorSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.api.modules.settings.impl.TextSetting;
import rtx.kimiko.api.ui.BaseScreen;
import rtx.kimiko.api.ui.UI;
import rtx.kimiko.utils.key.KeyBind;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.render2d.Render2D;

/**
 * Horizon: клик-меню в стиле чёрной дыры из Ambience.
 *
 * Структура (не как у классического UI):
 *  - на фоне живая чёрная дыра: аккреционный диск из частиц с кеплеровым вращением и доплер-яркостью,
 *    линзированная дуга над тенью (как в "Интерстеллар"), вторичное изображение снизу, фотонное кольцо,
 *    звёздное поле с мерцанием;
 *  - три парящих стеклянных панели: слева "орбиты" категорий, по центру сетка карточек модулей с поиском,
 *    справа инспектор настроек выбранного модуля;
 *  - у каждой карточки своя мини-сингулярность: включённый модуль = горящее фотонное кольцо с орбитой.
 *
 * Управление: ЛКМ по карточке - вкл/выкл, ПКМ - настройки в инспекторе, СКМ - назначить бинд
 * (Del/Backspace - сброс, Esc - отмена). Поиск: просто начни печатать или Ctrl+F.
 *
 * Всё рисуется примитивами Render2D (круги/прямоугольники), без отдельного шейдера и без рендер-таргетов,
 * поэтому по FPS меню дешёвое.
 */
public final class HorizonGui extends BaseScreen {
    @NotNull
    public static final HorizonGui INSTANCE = new HorizonGui();

    private static final int K_CAT = 1;
    private static final int K_MODULE = 2;
    private static final int K_SEARCH = 3;
    private static final int K_BIND = 4;
    private static final int K_BOOL = 5;
    private static final int K_SLIDER = 6;
    private static final int K_RANGE = 7;
    private static final int K_SELECT = 8;
    private static final int K_MULTI = 9;
    private static final int K_HUE = 10;
    private static final int K_BUTTON = 11;
    private static final int K_CLASSIC = 12;
    private static final int K_TOGGLE = 13;

    private static final float TILT = 0.2f;
    private static final int PARTICLES = 280;
    private static final int MINI_PARTICLES = 90;
    private static final float[] P_ANGLE = new float[PARTICLES];
    private static final float[] P_RAD = new float[PARTICLES];
    private static final float[] P_SPEED = new float[PARTICLES];
    private static final float[] P_SIZE = new float[PARTICLES];
    private static final float[] P_ALPHA = new float[PARTICLES];
    private static final int STARS = 170;
    private static final float[] S_X = new float[STARS];
    private static final float[] S_Y = new float[STARS];
    private static final float[] S_SIZE = new float[STARS];
    private static final float[] S_PHASE = new float[STARS];
    private static final float[] S_SPEED = new float[STARS];

    static {
        Random r = new Random(0x5EED1EL);
        for (int i = 0; i < PARTICLES; ++i) {
            P_ANGLE[i] = r.nextFloat() * 6.2831855f;
            P_RAD[i] = 1.5f + 2.2f * (float) Math.pow(r.nextFloat(), 1.7);
            P_SPEED[i] = 0.55f / (float) Math.pow(P_RAD[i] / 1.5f, 1.5);
            P_SIZE[i] = 0.5f + r.nextFloat() * 1.1f;
            P_ALPHA[i] = 0.35f + r.nextFloat() * 0.65f;
        }
        for (int i = 0; i < STARS; ++i) {
            S_X[i] = r.nextFloat();
            S_Y[i] = r.nextFloat();
            S_SIZE[i] = 0.35f + r.nextFloat() * r.nextFloat() * 1.0f;
            S_PHASE[i] = r.nextFloat() * 6.2831855f;
            S_SPEED[i] = 0.6f + r.nextFloat() * 2.2f;
        }
    }

    private record Hit(float x, float y, float w, float h, int kind, Object ref, int index) {
        boolean contains(float px, float py) {
            return px >= this.x && px <= this.x + this.w && py >= this.y && py <= this.y + this.h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    @Nullable private DrawContext ctx;
    @Nullable private Category category;
    @Nullable private Module selected;
    @Nullable private Module bindingModule;
    private String search = "";
    private boolean searchFocused;

    private float openAnim;
    private boolean closing;
    private boolean freshOpen = true;
    private long lastNanos;
    private float time;
    private float dt = 0.016f;

    private float moduleScroll;
    private float moduleScrollTarget;
    private float moduleScrollMax;
    private float settingsScroll;
    private float settingsScrollTarget;
    private float settingsScrollMax;
    private float gridX, gridY, gridW, gridH;
    private float inspX, inspY, inspW, inspH;

    private int dragKind;
    @Nullable private Object dragRef;
    private float dragX;
    private float dragW;
    private boolean dragMaxHandle;

    private int mouseXi;
    private int mouseYi;
    private int accent = 0xFFFFA24A;
    private int accent2 = 0xFF7AA8FF;
    private int diskWarm = 0xFFFFA246;
    private int diskCool = 0xFFE0452A;

    private HorizonGui() {
        super((Text) Text.literal("Horizon"));
    }

    public static boolean isOpen() {
        return MinecraftClient.getInstance().currentScreen == INSTANCE;
    }

    /** Пока ждём клавишу для бинда, миксин не должен закрывать меню по клавише открытия. */
    public static boolean isBinding() {
        return INSTANCE.bindingModule != null && isOpen();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        super.init();
        if (this.freshOpen) {
            this.openAnim = 0.0f;
            this.freshOpen = false;
        }
        this.closing = false;
        this.lastNanos = 0L;
    }

    @Override
    public void removed() {
        super.removed();
        this.freshOpen = true;
        this.dragKind = 0;
        this.dragRef = null;
        this.bindingModule = null;
        this.searchFocused = false;
    }

    @Override
    public void close() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        this.bindingModule = null;
        this.searchFocused = false;
        this.dragKind = 0;
        this.dragRef = null;
    }

    private void finishClose() {
        this.closing = false;
        this.openAnim = 0.0f;
        this.freshOpen = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == this) {
            mc.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderScreen(@NotNull DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        this.ctx = graphics;
        this.mouseXi = mouseX;
        this.mouseYi = mouseY;
        long now = System.nanoTime();
        this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.1f, (float) (now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;
        this.time += this.dt;
        this.openAnim = this.approach(this.openAnim, this.closing ? 0.0f : 1.0f, this.closing ? 14.0f : 7.5f);
        if (this.closing && this.openAnim < 0.03f) {
            this.finishClose();
            return;
        }
        float a = easeOut(this.openAnim);
        this.refreshColors();
        this.hits.clear();
        this.ensureCategory();

        Render2D.rect(0.0f, 0.0f, (float) this.width, (float) this.height, col(0x000000, 0.62f * a));
        this.drawStars(a);
        float holeR = Math.min(this.width, this.height) * 0.14f * (0.8f + 0.2f * a);
        this.drawBlackHole(this.width * 0.5f, this.height * 0.5f, holeR, a, true);

        float pw = Math.min((float) this.width - 28.0f, 680.0f);
        float ph = Math.min((float) this.height - 28.0f, 392.0f);
        float px = ((float) this.width - pw) * 0.5f;
        float py = ((float) this.height - ph) * 0.5f + (1.0f - a) * 16.0f;
        float gap = 8.0f;
        float sw = 124.0f;
        float iw = pw < 600.0f ? 170.0f : 204.0f;
        float mw = pw - sw - iw - gap * 2.0f;

        this.renderSidebar(px, py, sw, ph, a);
        this.renderModules(px + sw + gap, py, mw, ph, a);
        this.renderInspector(px + sw + gap + mw + gap, py, iw, ph, a);

        if (this.dragKind != 0) {
            this.applyDrag((float) mouseX);
        }
    }

    private void renderSidebar(float x, float y, float w, float h, float a) {
        this.glass(x, y, w, h, 10.0f, a);
        this.drawBlackHole(x + 19.0f, y + 21.0f, 6.5f, a, false);
        Render2D.text(Fonts.MEDIUM, "FROSTIX", x + 33.0f, y + 13.0f, 9.0f,
                col(0xFFFFFF, a), col(this.accent, a), col(this.accent, a), col(0xFFFFFF, a));
        Fonts.MEDIUM.draw("event horizon", x + 33.0f, y + 24.0f, 5.0f, col(0xB8B4C8, 0.55f * a));
        Render2D.line(x + 10.0f, y + 38.0f, x + w - 10.0f, y + 38.0f, 0.6f, col(0xFFFFFF, 0.07f * a));

        ModuleManager mm = ModuleManager.Companion.get();
        float cy = y + 46.0f;
        for (Category c : this.categories()) {
            boolean sel = c == this.category && this.search.isEmpty();
            boolean hov = this.inside(x + 6.0f, cy, w - 12.0f, 21.0f);
            float sa = this.anim("cat_sel_" + c.name(), sel, 10.0f);
            float ha = this.anim("cat_hov_" + c.name(), hov, 12.0f);
            if (sa > 0.01f) {
                Render2D.rect(x + 6.0f, cy, w - 12.0f, 21.0f, 7.0f,
                        col(this.accent, 0.24f * sa * a), col(this.accent2, 0.05f * sa * a),
                        col(this.accent2, 0.05f * sa * a), col(this.accent, 0.24f * sa * a));
                Render2D.outline(x + 6.0f, cy, w - 12.0f, 21.0f, 7.0f, 0.6f, col(this.accent, 0.35f * sa * a));
                Render2D.rect(x + 6.0f, cy + 5.0f, 2.0f, 11.0f, 1.0f, col(this.accent, sa * a));
            }
            if (ha > 0.01f && sa < 0.99f) {
                Render2D.rect(x + 6.0f, cy, w - 12.0f, 21.0f, 7.0f, col(0xFFFFFF, 0.04f * ha * (1.0f - sa) * a));
            }
            float ix = x + 18.0f;
            float iy = cy + 10.5f;
            float lit = Math.max(sa, ha);
            Fonts.KIMIKO.msdf(icon(c), ix - 3.5f, iy - 3.5f, 7.0f, lerpColor(col(0x9A96AA, a), col(0xFFFFFF, a), lit));
            if (sa > 0.01f) {
                float oa = this.time * 2.6f;
                Render2D.circle(ix + (float) Math.cos(oa) * 7.0f, iy + (float) Math.sin(oa) * 2.6f, 1.1f, col(this.accent, sa * a));
            }
            Fonts.MEDIUM.draw(c.getDisplayName(), x + 30.0f, cy + 7.0f, 6.8f,
                    lerpColor(col(0xA8A4B8, a), col(0xFFFFFF, a), Math.max(sa, ha * 0.6f)));
            List<Module> list = mm.getByCategory(c);
            int on = 0;
            for (Module m : list) {
                if (m.isEnabled()) {
                    ++on;
                }
            }
            String cnt = on + "/" + list.size();
            float cw = Fonts.MEDIUM.width(cnt, 5.0f);
            Fonts.MEDIUM.draw(cnt, x + w - 12.0f - cw, cy + 8.0f, 5.0f, on > 0 ? col(this.accent, 0.9f * a) : col(0x77738A, 0.6f * a));
            this.hits.add(new Hit(x + 6.0f, cy, w - 12.0f, 21.0f, K_CAT, c, 0));
            cy += 24.0f;
        }

        float fy = y + h - 44.0f;
        Render2D.line(x + 10.0f, fy, x + w - 10.0f, fy, 0.6f, col(0xFFFFFF, 0.07f * a));
        float pulse = 0.5f + 0.5f * (float) Math.sin(this.time * 3.0f);
        Render2D.circle(x + 14.0f, fy + 11.0f, 2.2f + pulse * 1.2f, col(this.accent, 0.18f * a));
        Render2D.circle(x + 14.0f, fy + 11.0f, 1.6f, col(this.accent, a));
        Fonts.MEDIUM.draw("Активно: " + mm.getEnabled().size(), x + 21.0f, fy + 8.0f, 5.8f, col(0xE8E6F0, 0.85f * a));

        boolean ch = this.inside(x + 8.0f, fy + 20.0f, w - 16.0f, 16.0f);
        float cha = this.anim("classic_hov", ch, 12.0f);
        Render2D.rect(x + 8.0f, fy + 20.0f, w - 16.0f, 16.0f, 6.0f, col(0xFFFFFF, (0.04f + 0.05f * cha) * a));
        Render2D.outline(x + 8.0f, fy + 20.0f, w - 16.0f, 16.0f, 6.0f, 0.6f, col(0xFFFFFF, 0.08f * a));
        String cl = "Классический вид";
        float clw = Fonts.MEDIUM.width(cl, 5.5f);
        Fonts.MEDIUM.draw(cl, x + (w - clw) * 0.5f, fy + 25.0f, 5.5f, col(0xCFCBDD, (0.7f + 0.3f * cha) * a));
        this.hits.add(new Hit(x + 8.0f, fy + 20.0f, w - 16.0f, 16.0f, K_CLASSIC, null, 0));
    }

    private void renderModules(float x, float y, float w, float h, float a) {
        this.glass(x, y, w, h, 10.0f, a);

        float sx = x + 10.0f;
        float sy = y + 10.0f;
        float sW = w - 20.0f;
        float sH = 18.0f;
        float fa = this.anim("search_focus", this.searchFocused, 12.0f);
        Render2D.rect(sx, sy, sW, sH, 9.0f, col(0xFFFFFF, (0.045f + 0.03f * fa) * a));
        Render2D.outline(sx, sy, sW, sH, 9.0f, 0.7f, lerpColor(col(0xFFFFFF, 0.08f * a), col(this.accent, 0.7f * a), fa));
        Render2D.circleOutline(sx + 11.0f, sy + 8.3f, 3.2f, 1.0f, col(0xCFCBDD, 0.7f * a));
        Render2D.line(sx + 13.3f, sy + 10.6f, sx + 15.5f, sy + 12.8f, 1.0f, col(0xCFCBDD, 0.7f * a));
        boolean placeholder = this.search.isEmpty() && !this.searchFocused;
        Fonts.MEDIUM.draw(placeholder ? "Поиск модулей  (Ctrl+F)" : this.search, sx + 21.0f, sy + 6.0f, 6.0f,
                placeholder ? col(0x8C889C, 0.8f * a) : col(0xFFFFFF, a));
        if (this.searchFocused && ((int) (this.time * 2.0f)) % 2 == 0) {
            float cx = sx + 21.0f + Fonts.MEDIUM.width(this.search, 6.0f) + 1.0f;
            Render2D.rect(cx, sy + 4.5f, 0.8f, 9.0f, col(this.accent, a));
        }
        this.hits.add(new Hit(sx, sy, sW, sH, K_SEARCH, null, 0));

        List<Module> mods = this.visibleModules();
        String title = this.search.isEmpty() && this.category != null ? this.category.getDisplayName() : "Результаты";
        Fonts.MEDIUM.draw(title, x + 12.0f, y + 36.0f, 9.5f, col(0xFFFFFF, a));
        float tw = Fonts.MEDIUM.width(title, 9.5f);
        Render2D.circle(x + 17.0f + tw, y + 41.5f, 1.1f, col(this.accent, 0.8f * a));
        Fonts.MEDIUM.draw(mods.size() + " " + plural(mods.size()), x + 21.0f + tw, y + 38.5f, 6.0f, col(0x8C889C, 0.9f * a));

        this.gridX = x + 8.0f;
        this.gridY = y + 54.0f;
        this.gridW = w - 16.0f;
        this.gridH = h - 62.0f;
        int cols = this.gridW > 300.0f ? 2 : 1;
        float gap = 6.0f;
        float cardH = 40.0f;
        float cardW = (this.gridW - gap * (float) (cols - 1)) / (float) cols;
        int rows = (mods.size() + cols - 1) / cols;
        float content = Math.max(0.0f, (float) rows * (cardH + gap) - gap);
        this.moduleScrollMax = Math.max(0.0f, content - this.gridH);
        this.moduleScrollTarget = clamp(this.moduleScrollTarget, 0.0f, this.moduleScrollMax);
        this.moduleScroll = this.approach(this.moduleScroll, this.moduleScrollTarget, 16.0f);

        Render2D.pushScissor(this.ctx, this.gridX, this.gridY, this.gridW, this.gridH);
        for (int i = 0; i < mods.size(); ++i) {
            int c = i % cols;
            int r = i / cols;
            float cx = this.gridX + (float) c * (cardW + gap);
            float cy = this.gridY + (float) r * (cardH + gap) - this.moduleScroll;
            if (cy + cardH < this.gridY || cy > this.gridY + this.gridH) {
                continue;
            }
            this.renderCard(mods.get(i), cx, cy, cardW, cardH, a);
        }
        Render2D.popScissor(this.ctx);

        if (this.moduleScrollMax > 0.5f) {
            float thumb = Math.max(18.0f, this.gridH * this.gridH / content);
            float ty = this.gridY + (this.gridH - thumb) * (this.moduleScroll / this.moduleScrollMax);
            Render2D.rect(x + w - 4.5f, ty, 1.6f, thumb, 0.8f, col(this.accent, 0.5f * a));
        }
        if (mods.isEmpty()) {
            String e1 = "Пусто";
            String e2 = "даже свет отсюда не выбрался";
            Fonts.MEDIUM.draw(e1, x + (w - Fonts.MEDIUM.width(e1, 8.0f)) * 0.5f, y + h * 0.5f - 8.0f, 8.0f, col(0xFFFFFF, 0.8f * a));
            Fonts.MEDIUM.draw(e2, x + (w - Fonts.MEDIUM.width(e2, 5.5f)) * 0.5f, y + h * 0.5f + 3.0f, 5.5f, col(0x8C889C, 0.9f * a));
        }
    }

    private void renderCard(Module m, float x, float y, float w, float h, float a) {
        boolean on = m.isEnabled();
        boolean inGrid = (float) this.mouseYi >= this.gridY && (float) this.mouseYi <= this.gridY + this.gridH;
        boolean hov = inGrid && this.inside(x, y, w, h);
        String key = m.getName();
        float ta = this.anim(key + "#t", on, 9.0f);
        float ha = this.anim(key + "#h", hov, 14.0f);
        float sa = this.anim(key + "#s", m == this.selected, 12.0f);
        boolean binding = m == this.bindingModule;

        if (ta > 0.01f) {
            Render2D.rect(x - 1.5f, y - 1.5f, w + 3.0f, h + 3.0f, 9.5f, col(this.accent, 0.07f * ta * a));
        }
        Render2D.rect(x, y, w, h, 8.0f, col(0xFFFFFF, (0.035f + 0.035f * ha) * a));
        if (ta > 0.01f) {
            Render2D.rect(x, y, w, h, 8.0f, col(this.accent, 0.18f * ta * a), col(this.accent, 0.0f),
                    col(this.accent, 0.0f), col(this.accent, 0.10f * ta * a));
        }
        int outline = lerpColor(col(0xFFFFFF, (0.06f + 0.04f * ha) * a), col(this.accent, 0.55f * a), ta);
        Render2D.outline(x, y, w, h, 8.0f, 0.7f, outline);
        if (sa > 0.01f) {
            Render2D.outline(x, y, w, h, 8.0f, 0.9f, col(this.accent2, 0.7f * sa * a), col(this.accent2, 0.15f * sa * a),
                    col(this.accent2, 0.15f * sa * a), col(this.accent2, 0.7f * sa * a));
        }

        // мини-сингулярность: чёрная тень + фотонное кольцо, у включённого модуля по орбите бегает частица
        float scx = x + 13.0f;
        float scy = y + h * 0.5f;
        if (ta > 0.01f) {
            Render2D.circle(scx, scy, 7.5f, col(this.accent, 0.12f * ta * a));
        }
        Render2D.circle(scx, scy, 4.6f, col(0x000000, 0.9f * a));
        Render2D.circleOutline(scx, scy, 5.0f, 0.9f, lerpColor(col(0x6E6A80, 0.6f * a), col(this.accent, a), ta));
        if (ta > 0.01f) {
            float oa = this.time * 3.2f + (float) (key.hashCode() & 0xFF) * 0.1f;
            Render2D.circle(scx + (float) Math.cos(oa) * 7.0f, scy + (float) Math.sin(oa) * 2.4f, 1.0f, col(0xFFF3E6, ta * a));
        }

        float tx = x + 25.0f;
        float textW = w - 25.0f - 34.0f;
        Fonts.MEDIUM.draw(fit(m.getDisplayName(), 7.2f, textW), tx, y + 9.0f, 7.2f, col(0xFFFFFF, (0.78f + 0.22f * Math.max(ta, ha)) * a));
        if (binding) {
            float p = 0.6f + 0.4f * (float) Math.sin(this.time * 6.0f);
            Fonts.MEDIUM.draw(fit("Нажми клавишу (Del - сброс)", 5.2f, textW + 20.0f), tx, y + 22.0f, 5.2f, col(this.accent, p * a));
        } else {
            Fonts.MEDIUM.draw(fit(m.getDisplayDescription(), 5.2f, textW), tx, y + 22.0f, 5.2f, col(0x8C889C, 0.9f * a));
        }

        this.drawSwitch(x + w - 26.0f, y + 9.0f, 18.0f, 9.0f, ta, a);
        KeyBind bind = m.getBind();
        if (bind != null && bind.isBound()) {
            String bk = bind.getDisplayName();
            float bw = Fonts.MEDIUM.width(bk, 4.8f) + 6.0f;
            float bx = x + w - 8.0f - bw;
            Render2D.rect(bx, y + h - 13.0f, bw, 8.0f, 4.0f, col(0xFFFFFF, 0.06f * a));
            Fonts.MEDIUM.draw(bk, bx + 3.0f, y + h - 11.4f, 4.8f, col(0xBDB9CC, 0.9f * a));
        }

        float hy = Math.max(y, this.gridY);
        float hb = Math.min(y + h, this.gridY + this.gridH);
        if (hb > hy) {
            this.hits.add(new Hit(x, hy, w, hb - hy, K_MODULE, m, 0));
        }
    }

    private void renderInspector(float x, float y, float w, float h, float a) {
        this.glass(x, y, w, h, 10.0f, a);
        Module m = this.selected;
        if (m == null) {
            this.drawBlackHole(x + w * 0.5f, y + h * 0.5f - 22.0f, 15.0f, a, false);
            String t1 = "Выбери модуль";
            String t2 = "ПКМ по карточке - настройки";
            String t3 = "СКМ - назначить бинд";
            Fonts.MEDIUM.draw(t1, x + (w - Fonts.MEDIUM.width(t1, 7.0f)) * 0.5f, y + h * 0.5f + 16.0f, 7.0f, col(0xFFFFFF, 0.85f * a));
            Fonts.MEDIUM.draw(t2, x + (w - Fonts.MEDIUM.width(t2, 5.2f)) * 0.5f, y + h * 0.5f + 28.0f, 5.2f, col(0x8C889C, 0.9f * a));
            Fonts.MEDIUM.draw(t3, x + (w - Fonts.MEDIUM.width(t3, 5.2f)) * 0.5f, y + h * 0.5f + 36.0f, 5.2f, col(0x8C889C, 0.9f * a));
            return;
        }

        float cx = x + 12.0f;
        float cy = y + 12.0f;
        boolean on = m.isEnabled();
        String st = on ? "ВКЛ" : "ВЫКЛ";
        float stw = Fonts.MEDIUM.width(st, 5.0f) + 8.0f;
        float stx = x + w - 12.0f - stw;
        Fonts.MEDIUM.draw(fit(m.getDisplayName(), 9.0f, w - 30.0f - stw), cx, cy, 9.0f, col(0xFFFFFF, a));
        Render2D.rect(stx, cy, stw, 10.0f, 5.0f, on ? col(this.accent, 0.85f * a) : col(0xFFFFFF, 0.08f * a));
        Fonts.MEDIUM.draw(st, stx + 4.0f, cy + 2.6f, 5.0f, on ? col(0x0A0810, a) : col(0xBDB9CC, a));
        this.hits.add(new Hit(stx, cy, stw, 10.0f, K_TOGGLE, m, 0));

        float ly = cy + 15.0f;
        List<String> lines = this.wrap(m.getDisplayDescription(), 5.4f, w - 24.0f);
        for (int i = 0; i < Math.min(3, lines.size()); ++i) {
            Fonts.MEDIUM.draw(lines.get(i), cx, ly, 5.4f, col(0x9A96AA, 0.9f * a));
            ly += 8.0f;
        }

        float by = ly + 4.0f;
        Fonts.MEDIUM.draw("Бинд", cx, by + 3.5f, 6.0f, col(0xBDB9CC, 0.8f * a));
        boolean binding = this.bindingModule == m;
        KeyBind bind = m.getBind();
        String bk = binding ? "..." : (bind != null && bind.isBound() ? bind.getDisplayName() : "нет");
        float bw = Math.max(26.0f, Fonts.MEDIUM.width(bk, 5.5f) + 12.0f);
        float bx = x + w - 12.0f - bw;
        float bp = binding ? 0.5f + 0.5f * (float) Math.sin(this.time * 6.0f) : 0.0f;
        Render2D.rect(bx, by, bw, 13.0f, 6.5f, lerpColor(col(0xFFFFFF, 0.06f * a), col(this.accent, 0.5f * a), bp));
        Render2D.outline(bx, by, bw, 13.0f, 6.5f, 0.6f, col(0xFFFFFF, 0.1f * a));
        Fonts.MEDIUM.draw(bk, bx + (bw - Fonts.MEDIUM.width(bk, 5.5f)) * 0.5f, by + 3.6f, 5.5f, col(0xFFFFFF, 0.9f * a));
        this.hits.add(new Hit(bx, by, bw, 13.0f, K_BIND, m, 0));

        float dy = by + 19.0f;
        Render2D.line(x + 10.0f, dy, x + w - 10.0f, dy, 0.6f, col(0xFFFFFF, 0.07f * a));

        this.inspX = x;
        this.inspY = dy + 5.0f;
        this.inspW = w;
        this.inspH = y + h - 8.0f - this.inspY;
        this.settingsScrollTarget = clamp(this.settingsScrollTarget, 0.0f, this.settingsScrollMax);
        this.settingsScroll = this.approach(this.settingsScroll, this.settingsScrollTarget, 16.0f);

        Render2D.pushScissor(this.ctx, x + 4.0f, this.inspY, w - 8.0f, this.inspH);
        float ry = this.inspY + 2.0f - this.settingsScroll;
        int shown = 0;
        for (Setting s : m.getSettings().all()) {
            if (s instanceof PositionSettings || !s.isVisible()) {
                continue;
            }
            float rh = this.renderSetting(s, x + 10.0f, ry, w - 20.0f, a);
            ry += rh + 4.0f;
            ++shown;
        }
        Render2D.popScissor(this.ctx);
        float contentH = ry + this.settingsScroll - this.inspY;
        this.settingsScrollMax = Math.max(0.0f, contentH - this.inspH);
        if (this.settingsScrollMax > 0.5f) {
            float thumb = Math.max(16.0f, this.inspH * this.inspH / contentH);
            float ty = this.inspY + (this.inspH - thumb) * (this.settingsScroll / this.settingsScrollMax);
            Render2D.rect(x + w - 4.5f, ty, 1.6f, thumb, 0.8f, col(this.accent2, 0.5f * a));
        }
        if (shown == 0) {
            String t = "Нет настроек";
            Fonts.MEDIUM.draw(t, x + (w - Fonts.MEDIUM.width(t, 6.0f)) * 0.5f, this.inspY + 16.0f, 6.0f, col(0x8C889C, 0.9f * a));
        }
    }

    private float renderSetting(Setting s, float x, float y, float w, float a) {
        String name = s.getDisplayName();
        if (s instanceof SeparatorSetting) {
            String t = name.toUpperCase(Locale.ROOT);
            Fonts.MEDIUM.draw(t, x, y + 4.0f, 5.2f, col(this.accent2, 0.85f * a));
            float tw = Fonts.MEDIUM.width(t, 5.2f) + 4.0f;
            Render2D.line(x + tw, y + 7.0f, x + w, y + 7.0f, 0.6f, col(0xFFFFFF, 0.08f * a));
            return 13.0f;
        }
        if (s instanceof BooleanSetting b) {
            boolean v = b.getValue();
            float t = this.anim(s, v, 12.0f);
            this.rowHover(x, y, w, 16.0f, a);
            Fonts.MEDIUM.draw(fit(name, 6.2f, w - 26.0f), x + 2.0f, y + 4.5f, 6.2f, col(0xE8E6F0, (0.75f + 0.25f * t) * a));
            this.drawSwitch(x + w - 18.0f, y + 3.8f, 16.0f, 8.5f, t, a);
            this.addHit(x, y, w, 16.0f, K_BOOL, s, 0);
            return 16.0f;
        }
        if (s instanceof RangeSliderSetting r) {
            String val = fmt(r.getMinValue(), r.getIncrement()) + " - " + fmt(r.getMaxValue(), r.getIncrement());
            this.sliderHeader(name, val, x, y, w, a);
            float p0 = clamp01(r.getMinProgress());
            float p1 = clamp01(r.getMaxProgress());
            Render2D.rect(x, y + 13.0f, w, 3.0f, 1.5f, col(0xFFFFFF, 0.08f * a));
            Render2D.rect(x + w * p0, y + 13.0f, Math.max(3.0f, w * (p1 - p0)), 3.0f, 1.5f,
                    col(this.accent, a), col(this.accent2, a), col(this.accent2, a), col(this.accent, a));
            this.knob(x + w * p0, y + 14.5f, a);
            this.knob(x + w * p1, y + 14.5f, a);
            this.addHit(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f, K_RANGE, s, 0);
            return 21.0f;
        }
        if (s instanceof SliderSetting sl) {
            this.sliderHeader(name, sl.isInteger() ? String.valueOf(Math.round(sl.getValue())) : fmt(sl.getValue(), sl.getIncrement()), x, y, w, a);
            float p = clamp01(sl.getProgress());
            Render2D.rect(x, y + 13.0f, w, 3.0f, 1.5f, col(0xFFFFFF, 0.08f * a));
            Render2D.rect(x, y + 13.0f, Math.max(3.0f, w * p), 3.0f, 1.5f,
                    col(this.accent, a), col(this.accent2, a), col(this.accent2, a), col(this.accent, a));
            this.knob(x + w * p, y + 14.5f, a);
            this.addHit(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f, K_SLIDER, s, 0);
            return 21.0f;
        }
        if (s instanceof ColorSetting c) {
            Fonts.MEDIUM.draw(fit(name, 6.2f, w - 16.0f), x + 2.0f, y + 2.0f, 6.2f, col(0xE8E6F0, 0.9f * a));
            Render2D.circle(x + w - 5.0f, y + 5.0f, 4.2f, col(c.getColorOpaque(), a));
            Render2D.circleOutline(x + w - 5.0f, y + 5.0f, 4.4f, 0.6f, col(0xFFFFFF, 0.25f * a));
            float seg = w / 6.0f;
            int[] stops = new int[]{0xFFFF3B3B, 0xFFFFE53B, 0xFF3BFF6A, 0xFF3BE8FF, 0xFF3B55FF, 0xFFE53BFF, 0xFFFF3B3B};
            for (int i = 0; i < 6; ++i) {
                int c0 = col(stops[i], a);
                int c1 = col(stops[i + 1], a);
                float r0 = i == 0 ? 2.0f : 0.0f;
                float r1 = i == 5 ? 2.0f : 0.0f;
                Render2D.rect(x + seg * (float) i, y + 13.0f, seg + 0.3f, 4.0f, r0, r1, r1, r0, c0, c1, c1, c0);
            }
            this.knob(x + w * clamp01(c.getHue()), y + 15.0f, a);
            this.addHit(x - 3.0f, y + 9.0f, w + 6.0f, 11.0f, K_HUE, s, 0);
            return 21.0f;
        }
        if (s instanceof MultiSelectSetting ms) {
            Fonts.MEDIUM.draw(fit(name, 6.2f, w), x + 2.0f, y + 2.0f, 6.2f, col(0xE8E6F0, 0.9f * a));
            float cx = x;
            float cy = y + 12.0f;
            List<String> opts = ms.getOptions();
            for (int i = 0; i < opts.size(); ++i) {
                String o = opts.get(i);
                String label = fit(o, 5.4f, w - 10.0f);
                float tw = Fonts.MEDIUM.width(label, 5.4f) + 10.0f;
                if (cx + tw > x + w && cx > x) {
                    cx = x;
                    cy += 14.0f;
                }
                boolean sel = ms.is(o);
                float t = this.anim(s.getName() + "#" + o, sel, 12.0f);
                Render2D.rect(cx, cy, tw, 11.0f, 5.5f, lerpColor(col(0xFFFFFF, 0.06f * a), col(this.accent, 0.8f * a), t));
                Fonts.MEDIUM.draw(label, cx + 5.0f, cy + 3.0f, 5.4f, lerpColor(col(0xBDB9CC, a), col(0x0A0810, a), t));
                this.addHit(cx, cy, tw, 11.0f, K_MULTI, s, i);
                cx += tw + 4.0f;
            }
            return cy + 11.0f - y;
        }
        if (s instanceof SelectSetting sel) {
            this.rowHover(x, y, w, 16.0f, a);
            String v = sel.getDisplaySelected();
            float vw = Math.min(w * 0.55f, Fonts.MEDIUM.width(v, 5.6f) + 20.0f);
            String vf = fit(v, 5.6f, vw - 20.0f);
            Fonts.MEDIUM.draw(fit(name, 6.2f, w - vw - 6.0f), x + 2.0f, y + 4.5f, 6.2f, col(0xE8E6F0, 0.9f * a));
            float vx = x + w - vw;
            Render2D.rect(vx, y + 2.0f, vw, 12.0f, 6.0f, col(0xFFFFFF, 0.06f * a));
            Render2D.outline(vx, y + 2.0f, vw, 12.0f, 6.0f, 0.6f, col(this.accent, 0.3f * a));
            Fonts.MEDIUM.draw("<", vx + 4.0f, y + 5.0f, 5.6f, col(this.accent, 0.8f * a));
            Fonts.MEDIUM.draw(">", vx + vw - 8.0f, y + 5.0f, 5.6f, col(this.accent, 0.8f * a));
            Fonts.MEDIUM.draw(vf, vx + (vw - Fonts.MEDIUM.width(vf, 5.6f)) * 0.5f, y + 5.0f, 5.6f, col(0xFFFFFF, a));
            this.addHit(x, y, w, 16.0f, K_SELECT, s, 0);
            return 16.0f;
        }
        if (s instanceof ButtonSetting btn) {
            boolean hov = this.inside(x, y, w, 15.0f);
            float ha = this.anim(s, hov, 12.0f);
            Render2D.rect(x, y, w, 15.0f, 7.5f, col(this.accent, (0.22f + 0.18f * ha) * a), col(this.accent2, (0.10f + 0.1f * ha) * a),
                    col(this.accent2, (0.10f + 0.1f * ha) * a), col(this.accent, (0.22f + 0.18f * ha) * a));
            Render2D.outline(x, y, w, 15.0f, 7.5f, 0.6f, col(this.accent, 0.45f * a));
            String label = btn.getDisplayLabel();
            if (label == null || label.isEmpty()) {
                label = btn.getLabel();
            }
            String text = label == null || label.isEmpty() ? name : name + ": " + label;
            text = fit(text, 5.8f, w - 10.0f);
            Fonts.MEDIUM.draw(text, x + (w - Fonts.MEDIUM.width(text, 5.8f)) * 0.5f, y + 4.6f, 5.8f, col(0xFFFFFF, a));
            this.addHit(x, y, w, 15.0f, K_BUTTON, s, 0);
            return 15.0f;
        }
        if (s instanceof TextSetting ts) {
            String v = ts.getText() == null ? "" : ts.getText();
            float vw = Math.min(w * 0.5f, Fonts.MEDIUM.width(v, 5.4f));
            Fonts.MEDIUM.draw(fit(name, 6.2f, w - vw - 6.0f), x + 2.0f, y + 4.5f, 6.2f, col(0xE8E6F0, 0.9f * a));
            String vf = fit(v, 5.4f, w * 0.5f);
            Fonts.MEDIUM.draw(vf, x + w - Fonts.MEDIUM.width(vf, 5.4f), y + 5.0f, 5.4f, col(0x9A96AA, a));
            return 16.0f;
        }
        if (s instanceof BindSetting bs) {
            KeyBind kb = bs.getValue();
            String v = kb != null && kb.isBound() ? kb.getDisplayName() : "нет";
            Fonts.MEDIUM.draw(fit(name, 6.2f, w - 40.0f), x + 2.0f, y + 4.5f, 6.2f, col(0xE8E6F0, 0.9f * a));
            float vw = Fonts.MEDIUM.width(v, 5.4f) + 10.0f;
            Render2D.rect(x + w - vw, y + 2.0f, vw, 12.0f, 6.0f, col(0xFFFFFF, 0.06f * a));
            Fonts.MEDIUM.draw(v, x + w - vw + 5.0f, y + 5.2f, 5.4f, col(0xBDB9CC, a));
            return 16.0f;
        }
        Fonts.MEDIUM.draw(fit(name, 6.2f, w), x + 2.0f, y + 3.5f, 6.2f, col(0xE8E6F0, 0.8f * a));
        return 14.0f;
    }

    // ------------------------------------------------------------------ black hole

    private void drawStars(float a) {
        for (int i = 0; i < STARS; ++i) {
            float tw = 0.5f + 0.5f * (float) Math.sin(this.time * S_SPEED[i] + S_PHASE[i]);
            Render2D.circle(S_X[i] * (float) this.width, S_Y[i] * (float) this.height, S_SIZE[i], col(0xFFFFFF, (0.1f + 0.45f * tw) * a));
        }
    }

    private void drawBlackHole(float cx, float cy, float R, float a, boolean full) {
        if (a <= 0.01f || R <= 0.5f) {
            return;
        }
        float px = Math.max(0.45f, R / 55.0f);
        int n = full ? PARTICLES : MINI_PARTICLES;
        float breathe = 0.92f + 0.08f * (float) Math.sin(this.time * 1.7f);
        int halo = lerpColor(this.diskWarm, this.accent, 0.35f);
        for (int i = 0; i < 6; ++i) {
            float rr = R * (3.6f - (float) i * 0.42f);
            Render2D.circle(cx, cy, rr, col(halo, a * (full ? 0.018f : 0.03f) * (float) (i + 1) * breathe));
        }
        // задняя половина диска + основная линзированная дуга над тенью
        for (int i = 0; i < n; ++i) {
            float th = P_ANGLE[i] + this.time * P_SPEED[i];
            float s = (float) Math.sin(th);
            if (s >= 0.0f) {
                continue;
            }
            float c = (float) Math.cos(th);
            float r = P_RAD[i] * R;
            int pc = this.particleColor(i);
            float dop = 0.5f - 0.5f * c;
            float al = P_ALPHA[i] * (0.35f + 0.65f * dop) * a;
            float size = P_SIZE[i] * px * (0.8f + 0.4f * dop);
            Render2D.circle(cx + c * r, cy + s * r * TILT, size, col(pc, al * 0.8f));
            float rho = R * (1.16f + (P_RAD[i] - 1.5f) * 0.38f);
            Render2D.circle(cx + c * rho, cy + s * rho, P_SIZE[i] * px * 0.85f, col(pc, al * 0.75f));
        }
        // тень горизонта событий и фотонное кольцо
        Render2D.circle(cx, cy, R * 1.02f, col(0x000000, a));
        int ringHot = lerpColor(0xFFFFF3E6, this.accent, 0.2f);
        Render2D.circleOutline(cx, cy, R * 1.2f, 4.5f * px, col(halo, 0.10f * a * breathe));
        Render2D.circleOutline(cx, cy, R * 1.1f, 2.6f * px, col(this.diskWarm, 0.28f * a * breathe));
        Render2D.circleOutline(cx, cy, R * 1.045f, 1.1f * px + 0.3f, col(ringHot, 0.9f * a));
        // вторичное изображение снизу + передняя половина диска поверх тени
        for (int i = 0; i < n; ++i) {
            float th = P_ANGLE[i] + this.time * P_SPEED[i];
            float s = (float) Math.sin(th);
            if (s < 0.0f) {
                continue;
            }
            float c = (float) Math.cos(th);
            float r = P_RAD[i] * R;
            int pc = this.particleColor(i);
            float dop = 0.5f - 0.5f * c;
            float al = P_ALPHA[i] * (0.35f + 0.65f * dop) * a;
            float size = P_SIZE[i] * px * (0.8f + 0.4f * dop);
            float rho = R * (1.12f + (P_RAD[i] - 1.5f) * 0.3f);
            Render2D.circle(cx + c * rho, cy + s * rho, P_SIZE[i] * px * 0.7f, col(pc, al * 0.35f));
            Render2D.circle(cx + c * r, cy + s * r * TILT, size, col(pc, al));
        }
    }

    private int particleColor(int i) {
        float t = (P_RAD[i] - 1.5f) / 2.2f;
        if (t < 0.35f) {
            return lerpColor(0xFFFFF1DC, this.diskWarm, t / 0.35f);
        }
        return lerpColor(this.diskWarm, this.diskCool, (t - 0.35f) / 0.65f);
    }

    // ------------------------------------------------------------------ widgets

    private void glass(float x, float y, float w, float h, float r, float a) {
        if (a > 0.2f) {
            Render2D.blur(x, y, w, h, r);
        }
        Render2D.rect(x, y, w, h, r, col(0x0E0C18, 0.70f * a), col(0x0E0C18, 0.70f * a), col(0x060509, 0.82f * a), col(0x060509, 0.82f * a));
        Render2D.outline(x, y, w, h, r, 0.8f, col(this.accent, 0.32f * a), col(this.accent2, 0.14f * a), col(0xFFFFFF, 0.04f * a), col(this.accent, 0.10f * a));
        Render2D.line(x + r, y + 0.6f, x + w - r, y + 0.6f, 0.5f, col(0xFFFFFF, 0.07f * a));
    }

    private void drawSwitch(float x, float y, float w, float h, float t, float a) {
        Render2D.rect(x, y, w, h, h * 0.5f, lerpColor(col(0xFFFFFF, 0.10f * a), col(this.accent, 0.85f * a), t));
        float kr = h * 0.5f - 1.5f;
        float kx = x + h * 0.5f + (w - h) * t;
        float ky = y + h * 0.5f;
        if (t > 0.01f) {
            Render2D.circle(kx, ky, h * 0.9f, col(this.accent, 0.2f * t * a));
        }
        Render2D.circle(kx, ky, kr, col(0xFFFFFF, a));
    }

    private void knob(float x, float y, float a) {
        Render2D.circle(x, y, 5.0f, col(this.accent, 0.18f * a));
        Render2D.circle(x, y, 3.0f, col(0xFFFFFF, a));
    }

    private void sliderHeader(String name, String val, float x, float y, float w, float a) {
        float vw = Fonts.MEDIUM.width(val, 5.6f);
        Fonts.MEDIUM.draw(fit(name, 6.2f, w - vw - 6.0f), x + 2.0f, y + 2.0f, 6.2f, col(0xE8E6F0, 0.9f * a));
        Fonts.MEDIUM.draw(val, x + w - vw, y + 2.5f, 5.6f, col(this.accent, 0.95f * a));
    }

    private void rowHover(float x, float y, float w, float h, float a) {
        if (this.inside(x, y, w, h) && (float) this.mouseYi >= this.inspY && (float) this.mouseYi <= this.inspY + this.inspH) {
            Render2D.rect(x - 3.0f, y, w + 6.0f, h, 5.0f, col(0xFFFFFF, 0.035f * a));
        }
    }

    private void addHit(float x, float y, float w, float h, int kind, Object ref, int index) {
        float hy = Math.max(y, this.inspY);
        float hb = Math.min(y + h, this.inspY + this.inspH);
        if (hb > hy) {
            this.hits.add(new Hit(x, hy, w, hb - hy, kind, ref, index));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NotNull Click event, boolean doubleClick) {
        if (this.closing) {
            return true;
        }
        float mx = (float) event.x();
        float my = (float) event.y();
        int button = event.button();
        if (this.bindingModule != null) {
            if (button >= 2) {
                this.bindingModule.setBind(KeyBind.Companion.mouse(button));
            }
            this.bindingModule = null;
            return true;
        }
        boolean hitSearch = false;
        for (int i = this.hits.size() - 1; i >= 0; --i) {
            Hit h = this.hits.get(i);
            if (!h.contains(mx, my)) {
                continue;
            }
            if (h.kind() == K_SEARCH) {
                hitSearch = true;
            }
            this.handleClick(h, button, mx);
            break;
        }
        if (!hitSearch) {
            this.searchFocused = false;
        }
        return true;
    }

    private void handleClick(Hit h, int button, float mx) {
        switch (h.kind()) {
            case K_CAT -> {
                this.category = (Category) h.ref();
                this.search = "";
                this.moduleScrollTarget = 0.0f;
            }
            case K_MODULE -> {
                Module m = (Module) h.ref();
                if (button == 0) {
                    m.toggle();
                } else if (button == 1) {
                    if (this.selected != m) {
                        this.settingsScroll = 0.0f;
                        this.settingsScrollTarget = 0.0f;
                    }
                    this.selected = m;
                } else if (button == 2) {
                    this.bindingModule = m;
                }
            }
            case K_TOGGLE -> ((Module) h.ref()).toggle();
            case K_SEARCH -> this.searchFocused = true;
            case K_BIND -> this.bindingModule = (Module) h.ref();
            case K_BOOL -> ((BooleanSetting) h.ref()).toggle();
            case K_SLIDER, K_HUE -> {
                this.dragKind = h.kind();
                this.dragRef = h.ref();
                this.dragX = h.x() + 3.0f;
                this.dragW = h.w() - 6.0f;
                this.applyDrag(mx);
            }
            case K_RANGE -> {
                RangeSliderSetting r = (RangeSliderSetting) h.ref();
                this.dragKind = K_RANGE;
                this.dragRef = r;
                this.dragX = h.x() + 3.0f;
                this.dragW = h.w() - 6.0f;
                float p = clamp01((mx - this.dragX) / Math.max(1.0f, this.dragW));
                this.dragMaxHandle = Math.abs(p - r.getMaxProgress()) < Math.abs(p - r.getMinProgress());
                this.applyDrag(mx);
            }
            case K_SELECT -> {
                SelectSetting s = (SelectSetting) h.ref();
                List<String> opts = s.getOptions();
                if (!opts.isEmpty()) {
                    int idx = Math.max(0, opts.indexOf(s.getSelected()));
                    idx = button == 1 ? (idx - 1 + opts.size()) % opts.size() : (idx + 1) % opts.size();
                    s.setSelected(opts.get(idx));
                }
            }
            case K_MULTI -> {
                MultiSelectSetting ms = (MultiSelectSetting) h.ref();
                List<String> opts = ms.getOptions();
                if (h.index() >= 0 && h.index() < opts.size()) {
                    ms.toggle(opts.get(h.index()));
                }
            }
            case K_BUTTON -> ((ButtonSetting) h.ref()).click();
            case K_CLASSIC -> MinecraftClient.getInstance().setScreen(UI.INSTANCE);
            default -> {
            }
        }
    }

    private void applyDrag(float mx) {
        if (this.dragRef == null || this.dragW <= 0.0f) {
            return;
        }
        float p = clamp01((mx - this.dragX) / this.dragW);
        if (this.dragKind == K_SLIDER && this.dragRef instanceof SliderSetting sl) {
            float min = sl.getMin();
            float max = sl.getMax();
            float v = min + p * (max - min);
            float inc = sl.getIncrement();
            if (inc > 0.0f) {
                v = min + (float) Math.round((v - min) / inc) * inc;
            }
            if (sl.isInteger()) {
                v = (float) Math.round(v);
            }
            sl.setValue(clamp(v, min, max));
        } else if (this.dragKind == K_RANGE && this.dragRef instanceof RangeSliderSetting r) {
            float v = r.valueAtProgress(p);
            if (this.dragMaxHandle) {
                r.setMaxValue(Math.max(v, r.getMinValue()));
            } else {
                r.setMinValue(Math.min(v, r.getMaxValue()));
            }
        } else if (this.dragKind == K_HUE && this.dragRef instanceof ColorSetting c) {
            float sat = c.getSaturation() < 0.05f ? 0.85f : c.getSaturation();
            float bri = c.getBrightness() < 0.05f ? 1.0f : c.getBrightness();
            c.setHSB(Math.min(p, 0.999f), sat, bri);
        }
    }

    @Override
    public boolean mouseDragged(@NotNull Click event, double dragX, double dragY) {
        if (this.dragKind != 0) {
            this.applyDrag((float) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NotNull Click event) {
        this.dragKind = 0;
        this.dragRef = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float mx = (float) mouseX;
        float my = (float) mouseY;
        if (mx >= this.gridX && mx <= this.gridX + this.gridW && my >= this.gridY && my <= this.gridY + this.gridH) {
            this.moduleScrollTarget = clamp(this.moduleScrollTarget - (float) vertical * 28.0f, 0.0f, this.moduleScrollMax);
        } else if (mx >= this.inspX && mx <= this.inspX + this.inspW && my >= this.inspY && my <= this.inspY + this.inspH) {
            this.settingsScrollTarget = clamp(this.settingsScrollTarget - (float) vertical * 22.0f, 0.0f, this.settingsScrollMax);
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyInput event) {
        int key = event.key();
        if (this.bindingModule != null) {
            if (key == 261 || key == 259) {
                this.bindingModule.setBind(KeyBind.NONE);
            } else if (key != 256) {
                this.bindingModule.setBind(KeyBind.Companion.keyboard(key));
            }
            this.bindingModule = null;
            return true;
        }
        if ((event.modifiers() & 2) != 0 && key == 70) {
            this.searchFocused = true;
            return true;
        }
        if (key == 256) {
            if (this.searchFocused || !this.search.isEmpty()) {
                this.search = "";
                this.searchFocused = false;
                return true;
            }
            this.close();
            return true;
        }
        if (this.searchFocused) {
            if (key == 259 && !this.search.isEmpty()) {
                this.search = this.search.substring(0, this.search.length() - 1);
                this.moduleScrollTarget = 0.0f;
            }
            return true;
        }
        int toggleKey = 344;
        ClickGui cg = ClickGui.getInstance();
        if (cg != null && cg.getBind() != null && cg.getBind().getCode() > 0) {
            toggleKey = cg.getBind().getCode();
        }
        if (key == toggleKey || key == 344) {
            this.close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharInput event) {
        if (this.bindingModule != null || this.closing) {
            return true;
        }
        int cp = event.codepoint();
        if (Character.isISOControl(cp) || !Character.isDefined(cp)) {
            return true;
        }
        if (!this.searchFocused) {
            if (cp == ' ') {
                return true;
            }
            this.searchFocused = true;
        }
        if (this.search.length() < 32) {
            this.search = this.search + new String(Character.toChars(cp));
            this.moduleScrollTarget = 0.0f;
        }
        return true;
    }

    // ------------------------------------------------------------------ data

    private List<Category> categories() {
        List<Category> out = new ArrayList<>();
        ModuleManager mm = ModuleManager.Companion.get();
        for (Category c : Category.values()) {
            if (!mm.getByCategory(c).isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    private void ensureCategory() {
        List<Category> cats = this.categories();
        if (this.category == null || !cats.contains(this.category)) {
            this.category = cats.isEmpty() ? null : cats.get(0);
        }
    }

    private List<Module> visibleModules() {
        ModuleManager mm = ModuleManager.Companion.get();
        if (this.search.isEmpty()) {
            return this.category == null ? new ArrayList<>() : new ArrayList<>(mm.getByCategory(this.category));
        }
        String q = this.search.toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        for (Module m : mm.getAll()) {
            String dn = m.getDisplayName();
            if ((dn != null && dn.toLowerCase(Locale.ROOT).contains(q)) || m.getName().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    private void refreshColors() {
        int p = 0xFFFFA24A;
        int s = 0xFF7AA8FF;
        try {
            InterfaceModule iface = InterfaceModule.Companion.getInstance();
            if (iface != null) {
                p = iface.clientPrimaryColorOpaque();
                s = iface.usesSecondClientColor() ? iface.clientSecondaryColorOpaque() : lerpColor(p, 0xFFFFFFFF, 0.35f);
            }
        } catch (Throwable ignored) {
        }
        this.accent = p;
        this.accent2 = s;
        this.diskWarm = lerpColor(0xFFFFA246, p, 0.3f);
        this.diskCool = lerpColor(0xFFE0452A, s, 0.3f);
    }

    private static String icon(Category c) {
        return switch (c) {
            case VISUALS -> "p";
            case DISPLAY -> "j";
            case UTILS -> "r";
            case EVENTS -> "i";
            case CONFIGS -> "w";
            case THEMES -> "B";
        };
    }

    private static String plural(int n) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) {
            return "модуль";
        }
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) {
            return "модуля";
        }
        return "модулей";
    }

    // ------------------------------------------------------------------ utils

    private float anim(Object key, boolean on, float speed) {
        float target = on ? 1.0f : 0.0f;
        Float cur = this.anims.get(key);
        float v = cur == null ? target : this.approach(cur, target, speed);
        this.anims.put(key, v);
        return v;
    }

    private float approach(float cur, float target, float speed) {
        float k = 1.0f - (float) Math.exp(-speed * this.dt);
        float v = cur + (target - cur) * k;
        return Math.abs(target - v) < 0.001f ? target : v;
    }

    private boolean inside(float x, float y, float w, float h) {
        return (float) this.mouseXi >= x && (float) this.mouseXi <= x + w && (float) this.mouseYi >= y && (float) this.mouseYi <= y + h;
    }

    private static String fit(@Nullable String s, float size, float maxW) {
        if (s == null) {
            return "";
        }
        if (Fonts.MEDIUM.width(s, size) <= maxW) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && Fonts.MEDIUM.width(t + "..", size) > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "..";
    }

    private List<String> wrap(@Nullable String text, float size, float maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (Fonts.MEDIUM.width(test, size) > maxW && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        if (out.size() > 3) {
            List<String> cut = new ArrayList<>(out.subList(0, 3));
            cut.set(2, fit(cut.get(2) + " " + out.get(3), size, maxW));
            return cut;
        }
        return out;
    }

    private static String fmt(float v, float inc) {
        if (inc >= 1.0f && Math.abs(v - (float) Math.round(v)) < 1.0E-4f) {
            return String.valueOf(Math.round(v));
        }
        int dec = inc >= 0.1f || inc <= 0.0f ? 1 : 2;
        return String.format(Locale.ROOT, "%." + dec + "f", v);
    }

    private static float easeOut(float t) {
        float u = 1.0f - clamp01(t);
        return 1.0f - u * u * u;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private static int col(int rgb, float alpha) {
        int al = Math.round(clamp01(alpha) * 255.0f);
        return al << 24 | rgb & 0xFFFFFF;
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = a >>> 24;
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return ra << 24 | rr << 16 | rg << 8 | rb;
    }
}
