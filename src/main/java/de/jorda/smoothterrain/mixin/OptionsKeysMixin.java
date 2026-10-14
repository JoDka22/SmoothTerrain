package de.jorda.smoothterrain.mixin;

import de.jorda.smoothterrain.TerrainKeys;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsKeysMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/client/Options;load()V"))
    private void smoothterrain$registerKey(CallbackInfo ci) {
        if(Arrays.stream(keyMappings).anyMatch(key->key==TerrainKeys.SETTINGS)) return;
        keyMappings=Arrays.copyOf(keyMappings,keyMappings.length+1);
        keyMappings[keyMappings.length-1]=TerrainKeys.SETTINGS;
    }
}
