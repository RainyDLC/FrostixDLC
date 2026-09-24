package fun.newrar.mixin;

import fun.newrar.module.impl.movement.NoWeb;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Паутина ставит замедление через этот колбэк: CobwebBlock#onEntityCollision -> Entity#slowMovement.
 * Отменяем вызов целиком — тогда movementMultiplier остаётся Vec3d.ZERO, и Entity#move не режет
 * движение и не обнуляет velocity (именно обнуление убивало импульс в прежней версии).
 */
@Mixin(CobwebBlock.class)
public class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void noWeb$cancelWebSlow(BlockState state, World world, BlockPos pos, Entity entity,
                                     EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;

        NoWeb noWeb = NoWeb.get();
        if (noWeb == null || !noWeb.isEnabled()) return;

        // ваниль сбрасывает fallDistance внутри slowMovement — держим поведение как у сервера
        entity.onLanding();
        ci.cancel();

        NoWeb.onEntityCollideCobweb(pos);
    }
}
