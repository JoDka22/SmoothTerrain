package de.jorda.smoothterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.jorda.smoothterrain.TerrainCollision;
import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;

/** Vanilla's escape test scans a whole block column. On slopes it sees higher
 * terrain outside the player's body and pushes the player back every tick.
 */
@Mixin(LocalPlayer.class)
public abstract class RoundedEscapeMixin {
    @WrapMethod(method="suffocatesAt")
    private boolean smoothterrain$bodyCollision(BlockPos pos,Operation<Boolean> original) {
        var player=(LocalPlayer)(Object)this;
        if(TerrainConfig.safeMovement||!TerrainConfig.enabled||!TerrainConfig.collisions
            ||TerrainConfig.rounding==0||!TerrainCollision.allowedWorld.test(player.level()))return original.call(pos);
        var body=player.getBoundingBox();
        double minX=Math.max(body.minX,pos.getX()),maxX=Math.min(body.maxX,pos.getX()+1);
        double minZ=Math.max(body.minZ,pos.getZ()),maxZ=Math.min(body.maxZ,pos.getZ()+1);
        // Neighbor columns queried to choose a real escape direction still use
        // vanilla's whole-column test; only the occupied column is narrowed.
        if(maxX<=minX||maxZ<=minZ)return original.call(pos);
        return player.level().collidesWithSuffocatingBlock(player,
            new AABB(minX,body.minY,minZ,maxX,body.maxY,maxZ).deflate(1e-7));
    }
}
