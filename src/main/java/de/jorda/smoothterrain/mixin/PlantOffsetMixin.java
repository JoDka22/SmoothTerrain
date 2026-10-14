package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainPlantOffsets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlantOffsetMixin {
    @Inject(method="getOffset",at=@At("RETURN"),cancellable=true)
    private void smoothterrain$plantOffset(BlockPos pos,CallbackInfoReturnable<Vec3> ci) {
        double dy=TerrainPlantOffsets.offset(pos);
        if(dy!=0) ci.setReturnValue(ci.getReturnValue().add(0,dy,0));
    }
}
