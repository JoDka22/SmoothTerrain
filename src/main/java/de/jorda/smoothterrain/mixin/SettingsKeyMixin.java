package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainKeys;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SettingsKeyMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void smoothterrain$settingsKey(CallbackInfo ci) {TerrainKeys.tick((Minecraft)(Object)this);}
}
