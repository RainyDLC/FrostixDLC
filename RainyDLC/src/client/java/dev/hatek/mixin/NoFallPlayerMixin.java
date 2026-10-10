package dev.hatek.mixin;

import dev.hatek.client.module.impl.player.NoFall;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks {@code LocalPlayer.sendPosition()} for the NoFall module.
 *
 * <p>At HEAD the module decides what to do: let vanilla run ({@code null}),
 * cancel silently (empty list, used while the Grim desync is waiting for the
 * server teleport), or send forged replacement packets (Grim landing spoof).
 * The "last sent" fields are updated from the replacement packets exactly like
 * vanilla would, so the next delta is computed correctly.</p>
 *
 * <p>The {@code onGround()} redirect applies the Vanilla-mode spoof: when armed,
 * outgoing movement packets report onGround=true.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class NoFallPlayerMixin {
    @Shadow
    private double xLast;
    @Shadow
    private double yLast;
    @Shadow
    private double zLast;
    @Shadow
    private int positionReminder;
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Shadow
    private boolean lastOnGround;
    @Shadow
    private boolean lastHorizontalCollision;

    @Shadow
    protected abstract void sendIsSprintingIfNeeded();

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void hatek$onSendPosition(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        List<Packet<?>> replacement = NoFall.replacementFor(self, this.yRotLast, this.xRotLast);
        if (replacement == null) {
            return;
        }
        this.sendIsSprintingIfNeeded();
        for (Packet<?> packet : replacement) {
            self.connection.send(packet);
            if (packet instanceof ServerboundMovePlayerPacket move) {
                if (move.hasPosition()) {
                    this.xLast = move.getX(this.xLast);
                    this.yLast = move.getY(this.yLast);
                    this.zLast = move.getZ(this.zLast);
                    this.positionReminder = 0;
                }
                if (move.hasRotation()) {
                    this.yRotLast = move.getYRot(this.yRotLast);
                    this.xRotLast = move.getXRot(this.xRotLast);
                }
                this.lastOnGround = move.isOnGround();
                this.lastHorizontalCollision = move.horizontalCollision();
            }
        }
        ci.cancel();
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void hatek$onSendPositionTail(CallbackInfo ci) {
        NoFall.disarmSpoof();
    }

    @Redirect(method = "sendPosition",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
    private boolean hatek$spoofOnGround(LocalPlayer instance) {
        return NoFall.spoofOnGround() || instance.onGround();
    }
}
