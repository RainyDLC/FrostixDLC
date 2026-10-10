package dev.hatek.client.module.impl.render.interf;

import dev.hatek.client.module.impl.render.Interface;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Маленькое всплывающее окно: выбор позиции ватермарки.
 * Открывается правым кликом по ватермарке.
 * Всё рисуется в координатах Screen (getGuiScaledWidth/Height),
 * кнопки позиционируются в тех же координатах в init().
 */
public final class WatermarkPosScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 178;
    private static final int BTN_W = 180;
    private static final int BTN_H = 22;
    private static final int BTN_GAP = 6;

    public WatermarkPosScreen() {
        super(Component.literal("Позиция ватермарки"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int by = panelY + 62;

        addRenderableWidget(optionButton("Слева", "Left", cx, by));
        addRenderableWidget(optionButton("По центру", "Center", cx, by + BTN_H + BTN_GAP));
        addRenderableWidget(optionButton("Справа", "Right", cx, by + 2 * (BTN_H + BTN_GAP)));
    }

    private Button optionButton(String label, String pos, int cx, int y) {
        return Button.builder(Component.literal(label), btn -> choose(pos))
                .bounds(cx - BTN_W / 2, y, BTN_W, BTN_H)
                .build();
    }

    private void choose(String pos) {
        Interface module = Interface.instance();
        if (module != null) {
            module.setWatermarkPos(pos);
        }
        Minecraft.getInstance().setScreenAndShow(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int px = cx - PANEL_W / 2;
        int py = (this.height - PANEL_H) / 2;

        gg.fill(0, 0, this.width, this.height, 0x80000000);
        gg.fill(px, py, px + PANEL_W, py + PANEL_H, 0xE0141414);
        gg.fill(px, py, px + PANEL_W, py + 2, Style.accent());
        gg.centeredText(this.font, Component.literal("Позиция ватермарки"),
                cx, py + 16, 0xFFFFFFFF);
        gg.centeredText(this.font, Component.literal("куда поставить ватермарку?"),
                cx, py + 32, 0xFF9A9A9A);

        super.extractRenderState(gg, mouseX, mouseY, partialTick);
    }
}
