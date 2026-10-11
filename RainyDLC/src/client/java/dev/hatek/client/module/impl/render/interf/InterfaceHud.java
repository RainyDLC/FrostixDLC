package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Отрисовка HUD модуля Interface в стилистике ClickGUI
 * (константы Style.HUD_* / Style.WM_*): ватермарка, кейбинды
 * функций, активные эффекты, кастомный хотбар и 2D-медиа.
 *
 * Масштаб применяется НЕ трансформацией позы (так текст из атласа
 * становился бы пиксельным), а честно: геометрия умножается на scale,
 * а текст рисуется шрифтом, запечённым в нужном размере
 * (Fonts.of кэширует по размеру, шаг квантования 0.1).
 *
 * Каждый элемент измеряется (measure*), затем рисуется в origin (0,0),
 * а {@link #place} ставит его на позицию из {@link HudLayout}
 * (или на якорь по умолчанию). Границы последнего кадра
 * доступны через {@link #boundsSnapshot()} для редактора HUD
 * и свободного перемещения.
 */
public final class InterfaceHud {
    private static boolean hooked;
    /**
     * true, пока открыт ClickGUI: игровой хук пропускает кадр,
     * HUD явно рисует сам экран (иначе за экраном остаётся замороженный кадр).
     */
    private static boolean screenOpen;

    /** Последний кадр: элемент -> {x, y, w, h} в GUI-пикселях. */
    private static final Map<HudLayout.Element, float[]> BOUNDS =
            new EnumMap<>(HudLayout.Element.class);

    /** Данные замера элемента (включают размер). Замер — один раз за кадр. */
    private interface Sized {
        float w();

        float h();
    }

    private InterfaceHud() {
    }

    public static void init() {
        if (hooked) {
            return;
        }
        hooked = true;
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("hatek_client", "interface_hud"),
                (gg, tickCounter) -> render(gg));
    }

    /** ClickGUI сообщает, открыт ли он, чтобы хук и экран не рисовали дважды. */
    public static void setScreenOpen(boolean open) {
        screenOpen = open;
    }

    /** Копия границ элементов последнего отрисованного кадра. */
    public static Map<HudLayout.Element, float[]> boundsSnapshot() {
        return new EnumMap<>(BOUNDS);
    }

    // ---------------- масштабированные шрифты ----------------

    /** Квантование размера шрифта, чтобы кэш Fonts.of не разрастался. */
    private static float qs(float s) {
        return Math.round(s * 10.0f) / 10.0f;
    }

    private static HFont sTitle(float s) {
        float q = qs(s);
        return q == 1.0f ? Fonts.title()
                : Fonts.of(Fonts.ROBOTO_MEDIUM, Style.FONT_TITLE * q);
    }

    private static HFont sBody(float s) {
        float q = qs(s);
        return q == 1.0f ? Fonts.body()
                : Fonts.of(Fonts.ROBOTO_REGULAR, Style.FONT_BODY * q);
    }

    private static HFont sLabel(float s) {
        float q = qs(s);
        return q == 1.0f ? Fonts.label()
                : Fonts.of(Fonts.INTER_MEDIUM, Style.FONT_LABEL * q);
    }

    // ---------------- точка входа ----------------

    private static void render(GuiGraphicsExtractor gg) {
        if (screenOpen) {
            return; // HUD явно рисует сам открытый экран.
        }
        HudMover.tickInGame();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (Interface.instance() == null || !Interface.instance().isEnabled()) {
            return;
        }
        renderAll(gg);
    }

    /**
     * Явная отрисовка HUD за открытым экраном (вызывает ClickGuiScreen
     * каждый кадр). Границы обновляются — перетаскивание видит свежие данные.
     */
    public static void renderExplicit(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            BOUNDS.clear();
            return;
        }
        if (Interface.instance() == null || !Interface.instance().isEnabled()) {
            BOUNDS.clear();
            return;
        }
        renderAll(gg);
    }

    /** Отрисовка всех элементов (и в игре, и за ClickGUI). */
    private static void renderAll(GuiGraphicsExtractor gg) {
        BOUNDS.clear();
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();

        Render2D.begin(gg);
        place(gg, HudLayout.Element.WATERMARK, Interface.showWatermark(), sw, sh,
                InterfaceHud::measureWatermark, InterfaceHud::drawWatermark);
        place(gg, HudLayout.Element.KEYBINDS, Interface.showKeybinds(), sw, sh,
                InterfaceHud::measureKeybinds, InterfaceHud::drawKeybinds);
        place(gg, HudLayout.Element.EFFECTS, Interface.showEffects(), sw, sh,
                InterfaceHud::measureEffects, InterfaceHud::drawEffects);
        place(gg, HudLayout.Element.HOTBAR, Interface.showHotbar(), sw, sh,
                InterfaceHud::measureHotbar, InterfaceHud::drawHotbar);
        placeMedia2D(gg);
        Render2D.end(gg);
    }

    /**
     * Ставит элемент на позицию из HudLayout (или якорь по умолчанию)
     * и рисует с ручным масштабом. Замер выполняется один раз за кадр
     * до позиционирования, чтобы якоря справа/снизу знали размер.
     */
    private interface Drawer<D extends Sized> {
        void draw(GuiGraphicsExtractor gg, float s, D data);
    }

    private static <D extends Sized> void place(GuiGraphicsExtractor gg,
                                                HudLayout.Element el, boolean visible,
                                                int sw, int sh,
                                                Function<Float, D> measure,
                                                Drawer<D> drawer) {
        if (!visible) {
            return;
        }
        HudLayout.Entry e = HudLayout.get(el);
        float s = e.scale;
        D data = measure.apply(s);
        if (data == null || data.w() <= 0 || data.h() <= 0) {
            return;
        }
        float[] a = anchor(el, data.w(), data.h(), sw, sh);
        float x = e.positioned() ? e.x : a[0];
        float y = e.positioned() ? e.y : a[1];

        Render2D.pushTranslate(gg, x, y);
        drawer.draw(gg, s, data);
        Render2D.popTransform(gg);

        BOUNDS.put(el, new float[]{x, y, data.w(), data.h()});
    }

    /** Якорь по умолчанию для элемента (размер уже с учётом масштаба). */
    private static float[] anchor(HudLayout.Element el, float w, float h, int sw, int sh) {
        float m = Style.HUD_MARGIN;
        switch (el) {
            case WATERMARK:
                return new float[]{m, m};
            case KEYBINDS:
                return new float[]{sw - w - m, m};
            case EFFECTS: {
                float[] kb = BOUNDS.get(HudLayout.Element.KEYBINDS);
                float top = (kb != null) ? kb[1] + kb[3] + 8.0f : m;
                return new float[]{sw - w - m, top};
            }
            case HOTBAR:
            default:
                return new float[]{(sw - w) / 2.0f, sh - h - 8.0f};
        }
    }

    // ---------------- ватермарка ----------------

    private static final class WmData implements Sized {
        final float w;
        final float h;
        final String fps;
        final String pingS;

        WmData(float w, float h, String fps, String pingS) {
            this.w = w;
            this.h = h;
            this.fps = fps;
            this.pingS = pingS;
        }

        @Override
        public float w() {
            return this.w;
        }

        @Override
        public float h() {
            return this.h;
        }

        public String fps() {
            return this.fps;
        }

        public String pingS() {
            return this.pingS;
        }
    }

    private static WmData measureWatermark(float s) {
        Minecraft mc = Minecraft.getInstance();
        HFont title = sTitle(s);
        HFont label = sLabel(s);
        float h = Style.WM_H * s;

        float nameW = title.width("Rainy") + title.width("DLC");

        String fps = mc.getFps() + " FPS";
        float fpsW = label.width(fps);

        int ping = ping();
        String pingS = ping >= 0 ? ping + " ms" : null;
        float pingW = pingS == null ? 0.0f : label.width(pingS);

        float logoS = 16.0f * s;
        float iconS = 11.0f * s;
        float w = Style.WM_PAD * s + logoS + 8.0f * s + nameW
                + Style.WM_SEP_GAP * s + Style.WM_SEP_W * s + Style.WM_SEP_GAP * s
                + iconS + Style.WM_UNIT_GAP * s + fpsW;
        if (pingS != null) {
            w += Style.WM_SEP_GAP * s + Style.WM_SEP_W * s + Style.WM_SEP_GAP * s
                    + iconS + Style.WM_UNIT_GAP * s + pingW;
        }
        w += Style.WM_PAD * s;
        return new WmData(w, h, fps, pingS);
    }

    /** Рисует ватермарку в origin (0,0) с ручным масштабом. */
    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WmData d) {
        float w = d.w();
        float h = d.h();
        HFont title = sTitle(s);
        HFont label = sLabel(s);

        Render2D.round(gg, 0, 0, w, h, Style.WM_R * s, Style.HUD_PANEL);

        float logoS = 16.0f * s;
        float iconS = 11.0f * s;
        float cx = Style.WM_PAD * s;
        Render2D.icon(gg, "logo", cx, (h - logoS) / 2.0f, logoS, logoS, Style.WHITE);
        cx += logoS + 8.0f * s;

        float baseline = Style.WM_BASELINE * s;
        cx = title.draw(gg, "Rainy", cx, baseline, Style.WHITE);
        cx = title.draw(gg, "DLC", cx, baseline, Style.accent());

        float unitBaseline = label.centeredBaseline(0, h);
        cx = separator(gg, cx, h, s);
        cx = unit(gg, "fps", d.fps(), cx, h, unitBaseline, s);

        if (d.pingS() != null) {
            cx = separator(gg, cx, h, s);
            unit(gg, "ping", d.pingS(), cx, h, unitBaseline, s);
        }
    }

    private static float separator(GuiGraphicsExtractor gg, float cx, float h, float s) {
        cx += Style.WM_SEP_GAP * s;
        Render2D.rect(gg, cx, (h - Style.WM_SEP_H * s) / 2.0f,
                Style.WM_SEP_W * s, Style.WM_SEP_H * s, Style.WHITE_08);
        return cx + Style.WM_SEP_W * s + Style.WM_SEP_GAP * s;
    }

    private static float unit(GuiGraphicsExtractor gg, String icon, String text,
                              float cx, float h, float baseline, float s) {
        float iconS = 11.0f * s;
        HFont label = sLabel(s);
        Render2D.icon(gg, icon, cx, (h - iconS) / 2.0f, iconS, iconS, Style.WHITE_45);
        cx += iconS + Style.WM_UNIT_GAP * s;
        float textW = label.width(text);
        label.draw(gg, text, cx, baseline, Style.WHITE);
        return cx + textW;
    }

    private static int cachedPing = -2;
    private static long pingAt;

    /** Пинг меняется редко — опрашиваем не чаще раза в секунду. */
    private static int ping() {
        long now = System.currentTimeMillis();
        if (now - pingAt < 1000 && cachedPing != -2) {
            return cachedPing;
        }
        pingAt = now;
        cachedPing = pingNow();
        return cachedPing;
    }

    private static int pingNow() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null || mc.player == null) {
                return -1;
            }
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? -1 : info.getLatency();
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------- кейбинды функций ----------------

    private static final class KbRow {
        final String name;
        final String keyLabel;
        final boolean enabled;

        KbRow(String name, String keyLabel, boolean enabled) {
            this.name = name;
            this.keyLabel = keyLabel;
            this.enabled = enabled;
        }
    }

    private static final class KbData implements Sized {
        final float w;
        final float h;
        final List<KbRow> rows;

        KbData(float w, float h, List<KbRow> rows) {
            this.w = w;
            this.h = h;
            this.rows = rows;
        }

        @Override
        public float w() {
            return this.w;
        }

        @Override
        public float h() {
            return this.h;
        }
    }

    private static KbData measureKeybinds(float s) {
        List<KbRow> rows = new ArrayList<>();
        for (Module module : ModuleManager.all()) {
            if (module.keyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                rows.add(new KbRow(module.name(), module.keyLabel(), module.isEnabled()));
            }
        }
        if (rows.isEmpty()) {
            return null;
        }
        rows.sort(Comparator.comparing(r -> r.name));

        HFont title = sTitle(s);
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float headerW = Style.HUD_TITLE_X * s + title.width("Keybinds") + Style.HUD_PAD * s;
        float rowsW = 0.0f;
        for (KbRow row : rows) {
            float badgeW = label.width(row.keyLabel) + Style.HUD_BADGE_PAD * 2.0f * s;
            float rowW = 23.0f * s + body.width(row.name) + 8.0f * s + badgeW
                    + Style.HUD_PAD * s;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float rowH = Style.HUD_ROW_H * s;
        float h = Style.HUD_HEADER_H * s + rows.size() * rowH + Style.HUD_BOTTOM_PAD * s;
        return new KbData(w, h, rows);
    }

    /** Рисует карточку кейбиндов в origin (0,0) с ручным масштабом. */
    private static void drawKeybinds(GuiGraphicsExtractor gg, float s, KbData d) {
        float w = d.w;
        HFont body = sBody(s);
        HFont label = sLabel(s);

        Render2D.round(gg, 0, 0, w, d.h, Style.HUD_CARD_R * s, Style.HUD_PANEL);
        drawCardHeader(gg, "keyboard", "Keybinds", s);

        float rowH = Style.HUD_ROW_H * s;
        float ry = Style.HUD_HEADER_H * s;
        for (KbRow row : d.rows) {
            if (row.enabled) {
                Render2D.round(gg, Style.HUD_PAD * s,
                        ry + (rowH - Style.HUD_STUB_H * s) / 2.0f,
                        Style.HUD_STUB_W * s, Style.HUD_STUB_H * s, 1.0f * s,
                        Style.accent());
            }
            body.draw(gg, row.name, 23.0f * s,
                    body.centeredBaseline(ry, rowH),
                    row.enabled ? Style.WHITE : Style.WHITE_45);

            float badgeW = label.width(row.keyLabel) + Style.HUD_BADGE_PAD * 2.0f * s;
            float bx = w - Style.HUD_PAD * s - badgeW;
            float by = ry + (rowH - Style.HUD_BADGE_H * s) / 2.0f;
            Render2D.round(gg, bx, by, badgeW, Style.HUD_BADGE_H * s,
                    Style.HUD_BADGE_R * s,
                    row.enabled ? Render2D.withAlpha(Style.accent(), 0.22f) : Style.WHITE_04);
            label.drawCentered(gg, row.keyLabel, bx + badgeW / 2.0f,
                    label.centeredBaseline(by, Style.HUD_BADGE_H * s),
                    row.enabled ? Style.WHITE : Style.WHITE_45);
            ry += rowH;
        }
    }

    private static void drawCardHeader(GuiGraphicsExtractor gg, String icon,
                                       String titleText, float s) {
        float iconS = Style.HUD_ICON * s;
        Render2D.icon(gg, icon, Style.HUD_ICON_X * s, Style.HUD_ICON_Y * s,
                iconS, iconS, Style.WHITE_45);
        sTitle(s).draw(gg, titleText, Style.HUD_TITLE_X * s,
                Style.HUD_TITLE_BASELINE * s, Style.WHITE);
    }

    // ---------------- эффекты ----------------

    private static final class FxRow {
        final Holder<MobEffect> holder;
        final String name;
        final String duration;

        FxRow(Holder<MobEffect> holder, String name, String duration) {
            this.holder = holder;
            this.name = name;
            this.duration = duration;
        }
    }

    private static final class FxData implements Sized {
        final float w;
        final float h;
        final List<FxRow> rows;

        FxData(float w, float h, List<FxRow> rows) {
            this.w = w;
            this.h = h;
            this.rows = rows;
        }

        @Override
        public float w() {
            return this.w;
        }

        @Override
        public float h() {
            return this.h;
        }
    }

    private static FxData measureEffects(float s) {
        Minecraft mc = Minecraft.getInstance();
        List<FxRow> rows = new ArrayList<>();
        if (mc.player != null) {
            List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
            effects.removeIf(instance -> !instance.showIcon());
            int count = Math.min(8, effects.size());
            for (int i = 0; i < count; i++) {
                MobEffectInstance instance = effects.get(i);
                rows.add(new FxRow(instance.getEffect(), effectName(instance),
                        formatDuration(instance.getDuration())));
            }
        }
        if (rows.isEmpty()) {
            return null;
        }

        HFont title = sTitle(s);
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float rowH = 22.0f * s;
        float headerW = Style.HUD_TITLE_X * s + title.width("Effects") + Style.HUD_PAD * s;
        float rowsW = 0.0f;
        for (FxRow row : rows) {
            float rowW = Style.HUD_PAD * s + 14.0f * s + 6.0f * s
                    + body.width(row.name)
                    + 8.0f * s + label.width(row.duration)
                    + Style.HUD_PAD * s;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float h = Style.HUD_HEADER_H * s + rows.size() * rowH + Style.HUD_BOTTOM_PAD * s;
        return new FxData(w, h, rows);
    }

    /** Рисует карточку эффектов в origin (0,0) с ручным масштабом. */
    private static void drawEffects(GuiGraphicsExtractor gg, float s, FxData d) {
        HFont title = sTitle(s);
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float rowH = 22.0f * s;
        float iconS = 14.0f * s;
        Render2D.round(gg, 0, 0, d.w, d.h, Style.HUD_CARD_R * s, Style.HUD_PANEL);
        Render2D.texture(gg, TexCache.flask((int) (Style.HUD_ICON * s)),
                Style.HUD_ICON_X * s, Style.HUD_ICON_Y * s,
                Style.HUD_ICON * s, Style.HUD_ICON * s, Style.WHITE_45);
        title.draw(gg, "Effects", Style.HUD_TITLE_X * s,
                Style.HUD_TITLE_BASELINE * s, Style.WHITE);

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        float ry = Style.HUD_HEADER_H * s;
        for (FxRow row : d.rows) {
            float spriteY = ry + (rowH - iconS) / 2.0f;
            if (row.holder != null) {
                gg.blitSprite(pipeline, Hud.getMobEffectSprite(row.holder),
                        (int) (Style.HUD_PAD * s), (int) spriteY,
                        (int) iconS, (int) iconS);
            } else {
                Render2D.texture(gg, TexCache.flask((int) iconS),
                        Style.HUD_PAD * s, spriteY, iconS, iconS, Style.WHITE);
            }

            float baseline = body.centeredBaseline(ry, rowH);
            body.draw(gg, row.name, Style.HUD_PAD * s + 20.0f * s,
                    baseline, Style.WHITE);
            label.drawRight(gg, row.duration,
                    d.w - Style.HUD_PAD * s, label.centeredBaseline(ry, rowH),
                    Style.WHITE_45);
            ry += rowH;
        }
    }

    private static final Map<String, String> EFFECT_NAMES = new java.util.HashMap<>();

    /** Имя эффекта резолвится через translation-систему — кэшируем. */
    private static String effectName(MobEffectInstance instance) {
        String key = instance.getEffect().value().getDescriptionId()
                + "#" + instance.getAmplifier();
        String cached = EFFECT_NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String name = Component.translatable(
                instance.getEffect().value().getDescriptionId()).getString();
        int amp = instance.getAmplifier();
        if (amp > 0) {
            name += " " + toRoman(amp + 1);
        }
        if (EFFECT_NAMES.size() < 256) {
            EFFECT_NAMES.put(key, name);
        }
        return name;
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0 || ticks > 1_000_000) {
            return "∞";
        }
        int total = ticks / 20;
        int mm = total / 60;
        int ss = total % 60;
        return mm + ":" + (ss < 10 ? "0" : "") + ss;
    }

    private static String toRoman(int n) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n <= 10) {
            return romans[n - 1];
        }
        return String.valueOf(n);
    }

    // ---------------- кастомный хотбар ----------------

    private static final class HbData implements Sized {
        final float w;
        final float h;

        HbData(float w, float h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public float w() {
            return this.w;
        }

        @Override
        public float h() {
            return this.h;
        }
    }

    private static HbData measureHotbar(float s) {
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        return new HbData(slot * 9.0f + gap * 8.0f, slot);
    }

    /** Рисует хотбар в origin (0,0) с ручным масштабом. */
    private static void drawHotbar(GuiGraphicsExtractor gg, float s, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();

        float slot = 22.0f * s;
        float gap = 2.0f * s;

        for (int i = 0; i < 9; i++) {
            float sx = i * (slot + gap);
            drawSlot(gg, mc, inv.getItem(i), sx, 0, slot, s, i == inv.getSelectedSlot());
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            drawSlot(gg, mc, offhand, -slot - 6.0f * s, 0, slot, s, false);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor gg, Minecraft mc, ItemStack stack,
                                 float x, float y, float slot, float s, boolean selected) {
        if (selected) {
            Render2D.round(gg, x - 2.0f * s, y - 2.0f * s,
                    slot + 4.0f * s, slot + 4.0f * s, 8.0f * s,
                    Render2D.withAlpha(Style.accent(), 0.9f));
        }
        Render2D.round(gg, x, y, slot, slot, 6.0f * s, Style.HUD_PANEL);
        if (stack.isEmpty()) {
            return;
        }
        // Предмет рендерится движком в 16px: масштабируем позой (векторно, чётко).
        Render2D.pushTranslate(gg, x + 3.0f * s, y + 3.0f * s);
        Render2D.pushScale(gg, 0.0f, 0.0f, s);
        gg.item(mc.player, stack, 0, 0, 0);
        gg.itemDecorations(mc.font, stack, 0, 0);
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);
    }

    // ---------------- медиа 2D ----------------

    /**
     * Границы 2D-медиа в пикселях экрана {x, y, w, h} или null.
     * Позиция/размер берутся из настроек (проценты/пиксели).
     */
    public static float[] media2DBounds() {
        MediaClip clip = MediaManager.get(Interface.mediaFile());
        if (clip == null) {
            return null;
        }
        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        float h = (float) Interface.mediaSize();
        float w = h * frame.width() / (float) frame.height();
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();
        float x = sw * (float) Interface.mediaX() / 100.0f - w / 2.0f;
        float y = sh * (float) Interface.mediaY() / 100.0f - h / 2.0f;
        return new float[]{x, y, w, h};
    }

    private static void placeMedia2D(GuiGraphicsExtractor gg) {
        if (!Interface.showMedia2D()) {
            return;
        }
        float[] b = media2DBounds();
        if (b == null) {
            return;
        }
        MediaClip clip = MediaManager.get(Interface.mediaFile());
        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        Render2D.texture(gg, frame.texture(), b[0], b[1], b[2], b[3], Style.WHITE);
    }
}
