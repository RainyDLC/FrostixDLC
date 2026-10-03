package rtx.kimiko.utils.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

public record PlayerStateSnapshot(
        float attackCooldown,
        float velocityY,
        float fallDistance,
        boolean onGround,
        boolean sprinting,
        boolean inWater,
        float targetHealth,
        int hurtTime,
        boolean jumpInput,
        boolean sneakInput
) {
    public static final PlayerStateSnapshot EMPTY = new PlayerStateSnapshot(1.0f, 0.0f, 0.0f, true, false, false, 0.0f, 0, true, false);

    public static PlayerStateSnapshot capture(ClientPlayerEntity player, LivingEntity target) {
        if (player == null) {
            return EMPTY;
        }
        boolean jump = player.input == null || player.input.playerInput == null || player.input.playerInput.jump();
        boolean sneak = player.input != null && player.input.playerInput != null && player.input.playerInput.sneak();
        return new PlayerStateSnapshot(
                player.getAttackCooldownProgress(0.0f),
                (float) player.getVelocity().y,
                (float) player.fallDistance,
                player.isOnGround(),
                player.isSprinting(),
                player.isSubmergedInWater() || player.isTouchingWater(),
                target == null ? 0.0f : target.getHealth(),
                target == null ? 0 : target.hurtTime,
                jump,
                sneak
        );
    }
}
