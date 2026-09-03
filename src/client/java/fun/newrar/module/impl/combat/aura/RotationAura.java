package fun.newrar.module.impl.combat.aura;

import net.minecraft.entity.LivingEntity;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.utils.annotation.IMinecraft;

public interface RotationAura extends IMinecraft {
    void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack);
}

