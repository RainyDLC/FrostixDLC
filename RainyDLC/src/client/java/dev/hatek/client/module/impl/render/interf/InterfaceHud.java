package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.hatek.client.media.MediaClip;
import dev.hatek.client.media.MediaManager;
import dev.hatek.client.mixin.accessor.BossHealthOverlayAccessor;
import dev.hatek.client.mixin.accessor.GuiAccessor;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.NoRender;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.BossHealthOverlay;
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
import java.util.List;
import java.util.Map;

/**
 * Отрисовка HUD модуля Interface в стилистике ClickGUI
 * (константы Style.HUD_* / Style.WM_*): ватермарка, кейбинды
 * функций, активные эффекты и кастомный хотбар.
 */
public final class InterfaceHud {
    private static boolean hooked;

    // Последний отрисованный прямоугольник ватермарки (для клика ПКМ).
    private static float wmX;
    private static float wmY;
    private static float wmW;
    private static float wmH;
    private static boolean wmVisible;

    // Плавная анимация Y ватермарки в режиме "Center" при появлении боссбара.
    private static float wmAnimY = Style.HUD_MARGIN;

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

    private static void render(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wmVisible = false;
            return;
        }
        if (Interface.instance() == null || !Interface.instance().isEnabled()) {
            wmVisible = false;
            return;
        }

        int sw = Render2D.screenWidth();
        int sh = Render2D.screenHeight();

