package dev.hatek.mixin;

import dev.hatek.client.cosmetic.SwordCosmetics;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Для меча в руке локального игрока строим модель от копии с названием
 * выбранного меча — ресурспак Blades of Majestica подставит свою 3D-модель.
 */
@Mixin(ItemModelResolver.class)
public class CosmeticItemModelMixin {
    @Unique
    private static final ThreadLocal<Boolean> HATEK$NESTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "updateForTopItem", at = @At("HEAD"), cancellable = true)
    private void hatek$cosmeticSword(ItemStackRenderState state, ItemStack stack,
                                     ItemDisplayContext context, Level level, ItemOwner owner,
                                     int seed, CallbackInfo ci) {
        if (HATEK$NESTED.get()) {
            return;
        }
        ItemStack replacement = SwordCosmetics.replacement(stack, context, owner);
        if (replacement == null) {
            return;
        }
        HATEK$NESTED.set(Boolean.TRUE);
        try {
            ((ItemModelResolver) (Object) this).updateForTopItem(state, replacement, context, level, owner, seed);
        } finally {
            HATEK$NESTED.set(Boolean.FALSE);
        }
        ci.cancel();
    }
}
