package dev.hatek.client.module.impl.combat.aimassist;

import dev.hatek.client.module.impl.combat.AimAssist;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.HFont;
import dev.hatek.client.ui.render.Render2D;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Полоса прогресса сверху экрана.
 * Видна, пока идёт запись наводки или обучение нейронки.
 */
public final class AimHud {
    private static boolean hooked;

    private AimHud() {
    }

    public static void init() {
        if (hooked) {
            return;
        }
        hooked = true;
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("hatek_client", "aim_hud"),
                (gg, tickCounter) -> render(gg));
    }

    private static void render(GuiGraphicsExtractor gg) {
        boolean training = AimAssist.training;
        boolean learning = AimAssist.learning;
        if (!training && !learning) {
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }

        String title;
        String sub;
        float bar = -1.0f;
        if (training) {
            long secs = (System.currentTimeMillis() - AimAssist.trainingStartMs) / 1000L;
            title = "Запись «" + AimAssist.trainingName + "» — наводись и бей";
            sub = AimAssist.trainingSamples + " сэмплов · "
                    + String.format("%02d:%02d", secs / 60L, secs % 60L);
        } else {
            title = "Нейронка обучается…";
            sub = AimAssist.learnStatus;
            bar = (float) AimAssist.learnProgress;
        }

        HFont body = Fonts.body();
        HFont label = Fonts.label();
        float textW = Math.max(body.width(title), label.width(sub));
        float w = textW + 36.0f;
        float h = bar >= 0.0f ? 56.0f : 46.0f;
        float x = (Render2D.screenWidth() - w) / 2.0f;
        float y = 10.0f;

        Render2D.begin(gg);
        Render2D.round(gg, x, y, w, h, 10.0f, Style.HUD_PANEL);
        body.drawCentered(gg, title, x + w / 2.0f, y + 20.0f, Style.WHITE);
        label.drawCentered(gg, sub, x + w / 2.0f, y + 36.0f, Style.WHITE_45);
        if (bar >= 0.0f) {
            float bw = w - 28.0f;
            Render2D.round(gg, x + 14.0f, y + 44.0f, bw, 4.0f, 2.0f, Style.WHITE_04);
            Render2D.round(gg, x + 14.0f, y + 44.0f, bw * Math.min(1.0f, Math.max(0.0f, bar)),
                    4.0f, 2.0f, Style.accent());
        }
        Render2D.end(gg);
    }
}
