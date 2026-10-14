package de.jorda.smoothterrain;

import java.util.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

public final class TerrainCollision {
    private static final ThreadLocal<net.minecraft.world.entity.Entity> VALIDATING_PLAYER=new ThreadLocal<>();
    /** Preserve the server's old-height collision context while querying rounded player geometry.
     * In 26.3 withPosition() also sets the placement flag; actual placement stays vanilla.
     * The scope covers iteration of the server's lazy collision iterable and is always released.
     */
    public static boolean validateMovement(net.minecraft.world.level.LevelReader world,
            net.minecraft.world.entity.Entity entity,java.util.function.BooleanSupplier original) {
        if(TerrainConfig.safeMovement||!TerrainConfig.enabled||!TerrainConfig.collisions
            ||TerrainConfig.rounding==0||!(entity instanceof net.minecraft.world.entity.player.Player)
            ||!allowedWorld.test(world))return original.getAsBoolean();
        var previous=VALIDATING_PLAYER.get();
        VALIDATING_PLAYER.set(entity);
        try {return original.getAsBoolean();}
        finally {if(previous==null)VALIDATING_PLAYER.remove();else VALIDATING_PLAYER.set(previous);}
    }
    public static boolean roundedContext(CollisionContext context) {
        // Navigation and mob movement must see the same vanilla grid. Empty
        // contexts also serve support, placement, spawning and mod queries.
        return !TerrainConfig.safeMovement&&context instanceof EntityCollisionContext entityContext
            && entityContext.getEntity() instanceof net.minecraft.world.entity.player.Player
            && (!context.isPlacement()||VALIDATING_PLAYER.get()==entityContext.getEntity());
    }
    public static java.util.function.Predicate<BlockGetter> allowedWorld=world->true;
    public static final AtomicLong REVISION=new AtomicLong();
    private static final Map<BlockGetter,Epochs> EPOCHS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Epochs {
        long serial,base;
        final Long2LongOpenHashMap sections=new Long2LongOpenHashMap();
        synchronized long at(BlockPos pos) { return sections.getOrDefault(SectionPos.asLong(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4),base); }
        synchronized void invalidate(BlockPos pos) {
            if(sections.size()>8192) { sections.clear();base=++serial; }
            long value=++serial;
            for(int x=(pos.getX()-5)>>4;x<=(pos.getX()+5)>>4;x++)
                for(int y=(pos.getY()-5)>>4;y<=(pos.getY()+5)>>4;y++)
                    for(int z=(pos.getZ()-5)>>4;z<=(pos.getZ()+5)>>4;z++) sections.put(SectionPos.asLong(x,y,z),value);
        }
    }
    private record Entry(BlockState state,long stamp,long chunks,VoxelShape shape) {}
    private static final class Cache {
        WeakReference<BlockGetter> world=new WeakReference<>(null);
        double rounding=-1;
        long filters=-1;
        Epochs epochs;
        final Long2ObjectLinkedOpenHashMap<Entry> shapes=new Long2ObjectLinkedOpenHashMap<>(1024);
    }
    private static final ThreadLocal<Cache> CACHE=ThreadLocal.withInitial(Cache::new);
    private static Epochs epochs(BlockGetter world) { synchronized(EPOCHS) { return EPOCHS.computeIfAbsent(world,ignored->new Epochs()); } }
    public static void invalidate(BlockGetter world,BlockPos pos) {
        epochs(world).invalidate(pos);REVISION.incrementAndGet();
    }
    static long localRevision(BlockGetter world,BlockPos pos) {return epochs(world).at(pos);}
    public static VoxelShape shape(BlockState state,BlockGetter world,BlockPos pos) {
        // WorldGenRegion and render/lighting readers must retain vanilla shapes. In
        // particular, never request neighbor chunks while worldgen is spawning mobs.
        if(!(world instanceof Level level)||!TerrainConfig.enabled||TerrainConfig.rounding==0
            ||!TerrainConfig.collisions||!allowedWorld.test(world)||(!TerrainConfig.smooth(state)&&!state.isAir())) return null;
        long chunks=0;
        for(int x=(pos.getX()-5)>>4;x<=(pos.getX()+5)>>4;x++) for(int z=(pos.getZ()-5)>>4;z<=(pos.getZ()+5)>>4;z++) {
            var chunk=level.getChunk(x,z,ChunkStatus.FULL,false);
            if(chunk==null) return null;
            chunks=chunks*31+System.identityHashCode(chunk);
        }
        return cachedShape(state,world,pos,chunks);
    }
    // Package-private entry for the runtime checks, which supply a tiny fake loaded world.
    static VoxelShape cachedShape(BlockState state,BlockGetter world,BlockPos pos,long chunks) {
        try(var scope=TerrainConfig.openBake()) { return cachedShapeScoped(state,world,pos,chunks); }
    }
    private static VoxelShape cachedShapeScoped(BlockState state,BlockGetter world,BlockPos pos,long chunks) {
        Cache cache=CACHE.get();
        if(cache.world.get()!=world||cache.rounding!=TerrainConfig.bakeRounding()||cache.filters!=TerrainConfig.bakeFilterRevision()) {
            if(cache.world.get()!=world) cache.epochs=epochs(world);
            cache.shapes.clear();cache.world=new WeakReference<>(world);cache.rounding=TerrainConfig.bakeRounding();cache.filters=TerrainConfig.bakeFilterRevision();
        }
        long key=pos.asLong(),stamp=cache.epochs.at(pos);
        Entry entry=cache.shapes.getAndMoveToLast(key);
        if(entry!=null&&entry.state==state&&entry.stamp==stamp&&entry.chunks==chunks) return entry.shape;
        VoxelShape result=build(world,pos);
        cache.shapes.putAndMoveToLast(key,new Entry(state,stamp,chunks,result));
        if(cache.shapes.size()>1024) cache.shapes.removeFirst();
        return result;
    }
    private static VoxelShape build(BlockGetter world,BlockPos pos) {
        BlockPos.MutableBlockPos sample=new BlockPos.MutableBlockPos();
        SurfaceMesh.Field field=(x,y,z)->TerrainConfig.volume(world.getBlockState(sample.set(x,y,z)));
        int px=pos.getX(),py=pos.getY(),pz=pos.getZ();
        boolean all=true,any=false;
        for(int z=-1;z<=1;z++) for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
            boolean solid=field.solid(px+x,py+y,pz+z);all&=solid;any|=solid;
        }
        if(all) return Shapes.block();if(!any) return Shapes.empty();
        List<SurfaceMesh.Quad> mesh=new ArrayList<>();
        // Only the query column and its neighboring columns can bend into the
        // query cube. Keep the full vertical interval for entering/exiting rays.
        new SurfaceMesh(field,TerrainConfig.bakeRounding(),(x,y,z)->TerrainConfig.meshAnchor(world.getBlockState(sample.set(x,y,z)))).generateBox(px-1,py-2,pz-1,3,5,3,source->true,q->{
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=minX,maxZ=maxX;
            for(int i=0;i<4;i++) { var p=q.point(i).position();minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z()); }
            if(maxX>px&&minX<px+1&&maxZ>pz&&minZ<pz+1) mesh.add(q);
        });
        // One compact discrete grid replaces dozens of expensive boolean shape unions.
        int resolution=8,height=32;
        BitSetDiscreteVoxelShape grid=new BitSetDiscreteVoxelShape(resolution,height,resolution);
        var columns=new MeshRay.Columns(mesh,px,pz,resolution);
        boolean below=field.solid(px,py-2,pz);
        for(int z=0;z<resolution;z++) for(int x=0;x<resolution;x++) {
            for(var interval:columns.intervals(x,z,py,py+1,below)) {
                int lo=Math.clamp((int)Math.round((interval.low()-py)*height),0,height);
                int hi=Math.clamp((int)Math.round((interval.high()-py)*height),0,height);
                for(int y=lo;y<hi;y++) grid.fill(x,y,z);
            }
        }
        return grid.isEmpty()?Shapes.empty():new CubeVoxelShape(grid);
    }
}
