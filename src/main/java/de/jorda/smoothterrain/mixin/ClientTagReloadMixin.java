package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientTagReloadMixin {
    @Inject(method="handleUpdateTags",at=@At("TAIL"))
    private void smoothterrain$tags(ClientboundUpdateTagsPacket packet,CallbackInfo ci) {
        TerrainConfig.tagsUpdated();
        var client=Minecraft.getInstance();
        if(TerrainConfig.hasTagFilters()&&client.level!=null) client.levelExtractor.allChanged();
    }
}
