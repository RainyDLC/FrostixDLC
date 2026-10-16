package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.interf.quick.*;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.LiquidBackdrop;
import dev.hatek.client.ui.render.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HUD RainyDLC «Velvet» — матовое стекло, мягкие тени, спокойная типографика.
 * Полупрозрачные панели со снимком мира, тонкая рамка, верхний блик,
 * заголовки с разрядкой, глянцевые бары, пилюли вместо голого текста.
 */
public final class InterfaceHud {
    private static boolean hooked, screenOpen;
    private static final Map<HudLayout.Element, float[]> BOUNDS = new EnumMap<>(HudLayout.Element.class);
    private static final Map<String, Long> STAFF_SINCE = new HashMap<>();
    private static final Map<HudLayout.Element, ListState> LISTS = new EnumMap<>(HudLayout.Element.class);
    private static Object lastLevel;
    private static long lastFrame;
    private static float delta = 1 / 60f;
    private static int frame;
    private static Hit dragHit;
    private static float grabX, grabY;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // ---------- токены «Velvet» ----------
    private static final float PAD = 7.0f;
    private static final float HEADER_H = 16.0f;
    private static final float ROW_H = 13.0f;
    private static final int SHADE = 0xC8101014;
    private static final int BORDER = 0x2BFFFFFF;
    private static final int SHEEN = 0x14FFFFFF;
    private static final int HAIR = 0x1CFFFFFF;
    private static final int GREY = 0xFFC6C6CF;
    private static final int DIM = 0xFF8E8E99;
    private static final int DARK = 0xFF17171C;
    private static final int TRACK = 0xFF26262D;
    private static final int GOLD = 0xFFFFD45F;

    private interface Sized {
        float w();
        float h();
    }

    private record Row(String key, String left, String right, String icon, Object sprite) {
    }

    private static final class RowAnim {
        Row row;
        float alpha;
        float slide;

        RowAnim(Row row) {
            this.row = row;
        }
    }

    private static final class ListState {
        final Map<String, RowAnim> rows = new LinkedHashMap<>();
        int seen = -1, dims = -1;
        float w, h, scale;
    }

    private record ListData(float w, float h, String title, List<RowAnim> rows) implements Sized {
    }

    private record PillData(float w, float h, String label, String text, ItemStack item) implements Sized {
    }

    private record WatermarkData(float w, float h, String nick, String fps, String time, String ping,
                                 int pingColor) implements Sized {
    }

    private record ArmorData(float w, float h, List<ItemStack> items, boolean vertical) implements Sized {
    }

    private record TargetData(float w, float h, LivingEntity target, String name, String hp,
                              float dist, float health, float absorption) implements Sized {
    }

    private record HbData(float w, float h, float s) implements Sized {
    }

    private static LivingEntity lastTarget;
    private static float targetShow;
    private static int targetFrame = -1;
    private static LivingEntity healthTarget;
    private static float healthAnimation = 1, damageAnimation = 1;

    private InterfaceHud() {
    }

