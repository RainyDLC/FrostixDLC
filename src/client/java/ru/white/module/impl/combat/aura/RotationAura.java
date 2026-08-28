package ru.white.module.impl.combat.aura;

import net.minecraft.entity.LivingEntity;
import ru.white.module.impl.combat.AttackAura;
import ru.white.utils.annotation.IMinecraft;

public interface RotationAura extends IMinecraft {
    void onRotation(AttackAura aura, LivingEntity target, float[] ranges, boolean canAttack);
}