        Render2D.begin(gg);
        float rightY = Style.HUD_MARGIN;
        if (Interface.showWatermark()) {
            renderWatermark(gg, sw);
            if (wmVisible && "Right".equals(Interface.watermarkPos())) {
                rightY = wmY + wmH + 8.0f;
            }
        } else {
            wmVisible = false;
        }
        if (Interface.showKeybinds()) {
            float h = renderKeybinds(gg, sw, rightY);
            if (h > 0.0f) {
                rightY += h + 8.0f;
            }
        }
        if (Interface.showEffects()) {
            renderEffects(gg, sw, rightY);
        }
        if (Interface.showHotbar()) {
            renderHotbar(gg, sw, sh);
        }
        if (Interface.showMedia2D()) {
            renderMedia2D(gg, sw, sh);
        }
        Render2D.end(gg);
    }

    // ---------------- ватермарка ----------------

    private static void renderWatermark(GuiGraphicsExtractor gg, int sw) {
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

        String pos = Interface.watermarkPos();
        float x;
        float y;
        if ("Center".equals(pos)) {
            x = (sw - w) / 2.0f;
            int bosses = bossBarCount();
            float target = Style.HUD_MARGIN + Math.min(bosses, 3) * 20.0f;
            wmAnimY += (target - wmAnimY) * 0.18f;
            if (Math.abs(target - wmAnimY) < 0.1f) {
                wmAnimY = target;
            }
            y = wmAnimY;
        } else {
            wmAnimY = Style.HUD_MARGIN;
            y = Style.HUD_MARGIN;
            x = "Right".equals(pos) ? sw - w - Style.HUD_MARGIN : Style.HUD_MARGIN;
        }

        wmX = x;
        wmY = y;
        wmW = w;
        wmH = h;
        wmVisible = true;

        Render2D.round(gg, x, y, w, h, Style.WM_R, Style.HUD_PANEL);

        float cx = x + Style.WM_PAD;
        Render2D.icon(gg, "logo", cx, y + (h - logoS) / 2.0f, logoS, logoS, Style.WHITE);
        cx += logoS + 8.0f;

        float baseline = y + Style.WM_BASELINE;
        cx = title.draw(gg, name1, cx, baseline, Style.WHITE);
        cx = title.draw(gg, name2, cx, baseline, Style.accent());

        float unitBaseline = label.centeredBaseline(y, h);
        cx = separator(gg, cx, y, h);
        cx = unit(gg, "fps", fps, fpsW, cx, y, h, unitBaseline);

        if (pingS != null) {
            cx = separator(gg, cx, y, h);
            unit(gg, "ping", pingS, pingW, cx, y, h, unitBaseline);
        }
    }

    private static float separator(GuiGraphicsExtractor gg, float cx, float y, float h) {
        cx += Style.WM_SEP_GAP;
        Render2D.rect(gg, cx, y + (h - Style.WM_SEP_H) / 2.0f,
                Style.WM_SEP_W, Style.WM_SEP_H, Style.WHITE_08);
        return cx + Style.WM_SEP_W + Style.WM_SEP_GAP;
    }

    private static float unit(GuiGraphicsExtractor gg, String icon, String text, float textW,
                              float cx, float y, float h, float baseline) {
        float iconS = 11.0f;
        Render2D.icon(gg, icon, cx, y + (h - iconS) / 2.0f, iconS, iconS, Style.WHITE_45);
        cx += iconS + Style.WM_UNIT_GAP;
        Fonts.label().draw(gg, text, cx, baseline, Style.WHITE);
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

    /** Количество активных боссбаров (0, если они скрыты NoRender). */
    private static int bossBarCount() {
        try {
            if (NoRender.hideBossBar()) {
                return 0;
            }
            Minecraft mc = Minecraft.getInstance();
            Gui gui = mc.gui;
            if (gui == null) {
                return 0;
            }
            Hud hud = ((GuiAccessor) gui).hatek$hud();
            if (hud == null) {
                return 0;
            }
            BossHealthOverlay overlay = hud.getBossOverlay();
            if (overlay == null) {
                return 0;
            }
            Map<?, ?> events = ((BossHealthOverlayAccessor) overlay).hatek$events();
            return events == null ? 0 : events.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** ПКМ по ватермарке открывает выбор позиции. */
    public static boolean handleRightClick() {
        if (!Interface.showWatermark() || !wmVisible) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui == null) {
            return false;
        }
        if (((GuiAccessor) mc.gui).hatek$screen() != null) {
            return false;
        }
        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        if (mx >= wmX && mx <= wmX + wmW && my >= wmY && my <= wmY + wmH) {
            mc.setScreenAndShow(new WatermarkPosScreen());
            return true;
        }
        return false;
    }

    // ---------------- кейбинды функций ----------------

    /**
     * Карточка биндов: какие клавиши назначены на функции.
     * Возвращает высоту карточки (0 — нечего показать).
     */
    private static float renderKeybinds(GuiGraphicsExtractor gg, int sw, float topY) {
        List<Module> bound = new ArrayList<>();
        for (Module module : ModuleManager.all()) {
            if (module.keyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                bound.add(module);
            }
        }
        if (bound.isEmpty()) {
            return 0.0f;
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
        float rowH = Style.HUD_ROW_H;
        float h = Style.HUD_HEADER_H + bound.size() * rowH + Style.HUD_BOTTOM_PAD;
        float x = sw - w - Style.HUD_MARGIN;
        float y = topY;

        Render2D.round(gg, x, y, w, h, Style.HUD_CARD_R, Style.HUD_PANEL);
        drawCardHeader(gg, x, y, "keyboard", "Keybinds");

        float ry = y + Style.HUD_HEADER_H;
        for (Module module : bound) {
            boolean enabled = module.isEnabled();
            if (enabled) {
                Render2D.round(gg, x + Style.HUD_PAD, ry + (rowH - Style.HUD_STUB_H) / 2.0f,
                        Style.HUD_STUB_W, Style.HUD_STUB_H, 1.0f, Style.accent());
            }
            body.draw(gg, module.name(), x + 23.0f,
                    body.centeredBaseline(ry, rowH),
                    enabled ? Style.WHITE : Style.WHITE_45);

            String key = module.keyLabel();
            float badgeW = label.width(key) + Style.HUD_BADGE_PAD * 2.0f;
            float bx = x + w - Style.HUD_PAD - badgeW;
            float by = ry + (rowH - Style.HUD_BADGE_H) / 2.0f;
            Render2D.round(gg, bx, by, badgeW, Style.HUD_BADGE_H, Style.HUD_BADGE_R,
                    enabled ? Render2D.withAlpha(Style.accent(), 0.22f) : Style.WHITE_04);
            label.drawCentered(gg, key, bx + badgeW / 2.0f,
                    label.centeredBaseline(by, Style.HUD_BADGE_H),
                    enabled ? Style.WHITE : Style.WHITE_45);
            ry += rowH;
        }
        return h;
    }

    private static void drawCardHeader(GuiGraphicsExtractor gg, float x, float y,
                                       String icon, String titleText) {
        Render2D.icon(gg, icon, x + Style.HUD_ICON_X, y + Style.HUD_ICON_Y,
                Style.HUD_ICON, Style.HUD_ICON, Style.WHITE_45);
        Fonts.title().draw(gg, titleText, x + Style.HUD_TITLE_X,
                y + Style.HUD_TITLE_BASELINE, Style.WHITE);
    }

    // ---------------- эффекты ----------------

    private static void renderEffects(GuiGraphicsExtractor gg, int sw, float topY) {
        Minecraft mc = Minecraft.getInstance();
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.removeIf(instance -> !instance.showIcon());
        if (effects.isEmpty()) {
            return;
        }

        HFont title = Fonts.title();
        HFont body = Fonts.body();
        HFont label = Fonts.label();

        float rowH = 22.0f;
        float headerW = Style.HUD_TITLE_X + title.width("Effects") + Style.HUD_PAD;
        float rowsW = 0.0f;
        for (int i = 0; i < Math.min(8, effects.size()); i++) {
            MobEffectInstance instance = effects.get(i);
            String name = effectName(instance);
            float rowW = Style.HUD_PAD + 14.0f + 6.0f + body.width(name)
                    + 8.0f + label.width(formatDuration(instance.getDuration()))
                    + Style.HUD_PAD;
            rowsW = Math.max(rowsW, rowW);
        }
        float w = Math.max(Style.HUD_MIN_W, Math.max(headerW, rowsW));
        int shown = Math.min(8, effects.size());
        float h = Style.HUD_HEADER_H + shown * rowH + Style.HUD_BOTTOM_PAD;
        float x = sw - w - Style.HUD_MARGIN;
        float y = topY;

        Render2D.round(gg, x, y, w, h, Style.HUD_CARD_R, Style.HUD_PANEL);
        Render2D.texture(gg, TexCache.flask(Style.HUD_ICON),
                x + Style.HUD_ICON_X, y + Style.HUD_ICON_Y,
                Style.HUD_ICON, Style.HUD_ICON, Style.WHITE_45);
        title.draw(gg, "Effects", x + Style.HUD_TITLE_X,
                y + Style.HUD_TITLE_BASELINE, Style.WHITE);

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        float ry = y + Style.HUD_HEADER_H;
        for (int i = 0; i < shown; i++) {
            MobEffectInstance instance = effects.get(i);
            Holder<MobEffect> holder = instance.getEffect();

            Render2D.pushTranslate(gg, x + Style.HUD_PAD, ry + (rowH - 14.0f) / 2.0f);
            gg.blitSprite(pipeline, Hud.getMobEffectSprite(holder), 0, 0, 14, 14);
            Render2D.popTransform();

            float baseline = body.centeredBaseline(ry, rowH);
            body.draw(gg, effectName(instance), x + Style.HUD_PAD + 20.0f,
                    baseline, Style.WHITE);
            label.drawRight(gg, formatDuration(instance.getDuration()),
                    x + w - Style.HUD_PAD, label.centeredBaseline(ry, rowH), Style.WHITE_45);
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

    // ---------------- медиа 2D ----------------

    private static void renderMedia2D(GuiGraphicsExtractor gg, int sw, int sh) {
        MediaClip clip = MediaManager.get(Interface.mediaFile());
        if (clip == null) {
            return;
        }
        MediaClip.Frame frame = clip.frameAt(System.currentTimeMillis());
        float h = (float) Interface.mediaSize();
        float w = h * frame.width() / (float) frame.height();
        float x = sw * (float) Interface.mediaX() / 100.0f - w / 2.0f;
        float y = sh * (float) Interface.mediaY() / 100.0f - h / 2.0f;
        Render2D.texture(gg, frame.texture(), x, y, w, h, Style.WHITE);
    }

    // ---------------- кастомный хотбар ----------------

    private static void renderHotbar(GuiGraphicsExtractor gg, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Inventory inv = mc.player.getInventory();

        float slot = 22.0f;
        float gap = 2.0f;
        float totalW = slot * 9.0f + gap * 8.0f;
        float x0 = (sw - totalW) / 2.0f;
        float y0 = sh - slot - 8.0f;

        for (int i = 0; i < 9; i++) {
            float sx = x0 + i * (slot + gap);
            drawSlot(gg, mc, inv.getItem(i), sx, y0, slot, i == inv.selected);
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            drawSlot(gg, mc, offhand, x0 - slot - 6.0f, y0, slot, false);
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
        Render2D.popTransform();
    }
}
