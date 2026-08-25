package ru.white.emotions;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import ru.white.module.impl.display.Emotions;
import ru.white.utils.other.Instance;

import java.util.List;

/**
 * Колесо выбора эмоций: тёмный диск с акцентным кольцом, карточки по кругу
 * с тенями, выбор мышью, клик — играть.
 */
public final class EmoteWheelScreen extends Screen {

    private static final int RADIUS = 96;
    private static final int CARD_W = 96;
    private static final int CARD_H = 26;

    private static final int SHADOW = 0x66000000;
    private static final int DISC = 0xE40D0E14;
    private static final int DISC_INNER = 0xF0121320;
    private static final int RING = 0xFF7B4DFF;
    private static final int CARD = 0xF01A1B26;
    private static final int CARD_SEL = 0xF07B4DFF;
    private static final int CARD_BORDER = 0xFF2A2B38;
    private static final int TEXT = 0xFFECECF4;
    private static final int TEXT_DIM = 0xFF8A8B9C;

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

        // мягкое затемнение всего экрана
        context.fill(0, 0, width, height, 0x7A050508);

        selected = pickSector(mouseX, mouseY);

        // диск с тенью и акцентным кольцом
        fillCircle(context, cx, cy + 3, 122, SHADOW);
        fillCircle(context, cx, cy, 118, RING);
        fillCircle(context, cx, cy, 114, DISC);
        fillCircle(context, cx, cy, 56, RING);
        fillCircle(context, cx, cy, 53, DISC_INNER);

        // карточки по кругу
        int n = emotes.size();
        for (int i = 0; i < n; i++) {
            double a = sectorAngle(i, n);
            int px = cx + (int) Math.round(Math.cos(a) * RADIUS) - CARD_W / 2;
            int py = cy + (int) Math.round(Math.sin(a) * RADIUS) - CARD_H / 2;

            boolean sel = i == selected;
            boolean active = EmoteManager.active() == emotes.get(i);

            int inflate = sel ? 3 : 0;
            int x = px - inflate;
            int y = py - inflate;
            int w = CARD_W + inflate * 2;
            int h = CARD_H + inflate * 2;

            // тень -> фон -> рамка -> внутренняя плашка
            context.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
            context.fill(x - 1, y - 1, x + w + 1, y + h + 1, sel ? RING : CARD_BORDER);
            context.fill(x, y, x + w, y + h, sel ? CARD_SEL : CARD);

            // индикатор активной эмоции
            if (active) {
                context.fill(x + w - 7, y + 3, x + w - 4, y + 6, 0xFFFFFFFF);
            }

            String label = emotes.get(i).name();
            int tw = textRenderer.getWidth(label);
            context.drawText(textRenderer, label,
                    x + (w - tw) / 2, y + (h - 8) / 2, sel ? 0xFFFFFFFF : TEXT, false);
        }

        // центр: выбранная эмоция крупно, подсказка снизу
        String title = selected >= 0 ? emotes.get(selected).name() : "EMOTIONS";
        int tw = textRenderer.getWidth(title);
        context.drawText(textRenderer, title,
                cx - tw / 2, cy - 9, TEXT, false);

        String hint = "LMB - play   ESC - close";
        int hw = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint,
                cx - hw / 2, cy + 3, TEXT_DIM, false);

        super.render(context, mouseX, mouseY, delta);
    }

    /** Плавный круг через скан-линии. */
    private void fillCircle(DrawContext context, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt((double) r * r - (double) dy * dy);
            context.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
        }
    }

    private static double sectorAngle(int i, int n) {
        // старт сверху, по часовой
        return -Math.PI / 2.0 + (Math.PI * 2.0 * i / n);
    }

    private int pickSector(double mx, double my) {
        double dx = mx - width / 2.0;
        double dy = my - height / 2.0;
        if (Math.sqrt(dx * dx + dy * dy) < 42) return -1;

        double ang = Math.atan2(dy, dx);
        int n = emotes.size();
        double sector = Math.PI * 2.0 / n;
        int idx = (int) Math.round((ang + Math.PI / 2.0) / sector) % n;
        if (idx < 0) idx += n;
        return idx;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int idx = pickSector(click.x(), click.y());
            if (idx >= 0) {
                EmoteManager.play(emotes.get(idx));
            }
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // закрытие ванильным биндом или клиентским
        if (Emotions.vanillaKey != null && Emotions.vanillaKey.matchesKey(input)) {
            close();
            return true;
        }
        Emotions module = Instance.get(Emotions.class);
        if (module != null && input.key() == module.wheelKey.get()) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}
