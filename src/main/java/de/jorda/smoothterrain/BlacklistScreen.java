package de.jorda.smoothterrain;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Draft-only editor: changes take effect together when Apply succeeds. */
public final class BlacklistScreen extends Screen {
    private final Screen parent;
    private final List<String> entries=new ArrayList<>();
    private boolean redstone,containers,structures;
    private int page;
    private String query="",error="";
    private EditBox search;
    private Button suggestion,add;
    private List<String> matches=List.of();
    private int matchIndex;
    private final List<String> candidates=new ArrayList<>();

    public BlacklistScreen(Screen parent) {
        super(Component.translatable("smoothterrain.blacklist.title"));this.parent=parent;
        var settings=TerrainConfig.filters();entries.addAll(settings.ignored_blocks());
        settings.ignored_tags().forEach(tag->entries.add("#"+(tag.startsWith("#")?tag.substring(1):tag)));
        redstone=settings.redstone();containers=settings.containers();structures=settings.structures();
        BuiltInRegistries.BLOCK.keySet().forEach(id->candidates.add(id.toString()));
        BuiltInRegistries.BLOCK.getTags().forEach(tag->candidates.add("#"+tag.key().location()));
        Collections.sort(candidates);
    }
    private int rows() { return Math.max(1,(height-202)/22); }
    private int pages() { return Math.max(1,(entries.size()+rows()-1)/rows()); }
    private Component label(String key,boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value?"options.on":"options.off"));
    }
    @Override protected void init() {
        int w=Math.min(380,width-24),left=(width-w)/2;
        page=Math.clamp(page,0,pages()-1);
        addRenderableWidget(Button.builder(label("smoothterrain.blacklist.redstone",redstone),b->{redstone=!redstone;b.setMessage(label("smoothterrain.blacklist.redstone",redstone));}).bounds(left,30,w,20).build());
        addRenderableWidget(Button.builder(label("smoothterrain.blacklist.containers",containers),b->{containers=!containers;b.setMessage(label("smoothterrain.blacklist.containers",containers));}).bounds(left,52,w,20).build());
        addRenderableWidget(Button.builder(label("smoothterrain.blacklist.structures",structures),b->{structures=!structures;b.setMessage(label("smoothterrain.blacklist.structures",structures));}).bounds(left,74,w,20).build());
        search=addRenderableWidget(new EditBox(font,left,100,w-76,20,Component.translatable("smoothterrain.blacklist.search")));
        search.setMaxLength(256);search.setHint(Component.literal("minecraft:furnace / #c:glass"));search.setValue(query);
        add=addRenderableWidget(Button.builder(Component.translatable("smoothterrain.blacklist.add"),b->insert(query)).bounds(left+w-72,100,72,20).build());
        suggestion=addRenderableWidget(Button.builder(Component.empty(),b->{if(!matches.isEmpty()) insert(matches.get(matchIndex));}).bounds(left,124,w-26,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{if(!matches.isEmpty()){matchIndex=(matchIndex+1)%matches.size();updateSuggestion();}}).bounds(left+w-22,124,22,20).build());
        search.setResponder(value->{query=value;matchIndex=0;updateSearch();});updateSearch();
        for(int row=0;row<rows();row++) {
            int index=page*rows()+row;if(index>=entries.size()) break;
            String entry=entries.get(index);
            Button text=addRenderableWidget(Button.builder(Component.literal(entry),b->{search.setValue(entry);setFocused(search);}).bounds(left,154+row*22,w-30,20).build());
            text.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(entry)));
            addRenderableWidget(Button.builder(Component.literal("[X]"),b->{query=search.getValue();entries.remove(index);error="";rebuildWidgets();}).bounds(left+w-26,154+row*22,26,20).build());
        }
        Button previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuildWidgets();}).bounds(left,height-46,30,20).build());previous.active=page>0;
        Button next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuildWidgets();}).bounds(left+w-30,height-46,30,20).build());next.active=page+1<pages();
        addRenderableWidget(Button.builder(Component.translatable("smoothterrain.settings.apply"),b->apply()).bounds(left,height-24,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left+(w+8)/2,height-24,(w-8)/2,20).build());
    }
    private String canonical(String input) {
        String value=input.trim();boolean tag=value.startsWith("#");
        Identifier id=Identifier.tryParse(tag?value.substring(1):value);
        if(id==null||(!tag&&!BuiltInRegistries.BLOCK.containsKey(id))) return null;
        return (tag?"#":"")+id;
    }
    private void updateSearch() {
        matches=findMatches(query,candidates,entries);
        String value=canonical(query);
        add.active=value!=null&&!entries.contains(value);updateSuggestion();
    }
    static List<String> findMatches(String query,List<String> candidates,List<String> entries) {
        String normalized=query.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty())return List.of();
        // Large modpacks can have thousands of registry IDs and exclusions.
        // Build membership once instead of scanning the full blacklist per ID.
        Set<String> excluded=entries.isEmpty()?Set.of():new HashSet<>(entries);
        return candidates.stream().filter(id->id.contains(normalized)&&!excluded.contains(id)).toList();
    }
    private void updateSuggestion() {
        suggestion.active=!matches.isEmpty();
        suggestion.setMessage(matches.isEmpty()?Component.translatable("smoothterrain.blacklist.no_match"):
            Component.literal("+ "+matches.get(matchIndex)+" ("+(matchIndex+1)+"/"+matches.size()+")"));
    }
    private void insert(String input) {
        String value=canonical(input);if(value==null||entries.contains(value)) return;
        entries.add(value);query="";error="";page=(entries.size()-1)/rows();rebuildWidgets();setFocused(search);
    }
    private void apply() {
        var settings=new TerrainConfig.Filters(entries.stream().filter(id->!id.startsWith("#")).toList(),
            entries.stream().filter(id->id.startsWith("#")).map(id->id.substring(1)).toList(),redstone,containers,structures);
        if(!TerrainConfig.saveFilters(settings)) {error="smoothterrain.settings.save_failed";return;}
        if(minecraft.level!=null) minecraft.levelExtractor.allChanged();onClose();
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick) {
        super.extractRenderState(graphics,x,y,tick);
        graphics.centeredText(font,title,width/2,12,0xffffffff);
        graphics.centeredText(font,Component.translatable("smoothterrain.blacklist.page",page+1,pages(),entries.size()),width/2,height-40,0xffdddddd);
        if(entries.isEmpty()) graphics.centeredText(font,Component.translatable("smoothterrain.blacklist.empty"),width/2,160,0xffbbbbbb);
        if(!error.isEmpty()) graphics.centeredText(font,Component.translatable(error),width/2,height-58,0xffff7777);
    }
}
