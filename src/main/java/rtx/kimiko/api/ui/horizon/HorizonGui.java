package rtx.kimiko.api.ui.horizon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Frost: чистое клик-меню в одном окне (шапка + категории + модули + настройки).
 *
 * Никаких текстур и фоновых эффектов: фон - мягкое затемнение с лёгким градиентом цвета темы.
 * Всё рисуется примитивами Render2D, круги идут через бюджет (fxCircle/fxRing),
 * поэтому CircleBatch (512 кругов на кадр) никогда не переполняется.
 *
 * Анимации: выезд окна снизу + fade, скользящая подсветка категории, каскад карточек
 * при смене категории/поиска, плавный вкл/выкл, вспышка при переключении,
 * плавный переход инспектора, плавные ползунки/цвет.
 *
 * Управление: ЛКМ по карточке - вкл/выкл, ПКМ - настройки справа, СКМ - назначить бинд
 * (Del/Backspace - сброс, Esc - отмена). Поиск: просто начни печатать или Ctrl+F.
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
    private static final int K_CLOSE = 14;

    /** Лимит кругов за кадр (у батча 512 на весь GUI-кадр, оставляем запас HUD'у). */
    private static final int CIRCLE_BUDGET = 300;

    // палитра
    private static final int C_TEXT = 0xECEFF6;
    private static final int C_SUB = 0xB4BACB;
    private static final int C_MUTED = 0x868DA0;
    private static final int C_DIM = 0x5C6376;
    private static final int C_DARK = 0x0A0C12;

    private static final float HEADER_H = 34.0f;
    private static final float RADIUS = 12.0f;

    private record Hit(float x, float y, float w, float h, int kind, Object ref, int index) {
        boolean contains(float px, float py) {
            return px >= this.x && px <= this.x + this.w && py >= this.y && py <= this.y + this.h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private final Map<Object, Float> smoothVals = new HashMap<>();
    private final Map<Object, Float> pulses = new HashMap<>();
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
    private int circles;

    private float catAnim = 1.0f;
    private float inspAnim = 1.0f;
    @Nullable private Object lastCatKey;
    private String lastSearch = "";
    @Nullable private Module lastSelected;
    private float catSelY = -1.0f;

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
    private int accent = 0xFF6FB7FF;
    private int accent2 = 0xFFA58BFF;

    private HorizonGui() {
        super((Text) Text.literal("Frostix"));
    }

    public static boolean isOpen() {
        return MinecraftClient.getInstance().currentScreen == INSTANCE;
    }

    /** Пока ждём клавишу для бинда, миксин не должен закрывать меню по клавише открытия. */
    public static boolean isBinding() {
        return INSTANCE.bindingModule != null && isOpen();
    }

    /** Меню сейчас само принимает ввод (бинд или поиск) - клавиша открытия не должна его закрывать. */
    public static boolean isCapturingKeys() {
        return isOpen() && (INSTANCE.bindingModule != null || INSTANCE.searchFocused);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        super.init();
        if (this.freshOpen) {
            this.openAnim = 0.0f;
            this.catAnim = 0.0f;
            this.inspAnim = 0.0f;
            this.freshOpen = false;
        }
        this.closing = false;
        this.lastNanos = 0L;
        this.catSelY = -1.0f;
    }

    @Override
    public void removed() {
        super.removed();
        this.freshOpen = true;
        this.closing = false;
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
        this.openAnim = this.approach(this.openAnim, this.closing ? 0.0f : 1.0f, this.closing ? 12.0f : 7.0f);
        if (this.closing && this.openAnim < 0.03f) {
            this.finishClose();
            return;
        }
        float a = easeOut(this.openAnim);
        this.circles = 0;
        this.refreshColors();
        this.hits.clear();
        this.ensureCategory();
        this.trackTransitions();
        this.catAnim = this.approach(this.catAnim, 1.0f, 6.0f);
        this.inspAnim = this.approach(this.inspAnim, 1.0f, 9.0f);
        this.decayPulses();

        this.drawBackdrop(a);

        float W = Math.min((float) this.width - 24.0f, 700.0f);
        float H = Math.min((float) this.height - 24.0f, 400.0f);
        float X = ((float) this.width - W) * 0.5f;
        float Y = ((float) this.height - H) * 0.5f + (1.0f - a) * 16.0f;
        float sw = 118.0f;
        float iw = W < 600.0f ? 172.0f : 206.0f;
        float mw = W - sw - iw;

        this.drawWindow(X, Y, W, H, sw, iw, a);

        float pH = this.stagger(0);
        float pS = this.stagger(1);
        float pM = this.stagger(2);
        float pI = this.stagger(3);
        this.renderHeader(X, Y, W, sw, mw, iw, a * pH);
        float bodyY = Y + HEADER_H;
        float bodyH = H - HEADER_H;
        this.renderSidebar(X, bodyY, sw, bodyH, a * pS);
        this.renderModules(X + sw, bodyY, mw, bodyH, a * pM);
        this.renderInspector(X + sw + mw, bodyY, iw, bodyH, a * pI);

        if (this.dragKind != 0) {
            this.applyDrag((float) mouseX);
        }
    }

    private float stagger(int i) {
        return easeOut(clamp01(this.openAnim * 1.4f - (float) i * 0.1f));
    }

    private void trackTransitions() {
        Object catKey = this.search.isEmpty() ? this.category : "search";
        if (catKey != this.lastCatKey) {
            this.catAnim = 0.0f;
        } else if (!this.search.equals(this.lastSearch)) {
            this.catAnim = Math.min(this.catAnim, 0.45f);
        }
        this.lastCatKey = catKey;
        this.lastSearch = this.search;
        if (this.selected != this.lastSelected) {
            this.inspAnim = 0.0f;
            this.lastSelected = this.selected;
        }
    }

    /** Фон: затемнение + мягкий градиент цвета темы сверху и тёмная виньетка снизу. Без текстур. */
    private void drawBackdrop(float a) {
        float w = (float) this.width;
        float h = (float) this.height;
        Render2D.rect(0.0f, 0.0f, w, h, col(0x05060A, 0.58f * a));
        Render2D.rect(0.0f, 0.0f, w, h * 0.55f, 0.0f,
                col(this.accent, 0.10f * a), col(this.accent2, 0.10f * a), col(this.accent2, 0.0f), col(this.accent, 0.0f));
        Render2D.rect(0.0f, h * 0.55f, w, h * 0.45f, 0.0f,
                col(0x000000, 0.0f), col(0x000000, 0.0f), col(0x000000, 0.35f * a), col(0x000000, 0.35f * a));
    }

    private void drawWindow(float x, float y, float w, float h, float sw, float iw, float a) {
        if (a <= 0.01f) {
            return;
        }
        // тень
        Render2D.rect(x - 4.0f, y + 2.0f, w + 8.0f, h + 8.0f, RADIUS + 4.0f, col(0x000000, 0.22f * a));
        if (a > 0.2f) {
            Render2D.blur(x, y, w, h, RADIUS);
        }
        Render2D.rect(x, y, w, h, RADIUS, col(0x12151E, 0.93f * a), col(0x12151E, 0.93f * a), col(0x0C0E15, 0.95f * a), col(0x0C0E15, 0.95f * a));
        // шапка и боковые колонки чуть темнее
        int tint = col(0x000000, 0.16f * a);
        Render2D.rect(x, y, w, HEADER_H, RADIUS, RADIUS, 0.0f, 0.0f, tint, tint, tint, tint);
        Render2D.rect(x, y + HEADER_H, sw, h - HEADER_H, 0.0f, 0.0f, 0.0f, RADIUS, tint, tint, tint, tint);
        int tint2 = col(0x000000, 0.10f * a);
        Render2D.rect(x + w - iw, y + HEADER_H, iw, h - HEADER_H, 0.0f, 0.0f, RADIUS, 0.0f, tint2, tint2, tint2, tint2);
        // разделители
        int div = col(0xFFFFFF, 0.06f * a);
        Render2D.line(x, y + HEADER_H, x + w, y + HEADER_H, 0.6f, div);
        Render2D.line(x + sw, y + HEADER_H, x + sw, y + h, 0.6f, div);
        Render2D.line(x + w - iw, y + HEADER_H, x + w - iw, y + h, 0.6f, div);
        Render2D.outline(x, y, w, h, RADIUS, 0.8f, col(0xFFFFFF, 0.09f * a));
        // тонкая акцентная линия по верхней кромке, плавно гаснет к краям
        float lx = x + RADIUS * 2.0f;
        float lw = (w - RADIUS * 4.0f) * 0.5f;
        Render2D.rect(lx, y, lw, 1.0f, 0.0f, col(this.accent, 0.0f), col(this.accent, 0.9f * a), col(this.accent, 0.9f * a), col(this.accent, 0.0f));
        Render2D.rect(lx + lw, y, lw, 1.0f, 0.0f, col(this.accent2, 0.9f * a), col(this.accent2, 0.0f), col(this.accent2, 0.0f), col(this.accent2, 0.9f * a));
    }

    private void renderHeader(float x, float y, float w, float sw, float mw, float iw, float a) {
        if (a <= 0.01f) {
            return;
        }
        // логотип
        float lx = x + 12.0f;
        float ly = y + 10.0f;
        Render2D.rect(lx, ly, 14.0f, 14.0f, 4.5f, col(this.accent, a), col(this.accent2, a), col(this.accent2, a), col(this.accent, a));
        float fw = Fonts.MEDIUM.width("F", 8.0f);
        Fonts.MEDIUM.draw("F", lx + (14.0f - fw) * 0.5f, ly + 3.2f, 8.0f, col(C_DARK, a));
        Render2D.text(Fonts.MEDIUM, "Frostix", lx + 20.0f, y + 9.5f, 8.5f,
                col(0xFFFFFF, a), col(this.accent, a), col(this.accent, a), col(0xFFFFFF, a));
        Fonts.MEDIUM.draw("client menu", lx + 20.0f, y + 20.5f, 5.0f, col(C_MUTED, 0.8f * a));

        // поиск
        float sx = x + sw + 10.0f;
        float sy = y + 8.0f;
        float sW = mw - 20.0f;
        float sH = 18.0f;
        float fa = this.anim("search_focus", this.searchFocused, 12.0f);
        float sh = this.anim("search_hov", this.inside(sx, sy, sW, sH), 12.0f);
        Render2D.rect(sx, sy, sW, sH, 6.0f, col(0xFFFFFF, (0.04f + 0.02f * sh + 0.02f * fa) * a));
        Render2D.outline(sx, sy, sW, sH, 6.0f, 0.7f, lerpColor(col(0xFFFFFF, (0.07f + 0.04f * sh) * a), col(this.accent, 0.8f * a), fa));
        float lensX = sx + 10.0f;
        int lensC = lerpColor(col(C_MUTED, 0.9f * a), col(this.accent, a), fa);
        this.fxRing(lensX, sy + 8.3f, 3.0f, 0.9f, lensC);
        Render2D.line(lensX + 2.2f, sy + 10.5f, lensX + 4.3f, sy + 12.6f, 1.0f, lensC);
        boolean placeholder = this.search.isEmpty() && !this.searchFocused;
        float phA = this.anim("search_ph", placeholder, 10.0f);
        float textW = sW - 28.0f;
        if (phA > 0.01f) {
            Fonts.MEDIUM.draw(fit("Поиск модулей...", 6.0f, textW), sx + 19.0f + (1.0f - phA) * 5.0f, sy + 6.0f, 6.0f, col(C_MUTED, 0.85f * phA * a));
        }
        if (!placeholder) {
            String shown = tail(this.search, 6.0f, textW);
            Fonts.MEDIUM.draw(shown, sx + 19.0f, sy + 6.0f, 6.0f, col(C_TEXT, a * (1.0f - phA)));
            if (this.searchFocused) {
                float blink = 0.5f + 0.5f * (float) Math.cos(this.time * 6.0f);
                float cx = sx + 19.0f + Fonts.MEDIUM.width(shown, 6.0f) + 1.0f;
                Render2D.rect(cx, sy + 4.5f, 0.8f, 9.0f, col(this.accent, blink * a));
            }
        }
        String hint = "Ctrl+F";
        float hw = Fonts.MEDIUM.width(hint, 4.8f) + 6.0f;
        if (sW > 140.0f && phA > 0.01f) {
            Render2D.rect(sx + sW - hw - 5.0f, sy + 4.5f, hw, 9.0f, 3.0f, col(0xFFFFFF, 0.06f * phA * a));
            Fonts.MEDIUM.draw(hint, sx + sW - hw - 2.0f, sy + 6.6f, 4.8f, col(C_MUTED, 0.9f * phA * a));
        }
        this.hits.add(new Hit(sx, sy, sW, sH, K_SEARCH, null, 0));

        // справа: активные модули + закрыть
        ModuleManager mm = ModuleManager.Companion.get();
        float rx = x + w - iw + 12.0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(this.time * 3.0f);
        this.fxCircle(rx + 3.0f, y + 17.0f, 2.4f + pulse * 1.6f, col(this.accent, 0.18f * (1.0f - pulse * 0.5f) * a));
        this.fxCircle(rx + 3.0f, y + 17.0f, 1.8f, col(this.accent, a));
        Fonts.MEDIUM.draw("Активно: " + mm.getEnabled().size(), rx + 10.0f, y + 14.0f, 6.0f, col(C_SUB, 0.9f * a));

        float cs = 16.0f;
        float cx = x + w - 10.0f - cs;
        float cy = y + (HEADER_H - cs) * 0.5f;
        float ch = this.anim("close_hov", this.inside(cx, cy, cs, cs), 14.0f);
        Render2D.rect(cx, cy, cs, cs, 5.0f, col(0xFF5A6A, 0.85f * ch * a));
        int xc = lerpColor(col(C_MUTED, a), col(0xFFFFFF, a), ch);
        float m = 5.2f;
        Render2D.line(cx + m, cy + m, cx + cs - m, cy + cs - m, 1.0f, xc);
        Render2D.line(cx + cs - m, cy + m, cx + m, cy + cs - m, 1.0f, xc);
        this.hits.add(new Hit(cx, cy, cs, cs, K_CLOSE, null, 0));
    }

    private void renderSidebar(float x, float y, float w, float h, float a) {
        if (a <= 0.01f) {
            return;
        }
        Fonts.MEDIUM.draw("КАТЕГОРИИ", x + 12.0f, y + 10.0f, 4.8f, col(C_DIM, a));

        ModuleManager mm = ModuleManager.Companion.get();
        List<Category> cats = this.categories();
        float top = y + 20.0f;
        float rowH = 21.0f;
        float step = 23.0f;
        int selIdx = this.search.isEmpty() && this.category != null ? cats.indexOf(this.category) : -1;
        float selTarget = top + (float) Math.max(0, selIdx) * step;
        if (this.catSelY < 0.0f) {
            this.catSelY = selTarget;
        }
        this.catSelY = this.approach(this.catSelY, selTarget, 16.0f);
        float selVis = this.anim("cat_sel_vis", selIdx >= 0, 10.0f);
        if (selVis > 0.01f) {
            float hy = this.catSelY;
            Render2D.rect(x + 7.0f, hy, w - 14.0f, rowH, 6.0f,
                    col(this.accent, 0.16f * selVis * a), col(this.accent, 0.04f * selVis * a),
                    col(this.accent, 0.04f * selVis * a), col(this.accent, 0.16f * selVis * a));
            float moving = clamp01(Math.abs(selTarget - hy) / step);
            float bar = 10.0f * (1.0f + moving * 0.5f);
            Render2D.rect(x + 7.0f, hy + rowH * 0.5f - bar * 0.5f, 2.2f, bar, 1.1f, col(this.accent, selVis * a));
        }

        float cy = top;
        for (Category c : cats) {
            boolean sel = c == this.category && this.search.isEmpty();
            boolean hov = this.inside(x + 7.0f, cy, w - 14.0f, rowH);
            float sa = this.anim("cat_sel_" + c.name(), sel, 10.0f);
            float ha = this.anim("cat_hov_" + c.name(), hov, 12.0f);
            if (ha > 0.01f && sa < 0.99f) {
                Render2D.rect(x + 7.0f, cy, w - 14.0f, rowH, 6.0f, col(0xFFFFFF, 0.045f * ha * (1.0f - sa) * a));
            }
            float shift = ha * 1.2f + sa * 1.5f;
            float ix = x + 19.0f + shift;
            float iy = cy + rowH * 0.5f;
            Fonts.KIMIKO.msdf(icon(c), ix - 3.5f, iy - 3.5f, 7.0f,
                    lerpColor(col(C_MUTED, a), col(this.accent, a), sa));
            Fonts.MEDIUM.draw(fit(c.getDisplayName(), 6.6f, w - 60.0f), x + 30.0f + shift, cy + 7.2f, 6.6f,
                    lerpColor(col(C_SUB, 0.85f * a), col(0xFFFFFF, a), Math.max(sa, ha * 0.6f)));
            List<Module> list = mm.getByCategory(c);
            int on = 0;
            for (Module m : list) {
                if (m.isEnabled()) {
                    ++on;
                }
            }
            String cnt = String.valueOf(on);
            float cw = Math.max(10.0f, Fonts.MEDIUM.width(cnt, 4.8f) + 6.0f);
            float bx = x + w - 12.0f - cw;
            if (on > 0) {
                Render2D.rect(bx, cy + 6.0f, cw, 9.0f, 4.5f, col(this.accent, (0.18f + 0.12f * sa) * a));
                Fonts.MEDIUM.draw(cnt, bx + (cw - Fonts.MEDIUM.width(cnt, 4.8f)) * 0.5f, cy + 8.2f, 4.8f, col(this.accent, a));
            } else {
                Fonts.MEDIUM.draw(cnt, bx + (cw - Fonts.MEDIUM.width(cnt, 4.8f)) * 0.5f, cy + 8.2f, 4.8f, col(C_DIM, 0.8f * a));
            }
            this.hits.add(new Hit(x + 7.0f, cy, w - 14.0f, rowH, K_CAT, c, 0));
            cy += step;
        }

        // внизу: переход на классическое меню
        float by = y + h - 26.0f;
        boolean ch = this.inside(x + 8.0f, by, w - 16.0f, 16.0f);
        float cha = this.anim("classic_hov", ch, 12.0f);
        Render2D.rect(x + 8.0f, by, w - 16.0f, 16.0f, 5.0f, col(0xFFFFFF, (0.035f + 0.045f * cha) * a));
        Render2D.outline(x + 8.0f, by, w - 16.0f, 16.0f, 5.0f, 0.6f,
                lerpColor(col(0xFFFFFF, 0.07f * a), col(this.accent, 0.55f * a), cha));
        String cl = "Классический вид";
        float clw = Fonts.MEDIUM.width(cl, 5.4f);
        Fonts.MEDIUM.draw(cl, x + (w - clw) * 0.5f, by + 5.2f, 5.4f, col(C_SUB, (0.7f + 0.3f * cha) * a));
        this.hits.add(new Hit(x + 8.0f, by, w - 16.0f, 16.0f, K_CLASSIC, null, 0));
    }

    private void renderModules(float x, float y, float w, float h, float a) {
        if (a <= 0.01f) {
            return;
        }
        List<Module> mods = this.visibleModules();
        float tA = easeOut(clamp01(this.catAnim * 2.0f));
        String title = this.search.isEmpty() && this.category != null ? this.category.getDisplayName() : "Результаты поиска";
        float tX = x + 12.0f + (1.0f - tA) * 6.0f;
        Fonts.MEDIUM.draw(title, tX, y + 10.0f, 8.5f, col(0xFFFFFF, a * tA));
        float tw = Fonts.MEDIUM.width(title, 8.5f);
        String cnt = mods.size() + " " + plural(mods.size());
        Fonts.MEDIUM.draw(cnt, tX + tw + 6.0f, y + 12.4f, 5.6f, col(C_MUTED, 0.9f * a * tA));

        this.gridX = x + 10.0f;
        this.gridY = y + 28.0f;
        this.gridW = w - 20.0f;
        this.gridH = h - 36.0f;
        int cols = this.gridW > 290.0f ? 2 : 1;
        float gap = 6.0f;
        float cardH = 38.0f;
        float cardW = (this.gridW - gap * (float) (cols - 1)) / (float) cols;
        int rows = (mods.size() + cols - 1) / cols;
        float content = Math.max(0.0f, (float) rows * (cardH + gap) - gap);
        this.moduleScrollMax = Math.max(0.0f, content - this.gridH);
        this.moduleScrollTarget = clamp(this.moduleScrollTarget, 0.0f, this.moduleScrollMax);
        this.moduleScroll = this.approach(this.moduleScroll, this.moduleScrollTarget, 16.0f);

        Render2D.pushScissor(this.ctx, this.gridX - 2.0f, this.gridY, this.gridW + 4.0f, this.gridH);
        int firstVisible = -1;
        for (int i = 0; i < mods.size(); ++i) {
            int c = i % cols;
            int r = i / cols;
            float cx = this.gridX + (float) c * (cardW + gap);
            float cy = this.gridY + (float) r * (cardH + gap) - this.moduleScroll;
            if (cy + cardH < this.gridY || cy > this.gridY + this.gridH) {
                continue;
            }
            if (firstVisible < 0) {
                firstVisible = i;
            }
            int order = Math.min(i - firstVisible, 16);
            float ca = easeOut(clamp01(this.catAnim * 1.8f - (float) order * 0.06f));
            if (ca <= 0.01f) {
                continue;
            }
            this.renderCard(mods.get(i), cx, cy + (1.0f - ca) * 10.0f, cardW, cardH, a * ca);
        }
        Render2D.popScissor(this.ctx);

        if (this.moduleScrollMax > 0.5f) {
            boolean overGrid = this.inside(this.gridX, this.gridY, this.gridW + 10.0f, this.gridH);
            float sbA = this.anim("mod_sb", overGrid || Math.abs(this.moduleScroll - this.moduleScrollTarget) > 0.5f, 8.0f);
            float thumb = Math.max(18.0f, this.gridH * this.gridH / content);
            float ty = this.gridY + (this.gridH - thumb) * (this.moduleScroll / this.moduleScrollMax);
            Render2D.rect(x + w - 5.0f, ty, 2.0f, thumb, 1.0f, col(0xFFFFFF, (0.10f + 0.20f * sbA) * a));
        }
        if (mods.isEmpty()) {
            String e1 = "Ничего не найдено";
            String e2 = this.search.isEmpty() ? "в этой категории пусто" : "попробуй другой запрос";
            float my = this.gridY + this.gridH * 0.5f;
            Fonts.MEDIUM.draw(e1, x + (w - Fonts.MEDIUM.width(e1, 7.5f)) * 0.5f, my - 8.0f, 7.5f, col(C_TEXT, 0.85f * a * tA));
            Fonts.MEDIUM.draw(e2, x + (w - Fonts.MEDIUM.width(e2, 5.5f)) * 0.5f, my + 3.0f, 5.5f, col(C_MUTED, 0.9f * a * tA));
        }
    }

    private void renderCard(Module m, float x, float y, float w, float h, float a) {
        boolean on = m.isEnabled();
        boolean inGrid = (float) this.mouseYi >= this.gridY && (float) this.mouseYi <= this.gridY + this.gridH;
        boolean hov = inGrid && this.inside(x, y, w, h);
        String key = m.getName();
        float ta = this.anim(key + "#t", on, 10.0f);
        float ha = this.anim(key + "#h", hov, 14.0f);
        float sa = this.anim(key + "#s", m == this.selected, 12.0f);
        float tp = this.pulse("tg#" + key);
        boolean binding = m == this.bindingModule;

        Render2D.rect(x, y, w, h, 7.0f, col(0xFFFFFF, (0.03f + 0.03f * ha) * a));
        if (ta > 0.01f) {
            Render2D.rect(x, y, w, h, 7.0f, col(this.accent, 0.13f * ta * a), col(this.accent, 0.02f * ta * a),
                    col(this.accent, 0.02f * ta * a), col(this.accent, 0.13f * ta * a));
        }
        if (tp > 0.01f) {
            Render2D.rect(x, y, w, h, 7.0f, col(on ? this.accent : 0xFFFFFF, 0.18f * tp * a));
        }
        int outline = lerpColor(col(0xFFFFFF, (0.05f + 0.05f * ha) * a), col(this.accent, 0.40f * a), ta);
        outline = lerpColor(outline, col(this.accent2, 0.85f * a), sa);
        Render2D.outline(x, y, w, h, 7.0f, 0.7f, outline);

        // акцентная полоска слева растёт при включении
        float barH = (h - 16.0f) * ta;
        if (barH > 0.5f) {
            Render2D.rect(x + 5.0f, y + (h - barH) * 0.5f, 2.2f, barH, 1.1f, col(this.accent, a));
        }

        float tx = x + 13.0f + ha * 0.8f;
        float textW = w - 13.0f - 34.0f;
        Fonts.MEDIUM.draw(fit(m.getDisplayName(), 7.0f, textW), tx, y + 8.5f, 7.0f,
                lerpColor(col(C_SUB, a), col(0xFFFFFF, a), Math.max(ta, ha)));
        if (binding) {
            float p = 0.6f + 0.4f * (float) Math.sin(this.time * 6.0f);
            Fonts.MEDIUM.draw(fit("Нажми клавишу (Del - сброс)", 5.2f, textW + 22.0f), tx, y + 21.5f, 5.2f, col(this.accent, p * a));
        } else {
            Fonts.MEDIUM.draw(fit(m.getDisplayDescription(), 5.2f, textW), tx, y + 21.5f, 5.2f, col(C_MUTED, (0.8f + 0.2f * ha) * a));
        }

        this.drawSwitch(x + w - 26.0f, y + 8.0f, 17.0f, 9.0f, ta, a, tp);
        KeyBind bind = m.getBind();
        if (bind != null && bind.isBound()) {
            String bk = bind.getDisplayName();
            float bw = Fonts.MEDIUM.width(bk, 4.6f) + 6.0f;
            float bx = x + w - 8.0f - bw;
            Render2D.rect(bx, y + h - 13.0f, bw, 8.0f, 3.0f, col(0xFFFFFF, (0.06f + 0.04f * ha) * a));
            Fonts.MEDIUM.draw(bk, bx + 3.0f, y + h - 11.2f, 4.6f, col(C_SUB, 0.9f * a));
        }

        float hy = Math.max(y, this.gridY);
        float hb = Math.min(y + h, this.gridY + this.gridH);
        if (hb > hy) {
            this.hits.add(new Hit(x, hy, w, hb - hy, K_MODULE, m, 0));
        }
    }

    private void renderInspector(float x0, float y, float w, float h, float a0) {
        if (a0 <= 0.01f) {
            return;
        }
        float ia = easeOut(this.inspAnim);
        float a = a0 * ia;
        float x = x0 + (1.0f - ia) * 8.0f;
        Module m = this.selected;
        if (m == null) {
            float cx = x0 + w * 0.5f;
            float cy = y + h * 0.5f - 20.0f;
            float bob = (float) Math.sin(this.time * 1.6f) * 1.5f;
            Render2D.rect(cx - 14.0f, cy - 14.0f + bob, 28.0f, 28.0f, 8.0f, col(0xFFFFFF, 0.05f * a));
            Render2D.outline(cx - 14.0f, cy - 14.0f + bob, 28.0f, 28.0f, 8.0f, 0.7f, col(this.accent, 0.45f * a));
            // три точки "настроек"
            for (int i = 0; i < 3; ++i) {
                float ph = 0.5f + 0.5f * (float) Math.sin(this.time * 3.0f - (float) i * 0.7f);
                this.fxCircle(cx - 6.0f + (float) i * 6.0f, cy + bob, 1.5f + ph * 0.5f, col(this.accent, (0.45f + 0.55f * ph) * a));
            }
            String t1 = "Выбери модуль";
            String t2 = "ПКМ по карточке - настройки";
            String t3 = "СКМ - назначить бинд";
            Fonts.MEDIUM.draw(t1, x0 + (w - Fonts.MEDIUM.width(t1, 7.0f)) * 0.5f, cy + 22.0f, 7.0f, col(C_TEXT, 0.9f * a));
            Fonts.MEDIUM.draw(t2, x0 + (w - Fonts.MEDIUM.width(t2, 5.2f)) * 0.5f, cy + 34.0f, 5.2f, col(C_MUTED, 0.9f * a));
            Fonts.MEDIUM.draw(t3, x0 + (w - Fonts.MEDIUM.width(t3, 5.2f)) * 0.5f, cy + 42.0f, 5.2f, col(C_MUTED, 0.9f * a));
            return;
        }

        float cx = x + 12.0f;
        float cy = y + 12.0f;
        boolean on = m.isEnabled();
        float onA = this.anim("insp_on#" + m.getName(), on, 10.0f);
        float tp = this.pulse("tg#" + m.getName());
        String st = on ? "ВКЛ" : "ВЫКЛ";
        float stw = Fonts.MEDIUM.width(st, 5.0f) + 10.0f;
        float stx = x + w - 12.0f - stw;
        boolean stHov = this.inside(stx, cy, stw, 11.0f);
        float stHa = this.anim("insp_st_hov", stHov, 12.0f);
        Fonts.MEDIUM.draw(fit(m.getDisplayName(), 8.5f, w - 30.0f - stw), cx, cy + 1.0f, 8.5f, col(0xFFFFFF, a));
        if (tp > 0.01f) {
            Render2D.rect(stx - 2.0f * tp, cy - 2.0f * tp, stw + 4.0f * tp, 11.0f + 4.0f * tp, 5.5f + 2.0f * tp, col(this.accent, 0.3f * tp * a));
        }
        Render2D.rect(stx, cy, stw, 11.0f, 5.5f, lerpColor(col(0xFFFFFF, (0.07f + 0.05f * stHa) * a), col(this.accent, (0.85f + 0.15f * stHa) * a), onA));
        Fonts.MEDIUM.draw(st, stx + 5.0f, cy + 3.0f, 5.0f, lerpColor(col(C_SUB, a), col(C_DARK, a), onA));
        this.hits.add(new Hit(stx, cy, stw, 11.0f, K_TOGGLE, m, 0));

        float ly = cy + 16.0f;
        List<String> lines = this.wrap(m.getDisplayDescription(), 5.3f, w - 24.0f);
        for (int i = 0; i < Math.min(3, lines.size()); ++i) {
            float la = easeOut(clamp01(this.inspAnim * 2.0f - (float) i * 0.2f));
            Fonts.MEDIUM.draw(lines.get(i), cx + (1.0f - la) * 4.0f, ly, 5.3f, col(C_MUTED, 0.95f * a0 * la));
            ly += 8.0f;
        }

        float by = ly + 4.0f;
        Fonts.MEDIUM.draw("Бинд", cx, by + 3.6f, 6.0f, col(C_SUB, 0.85f * a));
        boolean binding = this.bindingModule == m;
        KeyBind bind = m.getBind();
        String bk = binding ? "..." : (bind != null && bind.isBound() ? bind.getDisplayName() : "нет");
        float bwT = Math.max(28.0f, Fonts.MEDIUM.width(bk, 5.5f) + 14.0f);
        float bw = this.smooth("insp_bind_w", bwT, 14.0f);
        float bx = x + w - 12.0f - bw;
        boolean bHov = this.inside(bx, by, bw, 13.0f);
        float bHa = this.anim("insp_bind_hov", bHov, 12.0f);
        float bp = binding ? 0.5f + 0.5f * (float) Math.sin(this.time * 6.0f) : 0.0f;
        Render2D.rect(bx, by, bw, 13.0f, 5.0f, lerpColor(col(0xFFFFFF, (0.05f + 0.05f * bHa) * a), col(this.accent, 0.45f * a), bp));
        Render2D.outline(bx, by, bw, 13.0f, 5.0f, 0.6f, lerpColor(col(0xFFFFFF, 0.09f * a), col(this.accent, 0.65f * a), Math.max(bHa, bp)));
        Fonts.MEDIUM.draw(bk, bx + (bw - Fonts.MEDIUM.width(bk, 5.5f)) * 0.5f, by + 3.7f, 5.5f, col(C_TEXT, a));
        this.hits.add(new Hit(bx, by, bw, 13.0f, K_BIND, m, 0));

        float dy = by + 19.0f;
        Render2D.line(x0 + 10.0f, dy, x0 + 10.0f + (w - 20.0f) * ia, dy, 0.6f, col(0xFFFFFF, 0.06f * a0));

        this.inspX = x0;
        this.inspY = dy + 5.0f;
        this.inspW = w;
        this.inspH = y + h - 8.0f - this.inspY;
        this.settingsScrollTarget = clamp(this.settingsScrollTarget, 0.0f, this.settingsScrollMax);
        this.settingsScroll = this.approach(this.settingsScroll, this.settingsScrollTarget, 16.0f);

        Render2D.pushScissor(this.ctx, x0 + 4.0f, this.inspY, w - 8.0f, this.inspH);
        float ry = this.inspY + 2.0f - this.settingsScroll;
        int shown = 0;
        for (Setting s : m.getSettings().all()) {
            if (s instanceof PositionSettings || !s.isVisible()) {
                continue;
            }
            float ra = easeOut(clamp01(this.inspAnim * 1.8f - (float) Math.min(shown, 12) * 0.06f));
            float appear = this.anim("vis#" + System.identityHashCode(s), true, 10.0f);
            float rowA = a0 * ra * appear;
            float rh = this.renderSetting(s, x0 + 11.0f + (1.0f - ra) * 6.0f, ry, w - 22.0f, rowA);
            ry += rh + 4.0f;
            ++shown;
        }
        Render2D.popScissor(this.ctx);
        float contentH = ry + this.settingsScroll - this.inspY;
        this.settingsScrollMax = Math.max(0.0f, contentH - this.inspH);
        if (this.settingsScrollMax > 0.5f) {
            boolean over = this.inside(this.inspX, this.inspY, this.inspW, this.inspH);
            float sbA = this.anim("insp_sb", over || Math.abs(this.settingsScroll - this.settingsScrollTarget) > 0.5f, 8.0f);
            float thumb = Math.max(16.0f, this.inspH * this.inspH / contentH);
            float ty = this.inspY + (this.inspH - thumb) * (this.settingsScroll / this.settingsScrollMax);
            Render2D.rect(x0 + w - 5.0f, ty, 2.0f, thumb, 1.0f, col(0xFFFFFF, (0.10f + 0.20f * sbA) * a0));
        }
        if (shown == 0) {
            String t = "Нет настроек";
            Fonts.MEDIUM.draw(t, x0 + (w - Fonts.MEDIUM.width(t, 6.0f)) * 0.5f, this.inspY + 16.0f, 6.0f, col(C_MUTED, 0.9f * a));
        }
    }

    private float renderSetting(Setting s, float x, float y, float w, float a) {
        String name = s.getDisplayName();
        int id = System.identityHashCode(s);
        if (s instanceof SeparatorSetting) {
            String t = name.toUpperCase(Locale.ROOT);
            Fonts.MEDIUM.draw(t, x, y + 4.0f, 5.0f, col(this.accent, 0.9f * a));
            float tw = Fonts.MEDIUM.width(t, 5.0f) + 4.0f;
            Render2D.line(x + tw, y + 6.8f, x + tw + (w - tw) * clamp01(a), y + 6.8f, 0.6f, col(0xFFFFFF, 0.07f * a));
            return 13.0f;
        }
        if (s instanceof BooleanSetting b) {
            boolean v = b.getValue();
            float t = this.anim(s, v, 12.0f);
            float p = this.pulse("set#" + id);
            this.rowHover(x, y, w, 16.0f, a, "row#" + id);
            Fonts.MEDIUM.draw(fit(name, 6.0f, w - 26.0f), x + 2.0f, y + 4.8f, 6.0f, lerpColor(col(C_SUB, a), col(C_TEXT, a), t));
            this.drawSwitch(x + w - 17.0f, y + 3.8f, 15.0f, 8.5f, t, a, p);
            this.addHit(x, y, w, 16.0f, K_BOOL, s, 0);
            return 16.0f;
        }
        if (s instanceof RangeSliderSetting r) {
            String val = fmt(r.getMinValue(), r.getIncrement()) + " - " + fmt(r.getMaxValue(), r.getIncrement());
            this.sliderHeader(name, val, x, y, w, a);
            boolean dragging = this.dragRef == s;
            float p0 = this.smooth("r0#" + id, clamp01(r.getMinProgress()), dragging ? 30.0f : 14.0f);
            float p1 = this.smooth("r1#" + id, clamp01(r.getMaxProgress()), dragging ? 30.0f : 14.0f);
            Render2D.rect(x, y + 13.5f, w, 2.5f, 1.25f, col(0xFFFFFF, 0.08f * a));
            Render2D.rect(x + w * p0, y + 13.5f, Math.max(2.5f, w * (p1 - p0)), 2.5f, 1.25f,
                    col(this.accent, a), col(this.accent2, a), col(this.accent2, a), col(this.accent, a));
            float kh = this.anim("kn#" + id, dragging || this.inside(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f), 12.0f);
            this.knob(x + w * p0, y + 14.75f, a, kh);
            this.knob(x + w * p1, y + 14.75f, a, kh);
            this.addHit(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f, K_RANGE, s, 0);
            return 21.0f;
        }
        if (s instanceof SliderSetting sl) {
            this.sliderHeader(name, sl.isInteger() ? String.valueOf(Math.round(sl.getValue())) : fmt(sl.getValue(), sl.getIncrement()), x, y, w, a);
            boolean dragging = this.dragRef == s;
            float p = this.smooth("sl#" + id, clamp01(sl.getProgress()), dragging ? 30.0f : 14.0f);
            Render2D.rect(x, y + 13.5f, w, 2.5f, 1.25f, col(0xFFFFFF, 0.08f * a));
            Render2D.rect(x, y + 13.5f, Math.max(2.5f, w * p), 2.5f, 1.25f,
                    col(this.accent, a), col(this.accent2, a), col(this.accent2, a), col(this.accent, a));
            float kh = this.anim("kn#" + id, dragging || this.inside(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f), 12.0f);
            this.knob(x + w * p, y + 14.75f, a, kh);
            this.addHit(x - 3.0f, y + 8.0f, w + 6.0f, 12.0f, K_SLIDER, s, 0);
            return 21.0f;
        }
        if (s instanceof ColorSetting c) {
            Fonts.MEDIUM.draw(fit(name, 6.0f, w - 20.0f), x + 2.0f, y + 2.0f, 6.0f, col(C_TEXT, 0.9f * a));
            float cp = this.pulse("set#" + id);
            float sw = 12.0f + cp * 2.0f;
            Render2D.rect(x + w - sw, y + 0.5f - cp, sw, 8.0f + cp * 2.0f, 3.0f, col(c.getColorOpaque(), a));
            Render2D.outline(x + w - sw, y + 0.5f - cp, sw, 8.0f + cp * 2.0f, 3.0f, 0.6f, col(0xFFFFFF, 0.25f * a));
            float seg = w / 6.0f;
            int[] stops = new int[]{0xFFFF3B3B, 0xFFFFE53B, 0xFF3BFF6A, 0xFF3BE8FF, 0xFF3B55FF, 0xFFE53BFF, 0xFFFF3B3B};
            for (int i = 0; i < 6; ++i) {
                int c0 = col(stops[i], a);
                int c1 = col(stops[i + 1], a);
                float r0 = i == 0 ? 2.0f : 0.0f;
                float r1 = i == 5 ? 2.0f : 0.0f;
                Render2D.rect(x + seg * (float) i, y + 13.0f, seg + 0.3f, 4.0f, r0, r1, r1, r0, c0, c1, c1, c0);
            }
            boolean dragging = this.dragRef == s;
            float hue = this.smooth("hue#" + id, clamp01(c.getHue()), dragging ? 30.0f : 14.0f);
            float kh = this.anim("kn#" + id, dragging || this.inside(x - 3.0f, y + 9.0f, w + 6.0f, 11.0f), 12.0f);
            this.knob(x + w * hue, y + 15.0f, a, kh);
            this.addHit(x - 3.0f, y + 9.0f, w + 6.0f, 11.0f, K_HUE, s, 0);
            return 21.0f;
        }
        if (s instanceof MultiSelectSetting ms) {
            Fonts.MEDIUM.draw(fit(name, 6.0f, w), x + 2.0f, y + 2.0f, 6.0f, col(C_TEXT, 0.9f * a));
            float cx = x;
            float cy = y + 12.0f;
            List<String> opts = ms.getOptions();
            for (int i = 0; i < opts.size(); ++i) {
                String o = opts.get(i);
                String label = fit(o, 5.3f, w - 10.0f);
                float tw = Fonts.MEDIUM.width(label, 5.3f) + 10.0f;
                if (cx + tw > x + w && cx > x) {
                    cx = x;
                    cy += 14.0f;
                }
                boolean sel = ms.is(o);
                float t = this.anim(s.getName() + "#" + o + "#" + id, sel, 12.0f);
                float hv = this.anim("mh#" + id + "#" + i, this.inside(cx, cy, tw, 11.0f), 14.0f);
                float cp = this.pulse("ms#" + id + "#" + i);
                float grow = cp * 1.2f;
                Render2D.rect(cx - grow, cy - grow, tw + grow * 2.0f, 11.0f + grow * 2.0f, 4.0f + grow,
                        lerpColor(col(0xFFFFFF, (0.05f + 0.05f * hv) * a), col(this.accent, 0.85f * a), t));
                Fonts.MEDIUM.draw(label, cx + 5.0f, cy + 3.0f, 5.3f, lerpColor(col(C_SUB, a), col(C_DARK, a), t));
                this.addHit(cx, cy, tw, 11.0f, K_MULTI, s, i);
                cx += tw + 4.0f;
            }
            return cy + 11.0f - y;
        }
        if (s instanceof SelectSetting sel) {
            this.rowHover(x, y, w, 16.0f, a, "row#" + id);
            String v = sel.getDisplaySelected();
            float vwT = Math.min(w * 0.55f, Fonts.MEDIUM.width(v, 5.5f) + 20.0f);
            float vw = this.smooth("selw#" + id, vwT, 14.0f);
            String vf = fit(v, 5.5f, vw - 20.0f);
            float sp = this.pulse("sel#" + id);
            Float dirF = this.smoothVals.get("seldir#" + id);
            float dir = dirF == null ? 1.0f : dirF;
            Fonts.MEDIUM.draw(fit(name, 6.0f, w - vw - 6.0f), x + 2.0f, y + 4.8f, 6.0f, col(C_TEXT, 0.9f * a));
            float vx = x + w - vw;
            boolean hv = this.inside(vx, y + 2.0f, vw, 12.0f);
            float ha = this.anim("selh#" + id, hv, 12.0f);
            Render2D.rect(vx, y + 2.0f, vw, 12.0f, 4.5f, col(0xFFFFFF, (0.05f + 0.04f * ha) * a));
            Render2D.outline(vx, y + 2.0f, vw, 12.0f, 4.5f, 0.6f, lerpColor(col(0xFFFFFF, 0.08f * a), col(this.accent, 0.7f * a), Math.max(ha * 0.6f, sp)));
            Fonts.MEDIUM.draw("<", vx + 4.0f - ha * 0.8f - (dir < 0 ? sp * 1.5f : 0.0f), y + 5.0f, 5.5f, col(this.accent, 0.85f * a));
            Fonts.MEDIUM.draw(">", vx + vw - 8.0f + ha * 0.8f + (dir > 0 ? sp * 1.5f : 0.0f), y + 5.0f, 5.5f, col(this.accent, 0.85f * a));
            Fonts.MEDIUM.draw(vf, vx + (vw - Fonts.MEDIUM.width(vf, 5.5f)) * 0.5f + sp * 4.0f * dir, y + 5.0f, 5.5f, col(0xFFFFFF, a * (1.0f - sp * 0.7f)));
            this.addHit(x, y, w, 16.0f, K_SELECT, s, 0);
            return 16.0f;
        }
        if (s instanceof ButtonSetting btn) {
            boolean hov = this.inside(x, y, w, 15.0f);
            float ha = this.anim(s, hov, 12.0f);
            float bp = this.pulse("set#" + id);
            Render2D.rect(x, y, w, 15.0f, 5.0f, col(this.accent, (0.16f + 0.14f * ha + 0.35f * bp) * a));
            Render2D.outline(x, y, w, 15.0f, 5.0f, 0.6f, col(this.accent, (0.40f + 0.3f * ha) * a));
            String label = btn.getDisplayLabel();
            if (label == null || label.isEmpty()) {
                label = btn.getLabel();
            }
            String text = label == null || label.isEmpty() ? name : name + ": " + label;
            text = fit(text, 5.7f, w - 10.0f);
            Fonts.MEDIUM.draw(text, x + (w - Fonts.MEDIUM.width(text, 5.7f)) * 0.5f, y + 4.7f, 5.7f, col(0xFFFFFF, a));
            this.addHit(x, y, w, 15.0f, K_BUTTON, s, 0);
            return 15.0f;
        }
        if (s instanceof TextSetting ts) {
            String v = ts.getText() == null ? "" : ts.getText();
            float vw = Math.min(w * 0.5f, Fonts.MEDIUM.width(v, 5.3f));
            Fonts.MEDIUM.draw(fit(name, 6.0f, w - vw - 6.0f), x + 2.0f, y + 4.8f, 6.0f, col(C_TEXT, 0.9f * a));
            String vf = fit(v, 5.3f, w * 0.5f);
            Fonts.MEDIUM.draw(vf, x + w - Fonts.MEDIUM.width(vf, 5.3f), y + 5.2f, 5.3f, col(C_MUTED, a));
            return 16.0f;
        }
        if (s instanceof BindSetting bs) {
            KeyBind kb = bs.getValue();
            String v = kb != null && kb.isBound() ? kb.getDisplayName() : "нет";
            Fonts.MEDIUM.draw(fit(name, 6.0f, w - 40.0f), x + 2.0f, y + 4.8f, 6.0f, col(C_TEXT, 0.9f * a));
            float vw = Fonts.MEDIUM.width(v, 5.3f) + 10.0f;
            Render2D.rect(x + w - vw, y + 2.0f, vw, 12.0f, 4.5f, col(0xFFFFFF, 0.06f * a));
            Fonts.MEDIUM.draw(v, x + w - vw + 5.0f, y + 5.3f, 5.3f, col(C_SUB, a));
            return 16.0f;
        }
        Fonts.MEDIUM.draw(fit(name, 6.0f, w), x + 2.0f, y + 3.5f, 6.0f, col(C_TEXT, 0.8f * a));
        return 14.0f;
    }

    // ------------------------------------------------------------------ widgets

    /** Все круги меню идут через бюджет, чтобы не переполнить CircleBatch (512/кадр). */
    private void fxCircle(float x, float y, float r, int color) {
        if (r <= 0.0f || (color >>> 24) == 0 || this.circles >= CIRCLE_BUDGET) {
            return;
        }
        ++this.circles;
        Render2D.circle(x, y, r, color);
    }

    private void fxRing(float x, float y, float r, float thickness, int color) {
        if (r <= 0.0f || (color >>> 24) == 0 || this.circles >= CIRCLE_BUDGET) {
            return;
        }
        ++this.circles;
        Render2D.circleOutline(x, y, r, thickness, color);
    }

    private void drawSwitch(float x, float y, float w, float h, float t, float a, float pulse) {
        Render2D.rect(x, y, w, h, h * 0.5f, lerpColor(col(0xFFFFFF, 0.10f * a), col(this.accent, 0.9f * a), t));
        if (pulse > 0.01f) {
            Render2D.outline(x - pulse * 1.5f, y - pulse * 1.5f, w + pulse * 3.0f, h + pulse * 3.0f, h * 0.5f + pulse * 1.5f, 0.6f, col(this.accent, 0.6f * pulse * a));
        }
        float kr = h * 0.5f - 1.4f;
        float moving = 4.0f * t * (1.0f - t);
        float kx = x + h * 0.5f + (w - h) * t;
        float ky = y + h * 0.5f;
        this.fxCircle(kx, ky, kr + moving * 0.5f, lerpColor(col(0xD8DCE6, a), col(0xFFFFFF, a), t));
    }

    private void knob(float x, float y, float a, float hover) {
        if (hover > 0.01f) {
            this.fxCircle(x, y, 5.0f + hover * 1.2f, col(this.accent, 0.22f * hover * a));
        }
        this.fxCircle(x, y, 3.2f + hover * 0.4f, col(0xFFFFFF, a));
    }

    private void sliderHeader(String name, String val, float x, float y, float w, float a) {
        float vw = Fonts.MEDIUM.width(val, 5.5f);
        Fonts.MEDIUM.draw(fit(name, 6.0f, w - vw - 6.0f), x + 2.0f, y + 2.0f, 6.0f, col(C_TEXT, 0.9f * a));
        Fonts.MEDIUM.draw(val, x + w - vw, y + 2.5f, 5.5f, col(this.accent, 0.95f * a));
    }

    private void rowHover(float x, float y, float w, float h, float a, Object key) {
        boolean hov = this.inside(x, y, w, h) && (float) this.mouseYi >= this.inspY && (float) this.mouseYi <= this.inspY + this.inspH;
        float ha = this.anim(key, hov, 14.0f);
        if (ha > 0.01f) {
            Render2D.rect(x - 3.0f, y, w + 6.0f, h, 4.0f, col(0xFFFFFF, 0.035f * ha * a));
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
                    this.kick("tg#" + m.getName());
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
            case K_TOGGLE -> {
                Module m = (Module) h.ref();
                m.toggle();
                this.kick("tg#" + m.getName());
            }
            case K_SEARCH -> this.searchFocused = true;
            case K_BIND -> this.bindingModule = (Module) h.ref();
            case K_BOOL -> {
                ((BooleanSetting) h.ref()).toggle();
                this.kick("set#" + System.identityHashCode(h.ref()));
            }
            case K_SLIDER, K_HUE -> {
                this.dragKind = h.kind();
                this.dragRef = h.ref();
                this.dragX = h.x() + 3.0f;
                this.dragW = h.w() - 6.0f;
                this.applyDrag(mx);
                if (h.kind() == K_HUE) {
                    this.kick("set#" + System.identityHashCode(h.ref()));
                }
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
                    int id = System.identityHashCode(s);
                    this.smoothVals.put("seldir#" + id, button == 1 ? -1.0f : 1.0f);
                    this.kick("sel#" + id);
                }
            }
            case K_MULTI -> {
                MultiSelectSetting ms = (MultiSelectSetting) h.ref();
                List<String> opts = ms.getOptions();
                if (h.index() >= 0 && h.index() < opts.size()) {
                    ms.toggle(opts.get(h.index()));
                    this.kick("ms#" + System.identityHashCode(ms) + "#" + h.index());
                }
            }
            case K_BUTTON -> {
                ((ButtonSetting) h.ref()).click();
                this.kick("set#" + System.identityHashCode(h.ref()));
            }
            case K_CLASSIC -> MinecraftClient.getInstance().setScreen(UI.INSTANCE);
            case K_CLOSE -> this.close();
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
            this.kick("tg#" + this.bindingModule.getName());
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
        int p = 0xFF6FB7FF;
        int s = 0xFFA58BFF;
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

    private float smooth(Object key, float target, float speed) {
        Float cur = this.smoothVals.get(key);
        float v = cur == null ? target : this.approach(cur, target, speed);
        this.smoothVals.put(key, v);
        return v;
    }

    private void kick(Object key) {
        this.pulses.put(key, 1.0f);
    }

    private float pulse(Object key) {
        Float v = this.pulses.get(key);
        return v == null ? 0.0f : v;
    }

    private void decayPulses() {
        if (this.pulses.isEmpty()) {
            return;
        }
        final float k = (float) Math.exp(-4.0f * this.dt);
        this.pulses.replaceAll((key, v) -> v * k);
        this.pulses.values().removeIf(v -> v < 0.01f);
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

    /** Хвост строки, который влезает в ширину (для поля поиска, чтобы курсор не уезжал за край). */
    private static String tail(@Nullable String s, float size, float maxW) {
        if (s == null) {
            return "";
        }
        String t = s;
        while (t.length() > 1 && Fonts.MEDIUM.width(t, size) > maxW) {
            t = t.substring(1);
        }
        return t;
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
