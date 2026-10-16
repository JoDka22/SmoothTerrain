package de.jorda.smoothterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import de.jorda.smoothterrain.ExperimentalMovement;
import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class ExperimentalMovementMixin {
    @Shadow private static Vec3 collideWithShapes(Vec3 movement,AABB box,List<VoxelShape> shapes) {throw new AssertionError();}
    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 smoothterrain$groundContact(Entity entity,Vec3 requested,Operation<Vec3> original,
            @Local(argsOnly=true) MoverType type) {
        Vec3 resolved=original.call(entity,requested);
        if(TerrainConfig.safeMovement||type!=MoverType.SELF||!(entity instanceof Player player))return resolved;
        return ExperimentalMovement.resolveGroundContact(player,requested,resolved);
    }
    @WrapOperation(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;collectCandidateStepUpHeights(Lnet/minecraft/world/phys/AABB;Ljava/util/List;FF)[F"))
    private float[] smoothterrain$stepCandidates(AABB box,List<VoxelShape> shapes,float maxStep,float currentY,
            Operation<float[]> original,@Local(argsOnly=true) Vec3 requested) {
        float[] candidates=original.call(box,shapes,maxStep,currentY);
        var entity=(Entity)(Object)this;
        if(TerrainConfig.safeMovement||!(entity instanceof Player player)||!ExperimentalMovement.active(player)
            ||!ExperimentalMovement.nearTerrain(player,box.expandTowards(requested)))return candidates;
        return ExperimentalMovement.bestStep(candidates,requested,height->
            collideWithShapes(new Vec3(requested.x,height,requested.z),box,shapes));
    }
}
