package ru.white.bot;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import ru.white.utils.render.RenderUtil;

public final class PotatoGraphics {

    @Getter @Setter
    private static boolean enabled = false;

    @Getter @Setter
    private static boolean flatColorBoxes = true;

    @Getter @Setter
    private static boolean disableParticles = true;

    private PotatoGraphics() {}

    public static void toggle() {
        enabled = !enabled;
    }
}
