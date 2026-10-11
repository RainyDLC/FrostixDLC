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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Отрисовка HUD модуля Interface в стилистике ClickGUI
 * (константы Style.HUD_* / Style.WM_*): ватермарка, кейбинды
 * функций, активные эффекты, кастомный хотбар и 2D-медиа.
 *
 * Каждый элемент измеряется (measure*), затем рисуется в origin (0,0),
 * а {@link #place} ставит его на позицию из {@link HudLayout}
 * (или на якорь по умолчанию) с масштабом. Границы последнего кадра
 * доступны через {@link #boundsSnapshot()} для редактора HUD.
 */
public final class InterfaceHud {
    private static boolean hooked;
    /** true, пока открыт редактор HUD: игровой хук не рисует, рисует редактор. */
    private static boolean editMode;

    /** Последний кадр: элемент -> {x, y, w, h} в GUI-пикселях. */
    private static final Map<HudLayout.Element, float[]> BOUNDS =
            new EnumMap<>(HudLayout.Element.class);

    public record Size(float w, float h) {
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

    public static void setEditMode(boolean edit) {
        editMode = edit;
    }

    /** Копия границ элементов последнего отрисованного кадра. */
    public static Map<HudLayout.Element, float[]> boundsSnapshot() {
        return new EnumMap<>(BOUNDS);
    }

    // ---------------- точка входа ----------------

    private static void render(GuiGraphicsExtractor gg) {
        if (editMode) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (Interface.instance() == null || !Interface.instance().isEnabled()) {
            return;
        }
        renderAll(gg);
    }

    /** Отрисовка для редактора HUD (вызывается из HudEditScreen). */
    public static void renderEditor(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            BOUNDS.clear();
            return;
        }
        renderAll(gg);
    }

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
        placeMedia2D(gg, sw, sh);
        Render2D.end(gg);
    }

    /**
     * Ставит элемент на позицию из HudLayout (или якорь по умолчанию),
     * применяет масштаб и рисует. Замер выполняется до позиционирования,
     * чтобы якоря справа/снизу знали размер.
     */
    private static void place(GuiGraphicsExtractor gg, HudLayout.Element el,
                              boolean visible, int sw, int sh,
                              Supplier<Size> measure, Consumer<GuiGraphicsExtractor> draw) {
        if (!visible) {
            return;
        }
        Size s = measure.get();
        if (s == null || s.w() <= 0 || s.h() <= 0) {
            return;
        }
        HudLayout.Entry e = HudLayout.get(el);
        float[] anchor = anchor(el, s, e.scale, sw, sh);
        float x = e.positioned() ? e.x : anchor[0];
        float y = e.positioned() ? e.y : anchor[1];

        Render2D.pushTranslate(gg, x, y);
        Render2D.pushScale(gg, 0.0f, 0.0f, e.scale);
        draw.accept(gg);
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);

        BOUNDS.put(el, new float[]{x, y, s.w() * e.scale, s.h() * e.scale});
    }

    /** Якорь по умолчанию для элемента (размер уже с учётом масштаба). */
    private static float[] anchor(HudLayout.Element el, Size s, float scale, int sw, int sh) {
        float m = Style.HUD_MARGIN;
        float w = s.w() * scale;
        float h = s.h() * scale;
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

    private record WmData(float w, float h, String fps, String pingS) {
    }

    private static Size measureWatermark() {
        WmData d = measureWatermarkData();
        return new Size(d.w(), d.h());
    }

    private static WmData measureWatermarkData() {
        Minecraft mc = Minecraft.getInstance();
        HFont title = Fonts.title();
        HFont label = Fonts.label();
        float h = Style.WM_H;

        String name1 = "Rainy";
        String name2 = "DLC";
        float nameW = title.width(name1) + title.width(name2);

        String fps = mc.getFps() + " FPS";
        float fpsW = label.width(fps);

        int ping = ping();
        String pingS = ping >= 0 ? ping + " ms" : null;
        float pingW = pingS == null ? 0.0f : label.width(pingS);

        float logoS = 16.0f;
        float iconS = 11.0f;
        float w = Style.WM_PAD + logoS + 8.0f + nameW
                + Style.WM_SEP_GAP + Style.WM_SEP_W + Style.WM_SEP_GAP
                + iconS + Style.WM_UNIT_GAP + fpsW;
        if (pingS != null) {
            w += Style.WM_SEP_GAP + Style.WM_SEP_W + Style.WM_SEP_GAP
                    + iconS + Style.WM_UNIT_GAP + pingW;
        }
        w += Style.WM_PAD;
        return new WmData(w, h, fps, pingS);
    }

    /** Рисует ватермарку в origin (0,0). */
    private static void drawWatermark(GuiGraphicsExtractor gg) {
        WmData d = measureWatermarkData();
        float w = d.w();
        float h = d.h();
        HFont title = Fonts.title();
        HFont label = Fonts.label();

        Render2D.round(gg, 0, 0, w, h, Style.WM_R, Style.HUD_PANEL);

        float logoS = 16.0f;
        float iconS = 11.0f;
        float cx = Style.WM_PAD;
        Render2D.icon(gg, "logo", cx, (h - logoS) / 2.0f, logoS, logoS, Style.WHITE);
        cx += logoS + 8.0f;

        float baseline = Style.WM_BASELINE;
        cx = title.draw(gg, "Rainy", cx, baseline, Style.WHITE);
        cx = title.draw(gg, "DLC", cx, baseline, Style.accent());

        float unitBaseline = label.centeredBaseline(0, h);
        cx = separator(gg, cx, h);
        cx = unit(gg, "fps", d.fps(), cx, h, unitBaseline);

        if (d.pingS() != null) {
            cx = separator(gg, cx, h);
            unit(gg, "ping", d.pingS(), cx, h, unitBaseline);
        }
    }

    private static float separator(GuiGraphicsExtractor gg, float cx, float h) {
        cx += Style.WM_SEP_GAP;
        Render2D.rect(gg, cx, (h - Style.WM_SEP_H) / 2.0f,
                Style.WM_SEP_W, Style.WM_SEP_H, Style.WHITE_08);
        return cx + Style.WM_SEP_W + Style.WM_SEP_GAP;
    }

    private static float unit(GuiGraphicsExtractor gg, String icon, String text,
                              float cx, float h, float baseline) {
        float iconS = 11.0f;
        HFont label = Fonts.label();
        Render2D.icon(gg, icon, cx, (h - iconS) / 2.0f, iconS, iconS, Style.WHITE_45);
        cx += iconS + Style.WM_UNIT_GAP;
        float textW = label.width(text);
        label.draw(gg, text, cx, baseline, Style.WHITE);
        return cx + textW;
    }

    private static int ping() {
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

    private static final class KbData {
        final float w;
        final float h;
        final List<Module> bound;

        KbData(float w, float h, List<Module> bound) {
            this.w = w;
            this.h = h;
            this.bound = bound;
        }
    }

    private static Size measureKeybinds() {
        KbData d = measureKeybindsData();
        return d == null ? null : new Size(d.w, d.h);
    }

    private static KbData measureKeybindsData() {
        List<Module> bound = new ArrayList<>();
        for (Module module : ModuleManager.all()) {
            if (module.keyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                bound.add(module);
            }
        }
        if (bound.isEmpty()) {
            return null;
        }
        bound.sort(Comparator.comparing(Module::name));

        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        float headerW = Style.HUD_TITLE_X + title.width("Keybinds") + Style.HUD_PAD;
        float rowsW = 0.0f;
        for (Module module : bound) {
            float badgeW = label.width(module.keyLabel()) + Style.HUD_BADGE_PAD * 2.0f;
            float rowW = 23.0f + body.width(module.name()) + 8.0f + badgeW + Style.HUD_PAD;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W, Math.max(headerW, rowsW));
        float h = Style.HUD_HEADER_H + bound.size() * Style.HUD_ROW_H + Style.HUD_BOTTOM_PAD;
        return new KbData(w, h, bound);
    }

    /** Рисует карточку кейбиндов в origin (0,0). */
    private static void drawKeybinds(GuiGraphicsExtractor gg) {
        KbData d = measureKeybindsData();
        if (d == null) {
            return;
        }
        float w = d.w;
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        Render2D.round(gg, 0, 0, w, d.h, Style.HUD_CARD_R, Style.HUD_PANEL);
        drawCardHeader(gg, "keyboard", "Keybinds");

        float rowH = Style.HUD_ROW_H;
        float ry = Style.HUD_HEADER_H;
        for (Module module : d.bound) {
            boolean enabled = module.isEnabled();
            if (enabled) {
                Render2D.round(gg, Style.HUD_PAD, ry + (rowH - Style.HUD_STUB_H) / 2.0f,
                        Style.HUD_STUB_W, Style.HUD_STUB_H, 1.0f, Style.accent());
            }
            body.draw(gg, module.name(), 23.0f,
                    body.centeredBaseline(ry, rowH),
                    enabled ? Style.WHITE : Style.WHITE_45);

            String key = module.keyLabel();
            float badgeW = label.width(key) + Style.HUD_BADGE_PAD * 2.0f;
            float bx = w - Style.HUD_PAD - badgeW;
            float by = ry + (rowH - Style.HUD_BADGE_H) / 2.0f;
            Render2D.round(gg, bx, by, badgeW, Style.HUD_BADGE_H, Style.HUD_BADGE_R,
                    enabled ? Render2D.withAlpha(Style.accent(), 0.22f) : Style.WHITE_04);
            label.drawCentered(gg, key, bx + badgeW / 2.0f,
                    label.centeredBaseline(by, Style.HUD_BADGE_H),
                    enabled ? Style.WHITE : Style.WHITE_45);
            ry += rowH;
        }
    }

    private static void drawCardHeader(GuiGraphicsExtractor gg, String icon, String titleText) {
        Render2D.icon(gg, icon, Style.HUD_ICON_X, Style.HUD_ICON_Y,
                Style.HUD_ICON, Style.HUD_ICON, Style.WHITE_45);
        Fonts.title().draw(gg, titleText, Style.HUD_TITLE_X,
                Style.HUD_TITLE_BASELINE, Style.WHITE);
    }

    // ---------------- эффекты ----------------

    private static final class FxData {
        final float w;
        final float h;
        final List<MobEffectInstance> shown;

        FxData(float w, float h, List<MobEffectInstance> shown) {
            this.w = w;
            this.h = h;
            this.shown = shown;
        }
    }

    private static Size measureEffects() {
        FxData d = measureEffectsData();
        return d == null ? null : new Size(d.w, d.h);
    }

    private static FxData measureEffectsData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.removeIf(instance -> !instance.showIcon());
        if (effects.isEmpty()) {
            return null;
        }

        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        float rowH = 22.0f;
        float headerW = Style.HUD_TITLE_X + title.width("Effects") + Style.HUD_PAD;
        float rowsW = 0.0f;
        int count = Math.min(8, effects.size());
        for (int i = 0; i < count; i++) {
            MobEffectInstance instance = effects.get(i);
            float rowW = Style.HUD_PAD + 14.0f + 6.0f + body.width(effectName(instance))
                    + 8.0f + label.width(formatDuration(instance.getDuration()))
                    + Style.HUD_PAD;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W, Math.max(headerW, rowsW));
        float h = Style.HUD_HEADER_H + count * rowH + Style.HUD_BOTTOM_PAD;
        return new FxData(w, h, effects.subList(0, count));
    }

    /** Рисует карточку эффектов в origin (0,0). */
    private static void drawEffects(GuiGraphicsExtractor gg) {
        FxData d = measureEffectsData();
        if (d == null) {
            return;
        }
        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        float rowH = 22.0f;
        Render2D.round(gg, 0, 0, d.w, d.h, Style.HUD_CARD_R, Style.HUD_PANEL);
        Render2D.texture(gg, TexCache.flask(Style.HUD_ICON),
                Style.HUD_ICON_X, Style.HUD_ICON_Y,
                Style.HUD_ICON, Style.HUD_ICON, Style.WHITE_45);
        title.draw(gg, "Effects", Style.HUD_TITLE_X,
                Style.HUD_TITLE_BASELINE, Style.WHITE);

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        float ry = Style.HUD_HEADER_H;
        for (MobEffectInstance instance : d.shown) {
            Holder<MobEffect> holder = instance.getEffect();

            Render2D.pushTranslate(gg, Style.HUD_PAD, ry + (rowH - 14.0f) / 2.0f);
            gg.blitSprite(pipeline, Hud.getMobEffectSprite(holder), 0, 0, 14, 14);
            Render2D.popTransform(gg);

            float baseline = body.centeredBaseline(ry, rowH);
            body.draw(gg, effectName(instance), Style.HUD_PAD + 20.0f,
                    baseline, Style.WHITE);
            label.drawRight(gg, formatDuration(instance.getDuration()),
                    d.w - Style.HUD_PAD, label.centeredBaseline(ry, rowH), Style.WHITE_45);
            ry += rowH;
        }
    }

    private static String effectName(MobEffectInstance instance) {
        String name = Component.translatable(
                instance.getEffect().value().getDescriptionId()).getString();
        int amp = instance.getAmplifier();
        if (amp > 0) {
            name += " " + toRoman(amp + 1);
        }
        return name;
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0 || ticks > 1_000_000) {
            return "∞";
        }
        int s = ticks / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String toRoman(int n) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n <= 10) {
            return romans[n - 1];
        }
        return String.valueOf(n);
    }

    // ---------------- кастомный хотбар ----------------

    private static Size measureHotbar() {
        float slot = 22.0f;
        float gap = 2.0f;
        return new Size(slot * 9.0f + gap * 8.0f, slot);
    }

    /** Рисует хотбар в origin (0,0). */
    private static void drawHotbar(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();

        float slot = 22.0f;
        float gap = 2.0f;

        for (int i = 0; i < 9; i++) {
            float sx = i * (slot + gap);
            drawSlot(gg, mc, inv.getItem(i), sx, 0, slot, i == inv.getSelectedSlot());
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            drawSlot(gg, mc, offhand, -slot - 6.0f, 0, slot, false);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor gg, Minecraft mc, ItemStack stack,
                                 float x, float y, float slot, boolean selected) {
        if (selected) {
            Render2D.round(gg, x - 2.0f, y - 2.0f, slot + 4.0f, slot + 4.0f, 8.0f,
                    Render2D.withAlpha(Style.accent(), 0.9f));
        }
        Render2D.round(gg, x, y, slot, slot, 6.0f, Style.HUD_PANEL);
        if (stack.isEmpty()) {
            return;
        }
        Render2D.pushTranslate(gg, x + 3.0f, y + 3.0f);
        gg.item(mc.player, stack, 0, 0, 0);
        gg.itemDecorations(mc.font, stack, 0, 0);
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

    private static void placeMedia2D(GuiGraphicsExtractor gg, int sw, int sh) {
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
