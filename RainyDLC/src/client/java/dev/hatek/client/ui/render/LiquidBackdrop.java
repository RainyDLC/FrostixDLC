package dev.hatek.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Снимки мира для Liquid Glass (копия main render target на GPU, без чтения на CPU).
 *
 * SHARP — чёткий мир: GuiRenderer.render HEAD (мир без HUD), затем перезаписывается
 *         на GameRenderer.processBlurEffect HEAD (мир + ванильный HUD, до размытия).
 * FROST — тот же кадр после ванильного blur: processBlurEffect TAIL.
 *
 * Экран рисует SHARP на весь экран (снаружи мир снова чёткий), а внутри панели — FROST
 * с искажением линзы. Нет blur (настройка "Размытие фона" = 0) — линза по SHARP без матовости.
 */
public final class LiquidBackdrop {
    public static final Identifier SHARP = Identifier.fromNamespaceAndPath("hatek_client", "dyn/liquid_sharp");
    public static final Identifier FROST = Identifier.fromNamespaceAndPath("hatek_client", "dyn/liquid_frost");
    private static final long FRESH_NS = 250_000_000L;

    private static final Slot SHARP_SLOT = new Slot(SHARP, "hatek/liquid_sharp");
    private static final Slot FROST_SLOT = new Slot(FROST, "hatek/liquid_frost");

    /** Ставит экран каждый кадр, пока открыт ClickGUI в режиме Liquid Glass. */
    private static volatile boolean active;
    private static long lastRequest;

    private LiquidBackdrop() {
    }

    public static void request() {
        active = true;
        lastRequest = System.nanoTime();
    }

    public static void stop() {
        active = false;
    }

    public static boolean sharpReady() {
        return SHARP_SLOT.fresh();
    }

    public static boolean frostReady() {
        return FROST_SLOT.fresh();
    }

    public static int width() {
        return SHARP_SLOT.width;
    }

    public static int height() {
        return SHARP_SLOT.height;
    }

    // ---- хуки из миксинов

    public static void onGuiRender() {
        capture(SHARP_SLOT);
    }

    public static void onBlurStart() {
        capture(SHARP_SLOT);
    }

    public static void onBlurEnd() {
        capture(FROST_SLOT);
    }

    private static void capture(Slot slot) {
        if (!active || System.nanoTime() - lastRequest > FRESH_NS) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (target == null || target.getColorTexture() == null || target.width <= 0 || target.height <= 0) {
            return;
        }
        slot.ensure(target.width, target.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(), slot.texture.getTexture(), 0, 0, 0, 0, 0, slot.width, slot.height);
        slot.captured = System.nanoTime();
    }

    private static final class Slot {
        final Identifier id;
        final String label;
        DynamicTexture texture;
        int width;
        int height;
        long captured;

        Slot(Identifier id, String label) {
            this.id = id;
            this.label = label;
        }

        boolean fresh() {
            return this.texture != null && System.nanoTime() - this.captured < FRESH_NS;
        }

        void ensure(int w, int h) {
            if (this.texture != null && w == this.width && h == this.height) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (this.texture != null) {
                mc.getTextureManager().release(this.id);
            }
            String name = this.label;
            this.texture = new DynamicTexture(() -> name, new NativeImage(NativeImage.Format.RGBA, w, h, false));
            mc.getTextureManager().register(this.id, this.texture);
            this.width = w;
            this.height = h;
        }
    }
}
