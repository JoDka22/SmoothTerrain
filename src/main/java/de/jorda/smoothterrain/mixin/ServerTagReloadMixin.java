package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
public abstract class ServerTagReloadMixin {
    @Inject(method="updateComponentsAndStaticRegistryTags",at=@At("TAIL"))
    private void smoothterrain$tags(CallbackInfo ci) {
        TerrainConfig.tagsUpdated();
    }
}
