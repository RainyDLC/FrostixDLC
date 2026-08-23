package ru.white.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import ru.white.module.api.Category;
import ru.white.script.LuaScriptManager;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.Scissor;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Редактор Lua-модулей: несколько вкладок с независимым кодом, имя модуля,
 * подсветка синтаксиса, вертикальная и горизонтальная прокрутка, проверка
 * компиляции при сохранении. Каждая вкладка сохраняется отдельным .lua-файлом.
 */
public class ScriptEditorScreen extends Screen implements IMinecraft {

    private static final Set<String> KEYWORDS = Set.of(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
            "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
            "true", "until", "while");

    private static final int LINE_H = 11;

    /** Одна вкладка редактора: код, курсор, скроллы, файл. */
    private static final class Tab {
        String name = "";
        final List<String> lines = new ArrayList<>();
        int row, col;
        /** Якорь выделения; -1 — выделения нет. */
        int anchorRow = -1, anchorCol = -1;
        float scrollY, scrollYTarget;
        float scrollX, scrollXTarget;
        Path file;
        boolean dirty;

        String label() {
            String base = name.isEmpty() ? "Без имени" : name;
            return dirty ? base + " *" : base;
        }
    }

    private final Screen parent;
    private final Category category;
    private final List<Tab> tabs = new ArrayList<>();
    private int active;

    private String errorText;
    private final Animation anim = new Animation();
    private float scaleFix = 1F;
    private float mouseX, mouseY;

    private float panelX, panelY, panelW, panelH;
    private float[] closeRect, cancelRect, saveRect, addTabRect;
    private final List<float[]> tabRects = new ArrayList<>();
    private final List<Integer> tabIndex = new ArrayList<>();
    private final List<float[]> tabCloseRects = new ArrayList<>();

    private float codeX, codeY, codeW, codeH;
    private float gutterW = 24F;
    private float nameX, nameY, nameW = 220F, nameH = 16F;
    private boolean nameFocus;

    public ScriptEditorScreen(Screen parent, Category category) {
        super(Text.literal("Script Editor"));
        this.parent = parent;
        this.category = category;
        Tab first = new Tab();
        setTemplate(first);
        tabs.add(first);
    }

    private Tab tab() {
        return tabs.get(active);
    }

    private static void setTemplate(Tab t) {
        t.lines.clear();
        t.lines.add("-- Новый модуль RainyDLC");
        t.lines.add("-- Колбэки: on_enable, on_disable, on_tick, on_render(delta)");
        t.lines.add("-- API: api.chat(t), api.player_health(), api.player_x() ...");
        t.lines.add("");
        t.lines.add("module.name = \"МойМодуль\"");
        t.lines.add("module.desc = \"Описание модуля\"");
        t.lines.add("");
        t.lines.add("-- Параметры появятся в настройках модуля справа:");
        t.lines.add("local boost = module.setting_slider(\"Буст\", 2, 0, 10, 0.5)");
        t.lines.add("local extra = module.setting_toggle(\"Дополнительно\", false)");
        t.lines.add("local style = module.setting_mode(\"Режим\", \"Обычный\", {\"Обычный\", \"Агрессивный\"})");
        t.lines.add("local tint  = module.setting_color(\"Цвет\", 0xFF00FFFF)");
        t.lines.add("");
        t.lines.add("function on_enable()");
        t.lines.add("    api.chat(module.name .. \" включён!\")");
        t.lines.add("end");
        t.lines.add("");
        t.lines.add("function on_disable() end");
        t.lines.add("");
        t.lines.add("function on_tick()");
        t.lines.add("end");
        t.row = 4;
        t.col = t.lines.get(4).length();
    }

