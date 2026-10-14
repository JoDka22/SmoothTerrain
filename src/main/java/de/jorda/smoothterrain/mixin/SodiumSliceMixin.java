package de.jorda.smoothterrain.mixin;

import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LevelSlice.class,remap=false)
public abstract class SodiumSliceMixin {
    @Inject(method="prepare",at=@At("RETURN"),cancellable=true)
    private static void smoothterrain$halo(Level world,SectionPos section,ClonedChunkSectionCache cache,CallbackInfoReturnable<ChunkRenderContext> ci) {
        var context=ci.getReturnValue();
        if(context==null) return;
        // Sodium already captures 27 sections. Unpack a five-block border from
        // those immutable snapshots, instead of reading a live world on workers.
        // Keep the halo even at zero rounding: a queued task may begin after an
        // in-game settings change and must still have sufficient snapshot data.
        var volume=new BoundingBox(section.minBlockX()-5,section.minBlockY()-5,section.minBlockZ()-5,
            section.maxBlockX()+5,section.maxBlockY()+5,section.maxBlockZ()+5);
        ci.setReturnValue(new ChunkRenderContext(context.getOrigin(),context.getSections(),volume,context.getRenderers()));
    }
}
