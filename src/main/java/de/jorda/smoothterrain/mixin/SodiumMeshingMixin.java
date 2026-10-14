package de.jorda.smoothterrain.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import de.jorda.smoothterrain.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emit through Sodium's own consumers, retaining its sorting and buffer lifetime. */
@Mixin(value=ChunkBuilderMeshingTask.class,remap=false)
public abstract class SodiumMeshingMixin {
    private static final String EXECUTE="execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;";

    @WrapMethod(method=EXECUTE)
    private ChunkBuildOutput smoothterrain$scope(ChunkBuildContext context,CancellationToken token,Operation<ChunkBuildOutput> original) {
        try(var scope=TerrainConfig.openBake()) {
            TerrainPlantOffsets.begin();
            try { return original.call(context,token); }
            finally { TerrainPlantOffsets.end(); }
        }
    }

    @Inject(method=EXECUTE,at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;prepare(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;)V",shift=At.Shift.AFTER))
    private void smoothterrain$mesh(ChunkBuildContext context,CancellationToken token,CallbackInfoReturnable<ChunkBuildOutput> ci,
            @Local LevelSlice world,@Local TranslucentGeometryCollector collector) {
        if(token.isCancelled()) return;
        var section=((ChunkBuilderMeshingTask)(Object)this).getRenderSection().getPosition();
        boolean cutoutLeaves=((SodiumLeafAccessor)(Object)context.cache.getBlockRenderer()).smoothterrain$cutoutLeaves();
        TerrainMeshRenderer.render(section,world,context.cache.getBlockModels(),Minecraft.getInstance().getBlockColors(),cutoutLeaves,layer->{
            var material=DefaultMaterials.forChunkLayer(layer);
            return context.buffers.get(material).asFallbackVertexConsumer(material,collector);
        });
    }

    @ModifyExpressionValue(method=EXECUTE,at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape smoothterrain$replace(RenderShape original,@Local BlockState state,@Local(ordinal=0) BlockPos.MutableBlockPos pos) {
        return TerrainConfig.bakeEnabled()&&TerrainConfig.bakeRounding()!=0&&(TerrainConfig.smooth(state)||TerrainPlantOffsets.replacesSnow(pos))?RenderShape.INVISIBLE:original;
    }

    @ModifyExpressionValue(method=EXECUTE,at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
    private boolean smoothterrain$visibility(boolean original,@Local BlockState state,@Local(ordinal=0) BlockPos.MutableBlockPos pos) {
        return (!TerrainConfig.bakeEnabled()||TerrainConfig.bakeRounding()==0||TerrainPlantOffsets.enclosed()||!TerrainConfig.smooth(state)&&!TerrainPlantOffsets.replacesSnow(pos))&&original;
    }
}
