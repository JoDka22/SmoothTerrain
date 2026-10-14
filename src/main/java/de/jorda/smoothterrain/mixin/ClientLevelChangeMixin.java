package de.jorda.smoothterrain.mixin;
import de.jorda.smoothterrain.TerrainCollision;
import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelChangeMixin {
    @Shadow public abstract void setSectionRangeDirty(int x0,int y0,int z0,int x1,int y1,int z1);
    @Inject(method="setBlocksDirty",at=@At("TAIL"))
    private void smoothterrain$halo(BlockPos pos,BlockState oldState,BlockState newState,CallbackInfo ci) {
        if(!TerrainConfig.enabled||(TerrainConfig.volume(oldState)==TerrainConfig.volume(newState)&&TerrainConfig.meshAnchor(oldState)==TerrainConfig.meshAnchor(newState)&&TerrainConfig.renderGroup(oldState)==TerrainConfig.renderGroup(newState))) return;
        TerrainCollision.invalidate((ClientLevel)(Object)this,pos);
        setSectionRangeDirty((pos.getX()-5)>>4,(pos.getY()-5)>>4,(pos.getZ()-5)>>4,
            (pos.getX()+5)>>4,(pos.getY()+5)>>4,(pos.getZ()+5)>>4);
    }
}
