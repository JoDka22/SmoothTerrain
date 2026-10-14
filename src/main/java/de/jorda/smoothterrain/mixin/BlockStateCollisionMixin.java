package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateCollisionMixin {
    @Inject(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("HEAD"),cancellable=true)
    private void smoothterrain$collision(BlockGetter world,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> ci) {
        if(!TerrainCollision.roundedContext(context)) return;
        VoxelShape shape=TerrainCollision.shape((BlockState)(Object)this,world,pos);
        if(shape!=null) ci.setReturnValue(shape);
    }
}
