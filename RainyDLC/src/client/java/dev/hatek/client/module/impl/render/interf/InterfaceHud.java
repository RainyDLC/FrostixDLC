package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.interf.quick.*;
import dev.hatek.client.ui.Style;
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
 * HUD RainyDLC «Stripe» — плоский терминальный дизайн с нуля.
 * Никакого блюра и стекла: сплошные панели, острые углы,
 * акцентная полоса слева, верхние регистры заголовков,
 * сегментные бары, текстовый ArrayList без подложек.
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

    // ---------- токены «Stripe» ----------
    private static final float PAD = 6.0f;
    private static final float HEADER_H = 14.0f;
    private static final float ROW_H = 12.0f;
    private static final float STRIPE = 2.0f;
    private static final int FILL = 0xE90D0D10;
    private static final int FILL_SOFT = 0xE0121215;
    private static final int LINE = 0x24FFFFFF;
    private static final int HAIR = 0x1CFFFFFF;
    private static final int GREY = 0xFFB9B9C2;
    private static final int DIM = 0xFF7E7E88;
    private static final int DARK = 0xFF1A1A1F;

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

    private record WatermarkData(float w, float h, String nick, String fps, String time, String ping) implements Sized {
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
        return QuickFont.of("bold", 8.0f * s);
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

        Render2D.begin(gg);
        try {
            for (Placed p : placed) {
                var entry = HudLayout.get(p.el);
                Render2D.pushAlpha(entry.opacity);
                try {
                    // ArrayList — голый текст без подложки, остальное — плоская панель.
                    if (p.el != HudLayout.Element.ARRAYLIST) {
                        drawFlat(gg, p.x, p.y, p.data.w(), p.data.h(), p.s);
                    }
                    Render2D.pushTranslate(gg, p.x, p.y);
                    try {
                        draw(gg, p.el, p.s, p.data);
                    } finally {
                        Render2D.popTransform(gg);
                    }
                    if (editing() && dragHit != null && dragHit.element == p.el) {
                        Render2D.rect(gg, p.x, p.y, p.data.w(), Math.max(1, p.s * 0.75f), Style.accent());
                        Render2D.rect(gg, p.x, p.y + p.data.h() - p.s * 0.75f, p.data.w(),
                                Math.max(1, p.s * 0.75f), Style.accent());
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

    // ---------- плоская панель ----------

    /**
     * Сплошной прямоугольник + рамка 1px + акцентная полоса слева.
     * Углы острые, никакого блюра — полная противоположность стеклу.
     */
    private static void drawFlat(GuiGraphicsExtractor gg, float x, float y, float w, float h, float s) {
        Render2D.rect(gg, x, y, w, h, FILL);
        Render2D.rect(gg, x, y, w, Math.max(1, s * 0.5f), LINE);
        Render2D.rect(gg, x, y + h - Math.max(1, s * 0.5f), w, Math.max(1, s * 0.5f), LINE);
        Render2D.rect(gg, x, y, Math.max(1, s * 0.5f), h, LINE);
        Render2D.rect(gg, x + w - Math.max(1, s * 0.5f), y, Math.max(1, s * 0.5f), h, LINE);
        Render2D.rect(gg, x, y, STRIPE * s, h, Style.accent());
    }

    private static void header(GuiGraphicsExtractor gg, String title, int count, float w, float s) {
        String upper = title.toUpperCase(Locale.ROOT);
        fMicro(s).draw(gg, upper, (PAD + STRIPE) * s, fMicro(s).centeredBaseline(0, HEADER_H * s), GREY);
        fMicro(s).drawRight(gg, String.format(Locale.ROOT, "%02d", count), w - PAD * s,
                fMicro(s).centeredBaseline(0, HEADER_H * s), Style.accent());
        Render2D.rect(gg, (PAD + STRIPE) * s, HEADER_H * s, w - (PAD + STRIPE + PAD) * s,
                Math.max(1, s * 0.5f), HAIR);
    }

    private static void dottedLine(GuiGraphicsExtractor gg, float x, float y, float w, float s) {
        float step = 3.0f * s;
        float dot = 1.0f * s;
        for (float dx = 0; dx < w; dx += step) {
            Render2D.rect(gg, x + dx, y, Math.min(dot, w - dx), Math.max(1, s * 0.5f), HAIR);
        }
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
            case ARRAYLIST -> drawArrayLines(gg, s, (ListData) data);
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
        String ping = (info == null ? 0 : info.getLatency()) + " MS";
        float w = (PAD + STRIPE) * s + fTitle(s).width("RAINY") + 3 * s
                + fMicro(s).width("DLC") + 8 * s;
        w += fBody(s).width(nick) + 8 * s;
        w += fSmall(s).width(fps) + 8 * s;
        w += fSmall(s).width(time) + 8 * s;
        w += fSmall(s).width(ping) + PAD * s;
        return new WatermarkData(w, 20 * s, nick, fps, time, ping);
    }

    private static void drawWatermark(GuiGraphicsExtractor gg, float s, WatermarkData d) {
        float x = (PAD + STRIPE) * s;
        float mid = fTitle(s).centeredBaseline(0, d.h);
        // Логотип: квадрат + RAINY жирно + DLC микро-акцентом.
        Render2D.rect(gg, x, (d.h - 10 * s) / 2, 10 * s, 10 * s, Style.accent());
        fMicro(s).drawCentered(gg, "R", x + 5 * s, fMicro(s).visualBaseline("R", (d.h - 10 * s) / 2, 10 * s), 0xFF101014);
        x += 13 * s;
        x = fTitle(s).draw(gg, "RAINY", x, mid, Style.WHITE);
        x = fMicro(s).draw(gg, "DLC", x + 3 * s, fMicro(s).centeredBaseline(0, d.h), Style.accent());
        x += 8 * s;
        x = divider(gg, x, d.h, s);
        x = fBody(s).draw(gg, d.nick, x, fBody(s).centeredBaseline(0, d.h), GREY);
        x += 5 * s;
        x = divider(gg, x, d.h, s);
        x = fSmall(s).draw(gg, d.fps, x, fSmall(s).centeredBaseline(0, d.h), Style.WHITE);
        x += 5 * s;
        x = divider(gg, x, d.h, s);
        x = fSmall(s).draw(gg, d.time, x, fSmall(s).centeredBaseline(0, d.h), DIM);
        x += 5 * s;
        x = divider(gg, x, d.h, s);
        fSmall(s).draw(gg, d.ping, x, fSmall(s).centeredBaseline(0, d.h), Style.accent());
        // Нижняя акцентная черта на треть ширины — подпись стиля.
        Render2D.rect(gg, 0, d.h - Math.max(1, s * 0.75f), d.w * 0.38f, Math.max(1, s * 0.75f), Style.accent());
    }

    private static float divider(GuiGraphicsExtractor gg, float x, float h, float s) {
        Render2D.rect(gg, x, h * 0.28f, Math.max(1, s * 0.5f), h * 0.44f, HAIR);
        return x + 5 * s;
    }

    // ---------- строки ----------

    private static List<Row> keyRows() {
        List<Row> rows = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && m.keyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                rows.add(new Row(m.name(), m.name().toUpperCase(Locale.ROOT), m.keyLabel(), "", null));
            }
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "INTERFACE", "RSHIFT", "", null));
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
            String name = (effectBaseName(e) + (roman == null ? "" : " " + roman)).toUpperCase(Locale.ROOT);
            rows.add(new Row(e.getEffect().value().getDescriptionId(), name, formatDuration(e.getDuration()), "", e));
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "SPEED II", "1:30", "", null));
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
                String status = info == null ? "VANISH"
                        : info.getGameMode() == GameType.SPECTATOR ? "GM3" : "";
                rows.add(new Row(name, name.toUpperCase(Locale.ROOT),
                        (status.isEmpty() ? "" : status + " ")
                                + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                        "", info));
            }
        }
        STAFF_SINCE.keySet().retainAll(present);
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "MODERATOR", "00:00", "", null));
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
                String name = item.isEmpty() ? kv.getKey().getPath()
                        : item.getHoverName().getString();
                rows.add(new Row(kv.getKey().toString(), name.toUpperCase(Locale.ROOT),
                        String.format(Locale.ROOT, "%.1fs", kv.getValue() / 20f), "", item));
            }
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "ENDER PEARL", "12.4s", "", new ItemStack(Items.ENDER_PEARL)));
        }
        return rows;
    }

    private static List<Row> moduleRows(float s) {
        List<Row> rows = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && !(m instanceof Interface)) {
                rows.add(new Row(m.name(), m.name().toUpperCase(Locale.ROOT), "", "", null));
            }
        }
        rows.sort(Comparator.comparingDouble((Row r) -> fBody(s).width(r.left)).reversed());
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "INTERFACE", "", "", null));
        }
        return rows;
    }

    private static List<Row> notificationRows() {
        List<Row> rows = new ArrayList<>();
        for (var n : QuickNotifications.current()) {
            rows.add(new Row("notice-" + n.created(), n.text().toUpperCase(Locale.ROOT), "",
                    n.enabled() ? "ON" : "OFF", null));
        }
        if (rows.isEmpty() && editing()) {
            rows.add(new Row("preview", "ПРИМЕР УВЕДОМЛЕНИЯ", "", "ON", null));
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
        float rowH = (el == HudLayout.Element.NOTIFICATIONS ? 16 : bare ? 13 : ROW_H) * s;
        float w;
        float h;
        if (el == HudLayout.Element.ARRAYLIST) {
            w = 60 * s;
            for (var a : rows) {
                w = Math.max(w, fBody(s).width(a.row.left) + 12 * s);
            }
            h = rows.size() * rowH + 2 * s;
        } else if (el == HudLayout.Element.NOTIFICATIONS) {
            w = 120 * s;
            for (var a : rows) {
                w = Math.max(w, (PAD + STRIPE) * s + fSmall(s).width(a.row.left)
                        + fMicro(s).width(a.row.icon) + 14 * s);
            }
            h = rows.size() * (rowH + 2 * s) + 4 * s;
        } else {
            w = 96 * s;
            h = HEADER_H * s + PAD * s;
            for (var a : rows) {
                Row row = a.row;
                boolean sprite = row.sprite != null;
                float rw = (PAD + STRIPE + (sprite ? 16 : 0)) * s + fBody(s).width(row.left)
                        + fBody(s).width(row.right) + (row.right.isEmpty() ? 0 : 10 * s);
                w = Math.max(w, rw + PAD * s);
                h += rowH * a.alpha;
            }
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

    /** Плоская таблица: заголовок, пунктирная строка-разделитель под каждой строкой. */
    private static void drawTable(GuiGraphicsExtractor gg, float s, ListData d) {
        header(gg, d.title, d.rows.size(), d.w, s);
        float y = HEADER_H * s + PAD * 0.5f * s;
        Render2D.pushClip(gg, 0, HEADER_H * s, d.w, d.h - HEADER_H * s);
        try {
            for (var a : d.rows) {
                Row row = a.row;
                Render2D.pushAlpha(a.alpha);
                Render2D.pushTranslate(gg, a.slide, 0);
                float x = (PAD + STRIPE) * s;
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
                // Тень под текстом для читаемости поверх мира.
                fBody(s).draw(gg, row.left, x + s * 0.5f, fBody(s).centeredBaseline(y, ROW_H * s) + s * 0.5f, 0x90000000);
                fBody(s).draw(gg, row.left, x, fBody(s).centeredBaseline(y, ROW_H * s), Style.WHITE);
                if (!row.right.isEmpty()) {
                    int color = row.sprite instanceof MobEffectInstance effect
                            && effect.getDuration() >= 0 && effect.getDuration() < 200
                            ? 0xFFEB3C46 : DIM;
                    // Кейбинды — квадратный тэг справа.
                    if ("BINDS".equalsIgnoreCase(d.title)) {
                        float bw = fMicro(s).width(row.right) + 8 * s;
                        Render2D.rect(gg, d.w - PAD * s - bw, y + (ROW_H * s - 9 * s) / 2, bw, 9 * s, DARK);
                        Render2D.rect(gg, d.w - PAD * s - bw, y + (ROW_H * s - 9 * s) / 2, bw,
                                Math.max(1, s * 0.5f), Style.accent());
                        fMicro(s).drawCentered(gg, row.right, d.w - PAD * s - bw / 2,
                                fMicro(s).centeredBaseline(y + (ROW_H * s - 9 * s) / 2, 9 * s), Style.accent());
                    } else {
                        fBody(s).drawRight(gg, row.right, d.w - PAD * s,
                                fBody(s).centeredBaseline(y, ROW_H * s), color);
                    }
                }
                dottedLine(gg, x, y + ROW_H * s - 1, d.w - x - PAD * s, s);
                Render2D.popTransform(gg);
                Render2D.popAlpha();
                y += ROW_H * s * a.alpha;
            }
        } finally {
            Render2D.popClip(gg);
        }
    }

    /** ArrayList: голый терминальный текст, справа акцентный тик, без подложек. */
    private static void drawArrayLines(GuiGraphicsExtractor gg, float s, ListData d) {
        float rowH = 13 * s;
        float y = 0;
        boolean right = BOUNDS.get(HudLayout.Element.ARRAYLIST)[0] > Render2D.screenWidth() / 2f;
        Render2D.pushClip(gg, -PAD * s, -PAD * s, d.w + PAD * 2 * s, d.h + PAD * 2 * s);
        try {
            for (var a : d.rows) {
                Row row = a.row;
                Render2D.pushAlpha(a.alpha);
                float slide = a.slide * (right ? -1 : 1);
                Render2D.pushTranslate(gg, slide, 0);
                float tw = fBody(s).width(row.left);
                float tx = right ? d.w - tw - 6 * s : 6 * s;
                // Тень + сам текст + тик сбоку.
                fBody(s).draw(gg, row.left, tx + s * 0.5f, fBody(s).centeredBaseline(y, rowH) + s * 0.5f, 0x90000000);
                fBody(s).draw(gg, row.left, tx, fBody(s).centeredBaseline(y, rowH), Style.WHITE);
                float tickX = right ? d.w - 2 * s : tx - 4 * s;
                Render2D.rect(gg, tickX, y + 2 * s, 2 * s, rowH - 4 * s, Style.accent());
                Render2D.popTransform(gg);
                Render2D.popAlpha();
                y += rowH * a.alpha;
            }
        } finally {
            Render2D.popClip(gg);
        }
    }

    /** Уведомления: плоские тосты с цветной полосой слева. */
    private static void drawToasts(GuiGraphicsExtractor gg, float s, ListData d) {
        float rowH = 16 * s;
        float y = 2 * s;
        for (var a : d.rows) {
            Row row = a.row;
            Render2D.pushAlpha(a.alpha);
            Render2D.pushTranslate(gg, a.slide, 0);
            boolean on = !"OFF".equals(row.icon);
            Render2D.rect(gg, 0, y, d.w, rowH, FILL_SOFT);
            Render2D.rect(gg, 0, y, STRIPE * s, rowH, on ? 0xFF4ADE80 : 0xFFF87171);
            fMicro(s).draw(gg, row.icon, (PAD + STRIPE) * s,
                    fMicro(s).centeredBaseline(y, rowH), on ? 0xFF4ADE80 : 0xFFF87171);
            fSmall(s).draw(gg, row.left, (PAD + STRIPE) * s + fMicro(s).width(row.icon) + 5 * s,
                    fSmall(s).centeredBaseline(y, rowH), Style.WHITE);
            Render2D.popTransform(gg);
            Render2D.popAlpha();
            y += (rowH + 2 * s) * a.alpha;
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
                label = "TICKS";
                text = String.format(Locale.ROOT, "%.2f", mc.hasSingleplayerServer() ? 20 : QuickTelemetry.tps());
            }
            case PING -> {
                PlayerInfo info = mc.getConnection() == null ? null
                        : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                label = "PING";
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
                text = String.format(Locale.ROOT, "%.1f / %.1f / %.1f",
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
                label = "TOTEMS";
                text = String.valueOf(count);
                item = new ItemStack(Items.TOTEM_OF_UNDYING);
            }
            default -> {
                return null;
            }
        }
        float w = (PAD + STRIPE) * s + fMicro(s).width(label) + 5 * s
                + fBody(s).width(text) + (item.isEmpty() ? 0 : 15 * s) + PAD * s;
        return new PillData(w, 15 * s, label, text, item);
    }

    private static void drawPill(GuiGraphicsExtractor gg, float s, PillData d) {
        float x = (PAD + STRIPE) * s;
        float mid = fBody(s).centeredBaseline(0, d.h);
        x = fMicro(s).draw(gg, d.label, x, fMicro(s).centeredBaseline(0, d.h), Style.accent());
        x = fBody(s).draw(gg, d.text, x + 5 * s, mid, Style.WHITE);
        if (!d.item.isEmpty()) {
            item(gg, d.item, x + 5 * s, (d.h - 11 * s) / 2, 11 * s);
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
        float cell = 19 * s;
        float gap = 2 * s;
        float bar = 5 * s;
        HudLayout.Entry entry = HudLayout.get(HudLayout.Element.ARMOR);
        float x = entry.positioned() ? entry.x : (Render2D.screenWidth() - (cell * 6 + gap * 5)) / 2;
        boolean vertical = x < cell * 6 + 8 * s || x > Render2D.screenWidth() - cell * 6 - 8 * s;
        if (vertical) {
            return new ArmorData(cell + PAD * s, cell * 6 + gap * 5 + bar + PAD * s, items, true);
        }
        return new ArmorData(cell * 6 + gap * 5 + PAD * 2 * s, cell + bar + PAD * s, items, false);
    }

    private static void drawArmor(GuiGraphicsExtractor gg, float s, ArmorData d) {
        float cell = 19 * s;
        float gap = 2 * s;
        for (int i = 0; i < d.items.size(); i++) {
            float x = (PAD + STRIPE) * s + (d.vertical ? 0 : i * (cell + gap));
            float y = PAD * s * 0.5f + (d.vertical ? i * (cell + gap) : 0);
            ItemStack item = d.items.get(i);
            Render2D.rect(gg, x, y, cell, cell, DARK);
            Render2D.rect(gg, x, y, cell, Math.max(1, s * 0.5f), HAIR);
            item(gg, item, x + 3 * s, y + 3 * s, cell - 6 * s);
            if (item.isDamageableItem()) {
                float f = durabilityFrac(item);
                Render2D.rect(gg, x, y + cell + 2 * s, cell, 2 * s, 0xFF2A2A30);
                Render2D.rect(gg, x, y + cell + 2 * s, cell * f, 2 * s, durabilityColor(item));
            } else if (!item.isEmpty()) {
                Render2D.rect(gg, x, y + cell + 2 * s, cell, 2 * s, Style.accent());
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
        return new TargetData(150 * s, 40 * s, t,
                QuickFont.of("bold", 7.0f * s).trim(t.getName().getString(), 70 * s),
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
        float inner = (PAD + STRIPE) * s;
        // Верхняя акцентная линия = доля здоровья.
        Render2D.rect(gg, 0, 0, d.w * Mth.clamp(healthAnimation, 0, 1), Math.max(1, s), Style.accent());
        // Портрет: острый квадрат с рамкой.
        PlayerInfo info = d.target instanceof Player player && mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(player.getUUID()) : null;
        float avatar = 28 * s;
        if (info != null) {
            drawHead(gg, info, inner, 6 * s, avatar);
        } else {
            Render2D.rect(gg, inner, 6 * s, avatar, avatar, DARK);
            fTitle(s).drawCentered(gg, d.name.isEmpty() ? "?" : d.name.substring(0, 1),
                    inner + avatar / 2, fTitle(s).visualBaseline("H", 6 * s, avatar), Style.accent());
        }
        Render2D.rect(gg, inner, 6 * s, avatar, Math.max(1, s * 0.5f), LINE);
        Render2D.rect(gg, inner, 6 * s + avatar, avatar, Math.max(1, s * 0.5f), LINE);
        float x = inner + avatar + 5 * s;
        float w = d.w - x - PAD * s;
        // Имя + HP цифрой справа.
        fTitle(s).draw(gg, d.name.toUpperCase(Locale.ROOT), x, 11 * s, Style.WHITE);
        fBody(s).drawRight(gg, d.hp + " HP", d.w - PAD * s, 11 * s, Style.accent());
        // Сегментный бар: 10 делений с зазорами + белый гост урона.
        int segs = 10;
        float gap = 1.5f * s;
        float segW = (w - gap * (segs - 1)) / segs;
        float barY = 17 * s;
        float barH = 5 * s;
        int filled = Math.round(healthAnimation * segs);
        int ghost = Math.round(damageAnimation * segs);
        for (int i = 0; i < segs; i++) {
            float sx = x + i * (segW + gap);
            int c = 0xFF2A2A30;
            if (i < filled) {
                c = Style.accent();
            } else if (i < ghost) {
                c = 0xBFFFFFFF;
            }
            Render2D.rect(gg, sx, barY, segW, barH, c);
        }
        if (d.absorption > 0) {
            fMicro(s).draw(gg, "+" + String.format(Locale.ROOT, "%.0f ABS", d.target.getAbsorptionAmount()),
                    x, 28.5f * s, 0xFFFFD45F);
        } else {
            fSmall(s).draw(gg, String.format(Locale.ROOT, "%.1f M", d.dist), x, 28.5f * s, DIM);
        }
        String held = d.target.getMainHandItem().isEmpty() ? "NO ITEM"
                : d.target.getMainHandItem().getHoverName().getString().toUpperCase(Locale.ROOT);
        fMicro(s).drawRight(gg, QuickFont.of("bold", 7.0f * s).trim(held, 52 * s),
                d.w - PAD * s - 12 * s, 28.5f * s, GREY);
        item(gg, d.target.getMainHandItem(), d.w - PAD * s - 9 * s, 24 * s, 9 * s);
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
        return new HbData(slot * 9.0f + gap * 8.0f + extra, slot + 4 * s, s);
    }

    private static void drawHotbar(GuiGraphicsExtractor gg, float s, HbData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        float slot = 22.0f * s;
        float gap = 2.0f * s;
        float y = 2 * s;
        float off = mc.player.getOffhandItem().isEmpty() ? 0 : slot + 6 * s;
        for (int i = 0; i < 9; i++) {
            float sx = off + i * (slot + gap);
            boolean selected = i == inv.getSelectedSlot();
            Render2D.rect(gg, sx, y, slot, slot, selected ? 0xFF111114 : 0xB00B0B0D);
            Render2D.rect(gg, sx, y, slot, Math.max(1, s * 0.5f), selected ? Style.WHITE : LINE);
            Render2D.rect(gg, sx, y + slot - Math.max(1, s * 0.5f), slot, Math.max(1, s * 0.5f),
                    selected ? Style.WHITE : LINE);
            Render2D.rect(gg, sx, y, Math.max(1, s * 0.5f), slot, selected ? Style.WHITE : LINE);
            Render2D.rect(gg, sx + slot - Math.max(1, s * 0.5f), y, Math.max(1, s * 0.5f), slot,
                    selected ? Style.WHITE : LINE);
            if (selected) {
                Render2D.rect(gg, sx, y - 2 * s, slot, 2 * s, Style.accent());
            }
            // Номер слота в углу — терминальная деталь.
            fMicro(s).draw(gg, String.valueOf(i + 1), sx + 2 * s, y + 6 * s, DIM);
            item(gg, inv.getItem(i), sx + 3 * s, y + 3 * s, slot - 6 * s);
            if (selected) {
                Render2D.rect(gg, sx + 3 * s, y + slot - 3 * s, slot - 6 * s, 2 * s, Style.accent());
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            Render2D.rect(gg, 0, y, slot, slot, 0xB00B0B0D);
            Render2D.rect(gg, 0, y, slot, Math.max(1, s * 0.5f), LINE);
            Render2D.rect(gg, 0, y + slot - Math.max(1, s * 0.5f), slot, Math.max(1, s * 0.5f), LINE);
            item(gg, offhand, 3 * s, y + 3 * s, slot - 6 * s);
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
