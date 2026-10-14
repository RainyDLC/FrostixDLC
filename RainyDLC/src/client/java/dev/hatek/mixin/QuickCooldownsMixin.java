package dev.hatek.mixin;
import dev.hatek.client.module.impl.render.interf.quick.QuickCooldowns;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;
@Mixin(ItemCooldowns.class)
public class QuickCooldownsMixin implements QuickCooldowns {
    @Unique private final Map<Identifier,Integer> quick$ticks=new HashMap<>();
    @Inject(method="addCooldown(Lnet/minecraft/resources/Identifier;I)V",at=@At("TAIL"))
    private void quick$added(Identifier id,int ticks,CallbackInfo ci) { quick$ticks.put(id,ticks); }
    @Inject(method="tick",at=@At("HEAD"))
    private void quick$tick(CallbackInfo ci) { quick$ticks.replaceAll((id,t)->t-1); quick$ticks.values().removeIf(t->t<=0); }
    @Inject(method="removeCooldown",at=@At("TAIL"))
    private void quick$removed(Identifier id,CallbackInfo ci) { quick$ticks.remove(id); }
    @Override public Map<Identifier,Integer> quick$remainingTicks() { return Map.copyOf(quick$ticks); }
}
