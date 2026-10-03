package rtx.kimiko.api.modules.impl.Utils.neuro;

import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of the player/target state fed into the aim network,
 * matching the 27-feature layout the model was trained on.
 */
public record AimStateSnapshot(
        float attackCooldown,
        float velocityY,
        float fallDistance,
        boolean onGround,
        boolean sprinting,
        boolean inWater,
        float targetHealth,
        int hurtTime,
        boolean jumpInput,
        boolean sneakInput) {

    public static final AimStateSnapshot EMPTY =
            new AimStateSnapshot(1.0f, 0.0f, 0.0f, true, false, false, 0.0f, 0, true, false);

    public static AimStateSnapshot capture(@Nullable ClientPlayerEntity player, @Nullable LivingEntity target) {
        if (player == null) {
            return EMPTY;
        }
        return new AimStateSnapshot(
                player.getAttackCooldownProgress(0.0f),
                (float) player.getVelocity().y,
                (float) player.fallDistance,
                player.isOnGround(),
                player.isSprinting(),
                player.isTouchingWater() || player.isSubmergedInWater(),
                target == null ? 0.0f : target.getHealth(),
                target == null ? 0 : target.hurtTime,
                readJump(player),
                readSneak(player));
    }

    private static boolean readJump(ClientPlayerEntity player) {
        Input input = player.input;
        PlayerInput keys = input == null ? null : input.playerInput;
        return keys == null || keys.jump();
    }

    private static boolean readSneak(ClientPlayerEntity player) {
        Input input = player.input;
        PlayerInput keys = input == null ? null : input.playerInput;
        return keys != null && keys.sneak();
    }

    public static float clamp01(float value) {
        return MathHelper.clamp(value, 0.0f, 1.0f);
    }
}
