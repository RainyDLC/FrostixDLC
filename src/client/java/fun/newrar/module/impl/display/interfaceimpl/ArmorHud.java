package fun.newrar.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.ItemRender;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

public class ArmorHud implements IMinecraft {

    private final fun.newrar.utils.animation.satoshi.Animation openAnim = new EaseInOutQuad(250, 1);

    private static final ItemStack[] PREVIEW_ARMOR = new ItemStack[]{
            Items.NETHERITE_HELMET.getDefaultStack(),
            Items.NETHERITE_CHESTPLATE.getDefaultStack(),
            Items.NETHERITE_LEGGINGS.getDefaultStack(),
            Items.NETHERITE_BOOTS.getDefaultStack()
    };

    private static final ItemStack PREVIEW_MAINHAND = Items.NETHERITE_SWORD.getDefaultStack();
    private static final ItemStack PREVIEW_OFFHAND = Items.TOTEM_OF_UNDYING.getDefaultStack();

    private static class ArmorItemData {
        ItemStack stack;
        EquipmentSlot slot;
        boolean isPlaceholder;

        ArmorItemData(ItemStack stack, EquipmentSlot slot, boolean isPlaceholder) {
            this.stack = stack;
            this.slot = slot;
            this.isPlaceholder = isPlaceholder;
        }
    }

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        if (mc.player == null) return;

        float scale = interFace.armorScale != null ? interFace.armorScale.getValue() : 1.0F;
        float s = interFace.sizeHud.getValue() * 1.1F * scale;
        float opacity = interFace.alphaHUD.getValue();
        boolean isHorizontal = interFace.armorOrientation != null && interFace.armorOrientation.is("Горизонтальная");
        String durMode = interFace.armorDurability != null ? interFace.armorDurability.getValue() : "Проценты";
        boolean showHands = interFace.armorHands != null && interFace.armorHands.getValue();
        boolean showEmptySlots = interFace.armorEmptySlots != null && interFace.armorEmptySlots.getValue();
        boolean glow = interFace.armorGlow != null && interFace.armorGlow.getValue();

        Font fontBold = interFace.fontMode.is("Уникальный") ? Fonts.unique_bold : Fonts.sf_bold;

