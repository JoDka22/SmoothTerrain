package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.ExperimentalMovement;
import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ExperimentalMovementMixin {
    @Unique private boolean smoothterrain$groundedBefore;
    @Unique private double smoothterrain$previousX,smoothterrain$previousZ;
    @Inject(method="move",at=@At("HEAD"))
    private void smoothterrain$beforeMove(MoverType type,Vec3 movement,CallbackInfo ci) {
        smoothterrain$groundedBefore=false;
        if(TerrainConfig.safeMovement)return;
        var entity=(Entity)(Object)this;
        smoothterrain$groundedBefore=type==MoverType.SELF&&entity instanceof Player player
            &&player.onGround()&&ExperimentalMovement.active(player);
        if(smoothterrain$groundedBefore) {
            smoothterrain$previousX=entity.getX();smoothterrain$previousZ=entity.getZ();
        }
    }
    @Inject(method="move",at=@At("TAIL"))
    private void smoothterrain$afterMove(MoverType type,Vec3 movement,CallbackInfo ci) {
        if(smoothterrain$groundedBefore) {
            smoothterrain$groundedBefore=false;
            ExperimentalMovement.settle((Player)(Object)this,movement.y,smoothterrain$previousX,smoothterrain$previousZ);
        }
    }
}
