package de.jorda.smoothterrain.mixin;

import java.util.Map;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import de.jorda.smoothterrain.TerrainConfig;
import de.jorda.smoothterrain.TerrainMeshRenderer;
import de.jorda.smoothterrain.TerrainPlantOffsets;
import de.jorda.smoothterrain.RenderResources;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Unique private static final ThreadLocal<RenderResources> smoothterrain$resources=new ThreadLocal<>();
    @Shadow @Final private BlockStateModelSet blockModelSet;
    @Shadow @Final private BlockColors blockColors;
    @Shadow @Final private boolean cutoutLeaves;
    @Shadow protected abstract BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer,BufferBuilder> layers,SectionBufferBuilderPack pack,ChunkSectionLayer layer);

    @WrapMethod(method="compile")
    private SectionCompiler.Results smoothterrain$scope(SectionPos section,RenderSectionRegion region,VertexSorting sorting,
            SectionBufferBuilderPack pack,Operation<SectionCompiler.Results> original) {
        RenderResources previous=smoothterrain$resources.get();
        try(var scope=TerrainConfig.openBake();var resources=new RenderResources()) {
            smoothterrain$resources.set(resources);
            TerrainPlantOffsets.begin();
            SectionCompiler.Results result;
            try {
                result=original.call(section,region,sorting,pack);
            } finally {
                TerrainPlantOffsets.end();
                BlockModelLighter.clearCache();
            }
            resources.commit(); // Mesh ownership now belongs to Minecraft's upload task.
            return result;
        } finally {
            if(previous==null) smoothterrain$resources.remove();else smoothterrain$resources.set(previous);
        }
    }

    @WrapOperation(method="compile",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/BufferBuilder;build()Lcom/mojang/blaze3d/vertex/MeshData;"))
    private MeshData smoothterrain$ownMesh(BufferBuilder builder,Operation<MeshData> original) {
        MeshData mesh=original.call(builder);
        var resources=smoothterrain$resources.get();
        return resources==null?mesh:resources.track(mesh);
    }

    @Inject(method="compile",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private void smoothterrain$mesh(SectionPos section,RenderSectionRegion region,VertexSorting sorting,
            SectionBufferBuilderPack pack,CallbackInfoReturnable<SectionCompiler.Results> ci,
            @Local Map<ChunkSectionLayer,BufferBuilder> layers) {
        TerrainMeshRenderer.render(section,region,blockModelSet,blockColors,cutoutLeaves,layer->getOrBeginLayer(layers,pack,layer));
    }

    // Leave the actual tessellator call available for Fabric's renderer redirect.
    @ModifyExpressionValue(method="compile",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape smoothterrain$replaceCubes(RenderShape original,@Local BlockState state,@Local(ordinal=2) BlockPos pos) {
        return TerrainConfig.bakeEnabled()&&TerrainConfig.bakeRounding()!=0&&(TerrainConfig.smooth(state)||TerrainPlantOffsets.replacesSnow(pos))?RenderShape.INVISIBLE:original;
    }

    @ModifyExpressionValue(method="compile",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
    private boolean smoothterrain$visibility(boolean original,@Local BlockState state,@Local(ordinal=2) BlockPos pos) {
        return (!TerrainConfig.bakeEnabled()||TerrainConfig.bakeRounding()==0||TerrainPlantOffsets.enclosed()||!TerrainConfig.smooth(state)&&!TerrainPlantOffsets.replacesSnow(pos))&&original;
    }
}
