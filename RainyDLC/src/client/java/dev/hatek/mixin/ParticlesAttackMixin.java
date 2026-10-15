package dev.hatek.mixin;

import dev.hatek.client.module.impl.render.HitParticles;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Спавн частиц удара. TAIL MultiPlayerGameMode.attack — срабатывает только если
 * атака не была отменена (например, хуком MultiPlayerGameModeMixin на HEAD).
 * Дескриптор метода взят из существующего MultiPlayerGameModeMixin (origin/main).
 * Требует регистрации в hatek_client.client.mixins.json (см. register.diff).
 */
@Mixin(MultiPlayerGameMode.class)
public class ParticlesAttackMixin {
    @Inject(
            method = "attack(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/entity/Entity;)V",
            at = @At("TAIL")
    )
    private void hatek$hitParticles(Player player, Entity target, CallbackInfo ci) {
        HitParticles.onAttack(target);
    }
}
