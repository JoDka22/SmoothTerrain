package de.jorda.smoothterrain.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the immutable option of this worker's renderer, not a changing GUI option. */
@Mixin(value=BlockRenderer.class,remap=false)
public interface SodiumLeafAccessor {
    @Accessor("cutoutLeaves") boolean smoothterrain$cutoutLeaves();
}
