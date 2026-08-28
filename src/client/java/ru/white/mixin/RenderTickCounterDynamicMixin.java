package ru.white.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.white.utils.player.ITimerSpeed;

@Mixin(targets = "net.minecraft.client.render.RenderTickCounter$Dynamic")
public class RenderTickCounterDynamicMixin implements ITimerSpeed {
    @Unique
    private float speed = 1.0F;

    @Shadow private float dynamicDeltaTicks;

    @Override
    public float getSpeed() {
        return this.speed;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    @Inject(
            method = "beginRenderTick(J)I",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;lastTimeMillis:J",
                    shift = At.Shift.AFTER
            )
    )
    private void applySpeedToDelta(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        this.dynamicDeltaTicks *= this.speed;
    }
}
