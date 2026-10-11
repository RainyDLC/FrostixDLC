package dev.hatek.client.cosmetic;

import dev.hatek.client.module.impl.render.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Подмена меча в руке локального игрока на модель из Blades of Majestica.
 * Только для рендера: копии предмета даётся название выбранного меча, и пак сам
 * подставляет свою 3D-модель (с анимацией и эффектами). Настоящий предмет не меняется.
 */
public final class SwordCosmetics {
    /** Меч, показываемый на модельке в колесе при наведении. */
    private static volatile String previewName;
    private static volatile boolean previewActive;

    private SwordCosmetics() {
    }

    public static void beginPreview(String name) {
        previewName = name;
        previewActive = true;
    }

    public static void endPreview() {
        previewActive = false;
        previewName = null;
    }

    private static boolean handContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** @return копию меча с названием косметики или null, если подменять не нужно. */
    public static ItemStack replacement(ItemStack stack, ItemDisplayContext context, ItemOwner owner) {
        try {
            if (stack == null || stack.isEmpty() || owner == null || !handContext(context)) {
                return null;
            }
            LivingEntity living = owner.asLivingEntity();
            if (living == null || living != Minecraft.getInstance().player || !stack.is(ItemTags.SWORDS)) {
                return null;
            }
            String name = previewActive ? previewName : Cosmetics.selectedSword();
            if (name == null || name.isEmpty()) {
                return null;
            }
            return named(stack, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ItemStack named(ItemStack stack, String name) {
        ItemStack copy = stack.copy();
        copy.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return copy;
    }
}
