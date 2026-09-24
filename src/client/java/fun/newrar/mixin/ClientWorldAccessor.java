package fun.newrar.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ClientWorld#getPendingUpdateManager пакетный — нужен для sequence в блок-экшенах (обход Grim). */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor {
    @Invoker("getPendingUpdateManager")
    PendingUpdateManager rainydlc$getPendingUpdateManager();
}
