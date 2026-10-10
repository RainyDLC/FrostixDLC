package dev.hatek.mixin;

import dev.hatek.client.module.impl.player.NoFall;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets NoFall force a jump on the forged input (Grim mode: jump right
 * after the server teleport + velocity sequence completes while on ground).
 * The full input jump is load-bearing for the bypass; a weaker direct hop
 * was tried and broke it.
 */
@Mixin(KeyboardInput.class)
public class NoFallKeyboardMixin {
    @Redirect(method = "tick",
            at = @At(value = "NEW",
                    target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input hatek$noFallJump(boolean forward, boolean backward, boolean left, boolean right,
                                   boolean jump, boolean shift, boolean sprint) {
        if (NoFall.consumeJump()) {
            jump = true;
        }
        return new Input(forward, backward, left, right, jump, shift, sprint);
    }
}
