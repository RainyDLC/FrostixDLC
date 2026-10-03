package rtx.kimiko.utils.combat;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

public final class CriticalHitChecker {
    private CriticalHitChecker() {
    }

    public static boolean canCrit(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player.getAbilities().flying || player.hasVehicle()) {
            return false;
        }
        if (player.hasStatusEffect(StatusEffects.BLINDNESS) || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            return false;
        }
        return !player.isClimbing() && !player.isTouchingWater();
    }

    public static boolean isCritical(PlayerEntity player) {
        if (player == null || !canCrit(player) || player.isOnGround()) {
            return false;
        }
        return player.getVelocity().y < 0.0 || player.fallDistance > 0.0f;
    }
}
