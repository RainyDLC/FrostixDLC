package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

/**
 * Панель брони второго вида худа — как на референсе:
 * вертикальный список предметов (броня + руки) с числом прочности.
 */
public class ArmorHud implements IMinecraft {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static float S = 1.0F;

    private static float H = 16F * S;
    private static float MIN_W = 40F * S;
    private static float RADIUS = 5F * S;

    private static float ROW_TEXT = 6.5F * S;
    private static float ITEM_SIZE = 9F * S;

    private static float ROW_HEIGHT = 12.5F * S;
    private static float ROW_BASE_W = 27F * S;
    private static float ROW_PADDING_X = 4F * S;
    private static float ROW_START_Y = 3F * S;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        S = InterFace.getInstance().sizeHud.getValue();
        H = 16F * S;
        MIN_W = 40F * S;
        RADIUS = 5F * S;
        ROW_TEXT = 6.5F * S;
        ITEM_SIZE = 9F * S;
        ROW_HEIGHT = 12.5F * S;
        ROW_BASE_W = 27F * S;
        ROW_PADDING_X = 4F * S;
        ROW_START_Y = 3F * S;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        Font font = Fonts.sf_regular;
        float alpha = 1;

        // считаем размеры: показываем только непустые предметы
        int count = 0;
        float maxDurWidth = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            count++;
            int durability = durability(stack);
            if (durability >= 0) {
                maxDurWidth = Math.max(maxDurWidth, font.getWidth(String.valueOf(durability), ROW_TEXT));
            }
        }

        if (count == 0) {
            dragSetting.size.set(MIN_W, H);
            return;
        }

        float w = Math.max(ROW_BASE_W + ITEM_SIZE + 3F * S + maxDurWidth, MIN_W);
        float h = ROW_HEIGHT * count + ROW_START_Y;

        RenderUtil.Render2D.hudPlate(x, y, w, h, alpha, RADIUS, interFace.alphaHUD.getValue());

        float offsetY = y + ROW_START_Y;

        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            // предмет слева
            ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), stack,
                    x + ROW_PADDING_X, offsetY + (ROW_HEIGHT - ITEM_SIZE) / 2F - 0.8F * S, ITEM_SIZE / 16F, 1);

            int durability = durability(stack);

            if (durability < 0) {
                // у предмета нет прочности — рисуем количество
                String amount = String.valueOf(stack.getCount());
                font.draw(amount, x + w - ROW_PADDING_X - font.getWidth(amount, ROW_TEXT),
                        offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha));
            } else {
                // цвет прочности: зелёный > 60%, жёлтый > 25%, красный ниже
                float pct = (float) durability / (float) stack.getMaxDamage();
                int color = pct > 0.6F ? ColorUtil.getColor(90, 220, 110, alpha)
                        : pct > 0.25F ? ColorUtil.getColor(240, 200, 70, alpha)
                        : ColorUtil.getColor(235, 70, 70, alpha);

                String text = String.valueOf(durability);
                font.draw(text, x + w - ROW_PADDING_X - font.getWidth(text, ROW_TEXT), offsetY, ROW_TEXT, color);
            }

            offsetY += ROW_HEIGHT;
        }

        dragSetting.size.set(w, h);
    }

    /** Остаток прочности или -1, если предмет не ломается. */
    private int durability(ItemStack stack) {
        return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : -1;
    }
}
