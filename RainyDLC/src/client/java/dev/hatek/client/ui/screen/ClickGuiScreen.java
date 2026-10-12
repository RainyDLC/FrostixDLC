package dev.hatek.client.ui.screen;

import dev.hatek.client.ui.Style;
import dev.hatek.client.feature.config.ConfigManager;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.impl.render.ClickGui;
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
        // Режим Blur: размываем мир за GUI (сила — из настройки "Размытие фона" в Minecraft).
        if (ClickGui.blur()) {
            extractBlurredBackground(gg);
        }
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
        Render2D.pushTranslate(gg, 0.0f, (1.0f - eased) * 6.0f);
        Render2D.pushAlpha(eased);

        Render2D.round(gg, panelX, panelY, Style.PANEL_W, Style.PANEL_H,
                Style.PANEL_R, ClickGui.blur() ? Style.PANEL_BLUR : Style.PANEL);
        renderSidebar(gg, mx, my);
        renderSourceMenu(gg, mx, my);
        renderContent(gg, mx, my);
        renderNav(gg, mx, my);

        Render2D.popAlpha();
        Render2D.popTransform(gg);
        Render2D.end(gg);
    }

    private static float catY(float index) {
        return panelY + Style.SIDEBAR_Y + Style.CAT_TOP + index * Style.CAT_H;
    }

    private static float sourceY() {
        return panelY + Style.PANEL_H - Style.SIDEBAR_Y - Style.CAT_H - 8.0f;
    }

    private void renderSidebar(GuiGraphicsExtractor gg, double mx, double my) {
        float x = panelX + Style.SIDEBAR_X;
        float y = panelY + Style.SIDEBAR_Y;
        Render2D.rect(gg, panelX + Style.DIVIDER_X, y, 1.0f,
                Style.PANEL_H - Style.SIDEBAR_Y * 2.0f, Style.WHITE_04);

        Fonts.title().draw(gg, Style.CLIENT_NAME + Style.CLIENT_SUFFIX, x + Style.SIDEBAR_PAD,
                y + Style.NAME_BASELINE, Style.WHITE);

        Category current = MODULES_PAGE.category();
        boolean modules = tab == Tab.MODULES;

        Category[] categories = Category.values();
        float rowX = x + Style.CAT_INSET;
        float rowW = Style.SIDEBAR_W - Style.CAT_INSET * 2.0f;
        for (int i = 0; i < categories.length; i++) {
            Category value = categories[i];
            float rowY = catY(i);
            boolean active = value == current && modules;
            Anim anim = this.tabHover.get(value);
            anim.to(active || Render2D.hovered(mx, my, rowX, rowY, rowW, Style.CAT_H));
            int text = active ? Style.WHITE : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, anim.get());
            float ix = rowX + 10.0f;
            Render2D.icon(gg, value.icon(), ix, rowY + (Style.CAT_H - value.iconHeight()) / 2.0f,
                    value.iconWidth(), value.iconHeight(), active ? Style.accent() : text);
            Fonts.body().draw(gg, value.displayName(), ix + 14.0f + Style.CAT_ICON_GAP,
                    Fonts.body().centeredBaseline(rowY, Style.CAT_H), text);
        }
    }

    private void renderSourceMenu(GuiGraphicsExtractor gg, double mx, double my) {
        this.sourceAnim.to(tab == Tab.CONFIGS);
        float open = Anim.easeInOut(this.sourceAnim.get());
        if (open <= 0.002f) {
            return;
        }
        float y = sourceY();
        Render2D.pushAlpha(open);
        Render2D.pushTranslate(gg, 0.0f, (1.0f - open) * 10.0f);
        for (int i = 0; i < SOURCE_ICONS.length; i++) {
            float center = sourceCenter(i);
            this.sourceHover[i].to(Render2D.hovered(mx, my, center - Style.SOURCE_STEP / 2.0f, y,
                    Style.SOURCE_STEP, Style.CAT_H));
            float k = this.sourceHover[i].get();
            Render2D.icon(gg, SOURCE_ICONS[i], center - 7.0f,
                    y + (Style.CAT_H - 14.0f) / 2.0f, 14.0f, 14.0f,
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
        Render2D.round(gg, x, y, Style.NAV_W, Style.NAV_H, Style.NAV_R, Style.PANEL_RAISED);

        for (Tab value : Tab.values()) {
            float center = x + Style.NAV_FIRST + value.ordinal() * Style.NAV_STEP;
            boolean hover = Render2D.hovered(mx, my, center - Style.NAV_STEP / 2.0f, y,
                    Style.NAV_STEP, Style.NAV_H);
            Anim anim = this.navHover.get(value);
            anim.to(hover);
            Render2D.icon(gg, value.icon(), center - value.iconWidth() / 2.0f,
                    y + Style.NAV_H / 2.0f - value.iconHeight() / 2.0f,
                    value.iconWidth(), value.iconHeight(),
                    value == tab ? Style.accent()
                            : Render2D.lerp(Style.WHITE_25, Style.WHITE_45, anim.get()));
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
        return panelX + Style.SIDEBAR_X + Style.SIDEBAR_W / 2.0f
                + (index - (SOURCE_ICONS.length - 1) / 2.0f) * Style.SOURCE_STEP;
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

        float rowX = panelX + Style.SIDEBAR_X + Style.CAT_INSET;
        float rowW = Style.SIDEBAR_W - Style.CAT_INSET * 2.0f;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (Render2D.hovered(mx, my, rowX, catY(i), rowW, Style.CAT_H)) {
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
            float barY = sourceY();
            for (int i = 0; i < SOURCE_ICONS.length; i++) {
                float center = sourceCenter(i);
                if (Render2D.hovered(mx, my, center - Style.SOURCE_STEP / 2.0f, barY,
                        Style.SOURCE_STEP, Style.CAT_H)) {
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
