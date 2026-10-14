package de.jorda.smoothterrain.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.jorda.smoothterrain.TerrainCollision;
import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelChangeMixin {
    @WrapMethod(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean smoothterrain$invalidate(BlockPos pos,BlockState state,int flags,int depth,Operation<Boolean> original) {
        Level world=(Level)(Object)this;
        BlockState previous=world.getBlockState(pos);
        boolean before=TerrainConfig.volume(previous),fixed=TerrainConfig.meshAnchor(previous);
        boolean changed=original.call(pos,state,flags,depth);
        if(changed&&(before!=TerrainConfig.volume(state)||fixed!=TerrainConfig.meshAnchor(state))) TerrainCollision.invalidate(world,pos);
        return changed;
    }
}
