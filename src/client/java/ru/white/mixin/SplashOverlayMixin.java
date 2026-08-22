package ru.white.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.ScreenBlur;
import ru.white.utils.render.font.Fonts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Кастомный экран загрузки RainyDLC вместо ванильного:
 * дождливое «стекло» с каплями, дышащее гало у логотипа,
 * стеклянная полоса прогресса и подсказки — стиль главного меню.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique private static final int RAINYDLC_BG = 0x071426;
    @Unique private static final Identifier MENU_BG = Identifier.of("client", "textures/frame/menu.png");
    @Unique private static final Identifier LOGO_TEX = Identifier.of("client", "textures/icon.png");
    @Unique private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");

    // палитра главного меню: холодная дождевая синева
    @Unique private static final int ACCENT_R = 65, ACCENT_G = 145, ACCENT_B = 205;

    @Unique private static final long TIP_PERIOD_MS = 4200L;

    /** Вращающиеся подсказки: {рус, англ}. */
    @Unique private static final String[][] TIPS = {
            {"Нажми Right Shift, чтобы открыть меню клиента",
             "Press Right Shift to open the client menu"},
            {"Дождь на этом экране — фирменный стиль RainyDLC",
             "The rain on this screen is the RainyDLC signature"},
            {"Зажми Alt, чтобы свободно перетаскивать элементы худа",
             "Hold Alt to freely drag HUD elements"},
            {"Каждая функция гибко настраивается под тебя",
             "Every feature is finely customizable"},
            {"Приятной игры в дождливую погоду",
             "Enjoy your stay, whatever the weather"}
    };

    @Unique private long rainydlcStart = -1L;
    @Unique private long lastDropFrame = -1L;
    @Unique private final List<GlassDrop> glassDrops = new ArrayList<>();

    /** Капля дождя на «стекле» загрузочного экрана (как в главном меню). */
    @Unique
    private static final class GlassDrop {
        float x, y, r;
        double vy;
        boolean sliding;
        float slideStartY;
        final long born = System.currentTimeMillis();
        final long lifeMs = 8000 + (long) (Math.random() * 12000);

        GlassDrop(float x, float y, float r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }
    }

    @Unique
    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rainydlcRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        int i = context.getScaledWindowWidth();
        int j = context.getScaledWindowHeight();
        long l = Util.getMeasuringTimeMs();

        if (rainydlcStart == -1L) rainydlcStart = l;
        if (reloading && reloadStartTime == -1L) reloadStartTime = l;

        float f = reloadCompleteTime > -1L ? (float) (l - reloadCompleteTime) / 1000.0F : -1.0F;
        float g = reloadStartTime > -1L ? (float) (l - reloadStartTime) / 500.0F : -1.0F;

        float alpha;
        if (f >= 1.0F) {
            if (client.currentScreen != null) {
                client.currentScreen.renderWithTooltip(context, 0, 0, deltaTicks);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
            alpha = 1.0F - MathHelper.clamp(f - 1.0F, 0.0F, 1.0F);
        } else if (reloading) {
            if (client.currentScreen != null && g < 1.0F) {
                client.currentScreen.renderWithTooltip(context, mouseX, mouseY, deltaTicks);
            } else {
                client.inGameHud.renderDeferredSubtitles();
            }
            context.createNewRootLayer();
            alpha = MathHelper.clamp(g, 0.0F, 1.0F);
        } else {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorTexture(client.getFramebuffer().getColorAttachment(), 0xFF000000 | RAINYDLC_BG);
            alpha = 1.0F;
        }

        float rp = reload.getProgress();
        progress = MathHelper.clamp(progress * 0.95F + rp * 0.050000012F, 0.0F, 1.0F);

        boolean drawn = false;
        try {
            drawn = drawRainyDLC(context, alpha, l);
        } catch (Throwable ignored) {
            drawn = false;
        }
        if (!drawn) {
            drawFallback(context, i, j, alpha);
        }

        if (f >= 2.0F) {
            client.setOverlay(null);
        }

        ci.cancel();
    }

    // ── основной рендер ──────────────────────────────────────────────────

    @Unique
    private boolean drawRainyDLC(DrawContext context, float fade, long now) {
        double scaleFactor = client.getWindow().getScaleFactor();
        if (scaleFactor <= 0) return false;

        float scaleFix = 2.0F / (float) scaleFactor;
        int sw = (int) (client.getWindow().getScaledWidth() / scaleFix);
        int sh = (int) (client.getWindow().getScaledHeight() / scaleFix);
        if (sw <= 0 || sh <= 0) return false;

        float intro = MathHelper.clamp((now - rainydlcStart) / 600.0F, 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - intro) * (1.0F - intro) * (1.0F - intro);
        float a = MathHelper.clamp(fade * ease, 0.0F, 1.0F);
        if (a <= 0.01F) return true;

        float t = now / 1000.0F;
        float rise = (1.0F - ease) * 14.0F;

        context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        drawBackdrop(sw, sh, a);
        drawHalo(sw / 2.0F, sh / 2.0F - 22.0F + rise, t, a);
        updateGlassDrops(sw, sh, a, now);
        drawCenterBlock(sw, sh, a, t, rise, now);
        drawProgressBar(sw, sh, a, t);
        drawTips(sw, sh, a, now);
        drawFooter(sw, sh, a);

        Render2D.endOverlay();
        context.getMatrices().popMatrix();
        return true;
    }

    // ── фон: картинка меню + вуаль + морозное размытие + виньетка ────────

    @Unique
    private void drawBackdrop(int sw, int sh, float a) {
        try {
            RenderUtil.Images.texture(MENU_BG, 0, 0, sw, sh, ColorUtil.getColor(255, a));
        } catch (Throwable ignored) {
            Draw.rect(0, 0, sw, sh,
                    ColorUtil.getColor(RAINYDLC_BG >> 16 & 0xFF, RAINYDLC_BG >> 8 & 0xFF, RAINYDLC_BG & 0xFF, a));
        }

        Draw.rect(0, 0, sw, sh, ColorUtil.getColor(0, a * 0.20F));

        ScreenBlur.capture(2);
        Draw.blur(0, 0, sw, sh, a, ColorUtil.getColor(5, 12, 30, a * 0.35F));

        // виньетка сверху и снизу — фокус на центре
        int edge = ColorUtil.getColor(3, 8, 20, a * 0.8F);
        int edgeT = ColorUtil.getColor(3, 8, 20, 0F);
        Draw.gradientRect(0, 0, sw, 64, new int[]{edge, edge, edgeT, edgeT}, 0);
        Draw.gradientRect(0, sh - 90, sw, 90, new int[]{edgeT, edgeT, edge, edge}, 0);
    }

    // ── дышащее холодное гало за логотипом ───────────────────────────────

    @Unique
    private void drawHalo(float cx, float cy, float t, float a) {
        float breathe = 0.70F + 0.30F * (float) Math.sin(t * 0.8F);
        float breathe2 = 0.60F + 0.40F * (float) Math.sin(t * 1.3F + 1.7F);

        Draw.texture(GLOW_TEX, cx - 190, cy - 150, 380, 300,
                ColorUtil.getColor(40, 105, 185, a * 0.13F * breathe));
        Draw.texture(GLOW_TEX, cx - 105, cy - 85, 210, 170,
                ColorUtil.getColor(ACCENT_R, ACCENT_G, ACCENT_B, a * 0.11F * breathe2));
    }

    // ── центральный блок: логотип, название, статус загрузки ─────────────

    @Unique
    private void drawCenterBlock(int sw, int sh, float a, float t, float rise, long now) {
        float cx = sw / 2.0F;
        float cy = sh / 2.0F - 22.0F + rise + 2.0F * (float) Math.sin(t * 1.3F);

        float breathe = 1.0F + 0.03F * (float) Math.sin(t * 2.0F);
        float logoSize = 26F * breathe;
        Draw.texture(LOGO_TEX, cx - logoSize / 2.0F, cy - logoSize / 2.0F, logoSize, logoSize,
                ColorUtil.getColor(255, a));

        Fonts.sf_bold.drawCentered("RainyDLC", cx, cy + 34F, 24F,
                ColorUtil.getColor(236, 245, 255, a));

        int dots = (int) ((now / 400) % 4);
        String sub = ru.white.lang.Lang.pick("загрузка ресурсов", "loading resources") + ".".repeat(dots);
        Fonts.sf_regular.drawCentered(sub, cx, cy + 66F, 9F,
                ColorUtil.getColor(154, 164, 184, a * 0.85F));
    }

    // ── стеклянная полоса прогресса с бликом и свечением кончика ─────────

    @Unique
    private void drawProgressBar(int sw, int sh, float a, float t) {
        float barW = Math.min(sw * 0.34F, 300F);
        float barH = 4.5F;
        float x = sw / 2.0F - barW / 2.0F;
        float y = sh * 0.74F;

        // стеклянная дорожка
        Draw.rect(x - 1.5F, y - 1.5F, barW + 3F, barH + 3F,
                ColorUtil.getColor(8, 18, 36, a * 0.55F), (barH + 3F) / 2.0F);
        Draw.outline(x - 1.5F, y - 1.5F, barW + 3F, barH + 3F, 0.6F,
                ColorUtil.getColor(255, a * 0.08F), (barH + 3F) / 2.0F);

        float fillW = barW * progress;
        if (fillW > 0.75F) {
            int deep = ColorUtil.getColor(38, 108, 172, a);
            int light = ColorUtil.getColor(130, 210, 250, a);
            Draw.gradientRect(x, y, fillW, barH, new int[]{deep, light, light, deep}, barH / 2.0F);

            // свечение у правого края заливки
            Draw.glow(x + fillW - 7F, y + barH / 2.0F - 7F, 14F, 14F,
                    ColorUtil.getColor(ACCENT_R, ACCENT_G, ACCENT_B, a),
                    7F, 4.5F, 0.45F, 0.6F);

            // бегущий блик по залитой части
            float sweep = (t * 0.55F) % 1.0F;
            float sx = x + sweep * fillW;
            float shimW = Math.min(16F, fillW);
            sx = MathHelper.clamp(sx - shimW / 2.0F, x, x + fillW - shimW);
            Draw.rect(sx, y, shimW, barH, ColorUtil.getColor(235, 246, 255, a * 0.30F), barH / 2.0F);
        }

        String pct = (int) (progress * 100F) + "%";
        Fonts.sf_bold.draw(pct, x + barW + 9F, y - 2.5F, 8F,
                ColorUtil.getColor(220, 226, 240, a));
    }

    // ── вращающиеся подсказки с плавным кроссфейдом ──────────────────────

    @Unique
    private void drawTips(int sw, int sh, float a, long now) {
        if (TIPS.length == 0) return;

        long cycle = now % (TIP_PERIOD_MS * TIPS.length);
        int idx = (int) (cycle / TIP_PERIOD_MS);
        long phase = cycle % TIP_PERIOD_MS;

        float fadeMul = 1.0F;
        if (phase < 350L) fadeMul = phase / 350.0F;
        else if (phase > TIP_PERIOD_MS - 350L) fadeMul = (TIP_PERIOD_MS - phase) / 350.0F;

        String tip = ru.white.lang.Lang.pick(TIPS[idx][0], TIPS[idx][1]);
        Fonts.sf_regular.drawCentered(tip, sw / 2.0F, sh * 0.80F, 7.5F,
                ColorUtil.getColor(150, 200, 230, a * 0.55F * MathHelper.clamp(fadeMul, 0F, 1F)));
    }

    // ── подпись внизу ────────────────────────────────────────────────────

    @Unique
    private void drawFooter(int sw, int sh, float a) {
        Fonts.sf_bold.drawCentered(
                ru.white.lang.Lang.pick("RainyDLC · 2026 · Все права защищены", "RainyDLC · 2026 · All rights reserved"),
                sw / 2.0F, sh - 22F, 8F, ColorUtil.getColor(255, a * 0.14F));
    }

    // ── дождь на «стекле»: капли с физикой, как в главном меню ───────────

    @Unique
    private void updateGlassDrops(float w, float h, float anim, long now) {
        if (anim <= 0.01F) return;

        long dt = lastDropFrame == -1L ? 16L : MathHelper.clamp(now - lastDropFrame, 1L, 60L);
        lastDropFrame = now;

        float S = Math.max(0.8F, (float) client.getWindow().getScaleFactor() / 2.0F);
        int target = MathHelper.clamp((int) (w / 30), 12, 40);

        Iterator<GlassDrop> it = glassDrops.iterator();
        while (it.hasNext()) {
            GlassDrop d = it.next();

            if (!d.sliding && now - d.born > d.lifeMs) {
                it.remove();
                continue;
            }

            if (!d.sliding && d.r > 2.6F && Math.random() < 0.0009 * dt) {
                d.sliding = true;
                d.slideStartY = d.y;
            }

            if (d.sliding) {
                d.vy += 0.00035 * dt;
                d.y += d.vy * dt;

                // мокрый след за каплей
                float trailH = d.y - d.slideStartY;
                if (trailH > 2F) {
                    Draw.gradientRect(d.x - d.r * S * 0.7F, d.slideStartY,
                            d.r * S * 1.4F, trailH,
                            new int[]{
                                    ColorUtil.getColor(10, 24, 46, 0F),
                                    ColorUtil.getColor(10, 24, 46, 0F),
                                    ColorUtil.replAlpha(ColorUtil.getColor(10, 24, 46), anim * 70),
                                    ColorUtil.replAlpha(ColorUtil.getColor(10, 24, 46), anim * 70)
                            }, d.r * S * 1.4F);
                }
                if (d.y > h + 30) {
                    it.remove();
                    continue;
                }
            } else {
                // капля медленно наливается
                if (d.r < 3.4F) d.r += 0.00035 * dt;
            }

            drawGlassDrop(d, anim, S);
        }

        int added = 0;
        while (glassDrops.size() < target && added++ < 3) {
            float roll = (float) Math.random();
            float r;
            if (roll < 0.72F) r = 0.7F + (float) Math.random() * 0.8F;
            else if (roll < 0.95F) r = 1.5F + (float) Math.random() * 1.1F;
            else r = 2.7F + (float) Math.random() * 1.5F;
            glassDrops.add(new GlassDrop((float) (Math.random() * w), (float) (Math.random() * h), r));
        }
    }

    /** Реалистичная капля на стекле: тёмное тело, ядро, рефракция, блик. */
    @Unique
    private void drawGlassDrop(GlassDrop d, float anim, float S) {
        float dr = d.r * S;
        float stretch = d.sliding ? Math.min(1.7F, 1.0F + (float) d.vy * 6.0F) : 1.0F;
        float bh = dr * stretch;

        Draw.rect(d.x - dr, d.y - bh, dr * 2, bh * 2,
                ColorUtil.replAlpha(ColorUtil.getColor(12, 26, 48), anim * 125), Math.min(dr, bh));
        Draw.rect(d.x - dr * 0.42F, d.y - bh * 0.25F, dr * 1.02F, bh * 1.05F,
                ColorUtil.replAlpha(ColorUtil.getColor(6, 14, 30), anim * 95), dr * 0.5F);
        Draw.rect(d.x - dr * 0.66F, d.y + bh * 0.32F, dr * 1.32F, bh * 0.36F,
                ColorUtil.replAlpha(ColorUtil.getColor(150, 195, 240), anim * 75), dr * 0.34F);

        if (dr > 1.1F) {
            Draw.rect(d.x - dr * 0.62F, d.y - bh * 0.76F, dr * 0.5F, bh * 0.32F,
                    ColorUtil.replAlpha(ColorUtil.getColor(238, 249, 255), anim * 185), dr * 0.17F);
            Draw.rect(d.x + dr * 0.05F, d.y - bh * 0.42F, dr * 0.15F, bh * 0.12F,
                    ColorUtil.replAlpha(ColorUtil.getColor(255), anim * 155), dr * 0.07F);
        }
    }

    // ── аварийный рендер без шрифтов/пайплайнов ──────────────────────────

    @Unique
    private void drawFallback(DrawContext context, int width, int height, float alpha) {
        int a = MathHelper.ceil(MathHelper.clamp(alpha, 0F, 1F) * 255F);
        if (a <= 4) return;

        context.fill(0, 0, width, height, withAlpha(RAINYDLC_BG, a));

        TextRenderer tr = client.textRenderer;
        var matrices = context.getMatrices();
        String title = "RAINYDLC";
        float s = 4.0F;
        matrices.pushMatrix();
        matrices.scale(s, s);
        float cx = (width / 2.0F) / s;
        float ty = (height / 2.0F - 26F) / s;
        float half = tr.getWidth(title) / 2.0F;
        context.drawText(tr, title, (int) (cx - half), (int) ty, withAlpha(0x4191CD, a), false);
        matrices.popMatrix();

        int barW = (int) Math.min(width * 0.5F, 320F);
        int x = width / 2 - barW / 2;
        int y = (int) (height * 0.78F);
        context.fill(x, y, x + barW, y + 4, withAlpha(0x232A3A, (int) (a * 0.85F)));
        int fillW = MathHelper.ceil((barW - 2) * progress);
        context.fill(x + 1, y + 1, x + 1 + fillW, y + 3, withAlpha(0x4191CD, a));
        String pct = (int) (progress * 100F) + "%";
        context.drawText(tr, pct, x + barW + 8, y - 2, ColorHelper.getArgb(a, 220, 226, 240), false);
    }
}
