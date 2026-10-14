package de.jorda.smoothterrain;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keep optional renderer classes completely unloaded when Sodium is absent. */
public final class SmoothTerrainMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.endsWith("SodiumShaderContextMixin"))return FabricLoader.getInstance().isModLoaded("sodium")&&FabricLoader.getInstance().isModLoaded("iris");
        return !mixin.substring(mixin.lastIndexOf('.')+1).startsWith("Sodium")||FabricLoader.getInstance().isModLoaded("sodium");
    }
    public void acceptTargets(Set<String> own,Set<String> other) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
