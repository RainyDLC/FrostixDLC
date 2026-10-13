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
 * Отрисовка HUD модуля Interface в редакционном стиле «Editorial»:
 * плоские панели с акцентной линейкой сверху, волосные разделители,
 * крупная типографика, капс-подписи. Без скруглений, стекла и свечений.
 *
 * Элементы: ватермарка-мастхед, кейбинды функций, активные эффекты,
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

    // ---------------- визуальный язык «Quick» ----------------
    // Компактные скруглённые панели с мягким акцентным свечением,
    // шапка с акцентной иконкой, строки «белое слева / серое справа».

    /** Жирный мелкий шрифт списков (как quick bold). */
    private static HFont qBold(float s) {
        float q = qs(s);
        return q == 1.0f ? Fonts.title() : Fonts.of(Fonts.ROBOTO_MEDIUM, 7.0f * q);
    }

    /** Обычный мелкий шрифт второстепенного текста (как quick medium). */
    private static HFont qMed(float s) {
        float q = qs(s);
        return q == 1.0f ? Fonts.body() : Fonts.of(Fonts.ROBOTO_REGULAR, 7.0f * q);
    }

    /** Мягкое акцентное свечение позади панели, слегка пульсирует. */
    private static void qGlow(GuiGraphicsExtractor gg, float w, float h, float s) {
        float g = 9.0f * s;
        long t = System.currentTimeMillis() % 1200L;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t / 1200.0f * Math.PI * 2.0f);
        int gd = Math.max(1, Math.round(Math.max(w, h) + g * 2.0f));
        Render2D.texture(gg, TexCache.glow(gd), -g, -g, w + g * 2.0f, h + g * 2.0f,
                Render2D.withAlpha(Style.accent(), 0.10f * pulse));
    }

    /** Скруглённая тёмная панель со свечением. */
    private static void qPanel(GuiGraphicsExtractor gg, float w, float h, float s) {
        qGlow(gg, w, h, s);
        Render2D.round(gg, 0, 0, w, h, Style.HUD_R * s, Style.HUD_BG);
    }

    /** Шапка списка: акцентная векторная иконка + заголовок + волосная линия. */
    private static float qHeader(GuiGraphicsExtractor gg, float s, float w,
                                 String icon, String title) {
        float pad = Style.HUD_PAD * s;
        float hh = Style.HUD_HEAD_H * s;
        float is = 8.0f * s;
        Render2D.icon(gg, icon, pad, (hh - is) / 2.0f, is, is, Style.accent());
        HFont b = qBold(s);
        b.draw(gg, title, pad + is + 5.0f * s, b.centeredBaseline(0, hh), Style.WHITE);
        float lh = Math.max(1.0f, 0.5f * s);
        Render2D.rect(gg, pad, hh - lh, w - pad * 2.0f, lh, white(0.08f));
        return hh;
    }

    /** Шапка списка: акцентная PNG-иконка + заголовок + волосная линия. */
    private static float qHeaderTex(GuiGraphicsExtractor gg, float s, float w,
                                    net.minecraft.resources.Identifier tex, String title) {
        float pad = Style.HUD_PAD * s;
        float hh = Style.HUD_HEAD_H * s;
        float is = 8.0f * s;
        Render2D.texture(gg, TexCache.icon(tex), pad, (hh - is) / 2.0f, is, is,
                Style.accent());
        HFont b = qBold(s);
        b.draw(gg, title, pad + is + 5.0f * s, b.centeredBaseline(0, hh), Style.WHITE);
        float lh = Math.max(1.0f, 0.5f * s);
        Render2D.rect(gg, pad, hh - lh, w - pad * 2.0f, lh, white(0.08f));
        return hh;
    }


    /** Скруглённый слот предмета. */
    private static void qSlot(GuiGraphicsExtractor gg, Minecraft mc, ItemStack stack,
                              float x, float y, float size, float s) {
        Render2D.round(gg, x, y, size, size, 3.0f * s, Style.HUD_BG);
        if (stack.isEmpty()) {
            return;
        }
        float pad = 2.0f * s;
        float is = size - pad * 2.0f;
        gg.pose().pushMatrix();
        gg.pose().translate(x + pad, y + pad);
        float k = is / 16.0f;
        gg.pose().scale(k, k);
        gg.renderItem(mc.player, stack, 0, 0, 0);
        gg.pose().popMatrix();
        int count = stack.getCount();
        if (count > 1) {
            HFont f = sLabel(s * 0.62f);
            String text = String.valueOf(count);
            f.drawRight(gg, text, x + size - 2.5f * s, y + size - 2.5f * s,
                    Style.WHITE);
        }
    }

    /** Линия прочности 2px под слотом, цвет — светофор. */
    private static void qDur(GuiGraphicsExtractor gg, float x, float y, float w,
                             ItemStack stack, float s) {
        float frac = durabilityFrac(stack);
        if (frac >= 1.0f) {
            return;
        }
        float h = Math.max(1.5f, 2.0f * s);
        Render2D.rect(gg, x, y, w, h, white(0.14f));
        Render2D.rect(gg, x, y, w * frac, h, durabilityColor(stack));
    }

    private static int white(float a) {
        return Render2D.withAlpha(0xFFFFFFFF, a);
    }

    private static final class WmData implements Sized {
        final float w;
        final float h;
        final String[] parts;

        WmData(float w, float h, String[] parts) {
            this.w = w;
            this.h = h;
            this.parts = parts;
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

    /** Пилюля-ватермарка: логотип + части через серый » (как Quick). */
    private static WmData measureWatermark(float s) {
        Minecraft mc = Minecraft.getInstance();
        HFont b = qBold(s);
        HFont m = qMed(s);
        float h = 16.0f * s;

        String user = mc.player != null ? mc.player.getName().getString()
                : mc.getUser() != null ? mc.getUser().getName() : "Player";
        int ping = ping();
        String[] parts = {
                "RainyDLC",
                user,
                "FPS " + mc.getFps(),
                ping >= 0 ? ping + " MS" : "--",
                java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
        };
        float pad = Style.HUD_PAD * s;
        float gap = 4.0f * s;
        float iconW = 8.0f * s;
        float sepW = m.width("»");
        float w = pad + iconW + pad;
        for (String p : parts) {
            w += gap + sepW + gap + b.width(p);
        }
        return new WmData(w, h, parts);
    }

    /** Рисует ватермарку-пилюлю в origin (0,0) с ручным масштабом. */
    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WmData d) {
        float w = d.w();
        float h = d.h();
        qGlow(gg, w, h, s);
        Render2D.round(gg, 0, 0, w, h, h / 2.0f, Style.HUD_BG);

        float pad = Style.HUD_PAD * s;
        float gap = 4.0f * s;
        float iconS = 8.0f * s;
        Render2D.texture(gg, TexCache.icon(IDENT_LOGO), pad, (h - iconS) / 2.0f,
                iconS, iconS, Style.accent());

        HFont b = qBold(s);
        HFont m = qMed(s);
        float ty = b.centeredBaseline(0, h);
        float sy = m.centeredBaseline(0, h);
        float x = pad + iconS + pad;
        for (int i = 0; i < d.parts.length; i++) {
            x += gap;
            x = m.draw(gg, "»", x, sy, Style.HUD_GRAY) + gap;
            x = b.draw(gg, d.parts[i], x, ty,
                    i == 0 ? Style.accent() : Style.WHITE);
        }
    }

    private static final net.minecraft.resources.Identifier IDENT_LOGO =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "hatek_client", "textures/gui/logo.png");

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

    /** Кейбинды: шапка с иконкой + компактные строки. null — нечего показать. */
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

        HFont b = qBold(s);
        float pad = Style.HUD_PAD * s;
        float rowH = Style.HUD_ROW_H * s;

        float iconW = 8.0f * s;
        float headerW = pad + iconW + 5.0f * s + b.width("Hotkeys") + pad;
        float rowsW = 0.0f;
        for (Module module : bound) {
            float rowW = pad + b.width(module.name()) + 14.0f * s
                    + b.width("[" + module.keyLabel() + "]") + pad;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float h = Style.HUD_HEAD_H * s + bound.size() * rowH
                + Style.HUD_BOTTOM * s;
        return new KbData(w, h, bound);
    }

    /** Рисует панель кейбиндов в origin (0,0) с ручным масштабом. */
    private static void drawKeybinds(GuiGraphicsExtractor gg, float s, KbData d) {
        qPanel(gg, d.w, d.h, s);
        float ry = qHeader(gg, s, d.w, "keyboard", "Hotkeys");

        float rowH = Style.HUD_ROW_H * s;
        HFont b = qBold(s);
        float pad = Style.HUD_PAD * s;
        for (Module module : d.bound) {
            float ty = b.centeredBaseline(ry, rowH);
            b.draw(gg, module.name(), pad, ty,
                    module.isEnabled() ? Style.WHITE : Style.HUD_GRAY);
            b.drawRight(gg, "[" + module.keyLabel() + "]", d.w - pad, ty,
                    Style.HUD_GRAY);
            ry += rowH;
        }
    }

    private static final class FxData implements Sized {
        final float w;
        final float h;
        final List<MobEffectInstance> shown;

        FxData(float w, float h, List<MobEffectInstance> shown) {
            this.w = w;
            this.h = h;
            this.shown = shown;
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

    /** Эффекты: шапка с колбой + строки «имя + уровень / длительность». */
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

        HFont b = qBold(s);
        float pad = Style.HUD_PAD * s;
        float rowH = Style.HUD_ROW_H * s;

        float headerW = pad + 8.0f * s + 5.0f * s + b.width("Effects") + pad;
        float rowsW = 0.0f;
        int count = Math.min(8, effects.size());
        for (int i = 0; i < count; i++) {
            MobEffectInstance instance = effects.get(i);
            String roman = ampRoman(instance.getAmplifier());
            float leadW = 9.0f * s + 4.0f * s;
            float leftW = b.width(effectBaseName(instance))
                    + (roman == null ? 0.0f : 3.0f * s + b.width(roman));
            float rowW = pad + leadW + leftW + 14.0f * s
                    + b.width(formatDuration(instance.getDuration())) + pad;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W * s, Math.max(headerW, rowsW));
        float h = Style.HUD_HEAD_H * s + count * rowH + Style.HUD_BOTTOM * s;
        return new FxData(w, h, effects.subList(0, count));
    }

    /** Рисует панель эффектов в origin (0,0) с ручным масштабом. */
    private static void drawEffects(GuiGraphicsExtractor gg, float s, FxData d) {
        qPanel(gg, d.w, d.h, s);
        float ry = qHeaderTex(gg, s, d.w, IDENT_FLASK, "Effects");

        float pad = Style.HUD_PAD * s;
        float rowH = Style.HUD_ROW_H * s;
        float iconS = 9.0f * s;
        HFont b = qBold(s);
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        for (MobEffectInstance instance : d.shown) {
            Holder<MobEffect> holder = instance.getEffect();
            gg.blitSprite(pipeline, Hud.getMobEffectSprite(holder),
                    (int) pad, (int) (ry + (rowH - iconS) / 2.0f),
                    (int) iconS, (int) iconS);

            float ty = b.centeredBaseline(ry, rowH);
            float x = pad + iconS + 4.0f * s;
            x = b.draw(gg, effectBaseName(instance), x, ty, Style.WHITE);
            String roman = ampRoman(instance.getAmplifier());
            if (roman != null) {
                b.draw(gg, roman, x + 3.0f * s, ty, Style.HUD_LEVEL);
            }
            b.drawRight(gg, formatDuration(instance.getDuration()),
                    d.w - pad, ty, Style.HUD_GRAY);
            ry += rowH;
        }
    }

    private static final net.minecraft.resources.Identifier IDENT_FLASK =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "hatek_client", "textures/gui/flask.png");

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
            if (i == inv.getSelectedSlot()) {
                // Выбранный: акцентная подложка + скруглённый слот поверх.
                float fr = 1.5f * s;
                Render2D.round(gg, sx - fr, -fr, slot + fr * 2.0f, slot + fr * 2.0f,
                        (3.0f + 1.5f) * s, Style.accent());
            }
            qSlot(gg, mc, inv.getItem(i), sx, 0, slot, s);
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            qSlot(gg, mc, offhand, -slot - 6.0f * s, 0, slot, s);
        }
    }

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
        float item = 14.0f * s;
        float gap = 3.0f * s;
        float w = gap * 2.0f + 4.0f * item + 3.0f * gap;
        float h = gap * 2.0f + item + 3.0f * s;
        return new ArmorData(w, h, armor, count);
    }

    /** Панель брони: ряд предметов с линиями прочности (как Quick). */
    private static void drawArmor(GuiGraphicsExtractor gg, float s, ArmorData d) {
        qPanel(gg, d.w, d.h, s);

        Minecraft mc = Minecraft.getInstance();
        float item = 14.0f * s;
        float gap = 3.0f * s;
        float top = gap;
        for (int i = 0; i < 4; i++) {
            float x = gap + i * (item + gap);
            ItemStack stack = d.armor.get(i);
            qSlot(gg, mc, stack, x, top, item, s);
            qDur(gg, x, top + item + 2.0f * s, item, stack, s);
        }
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
        // Компактная панель 112x30, как Quick TargetInfo.
        float w = 112.0f * s;
        float h = 30.0f * s;
        return new TargetData(w, h, t, t.getName().getString(),
                t == AttackAura.target, mc.player.distanceTo(t),
                0.0f, 0.0f, "", armorPieces(t),
                t.getMainHandItem(), t.getOffhandItem());
    }

    private static float targetHpAnim = 1.0f;
    private static float targetDmgAnim = 1.0f;

    private static void drawTarget(GuiGraphicsExtractor gg, float s, TargetData d) {
        Minecraft mc = Minecraft.getInstance();
        float maxHp = Math.max(1.0f, d.target.getMaxHealth());
        float targetFrac = Mth.clamp(d.target.getHealth() / maxHp, 0.0f, 1.0f);
        targetHpAnim += (targetFrac - targetHpAnim) * 0.25f;
        targetDmgAnim += (targetFrac - targetDmgAnim) * 0.08f;

        qPanel(gg, d.w, d.h, s);

        float head = 21.0f * s;
        float headX = 4.5f * s;
        float headY = (d.h - head) / 2.0f;
        ItemStack headStack = headStack(d.target, mc);
        if (!headStack.isEmpty()) {
            qSlot(gg, mc, headStack, headX, headY, head, s);
        } else {
            drawAvatar(gg, d.name, headX, headY, head, s);
        }

        HFont nameF = qFont(s, 6.5f, true);
        HFont hpF = qFont(s, 6.0f, true);
        HFont subF = qFont(s, 5.8f, false);
        float contentX = headX + head + 5.5f * s;
        float contentW = d.w - contentX - 5.0f * s;

        String hpStr = String.format(java.util.Locale.US, "%.1f HP",
                d.target.getHealth() + d.target.getAbsorptionAmount());
        float hpW = hpF.width(hpStr);
        float topY = 4.5f * s;
        String name = nameF.trim(d.name, contentW - hpW - 4.0f * s);
        nameF.draw(gg, name, contentX, topY, Style.WHITE);
        hpF.drawRight(gg, hpStr, d.w - 5.0f * s, topY + 0.3f * s, Style.accent());

        // HP-бар: трек + «призрак» урона + заливка + золото абсорбции.
        float barY = 14.5f * s;
        float barH = 3.5f * s;
        float barR = barH / 2.0f;
        Render2D.round(gg, contentX, barY, contentW, barH, barR, white(0.14f));
        float dmgW = contentW * clamp01(targetDmgAnim);
        if (dmgW > 0.5f) {
            Render2D.round(gg, contentX, barY, dmgW, barH, barR,
                    Render2D.withAlpha(Style.accent(), 0.45f));
        }
        float hpW2 = contentW * clamp01(targetHpAnim);
        if (hpW2 > 0.5f) {
            Render2D.round(gg, contentX, barY, hpW2, barH, barR, Style.accent());
        }
        float abFrac = Mth.clamp(d.target.getAbsorptionAmount() / maxHp, 0.0f, 1.0f);
        if (abFrac > 0.0f) {
            float abW = Math.min(contentW, contentW * abFrac);
            if (abW > 0.5f) {
                Render2D.round(gg, contentX, barY, abW, barH, barR,
                        Render2D.withAlpha(Style.HUD_GOLD, 0.75f));
            }
        }

        // Низ: дистанция + предмет в руке (или проценты).
        float botY = 20.0f * s;
        String dist = String.format(java.util.Locale.US, "%.1fm", d.dist);
        subF.draw(gg, dist, contentX, botY, Style.HUD_GRAY);

        if (!d.mainHand.isEmpty()) {
            float itemS = 9.0f * s;
            float itemX = d.w - itemS - 5.0f * s;
            float itemY = botY - 1.5f * s;
            gg.pose().pushMatrix();
            gg.pose().translate(itemX, itemY);
            float k = itemS / 16.0f;
            gg.pose().scale(k, k);
            gg.renderItem(mc.player, d.mainHand, 0, 0, 0);
            gg.pose().popMatrix();
        } else {
            String pct = Math.round(targetFrac * 100.0f) + "%";
            subF.drawRight(gg, pct, d.w - 5.0f * s, botY, Style.HUD_GRAY);
        }
    }

    /** Шрифт произвольного кегля для компактных панелей. */
    private static HFont qFont(float s, float px, boolean bold) {
        float q = qs(s);
        if (q == 1.0f) {
            return bold ? Fonts.title() : Fonts.body();
        }
        return Fonts.of(bold ? Fonts.ROBOTO_MEDIUM : Fonts.ROBOTO_REGULAR, px * q);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
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

    /** Аватарка-заглушка: первая буква ника в квадратном слоте. */
    /** Аватарка-заглушка: первая буква ника в скруглённом слоте. */
    private static void drawAvatar(GuiGraphicsExtractor gg, String name,
                                   float x, float y, float size, float s) {
        Render2D.round(gg, x, y, size, size, 3.0f * s, Style.HUD_BG);
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        HFont f = qFont(s, 8.0f, true);
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
