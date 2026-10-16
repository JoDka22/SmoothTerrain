package de.jorda.smoothterrain;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.lang.ref.WeakReference;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/** Only retries a rejected BlockItem entity-clearance check in rounded mode. */
public final class TerrainPlacement {
    private TerrainPlacement() {}
    private static final class PreviewCache {
        WeakReference<Level> world=new WeakReference<>(null);
        BlockPos target;BlockState state;double rounding;long filters,revision;
        final Long2ObjectOpenHashMap<VoxelShape> shapes=new Long2ObjectOpenHashMap<>();
        void prepare(Level next,BlockState block,BlockPos pos) {
            long current=TerrainCollision.REVISION.get(),filter=TerrainConfig.bakeFilterRevision();double amount=TerrainConfig.bakeRounding();
            if(world.get()!=next||state!=block||!pos.equals(target)||rounding!=amount||filters!=filter||revision!=current) {
                shapes.clear();world=new WeakReference<>(next);state=block;target=pos.immutable();rounding=amount;filters=filter;revision=current;
            }
        }
    }
    // At most 32 immutable voxel shapes; no preview/world closure is retained.
    // Separate from the live collision cache, invalidated on edits and config/chunk changes.
    private static final ThreadLocal<PreviewCache> CACHE=ThreadLocal.withInitial(PreviewCache::new);
    public static boolean roundedClearance(Level world,BlockState state,BlockPos target,CollisionContext context) {
        if(TerrainConfig.safeMovement||!TerrainConfig.enabled||!TerrainConfig.collisions||TerrainConfig.rounding==0
            ||!TerrainCollision.allowedWorld.test(world)||!(context instanceof EntityCollisionContext entities)
            ||!(entities.getEntity() instanceof Player player))return false;
        try(var scope=TerrainConfig.openBake()) {
            if(!TerrainConfig.smooth(state))return false;
            var original=state.getCollisionShape(world,target,context).move(target);
            if(original.isEmpty())return false;
            // Mobs and other players keep the exact original placement rules.
            // Do not exclude passengers/vehicles as isUnobstructed(player,shape) would.
            for(Entity other:world.getEntities((Entity)null,original.bounds()))
                if(other!=player&&!other.isRemoved()&&other.blocksBuilding
                    &&Shapes.joinIsNotEmpty(original,Shapes.create(other.getBoundingBox()),BooleanOp.AND))return false;
            var body=player.getBoundingBox();
            if(!Shapes.joinIsNotEmpty(original,Shapes.create(body),BooleanOp.AND))return false;
            int minX=Mth.floor(body.minX),maxX=Mth.floor(body.maxX-1e-7);
            int minY=Mth.floor(body.minY),maxY=Mth.floor(body.maxY-1e-7);
            int minZ=Mth.floor(body.minZ),maxZ=Mth.floor(body.maxZ-1e-7);
            // Match collision's loaded-neighbour policy; never load chunks for a preview.
            for(int x=(minX-5)>>4;x<=(maxX+5)>>4;x++)for(int z=(minZ-5)>>4;z<=(maxZ+5)>>4;z++)
                if(world.getChunk(x,z,ChunkStatus.FULL,false)==null)return false;
            BlockGetter preview=new BlockGetter() {
                public BlockState getBlockState(BlockPos pos){return pos.equals(target)?state:world.getBlockState(pos);}
                public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
                public BlockEntity getBlockEntity(BlockPos pos){return pos.equals(target)?null:world.getBlockEntity(pos);}
                public int getHeight(){return world.getHeight();}
                public int getMinY(){return world.getMinY();}
            };
            var cache=CACHE.get();cache.prepare(world,state,target);
            var bodyShape=Shapes.create(body);var sample=new BlockPos.MutableBlockPos();
            // Placement changes adjoining meshes as well as the target itself.
            // Check the full body against that future geometry without moving it,
            // changing ground flags or temporarily editing the live world.
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
                sample.set(x,y,z);
                var block=preview.getBlockState(sample);VoxelShape shape;
                if(TerrainConfig.smooth(block)||block.isAir()) {
                    long key=sample.asLong();shape=cache.shapes.get(key);
                    if(shape==null) {
                        shape=TerrainCollision.placementShape(preview,sample,context);
                        if(cache.shapes.size()>=32)cache.shapes.clear();
                        cache.shapes.put(key,shape);
                    }
                } else shape=TerrainCollision.placementShape(preview,sample,context);
                if(Shapes.joinIsNotEmpty(shape.move(sample),bodyShape,BooleanOp.AND))return false;
            }
            return true;
        }
    }
}