    @Override
    protected void init() {
        anim.set(0);
        anim.run(1, 0.2F, Easings.SINE_OUT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private String code() {
        return String.join("\n", tab().lines);
    }

    // ── рендер ──

    @Override
    public void render(DrawContext context, int rawX, int rawY, float delta) {
        scaleFix = 2F / mc.getWindow().getScaleFactor();
        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);
        mouseX = rawX / scaleFix;
        mouseY = rawY / scaleFix;

        anim.update();
        float a = anim.get();
        Font f = Fonts.sf_regular;
        int accent = ColorUtil.client();
        Tab cur = tab();

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, a, 10F, ColorUtil.getColor(0, 0.45F));

        float w = 560F, h = 360F;
        float x = screenWidth / 2F - w / 2F;
        float y = screenHeight / 2F - h / 2F;
        panelX = x; panelY = y; panelW = w; panelH = h;

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F, ColorUtil.getColor(0, 0.18F * a), 10F, 15, 1);
        RenderUtil.Blur.blur(x, y, w, h, a, 10F, ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.55F), a));
        RenderUtil.Render2D.outline(x, y, w, h, 0.8F, ColorUtil.replAlpha(accent, a * 90), 10F);

        // ── шапка ──
        f.draw("Редактор Lua", x + 14F, y + 12F, 9F, ColorUtil.getColor(235, a));
        f.draw("Категория: " + category.getName(), x + 14F, y + 27F, 6F, ColorUtil.replAlpha(accent, a * 0.9F));

        float cs = 16F;
        float cx = x + w - cs - 8F;
        closeRect = new float[]{cx, y + 8F, cs, cs};
        drawIconButton(f, closeRect, "X", a);

        // ── имя активной вкладки ──
        f.draw("Имя:", x + 14F, y + 33.5F, 6.5F, ColorUtil.getColor(200, a * 0.85F));
        nameX = x + 40F;
        nameY = y + 29F;
        boolean nHov = MathUtil.isHovered(mouseX, mouseY, nameX, nameY, nameW, nameH);
        RenderUtil.Render2D.rect(nameX, nameY, nameW, nameH, ColorUtil.getColor(0, 0.25F * a), 5F);
        RenderUtil.Render2D.outline(nameX, nameY, nameW, nameH, 0.6F,
                ColorUtil.replAlpha(accent, a * (nameFocus ? 0.9F : nHov ? 0.4F : 0.18F)), 5F);
        String shownName = nameFocus && nameTextFocused()
                ? cur.name + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "")
                : cur.name;
        f.draw(shownName.isEmpty() ? "Название модуля" : shownName, nameX + 7F, nameY + 4.5F, 6.5F,
                shownName.isEmpty() ? ColorUtil.getColor(150, a * 0.5F) : ColorUtil.getColor(230, a));

        // ── вкладки скриптов ──
        float stripY = y + 50F;
        float stripH = 14F;
        tabRects.clear();
        tabIndex.clear();
        tabCloseRects.clear();

        float tx = x + 12F;
        float maxTabX = x + w - 12F - 16F; // справа место под «+»
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String label = t.label();
            float tw = Math.max(44F, Math.min(96F, f.getWidth(label, 6F) + 20F));
            if (tx + tw > maxTabX) break;

            boolean actTab = i == active;
            boolean hovT = MathUtil.isHovered(mouseX, mouseY, tx, stripY, tw, stripH);
            RenderUtil.Render2D.rect(tx, stripY, tw, stripH,
                    ColorUtil.overCol(ColorUtil.getColor(0, 0.22F * a),
                            ColorUtil.replAlpha(accent, a * (actTab ? 0.32F : hovT ? 0.12F : 0F)), 1F), 4F);
            if (actTab)
                RenderUtil.Render2D.outline(tx, stripY, tw, stripH, 0.5F, ColorUtil.replAlpha(accent, a * 0.85F), 4F);
            RenderUtil.Render2D.rect(tx + 4F, stripY + stripH - 2.25F, (tw - 8F), 1.1F,
                    ColorUtil.replAlpha(accent, a * (actTab ? 0.9F : 0F)), 0.5F);

            f.draw(label, tx + 6F, stripY + 4F, 6F,
                    ColorUtil.getColor(actTab ? 240 : 185, a * (actTab ? 1F : hovT ? 0.9F : 0.65F)));

            // крестик закрытия вкладки
            float[] cr = new float[]{tx + tw - 10F, stripY + 3.5F, 7F, 7F};
            boolean crHov = MathUtil.isHovered(mouseX, mouseY, cr[0], cr[1], cr[2], cr[3]);
            if (crHov)
                RenderUtil.Render2D.rect(cr[0], cr[1], cr[2], cr[3], ColorUtil.getColor(255, 80, 80, a * 0.30F), 2F);
            f.drawCentered("x", cr[0] + cr[2] / 2F, cr[1] + 0.5F, 5F,
                    ColorUtil.getColor(crHov ? 255 : 170, a * (crHov ? 1F : 0.55F)));

            tabRects.add(new float[]{tx, stripY, tw, stripH});
            tabIndex.add(i);
            tabCloseRects.add(cr);
            tx += tw + 3F;
        }

        // кнопка новой вкладки
        float pbW = 13F;
        float pbX = x + w - 12F - pbW;
        addTabRect = new float[]{pbX, stripY, pbW, stripH};
        boolean pHov = MathUtil.isHovered(mouseX, mouseY, pbX, stripY, pbW, stripH);
        RenderUtil.Render2D.rect(pbX, stripY, pbW, stripH,
                ColorUtil.overCol(ColorUtil.getColor(0, 0.2F * a), ColorUtil.replAlpha(accent, a * 0.25F), pHov ? 1F : 0F), 4F);
        f.drawCentered("+", pbX + pbW / 2F, stripY + 3.5F, 7F, ColorUtil.getColor(230, a * (pHov ? 1F : 0.7F)));

        // ── область кода ──
        codeX = x + 12F;
        codeY = y + 68F;
        codeW = w - 24F;
        codeH = h - 68F - 36F;

        // колонка номеров подстраивается под количество строк — слева ничего не срезается
        int digits = Math.max(2, String.valueOf(cur.lines.size()).length());
        gutterW = 12F + f.getWidth("8".repeat(digits), 5.5F);

        RenderUtil.Render2D.rect(codeX, codeY, codeW, codeH, ColorUtil.getColor(0, 0.32F * a), 8F);
        RenderUtil.Render2D.outline(codeX, codeY, codeW, codeH, 0.6F,
                ColorUtil.overCol(ColorUtil.getColor(255, a * 0.10F),
                        ColorUtil.replAlpha(accent, a * 0.55F), nameFocus ? 0F : 1F), 8F);
        RenderUtil.Render2D.rect(codeX + 1F, codeY + 1F, gutterW - 1F, codeH - 2F, ColorUtil.getColor(255, a * 0.03F), 6F);
        RenderUtil.Render2D.rect(codeX + gutterW, codeY + 2F, 1F, codeH - 4F, ColorUtil.getColor(255, a * 0.08F), 0.5F);

        float viewW = codeW - gutterW - 12F;
        float maxLineW = 40F;
        for (String ln : cur.lines) maxLineW = Math.max(maxLineW, f.getWidth(ln, 6.5F));
        float maxX = Math.max(0F, maxLineW - viewW);
        cur.scrollXTarget = MathUtil.clamp(cur.scrollXTarget, 0F, maxX);
        cur.scrollX += (cur.scrollXTarget - cur.scrollX) * 0.3F;

        int contentH = cur.lines.size() * LINE_H + 8;
        int maxY = Math.max(0, contentH - (int) codeH);
        cur.scrollYTarget = MathUtil.clamp(cur.scrollYTarget, 0F, maxY);
        cur.scrollY += (cur.scrollYTarget - cur.scrollY) * 0.3F;

        // внимание: последний аргумент Scissor.enable — guiScale (как в Menu/RotationBuilder),
        // координаты редактора живут в пространстве x2
        Scissor.enable(codeX, codeY, codeW, codeH, 2);

        int firstRow = Math.max(0, (int) (cur.scrollY / LINE_H) - 1);
        int lastRow = Math.min(cur.lines.size(), firstRow + (int) (codeH / LINE_H) + 3);
        float lx = codeX + gutterW + 8F - cur.scrollX;

        for (int i = firstRow; i < lastRow; i++) {
            float rowY = codeY + 5F + i * LINE_H - cur.scrollY;
            String line = cur.lines.get(i);
            String num = String.valueOf(i + 1);
            f.draw(num, codeX + gutterW - 7F - f.getWidth(num, 5.5F), rowY + 1.5F, 5.5F,
                    i == cur.row ? ColorUtil.replAlpha(accent, a * 0.95F) : ColorUtil.getColor(140, a * 0.4F));
            if (i == cur.row)
                RenderUtil.Render2D.rect(codeX + gutterW + 2F, rowY - 0.5F, codeW - gutterW - 4F, LINE_H,
                        ColorUtil.getColor(255, a * 0.03F), 3F);

            // подсветка выделения
            if (hasSel(cur)) {
                int[] sP = selStart(cur), eP = selEnd(cur);
                int c0 = -1, c1 = -1;
                if (i > sP[0] && i < eP[0]) {
                    c0 = 0;
                    c1 = line.length();
                } else if (i == sP[0] && i == eP[0]) {
                    c0 = sP[1];
                    c1 = eP[1];
                } else if (i == sP[0]) {
                    c0 = sP[1];
                    c1 = line.length();
                } else if (i == eP[0]) {
                    c0 = 0;
                    c1 = eP[1];
                }
                if (c1 > c0) {
                    float x0 = lx + f.getWidth(substring(line, c0), 6.5F);
                    float ww = f.getWidth(line.substring(c0, c1), 6.5F);
                    RenderUtil.Render2D.rect(x0, rowY, ww, LINE_H - 1F, ColorUtil.replAlpha(accent, a * 0.30F), 2F);
                }
            }

            drawHighlighted(f, line, lx, rowY, a);
        }

        if (!nameFocus && nameTextFocused()) {
            String before = substring(cur.lines.get(Math.min(cur.row, cur.lines.size() - 1)), cur.col);
            float cxp = lx + f.getWidth(before, 6.5F);
            if (cxp >= codeX + gutterW && cxp <= codeX + codeW - 2F) {
                float cyp = codeY + 4.5F + cur.row * LINE_H - cur.scrollY;
                RenderUtil.Render2D.rect(cxp, cyp, 0.9F, LINE_H - 2.5F, ColorUtil.replAlpha(accent, a * 0.95F), 0.4F);
            }
        }

        Scissor.reset();

        if (maxY > 0) {
            float barH = Math.max(14F, codeH * ((float) codeH / contentH));
            float barY = codeY + (codeH - barH) * (cur.scrollY / maxY);
            RenderUtil.Render2D.rect(codeX + codeW - 4F, barY, 2.5F, barH, ColorUtil.getColor(255, a * 0.15F), 1.2F);
        }
        if (maxX > 0) {
            float barW = Math.max(14F, viewW * (viewW / Math.max(viewW, maxLineW)));
            float barX2 = codeX + gutterW + (viewW - barW) * (cur.scrollX / maxX);
            RenderUtil.Render2D.rect(barX2, codeY + codeH - 4F, barW, 2.5F, ColorUtil.getColor(255, a * 0.15F), 1.2F);
        }

        // ── низ: ошибка и кнопки ──
        if (errorText != null && !errorText.isEmpty()) {
            f.draw("! " + errorText, x + 14F, y + h - 22F, 6F, ColorUtil.getColor(255, 110, 110, a * 0.95F));
        }

        float bw = 74F, bh = 18F, byy = y + h - bh - 9F;
        float sx = x + w - 9F - bw;
        saveRect = new float[]{sx, byy, bw, bh};
        drawButton(f, saveRect, "Сохранить", true, a, accent);
        float cbx = sx - 6F - 60F;
        cancelRect = new float[]{cbx, byy, 60F, bh};
        drawButton(f, cancelRect, "Отмена", false, a, accent);

        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private boolean nameTextFocused() {
        return (System.currentTimeMillis() / 500) % 2 == 0;
    }

    // ── подсветка синтаксиса ──

    private void drawHighlighted(Font f, String line, float x, float y, float a) {
        int i = 0, n = line.length();
        float px = x;

        while (i < n) {
            char c = line.charAt(i);

            if (c == '-' && i + 1 < n && line.charAt(i + 1) == '-') {
                px = drawSeg(f, line.substring(i), px, y, 105, 165, 125, a);
                break;
            }
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c) j++;
                j = Math.min(j + 1, n);
                px = drawSeg(f, line.substring(i, j), px, y, 235, 180, 100, a);
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.')) j++;
                px = drawSeg(f, line.substring(i, j), px, y, 115, 205, 235, a);
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) j++;
                String word = line.substring(i, j);
                boolean kw = KEYWORDS.contains(word);
                boolean call = !kw && j < n && line.charAt(j) == '(';
                px = kw ? drawSeg(f, word, px, y, 195, 145, 255, a)
                        : call ? drawSeg(f, word, px, y, 130, 190, 255, a)
                        : drawSeg(f, word, px, y, 215, 215, 225, a);
                i = j;
                continue;
            }

            int j = i;
            while (j < n && !isSpecial(line.charAt(j))) j++;
            if (j == i) j++;
            px = drawSeg(f, line.substring(i, j), px, y, 170, 170, 185, a);
            i = j;
        }
    }

    private boolean isSpecial(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '"' || c == '\'';
    }

    private float drawSeg(Font f, String seg, float px, float py, int r, int g, int b, float a) {
        f.draw(seg, px, py + 1.5F, 6.5F, ColorUtil.getColor(r, g, b, a * 0.92F));
        return px + f.getWidth(seg, 6.5F);
    }

    // ── правка кода ──

    private static String substring(String s, int end) {
        return s.substring(0, Math.min(end, s.length()));
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    private Tab cur() {
        return tabs.get(active);
    }

    private void markDirty() {
        tab().dirty = true;
    }

    // ── выделение ──

    private boolean hasSel(Tab t) {
        return t.anchorRow != -1 && (t.anchorRow != t.row || t.anchorCol != t.col);
    }

    /** Лексикографически первая точка выделения: {row, col}. */
    private int[] selStart(Tab t) {
        if (t.anchorRow < t.row || (t.anchorRow == t.row && t.anchorCol <= t.col))
            return new int[]{t.anchorRow, t.anchorCol};
        return new int[]{t.row, t.col};
    }

    private int[] selEnd(Tab t) {
        int[] s = selStart(t);
        if (s[0] == t.anchorRow && s[1] == t.anchorCol) return new int[]{t.row, t.col};
        return new int[]{t.anchorRow, t.anchorCol};
    }

    private String selectedText(Tab t) {
        int[] s = selStart(t), e = selEnd(t);
        if (s[0] == e[0]) return t.lines.get(s[0]).substring(s[1], e[1]);
        StringBuilder sb = new StringBuilder();
        sb.append(t.lines.get(s[0]).substring(s[1]));
        for (int r = s[0] + 1; r < e[0]; r++) sb.append('\n').append(t.lines.get(r));
        sb.append('\n').append(t.lines.get(e[0]), 0, e[1]);
        return sb.toString();
    }

    /** Удаляет выделенный диапазон, курсор — в начало бывшего выделения. */
    private void deleteSelection() {
        Tab t = tab();
        if (!hasSel(t)) return;
        int[] s = selStart(t), e = selEnd(t);
        if (s[0] == e[0]) {
            String line = t.lines.get(s[0]);
            t.lines.set(s[0], line.substring(0, s[1]) + line.substring(e[1]));
        } else {
            String head = t.lines.get(s[0]).substring(0, s[1]);
            String tail = t.lines.get(e[0]).substring(e[1]);
            t.lines.set(s[0], head + tail);
            for (int r = e[0]; r >= s[0] + 1; r--) t.lines.remove(r);
        }
        t.anchorRow = -1;
        t.row = s[0];
        t.col = s[1];
        markDirty();
    }

    /** Каретка всегда остаётся в пределах видимой области (по обеим осям). */
    private void ensureCaretVisible() {
        Font f = Fonts.sf_regular;
        Tab t = tab();
        ensureCursor(t);

        float viewW = codeW - gutterW - 12F;
        float cxw = t.col == 0 ? 0 : f.getWidth(substring(t.lines.get(t.row), t.col), 6.5F);
        if (cxw < t.scrollX) t.scrollXTarget = Math.max(0F, cxw - 10F);
        else if (cxw > t.scrollX + viewW) t.scrollXTarget = Math.max(0F, cxw - viewW + 10F);

        float cyh = t.row * (float) LINE_H;
        if (cyh < t.scrollY) t.scrollYTarget = Math.max(0F, cyh);
        else if (cyh + LINE_H > t.scrollY + codeH - LINE_H)
            t.scrollYTarget = Math.max(0F, cyh - codeH + 2F * LINE_H);
    }

    private void ensureCursor(Tab t) {
        t.row = clampInt(t.row, 0, t.lines.size() - 1);
        t.col = clampInt(t.col, 0, t.lines.get(t.row).length());
    }

    private void insertText(String text) {
        Tab t = tab();
        ensureCursor(t);
        text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "  ");
        String[] parts = text.split("\n", -1);
        String head = t.lines.get(t.row).substring(0, t.col);
        String tail = t.lines.get(t.row).substring(t.col);
        t.lines.set(t.row, head + parts[0]);
        for (int p = 1; p < parts.length; p++) {
            t.lines.add(t.row + p, parts[p]);
        }
        String lastPart = parts[parts.length - 1];
        int lastIdx = t.row + parts.length - 1;
        t.lines.set(lastIdx, t.lines.get(lastIdx) + tail);
        t.row += parts.length - 1;
        t.col = lastPart.length();
        markDirty();
    }

    private void newlineWithIndent() {
        Tab t = tab();
        ensureCursor(t);
        String curLine = t.lines.get(t.row);
        StringBuilder indent = new StringBuilder();
        for (char ch : curLine.toCharArray()) {
            if (ch == ' ') indent.append(' ');
            else break;
        }
        String trimmed = curLine.trim();
        if (trimmed.endsWith("then") || trimmed.endsWith("do") || trimmed.endsWith("{")
                || trimmed.endsWith("else") || trimmed.endsWith("function")) {
            indent.append("    ");
        }
        t.lines.add(t.row + 1, indent.toString());
        t.row++;
        t.col = indent.length();
        markDirty();
    }

    // ── клавиатура ──

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        String str = input.asString();
        Tab t = tab();

        if (nameFocus) {
            if (nameW > 0 && str.matches("[^\\r\\n]") && t.name.length() < 24) {
                t.name += str;
                t.dirty = true;
                GuiSounds.type();
            }
            return true;
        }

        boolean ctrl = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrl && str.equalsIgnoreCase("v")) {
            try {
                String clip = mc.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    insertText(clip);
                    ensureCaretVisible();
                    GuiSounds.type();
                }
            } catch (Exception ignored) {
            }
            return true;
        }
        if (ctrl) return true;

        if (hasSel(tab())) deleteSelection();
        insertText(str);
        ensureCaretVisible();
        GuiSounds.type();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        Tab t = tab();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            backToMenu();
            return true;
        }

        if (nameFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !t.name.isEmpty()) {
                t.name = t.name.substring(0, t.name.length() - 1);
                t.dirty = true;
                GuiSounds.erase();
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                nameFocus = false;
                GuiSounds.editCommit();
            }
            return true;
        }

        boolean ctrl = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);

        // ── буфер обмена: Ctrl+V вставка, Ctrl+C строка, Ctrl+X вырезать строку ──
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip;
            try {
                clip = mc.keyboard.getClipboard();
            } catch (Exception e) {
                clip = null;
            }
            if (clip != null && !clip.isEmpty()) {
                if (nameFocus) {
                    String clean = clip.replace("\r", " ").replace("\n", " ");
                    t.name = t.name + clean;
                    if (t.name.length() > 24) t.name = t.name.substring(0, 24);
                    t.dirty = true;
                } else {
                    if (hasSel(t)) deleteSelection();
                    insertText(clip);
                    ensureCaretVisible();
                }
                GuiSounds.type();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C && !nameFocus) {
            try {
                mc.keyboard.setClipboard(hasSel(t) ? selectedText(t) : t.lines.get(t.row));
                GuiSounds.editCommit();
            } catch (Exception ignored) {
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X && !nameFocus) {
            try {
                mc.keyboard.setClipboard(hasSel(t) ? selectedText(t) : t.lines.get(t.row));
                if (hasSel(t)) {
                    deleteSelection();
                } else {
                    t.lines.remove(t.row);
                    if (t.lines.isEmpty()) t.lines.add("");
                    t.row = clampInt(t.row, 0, t.lines.size() - 1);
                    t.col = 0;
                }
                markDirty();
                ensureCaretVisible();
                GuiSounds.erase();
            } catch (Exception ignored) {
            }
            return true;
        }
        // Ctrl+A — выделить весь код
        if (ctrl && key == GLFW.GLFW_KEY_A && !nameFocus) {
            t.anchorRow = 0;
            t.anchorCol = 0;
            t.row = t.lines.size() - 1;
            t.col = t.lines.get(t.row).length();
            ensureCaretVisible();
            GuiSounds.type();
            return true;
        }

        // Ctrl+Tab / Ctrl+Shift+Tab — переключение вкладок
        if (ctrl && key == GLFW.GLFW_KEY_TAB && !tabs.isEmpty()) {
            int dir = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) ? -1 : 1;
            switchTo((active + dir + tabs.size()) % tabs.size());
            GuiSounds.chip(0, 2);
            return true;
        }

        if (ctrl && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }

        ensureCursor(t);
        boolean shift = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        int oldRow = t.row, oldCol = t.col;
        boolean edited = false;

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSel(t)) {
                    deleteSelection();
                } else {
                    String line = t.lines.get(t.row);
                    if (t.col > 0) {
                        t.lines.set(t.row, line.substring(0, t.col - 1) + line.substring(t.col));
                        t.col--;
                    } else if (t.row > 0) {
                        String prev = t.lines.get(t.row - 1);
                        t.col = prev.length();
                        t.lines.set(t.row - 1, prev + line);
                        t.lines.remove(t.row);
                        t.row--;
                    }
                }
                edited = true;
                markDirty();
                GuiSounds.erase();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSel(t)) {
                    deleteSelection();
                } else {
                    String line = t.lines.get(t.row);
                    if (t.col < line.length()) {
                        t.lines.set(t.row, line.substring(0, t.col) + line.substring(t.col + 1));
                    } else if (t.row < t.lines.size() - 1) {
                        t.lines.set(t.row, line + t.lines.get(t.row + 1));
                        t.lines.remove(t.row + 1);
                    }
                    markDirty();
                }
                edited = true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (hasSel(t)) deleteSelection();
                newlineWithIndent();
                edited = true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (hasSel(t)) deleteSelection();
                insertText("  ");
                edited = true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (t.col > 0) t.col--;
                else if (t.row > 0) {
                    t.row--;
                    t.col = t.lines.get(t.row).length();
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (t.col < t.lines.get(t.row).length()) t.col++;
                else if (t.row < t.lines.size() - 1) {
                    t.row++;
                    t.col = 0;
                }
            }
            case GLFW.GLFW_KEY_UP -> t.row = Math.max(0, t.row - 1);
            case GLFW.GLFW_KEY_DOWN -> t.row = Math.min(t.lines.size() - 1, t.row + 1);
            case GLFW.GLFW_KEY_HOME -> t.col = 0;
            case GLFW.GLFW_KEY_END -> t.col = t.lines.get(t.row).length();
            case GLFW.GLFW_KEY_PAGE_UP -> t.row = Math.max(0, t.row - 12);
            case GLFW.GLFW_KEY_PAGE_DOWN -> t.row = Math.min(t.lines.size() - 1, t.row + 12);
            default -> {
                return super.keyPressed(input);
            }
        }

        // Shift+движение — расширяет выделение; движение без Shift — схлопывает
        if (shift && !edited) {
            if (!hasSel(t)) {
                t.anchorRow = oldRow;
                t.anchorCol = oldCol;
            }
        } else if (!edited) {
            t.anchorRow = -1;
        }

        ensureCaretVisible();
        return true;
    }

    // ── мышь ──

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = (float) (click.x() / scaleFix);
        float my = (float) (click.y() / scaleFix);
        mouseX = mx;
        mouseY = my;

        if (closeRect != null && MathUtil.isHovered(mx, my, closeRect[0], closeRect[1], closeRect[2], closeRect[3])) {
            backToMenu();
            return true;
        }
        if (cancelRect != null && MathUtil.isHovered(mx, my, cancelRect[0], cancelRect[1], cancelRect[2], cancelRect[3])) {
            backToMenu();
            return true;
        }
        if (saveRect != null && MathUtil.isHovered(mx, my, saveRect[0], saveRect[1], saveRect[2], saveRect[3])) {
            save();
            return true;
        }

        // вкладки: переключение / закрытие / новая
        for (int i = 0; i < tabCloseRects.size(); i++) {
            float[] cr = tabCloseRects.get(i);
            if (MathUtil.isHovered(mx, my, cr[0], cr[1], cr[2], cr[3])) {
                GuiSounds.erase();
                closeTab(tabIndex.get(i));
                return true;
            }
        }
        for (int i = 0; i < tabRects.size(); i++) {
            float[] r = tabRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                switchTo(tabIndex.get(i));
                GuiSounds.chip(i, tabs.size());
                return true;
            }
        }
        if (addTabRect != null && MathUtil.isHovered(mx, my, addTabRect[0], addTabRect[1], addTabRect[2], addTabRect[3])) {
            newTab();
            GuiSounds.button();
            return true;
        }

        Tab t = tab();
        if (MathUtil.isHovered(mx, my, nameX, nameY, nameW, nameH)) {
            nameFocus = true;
            GuiSounds.editStart();
            return true;
        }
        nameFocus = false;

        if (MathUtil.isHovered(mx, my, codeX, codeY, codeW, codeH)) {
            int newRow = Math.round((my - codeY - 5F + t.scrollY) / LINE_H);
            newRow = clampInt(newRow, 0, t.lines.size() - 1);

            String line = t.lines.get(newRow);
            Font f = Fonts.sf_regular;
            float relX = mx - (codeX + gutterW + 8F) + t.scrollX;
            int newCol = 0;
            for (int i = 0; i <= line.length(); i++) {
                if (i == line.length() || f.getWidth(line.substring(0, i + 1), 6.5F) > relX) {
                    newCol = i;
                    break;
                }
            }
            newCol = clampInt(newCol, 0, line.length());

            // Shift+клик — расширяет выделение от прежнего курсора
            boolean shiftClick = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (shiftClick) {
                if (!hasSel(t) && t.anchorRow == -1) {
                    t.anchorRow = t.row;
                    t.anchorCol = t.col;
                }
            } else {
                t.anchorRow = -1;
            }
            t.row = newRow;
            t.col = newCol;
            nameFocus = false;
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseXRaw, double mouseYRaw, double horizontalAmount, double verticalAmount) {
        float mx = (float) (mouseXRaw / scaleFix);
        float my = (float) (mouseYRaw / scaleFix);
        Tab t = tab();

        // колесо над полосой вкладок — листает вкладки
        if (!tabRects.isEmpty()) {
            float sy0 = tabRects.get(0)[1];
            float sxL = tabRects.get(0)[0];
            float sxR = addTabRect != null ? addTabRect[0] + addTabRect[2] : sxL + 100F;
            if (my >= sy0 && my <= sy0 + tabRects.get(0)[3] && mx >= sxL - 4F && mx <= sxR) {
                switchTo((active + (verticalAmount > 0 ? -1 : 1) + tabs.size()) % tabs.size());
                return true;
            }
        }

        if (MathUtil.isHovered(mx, my, codeX, codeY, codeW, codeH)) {
            boolean shift = InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (shift) {
                // горизонтальная прокрутка длинных строк
                float viewW = codeW - gutterW - 12F;
                float maxLineW = 40F;
                for (String ln : t.lines) maxLineW = Math.max(maxLineW, f_width(ln));
                float maxXs = Math.max(0F, maxLineW - viewW);
                t.scrollXTarget = MathUtil.clamp(t.scrollXTarget - (float) verticalAmount * 24F, 0F, maxXs);
            } else {
                t.scrollYTarget -= (float) (verticalAmount * LINE_H * 2);
            }
            return true;
        }
        return super.mouseScrolled(mouseXRaw, mouseYRaw, horizontalAmount, verticalAmount);
    }

    private float f_width(String s) {
        return Fonts.sf_regular.getWidth(s, 6.5F);
    }

    // ── вкладки ──

    private void switchTo(int index) {
        active = clampInt(index, 0, tabs.size() - 1);
        nameFocus = false;
        errorText = null;
    }

    private void newTab() {
        Tab t = new Tab();
        t.lines.add("");
        tabs.add(t);
        active = tabs.size() - 1;
        errorText = null;
        nameFocus = false;
    }

    private void closeTab(int index) {
        tabs.remove(index);
        if (tabs.isEmpty()) {
            Tab fresh = new Tab();
            fresh.lines.add("");
            tabs.add(fresh);
        }
        active = clampInt(active > index ? active - 1 : active, 0, tabs.size() - 1);
    }

    // ── сохранение ──

    private void save() {
        Tab t = tab();
        String name = t.name.replaceAll("[^\\w А-Яа-яЁё\\-]", "").trim();
        if (name.isEmpty()) {
            errorText = "Введите название модуля";
            return;
        }
        String syntax = LuaScriptManager.validate(code());
        if (syntax != null) {
            errorText = syntax;
            return;
        }

        LuaScriptManager.Result result = t.file != null
                ? LuaScriptManager.get().update(t.file, category, code())
                : LuaScriptManager.get().create(category, name, code());
        if (!result.ok()) {
            errorText = result.error();
            return;
        }

        if (result.module() != null) t.file = result.module().getFile();
        t.name = name;
        t.dirty = false;
        errorText = null;
        backToMenu();
    }

    private void backToMenu() {
        if (mc.currentScreen == this) mc.setScreen(parent);
    }

    // ── кнопки ──

    private void drawButton(Font f, float[] r, String text, boolean primary, float a, int accent) {
        boolean hov = MathUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        RenderUtil.Render2D.rect(r[0], r[1], r[2], r[3], ColorUtil.overCol(
                ColorUtil.getColor(255, a * (hov ? 0.09F : 0.04F)),
                ColorUtil.replAlpha(accent, a * (primary ? 0.35F : 0.12F)), hov ? 1F : 0F), 5F);
        RenderUtil.Render2D.outline(r[0], r[1], r[2], r[3], 0.5F,
                ColorUtil.replAlpha(accent, a * (primary ? 0.8F : (hov ? 0.5F : 0.2F))), 5F);
        f.drawCentered(text, r[0] + r[2] / 2F, r[1] + 5.5F, 6.5F,
                ColorUtil.getColor(primary ? 245 : 210, a * (hov ? 1F : 0.85F)));
    }

    private void drawIconButton(Font f, float[] r, String glyph, float a) {
        boolean hov = MathUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        RenderUtil.Render2D.rect(r[0], r[1], r[2], r[3], ColorUtil.getColor(255, a * (hov ? 0.10F : 0.04F)), 5F);
        RenderUtil.Render2D.outline(r[0], r[1], r[2], r[3], 0.5F, ColorUtil.getColor(255, a * (hov ? 0.45F : 0.15F)), 5F);
        f.drawCentered(glyph, r[0] + r[2] / 2F, r[1] + 5F, 6.5F, ColorUtil.getColor(255, a * (hov ? 0.95F : 0.55F)));
    }
}
