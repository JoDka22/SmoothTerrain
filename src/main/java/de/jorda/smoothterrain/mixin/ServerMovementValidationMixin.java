package de.jorda.smoothterrain.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.jorda.smoothterrain.TerrainCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerMovementValidationMixin {
    @WrapMethod(method="isEntityCollidingWithAnythingNew")
    private boolean smoothterrain$validateRounded(LevelReader world,Entity entity,AABB previousBox,
            double x,double y,double z,Operation<Boolean> original) {
        return TerrainCollision.validateMovement(world,entity,
            ()->original.call(world,entity,previousBox,x,y,z));
    }
}
