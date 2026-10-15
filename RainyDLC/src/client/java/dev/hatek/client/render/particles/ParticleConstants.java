package dev.hatek.client.render.particles;

import net.minecraft.resources.Identifier;

/**
 * Каталог текстур частиц. Порт rtx.kimiko ParticleConstants.
 * Неймспейс kimiko → hatek_client, путь textures/features/particles/ → textures/particles/.
 * PNG лежат в RainyDLC/src/client/resources/assets/hatek_client/textures/particles/.
 */
public final class ParticleConstants {
    public static final String TEXTURE_CROSS = "Крест";
    public static final String TEXTURE_CROWN = "Корона";
    public static final String TEXTURE_DOLLAR = "Доллар";
    public static final String TEXTURE_HEART = "Сердце";
    public static final String TEXTURE_LIGHTNING = "Молния";
    public static final String TEXTURE_LINE = "Линия";
    public static final String TEXTURE_POINT = "Точка";
    public static final String TEXTURE_RHOMBUS = "Ромб";
    public static final String TEXTURE_SNOWFLAKE = "Снежинка";
    public static final String TEXTURE_STAR = "Звезда";
    public static final String TEXTURE_TRIANGLE = "Треугольник";

    public static final ParticleTexture[] TEXTURES = {
            particle(TEXTURE_CROSS, "cross.png"),
            particle(TEXTURE_CROWN, "crown.png"),
            particle(TEXTURE_DOLLAR, "dollar.png"),
            particle(TEXTURE_HEART, "heart.png"),
            particle(TEXTURE_LIGHTNING, "lighting.png"),
            particle(TEXTURE_LINE, "line.png"),
            particle(TEXTURE_POINT, "point.png"),
            particle(TEXTURE_RHOMBUS, "rhombus.png"),
            particle(TEXTURE_SNOWFLAKE, "snowflake.png"),
            particle(TEXTURE_STAR, "star.png"),
            particle(TEXTURE_TRIANGLE, "triangle.png"),
    };

    /** 1×1 белая текстура для неоновых штрихов (DashLineRenderer). */
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath("hatek_client", "textures/particles/white.png");

    private ParticleConstants() {
    }

    private static ParticleTexture particle(String mode, String path) {
        return new ParticleTexture(mode,
                Identifier.fromNamespaceAndPath("hatek_client", "textures/particles/" + path));
    }
}
