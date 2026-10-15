package dev.hatek.client.render.particles;

import dev.hatek.client.module.setting.ModeSetting;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Выбор текстуры частицы по настройке «Отображать». Порт rtx.kimiko ParticleTexturePicker.
 * Если ни одна текстура не совпала с выбранным режимом — случайная из каталога.
 */
public final class ParticleTexturePicker {
    public static final String SHOW_ALL = "Отображать всё";

    private static final String[] MODE_OPTIONS = {
            SHOW_ALL, "Точка", "Звезда", "Молния", "Крест", "Корона",
            "Сердце", "Линия", "Ромб", "Доллар", "Снежинка", "Треугольник"
    };

    private ParticleTexturePicker() {
    }

    public static String[] modeOptions() {
        return MODE_OPTIONS.clone();
    }

    public static Identifier pick(ModeSetting mode, Random random) {
        for (ParticleTexture texture : ParticleConstants.TEXTURES) {
            if (mode.is(texture.mode())) {
                return texture.id();
            }
        }
        ParticleTexture[] all = ParticleConstants.TEXTURES;
        return all[random.nextInt(all.length)].id();
    }
}
