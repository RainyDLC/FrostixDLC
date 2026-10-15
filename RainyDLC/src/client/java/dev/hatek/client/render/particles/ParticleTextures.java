package dev.hatek.client.render.particles;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hatek.client.render.particles.embedded.ParticlePngData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;

/**
 * Регистрирует PNG-текстуры частиц как DynamicTexture при первом рендере.
 * Байты зашиты в {@link ParticlePngData} (паттерн dev.hatek.quickembedded) —
 * ресурсные PNG в jar не нужны, переживает resource-pack reload.
 * Identifier'ы совпадают с ParticleConstants, поэтому остальной код не меняется.
 */
public final class ParticleTextures {
    private static volatile boolean registered;

    private ParticleTextures() {
    }

    /** Вызывать из renderWorld модулей перед рендером. Потокобезопасно, однократно. */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        synchronized (ParticleTextures.class) {
            if (registered) {
                return;
            }
            registerAll();
            registered = true;
        }
    }

    private static void registerAll() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (String name : ParticlePngData.names()) {
            try {
                byte[] bytes = ParticlePngData.png(name);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                Identifier id = Identifier.fromNamespaceAndPath(
                        "hatek_client", "textures/particles/" + name + ".png");
                textureManager.register(id,
                        new DynamicTexture(() -> "hatek_particles/" + name, image));
            } catch (Exception e) {
                // Текстура не декодировалась — рендер упадёт на missing texture, не крашим игру.
                System.err.println("[hatek] particles: cannot decode embedded texture " + name + ": " + e);
            }
        }
    }
}
