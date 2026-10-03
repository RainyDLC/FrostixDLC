package rtx.kimiko.api.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import rtx.kimiko.utils.math.rotation.RotationApplyMode;
import rtx.kimiko.utils.math.rotation.RotationManager;

public abstract class RotationMode {
    private final String name;

    public RotationMode(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void update() {
    }

    public void enabled() {
    }

    public void targetNull() {
    }

    public void attack() {
    }

    public boolean canAttack() {
        return true;
    }

    public abstract void rotate(RotationManager rotationManager, float attackRange, boolean raycastMode, boolean rayTrace, RotationApplyMode applyMode, LivingEntity target);
}
