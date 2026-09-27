package rtx.kimiko.api.modules.impl.Visuals.atmosphere.texture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * White texture whose alpha comes from a function. Lazily uploaded on the render thread,
 * so no PNG assets are needed and everything stays tweakable in code.
 */
public final class ProceduralTexture {

    @FunctionalInterface
    public interface AlphaFunction {
        /** @param u,v in -1..1, center is 0,0. Return alpha 0..1. */
        float alpha(float u, float v);
    }

    private final Identifier id;
    private final int width;
    private final int height;
    private final AlphaFunction function;
    private boolean uploaded;

    public ProceduralTexture(String name, int width, int height, AlphaFunction function) {
        this.id = Identifier.of("kimiko", "atmosphere/" + name);
        this.width = width;
        this.height = height;
        this.function = function;
    }

    public Identifier id() {
        if (!uploaded) {
            upload();
            uploaded = true;
        }
        return id;
    }

    /** Forces re-generation next time {@link #id()} is used. */
    public void invalidate() {
        uploaded = false;
    }

    private void upload() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            float v = (y + 0.5f) / height * 2f - 1f;
            for (int x = 0; x < width; x++) {
                float u = (x + 0.5f) / width * 2f - 1f;
                int a = Math.round(Math.max(0f, Math.min(1f, function.alpha(u, v))) * 255f);
                image.setColorArgb(x, y, (a << 24) | 0xFFFFFF);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
    }
}
