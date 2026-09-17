package fun.newrar.mixin;

import fun.newrar.interfaces.TargetScanRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements TargetScanRenderState {
    @Unique
    private boolean nightix$targetScanTarget;

    @Override
    public boolean nightix$isTargetScanTarget() {
        return this.nightix$targetScanTarget;
    }

    @Override
    public void nightix$setTargetScanTarget(boolean target) {
        this.nightix$targetScanTarget = target;
    }
}
