package dev.hatek.client.module.impl.render.interf;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.hatek.client.mixin.accessor.BossHealthOverlayAccessor;
import dev.hatek.client.mixin.accessor.GuiAccessor;
import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.module.impl.render.NoRender;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Отрисовка HUD модуля Interface: ватермарка, кейбинды,
 * активные эффекты и кастомный хотбар.
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
    private static float wmAnimY = 10.0f;

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
        if (Interface.showWatermark()) {
            renderWatermark(gg, sw);
        } else {
            wmVisible = false;
        }
        if (Interface.showKeybinds()) {
            renderKeybinds(gg, sh);
        }
        if (Interface.showEffects()) {
            renderEffects(gg, sw);
        }
        if (Interface.showHotbar()) {
            renderHotbar(gg, sw, sh);
        }
        Render2D.end(gg);
    }

    // ---------------- ватермарка ----------------

    private static void renderWatermark(GuiGraphicsExtractor gg, int sw) {
        Minecraft mc = Minecraft.getInstance();
        HFont title = Fonts.title();
        HFont label = Fonts.label();

        String part1 = "Frostix";
        String part2 = "DLC";
        String info = mc.getFps() + " FPS";
        float nameW = title.width(part1) + title.width(part2);
        float w = 10.0f + nameW + 8.0f + label.width(info) + 12.0f;
        float h = 24.0f;

        String pos = Interface.watermarkPos();
        float x;
        float y;
        if ("Center".equals(pos)) {
            x = (sw - w) / 2.0f;
            int bosses = bossBarCount();
            float target = 10.0f + Math.min(bosses, 3) * 20.0f;
            wmAnimY += (target - wmAnimY) * 0.18f;
            if (Math.abs(target - wmAnimY) < 0.1f) {
                wmAnimY = target;
            }
            y = wmAnimY;
        } else {
            wmAnimY = 10.0f;
            y = 10.0f;
            x = "Right".equals(pos) ? sw - w - 10.0f : 10.0f;
        }

        wmX = x;
        wmY = y;
        wmW = w;
        wmH = h;
        wmVisible = true;

        Render2D.round(gg, x, y, w, h, 8.0f, Style.HUD_PANEL);
        float baseline = title.centeredBaseline(y, h);
        float cx = x + 10.0f;
        cx = title.draw(gg, part1, cx, baseline, Style.WHITE);
        cx = title.draw(gg, part2, cx, baseline, Style.accent());
        label.draw(gg, info, cx + 8.0f, label.centeredBaseline(y, h), Style.WHITE_45);
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

    // ---------------- кейбинды ----------------

    private static void renderKeybinds(GuiGraphicsExtractor gg, int sh) {
        Minecraft mc = Minecraft.getInstance();
        float cell = 26.0f;
        float gap = 4.0f;
        float x0 = 10.0f;
        float totalH = cell * 3.0f + gap * 2.0f;
        float y0 = (sh - totalH) / 2.0f;

        HFont font = Fonts.label();
        keyCell(gg, font, "W", x0 + cell + gap, y0, cell, cell, mc.options.keyUp.isDown());
        keyCell(gg, font, "A", x0, y0 + cell + gap, cell, cell, mc.options.keyLeft.isDown());
        keyCell(gg, font, "S", x0 + cell + gap, y0 + cell + gap, cell, cell, mc.options.keyDown.isDown());
        keyCell(gg, font, "D", x0 + 2.0f * (cell + gap), y0 + cell + gap, cell, cell, mc.options.keyRight.isDown());
        keyCell(gg, font, "SPACE", x0, y0 + 2.0f * (cell + gap),
                cell * 2.0f + gap, cell, mc.options.keyJump.isDown());
        keyCell(gg, font, "SHIFT", x0 + 2.0f * (cell + gap), y0 + 2.0f * (cell + gap),
                cell, cell, mc.options.keyShift.isDown());
    }

    private static void keyCell(GuiGraphicsExtractor gg, HFont font, String text,
                                float x, float y, float w, float h, boolean pressed) {
        int bg = pressed ? Render2D.withAlpha(Style.accent(), 0.85f) : Style.HUD_PANEL;
        Render2D.round(gg, x, y, w, h, 6.0f, bg);
        font.drawCentered(gg, text, x + w / 2.0f, font.centeredBaseline(y, h),
                pressed ? Style.WHITE : Style.WHITE_45);
    }

    // ---------------- эффекты ----------------

    private static void renderEffects(GuiGraphicsExtractor gg, int sw) {
        Minecraft mc = Minecraft.getInstance();
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.removeIf(instance -> !instance.showIcon());
        if (effects.isEmpty()) {
            return;
        }

        HFont label = Fonts.label();
        float rowW = 170.0f;
        float rowH = 24.0f;
        float gap = 4.0f;
        float x = sw - rowW - 10.0f;
        float y = 10.0f;
        if (wmVisible && "Right".equals(Interface.watermarkPos())) {
            y = wmY + wmH + 8.0f;
        }

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        int shown = 0;
        for (MobEffectInstance instance : effects) {
            if (shown >= 8) {
                break;
            }
            float ry = y + shown * (rowH + gap);
            Render2D.round(gg, x, ry, rowW, rowH, 7.0f, Style.HUD_PANEL);

            Holder<MobEffect> holder = instance.getEffect();
            Identifier sprite = Hud.getMobEffectSprite(holder);
            Render2D.pushTranslate(gg, x + 4.0f, ry + 4.0f);
            gg.blitSprite(pipeline, sprite, 0, 0, 16, 16);
            Render2D.popTransform();

            String name = Component.translatable(
                    holder.value().getDescriptionId()).getString();
            int amp = instance.getAmplifier();
            if (amp > 0) {
                name += " " + toRoman(amp + 1);
            }
            float baseline = label.centeredBaseline(ry, rowH);
            label.draw(gg, name, x + 26.0f, baseline, Style.WHITE);
            label.drawRight(gg, formatDuration(instance.getDuration()),
                    x + rowW - 8.0f, baseline, Style.WHITE_45);
            shown++;
        }
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
