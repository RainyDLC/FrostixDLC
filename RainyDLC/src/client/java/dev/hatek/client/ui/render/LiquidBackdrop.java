package dev.hatek.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Снимок мира для Liquid Glass. Копия main render target делается на GPU
 * в начале отрисовки GUI (LiquidBackdropMixin -> GuiRenderer.render HEAD):
 * там уже есть мир этого кадра, но ещё нет GUI — стекло не видит само себя.
 */
public final class LiquidBackdrop {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("hatek_client", "dyn/liquid_backdrop");

    private static DynamicTexture texture;
    private static int width;
    private static int height;
    private static boolean wanted;
    private static long lastCapture;

    private LiquidBackdrop() {
    }

    /** Экран хочет снимок в этом кадре (вызывается на extract). */
    public static void request() {
        wanted = true;
    }

    /** Снимок свежий — можно рисовать преломление. Иначе экран падает на обычный blur. */
    public static boolean ready() {
        return texture != null && System.nanoTime() - lastCapture < 250_000_000L;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static void capture() {
        if (!wanted) {
            return;
        }
        wanted = false;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target == null || target.getColorTexture() == null || target.width <= 0 || target.height <= 0) {
            return;
        }
        ensure(target.width, target.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(), texture.getTexture(), 0, 0, 0, 0, 0, width, height);
        lastCapture = System.nanoTime();
    }

    private static void ensure(int w, int h) {
        if (texture != null && w == width && h == height) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (texture != null) {
            mc.getTextureManager().release(ID);
        }
        texture = new DynamicTexture(() -> "hatek/liquid_backdrop",
                new NativeImage(NativeImage.Format.RGBA, w, h, false));
        mc.getTextureManager().register(ID, texture);
        width = w;
        height = h;
    }
}
