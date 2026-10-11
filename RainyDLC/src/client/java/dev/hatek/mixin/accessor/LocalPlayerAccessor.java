package dev.hatek.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    /** Состояние спринта, которое сервер видел последним (vanilla-трекинг). */
    @Accessor("wasSprinting")
    boolean hatek$wasSprinting();

    /**
     * Vanilla-синхронизация спринта: шлёт START/STOP только если isSprinting()
     * расходится с wasSprinting, и сразу обновляет wasSprinting — без дублей.
     */
    @Invoker("sendIsSprintingIfNeeded")
    void hatek$sendIsSprintingIfNeeded();
}
