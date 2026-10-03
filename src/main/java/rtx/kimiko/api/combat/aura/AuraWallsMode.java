package rtx.kimiko.api.combat.aura;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public enum AuraWallsMode {
    NONE,
    ALL,
    DOORS,
    FT,
    RW;

    public RaycastContext.FluidHandling getFluidHandling() {
        return this == DOORS ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;
    }

    public boolean isRaycastMode() {
        return this == ALL || this == RW;
    }

    public boolean canPassThrough(BlockView world, BlockPos pos, BlockState state) {
        return switch (this) {
            case NONE -> false;
            case DOORS -> state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapdoorBlock;
            case FT -> !state.isOpaqueFullCube();
            case ALL, RW -> true;
        };
    }
}
