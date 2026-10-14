package de.jorda.smoothterrain.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import de.jorda.smoothterrain.TerrainSelection;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class OutlineMixin {
    @Inject(method="submitHitOutline",at=@At("HEAD"),cancellable=true)
    private void smoothterrain$outline(PoseStack pose,SubmitNodeCollector collector,RenderType type,
            BlockOutlineRenderState state,int color,float width,boolean depth,CallbackInfo ci) {
        if(TerrainSelection.submit(pose,collector,type,state.pos(),color,width)) ci.cancel();
    }
}