    public static void init() {
        if (hooked) {
            return;
        }
        hooked = true;
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("hatek_client", "interface_hud"),
                (gg, tick) -> render(gg));
    }

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
    }

    private static boolean enabled() {
        return Interface.instance() != null && Interface.instance().isEnabled();
    }

    private static boolean editing() {
        return Minecraft.getInstance().gui.screen() instanceof ChatScreen;
    }

    private static void render(GuiGraphicsExtractor gg) {
        if (screenOpen) {
            return;
        }
        renderExplicit(gg);
    }

    public static void renderExplicit(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !enabled()) {
            BOUNDS.clear();
            stopDrag();
            QuickElementMenu.close();
            return;
        }
        renderAll(gg);
    }

    public static Map<HudLayout.Element, float[]> boundsSnapshot() {
        Map<HudLayout.Element, float[]> result = new EnumMap<>(HudLayout.Element.class);
        BOUNDS.forEach((el, b) -> result.put(el, b.clone()));
        return result;
    }

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

    public static Hit hitTest(double mx, double my) {
        if (!enabled()) {
            return null;
        }
        if (Interface.showMedia2D()) {
            float[] b = media2DBounds();
            if (hit(b, mx, my)) {
                return new Hit(null, true, b);
            }
        }
        Map<HudLayout.Element, float[]> bounds = computeBounds();
        var elements = new ArrayList<>(bounds.keySet());
        Collections.reverse(elements);
        for (var el : elements) {
            var b = bounds.get(el);
            if (hit(b, mx, my)) {
                return new Hit(el, false, b);
            }
        }
        return null;
    }

    private static boolean hit(float[] b, double x, double y) {
        return b != null && x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3];
    }

    public static Map<HudLayout.Element, float[]> computeBounds() {
        Map<HudLayout.Element, float[]> result = new EnumMap<>(HudLayout.Element.class);
        if (Minecraft.getInstance().player == null || !enabled()) {
            return result;
        }
        for (var el : HudLayout.Element.values()) {
            if (!visible(el)) {
                continue;
            }
            var e = HudLayout.get(el);
            Sized d = measure(el, e.scale * Render2D.guiScale());
            if (d == null) {
                continue;
            }
            float[] p = position(el, e, d.w(), d.h());
            result.put(el, new float[]{p[0], p[1], d.w(), d.h()});
        }
        return result;
    }

    private static boolean visible(HudLayout.Element el) {
        return switch (el) {
            case WATERMARK -> Interface.showWatermark();
            case KEYBINDS -> Interface.showKeybinds();
            case EFFECTS -> Interface.showEffects();
            case HOTBAR -> Interface.showHotbar();
            case ARMOR -> Interface.showArmor();
            case TARGET -> Interface.showTarget();
            default -> Interface.showExtra(el);
        };
    }

    public static boolean startDrag(double mx, double my) {
        if (!editing() || QuickElementMenu.open()) {
            return false;
        }
        Hit h = hitTest(mx, my);
        if (h == null) {
            return false;
        }
        dragHit = h;
        grabX = (float) mx - h.bounds[0];
        grabY = (float) my - h.bounds[1];
        return true;
    }

    public static void stopDrag() {
        if (dragHit != null) {
            dragHit = null;
            HudLayout.save();
        }
    }

    public static void moveHit(Hit hit, float x, float y) {
        float w = hit.bounds[2], h = hit.bounds[3];
        x = clampPosition(x, w, Render2D.screenWidth());
        y = clampPosition(y, h, Render2D.screenHeight());
        if (hit.media) {
            Interface.setMediaGeometry(Interface.mediaSize(),
                    (x + w / 2) / Render2D.screenWidth() * 100,
                    (y + h / 2) / Render2D.screenHeight() * 100);
        } else {
            HudLayout.setPos(hit.element, x, y);
        }
    }

    public static void resizeHit(Hit hit, float factor) {
        if (hit.media) {
            Interface.setMediaGeometry(Interface.mediaSize() * factor, Interface.mediaX(), Interface.mediaY());
        } else {
            HudLayout.setScale(hit.element, HudLayout.get(hit.element).scale * factor);
        }
    }

    private static float clampPosition(float p, float size, float screen) {
        return Math.max(4, Math.min(Math.max(4, screen - size - 4), p));
    }

    private static float[] position(HudLayout.Element el, HudLayout.Entry e, float w, float h) {
        float sw = Render2D.screenWidth(), sh = Render2D.screenHeight(), s = Render2D.guiScale();
        float x = 4 * s, y = 4 * s;
        switch (el) {
            case WATERMARK -> {
                if ("Center".equals(Interface.watermarkPos())) {
                    x = (sw - w) / 2;
                } else if ("Right".equals(Interface.watermarkPos())) {
                    x = sw - w - 4 * s;
                }
            }
            case KEYBINDS -> {
                x = 4 * s;
                y = 30 * s;
            }
            case EFFECTS -> {
                x = 140 * s;
                y = 30 * s;
            }
            case STAFF -> {
                x = 4 * s;
                y = 100 * s;
            }
            case COOLDOWNS -> {
                x = 140 * s;
                y = 100 * s;
            }
            case ARRAYLIST -> {
                x = sw - w - 4 * s;
                y = 24 * s;
            }
            case TARGET -> {
                x = (sw - w) / 2 + 80 * s;
                y = sh / 2 + 40 * s;
            }
            case ARMOR -> {
                x = (sw - w) / 2;
                y = sh - h - 48 * s;
            }
            case HOTBAR -> {
                x = (sw - w) / 2;
                y = sh - h - 4 * s;
            }
            case NOTIFICATIONS -> {
                x = (sw - w) / 2;
                y = sh - h - 80 * s;
            }
            case TOTEMS -> {
                x = sw - w - 4 * s;
                y = sh - h - 4 * s;
            }
            case TICKS, PING, FPS, BPS, XYZ -> {
                x = 4 * s;
                y = sh - h - (4 + 14 * (el.ordinal() - HudLayout.Element.TICKS.ordinal())) * s;
            }
        }
        return new float[]{clampPosition(e.positioned() ? e.x : x, w, sw),
                clampPosition(e.positioned() ? e.y : y, h, sh)};
    }

    private static QuickFont fTitle(float s) {
        return QuickFont.of("bold", 7.5f * s);
    }

    private static QuickFont fBody(float s) {
        return QuickFont.of("medium", 7.0f * s);
    }

    private static QuickFont fMicro(float s) {
        return QuickFont.of("bold", 5.5f * s);
    }

    private static QuickFont fSmall(float s) {
        return QuickFont.of("medium", 6.0f * s);
    }

    // ---------- кадр ----------

    private record Placed(HudLayout.Element el, float x, float y, Sized data, float s) {
    }

    private static void renderAll(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            STAFF_SINCE.clear();
            LISTS.clear();
            QuickTelemetry.reset();
            QuickNotifications.clear();
            lastTarget = null;
            targetShow = 0;
        }
        long now = System.nanoTime();
        delta = lastFrame == 0 ? 1 / 60f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        frame++;
        if (!editing()) {
            stopDrag();
            QuickElementMenu.close();
        } else if (dragHit != null) {
            moveHit(dragHit, (float) Render2D.mouseX() - grabX, (float) Render2D.mouseY() - grabY);
        }
        BOUNDS.clear();
        LiquidBackdrop.request();

        List<Placed> placed = new ArrayList<>();
        for (var el : HudLayout.Element.values()) {
            if (!visible(el)) {
                continue;
            }
            var entry = HudLayout.get(el);
            float s = entry.scale * Render2D.guiScale();
            Sized data = measure(el, s);
            if (data == null || data.w() <= 0 || data.h() <= 0) {
                continue;
            }
            float[] pos = position(el, entry, data.w(), data.h());
            BOUNDS.put(el, new float[]{pos[0], pos[1], data.w(), data.h()});
            placed.add(new Placed(el, pos[0], pos[1], data, s));
        }

        if (!placed.isEmpty()) {
            LiquidBackdrop.onGuiRender();
        }

        Render2D.begin(gg);
        try {
            for (Placed p : placed) {
                var entry = HudLayout.get(p.el);
                Render2D.pushAlpha(entry.opacity);
                try {
                    // Пилюли ArrayList и тосты рисуют собственное стекло.
                    if (p.el != HudLayout.Element.ARRAYLIST && p.el != HudLayout.Element.NOTIFICATIONS) {
                        glass(gg, p.x, p.y, p.data.w(), p.data.h(), p.s);
                    }
                    Render2D.pushTranslate(gg, p.x, p.y);
                    try {
                        draw(gg, p.el, p.s, p.data);
                    } finally {
                        Render2D.popTransform(gg);
                    }
                    if (editing() && dragHit != null && dragHit.element == p.el) {
                        float r = 9 * p.s;
                        Render2D.round(gg, p.x, p.y, p.data.w(), p.data.h(), r,
                                Render2D.withAlpha(Style.accent(), 0.35f));
                    }
                } finally {
                    Render2D.popAlpha();
                }
            }
            placeMedia2D(gg);
            QuickElementMenu.render(gg);
        } finally {
            Render2D.end(gg);
        }
    }

    // ---------- стекло ----------

    private static Identifier frostTexture() {
        if (LiquidBackdrop.frostReady()) {
            return LiquidBackdrop.FROST;
        }
        if (LiquidBackdrop.sharpReady()) {
            return LiquidBackdrop.SHARP;
        }
        return null;
    }

    /**
     * Панель «Velvet»: мягкая тень, тонкая рамка, матовый снимок мира,
     * затемнение и верхний блик. Радиус 9 — единый для всех панелей.
     */
    private static void glass(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        float r = 9.0f * s;
        Render2D.round(gg, x, y + 2.5f * s, w, h, r, 0x38000000);
        Render2D.round(gg, x, y + 5.0f * s, w, h, r, 0x1C000000);
        Render2D.round(gg, x, y, w, h, r, BORDER);
        float inset = Math.max(1.0f, s * 0.5f);
        if (Interface.hudBlur()) {
            Identifier tex = frostTexture();
            if (tex != null) {
                snapshot(gg, tex, x + inset, y + inset, w - inset * 2, h - inset * 2, r - inset);
            }
        }
        Render2D.round(gg, x + inset, y + inset, w - inset * 2, h - inset * 2,
                Math.max(1.0f, r - inset), SHADE);
        Render2D.round(gg, x + inset + 1, y + inset + 1, w - (inset + 1) * 2,
                Math.max(1.0f, s), s * 0.5f, SHEEN);
    }

    /** Прямоугольник снимка 1:1 (те же единицы, что позиции HUD). */
    private static void snapshot(GuiGraphicsExtractor gg, Identifier tex,
                                 float x, float y, float w, float h, float r) {
        int tw = LiquidBackdrop.width(), th = LiquidBackdrop.height();
        if (tw <= 0 || th <= 0 || w <= 0 || h <= 0) {
            return;
        }
        int rows = Math.max(1, Math.round(Math.max(2.0f, r / 3.0f)));
        int height = Math.max(1, Math.round(h));
        for (int yy = 0; yy < height; yy += rows) {
            int band = Math.min(rows, height - yy);
            float cy = yy + band / 2.0f;
            float dy = cy < r ? r - cy : cy > h - r ? cy - (h - r) : 0.0f;
            float inset = dy > 0.0f ? r - (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy)) : 0.0f;
            float bx = x + inset;
            float bw = Math.max(1.0f, w - inset * 2.0f);
            gg.blit(QuickPipelines.BLUR, tex, Math.round(bx), Math.round(y + yy),
                    bx, th - (y + yy), Math.round(bw), band,
                    Math.round(bw), -band, tw, th,
                    Render2D.withAlpha(0xFFFFFFFF, Render2D.alpha()));
        }
    }

    // ---------- типографика ----------

    private static float textShadow(GuiGraphicsExtractor gg, QuickFont f, String text,
                                    float x, float baseline, int color, float s) {
        f.draw(gg, text, x + 0.5f * s, baseline + 0.5f * s, 0x80000000);
        return f.draw(gg, text, x, baseline, color);
    }

    /** Текст с разрядкой для микро-заголовков. */
    private static float tracked(GuiGraphicsExtractor gg, QuickFont f, String text,
                                 float x, float baseline, int color, float tracking) {
        if (text == null) {
            return x;
        }
        for (int code : text.codePoints().toArray()) {
            String ch = new String(Character.toChars(code));
            f.draw(gg, ch, x, baseline, color);
            x += f.width(ch) + (" ".equals(ch) ? 0 : tracking);
        }
        return x;
    }

    private static float trackedWidth(QuickFont f, String text, float tracking) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int glyphs = 0;
        for (int i = 0; i < text.length();) {
            int code = text.codePointAt(i);
            if (code != ' ') {
                glyphs++;
            }
            i += Character.charCount(code);
        }
        return f.width(text) + tracking * Math.max(0, glyphs - 1);
    }

    /** Заголовок панели: разрядка + счётчик + градиентная акцентная линия. */
    private static void header(GuiGraphicsExtractor gg, String title, int count, float w, float s) {
        QuickFont micro = fMicro(s);
        float tracking = 0.9f * s;
        tracked(gg, micro, title.toUpperCase(Locale.ROOT), PAD * s,
                micro.centeredBaseline(0, HEADER_H * s), DIM, tracking);
        String num = String.format(Locale.ROOT, "%02d", count);
        micro.drawRight(gg, num, w - PAD * s, micro.centeredBaseline(0, HEADER_H * s), Style.accent());
        gradientLine(gg, PAD * s, HEADER_H * s, w * 0.45f, s);
    }

    /** Горизонтальная линия: акцент → прозрачность. */
    private static void gradientLine(GuiGraphicsExtractor gg, float x, float y, float w, float s) {
        int steps = Math.max(4, Math.min(48, Math.round(w / (2 * s))));
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            int c = Render2D.withAlpha(Style.accent(), (1 - t0) * 0.55f);
            Render2D.rect(gg, x + w * t0, y, Math.max(1, w * (t1 - t0) + 0.5f),
                    Math.max(1, s * 0.75f), c);
        }
    }

    /** Точка-разделитель. */
    private static void dot(GuiGraphicsExtractor gg, float cx, float cy, float d, int color) {
        Render2D.circle(gg, cx - d / 2, cy - d / 2, d, color);
    }

    // ---------- measure / draw ----------

    private static Sized measure(HudLayout.Element el, float s) {
        return switch (el) {
            case WATERMARK -> measureWatermark(s);
            case HOTBAR -> measureHotbar(s);
            case TARGET -> measureTarget(s);
            case ARMOR -> measureArmor(s);
            case KEYBINDS -> list(el, "Binds", keyRows(), s);
            case EFFECTS -> list(el, "Effects", effectRows(), s);
            case STAFF -> list(el, "Staff", staffRows(), s);
            case COOLDOWNS -> list(el, "Cooldowns", cooldownRows(), s);
            case ARRAYLIST -> list(el, "", moduleRows(s), s);
            case NOTIFICATIONS -> list(el, "", notificationRows(), s);
            default -> measurePill(el, s);
        };
    }

    private static void draw(GuiGraphicsExtractor gg, HudLayout.Element el, float s, Sized data) {
        switch (el) {
            case WATERMARK -> drawWatermark(gg, s, (WatermarkData) data);
            case HOTBAR -> drawHotbar(gg, s, (HbData) data);
            case TARGET -> {
                Render2D.pushAlpha(targetShow);
                try {
                    drawTarget(gg, s, (TargetData) data);
                } finally {
                    Render2D.popAlpha();
                }
            }
            case ARMOR -> drawArmor(gg, s, (ArmorData) data);
            case KEYBINDS, EFFECTS, STAFF, COOLDOWNS ->
                    drawTable(gg, s, (ListData) data);
            case ARRAYLIST -> drawArrayPills(gg, s, (ListData) data);
            case NOTIFICATIONS -> drawToasts(gg, s, (ListData) data);
            default -> drawPill(gg, s, (PillData) data);
        }
    }

    // ---------- вотермарка ----------

    private static WatermarkData measureWatermark(float s) {
        Minecraft mc = Minecraft.getInstance();
        String nick = mc.player.getName().getString();
        String fps = mc.getFps() + " FPS";
        String time = LocalTime.now().format(CLOCK);
        PlayerInfo info = mc.getConnection() == null ? null
                : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int latency = info == null ? 0 : info.getLatency();
        int pingColor = latency < 60 ? 0xFF4ADE80 : latency < 140 ? GOLD : 0xFFF87171;
        float w = PAD * s + 13 * s;
        w += fTitle(s).width("Rainy") + 4 * s + fMicro(s).width("DLC") + 9 * s;
        w += fBody(s).width(nick) + 17 * s;
        w += fSmall(s).width(fps) + 17 * s;
        w += fSmall(s).width(time) + 17 * s;
        w += 4 * s + fSmall(s).width(latency + " ms") + PAD * s;
        return new WatermarkData(w, 23 * s, nick, fps, time, latency + " ms", pingColor);
    }

    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WatermarkData d) {
        float x = PAD * s;
        float cy = d.h / 2;
        // Логотип: скруглённый квадрат с глянцем.
        float logo = 13 * s;
        float ly = (d.h - logo) / 2;
        Render2D.round(gg, x, ly, logo, logo, 4 * s, Style.accent());
        Render2D.round(gg, x + 1, ly + 1, logo - 2, (logo - 2) / 2, 3 * s,
                Render2D.withAlpha(0xFFFFFFFF, 0.22f));
        fTitle(s).drawCentered(gg, "R", x + logo / 2,
                fTitle(s).visualBaseline("R", ly, logo), 0xFF101014);
        x += logo + 5 * s;
        float base = fTitle(s).centeredBaseline(0, d.h);
        x = fTitle(s).draw(gg, "Rainy", x, base, Style.WHITE);
        x = fMicro(s).draw(gg, "DLC", x + 4 * s, fMicro(s).centeredBaseline(0, d.h) - 2 * s,
                Style.accent());
        x += 9 * s;
        x = statDot(gg, x, cy, s);
        x = textShadow(gg, fBody(s), d.nick, x, fBody(s).centeredBaseline(0, d.h), GREY, s);
        x += 8 * s;
        x = statDot(gg, x, cy, s);
        x = textShadow(gg, fSmall(s), d.fps, x, fSmall(s).centeredBaseline(0, d.h), Style.WHITE, s);
        x += 8 * s;
        x = statDot(gg, x, cy, s);
        x = textShadow(gg, fSmall(s), d.time, x, fSmall(s).centeredBaseline(0, d.h), DIM, s);
        x += 8 * s;
        x = statDot(gg, x, cy, s);
        dot(gg, x + 2 * s, cy, 4 * s, Render2D.withAlpha(d.pingColor, 0.9f));
        textShadow(gg, fSmall(s), d.ping, x + 6 * s, fSmall(s).centeredBaseline(0, d.h), GREY, s);
    }

    private static float statDot(GuiGraphicsExtractor gg, float x, float cy, float s) {
        dot(gg, x + 1.5f * s, cy, 2.5f * s, 0xFF4A4A55);
        return x + 8 * s;
    }

    // ---------- строки ----------

    private static List<Row> keyRows() {
        List<Row> rows = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && m.keyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                rows.add(new Row(m.name(), m.name(), m.keyLabel(), "", null));
            }
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Interface", "RSHIFT", "", null));
        }
        return rows;
    }

    private static List<Row> effectRows() {
        List<Row> rows = new ArrayList<>();
        for (MobEffectInstance e : Minecraft.getInstance().player.getActiveEffects()) {
            if (!e.showIcon()) {
                continue;
            }
            String roman = ampRoman(e.getAmplifier());
            String name = effectBaseName(e) + (roman == null ? "" : " " + roman);
            rows.add(new Row(e.getEffect().value().getDescriptionId(), name, formatDuration(e.getDuration()), "", e));
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Speed II", "1:30", "", null));
        }
        return rows;
    }

    private static List<Row> staffRows() {
        Minecraft mc = Minecraft.getInstance();
        List<Row> rows = new ArrayList<>();
        if (mc.getConnection() == null) {
            return rows;
        }
        Set<String> present = new HashSet<>();
        for (PlayerTeam team : mc.level.getScoreboard().getPlayerTeams()) {
            String rank = (team.getPlayerPrefix().getString() + " " + team.getName()).toLowerCase(Locale.ROOT);
            if (!rank.matches(".*(admin|moder|helper|staff|owner|админ|модер|хелпер|куратор).*")) {
                continue;
            }
            for (String name : team.getPlayers()) {
                if (!name.matches("[A-Za-z0-9_]{3,16}") || !present.add(name)) {
                    continue;
                }
                PlayerInfo info = mc.getConnection().getPlayerInfo(name);
                long since = STAFF_SINCE.computeIfAbsent(name, k -> System.nanoTime());
                long seconds = (System.nanoTime() - since) / 1000000000L;
                String status = info == null ? "Vanish"
                        : info.getGameMode() == GameType.SPECTATOR ? "GM3" : "";
                rows.add(new Row(name, name,
                        (status.isEmpty() ? "" : status + " ")
                                + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                        status.isEmpty() ? "" : "!", info));
            }
        }
        STAFF_SINCE.keySet().retainAll(present);
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Moderator", "00:00", "", null));
        }
        return rows;
    }

    private static List<Row> cooldownRows() {
        Minecraft mc = Minecraft.getInstance();
        List<Row> rows = new ArrayList<>();
        Object cooldowns = mc.player.getCooldowns();
        if (cooldowns instanceof QuickCooldowns tracked) {
            for (var kv : tracked.quick$remainingTicks().entrySet()) {
                ItemStack item = ItemStack.EMPTY;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty() && mc.player.getCooldowns().getCooldownGroup(stack).equals(kv.getKey())) {
                        item = stack;
                        break;
                    }
                }
                String name = item.isEmpty() ? kv.getKey().getPath() : item.getHoverName().getString();
                rows.add(new Row(kv.getKey().toString(), name,
                        String.format(Locale.ROOT, "%.1fs", kv.getValue() / 20f), "", item));
            }
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Ender Pearl", "12.4s", "", new ItemStack(Items.ENDER_PEARL)));
        }
        return rows;
    }

    private static List<Row> moduleRows(float s) {
        List<Row> rows = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && !(m instanceof Interface)) {
                rows.add(new Row(m.name(), m.name(), "", "", null));
            }
        }
        rows.sort(Comparator.comparingDouble((Row r) -> fBody(s).width(r.left)).reversed());
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Interface", "", "", null));
        }
        return rows;
    }

    private static List<Row> notificationRows() {
        List<Row> rows = new ArrayList<>();
        for (var n : QuickNotifications.current()) {
            rows.add(new Row("notice-" + n.created(), n.text(), "", n.enabled() ? "+" : "–", null));
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Пример уведомления", "", "+", null));
        }
        return rows;
    }

    private static ListData list(HudLayout.Element el, String title, List<Row> source, float s) {
        ListState state = LISTS.computeIfAbsent(el, k -> new ListState());
        if (state.seen != frame) {
            state.seen = frame;
            Set<String> active = new HashSet<>();
            for (Row row : source) {
                active.add(row.key);
                state.rows.computeIfAbsent(row.key, k -> new RowAnim(row)).row = row;
            }
            float k = 1 - (float) Math.exp(-14 * delta);
            for (var kv : state.rows.entrySet()) {
                RowAnim a = kv.getValue();
                boolean on = active.contains(kv.getKey());
                a.alpha += ((on ? 1 : 0) - a.alpha) * k;
                a.slide += ((on ? 0 : 14 * s) - a.slide) * k;
            }
            state.rows.values().removeIf(a -> a.alpha < 0.01f && a.slide > 13.5f * s
                    && !active.contains(a.row.key));
        }
        List<RowAnim> rows = new ArrayList<>(state.rows.values());
        boolean bare = title.isEmpty();
        if (el == HudLayout.Element.ARRAYLIST) {
            rows.sort(Comparator.comparingDouble((RowAnim a) -> fBody(s).width(a.row.left)).reversed());
        }
        if (rows.isEmpty() && !editing()) {
            return null;
        }
        float rowH = (el == HudLayout.Element.NOTIFICATIONS ? 19 : bare ? 15 : ROW_H) * s;
        float gap = (el == HudLayout.Element.ARRAYLIST ? 3 : el == HudLayout.Element.NOTIFICATIONS ? 4 : 0) * s;
        float w;
        float h;
        if (bare) {
            w = 64 * s;
            for (var a : rows) {
                float rw = PAD * 2 * s + fBody(s).width(a.row.left) + 10 * s;
                if (el == HudLayout.Element.NOTIFICATIONS) {
                    rw += 14 * s;
                }
                w = Math.max(w, rw);
            }
            h = rows.size() * (rowH + gap) - gap + 2 * s;
        } else {
            w = 104 * s;
            h = HEADER_H * s + PAD * 0.5f * s;
            for (var a : rows) {
                Row row = a.row;
                boolean sprite = row.sprite != null;
                float rw = PAD * 2 * s + (sprite ? 15 : 0) * s + fBody(s).width(row.left)
                        + fBody(s).width(row.right) + (row.right.isEmpty() ? 0 : 12 * s);
                if ("Binds".equals(title)) {
                    rw += 6 * s;
                }
                w = Math.max(w, rw);
                h += rowH * a.alpha;
            }
            h += PAD * 0.5f * s;
        }
        if (state.w == 0 || Math.abs(s - state.scale) > 0.01f) {
            state.w = w;
            state.h = h;
            state.scale = s;
        } else if (state.dims != frame) {
            float k = 1 - (float) Math.exp(-18 * delta);
            state.w += (w - state.w) * k;
            state.h += (h - state.h) * k;
        }
        state.dims = frame;
        return new ListData(state.w, Math.max(1, state.h), title, rows);
    }

    /** Таблица: заголовок с разрядкой, строки с иконками, чипы кейбиндов. */
    private static void drawTable(GuiGraphicsExtractor gg, float s, ListData d) {
        header(gg, d.title, d.rows.size(), d.w, s);
        float y = HEADER_H * s + 1.5f * s;
        Render2D.pushClip(gg, 0, HEADER_H * s, d.w, d.h - HEADER_H * s);
        try {
            for (var a : d.rows) {
                Row row = a.row;
                Render2D.pushAlpha(a.alpha);
                Render2D.pushTranslate(gg, a.slide, 0);
                float x = PAD * s;
                if (row.sprite instanceof MobEffectInstance effect) {
                    float icon = 10 * s;
                    Render2D.round(gg, x, y + (ROW_H * s - icon) / 2, icon, icon, 3 * s, DARK);
                    gg.blitSprite(RenderPipelines.GUI_TEXTURED,
                            Hud.getMobEffectSprite(effect.getEffect()),
                            Math.round(x + s), Math.round(y + (ROW_H * s - icon) / 2 + s),
                            Math.round(icon - 2 * s), Math.round(icon - 2 * s),
                            Render2D.withAlpha(0xFFFFFFFF, Render2D.alpha()));
                    x += 15 * s;
                } else if (row.sprite instanceof PlayerInfo player) {
                    drawHead(gg, player, x, y + (ROW_H * s - 9 * s) / 2, 9 * s, 3 * s);
                    if (!row.icon.isEmpty()) {
                        dot(gg, x + 9 * s + 2.5f * s, y + ROW_H * s / 2, 3 * s, 0xFFF87171);
                    }
                    x += 15 * s;
                } else if (row.sprite instanceof ItemStack stack && !stack.isEmpty()) {
                    Render2D.round(gg, x, y + (ROW_H * s - 11 * s) / 2, 11 * s, 11 * s, 3 * s, DARK);
                    item(gg, stack, x + s, y + (ROW_H * s - 11 * s) / 2 + s, 9 * s);
                    x += 15 * s;
                }
                float mid = fBody(s).centeredBaseline(y, ROW_H * s);
                textShadow(gg, fBody(s), row.left, x, mid, Style.WHITE, s);
                if (!row.right.isEmpty()) {
                    if ("Binds".equals(d.title)) {
                        float bw = fMicro(s).width(row.right) + 9 * s;
                        float by = y + (ROW_H * s - 10 * s) / 2;
                        Render2D.round(gg, d.w - PAD * s - bw, by, bw, 10 * s, 3.5f * s, DARK);
                        Render2D.round(gg, d.w - PAD * s - bw, by, bw, 10 * s, 3.5f * s,
                                Render2D.withAlpha(Style.accent(), 0.35f));
                        fMicro(s).drawCentered(gg, row.right, d.w - PAD * s - bw / 2,
                                fMicro(s).centeredBaseline(by, 10 * s), Style.accent());
                    } else {
                        int color = row.sprite instanceof MobEffectInstance effect
                                && effect.getDuration() >= 0 && effect.getDuration() < 200
                                ? 0xFFEB3C46 : GREY;
                        if (el_isCooldown(d)) {
                            color = Style.accent();
                        }
                        fBody(s).drawRight(gg, row.right, d.w - PAD * s, mid, color);
                    }
                }
                Render2D.popTransform(gg);
                Render2D.popAlpha();
                y += ROW_H * s * a.alpha;
            }
        } finally {
            Render2D.popClip(gg);
        }
    }

    private static boolean el_isCooldown(ListData d) {
        return "Cooldowns".equals(d.title);
    }

    /** ArrayList: стеклянные пилюли с акцентным краем и тенью. */
    private static void drawArrayPills(GuiGraphicsExtractor gg, float s, ListData d) {
        float rowH = 15 * s;
        float gap = 3 * s;
        float y = s;
        boolean right = BOUNDS.get(HudLayout.Element.ARRAYLIST)[0] > Render2D.screenWidth() / 2f;
        for (var a : d.rows) {
            Row row = a.row;
            Render2D.pushAlpha(a.alpha);
            float slide = a.slide * (right ? -1 : 1);
            Render2D.pushTranslate(gg, slide, y);
            float tw = fBody(s).width(row.left);
            float pw = tw + PAD * 2 * s + 3 * s;
            float px = right ? d.w - pw : 0;
            float r = 5.5f * s;
            Render2D.round(gg, px, 1.5f * s, pw, rowH, r, 0x30000000);
            Render2D.round(gg, px, 0, pw, rowH, r, BORDER);
            float inset = Math.max(1.0f, s * 0.5f);
            if (Interface.hudBlur()) {
                Identifier tex = frostTexture();
                if (tex != null) {
                    Render2D.pushClip(gg, px, y, pw, rowH);
                    snapshot(gg, tex, px + inset, inset, pw - inset * 2, rowH - inset * 2, r - inset);
                    Render2D.popClip(gg);
                }
            }
            Render2D.round(gg, px + inset, inset, pw - inset * 2, rowH - inset * 2,
                    Math.max(1.0f, r - inset), SHADE);
            // Акцентный край со стороны экрана.
            float edgeX = right ? px + pw - 2.5f * s : px;
            Render2D.round(gg, edgeX, 3 * s, 2.5f * s, rowH - 6 * s, s, Style.accent());
            float tx = right ? px + PAD * s : px + PAD * s + 1 * s;
            textShadow(gg, fBody(s), row.left, tx,
                    fBody(s).centeredBaseline(0, rowH), Style.WHITE, s);
            Render2D.popTransform(gg);
            Render2D.popAlpha();
            y += (rowH + gap) * a.alpha;
        }
    }

    /** Уведомления: стеклянные тосты с иконкой-знаком. */
    private static void drawToasts(GuiGraphicsExtractor gg, float s, ListData d) {
        float rowH = 19 * s;
        float gap = 4 * s;
        float y = s;
        for (var a : d.rows) {
            Row row = a.row;
            Render2D.pushAlpha(a.alpha);
            Render2D.pushTranslate(gg, a.slide, y);
            int tint = "+".equals(row.icon) ? 0xFF4ADE80 : 0xFFF87171;
            float r = 6 * s;
            Render2D.round(gg, 0, 1.5f * s, d.w, rowH, r, 0x30000000);
            Render2D.round(gg, 0, 0, d.w, rowH, r, BORDER);
            float inset = Math.max(1.0f, s * 0.5f);
            if (Interface.hudBlur()) {
                Identifier tex = frostTexture();
                if (tex != null) {
                    Render2D.pushClip(gg, 0, y, d.w, rowH);
                    snapshot(gg, tex, inset, inset, d.w - inset * 2, rowH - inset * 2, r - inset);
                    Render2D.popClip(gg);
                }
            }
            Render2D.round(gg, inset, inset, d.w - inset * 2, rowH - inset * 2,
                    Math.max(1.0f, r - inset), SHADE);
            float badge = 12 * s;
            float bx = PAD * s;
            float by = (rowH - badge) / 2;
            Render2D.round(gg, bx, by, badge, badge, 4 * s, Render2D.withAlpha(tint, 0.16f));
            Render2D.round(gg, bx, by, badge, badge, 4 * s, Render2D.withAlpha(tint, 0.5f));
            fBody(s).drawCentered(gg, row.icon, bx + badge / 2,
                    fBody(s).centeredBaseline(by, badge), tint);
            textShadow(gg, fSmall(s), row.left, bx + badge + 5 * s,
                    fSmall(s).centeredBaseline(0, rowH), Style.WHITE, s);
            Render2D.popTransform(gg);
            Render2D.popAlpha();
            y += (rowH + gap) * a.alpha;
        }
    }

    // ---------- пилюли ----------

    private static PillData measurePill(HudLayout.Element el, float s) {
        Minecraft mc = Minecraft.getInstance();
        String label;
        String text;
        ItemStack item = ItemStack.EMPTY;
        switch (el) {
            case TICKS -> {
                label = "Ticks";
                text = String.format(Locale.ROOT, "%.2f", mc.hasSingleplayerServer() ? 20 : QuickTelemetry.tps());
            }
            case PING -> {
                PlayerInfo info = mc.getConnection() == null ? null
                        : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                label = "Ping";
                text = String.valueOf(info == null ? 0 : info.getLatency());
            }
            case FPS -> {
                label = "FPS";
                text = String.valueOf(mc.getFps());
            }
            case BPS -> {
                label = "BPS";
                text = String.format(Locale.ROOT, "%.2f",
                        Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo) * 20);
            }
            case XYZ -> {
                label = "XYZ";
                text = String.format(Locale.ROOT, "%.1f  %.1f  %.1f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ());
            }
            case TOTEMS -> {
                int count = 0;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.is(Items.TOTEM_OF_UNDYING)) {
                        count += stack.getCount();
                    }
                }
                label = "Totems";
                text = String.valueOf(count);
                item = new ItemStack(Items.TOTEM_OF_UNDYING);
            }
            default -> {
                return null;
            }
        }
        float w = PAD * s + 4 * s + trackedWidth(fMicro(s), label.toUpperCase(Locale.ROOT), 0.7f * s)
                + 6 * s + fBody(s).width(text) + (item.isEmpty() ? 0 : 15 * s) + PAD * s;
        return new PillData(w, 17 * s, label, text, item);
    }

    private static void drawPill(GuiGraphicsExtractor gg, float s, PillData d) {
        float x = PAD * s;
        dot(gg, x + 2 * s, d.h / 2, 4 * s, Style.accent());
        x += 7 * s;
        x = tracked(gg, fMicro(s), d.label.toUpperCase(Locale.ROOT), x,
                fMicro(s).centeredBaseline(0, d.h), DIM, 0.7f * s);
        x = textShadow(gg, fBody(s), d.text, x + 6 * s, fBody(s).centeredBaseline(0, d.h), Style.WHITE, s);
        if (!d.item.isEmpty()) {
            Render2D.round(gg, x + 5 * s, (d.h - 12 * s) / 2, 12 * s, 12 * s, 3.5f * s, DARK);
            item(gg, d.item, x + 6 * s, (d.h - 12 * s) / 2 + s, 10 * s);
        }
    }

    // ---------- броня ----------

    private static ArmorData measureArmor(float s) {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> items = new ArrayList<>();
        items.add(mc.player.getMainHandItem());
        items.addAll(armorPieces(mc.player));
        items.add(mc.player.getOffhandItem());
        if (items.stream().allMatch(ItemStack::isEmpty) && !editing()) {
            return null;
        }
        float cell = 21 * s;
        float gap = 3 * s;
        HudLayout.Entry entry = HudLayout.get(HudLayout.Element.ARMOR);
        float probe = entry.positioned() ? entry.x : Render2D.screenWidth() / 2f;
        boolean vertical = probe < (cell * 6 + gap * 5) + 10 * s
                || probe > Render2D.screenWidth() - (cell * 6 + gap * 5) - 10 * s;
        if (vertical) {
            return new ArmorData(PAD * 2 * s + cell, PAD * 2 * s + cell * 6 + gap * 5, items, true);
        }
        return new ArmorData(PAD * 2 * s + cell * 6 + gap * 5, PAD * 2 * s + cell, items, false);
    }

    private static void drawArmor(GuiGraphicsExtractor gg, float s, ArmorData d) {
        float cell = 21 * s;
        float gap = 3 * s;
        for (int i = 0; i < d.items.size(); i++) {
            float x = PAD * s + (d.vertical ? 0 : i * (cell + gap));
            float y = PAD * s + (d.vertical ? i * (cell + gap) : 0);
            ItemStack item = d.items.get(i);
            Render2D.round(gg, x, y, cell, cell, 5 * s, DARK);
            Render2D.round(gg, x, y, cell, cell, 5 * s, HAIR);
            Render2D.round(gg, x + 1, y + 1, cell - 2, (cell - 2) / 2, 4 * s,
                    Render2D.withAlpha(0xFFFFFFFF, 0.05f));
            item(gg, item, x + 3.5f * s, y + 3.5f * s, cell - 7 * s);
            if (!item.isEmpty()) {
                int count = item.getCount();
                if (count > 1) {
                    fMicro(s).drawRight(gg, String.valueOf(count), x + cell - 2.5f * s,
                            y + cell - 1.5f * s, GREY);
                }
                if (item.isDamageableItem()) {
                    float f = durabilityFrac(item);
                    float bw = cell - 7 * s;
                    Render2D.round(gg, x + 3.5f * s, y + cell - 4.5f * s, bw, 2 * s, s, TRACK);
                    Render2D.round(gg, x + 3.5f * s, y + cell - 4.5f * s,
                            Math.max(2 * s, bw * f), 2 * s, s, durabilityColor(item));
                }
            }
        }
    }

    // ---------- цель ----------

    private static TargetData measureTarget(float s) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity current = editing() ? mc.player : resolveTarget(mc);
        if (targetFrame != frame) {
            targetFrame = frame;
            if (current != null) {
                lastTarget = current;
            }
            targetShow += ((current != null ? 1 : 0) - targetShow) * (1 - (float) Math.exp(-15 * delta));
        }
        LivingEntity t = current != null ? current : lastTarget;
        if (t == null || targetShow < 0.01f) {
            return null;
        }
        float max = Math.max(1, t.getMaxHealth());
        return new TargetData(164 * s, 48 * s, t,
                QuickFont.of("bold", 7.0f * s).trim(t.getName().getString(), 78 * s),
                String.format(Locale.ROOT, "%.1f", t.getHealth() + t.getAbsorptionAmount()),
                mc.player.distanceTo(t),
                Mth.clamp(t.getHealth() / max, 0, 1),
                Mth.clamp(t.getAbsorptionAmount() / max, 0, 1));
    }

    private static void drawTarget(GuiGraphicsExtractor gg, float s, TargetData d) {
        if (healthTarget != d.target) {
            healthTarget = d.target;
            healthAnimation = d.health;
            damageAnimation = d.health;
        }
        healthAnimation += (d.health - healthAnimation) * (1 - (float) Math.exp(-18 * delta));
        damageAnimation += (d.health - damageAnimation) * (1 - (float) Math.exp(-5 * delta));
        Minecraft mc = Minecraft.getInstance();
        float x0 = PAD * s;
        float avatar = 32 * s;
        float ay = (d.h - avatar) / 2;
        // Аватар со скруглением и рамкой по состоянию здоровья.
        int ring = d.health > 0.5f ? Style.accent() : d.health > 0.25f ? GOLD : 0xFFF87171;
        PlayerInfo info = d.target instanceof Player player && mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(player.getUUID()) : null;
        if (info != null) {
            drawHead(gg, info, x0, ay, avatar, 6 * s);
        } else {
            Render2D.round(gg, x0, ay, avatar, avatar, 6 * s, DARK);
            fTitle(s).drawCentered(gg, d.name.isEmpty() ? "?" : d.name.substring(0, 1),
                    x0 + avatar / 2, fTitle(s).visualBaseline("H", ay, avatar), Style.accent());
        }
        Render2D.round(gg, x0, ay, avatar, avatar, 6 * s, Render2D.withAlpha(ring, 0.55f));
        float x = x0 + avatar + 6 * s;
        float w = d.w - x - PAD * s;
        // Имя + HP.
        textShadow(gg, fTitle(s), d.name, x, 13.5f * s, Style.WHITE, s);
        fBody(s).drawRight(gg, d.hp, d.w - PAD * s, 13.5f * s, Style.accent());
        // Глянцевый бар: трек, гост урона, заливка, блик.
        float barY = 20 * s;
        float barH = 6 * s;
        float barR = 3 * s;
        Render2D.round(gg, x, barY, w, barH, barR, TRACK);
        Render2D.round(gg, x, barY, w * Mth.clamp(damageAnimation, 0, 1), barH, barR,
                Render2D.withAlpha(0xFFFFFFFF, 0.4f));
        Render2D.round(gg, x, barY, w * Mth.clamp(healthAnimation, 0, 1), barH, barR, ring);
        Render2D.pushClip(gg, x, barY, w * Mth.clamp(healthAnimation, 0, 1), barH / 2);
        Render2D.round(gg, x, barY, w * Mth.clamp(healthAnimation, 0, 1), barH, barR,
                Render2D.withAlpha(0xFFFFFFFF, 0.28f));
        Render2D.popClip(gg);
        if (d.absorption > 0) {
            Render2D.round(gg, x, barY - 2.5f * s, w * Math.min(1, d.absorption), 1.5f * s,
                    s * 0.75f, GOLD);
        }
        // Низ: дистанция с точкой + предмет в ячейке.
        float by = 33.5f * s;
        dot(gg, x + 2 * s, by - 2 * s, 3.5f * s, 0xFF4ADE80);
        textShadow(gg, fSmall(s), String.format(Locale.ROOT, "%.1f m", d.dist),
                x + 6 * s, by, GREY, s);
        String held = d.target.getMainHandItem().isEmpty() ? "Empty hand"
                : d.target.getMainHandItem().getHoverName().getString();
        held = QuickFont.of("medium", 6.0f * s).trim(held, 56 * s);
        fSmall(s).drawRight(gg, held, d.w - PAD * s - 13 * s, by, DIM);
        Render2D.round(gg, d.w - PAD * s - 11 * s, by - 8 * s, 11 * s, 11 * s, 3 * s, DARK);
        Render2D.round(gg, d.w - PAD * s - 11 * s, by - 8 * s, 11 * s, 11 * s, 3 * s, HAIR);
        item(gg, d.target.getMainHandItem(), d.w - PAD * s - 10 * s, by - 7 * s, 9 * s);
        // Тонкая акцентная линия снизу панели.
        gradientLine(gg, x0, d.h - PAD * 0.5f * s, (d.w - x0 - PAD * s) * 0.5f, s);
    }

    private static void drawHead(GuiGraphicsExtractor gg, PlayerInfo info, float x, float y, float d, float r) {
        Render2D.pushClip(gg, x, y, d, d);
        Identifier id = info.getSkin().body().texturePath();
        int px = Math.round(x), py = Math.round(y), size = Math.round(d);
        int color = Render2D.withAlpha(0xFFFFFFFF, Render2D.alpha());
        gg.blit(RenderPipelines.GUI_TEXTURED, id, px, py, 8, 8, size, size, 8, 8, 64, 64, color);
        if (info.showHat()) {
            gg.blit(RenderPipelines.GUI_TEXTURED, id, px, py, 40, 8, size, size, 8, 8, 64, 64, color);
        }
        Render2D.popClip(gg);
    }

    private static void item(GuiGraphicsExtractor gg, ItemStack stack, float x, float y, float d) {
        if (stack.isEmpty()) {
            return;
        }
        Render2D.pushTranslate(gg, x, y);
        Render2D.pushScale(gg, 0, 0, d / 16);
        gg.item(Minecraft.getInstance().player, stack, 0, 0, 0);
        Render2D.popTransform(gg);
        Render2D.popTransform(gg);
    }

    private static List<ItemStack> armorPieces(LivingEntity living) {
        List<ItemStack> out = new ArrayList<>(4);
        out.add(living.getItemBySlot(EquipmentSlot.HEAD));
        out.add(living.getItemBySlot(EquipmentSlot.CHEST));
        out.add(living.getItemBySlot(EquipmentSlot.LEGS));
        out.add(living.getItemBySlot(EquipmentSlot.FEET));
        return out;
    }

    private static float durabilityFrac(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (stack.isEmpty() || max <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (max - stack.getDamageValue()) / (float) max));
    }

    private static int durabilityColor(ItemStack stack) {
        int max = stack.getMaxDamage();
        float f = max <= 0 ? 1.0f : (max - stack.getDamageValue()) / (float) max;
        return f > 0.6f ? 0xFF4ADE80 : f > 0.3f ? GOLD : 0xFFF87171;
    }

    // ---------- эффекты: имена ----------

    private static final Map<String, String> EFFECT_NAMES = new HashMap<>();

    private static String effectBaseName(MobEffectInstance instance) {
        String key = instance.getEffect().value().getDescriptionId();
        String cached = EFFECT_NAMES.get(key);
        if (cached != null) {
            return cached;
        }
        String name = Component.translatable(instance.getEffect().value().getDescriptionId()).getString();
        if (EFFECT_NAMES.size() < 256) {
            EFFECT_NAMES.put(key, name);
        }
        return name;
    }

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

    // ---------- хотбар ----------

    private static HbData measureHotbar(float scale) {
        float s = scale * (float) Interface.hotbarSize();
        float slot = 24.0f * s;
        float gap = 3.0f * s;
        float extra = Minecraft.getInstance().player.getOffhandItem().isEmpty() ? 0 : slot + 6 * s;
        return new HbData(PAD * 2 * s + slot * 9.0f + gap * 8.0f + extra,
                PAD * 2 * s + slot + 2 * s, s);
    }

    private static void drawHotbar(GuiGraphicsExtractor gg, float s, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        float slot = 24.0f * s;
        float gap = 3.0f * s;
        float y = PAD * s;
        float off = mc.player.getOffhandItem().isEmpty() ? 0 : slot + 6 * s;
        for (int i = 0; i < 9; i++) {
            float sx = PAD * s + off + i * (slot + gap);
            boolean selected = i == inv.getSelectedSlot();
            if (selected) {
                Render2D.round(gg, sx - s, y - s, slot + 2 * s, slot + 2 * s, 7 * s,
                        Render2D.withAlpha(Style.accent(), 0.22f));
            }
            Render2D.round(gg, sx, y, slot, slot, 6 * s, selected ? 0xFF1B1B21 : 0xB0121216);
            Render2D.round(gg, sx, y, slot, slot, 6 * s,
                    selected ? Style.accent() : Render2D.withAlpha(0xFFFFFFFF, 0.10f));
            Render2D.round(gg, sx + 1, y + 1, slot - 2, (slot - 2) / 2, 5 * s,
                    Render2D.withAlpha(0xFFFFFFFF, selected ? 0.08f : 0.04f));
            fMicro(s).draw(gg, String.valueOf(i + 1), sx + 3 * s, y + 7.5f * s,
                    selected ? Style.accent() : DIM);
            item(gg, inv.getItem(i), sx + 4 * s, y + 4 * s, slot - 8 * s);
            if (selected) {
                Render2D.round(gg, sx + 5 * s, y + slot - 3.5f * s, slot - 10 * s, 2 * s,
                        s, Style.accent());
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            Render2D.round(gg, PAD * s, y, slot, slot, 6 * s, 0xB0121216);
            Render2D.round(gg, PAD * s, y, slot, slot, 6 * s,
                    Render2D.withAlpha(0xFFFFFFFF, 0.10f));
            item(gg, offhand, PAD * s + 4 * s, y + 4 * s, slot - 8 * s);
        }
    }

    // ---------- цель: поиск ----------

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

    // ---------- медиа 2D ----------

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
