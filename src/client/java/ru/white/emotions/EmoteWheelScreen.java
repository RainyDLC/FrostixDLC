package ru.white.emotions;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ru.white.utils.other.Instance;

import java.util.List;

/**
 * Колесо выбора эмоций: сектора по кругу, выбор мышью, клик — играть.
 */
public final class EmoteWheelScreen extends Screen {

    private static final int RADIUS = 95;
    private static final int CARD_W = 92;
    private static final int CARD_H = 26;
    private static final int BG = 0xC8101014;
    private static final int CARD = 0xFF1A1B22;
    private static final int CARD_SEL = 0xFF7B4DFF;
    private static final int TEXT = 0xFFE8E8F0;

    private final List<Emote> emotes;
    private int selected = -1;

    public EmoteWheelScreen() {
        super(Text.literal("Emotions"));
        this.emotes = Emotes.ALL;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cx = width / 2;
        int cy = height / 2;

        // затемнение фона
        context.fill(0, 0, width, height, 0x8806060A);

        selected = pickSector(mouseX, mouseY);

        // карточки по кругу
        int n = emotes.size();
        for (int i = 0; i < n; i++) {
            double a = sectorAngle(i, n);
            int px = cx + (int) (Math.cos(a) * RADIUS) - CARD_W / 2;
            int py = cy + (int) (Math.sin(a) * RADIUS) - CARD_H / 2;

            boolean sel = i == selected;
            boolean active = EmoteManager.active() == emotes.get(i);

            context.fill(px, py, px + CARD_W, py + CARD_H, sel ? CARD_SEL : CARD);
            // рамка активной эмоции
            if (active) {
                context.fill(px, py, px + CARD_W, py + 1, 0xFFFFFFFF);
                context.fill(px, py + CARD_H - 1, px + CARD_W, py + CARD_H, 0xFFFFFFFF);
            }

            String label = emotes.get(i).name();
            int tw = textRenderer.getWidth(label);
            context.drawText(textRenderer, label,
                    px + (CARD_W - tw) / 2, py + (CARD_H - 8) / 2, TEXT, false);
        }

        // центр
        context.fill(cx - 52, cy - 14, cx + 52, cy + 14, BG);
        String title = "EMOTIONS";
        context.drawText(textRenderer, title,
                cx - textRenderer.getWidth(title) / 2, cy - 4, TEXT, false);

        super.render(context, mouseX, mouseY, delta);
    }

    private static double sectorAngle(int i, int n) {
        // старт сверху, по часовой
        return -Math.PI / 2.0 + (Math.PI * 2.0 * i / n);
    }

    private int pickSector(double mx, double my) {
        double dx = mx - width / 2.0;
        double dy = my - height / 2.0;
        if (Math.sqrt(dx * dx + dy * dy) < 40) return -1;

        double ang = Math.atan2(dy, dx);
        int n = emotes.size();
        double sector = Math.PI * 2.0 / n;
        int idx = (int) Math.round((ang + Math.PI / 2.0) / sector) % n;
        if (idx < 0) idx += n;
        return idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = pickSector(mouseX, mouseY);
            if (idx >= 0) {
                EmoteManager.play(emotes.get(idx));
                close();
                return true;
            }
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // повторное нажатие бинда закрывает колесо
        Emotions module = Instance.get(Emotions.class);
        if (module != null && keyCode == module.wheelKey.get()) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
