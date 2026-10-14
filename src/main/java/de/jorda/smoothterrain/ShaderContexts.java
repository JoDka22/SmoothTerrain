package de.jorda.smoothterrain;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

/** Optional Iris context on borrowed consumers; no hard dependency or native storage. */
public final class ShaderContexts {
    public interface Receiver {
        void smoothterrain$beginShader(int id,byte fluid,byte emission,int x,int y,int z);
        void smoothterrain$endShader();
    }
    private record Bindings(Class<?> consumer,MethodHandle ids,MethodHandle begin,MethodHandle end,Class<?> vertex,MethodHandle vertexData) {}
    private static final Bindings IRIS=bindings();
    private static final ShaderContexts NONE=new ShaderContexts(null,null,BlockPos.ZERO);
    private final Bindings api;
    private final Object2IntMap<BlockState> ids;
    private final BlockPos origin;
    private ShaderContexts(Bindings api,Object2IntMap<BlockState> ids,BlockPos origin) {
        this.api=api;this.ids=ids;this.origin=origin;
    }
    private static Bindings bindings() {
        if(!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            var lookup=MethodHandles.publicLookup();
            var consumer=Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
            var settings=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
            var ids=lookup.findVirtual(settings,"getBlockStateIds",MethodType.methodType(Object2IntMap.class))
                .bindTo(settings.getField("INSTANCE").get(null));
            var begin=lookup.findVirtual(consumer,"beginBlock",MethodType.methodType(void.class,int.class,byte.class,byte.class,int.class,int.class,int.class))
                .asType(MethodType.methodType(void.class,Object.class,int.class,byte.class,byte.class,int.class,int.class,int.class));
            var end=lookup.findVirtual(consumer,"endBlock",MethodType.methodType(void.class))
                .asType(MethodType.methodType(void.class,Object.class));
            var vertex=Class.forName("net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension");
            var data=lookup.findVirtual(vertex,"iris$setData",MethodType.methodType(void.class,byte.class,byte.class,int.class,int.class,int.class,int.class))
                .asType(MethodType.methodType(void.class,Object.class,byte.class,byte.class,int.class,int.class,int.class,int.class));
            return new Bindings(consumer,ids,begin,end,vertex,data);
        } catch(ReflectiveOperationException|LinkageError unsupported) {
            LoggerFactory.getLogger("smoothterrain").warn("Iris block-context integration unavailable for this version",unsupported);
            return null;
        }
    }
    @SuppressWarnings("unchecked")
    public static ShaderContexts capture(BlockPos origin) {
        if(IRIS==null) return NONE;
        try {
            var ids=(Object2IntMap<BlockState>)IRIS.ids.invokeExact();
            return ids==null?NONE:new ShaderContexts(IRIS,ids,origin);
        } catch(Throwable failure) {throw failure(failure);}
    }
    public void begin(VertexConsumer buffer,BlockState state,SurfaceMesh.C source,boolean fluid) {
        if(api==null) return;
        if(buffer instanceof Receiver receiver) {
            receiver.smoothterrain$beginShader(ids.getOrDefault(state,-1),(byte)(fluid?1:0),(byte)state.getLightEmission(),
                source.x()-origin.getX(),source.y()-origin.getY(),source.z()-origin.getZ());
            return;
        }
        if(!api.consumer.isInstance(buffer))return;
        try {
            api.begin.invokeExact((Object)buffer,ids.getOrDefault(state,-1),(byte)(fluid?1:0),(byte)state.getLightEmission(),
                source.x()-origin.getX(),source.y()-origin.getY(),source.z()-origin.getZ());
        } catch(Throwable failure) {throw failure(failure);}
    }
    public void end(VertexConsumer buffer) {
        if(api==null)return;
        if(buffer instanceof Receiver receiver) {receiver.smoothterrain$endShader();return;}
        if(!api.consumer.isInstance(buffer))return;
        try {api.end.invokeExact((Object)buffer);}
        catch(Throwable failure) {throw failure(failure);}
    }
    public static void vertex(Object vertex,int id,byte fluid,byte emission,int x,int y,int z) {
        if(IRIS==null||!IRIS.vertex.isInstance(vertex))return;
        try {IRIS.vertexData.invokeExact(vertex,emission,fluid,id,x,y,z);}
        catch(Throwable failure) {throw failure(failure);}
    }
    private static RuntimeException failure(Throwable failure) {
        if(failure instanceof Error error) throw error;
        return failure instanceof RuntimeException runtime?runtime:new IllegalStateException("Iris block-context operation failed",failure);
    }
}
