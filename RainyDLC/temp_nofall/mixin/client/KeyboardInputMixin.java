package su.DSF.calcite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import su.DSF.calcite.api.events.impl.game.EventInputMove;

@Mixin({KeyboardInput.class})
public class KeyboardInputMixin {
   @Unique
   private static float calcite$mult(boolean positive, boolean negative) {
      return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
   }

   @ModifyExpressionValue(
      method = {"tick"},
      at = {@At(
         value = "NEW",
         target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"
      )}
   )
   private Input calcite$tickHook(Input original) {
      float forward = calcite$mult(original.forward(), original.backward());
      float strafe = calcite$mult(original.left(), original.right());
      EventInputMove event = new EventInputMove(forward, strafe, original.jump(), original.shift(), original.sprint());
      event.call();
      if (event.isCancelled()) {
         return original;
      }

      return new Input(
         event.getForward() > 0.0F,
         event.getForward() < 0.0F,
         event.getStrafe() > 0.0F,
         event.getStrafe() < 0.0F,
         event.isJump(),
         event.isSneaking(),
         event.isSprint()
      );
   }
}
