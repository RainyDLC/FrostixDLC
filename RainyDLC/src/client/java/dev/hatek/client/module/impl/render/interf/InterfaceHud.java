package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.mixin.accessor.BossHealthOverlayAccessor;
import dev.hatek.mixin.accessor.GuiAccessor;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Отрисовка HUD модуля Interface в стилистике ClickGUI:
 * тёмные карточки (HUD_PANEL, радиус как у карточек GUI), стеклянная кромка
 * сверху (TexCache.glassRim), чипы с иконками в акценте в шапках,
 * пилюли-счётчики, бары в стиле слайдеров GUI.
 *
 * Элементы: ватермарка-пилюля, кейбинды функций, активные эффекты,
 * кастомный хотбар, своя броня, цель (таргет ауры / прицел) и 2D-медиа.
 *
 * Масштаб применяется НЕ трансформацией позы (так текст из атласа
 * становился бы пиксельным), а честно: геометрия умножается на scale,
 * а текст рисуется шрифтом, запечённым в нужном размере
 * (Fonts.of кэширует по размеру, шаг квантования 0.1).
 *
 * Каждый элемент измеряется (measure*), затем рисуется в origin (0,0),
 * а {@link #place} ставит его на позицию из {@link HudLayout}
 * (или на якорь по умолчанию). Границы последнего кадра
 * доступны через {@link #boundsSnapshot()} для свободного перемещения.
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

    /** Результат хит-теста: элемент (null = 2D-медиа) и его границы. */
    public static final class Hit {
        public final HudLayout.Element element;
        public final boolean media;
        public final float[] bounds;

        Hit(HudLayout.Element element, boolean media, float[] bounds) {
            this.element = element;
            this.media = media;
            this.bounds = bounds;
        }
    }

    /**
     * Синхронный хит-тест: границы считаются в момент вызова,
     * не зависят от того, когда последний раз отрисовывался HUD.
     */
    public static Hit hitTest(double mx, double my) {
        Interface module = Interface.instance();
        if (module == null || !module.isEnabled()) {
            return null;
        }
        // Медиа рисуется последним = верхнее.
        if (Interface.showMedia2D()) {
            float[] b = media2DBounds();
            if (hit(b, mx, my)) {
                return new Hit(null, true, b);
            }
        }
        Map<HudLayout.Element, float[]> bounds = computeBounds();
        java.util.List<HudLayout.Element> order =
                new java.util.ArrayList<>(bounds.keySet());
        java.util.Collections.reverse(order);
        for (HudLayout.Element el : order) {
            float[] b = bounds.get(el);
            if (hit(b, mx, my)) {
                return new Hit(el, false, b);
            }
        }
        return null;
    }

    /** Свежие границы всех видимых элементов (замер без отрисовки). */
    public static Map<HudLayout.Element, float[]> computeBounds() {
        Map<HudLayout.Element, float[]> out = new EnumMap<>(HudLayout.Element.class);
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();
        putBound(out, HudLayout.Element.WATERMARK, Interface.showWatermark(), sw, sh,
                InterfaceHud::measureWatermark);
        putBound(out, HudLayout.Element.KEYBINDS, Interface.showKeybinds(), sw, sh,
                InterfaceHud::measureKeybinds);
        putBound(out, HudLayout.Element.EFFECTS, Interface.showEffects(), sw, sh,
                InterfaceHud::measureEffects);
        putBound(out, HudLayout.Element.HOTBAR, Interface.showHotbar(), sw, sh,
                InterfaceHud::measureHotbar);
        putBound(out, HudLayout.Element.ARMOR, Interface.showArmor(), sw, sh,
                InterfaceHud::measureArmor);
        putBound(out, HudLayout.Element.TARGET, Interface.showTarget(), sw, sh,
                InterfaceHud::measureTarget);
        return out;
    }

    private static <D extends Sized> void putBound(Map<HudLayout.Element, float[]> out,
                                                   HudLayout.Element el, boolean visible,
                                                   int sw, int sh,
                                                   Function<Float, D> measure) {
        if (!visible) {
            return;
        }
        HudLayout.Entry e = HudLayout.get(el);
        D data = measure.apply(e.scale * Render2D.guiScale());
        if (data == null || data.w() <= 0 || data.h() <= 0) {
            return;
        }
        float[] p = resolvePos(el, e, data.w(), data.h(), sw, sh);
        out.put(el, new float[]{p[0], p[1], data.w(), data.h()});
    }

    /** Применить перемещение хита (с клэмпом). x/y — новый левый верхний угол. */
    public static void moveHit(Hit hit, float x, float y) {
        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();
        float w = hit.bounds[2];
        float h = hit.bounds[3];
        x = Math.max(-w + 24.0f, Math.min(sw - 24.0f, x));
        y = Math.max(-h + 24.0f, Math.min(sh - 24.0f, y));
        if (hit.media) {
            Interface.setMediaGeometry(Interface.mediaSize(),
                    (x + w / 2.0f) / sw * 100.0, (y + h / 2.0f) / sh * 100.0);
        } else {
            HudLayout.setPos(hit.element, x, y);
        }
    }

    /** Масштабировать хит колесом. factor > 1 — больше. */
    public static void resizeHit(Hit hit, float factor) {
        if (hit.media) {
            double size = Interface.mediaSize() * factor;
            Interface.setMediaGeometry(size, Interface.mediaX(), Interface.mediaY());
        } else {
            HudLayout.setScale(hit.element, HudLayout.get(hit.element).scale * factor);
        }
    }

    private static boolean hit(float[] b, double mx, double my) {
        return b != null && mx >= b[0] && mx <= b[0] + b[2]
                && my >= b[1] && my <= b[1] + b[3];
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

    // ---- перетаскивание прямо в игре (миксин мыши, экран открыт) ----
    private static Hit dragHit;
    private static float dragGrabX;
    private static float dragGrabY;

    /** Начать drag по координатам мыши. true — попали по элементу HUD. */
    public static boolean startDrag(double mx, double my) {
        if (dragHit != null) {
            return true;
        }
        Hit hit = hitTest(mx, my);
        if (hit == null) {
            return false;
        }
        dragHit = hit;
        dragGrabX = (float) mx - hit.bounds[0];
        dragGrabY = (float) my - hit.bounds[1];
        return true;
    }

    /** Закончить drag (позиции сохраняются). */
    public static void stopDrag() {
        if (dragHit != null) {
            dragHit = null;
            HudLayout.save();
        }
    }

    /** Каждый кадр тянет элемент за курсором; вызывается из renderAll. */
    private static void tickDrag() {
        if (dragHit == null) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() == null) {
            stopDrag();
            return;
        }
        moveHit(dragHit,
                (float) Render2D.mouseX() - dragGrabX,
                (float) Render2D.mouseY() - dragGrabY);
    }

    /** Отрисовка всех элементов (и в игре, и за ClickGUI). */
    private static void renderAll(GuiGraphicsExtractor gg) {
        tickDrag();
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
        place(gg, HudLayout.Element.ARMOR, Interface.showArmor(), sw, sh,
                InterfaceHud::measureArmor, InterfaceHud::drawArmor);
        place(gg, HudLayout.Element.TARGET, Interface.showTarget(), sw, sh,
                InterfaceHud::measureTarget, InterfaceHud::drawTarget);
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
        // Размер в GUI-единицах: умножаем на guiScale, иначе на большом
        // GUI-скейле элементы выглядят мельче ванильных (хотбар!).
        // Позиции остаются в сырых пикселях (мышь), их begin() переводит сам.
        float s = e.scale * Render2D.guiScale();
        D data = measure.apply(s);
        if (data == null || data.w() <= 0 || data.h() <= 0) {
            return;
        }
        float[] p = resolvePos(el, e, data.w(), data.h(), sw, sh);
        float x = p[0];
        float y = p[1];

        Render2D.pushTranslate(gg, x, y);
        drawer.draw(gg, s, data);
        Render2D.popTransform(gg);

        BOUNDS.put(el, new float[]{x, y, data.w(), data.h()});
    }

    /** Позиция элемента: из HudLayout или якорь по умолчанию. */
    private static float[] resolvePos(HudLayout.Element el, HudLayout.Entry e,
                                      float w, float h, int sw, int sh) {
        float[] a = anchor(el, w, h, sw, sh);
        return new float[]{e.positioned() ? e.x : a[0], e.positioned() ? e.y : a[1]};
    }

    /** Якорь по умолчанию для элемента (размер уже с учётом масштаба). */
    private static String lastWatermarkPos;
    /** Сглажённый Y ватермарки по центру (уезд под босс-бар). <0 = не инициализирован. */
    private static float watermarkBossY = -1.0f;

    /** Сколько босс-баров сейчас показано. */
    private static int bossBarCount() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui == null) {
                return 0;
            }
            Hud hud = ((GuiAccessor) mc.gui).hatek$hud();
            if (hud == null) {
                return 0;
            }
            BossHealthOverlay overlay = hud.getBossOverlay();
            if (overlay instanceof BossHealthOverlayAccessor acc) {
                java.util.Map events = acc.hatek$events();
                return events == null ? 0 : events.size();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static float[] anchor(HudLayout.Element el, float w, float h, int sw, int sh) {
        float m = Style.HUD_MARGIN;
        switch (el) {
            case WATERMARK: {
                // Настройка позиции: при смене сбрасываем ручное перетаскивание,
                // чтобы якорь применился сразу. Перетаскивание после — вручную.
                String pos = Interface.watermarkPos();
                if (!pos.equals(lastWatermarkPos)) {
                    lastWatermarkPos = pos;
                    HudLayout.clearPos(HudLayout.Element.WATERMARK);
                    watermarkBossY = -1.0f;
                }
                float x = m;
                float y = m;
                if ("Center".equalsIgnoreCase(pos)) {
                    x = (sw - w) / 2.0f;
                    // Босс-бар сверху по центру: ватермарка уезжает под него,
                    // а когда бар исчезает — плавно поднимается наверх.
                    int bosses = bossBarCount();
                    float targetY = bosses > 0
                            ? (12.0f + 19.0f * bosses) * Render2D.guiScale()
                            : m;
                    if (watermarkBossY < 0.0f) {
                        watermarkBossY = targetY;
                    }
                    watermarkBossY += (targetY - watermarkBossY) * 0.18f;
                    y = watermarkBossY;
                } else {
                    if ("Right".equalsIgnoreCase(pos)) {
                        x = sw - w - m;
                    }
                    watermarkBossY = -1.0f;
                }
                return new float[]{x, y};
            }
            case KEYBINDS:
                return new float[]{sw - w - m, m};
            case EFFECTS: {
                float[] kb = BOUNDS.get(HudLayout.Element.KEYBINDS);
                float top = (kb != null) ? kb[1] + kb[3] + 8.0f : m;
                return new float[]{sw - w - m, top};
            }
            case ARMOR:
                return new float[]{m, sh - h - 8.0f};
            case TARGET:
                return new float[]{(sw - w) / 2.0f, m};
            case HOTBAR:
            default:
                return new float[]{(sw - w) / 2.0f, sh - h - 8.0f};
        }
    }

    // ---------------- общий визуальный язык (как в ClickGUI) ----------------

    /**
     * Карточка: тёмная панель + стеклянная кромка (блик сверху, глянец),
     * как у панелей ClickGUI.
     */
    private static void card(GuiGraphicsExtractor gg, float w, float h, float s) {
        float r = Style.HUD_CARD_R * s;
        Render2D.round(gg, 0, 0, w, h, r, Style.HUD_PANEL);
        Render2D.texture(gg,
                TexCache.glassRim(Math.max(1, Math.round(w)),
                        Math.max(1, Math.round(h)), r),
                0, 0, w, h, Style.WHITE);
    }

    /** Чип с векторной иконкой в шапке карточки (иконка — в акценте). */
    private static void headerChip(GuiGraphicsExtractor gg, float s, String icon) {
        float chip = Style.HUD_CHIP * s;
        float y = (Style.HUD_HEADER_H * s - chip) / 2.0f;
        Render2D.round(gg, Style.HUD_PAD * s, y, chip, chip,
                Style.HUD_CHIP_R * s, Style.WHITE_04);
        float is = Style.HUD_CHIP_ICON * s;
        Render2D.icon(gg, icon, Style.HUD_PAD * s + (chip - is) / 2.0f,
                y + (chip - is) / 2.0f, is, is, Style.accent());
    }

    /** Чип с растровой текстурой в шапке (флакон эффектов). */
    private static void headerChipTex(GuiGraphicsExtractor gg, float s, Identifier tex) {
        float chip = Style.HUD_CHIP * s;
        float y = (Style.HUD_HEADER_H * s - chip) / 2.0f;
        Render2D.round(gg, Style.HUD_PAD * s, y, chip, chip,
                Style.HUD_CHIP_R * s, Style.WHITE_04);
        float is = Style.HUD_CHIP_ICON * s;
        Render2D.texture(gg, tex, Style.HUD_PAD * s + (chip - is) / 2.0f,
                y + (chip - is) / 2.0f, is, is, Style.accent());
    }

    /** Пилюля-счётчик справа в шапке карточки. */
    private static void headerPill(GuiGraphicsExtractor gg, float s, float w, String text) {
        HFont label = sLabel(s);
        float pillW = label.width(text) + Style.HUD_PILL_PAD * 2.0f * s;
        float pillH = Style.HUD_PILL_H * s;
        float x = w - Style.HUD_PAD * s - pillW;
        float y = (Style.HUD_HEADER_H * s - pillH) / 2.0f;
        Render2D.round(gg, x, y, pillW, pillH, Style.HUD_PILL_R * s,
                Render2D.withAlpha(Style.accent(), 0.22f));
        label.drawCentered(gg, text, x + pillW / 2.0f,
                label.centeredBaseline(y, pillH), Style.WHITE);
    }

    /** Шапка карточки: чип + заголовок + счётчик. */
    private static void drawCardHeader(GuiGraphicsExtractor gg, String icon,
                                       String titleText, int count, float s, float w) {
        headerChip(gg, s, icon);
        HFont title = sTitle(s);
        title.draw(gg, titleText,
                Style.HUD_PAD * s + Style.HUD_CHIP * s + 8.0f * s,
                title.centeredBaseline(0, Style.HUD_HEADER_H * s), Style.WHITE);
        headerPill(gg, s, w, String.valueOf(count));
    }

    /** Тонкий бар в стиле слайдеров GUI: трек + заливка. */
    private static void bar(GuiGraphicsExtractor gg, float x, float y, float w,
                            float h, float s, float frac, int fill) {
        Render2D.round(gg, x, y, w, h, h / 2.0f, Style.WHITE_08);
        float fw = w * Math.max(0.0f, Math.min(1.0f, frac));
        if (fw > 0.5f) {
            Render2D.round(gg, x, y, fw, h, h / 2.0f, fill);
        }
    }

    /** Светящаяся акцентная точка (ватермарка, активные кейбинды). */
    private static void glowDot(GuiGraphicsExtractor gg, float cx, float cy,
                                float d, float alpha) {
        int gd = Math.max(1, Math.round(d * 3.2f));
        Render2D.texture(gg, TexCache.glow(gd), cx - gd / 2.0f, cy - gd / 2.0f,
                gd, gd, Render2D.withAlpha(Style.accent(), alpha));
        Render2D.circle(gg, cx - d / 2.0f, cy - d / 2.0f, d, Style.accent());
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
        float w = (Style.WM_PAD + Style.WM_DOT + Style.WM_NAME_GAP) * s + nameW;

        String fps = mc.getFps() + " FPS";
        float unitIcon = Style.WM_UNIT_ICON * s;
        w += sepW(s) + unitIcon + Style.WM_UNIT_GAP * s + label.width(fps);

        int ping = ping();
        String pingS = ping >= 0 ? ping + " ms" : null;
        if (pingS != null) {
            w += sepW(s) + unitIcon + Style.WM_UNIT_GAP * s + label.width(pingS);
        }
        w += Style.WM_PAD * s;
        return new WmData(w, h, fps, pingS);
    }

    /** Ширина разделителя вместе с отступами по бокам. */
    private static float sepW(float s) {
        return (Style.WM_SEP_GAP * 2.0f + Style.WM_SEP_W) * s;
    }

    /** Рисует ватермарку-пилюлю в origin (0,0) с ручным масштабом. */
    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WmData d) {
        float w = d.w();
        float h = d.h();
        float r = Style.WM_R * s;
        Render2D.round(gg, 0, 0, w, h, r, Style.HUD_PANEL);
        Render2D.texture(gg,
                TexCache.glassRim(Math.max(1, Math.round(w)),
                        Math.max(1, Math.round(h)), r),
                0, 0, w, h, Style.WHITE);

        HFont title = sTitle(s);
        HFont label = sLabel(s);

        // Пульсирующая акцентная точка вместо логотипа.
        float dot = Style.WM_DOT * s;
        float dotCx = Style.WM_PAD * s + dot / 2.0f;
        long t = System.currentTimeMillis() % 1600L;
        float pulse = 0.65f + 0.35f
                * (float) Math.sin(t / 1600.0f * Math.PI * 2.0f);
        glowDot(gg, dotCx, h / 2.0f, dot, 0.30f * pulse);

        float nameX = (Style.WM_PAD + Style.WM_DOT + Style.WM_NAME_GAP) * s;
        float baseline = title.centeredBaseline(0, h);
        float cx = title.draw(gg, "Rainy", nameX, baseline, Style.WHITE);
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
        float iconS = Style.WM_UNIT_ICON * s;
        Render2D.icon(gg, icon, cx, (h - iconS) / 2.0f, iconS, iconS, Style.WHITE_45);
        cx += iconS + Style.WM_UNIT_GAP * s;
        return sLabel(s).draw(gg, text, cx, baseline, Style.WHITE);
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

    private static final class KbData implements Sized {
        final float w;
        final float h;
        final List<Module> bound;

        KbData(float w, float h, List<Module> bound) {
            this.w = w;
            this.h = h;
            this.bound = bound;
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

        HFont title = sTitle(s);
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float headerW = Style.HUD_PAD * s + Style.HUD_CHIP * s + 8.0f * s
                + title.width("Keybinds") + Style.HUD_PAD * s;
        float rowsW = 0.0f;
        for (Module module : bound) {
            float badgeW = label.width(module.keyLabel())
                    + Style.HUD_PILL_PAD * 2.0f * s;
            float rowW = 24.0f * s + body.width(module.name()) + 8.0f * s
                    + badgeW + Style.HUD_PAD * s;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float rowH = 22.0f * s;
        float h = Style.HUD_HEADER_H * s + bound.size() * rowH
                + Style.HUD_BOTTOM_PAD * s;
        return new KbData(w, h, bound);
    }

    /** Рисует карточку кейбиндов в origin (0,0) с ручным масштабом. */
    private static void drawKeybinds(GuiGraphicsExtractor gg, float s, KbData d) {
        float w = d.w;
        HFont body = sBody(s);
        HFont label = sLabel(s);

        card(gg, w, d.h, s);
        drawCardHeader(gg, "keyboard", "Keybinds", d.bound.size(), s, w);

        float rowH = 22.0f * s;
        float ry = Style.HUD_HEADER_H * s;
        for (Module module : d.bound) {
            boolean enabled = module.isEnabled();
            float dotCx = Style.HUD_PAD * s + 2.5f * s;
            float dotCy = ry + rowH / 2.0f;
            if (enabled) {
                glowDot(gg, dotCx, dotCy, 5.0f * s, 0.45f);
            } else {
                Render2D.circle(gg, dotCx - 2.5f * s, dotCy - 2.5f * s,
                        5.0f * s, Style.WHITE_25);
            }
            body.draw(gg, module.name(), 24.0f * s,
                    body.centeredBaseline(ry, rowH),
                    enabled ? Style.WHITE : Style.WHITE_45);

            String key = module.keyLabel();
            float badgeW = label.width(key) + Style.HUD_PILL_PAD * 2.0f * s;
            float bx = w - Style.HUD_PAD * s - badgeW;
            float by = ry + (rowH - Style.HUD_PILL_H * s) / 2.0f;
            Render2D.round(gg, bx, by, badgeW, Style.HUD_PILL_H * s,
                    Style.HUD_PILL_R * s,
                    enabled ? Render2D.withAlpha(Style.accent(), 0.22f) : Style.WHITE_04);
            label.drawCentered(gg, key, bx + badgeW / 2.0f,
                    label.centeredBaseline(by, Style.HUD_PILL_H * s),
                    enabled ? Style.WHITE : Style.WHITE_45);
            ry += rowH;
        }
    }

    // ---------------- эффекты ----------------

    private static final class FxData implements Sized {
        final float w;
        final float h;
        final List<MobEffectInstance> shown;
        final List<Float> fracs; // доля оставшейся длительности (для баров)

        FxData(float w, float h, List<MobEffectInstance> shown, List<Float> fracs) {
            this.w = w;
            this.h = h;
            this.shown = shown;
            this.fracs = fracs;
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

    /** Замеченные максимумы длительности эффектов (для баров убывания). */
    private static final Map<String, Integer> FX_MAX = new java.util.HashMap<>();

    private static FxData measureEffects(float s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.removeIf(instance -> !instance.showIcon());
        if (effects.isEmpty()) {
            return null;
        }

        HFont title = sTitle(s);
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float rowH = 26.0f * s;
        float headerW = Style.HUD_PAD * s + Style.HUD_CHIP * s + 8.0f * s
                + title.width("Effects") + Style.HUD_PAD * s;
        float rowsW = 0.0f;
        int count = Math.min(8, effects.size());
        List<Float> fracs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MobEffectInstance instance = effects.get(i);
            String name = effectBaseName(instance);
            String roman = ampRoman(instance.getAmplifier());
            float nameW = body.width(name);
            float ampW = roman == null ? 0.0f
                    : label.width(roman) + 9.0f * s + 5.0f * s;
            String dur = formatDuration(instance.getDuration());
            float rowW = Style.HUD_PAD * s + 18.0f * s + 7.0f * s
                    + nameW + ampW + 10.0f * s + label.width(dur)
                    + Style.HUD_PAD * s;
            rowsW = Math.max(rowsW, rowW);
            fracs.add(effectFrac(instance));
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float h = Style.HUD_HEADER_H * s + count * rowH + 10.0f * s;
        return new FxData(w, h, effects.subList(0, count), fracs);
    }

    /** Доля оставшейся длительности: запоминаем максимум, бар убывает от него. */
    private static float effectFrac(MobEffectInstance instance) {
        int dur = instance.getDuration();
        if (dur < 0 || dur > 1_000_000) {
            return 1.0f; // бесконечный
        }
        String key = instance.getEffect().value().getDescriptionId()
                + "#" + instance.getAmplifier();
        Integer max = FX_MAX.get(key);
        if (max == null || dur > max) {
            max = dur;
            if (FX_MAX.size() > 256) {
                FX_MAX.clear();
            }
            FX_MAX.put(key, max);
        }
        return max <= 0 ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, dur / (float) max));
    }

    /** Рисует карточку эффектов в origin (0,0) с ручным масштабом. */
    private static void drawEffects(GuiGraphicsExtractor gg, float s, FxData d) {
        HFont body = sBody(s);
        HFont label = sLabel(s);

        float rowH = 26.0f * s;
        float iconS = 14.0f * s;
        card(gg, d.w, d.h, s);
        headerChipTex(gg, s, TexCache.flask(Math.max(1, Math.round(Style.HUD_CHIP_ICON * s))));
        HFont title = sTitle(s);
        title.draw(gg, "Effects",
                Style.HUD_PAD * s + Style.HUD_CHIP * s + 8.0f * s,
                title.centeredBaseline(0, Style.HUD_HEADER_H * s), Style.WHITE);
        headerPill(gg, s, d.w, String.valueOf(d.shown.size()));

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        float ry = Style.HUD_HEADER_H * s;
        for (int i = 0; i < d.shown.size(); i++) {
            MobEffectInstance instance = d.shown.get(i);
            Holder<MobEffect> holder = instance.getEffect();

            // Иконка эффекта в чипе.
            float slot = 18.0f * s;
            float slotX = Style.HUD_PAD * s;
            float slotY = ry + (rowH - slot) / 2.0f;
            Render2D.round(gg, slotX, slotY, slot, slot, 5.0f * s, Style.WHITE_04);
            gg.blitSprite(pipeline, Hud.getMobEffectSprite(holder),
                    (int) (slotX + (slot - iconS) / 2.0f),
                    (int) (slotY + (slot - iconS) / 2.0f),
                    (int) iconS, (int) iconS);

            float nameX = slotX + slot + 7.0f * s;
            float baseline = body.centeredBaseline(ry, 21.0f * s);
            String name = effectBaseName(instance);
            float nx = body.draw(gg, name, nameX, baseline, Style.WHITE);

            // Бейдж уровня эффекта.
            String roman = ampRoman(instance.getAmplifier());
            if (roman != null) {
                float bw = label.width(roman) + 9.0f * s;
                float bh = 13.0f * s;
                float bx = nx + 5.0f * s;
                float by = ry + (21.0f * s - bh) / 2.0f;
                Render2D.round(gg, bx, by, bw, bh, bh / 2.0f,
                        Render2D.withAlpha(Style.accent(), 0.16f));
                label.drawCentered(gg, roman, bx + bw / 2.0f,
                        label.centeredBaseline(by, bh), Style.accent());
            }

            label.drawRight(gg, formatDuration(instance.getDuration()),
                    d.w - Style.HUD_PAD * s,
                    label.centeredBaseline(ry, 21.0f * s), Style.WHITE_45);

            // Бар оставшейся длительности, как трек слайдера в GUI.
            bar(gg, nameX, ry + rowH - 5.0f * s, d.w - Style.HUD_PAD * s - nameX,
                    2.5f * s, s, d.fracs.get(i), Style.accent());
            ry += rowH;
        }
    }

    private static final Map<String, String> EFFECT_NAMES = new java.util.HashMap<>();

    /** Имя эффекта без уровня, резолвится через translation-систему — кэшируем. */
    private static String effectBaseName(MobEffectInstance instance) {
        String key = instance.getEffect().value().getDescriptionId();
        String cached = EFFECT_NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String name = Component.translatable(
                instance.getEffect().value().getDescriptionId()).getString();
        if (EFFECT_NAMES.size() < 256) {
            EFFECT_NAMES.put(key, name);
        }
        return name;
    }

    /** Римская цифра уровня (null — первый уровень, бейдж не нужен). */
    private static String ampRoman(int amp) {
        if (amp <= 0) {
            return null;
        }
        return toRoman(amp + 1);
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
        final float s; // эффективный масштаб (со слайдером)

        HbData(float w, float h, float s) {
            this.w = w;
            this.h = h;
            this.s = s;
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

    private static HbData measureHotbar(float scale) {
        float s = scale * (float) Interface.hotbarSize();
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        return new HbData(slot * 9.0f + gap * 8.0f, slot, s);
    }

    /** Рисует хотбар в origin (0,0) с ручным масштабом. */
    private static void drawHotbar(GuiGraphicsExtractor gg, float ignored, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        float s = data.s;

        float slot = 22.0f * s;
        float gap = 2.0f * s;

        for (int i = 0; i < 9; i++) {
            float sx = i * (slot + gap);
            drawHotSlot(gg, mc, inv.getItem(i), sx, 0, slot, s,
                    i == inv.getSelectedSlot());
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            drawHotSlot(gg, mc, offhand, -slot - 6.0f * s, 0, slot, s, false);
        }
    }

    private static void drawHotSlot(GuiGraphicsExtractor gg, Minecraft mc, ItemStack stack,
                                    float x, float y, float slot, float s, boolean selected) {
        if (selected) {
            // Мягкое акцентное свечение + кольцо, как активные элементы в GUI.
            int gd = Math.max(1, Math.round(slot + 18.0f * s));
            Render2D.texture(gg, TexCache.glow(gd),
                    x + slot / 2.0f - gd / 2.0f, y + slot / 2.0f - gd / 2.0f,
                    gd, gd, Render2D.withAlpha(Style.accent(), 0.35f));
            float ring = 2.5f * s;
            Render2D.round(gg, x - ring, y - ring, slot + ring * 2.0f,
                    slot + ring * 2.0f, 9.0f * s,
                    Render2D.withAlpha(Style.accent(), 0.85f));
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

    // ---------------- броня (своя) ----------------

    private static final class ArmorData implements Sized {
        final float w;
        final float h;
        final List<ItemStack> armor; // шлем..ботинки
        final int count; // сколько частей надето

        ArmorData(float w, float h, List<ItemStack> armor, int count) {
            this.w = w;
            this.h = h;
            this.armor = armor;
            this.count = count;
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

    /** Броня игрока: шлем..ботинки. null — нечего показывать. */
    private static ArmorData measureArmor(float s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        List<ItemStack> armor = armorPieces(mc.player);
        int count = 0;
        for (ItemStack stack : armor) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        float pad = 6.0f * s;
        float slot = 20.0f * s;
        float gap = 4.0f * s;
        float w = 4.0f * slot + 3.0f * gap + pad * 2.0f;
        float h = Style.HUD_HEADER_H * s + 2.0f * s + slot
                + 3.0f * s + 2.5f * s + 3.0f * s + 9.0f * s + pad;
        return new ArmorData(w, h, armor, count);
    }

    private static void drawArmor(GuiGraphicsExtractor gg, float s, ArmorData d) {
        float pad = 6.0f * s;
        float slot = 20.0f * s;
        float gap = 4.0f * s;
        card(gg, d.w, d.h, s);
        // Компактная шапка: чип + "Armor" + счётчик надетых частей.
        headerChip(gg, s, "player");
        HFont title = sTitle(s);
        title.draw(gg, "Armor", Style.HUD_PAD * s + Style.HUD_CHIP * s + 8.0f * s,
                title.centeredBaseline(0, Style.HUD_HEADER_H * s), Style.WHITE);
        headerPill(gg, s, d.w, String.valueOf(d.count));

        Minecraft mc = Minecraft.getInstance();
        HFont pct = sLabel(s * 0.7f);
        float top = Style.HUD_HEADER_H * s + 2.0f * s;
        for (int i = 0; i < 4; i++) {
            float x = pad + i * (slot + gap);
            drawItemSlot(gg, mc, d.armor.get(i), x, top, slot, s);
            // Бар прочности под слотом, как трек слайдера в GUI.
            bar(gg, x, top + slot + 3.0f * s, slot, 2.5f * s, s,
                    durabilityFrac(d.armor.get(i)), durabilityColor(d.armor.get(i)));
            String p = durabilityPct(d.armor.get(i));
            if (p != null) {
                float tw = pct.width(p);
                pct.draw(gg, p, x + (slot - tw) / 2.0f,
                        top + slot + 3.0f * s + 2.5f * s + 3.0f * s,
                        durabilityColor(d.armor.get(i)));
            }
        }
    }

    /** Слот под предмет: светлый чип, как слоты в карточках GUI. */
    private static void drawItemSlot(GuiGraphicsExtractor gg, Minecraft mc, ItemStack stack,
                                     float x, float y, float slot, float s) {
        Render2D.round(gg, x, y, slot, slot, 6.0f * s, Style.WHITE_04);
        if (stack.isEmpty()) {
            return;
        }
        float is = slot - 6.0f * s; // предмет с отступом
        Render2D.pushTranslate(gg, x + 3.0f * s, y + 3.0f * s);
        Render2D.pushScale(gg, 0.0f, 0.0f, is / 16.0f);
        gg.item(mc.player, stack, 0, 0, 0);
        gg.itemDecorations(mc.font, stack, 0, 0);
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);
    }

    /** Кусочки брони шлем..ботинки. */
    private static List<ItemStack> armorPieces(LivingEntity living) {
        List<ItemStack> out = new ArrayList<>(4);
        out.add(living.getItemBySlot(EquipmentSlot.HEAD));
        out.add(living.getItemBySlot(EquipmentSlot.CHEST));
        out.add(living.getItemBySlot(EquipmentSlot.LEGS));
        out.add(living.getItemBySlot(EquipmentSlot.FEET));
        return out;
    }

    private static String durabilityPct(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (stack.isEmpty() || max <= 0) {
            return null;
        }
        int pct = Math.round((max - stack.getDamageValue()) / (float) max * 100.0f);
        return pct + "%";
    }

    /** Доля оставшейся прочности (для баров). */
    private static float durabilityFrac(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (stack.isEmpty() || max <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f,
                (max - stack.getDamageValue()) / (float) max));
    }

    private static int durabilityColor(ItemStack stack) {
        int max = stack.getMaxDamage();
        float f = max <= 0 ? 1.0f : (max - stack.getDamageValue()) / (float) max;
        return f > 0.6f ? 0xFF4ADE80 : f > 0.3f ? 0xFFFACC15 : 0xFFF87171;
    }

    // ---------------- цель ----------------

    private static final class TargetData implements Sized {
        final float w;
        final float h;
        final LivingEntity target;
        final String name;
        final boolean aura; // цель взята из ауры (иначе — прицел)
        final float dist;
        final float hpFrac;
        final float abFrac;
        final String hpText;
        final List<ItemStack> armor;
        final ItemStack mainHand;
        final ItemStack offHand;

        TargetData(float w, float h, LivingEntity target, String name, boolean aura,
                   float dist, float hpFrac, float abFrac, String hpText,
                   List<ItemStack> armor, ItemStack mainHand, ItemStack offHand) {
            this.w = w;
            this.h = h;
            this.target = target;
            this.name = name;
            this.aura = aura;
            this.dist = dist;
            this.hpFrac = hpFrac;
            this.abFrac = abFrac;
            this.hpText = hpText;
            this.armor = armor;
            this.mainHand = mainHand;
            this.offHand = offHand;
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

    /** Цель: сначала таргет ауры, иначе то, на что смотрим. null — нет цели. */
    private static TargetData measureTarget(float s) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = resolveTarget(mc);
        if (t == null) {
            return null;
        }
        float maxHp = Math.max(1.0f, t.getMaxHealth());
        float hpFrac = Mth.clamp(t.getHealth() / maxHp, 0.0f, 1.0f);
        float abFrac = Mth.clamp(t.getAbsorptionAmount() / maxHp, 0.0f, 1.0f - hpFrac);
        String hpText = String.valueOf((int) Math.ceil(t.getHealth() + t.getAbsorptionAmount()));

        HFont title = sTitle(s);
        String name = title.trim(t.getName().getString(), 140.0f * s);
        boolean aura = t == AttackAura.target;
        float dist = mc.player.distanceTo(t);

        float pad = 8.0f * s;
        float head = 36.0f * s;
        float nameX = pad + head + 8.0f * s;
        float row2Y = pad + 28.0f * s;
        float icon = 18.0f * s;
        float gap = 3.0f * s;
        float armorW = 4.0f * icon + 3.0f * gap;
        float handsX = nameX + armorW + 10.0f * s;
        float handsW = 2.0f * icon + gap;
        float leftW = Math.max(handsX + handsW, nameX + title.width(name) + 8.0f * s);
        float circleD = 40.0f * s;
        float circleGap = 12.0f * s;
        float w = leftW + circleGap + circleD + pad;
        float row2Bottom = row2Y + icon + 3.0f * s + 2.0f * s;
        float h = Math.max(pad + head, row2Bottom) + pad;

        return new TargetData(w, h, t, name, aura, dist, hpFrac, abFrac, hpText,
                armorPieces(t), t.getMainHandItem(), t.getOffhandItem());
    }

    private static void drawTarget(GuiGraphicsExtractor gg, float s, TargetData d) {
        card(gg, d.w, d.h, s);
        Minecraft mc = Minecraft.getInstance();

        float pad = 8.0f * s;
        float head = 36.0f * s;

        // Голова цели: предмет PLAYER_HEAD с её профилем (настоящий скин).
        ItemStack headStack = headStack(d.target, mc);
        if (!headStack.isEmpty()) {
            drawItemSlot(gg, mc, headStack, pad, pad, head, s);
        } else {
            drawAvatar(gg, d.name, pad, pad, head, s);
        }

        // Ник + сабтайтл (дистанция, источник цели).
        HFont title = sTitle(s);
        HFont label = sLabel(s);
        float nameX = pad + head + 8.0f * s;
        title.draw(gg, d.name, nameX, pad + 3.0f * s, Style.WHITE);
        String dist = Math.round(d.dist * 10.0f) / 10.0f + "m";
        label.draw(gg, dist + (d.aura ? " · Aura" : " · Looking"),
                nameX, pad + 18.0f * s, Style.WHITE_45);

        // Броня с барами прочности + руки.
        float row2Y = pad + 28.0f * s;
        float icon = 18.0f * s;
        float gap = 3.0f * s;
        for (int i = 0; i < 4; i++) {
            float x = nameX + i * (icon + gap);
            ItemStack stack = d.armor.get(i);
            drawItemSlot(gg, mc, stack, x, row2Y, icon, s);
            bar(gg, x, row2Y + icon + 3.0f * s, icon, 2.0f * s, s,
                    durabilityFrac(stack), durabilityColor(stack));
        }
        float handsX = nameX + 4.0f * icon + 3.0f * gap + 10.0f * s;
        drawItemSlot(gg, mc, d.mainHand, handsX, row2Y, icon, s);
        drawItemSlot(gg, mc, d.offHand, handsX + icon + gap, row2Y, icon, s);

        // Круг ХП справа: свечение + гладкие дуги (фон, золото абсорбции, хп).
        float circleD = 40.0f * s;
        float circleX = d.w - pad - circleD;
        float circleY = (d.h - circleD) / 2.0f;
        float circleCx = circleX + circleD / 2.0f;
        int hpCol = hpColor(d.hpFrac);
        int gd = Math.max(1, Math.round(circleD + 18.0f * s));
        Render2D.texture(gg, TexCache.glow(gd),
                circleCx - gd / 2.0f, circleY + circleD / 2.0f - gd / 2.0f,
                gd, gd, Render2D.withAlpha(hpCol, 0.30f));
        hpArc(gg, circleX, circleY, circleD, 1.0f, false, 0xFF2A2929);
        float total = Math.min(1.0f, d.hpFrac + d.abFrac);
        hpArc(gg, circleX, circleY, circleD, total, true, 0xFFFFC94D);
        hpArc(gg, circleX, circleY, circleD, d.hpFrac, true, hpCol);
        HFont hpFont = sTitle(s * 1.05f);
        hpFont.drawCentered(gg, d.hpText, circleCx,
                hpFont.centeredBaseline(circleY, circleD), Style.WHITE);
    }

    /**
     * Гладкая дуга ХП: сглаженная текстура из TexCache, тинтится в цвет.
     * x/y/d — квадрат, в который вписана дуга.
     */
    private static void hpArc(GuiGraphicsExtractor gg, float x, float y, float d,
                              float frac, boolean round, int argb) {
        if (frac <= 0.0f) {
            return;
        }
        Identifier id = TexCache.arc(128, 16.0f, frac, round);
        if (id == null) {
            return;
        }
        Render2D.texture(gg, id, x, y, d, d, argb);
    }

    private static int hpColor(float frac) {
        return frac > 0.6f ? 0xFF4ADE80 : frac > 0.3f ? 0xFFFACC15 : 0xFFF87171;
    }

    /** Голова игрока как предмет (скин подтягивается из профиля). */
    private static ItemStack headStack(LivingEntity target, Minecraft mc) {
        if (!(target instanceof Player) || mc.getConnection() == null) {
            return ItemStack.EMPTY;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(target.getUUID());
        if (info == null) {
            return ItemStack.EMPTY;
        }
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(info.getProfile()));
        return head;
    }

    /** Аватарка-заглушка: первая буква ника в чипе, как в карточках GUI. */
    private static void drawAvatar(GuiGraphicsExtractor gg, String name,
                                   float x, float y, float size, float s) {
        Render2D.round(gg, x, y, size, size, 8.0f * s, Style.WHITE_04);
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        HFont f = sTitle(s * 1.2f);
        f.drawCentered(gg, letter, x + size / 2.0f,
                f.centeredBaseline(y, size), Style.accent());
    }

    private static LivingEntity resolveTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        LivingEntity aura = AttackAura.target;
        if (isValidHudTarget(mc, aura)) {
            return aura;
        }
        return raycastTarget(mc);
    }

    private static boolean isValidHudTarget(Minecraft mc, LivingEntity e) {
        return e != null && e != mc.player && e.isAlive() && !e.isRemoved()
                && e.distanceTo(mc.player) < 16.0f;
    }

    private static LivingEntity raycastTarget(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) {
            return null;
        }
        Vec3 eye = cam.getEyePosition();
        Vec3 look = cam.getLookAngle();
        double range = 6.0;
        Vec3 end = eye.add(look.scale(range));
        LivingEntity best = null;
        double bestD = range;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || !isValidHudTarget(mc, living)) {
                continue;
            }
            Optional<Vec3> clip = living.getBoundingBox().inflate(0.35).clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double d = eye.distanceTo(clip.get());
            if (d < bestD) {
                bestD = d;
                best = living;
            }
        }
        return best;
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
