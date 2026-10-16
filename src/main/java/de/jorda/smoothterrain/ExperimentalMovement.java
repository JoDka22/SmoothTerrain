package de.jorda.smoothterrain;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Conservative ground following as part of vanilla's resolved movement. */
public final class ExperimentalMovement {
    private static final double MAX_DROP=.375;
    public static boolean active(Player player) {
        return TerrainConfig.enabled&&TerrainConfig.collisions&&!TerrainConfig.safeMovement&&TerrainConfig.rounding>0
            &&TerrainCollision.allowedWorld.test(player.level())&&!player.noPhysics&&!player.getAbilities().flying
            &&!player.isPassenger()&&!player.isShiftKeyDown()&&!player.isInWater()&&!player.isInLava()
            &&!player.onClimbable()&&!player.isFallFlying();
    }
    public static boolean nearTerrain(Player player,AABB box) {
        if(!loadedHalo(player,box))return false;
        var pos=new BlockPos.MutableBlockPos();
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX);x++)
            for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ);z++)
                for(int y=(int)Math.floor(box.minY)-1;y<=(int)Math.floor(box.minY+player.maxUpStep());y++)
                    if(TerrainConfig.smooth(player.level().getBlockState(pos.set(x,y,z))))return true;
        return false;
    }
    private static boolean loadedHalo(Player player,AABB box) {
        for(int x=((int)Math.floor(box.minX)-5)>>4;x<=((int)Math.floor(box.maxX)+5)>>4;x++)
            for(int z=((int)Math.floor(box.minZ)-5)>>4;z<=((int)Math.floor(box.maxZ)+5)>>4;z++)
                if(player.level().getChunk(x,z,ChunkStatus.FULL,false)==null)return false;
        return true;
    }
    /** Select from vanilla's legal heights and use its exact swept solver.
     * Fine voxel stairs otherwise accept the first partial horizontal advance,
     * even when the next small legal step preserves the complete input.
     */
    public static float[] bestStep(float[] candidates,Vec3 requested,java.util.function.DoubleFunction<Vec3> solve) {
        if(candidates.length<2)return candidates;
        double best=-1;float selected=candidates[0];
        for(float height:candidates) {
            double progress=solve.apply(height).horizontalDistanceSqr();
            if(progress>best) {best=progress;selected=height;}
            // Ascending candidates preserve the lowest equally good height.
            if(progress>=requested.horizontalDistanceSqr())break;
        }
        return new float[]{selected};
    }
    public static Vec3 resolveGroundContact(Player player,Vec3 requested,Vec3 resolved) {
        // Run before vanilla updates position, fall damage, support and block
        // reactions; those must all observe the same resolved displacement.
        if(!player.onGround()||!active(player)||requested.y>=0||resolved.y!=requested.y
            ||Math.abs(resolved.x)+Math.abs(resolved.z)<1e-8)return resolved;
        var box=player.getBoundingBox().move(resolved);
        // Do not trigger synchronous chunk loading or adjust vanilla fallback
        // movement when the rounded collision halo is unavailable.
        if(!loadedHalo(player,box))return resolved;
        boolean terrain=false;
        var sample=new BlockPos.MutableBlockPos();
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX);x++)
            for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ);z++)
                // Rounded surfaces can extend into the air cell above their
                // source block. Include that source without enlarging the actual
                // downward collision probe or following a full cliff drop.
                for(int y=(int)Math.floor(box.minY-MAX_DROP)-1;y<=(int)Math.floor(box.minY);y++)
                    if(TerrainConfig.smooth(player.level().getBlockState(sample.set(x,y,z))))terrain=true;
        if(!terrain)return resolved;
        double drop=Entity.collideBoundingBox(player,new Vec3(0,-MAX_DROP,0),box,player.level(),List.of()).y;
        // A nearby real floor must stop the probe. A full drop is an edge/cliff,
        // so retain vanilla falling; never raise a player or cancel an upward jump.
        if(drop<=-MAX_DROP+1e-7||drop>0)return resolved;
        return new Vec3(resolved.x,resolved.y+drop,resolved.z);
    }
}
