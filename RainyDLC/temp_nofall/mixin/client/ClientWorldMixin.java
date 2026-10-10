package su.DSF.calcite.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.DSF.calcite.api.events.impl.game.EventWorldLoad;

@Mixin({ClientLevel.class})
public class ClientWorldMixin {
   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void calcite$onWorldLoad(CallbackInfo ci) {
      new EventWorldLoad().call();
   }
}
