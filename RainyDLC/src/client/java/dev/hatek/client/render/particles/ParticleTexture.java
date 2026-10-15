package dev.hatek.client.render.particles;

import net.minecraft.resources.Identifier;

/** Пара «название в меню → текстура». Порт rtx.kimiko ParticleTexture (Kotlin data class → обычный класс). */
public final class ParticleTexture {
    private final String mode;
    private final Identifier id;

    public ParticleTexture(String mode, Identifier id) {
        this.mode = mode;
        this.id = id;
    }

    public String mode() {
        return this.mode;
    }

    public Identifier id() {
        return this.id;
    }
}
