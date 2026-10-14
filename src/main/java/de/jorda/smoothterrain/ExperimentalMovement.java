package de.jorda.smoothterrain;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/** Conservative ground following after a grounded player's ordinary movement. */
public final class ExperimentalMovement {
    private static final double MAX_DROP=.375;
    public static boolean active(Player player) {
        return TerrainConfig.enabled&&TerrainConfig.collisions&&!TerrainConfig.safeMovement&&TerrainConfig.rounding>0
            &&TerrainCollision.allowedWorld.test(player.level())&&!player.getAbilities().flying
            &&!player.isPassenger()&&!player.isShiftKeyDown()&&!player.isInWater()&&!player.isInLava()
            &&!player.onClimbable()&&!player.isFallFlying();
    }
    public static void settle(Player player,double requestedY,double previousX,double previousZ) {
        if(!active(player)||requestedY>0||player.onGround()
            ||Math.abs(player.getX()-previousX)+Math.abs(player.getZ()-previousZ)<1e-8)return;
        var box=player.getBoundingBox();
        // Do not trigger synchronous chunk loading or adjust vanilla fallback
        // movement when the rounded collision halo is unavailable.
        for(int x=((int)Math.floor(box.minX)-5)>>4;x<=((int)Math.floor(box.maxX)+5)>>4;x++)
            for(int z=((int)Math.floor(box.minZ)-5)>>4;z<=((int)Math.floor(box.maxZ)+5)>>4;z++)
                if(player.level().getChunk(x,z,ChunkStatus.FULL,false)==null)return;
        boolean terrain=false;
        var sample=new BlockPos.MutableBlockPos();
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX);x++)
            for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ);z++)
                // Rounded surfaces can extend into the air cell above their
                // source block. Include that source without enlarging the actual
                // downward collision probe or following a full cliff drop.
                for(int y=(int)Math.floor(box.minY-MAX_DROP)-1;y<=(int)Math.floor(box.minY);y++)
                    if(TerrainConfig.smooth(player.level().getBlockState(sample.set(x,y,z))))terrain=true;
        if(!terrain)return;
        double drop=Entity.collideBoundingBox(player,new Vec3(0,-MAX_DROP,0),box,player.level(),List.of()).y;
        // A nearby real floor must stop the probe. A full drop is an edge/cliff,
        // so retain vanilla falling; never raise a player or cancel an upward jump.
        if(drop<=-MAX_DROP+1e-7||drop>0)return;
        player.setPos(player.getX(),player.getY()+drop,player.getZ());
        player.setOnGround(true);player.verticalCollision=true;player.verticalCollisionBelow=true;
        player.resetFallDistance();
        var velocity=player.getDeltaMovement();
        if(velocity.y<0)player.setDeltaMovement(velocity.x,0,velocity.z);
    }
}
