package de.jorda.smoothterrain;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Drafts belong to the parent screen; only its Apply button persists them. */
final class AppearanceScreen extends Screen {
    private final RoundingScreen parent;
    AppearanceScreen(RoundingScreen parent) { super(Component.translatable("smoothterrain.appearance.title"));this.parent=parent; }
    @Override protected void init() {
        int w=Math.min(340,width-24),x=(width-w)/2,y=Math.max(28,(height-198)/2);
        String[] names={"leaves","logs","snow","shores","grass","all"};
        for(int i=0;i<names.length;i++) {
            int index=i;String name=names[i];
            addRenderableWidget(Button.builder(label(index,name),button->{
                var a=parent.choices;
                parent.choices=new TerrainConfig.Appearance(index==0?!a.leaves():a.leaves(),index==1?!a.logs():a.logs(),index==2?!a.snow():a.snow(),index==3?!a.shores():a.shores(),index==4?!a.grass():a.grass());
                if(index==5) parent.all=!parent.all;
                button.setMessage(label(index,name));
            }).tooltip(Tooltip.create(Component.translatable("smoothterrain.appearance."+name+"_hint"))).bounds(x,y+i*24,w,20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,y+150,w,20).build());
    }
    private Component label(int i,String name) {
        var a=parent.choices;boolean active=switch(i){case 0->a.leaves();case 1->a.logs();case 2->a.snow();case 3->a.shores();case 4->a.grass();default->parent.all;};
        return Component.translatable("smoothterrain.appearance."+name).append(": ").append(Component.translatable(active?"options.on":"options.off"));
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);g.centeredText(font,title,width/2,Math.max(28,(height-198)/2)-20,0xffffffff);
    }
}
