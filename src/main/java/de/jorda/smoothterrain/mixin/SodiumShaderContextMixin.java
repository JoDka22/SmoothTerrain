package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.ShaderContexts;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris-only bridge for Sodium fallback vertices, independent of its model builder type. */
@Mixin(value=ChunkVertexConsumer.class,remap=false)
public abstract class SodiumShaderContextMixin implements ShaderContexts.Receiver {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Unique private boolean smoothterrain$active;
    @Unique private int smoothterrain$id,smoothterrain$x,smoothterrain$y,smoothterrain$z;
    @Unique private byte smoothterrain$fluid,smoothterrain$emission;
    public void smoothterrain$beginShader(int id,byte fluid,byte emission,int x,int y,int z) {
        smoothterrain$active=true;smoothterrain$id=id;smoothterrain$fluid=fluid;smoothterrain$emission=emission;
        smoothterrain$x=x;smoothterrain$y=y;smoothterrain$z=z;
    }
    @Inject(method="potentiallyEndVertex",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;fromPackedNormal(I)Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;"))
    private void smoothterrain$shaderVertices(CallbackInfoReturnable<VertexConsumer> ci) {
        if(smoothterrain$active)for(var vertex:vertices)
            ShaderContexts.vertex(vertex,smoothterrain$id,smoothterrain$fluid,smoothterrain$emission,smoothterrain$x,smoothterrain$y,smoothterrain$z);
    }
    public void smoothterrain$endShader() {
        smoothterrain$active=false;
        for(var vertex:vertices)ShaderContexts.vertex(vertex,-1,(byte)0,(byte)0,0,0,0);
    }
}
