package de.jorda.smoothterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import de.jorda.smoothterrain.TerrainPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementMixin {
    @WrapOperation(method="canPlace",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"))
    private boolean smoothterrain$placement(Level world,BlockState state,BlockPos pos,CollisionContext context,Operation<Boolean> original) {
        return original.call(world,state,pos,context)||TerrainPlacement.roundedClearance(world,state,pos,context);
    }
}
