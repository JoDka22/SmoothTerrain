package de.jorda.smoothterrain;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RoundingScreen extends Screen {
    private final Screen parent;
    private double draft=TerrainConfig.rounding;
    private boolean stable=TerrainConfig.safeMovement;
    TerrainConfig.Appearance choices=TerrainConfig.appearance();
    boolean all=TerrainConfig.allBlocks();
    private boolean saveFailed;

    public RoundingScreen(Screen parent) {
        super(Component.translatable("smoothterrain.settings.title"));
        this.parent=parent;
    }

    @Override
    protected void init() {
        int w=Math.min(300,width-32),left=(width-w)/2;
        int y=Math.max(48,height/2-56);
        RoundingSlider slider=addRenderableWidget(new RoundingSlider(left,y,w));
        addRenderableWidget(Button.builder(Component.translatable("smoothterrain.settings.default"),button->{
            slider.reset();saveFailed=false;
        }).bounds(left,y+30,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("smoothterrain.blacklist.title"),button->minecraft.gui.setScreen(new BlacklistScreen(this)))
            .bounds(left,y+54,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.translatable("smoothterrain.appearance.title"),button->minecraft.gui.setScreen(new AppearanceScreen(this)))
            .bounds(left+(w+8)/2,y+54,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(movementLabel(),button->{
            stable=!stable;button.setMessage(movementLabel());button.setTooltip(movementTooltip());saveFailed=false;
        }).tooltip(movementTooltip()).bounds(left,y+78,w,20).build());
        int gap=8,half=(w-gap)/2;
        addRenderableWidget(Button.builder(Component.translatable("smoothterrain.settings.apply"),button->apply())
            .bounds(left,y+104,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button->onClose())
            .bounds(left+half+gap,y+104,w-half-gap,20).build());
    }

    private void apply() {
        if(!TerrainConfig.saveSettings(draft,stable,choices,all)) { saveFailed=true;return; }
        if(minecraft.level!=null) minecraft.levelExtractor.allChanged();
        minecraft.gui.setScreen(parent);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    private Component movementLabel() { return Component.translatable(stable?"smoothterrain.settings.movement_stable":"smoothterrain.settings.movement_experimental"); }
    private net.minecraft.client.gui.components.Tooltip movementTooltip() {
        return net.minecraft.client.gui.components.Tooltip.create(Component.translatable(stable?"smoothterrain.settings.movement_stable_hint":"smoothterrain.settings.movement_experimental_hint"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        int y=Math.max(48,height/2-56);
        graphics.centeredText(font,title,width/2,y-28,0xffffffff);
        graphics.centeredText(font,Component.translatable("smoothterrain.settings.hint"),width/2,y+129,0xffc0c0c0);
        if(saveFailed) graphics.centeredText(font,Component.translatable("smoothterrain.settings.save_failed"),width/2,y+143,0xffff7777);
    }

    private final class RoundingSlider extends AbstractSliderButton {
        RoundingSlider(int x,int y,int width) {
            super(x,y,width,20,Component.empty(),draft);
            updateMessage();
        }
        void reset() { setValue(0.25); }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("smoothterrain.settings.rounding",Math.round(value*100)));
        }
        @Override protected void applyValue() { draft=Math.round(value*100)/100.0;saveFailed=false; }
    }
}