        List<ArmorItemData> items = new ArrayList<>();
        boolean inChat = mc.currentScreen instanceof ChatScreen;

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        if (showHands) {
            ItemStack mainStack = mc.player.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!mainStack.isEmpty()) {
                items.add(new ArmorItemData(mainStack, EquipmentSlot.MAINHAND, false));
            } else if (inChat) {
                items.add(new ArmorItemData(PREVIEW_MAINHAND, EquipmentSlot.MAINHAND, true));
            } else if (showEmptySlots) {
                items.add(new ArmorItemData(ItemStack.EMPTY, EquipmentSlot.MAINHAND, false));
            }
        }

        for (int i = 0; i < armorSlots.length; i++) {
            EquipmentSlot slot = armorSlots[i];
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                items.add(new ArmorItemData(stack, slot, false));
            } else if (inChat) {
                items.add(new ArmorItemData(PREVIEW_ARMOR[i], slot, true));
            } else if (showEmptySlots) {
                items.add(new ArmorItemData(ItemStack.EMPTY, slot, false));
            }
        }

        if (showHands) {
            ItemStack offStack = mc.player.getEquippedStack(EquipmentSlot.OFFHAND);
            if (!offStack.isEmpty()) {
                items.add(new ArmorItemData(offStack, EquipmentSlot.OFFHAND, false));
            } else if (inChat) {
                items.add(new ArmorItemData(PREVIEW_OFFHAND, EquipmentSlot.OFFHAND, true));
            } else if (showEmptySlots) {
                items.add(new ArmorItemData(ItemStack.EMPTY, EquipmentSlot.OFFHAND, false));
            }
        }

        boolean hasAnyEquipped = false;
        for (ArmorItemData d : items) {
            if (!d.stack.isEmpty() && !d.isPlaceholder) {
                hasAnyEquipped = true;
                break;
            }
        }

        boolean shouldClose = !hasAnyEquipped && !inChat;
        openAnim.setDirection(shouldClose ? Direction.BACKWARDS : Direction.FORWARDS);

        float alpha = openAnim.getOutput();
        if (shouldClose && alpha <= 0.01F) {
            dragSetting.active = false;
            return;
        }
        dragSetting.active = true;

        if (items.isEmpty()) return;

        int count = items.size();
        float padX = 4.0F * s;
        float padY = 4.0F * s;
        float gap = 3.5F * s;
        float radius = 6.0F * s;

        float slotW, slotH, totalW, totalH;

        if (isHorizontal) {
            slotW = 22.0F * s;
            slotH = durMode.equals("Нет") ? 22.0F * s : 29.0F * s;
            totalW = padX * 2.0F + count * slotW + (count - 1) * gap;
            totalH = padY * 2.0F + slotH;
        } else {
            slotW = durMode.equals("Нет") || durMode.equals("Полоса") ? 22.0F * s : 44.0F * s;
            slotH = 22.0F * s;
            totalW = padX * 2.0F + slotW;
            totalH = padY * 2.0F + count * slotH + (count - 1) * gap;
        }

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;
        dragSetting.size.set(totalW, totalH);

        // Ambient Neon Glow
        if (glow) {
            RenderUtil.Render2D.glow(x, y + 1.5F * s, totalW, totalH,
                    ColorUtil.replAlpha(ColorUtil.client(), 0.14F * opacity * alpha),
                    radius + 3.0F * s, 10.0F * s, 0.8F);
        }

        // Frosted Blur
        int tint = ((int) (255 * opacity * alpha * 0.90F) << 24) | 0x080A12;
        RenderUtil.Blur.blur(x, y, totalW, totalH, alpha, radius, tint);

        // Obsidian Glass Acrylic Fill
        Draw.rect(x, y, totalW, totalH, ColorUtil.getColor(10, 12, 18, (int) (185 * opacity * alpha)), radius);

        // Specular Apple-like Glass Outline
        Draw.glassOutline(x, y, totalW, totalH, 0.55F * s, radius, 0.48F * opacity * alpha, 0.22F);

        // Bottom accent runner line
        int acc = ColorUtil.replAlpha(ColorUtil.client(), 0.50F * opacity * alpha);
        int accDark = ColorUtil.multDark(acc, 0.15F);
        RenderUtil.Render2D.gradientRect(x + radius, y + totalH - 1.0F * s,
                totalW - radius * 2.0F, 0.8F * s,
                new int[]{accDark, acc, acc, accDark}, 0.4F * s);

        DrawContext drawContext = eventDisplay.getDrawContext();

        for (int i = 0; i < count; i++) {
            ArmorItemData itemData = items.get(i);
            ItemStack stack = itemData.stack;

            float slotX, slotY;
            if (isHorizontal) {
                slotX = x + padX + i * (slotW + gap);
                slotY = y + padY;
            } else {
                slotX = x + padX;
                slotY = y + padY + i * (slotH + gap);
            }

            // Durability calculation
            boolean damageable = !stack.isEmpty() && stack.isDamageable();
            int maxDmg = damageable ? stack.getMaxDamage() : 0;
            int dmg = damageable ? stack.getDamage() : 0;
            int remaining = damageable ? maxDmg - dmg : 0;
            float durFraction = (damageable && maxDmg > 0) ? (float) remaining / (float) maxDmg : 1.0F;
            durFraction = Math.max(0.0F, Math.min(1.0F, durFraction));
            int percent = Math.round(durFraction * 100.0F);

            boolean isCritical = damageable && durFraction < 0.15F;

            // Slot Background tile
            Draw.rect(slotX, slotY, slotW, slotH,
                    ColorUtil.getColor(20, 22, 32, (int) (140 * opacity * alpha)), 4.0F * s);
            Draw.glassOutline(slotX, slotY, slotW, slotH, 0.4F * s, 4.0F * s,
                    0.25F * opacity * alpha, 0.10F);

            // Critical durability pulsing warning border
            if (isCritical) {
                float pulse = (float) (0.4 + 0.6 * Math.sin(System.currentTimeMillis() * 0.008));
                Draw.outline(slotX, slotY, slotW, slotH, 0.7F * s,
                        ColorUtil.getColor(255, 45, 45, (int) (220 * pulse * alpha)), 4.0F * s);
            }

            // Color for durability bar & text
            int durColor = durFraction > 0.65F
                    ? ColorUtil.getColor(16, 185, 129, (int) (255 * alpha))
                    : (durFraction > 0.35F
                    ? ColorUtil.getColor(245, 158, 11, (int) (255 * alpha))
                    : (durFraction > 0.15F
                    ? ColorUtil.getColor(251, 146, 60, (int) (255 * alpha))
                    : ColorUtil.getColor(239, 68, 68, (int) (255 * alpha))));

            if (!stack.isEmpty()) {
                float renderAlpha = itemData.isPlaceholder ? alpha * 0.55F : alpha;

                if (isHorizontal) {
                    float itemX = slotX + (slotW - 16.0F * s) / 2.0F;
                    float itemY = slotY + 2.5F * s;
                    ItemRender.drawItemWithContext(drawContext, stack, itemX, itemY, s, renderAlpha);

                    // Stack count (e.g. Totems x3)
                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        float countW = fontBold.getWidth(countStr, 4.5F * s);
                        fontBold.draw(countStr, itemX + 16.0F * s - countW + 1.0F * s,
                                itemY + 11.0F * s, 4.5F * s, ColorUtil.getColor(255, (int) (255 * renderAlpha)));
                    }

                    // Durability Bar
                    if (damageable && !durMode.equals("Нет")) {
                        float barW = 15.0F * s;
                        float barH = 1.6F * s;
                        float barX = slotX + (slotW - barW) / 2.0F;
                        float barY = slotY + 19.5F * s;

                        Draw.rect(barX, barY, barW, barH, ColorUtil.getColor(255, 0.12F * alpha), barH / 2.0F);
                        if (durFraction > 0.0F) {
                            Draw.rect(barX, barY, barW * durFraction, barH, durColor, barH / 2.0F);
                        }

                        // Durability Text
                        if (durMode.equals("Проценты")) {
                            String pctStr = percent + "%";
                            fontBold.drawCentered(pctStr, slotX + slotW / 2.0F, barY + 2.8F * s,
                                    4.2F * s, durColor);
                        } else if (durMode.equals("Числа")) {
                            String numStr = String.valueOf(remaining);
                            fontBold.drawCentered(numStr, slotX + slotW / 2.0F, barY + 2.8F * s,
                                    4.2F * s, durColor);
                        }
                    }
                } else {
                    // Vertical
                    float itemX = slotX + 3.0F * s;
                    float itemY = slotY + (slotH - 16.0F * s) / 2.0F;
                    ItemRender.drawItemWithContext(drawContext, stack, itemX, itemY, s, renderAlpha);

                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        float countW = fontBold.getWidth(countStr, 4.5F * s);
                        fontBold.draw(countStr, itemX + 16.0F * s - countW + 1.0F * s,
                                itemY + 11.0F * s, 4.5F * s, ColorUtil.getColor(255, (int) (255 * renderAlpha)));
                    }

                    if (damageable && !durMode.equals("Нет")) {
                        if (durMode.equals("Полоса")) {
                            float barW = 15.0F * s;
                            float barH = 1.6F * s;
                            float barX = slotX + (slotW - barW) / 2.0F;
                            float barY = slotY + slotH - 3.2F * s;
                            Draw.rect(barX, barY, barW, barH, ColorUtil.getColor(255, 0.12F * alpha), barH / 2.0F);
                            if (durFraction > 0.0F) {
                                Draw.rect(barX, barY, barW * durFraction, barH, durColor, barH / 2.0F);
                            }
                        } else {
                            float contentX = slotX + 22.0F * s;
                            String textStr = durMode.equals("Проценты") ? percent + "%" : String.valueOf(remaining);
                            fontBold.draw(textStr, contentX, slotY + 4.2F * s, 5.0F * s, durColor);

                            float barW = 18.0F * s;
                            float barH = 1.6F * s;
                            float barY = slotY + 13.0F * s;
                            Draw.rect(contentX, barY, barW, barH, ColorUtil.getColor(255, 0.12F * alpha), barH / 2.0F);
                            if (durFraction > 0.0F) {
                                Draw.rect(contentX, barY, barW * durFraction, barH, durColor, barH / 2.0F);
                            }
                        }
                    }
                }
            } else {
                // Empty slot indicator
                float cx = slotX + (isHorizontal ? slotW / 2.0F : 11.0F * s);
                float cy = slotY + slotH / 2.0F;
                Draw.rect(cx - 2.0F * s, cy - 0.5F * s, 4.0F * s, 1.0F * s,
                        ColorUtil.getColor(255, 0.15F * alpha), 0.5F * s);
            }
        }
    }
}
