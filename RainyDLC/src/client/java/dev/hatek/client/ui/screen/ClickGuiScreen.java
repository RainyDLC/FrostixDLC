package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.feature.config.ConfigManager;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.feature.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ClickGuiScreen extends Screen {
    private static float panelX = Float.NaN;
    private static float panelY = Float.NaN;
    private static Tab tab = Tab.MODULES;
    private static final Map<String, Float> SCROLL = new HashMap<>();

    private static final ModulesPage MODULES_PAGE = new ModulesPage();
    private static final ThemesPage THEMES_PAGE = new ThemesPage();
    private static final ConfigsPage CONFIGS_PAGE = new ConfigsPage();

    private static final String[] SOURCE_ICONS = {"folder", "newfile", "cloud"};

    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim switchAnim = new Anim(1.0f, 12.0f);
    private final Anim selectorAnim = new Anim(0.0f, 17.0f);
    private final Anim navAnim = new Anim(0.0f, 17.0f);
    private final Anim scrollAnim = new Anim(0.0f, 17.0f);
    private final Anim scrollbarAnim = new Anim(0.0f, 9.0f);
    private final Anim sourceAnim = new Anim(0.0f, 13.0f);
    private final Map<Category, Anim> tabHover = new EnumMap<>(Category.class);
    private final Map<Tab, Anim> navHover = new EnumMap<>(Tab.class);
    private final Anim[] sourceHover = {new Anim(0.0f, 18.0f), new Anim(0.0f, 18.0f), new Anim(0.0f, 18.0f)};

    private boolean closing;
    private boolean draggingPanel;
    private float grabX;
    private float grabY;
    private long lastFrame = System.nanoTime();
    private long lastScroll;

    public ClickGuiScreen() {
        super(Component.literal(Style.CLIENT_NAME + Style.CLIENT_SUFFIX));
        for (Category value : Category.values()) {
            this.tabHover.put(value, new Anim(0.0f, 18.0f));
        }
        for (Tab value : Tab.values()) {
            this.navHover.put(value, new Anim(0.0f, 18.0f));
        }
    }

    private Page page() {
        return switch (tab) {
            case MODULES -> MODULES_PAGE;
            case THEMES -> THEMES_PAGE;
            case CONFIGS -> CONFIGS_PAGE;
        };
    }

    private static String scrollKey() {
        return tab == Tab.MODULES ? "modules:" + MODULES_PAGE.category().name() : tab.name();
    }

    @Override
    protected void init() {
        if (Float.isNaN(panelX)) {
            panelX = Render2D.screenWidth() * (Style.DESIGN_X / (float) Style.DESIGN_SCREEN_W);
            panelY = Render2D.screenHeight() * (Style.DESIGN_Y / (float) Style.DESIGN_SCREEN_H);
        }
        clampToScreen();
        ThemeManager.all();
        this.openAnim.snap(0.0f);
        this.openAnim.to(1.0f);
        this.selectorAnim.snap(MODULES_PAGE.category().ordinal());
        this.navAnim.snap(tab.ordinal());
        this.sourceAnim.snap(tab == Tab.CONFIGS ? 1.0f : 0.0f);
        this.scrollAnim.snap(SCROLL.getOrDefault(scrollKey(), 0.0f));
        page().onShow();
        // HUD за экраном рисуем сами каждый кадр (хук в это время пропускает).
        InterfaceHud.setScreenOpen(true);
    }

    @Override
    public void removed() {
        InterfaceHud.setScreenOpen(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        Anim.beginFrame((now - this.lastFrame) / 1_000_000_000.0f);
        this.lastFrame = now;

        this.openAnim.to(this.closing ? 0.0f : 1.0f);
        float open = this.openAnim.get();
        if (this.closing && open < 0.01f) {
            this.minecraft.gui.setScreen(null);
            return;
        }

        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        float eased = this.closing ? open : Anim.easeOut(open);

        // HUD за панелью: рисуем явно каждый кадр, чтобы перетаскивание
        // было видно сразу (игровой хук в это время пропускает).
        InterfaceHud.renderExplicit(gg);

        Render2D.begin(gg);
        Render2D.pushScale(gg, panelX + Style.PANEL_W / 2.0f, panelY + Style.PANEL_H / 2.0f,
                0.96f + 0.04f * eased);
        Render2D.pushTranslate(gg, 0.0f, (1.0f - eased) * 12.0f);
        Render2D.pushAlpha(eased);

        Render2D.round(gg, panelX, panelY, Style.PANEL_W, Style.PANEL_H,
                Style.PANEL_R, Style.PANEL);
        renderTopBar(gg, mx, my);
        renderSourceMenu(gg, mx, my);
        renderContent(gg, mx, my);
        renderNav(gg, mx, my);

        Render2D.popAlpha();
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);
        Render2D.end(gg);
    }

    /** Позиции вкладок категорий в верхней панели (абсолютные X центров и ширины). */
    private static final class TopBarLayout {
        float barX;
        float barW;
        float nameX;
        float sepX;
        final float[] tabX = new float[Category.values().length];
        final float[] tabW = new float[Category.values().length];
    }

    private static float tabWidth(Category value) {
        return Style.TOPBAR_TAB_PAD * 2.0f + value.iconWidth() + Style.TOPBAR_TAB_ICON_GAP
                + Fonts.body().width(value.displayName());
    }

    private static TopBarLayout topBarLayout() {
        TopBarLayout layout = new TopBarLayout();
        float nameW = Fonts.title().width(Style.CLIENT_NAME + Style.CLIENT_SUFFIX);
        Category[] categories = Category.values();
        float tabsW = 0.0f;
        for (Category value : categories) {
            tabsW += tabWidth(value);
        }
        float width = Style.TOPBAR_PAD + nameW + Style.TOPBAR_SEP_GAP * 2.0f + 1.0f
                + tabsW + Style.TOPBAR_PAD - Style.TOPBAR_TAB_PAD;
        float right = panelX + Style.CONTENT_RIGHT;
        layout.barX = right - width;
        layout.barW = width;
        layout.nameX = layout.barX + Style.TOPBAR_PAD;
        layout.sepX = layout.nameX + nameW + Style.TOPBAR_SEP_GAP;
        float cursor = layout.sepX + 1.0f + Style.TOPBAR_SEP_GAP - Style.TOPBAR_TAB_PAD;
        for (int i = 0; i < categories.length; i++) {
            layout.tabX[i] = cursor;
            layout.tabW[i] = tabWidth(categories[i]);
            cursor += layout.tabW[i];
        }
        return layout;
    }

    private static float tabCenter(TopBarLayout layout, float slot) {
        int last = layout.tabX.length - 1;
        int i = Mth.clamp((int) Math.floor(slot), 0, last);
        int j = Math.min(i + 1, last);
        float f = Mth.clamp(slot - i, 0.0f, 1.0f);
        float a = layout.tabX[i] + layout.tabW[i] / 2.0f;
        float b = layout.tabX[j] + layout.tabW[j] / 2.0f;
        return a + (b - a) * f;
    }

    private void renderTopBar(GuiGraphicsExtractor gg, double mx, double my) {
        TopBarLayout layout = topBarLayout();
        float y = panelY + Style.TOPBAR_Y;
        float h = Style.TOPBAR_H;
        Render2D.round(gg, layout.barX, y, layout.barW, h, Style.TOPBAR_R, Style.WHITE_02);

        // Название клиента: "Rainy" белым, "DLC" акцентом.
        float baseline = Fonts.title().centeredBaseline(y, h);
        float after = Fonts.title().draw(gg, Style.CLIENT_NAME, layout.nameX, baseline, Style.WHITE);
        Fonts.title().draw(gg, Style.CLIENT_SUFFIX, after, baseline, Style.accent());

        Render2D.rect(gg, layout.sepX, y + (h - Style.WM_SEP_H) / 2.0f, Style.WM_SEP_W,
                Style.WM_SEP_H, Style.WHITE_08);

        Category current = MODULES_PAGE.category();
        boolean modules = tab == Tab.MODULES;
        Category[] categories = Category.values();
        float textBaseline = Fonts.body().centeredBaseline(y, h);
        for (int i = 0; i < categories.length; i++) {
            Category value = categories[i];
            boolean active = value == current && modules;
            boolean hover = Render2D.hovered(mx, my, layout.tabX[i], y, layout.tabW[i], h);
            Anim anim = this.tabHover.get(value);
            anim.to(hover || active);
            float k = anim.get();
            int tint = active ? Style.accent() : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, k);
            int text = active ? Style.WHITE : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, k);

            float ix = layout.tabX[i] + Style.TOPBAR_TAB_PAD;
            Render2D.icon(gg, value.icon(), ix, y + (h - value.iconHeight()) / 2.0f,
                    value.iconWidth(), value.iconHeight(), tint);
            Fonts.body().draw(gg, value.displayName(),
                    ix + value.iconWidth() + Style.TOPBAR_TAB_ICON_GAP, textBaseline, text);
        }

        // Индикатор активной категории с растяжением при переходе.
        this.selectorAnim.to(current.ordinal());
        float slot = this.selectorAnim.get();
        float travel = Math.abs(slot - current.ordinal());
        float width = Style.TOPBAR_INDICATOR_W * (1.0f + Math.min(travel, 1.0f) * 1.4f);
        Render2D.round(gg, tabCenter(layout, slot) - width / 2.0f, y + Style.TOPBAR_INDICATOR_Y,
                width, Style.TOPBAR_INDICATOR_H, 1.0f,
                Render2D.withAlpha(Style.accent(), modules ? 1.0f : 0.45f));
    }

    private void renderSourceMenu(GuiGraphicsExtractor gg, double mx, double my) {
        this.sourceAnim.to(tab == Tab.CONFIGS);
        float open = Anim.easeInOut(this.sourceAnim.get());
        if (open <= 0.002f) {
            return;
        }
        float x = panelX + Style.CONTENT_X;
        float y = panelY + Style.TOPBAR_Y;

        Render2D.pushAlpha(open);
        Render2D.pushTranslate(gg, (1.0f - open) * -10.0f, 0.0f);
        Render2D.round(gg, x, y, Style.SOURCE_W, Style.TOPBAR_H,
                Style.TOPBAR_R, Style.WHITE_02);
        for (int i = 0; i < SOURCE_ICONS.length; i++) {
            float center = sourceCenter(i);
            this.sourceHover[i].to(Render2D.hovered(mx, my, center - Style.SOURCE_STEP / 2.0f, y,
                    Style.SOURCE_STEP, Style.TOPBAR_H));
            float k = this.sourceHover[i].get();
            Render2D.icon(gg, SOURCE_ICONS[i], center - 7.0f,
                    y + (Style.TOPBAR_H - 14.0f) / 2.0f, 14.0f, 14.0f,
                    Render2D.lerp(Style.WHITE_25, Style.accent(), k));
        }
        Render2D.popTransform(gg);
        Render2D.popAlpha();
    }

    private void renderContent(GuiGraphicsExtractor gg, double mx, double my) {
        Page page = page();
        float contentHeight = page.contentHeight();
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        float scroll = scroll(contentHeight, viewport);

        float clipX = panelX + Style.CONTENT_X;
        float clipY = panelY + Style.CONTENT_TOP;
        float clipW = Style.COLUMN_PITCH * Style.COLUMNS;

        this.switchAnim.to(1.0f);
        float appear = Anim.easeOut(this.switchAnim.get());

        Render2D.pushClip(gg, clipX, clipY, clipW, viewport);
        Render2D.pushAlpha(appear);
        Render2D.pushTranslate(gg, 0.0f, (1.0f - appear) * 16.0f);
        page.render(gg, clipX, clipY, scroll, mx, my);
        Render2D.popTransform(gg);
        Render2D.popAlpha();
        Render2D.popClip(gg);

        renderScrollbar(gg, contentHeight, viewport, scroll, mx, my);
    }

    private void renderNav(GuiGraphicsExtractor gg, double mx, double my) {
        float x = panelX + Style.NAV_X;
        float y = panelY + Style.NAV_Y;
        Render2D.round(gg, x, y, Style.NAV_W, Style.NAV_H, Style.NAV_R,
                Style.PANEL_RAISED);

        this.navAnim.to(tab.ordinal());
        float slot = this.navAnim.get();
        float indicatorX = x + Style.NAV_FIRST + slot * Style.NAV_STEP
                - Style.NAV_INDICATOR_W / 2.0f;
        float spread = Math.abs(slot - tab.ordinal());
        float width = Style.NAV_INDICATOR_W * (1.0f + Math.min(spread, 1.0f) * 1.4f);
        Render2D.round(gg, indicatorX - (width - Style.NAV_INDICATOR_W) / 2.0f,
                y + Style.NAV_INDICATOR_Y, width, Style.NAV_INDICATOR_H, 1.0f,
                Style.accent());

        for (Tab value : Tab.values()) {
            float center = x + Style.NAV_FIRST + value.ordinal() * Style.NAV_STEP;
            boolean hover = Render2D.hovered(mx, my, center - Style.NAV_STEP / 2.0f, y,
                    Style.NAV_STEP, Style.NAV_H);
            Anim anim = this.navHover.get(value);
            anim.to(hover || value == tab);
            float k = anim.get();
            Render2D.icon(gg, value.icon(), center - value.iconWidth() / 2.0f,
                    y + Style.NAV_H / 2.0f - value.iconHeight() / 2.0f,
                    value.iconWidth(), value.iconHeight(),
                    value == tab ? Style.accent()
                            : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, k));
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor gg, float contentHeight, float viewport,
                                 float scroll, double mx, double my) {
        float overflow = Math.max(0.0f, contentHeight - viewport);
        boolean recent = System.currentTimeMillis() - this.lastScroll < 900L;
        boolean nearby = Render2D.hovered(mx, my, panelX, panelY,
                Style.PANEL_W, Style.PANEL_H);
        this.scrollbarAnim.to(overflow > 0.0f && (recent || nearby) ? 1.0f : 0.35f);
        float visibility = this.scrollbarAnim.get();

        float x = panelX + Style.SCROLLBAR_X;
        float y = panelY + Style.SCROLLBAR_Y;
        Render2D.round(gg, x, y, Style.SCROLLBAR_W, Style.SCROLLBAR_H, 1.0f,
                Render2D.withAlpha(Style.WHITE_04, visibility));

        float thumb = overflow <= 0.0f ? Style.SCROLLBAR_H
                : Math.max(Style.SCROLLBAR_MIN_THUMB,
                Style.SCROLLBAR_H * viewport / contentHeight);
        float travel = Style.SCROLLBAR_H - thumb;
        float offset = overflow <= 0.0f ? 0.0f : travel * (scroll / overflow);
        Render2D.round(gg, x, y + offset, Style.SCROLLBAR_W, thumb, 1.0f,
                Render2D.withAlpha(Render2D.lerp(Style.WHITE_08, Style.accent(),
                        (visibility - 0.35f) / 0.65f * 0.55f), visibility));
    }

    private float scroll(float contentHeight, float viewport) {
        float max = Math.max(0.0f, contentHeight - viewport);
        float target = Mth.clamp(SCROLL.getOrDefault(scrollKey(), 0.0f), 0.0f, max);
        SCROLL.put(scrollKey(), target);
        this.scrollAnim.to(target);
        return Math.round(this.scrollAnim.get());
    }

    private float sourceCenter(int index) {
        return panelX + Style.CONTENT_X + (Style.SOURCE_W - SOURCE_ICONS.length * Style.SOURCE_STEP) / 2.0f
                + Style.SOURCE_STEP * (index + 0.5f);
    }

    private void clampToScreen() {
        panelX = Math.round(Mth.clamp(panelX, 0.0f,
                Math.max(0, Render2D.screenWidth() - Style.PANEL_W)));
        panelY = Math.round(Mth.clamp(panelY, 0.0f,
                Math.max(0, Render2D.screenHeight() - Style.PANEL_H)));
    }

    /** Курсор над панелью ClickGUI (а не над HUD за ней). */
    private static boolean overPanel(double mx, double my) {
        return Render2D.hovered(mx, my, panelX, panelY, Style.PANEL_W, Style.PANEL_H);
    }

    private void switchTo(Tab value) {
        if (tab == value) {
            return;
        }
        tab = value;
        this.switchAnim.snap(0.0f);
        this.scrollAnim.snap(SCROLL.getOrDefault(scrollKey(), 0.0f));
        page().onShow();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.closing) {
            return true;
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        int button = event.button();


        for (Tab value : Tab.values()) {
            float center = panelX + Style.NAV_X + Style.NAV_FIRST
                    + value.ordinal() * Style.NAV_STEP;
            if (Render2D.hovered(mx, my, center - Style.NAV_STEP / 2.0f,
                    panelY + Style.NAV_Y, Style.NAV_STEP, Style.NAV_H)) {
                switchTo(value);
                return true;
            }
        }

        TopBarLayout layout = topBarLayout();
        float barY = panelY + Style.TOPBAR_Y;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (Render2D.hovered(mx, my, layout.tabX[i], barY, layout.tabW[i], Style.TOPBAR_H)) {
                if (tab != Tab.MODULES) {
                    switchTo(Tab.MODULES);
                }
                if (categories[i] != MODULES_PAGE.category()) {
                    MODULES_PAGE.category(categories[i]);
                    this.switchAnim.snap(0.0f);
                    this.scrollAnim.snap(SCROLL.getOrDefault(scrollKey(), 0.0f));
                }
                return true;
            }
        }

        if (tab == Tab.CONFIGS && this.sourceAnim.get() > 0.5f) {
            for (int i = 0; i < SOURCE_ICONS.length; i++) {
                float center = sourceCenter(i);
                if (Render2D.hovered(mx, my, center - Style.SOURCE_STEP / 2.0f, barY,
                        Style.SOURCE_STEP, Style.TOPBAR_H)) {
                    switch (i) {
                        case 0 -> ConfigManager.openFolder();
                        case 1 -> ConfigManager.save(ConfigManager.nextFreeName());
                        default -> ConfigManager.refresh();
                    }
                    return true;
                }
            }
        }

        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        float clipX = panelX + Style.CONTENT_X;
        float clipY = panelY + Style.CONTENT_TOP;
        if (Render2D.hovered(mx, my, clipX, clipY,
                Style.COLUMN_PITCH * Style.COLUMNS, viewport)) {
            Page page = page();
            if (page.mouseClicked(clipX, clipY, scroll(page.contentHeight(), viewport), mx, my, button)) {
                return true;
            }
        }

        if (Render2D.hovered(mx, my, panelX, panelY, Style.PANEL_W, Style.PANEL_H)) {
            this.draggingPanel = true;
            this.grabX = (float) mx - panelX;
            this.grabY = (float) my - panelY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        if (this.draggingPanel) {
            panelX = (float) mx - this.grabX;
            panelY = (float) my - this.grabY;
            clampToScreen();
            return true;
        }
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        Page page = page();
        page.mouseDragged(panelX + Style.CONTENT_X, panelY + Style.CONTENT_TOP,
                scroll(page.contentHeight(), viewport), mx, my);
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingPanel = false;
        page().mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        if (!Render2D.hovered(mx, my,
                panelX, panelY, Style.PANEL_W, Style.PANEL_H)) {
            return false;
        }
        float viewport = Style.PANEL_H - Style.CONTENT_TOP;
        float max = Math.max(0.0f, page().contentHeight() - viewport);
        float value = Mth.clamp(SCROLL.getOrDefault(scrollKey(), 0.0f) - (float) scrollY * 48.0f,
                0.0f, max);
        SCROLL.put(scrollKey(), value);
        this.lastScroll = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (page().keyPressed(key)) {
            return true;
        }
        if (page().capturesInput()) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return page().charTyped((char) event.codepoint()) || super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        page().mouseReleased();
    }

    public static void preload() {
        Fonts.title();
        Fonts.body();
        Fonts.label();
    }
}
