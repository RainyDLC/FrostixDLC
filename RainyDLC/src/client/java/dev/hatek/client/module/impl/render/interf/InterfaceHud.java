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
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HUD RainyDLC «Rainy Glass» — переписан с нуля под стиль ClickGUI:
 * матовое стекло (снимок мира + блюр, как в гуи), тонкая рамка, акцент.
 *
 * Позиции/якоря/масштабы из hud.json совместимы со старым дизайном,
 * редактирование (чат + ЛКМ/колесо/ПКМ-меню) работает как раньше.
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

    // ---------- дизайн-токены ----------
    private static final float RADIUS = 7.0f;
    private static final float PAD = 6.0f;
    private static final float HEADER_H = 15.0f;
    private static final float ROW_H = 12.0f;
    private static final int SHADE = 0x99111416;
    private static final int BORDER = 0x2EFFFFFF;
    private static final int HAIRLINE = 0x1FFFFFFF;
    private static final int GREY = 0xFFB9B9C2;
    private static final int DIM = 0xFF7E7E88;

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

    private record PillData(float w, float h, String text, ItemStack item) implements Sized {
    }

    private record WatermarkData(float w, float h, String[] parts) implements Sized {
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
        // Первым слоем: блюр и возврат чёткости не задевают ванильный HUD поверх нас.
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

    private static QuickFont sTitle(float s) {
        return QuickFont.of("bold", 7.5f * s);
    }

    private static QuickFont sBody(float s) {
        return QuickFont.of("bold", 7 * s);
    }

    private static QuickFont sLabel(float s) {
        return QuickFont.of("medium", 6.5f * s);
    }

    private static QuickFont sSmall(float s) {
        return QuickFont.of("medium", 6 * s);
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

        boolean noScreen = mc.gui.screen() == null;
        boolean blurred = false;
        if (!placed.isEmpty() && Interface.hudBlur() && noScreen) {
            try {
                mc.gameRenderer.processBlurEffect();
                blurred = LiquidBackdrop.frostReady();
            } catch (Throwable ignored) {
            }
        }
        if (blurred) {
            restoreSharpExcept(gg, placed);
        }

        Render2D.begin(gg);
        try {
            for (Placed p : placed) {
                var entry = HudLayout.get(p.el);
                Render2D.pushAlpha(entry.opacity);
                try {
                    drawGlass(gg, p.x, p.y, p.data.w(), p.data.h(), p.s);
                    Render2D.pushTranslate(gg, p.x, p.y);
                    try {
                        draw(gg, p.el, p.s, p.data);
                    } finally {
                        Render2D.popTransform(gg);
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

    /** Снимок для стекла: матовый после блюра, иначе чёткий. */
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
     * Стеклянная карточка в абсолютных координатах: снимок мира, затемнение,
     * рамка и верхний блик — как панели ClickGUI.
     */
    private static void glassCard(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        float r = RADIUS * s;
        Render2D.round(gg, x, y, w, h, r, BORDER);
        float inset = Math.max(1.0f, s * 0.5f);
        Identifier tex = frostTexture();
        if (tex != null) {
            snapshot(gg, tex, x + inset, y + inset, w - inset * 2, h - inset * 2, r);
        }
        Render2D.round(gg, x + inset, y + inset, w - inset * 2, h - inset * 2,
                Math.max(0.5f, r - inset), SHADE);
        Render2D.round(gg, x + inset + s, y + inset + s * 0.5f, w - (inset + s) * 2,
                Math.max(1.0f, s * 0.5f), s * 0.25f, 0x14FFFFFF);
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

    /** Возвращает чёткий мир везде, кроме стеклянных карточек. */
    private static void restoreSharpExcept(GuiGraphicsExtractor gg, List<Placed> placed) {
        if (!LiquidBackdrop.sharpReady()) {
            return;
        }
        float sw = Render2D.screenWidth(), sh = Render2D.screenHeight();
        List<float[]> rects = new ArrayList<>();
        for (Placed p : placed) {
            rects.add(new float[]{p.x, p.y, p.x + p.data.w(), p.y + p.data.h()});
        }
        List<Float> edges = new ArrayList<>();
        edges.add(0.0f);
        edges.add((float) sh);
        for (float[] rc : rects) {
            edges.add(Mth.clamp(rc[1], 0.0f, (float) sh));
            edges.add(Mth.clamp(rc[3], 0.0f, (float) sh));
        }
        edges.sort(Float::compare);
        float prev = -1.0f;
        for (float edge : edges) {
            if (edge <= prev) {
                continue;
            }
            if (prev >= 0.0f && edge > prev) {
                restoreBand(gg, prev, edge, rects, sw);
            }
            prev = edge;
        }
    }

    private static void restoreBand(GuiGraphicsExtractor gg, float y0, float y1,
                                    List<float[]> rects, float sw) {
        List<float[]> spans = new ArrayList<>();
        for (float[] rc : rects) {
            if (rc[1] < y1 && rc[3] > y0) {
                spans.add(new float[]{Math.max(0.0f, rc[0]), Math.min(sw, rc[2])});
            }
        }
        spans.sort(Comparator.comparingDouble(a -> a[0]));
        float cursor = 0.0f;
        for (float[] span : spans) {
            if (span[0] > cursor) {
                blitSharp(gg, cursor, y0, span[0] - cursor, y1 - y0);
            }
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < sw) {
            blitSharp(gg, cursor, y0, sw - cursor, y1 - y0);
        }
    }

    private static void blitSharp(GuiGraphicsExtractor gg, float x, float y, float w, float h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int tw = LiquidBackdrop.width(), th = LiquidBackdrop.height();
        gg.blit(QuickPipelines.BLUR, LiquidBackdrop.SHARP,
                Math.round(x), Math.round(y), x, th - y, Math.round(w), Math.round(h),
                Math.round(w), Math.round(-h), tw, th, 0xFFFFFFFF);
    }

    private static void drawGlass(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        glassCard(gg, x, y, w, h, s);
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
            case KEYBINDS, EFFECTS, STAFF, COOLDOWNS, ARRAYLIST, NOTIFICATIONS ->
                    drawList(gg, el, s, (ListData) data);
            default -> drawPill(gg, s, (PillData) data);
        }
    }

    // ---------- вотермарка ----------

    private static WatermarkData measureWatermark(float s) {
        Minecraft mc = Minecraft.getInstance();
        String[] parts = {"RainyDLC", mc.player.getName().getString(),
                "FPS " + mc.getFps(), LocalTime.now().format(CLOCK)};
        float w = PAD * 2 * s + sTitle(s).width("R");
        for (int i = 0; i < parts.length; i++) {
            w += (i == 0 ? sTitle(s) : sBody(s)).width(parts[i]) + (i == 0 ? 0 : 9 * s);
        }
        return new WatermarkData(w, 17 * s, parts);
    }

    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WatermarkData d) {
        float x = PAD * s;
        float cy = d.h / 2;
        Render2D.round(gg, x, cy - 4 * s, 8 * s, 8 * s, 2.5f * s, Style.accent());
        sSmall(s).drawCentered(gg, "R", x + 4 * s, cy + sSmall(s).ascent() / 2 - 1, 0xFF101014);
        x += 11 * s;
        for (int i = 0; i < d.parts.length; i++) {
            if (i > 0) {
                Render2D.rect(gg, x, cy - 5 * s, Math.max(1, s * 0.5f), 10 * s, HAIRLINE);
                x += 4.5f * s;
            }
            if (i == 0) {
                x = sTitle(s).draw(gg, d.parts[i], x, sTitle(s).centeredBaseline(0, d.h), Style.accent());
            } else {
                x = sBody(s).draw(gg, d.parts[i], x, sBody(s).centeredBaseline(0, d.h),
                        i == 1 ? Style.WHITE : GREY);
            }
            x += 4.5f * s;
        }
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
                        "", info));
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
        rows.sort(Comparator.comparingDouble((Row r) -> sBody(s).width(r.left)).reversed());
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Interface", "", "", null));
        }
        return rows;
    }

    private static List<Row> notificationRows() {
        List<Row> rows = new ArrayList<>();
        for (var n : QuickNotifications.current()) {
            rows.add(new Row("notice-" + n.created(), n.text(), "", n.enabled() ? "J" : "K", null));
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "Пример уведомления", "", "J", null));
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
        boolean separate = title.isEmpty();
        if (el == HudLayout.Element.ARRAYLIST) {
            rows.sort(Comparator.comparingDouble((RowAnim a) -> sBody(s).width(a.row.left)).reversed());
        }
        if (rows.isEmpty() && !editing()) {
            return null;
        }
        float rowH = (el == HudLayout.Element.NOTIFICATIONS ? 17 : separate ? 15 : ROW_H) * s;
        float w = separate ? 44 * s : 92 * s;
        float h = separate ? 0 : HEADER_H * s + PAD * s;
        for (var a : rows) {
            Row row = a.row;
            boolean sprite = row.sprite != null;
            float rw = (sprite ? 26 : PAD * 2) * s + sBody(s).width(row.left)
                    + sBody(s).width(row.right) + (row.right.isEmpty() ? 0 : 10 * s);
            if (!row.icon.isEmpty()) {
                rw += 12 * s;
            }
            w = Math.max(w, rw);
            h += rowH * a.alpha;
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

    private static void drawList(GuiGraphicsExtractor gg, HudLayout.Element el, float s, ListData d) {
        boolean separate = d.title.isEmpty();
        float y = separate ? 0 : HEADER_H * s + PAD * 0.5f * s;
        if (!separate) {
            sTitle(s).draw(gg, d.title, PAD * s, sTitle(s).centeredBaseline(0, HEADER_H * s), Style.WHITE);
            sLabel(s).drawRight(gg, String.valueOf(d.rows.size()), d.w - PAD * s,
                    sLabel(s).centeredBaseline(0, HEADER_H * s), Style.accent());
            Render2D.rect(gg, PAD * s, HEADER_H * s, d.w - PAD * 2 * s,
                    Math.max(1, s * 0.5f), HAIRLINE);
        }
        float clipY = separate ? -PAD * s : HEADER_H * s + PAD * 0.5f * s;
        float clipH = d.h - clipY + (separate ? PAD * s : 0);
        Render2D.pushClip(gg, separate ? -PAD * s : 0, clipY, d.w + (separate ? PAD * 2 * s : 0), clipH);
        try {
            for (var a : d.rows) {
                Row row = a.row;
                float rowH = (el == HudLayout.Element.NOTIFICATIONS ? 17 : separate ? 15 : ROW_H) * s;
                Render2D.pushAlpha(a.alpha);
                float slide = a.slide * (el == HudLayout.Element.ARRAYLIST
                        && BOUNDS.get(el)[0] > Render2D.screenWidth() / 2f ? -1 : 1);
                Render2D.pushTranslate(gg, slide, 0);
                float x = PAD * s;
                if (separate && el == HudLayout.Element.ARRAYLIST) {
                    float rw = sBody(s).width(row.left) + PAD * 2 * s;
                    boolean right = BOUNDS.get(el)[0] > Render2D.screenWidth() / 2f;
                    float rx = right ? d.w - rw : 0;
                    glassChip(gg, rx, y, rw, rowH - 2 * s, s);
                    if (!row.icon.isEmpty()) {
                        QuickFont.of("icons_nurik", 8 * s).draw(gg, row.icon, rx + 4 * s,
                                QuickFont.of("icons_nurik", 8 * s).centeredBaseline(y, rowH - 2 * s),
                                Style.accent());
                        x = rx + 16 * s;
                    } else {
                        x = rx + PAD * s;
                    }
                    sBody(s).draw(gg, row.left, x,
                            sBody(s).centeredBaseline(y, rowH - 2 * s), Style.accent());
                } else if (separate) {
                    boolean on = !"K".equals(row.icon);
                    Render2D.round(gg, 0, y, 2.5f * s, rowH - 2 * s, s,
                            on ? Style.accent() : 0xFFF87171);
                    x = PAD * s;
                    if (!row.icon.isEmpty()) {
                        x = sBody(s).draw(gg, row.icon.equals("J") ? "+" : "–", x,
                                sBody(s).centeredBaseline(y, rowH - 2 * s),
                                on ? 0xFF4ADE80 : 0xFFF87171);
                        x += 4 * s;
                    }
                    x = sBody(s).draw(gg, row.left, x,
                            sBody(s).centeredBaseline(y, rowH - 2 * s), Style.WHITE);
                } else {
                    if (row.sprite instanceof MobEffectInstance effect) {
                        gg.blitSprite(RenderPipelines.GUI_TEXTURED,
                                Hud.getMobEffectSprite(effect.getEffect()),
                                Math.round(x), Math.round(y + s),
                                Math.round(9 * s), Math.round(9 * s),
                                Render2D.withAlpha(0xFFFFFFFF, Render2D.alpha()));
                        x += 13 * s;
                    } else if (row.sprite instanceof PlayerInfo player) {
                        drawHead(gg, player, x, y + 1.5f * s, 8 * s);
                        x += 12 * s;
                    } else if (row.sprite instanceof ItemStack stack && !stack.isEmpty()) {
                        item(gg, stack, x, y, 10 * s);
                        x += 14 * s;
                    }
                    sBody(s).draw(gg, row.left, x,
                            sBody(s).centeredBaseline(y, rowH), Style.WHITE);
                    int color = row.sprite instanceof MobEffectInstance effect
                            && effect.getDuration() >= 0 && effect.getDuration() < 200
                            ? 0xFFEB3C46 : GREY;
                    if (!row.right.isEmpty()) {
                        if (el == HudLayout.Element.KEYBINDS) {
                            float bw = sLabel(s).width(row.right) + 8 * s;
                            Render2D.round(gg, d.w - PAD * s - bw, y + (rowH - 9 * s) / 2,
                                    bw, 9 * s, 2.5f * s, 0x33FFFFFF);
                            sLabel(s).drawCentered(gg, row.right, d.w - PAD * s - bw / 2,
                                    sLabel(s).centeredBaseline(y + (rowH - 9 * s) / 2, 9 * s),
                                    Style.accent());
                        } else {
                            sBody(s).drawRight(gg, row.right, d.w - PAD * s,
                                    sBody(s).centeredBaseline(y, rowH), color);
                        }
                    }
                }
                Render2D.popTransform(gg);
                Render2D.popAlpha();
                y += rowH * a.alpha;
            }
        } finally {
            Render2D.popClip(gg);
        }
    }

    /** Маленькая стеклянная плашка для строк ArrayList. */
    private static void glassChip(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        Render2D.round(gg, x, y, w, h, 4 * s, Render2D.withAlpha(Style.accent(), 0.10f));
        Render2D.round(gg, x + 1, y + 1, w - 2, h - 2, 3.5f * s, 0xB0101013);
    }

    // ---------- пилюли ----------

    private static PillData measurePill(HudLayout.Element el, float s) {
        Minecraft mc = Minecraft.getInstance();
        String text;
        ItemStack item = ItemStack.EMPTY;
        switch (el) {
            case TICKS ->
                    text = String.format(Locale.ROOT, "%.2f Ticks", mc.hasSingleplayerServer() ? 20 : QuickTelemetry.tps());
            case PING -> {
                PlayerInfo info = mc.getConnection() == null ? null
                        : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                text = (info == null ? 0 : info.getLatency()) + " Ping";
            }
            case FPS -> text = mc.getFps() + " FPS";
            case BPS -> text = String.format(Locale.ROOT, "%.2f BPS",
                    Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo) * 20);
            case XYZ -> text = String.format(Locale.ROOT, "%.1f, %.1f, %.1f XYZ",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ());
            case TOTEMS -> {
                int count = 0;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.is(Items.TOTEM_OF_UNDYING)) {
                        count += stack.getCount();
                    }
                }
                text = String.valueOf(count);
                item = new ItemStack(Items.TOTEM_OF_UNDYING);
            }
            default -> {
                return null;
            }
        }
        return new PillData(PAD * 2 * s + 5 * s + sBody(s).width(text) + (item.isEmpty() ? 0 : 15 * s),
                16 * s, text, item);
    }

    private static void drawPill(GuiGraphicsExtractor gg, float s, PillData d) {
        float x = PAD * s;
        float cy = d.h / 2;
        Render2D.circle(gg, x, cy - 2 * s, 4 * s, Style.accent());
        x += 8 * s;
        if (!d.item.isEmpty()) {
            item(gg, d.item, x, cy - 6.5f * s, 13 * s);
            x += 15 * s;
        }
        sBody(s).draw(gg, d.text, x, sBody(s).centeredBaseline(0, d.h), Style.WHITE);
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
        float longSide = 104 * s, shortSide = 20 * s;
        HudLayout.Entry entry = HudLayout.get(HudLayout.Element.ARMOR);
        float x = entry.positioned() ? entry.x : (Render2D.screenWidth() - longSide) / 2;
        boolean vertical = x < longSide + 3 * s || x > Render2D.screenWidth() - longSide - 3 * s;
        return new ArmorData(vertical ? shortSide : longSide, vertical ? longSide : shortSide, items, vertical);
    }

    private static void drawArmor(GuiGraphicsExtractor gg, float s, ArmorData d) {
        for (int i = 0; i < d.items.size(); i++) {
            float x = 3.5f * s + (d.vertical ? 0 : i * 16.5f * s);
            float y = 3.5f * s + (d.vertical ? i * 16.5f * s : 0);
            ItemStack item = d.items.get(i);
            item(gg, item, x, y, 13 * s);
            if (item.isDamageableItem()) {
                Render2D.round(gg, x, y + 11.5f * s, 13 * s * durabilityFrac(item),
                        1.5f * s, s * 0.5f, durabilityColor(item));
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
        return new TargetData(122 * s, 32 * s, t,
                QuickFont.of("bold", 6.5f * s).trim(t.getName().getString(), 46 * s),
                String.format(Locale.ROOT, "%.1f HP", t.getHealth() + t.getAbsorptionAmount()),
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
        PlayerInfo info = d.target instanceof Player player && mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(player.getUUID()) : null;
        if (info != null) {
            drawHead(gg, info, 5 * s, 5 * s, 22 * s);
        } else {
            Render2D.round(gg, 5 * s, 5 * s, 22 * s, 22 * s, 4 * s, 0xFF23232B);
            sTitle(s).drawCentered(gg, d.name.isEmpty() ? "?" : d.name.substring(0, 1),
                    16 * s, 16 * s + sTitle(s).ascent() / 2, Style.accent());
        }
        float x = 32 * s, w = 84 * s;
        QuickFont.of("bold", 6.5f * s).draw(gg, d.name, x, 10.5f * s, Style.WHITE);
        sLabel(s).drawRight(gg, d.hp, d.w - 5 * s, 10.5f * s, Style.accent());
        Render2D.round(gg, x, 15 * s, w, 3.5f * s, 1.75f * s, 0x30FFFFFF);
        Render2D.round(gg, x, 15 * s, w * damageAnimation, 3.5f * s, 1.75f * s,
                Render2D.withAlpha(Style.accent(), 0.45f));
        Render2D.round(gg, x, 15 * s, w * healthAnimation, 3.5f * s, 1.75f * s, Style.accent());
        if (d.absorption > 0) {
            Render2D.round(gg, x, 15 * s, w * d.absorption, 3.5f * s, 1.75f * s, 0xFFFFD45F);
        }
        sSmall(s).draw(gg, String.format(Locale.ROOT, "%.1fm", d.dist), x, 25.5f * s, DIM);
        item(gg, d.target.getMainHandItem(), d.w - 15 * s, 19 * s, 8 * s);
    }

    private static void drawHead(GuiGraphicsExtractor gg, PlayerInfo info, float x, float y, float d) {
        Identifier id = info.getSkin().body().texturePath();
        int px = Math.round(x), py = Math.round(y), size = Math.round(d);
        int color = Render2D.withAlpha(0xFFFFFFFF, Render2D.alpha());
        gg.blit(RenderPipelines.GUI_TEXTURED, id, px, py, 8, 8, size, size, 8, 8, 64, 64, color);
        if (info.showHat()) {
            gg.blit(RenderPipelines.GUI_TEXTURED, id, px, py, 40, 8, size, size, 8, 8, 64, 64, color);
        }
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
        return f > 0.6f ? 0xFF4ADE80 : f > 0.3f ? 0xFFFACC15 : 0xFFF87171;
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
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        float extra = Minecraft.getInstance().player.getOffhandItem().isEmpty() ? 0 : slot + 6 * s;
        return new HbData(slot * 9.0f + gap * 8.0f + extra, slot, s);
    }

    private static void drawHotbar(GuiGraphicsExtractor gg, float s, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        float off = mc.player.getOffhandItem().isEmpty() ? 0 : slot + 6 * s;
        for (int i = 0; i < 9; i++) {
            float sx = off + i * (slot + gap);
            boolean selected = i == inv.getSelectedSlot();
            Render2D.round(gg, sx, 0, slot, slot, 4 * s, 0x55101013);
            Render2D.round(gg, sx, 0, slot, slot, 4 * s, Render2D.withAlpha(Style.accent(), selected ? 0.55f : 0.0f));
            Render2D.round(gg, sx + s * 0.5f, s * 0.5f, slot - s, slot - s, 3.5f * s, 0x00000000);
            item(gg, inv.getItem(i), sx + 3 * s, 3 * s, slot - 6 * s);
            if (selected) {
                Render2D.rect(gg, sx + 3 * s, slot - 2.5f * s, slot - 6 * s, 2 * s, Style.accent());
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            Render2D.round(gg, 0, 0, slot, slot, 4 * s, 0x55101013);
            item(gg, offhand, 3 * s, 3 * s, slot - 6 * s);
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
