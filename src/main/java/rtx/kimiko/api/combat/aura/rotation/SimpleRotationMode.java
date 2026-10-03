package rtx.kimiko.api.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import rtx.kimiko.utils.combat.MotionUtils;
import rtx.kimiko.utils.math.rotation.Rotation;
import rtx.kimiko.utils.math.rotation.RotationApplyMode;
import rtx.kimiko.utils.math.rotation.RotationManager;
import rtx.kimiko.utils.math.rotation.RotationPriority;
import rtx.kimiko.utils.math.rotation.RotationUtil;

public class SimpleRotationMode extends RotationMode {
    public SimpleRotationMode() {
        super("Simple");
    }

    @Override
    public void rotate(RotationManager rotationManager, float attackRange, boolean raycastMode, boolean rayTrace, RotationApplyMode applyMode, LivingEntity target) {
        Rotation rotation = RotationUtil.getRotationToEntity(target, MotionUtils.getTargetPoint(target, raycastMode));
        rotationManager.applyRotation(rotation, applyMode, 180.0f, 180.0f, 180.0f, RotationPriority.TO_TARGET);
    }
}
