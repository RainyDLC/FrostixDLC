package dev.hatek.mixin;

import dev.hatek.client.cosmetic.SwordRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Помечает состояние меча в руке локального игрока для кастомного рендера. */
@Mixin(ItemModelResolver.class)
public class CosmeticItemModelMixin {
    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void hatek$markCosmeticSword(ItemStackRenderState state, ItemStack stack,
                                         ItemDisplayContext context, Level level, ItemOwner owner,
                                         int seed, CallbackInfo ci) {
        SwordRenderer.onUpdate(state, stack, context, owner);
    }
}
