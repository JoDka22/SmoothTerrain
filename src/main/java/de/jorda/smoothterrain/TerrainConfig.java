package de.jorda.smoothterrain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

public final class TerrainConfig {
    public static volatile boolean enabled = true;
    public static volatile double rounding = 0.25;
    private static volatile Set<Block> blocks = Set.of();
    private static Set<String> blockIds=Set.of();
    private static int knownBlockCount=-1;
    private static volatile Set<BlockState> fullStates = Set.of();
    private static volatile Set<BlockState> modelAnchors = Set.of();
    private static volatile boolean allBlocks = false;
    public record Appearance(boolean leaves,boolean logs,boolean snow,boolean shores,boolean grass) {}
    private static volatile Appearance appearance=new Appearance(false,false,true,true,true);
    public static Appearance appearance() { BakeData data=BAKE.get();return data==null?appearance:data.appearance; }
    public static boolean allBlocks() { return allBlocks; }
    /** Separately emitted material groups must agree on their shared contact border. */
    public static boolean renderAnchor(BlockState state,int group) {
        return meshAnchor(state)||smooth(state)&&renderGroup(state)!=group;
    }
    public record Filters(List<String> ignored_blocks, List<String> ignored_tags,
                          boolean redstone, boolean containers, boolean structures) {
        public Filters { ignored_blocks=normalize(ignored_blocks,false); ignored_tags=normalize(ignored_tags,true); }
    }
    private static List<String> normalize(List<String> entries,boolean tags) {
        return entries.stream().map(String::trim).map(id->tags&&id.startsWith("#")?id.substring(1):id)
            .map(Identifier::tryParse).filter(Objects::nonNull).map(Identifier::toString).distinct().toList();
    }
    private static final Set<String> CONTAINERS=Set.of("barrel","hopper","furnace","blast_furnace","smoker","dispenser","dropper","crafter","brewing_stand","decorated_pot");
    private static final Set<String> TECHNICAL=Set.of("observer","repeater","comparator","daylight_detector","target","dispenser","dropper","crafter","hopper","sculk_sensor","calibrated_sculk_sensor","sculk_shrieker","tnt","lever","tripwire_hook","tripwire");
    private record FilterState(Filters settings, Set<Block> blocks, List<TagKey<Block>> tags) {}
    private static volatile FilterState filterState=new FilterState(new Filters(List.of(),List.of(),false,false,false),Set.of(),List.of());
    public static final java.util.concurrent.atomic.AtomicLong FILTER_REVISION=new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean tagsReady;
    public static synchronized void tagsUpdated() {
        tagsReady=true;
        if(hasTagFilters()) { filterState=compile(filterState.settings);FILTER_REVISION.incrementAndGet();TerrainCollision.REVISION.incrementAndGet(); }
    }
    public static boolean hasTagFilters() { return !filterState.tags.isEmpty(); }
    public static Filters filters() { return filterState.settings; }
    private static FilterState compile(Filters settings) {
        Set<Block> excluded=new HashSet<>();
        for(String id:settings.ignored_blocks) {
            Identifier key=Identifier.tryParse(id);
            if(key!=null) BuiltInRegistries.BLOCK.getOptional(key).ifPresent(excluded::add);
        }
        if(settings.redstone||settings.containers||settings.structures) for(Block block:BuiltInRegistries.BLOCK) {
            String name=BuiltInRegistries.BLOCK.getKey(block).getPath();
            BlockState state=block.defaultBlockState();
            boolean container=name.contains("chest")||name.contains("shulker_box")||CONTAINERS.contains(name);
            boolean redstone=state.isSignalSource()||name.contains("redstone")||name.contains("piston")||name.contains("command_block")||TECHNICAL.contains(name)||name.endsWith("_rail")||name.equals("rail")||name.endsWith("_button")||name.endsWith("_pressure_plate");
            boolean structure=name.endsWith("_fence")||name.endsWith("_fence_gate")||name.endsWith("_wall")||name.endsWith("_pane")||name.equals("iron_bars");
            if(settings.redstone&&redstone||settings.containers&&container||settings.structures&&structure) excluded.add(block);
        }
        List<TagKey<Block>> tags=settings.ignored_tags.stream().map(id->Identifier.tryParse(id.startsWith("#")?id.substring(1):id))
            .filter(Objects::nonNull).map(id->TagKey.create(Registries.BLOCK,id)).toList();
        // Resolve tags when they change, not against live registry tag sets on
        // worker threads. Every published filter set is immutable.
        if(tagsReady&&!tags.isEmpty()) for(Block block:BuiltInRegistries.BLOCK)
            for(TagKey<Block> tag:tags) if(block.defaultBlockState().is(tag)) { excluded.add(block);break; }
        return new FilterState(settings,Set.copyOf(excluded),tags);
    }
    private static List<String> list(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isEmpty()).distinct().toList();
    }
    public static boolean ignored(BlockState state) {
        BakeData data=BAKE.get();
        return (data==null?filterState:data.filters).blocks.contains(state.getBlock());
    }
    public static boolean fixed(BlockState state) {
        BakeData data=BAKE.get();
        return volume(state)&&!smooth(state);
    }
    // Fluids retain their original mesh: keep all shared shoreline vertices exact.
    public static boolean meshAnchor(BlockState state) {
        if(state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock) return !appearance().snow||ignored(state);
        BakeData data=BAKE.get();return fixed(state)||(!state.getFluidState().isEmpty()&&(!appearance().shores||!state.getFluidState().isSource()))||(data==null?modelAnchors:data.anchors).contains(state);
    }
    public static boolean hasFilters() { BakeData data=BAKE.get();FilterState snapshot=data==null?filterState:data.filters; return !(data==null?allBlocks:data.all)||!snapshot.blocks.isEmpty()||!snapshot.tags.isEmpty(); }

    public static volatile boolean collisions = true;
    // Stable movement is the default, including migration from legacy configs.
    // Rounded collision geometry remains opt-in until its movement is reliable.
    public static volatile boolean safeMovement = true;
    public static volatile boolean multiplayerCollisions = false;
    private record BakeData(boolean enabled,double rounding,boolean all,Set<Block> blocks,Set<BlockState> full,Set<BlockState> anchors,FilterState filters,long revision,Appearance appearance) {}
    private static final ThreadLocal<BakeData> BAKE=new ThreadLocal<>();
    public static final class BakeScope implements AutoCloseable {
        private final BakeData previous;
        private boolean closed;
        private BakeScope(BakeData previous) { this.previous=previous; }
        @Override public void close() {
            if(closed) return;closed=true;
            if(previous==null) BAKE.remove();else BAKE.set(previous);
        }
    }
    public static BakeScope openBake() {
        BakeData previous=BAKE.get();
        // Allocate the cleanup handle before publishing a thread-local snapshot.
        BakeScope scope=new BakeScope(previous);
        // A nested compiler/collider already owns an immutable snapshot. Do not
        // wait for a UI writer (including its disk I/O) to reuse that snapshot.
        if(previous==null) synchronized(TerrainConfig.class) {
            BAKE.set(new BakeData(enabled,rounding,allBlocks,blocks,fullStates,modelAnchors,filterState,FILTER_REVISION.get(),appearance));
        }
        return scope;
    }
    public static boolean bakeEnabled() { BakeData data=BAKE.get();return data==null?enabled:data.enabled; }
    public static double bakeRounding() { BakeData data=BAKE.get();return data==null?rounding:data.rounding; }
    public static long bakeFilterRevision() { BakeData data=BAKE.get();return data==null?FILTER_REVISION.get():data.revision; }
    private static final String LEGACY_BLOCKS = "minecraft:grass_block,minecraft:dirt,minecraft:coarse_dirt,minecraft:rooted_dirt,minecraft:podzol,minecraft:mycelium,minecraft:stone,minecraft:granite,minecraft:diorite,minecraft:andesite,minecraft:deepslate,minecraft:tuff,minecraft:calcite,minecraft:sand,minecraft:red_sand,minecraft:gravel,minecraft:clay,minecraft:snow_block,minecraft:netherrack,minecraft:end_stone";
    private static String defaultBlocks() {
        Set<String> names=new HashSet<>(Arrays.asList(LEGACY_BLOCKS.split(",")));
        for(String name:List.of("mud","moss_block","pale_moss_block","dripstone_block","basalt","smooth_basalt","blackstone","soul_sand","soul_soil","ice","packed_ice","blue_ice","terracotta","amethyst_block","budding_amethyst","raw_iron_block","raw_copper_block","raw_gold_block")) names.add("minecraft:"+name);
        for(var id:BuiltInRegistries.BLOCK.keySet()) if(id.getNamespace().equals("minecraft")&&(id.getPath().endsWith("_ore")||id.getPath().endsWith("_terracotta")&&!id.getPath().contains("glazed"))) names.add(id.toString());
        return String.join(",",new TreeSet<>(names));
    }

    public static synchronized void load() {
        Properties properties = new Properties();
        properties.setProperty("enabled", "true");
        properties.setProperty("rounding", "0.25");
        properties.setProperty("blocks", defaultBlocks());
        properties.setProperty("mode", "terrain");
        properties.setProperty("config_version", "2");
        properties.setProperty("ignored_blocks", "");
        properties.setProperty("ignored_tags", "");
        properties.setProperty("ignore_redstone", "false");
        properties.setProperty("ignore_containers", "false");
        properties.setProperty("ignore_structures", "false");
        properties.setProperty("collisions", "true");
        properties.setProperty("safeMovement", "true");
        properties.setProperty("multiplayerCollisions", "false");
        Path file = FabricLoader.getInstance().getConfigDir().resolve("smoothterrain.properties");
        boolean migrate=false;
        try {
            if (Files.exists(file)) {
                Properties loaded=new Properties();
                try (Reader reader = Files.newBufferedReader(file)) { loaded.load(reader); }
                properties.putAll(loaded);
                // Old files were generated with mode=all. Migrate once to the
                // safe terrain-only default; explicit schema-2 opt-ins survive.
                if(!loaded.getProperty("config_version", "0").equals("2")) {
                    migrate=true;
                    properties.setProperty("mode", "terrain");
                    if(properties.getProperty("blocks").equals(LEGACY_BLOCKS)) properties.setProperty("blocks",defaultBlocks());
                }
            } else {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    properties.store(writer, "Smooth Terrain: mode=all or terrain; restart Minecraft after changing settings.");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("smoothterrain").warn("Could not read/write config; using defaults", e);
        }
        enabled = Boolean.parseBoolean(properties.getProperty("enabled"));
        try {
            double value=Double.parseDouble(properties.getProperty("rounding"));
            rounding=Double.isFinite(value)?Math.clamp(value,0.0,1.0):0.25;
        } catch(NumberFormatException e) { rounding=0.25; }
        allBlocks = properties.getProperty("mode").trim().equalsIgnoreCase("all");
        collisions = Boolean.parseBoolean(properties.getProperty("collisions"));
        // Only an explicit false opts into experimental movement. Typos and
        // empty values must not accidentally disable the stable default.
        safeMovement = !"false".equalsIgnoreCase(properties.getProperty("safeMovement").trim());
        multiplayerCollisions = Boolean.parseBoolean(properties.getProperty("multiplayerCollisions"));
        appearance=new Appearance(Boolean.parseBoolean(properties.getProperty("smooth_leaves","false")),Boolean.parseBoolean(properties.getProperty("smooth_logs","false")),
            !"false".equalsIgnoreCase(properties.getProperty("smooth_snow","true")),!"false".equalsIgnoreCase(properties.getProperty("smooth_shores","true")),!"false".equalsIgnoreCase(properties.getProperty("better_grass","true")));
        blockIds = Set.copyOf(normalize(list(properties.getProperty("blocks")),false));
        rebuildRegistryStates();
        filterState=compile(new Filters(list(properties.getProperty("ignored_blocks","")),list(properties.getProperty("ignored_tags","")),
            Boolean.parseBoolean(properties.getProperty("ignore_redstone","false")),Boolean.parseBoolean(properties.getProperty("ignore_containers","false")),Boolean.parseBoolean(properties.getProperty("ignore_structures","false"))));
        FILTER_REVISION.incrementAndGet();
        if(migrate) saveRounding(rounding);
    }

    // Fabric runs every common initializer before client/server initializers.
    // Pick up registrations from mods that initialize after us, without a second
    // full scan when the registry has not grown.
    public static synchronized void refreshRegistry() {
        if(BuiltInRegistries.BLOCK.size()==knownBlockCount) return;
        rebuildRegistryStates();filterState=compile(filterState.settings);FILTER_REVISION.incrementAndGet();
    }

    private static void rebuildRegistryStates() {
        blocks = BuiltInRegistries.BLOCK.stream()
            .filter(block -> blockIds.contains(BuiltInRegistries.BLOCK.getKey(block).toString()))
            .collect(Collectors.toUnmodifiableSet());
        Set<Block> unsupported=new HashSet<>();
        fullStates = BuiltInRegistries.BLOCK.stream().flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
            .filter(state -> fullCube(state,unsupported))
            .collect(Collectors.toUnmodifiableSet());
        modelAnchors=BuiltInRegistries.BLOCK.stream().flatMap(block->block.getStateDefinition().getPossibleStates().stream())
            .filter(state->!fullStates.contains(state)&&partialAnchor(state)).collect(Collectors.toUnmodifiableSet());
        knownBlockCount=BuiltInRegistries.BLOCK.size();
    }

    private static boolean partialAnchor(BlockState state) {
        if(state.getRenderShape()!=RenderShape.MODEL||state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock||leaves(state)) return false;
        try {return !state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty();}
        catch(RuntimeException unsupported) {return true;}
    }

    private static boolean fullCube(BlockState state,Set<Block> unsupported) {
        if(state.getRenderShape()!=RenderShape.MODEL||!state.getFluidState().isEmpty()
            ||state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock) return false;
        try {
            return state.isSolidRender()||Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));
        } catch(RuntimeException exception) {
            // Some mod blocks require a real world to determine their shape.
            // Preserve that model instead of aborting configuration/startup.
            if(unsupported.add(state.getBlock())) LoggerFactory.getLogger("smoothterrain").warn("Preserving world-dependent block {}: {}",BuiltInRegistries.BLOCK.getKey(state.getBlock()),exception.toString());
            return false;
        }
    }

    public static boolean smooth(BlockState state) {
        BakeData data=BAKE.get();
        boolean selected=(data==null?allBlocks:data.all)||(data==null?blocks:data.blocks).contains(state.getBlock());
        if(leaves(state)&&appearance().leaves) selected=true;
        if(appearance().logs&&logs(state)) selected=true;
        if(state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)&&!appearance().snow) selected=false;
        if(state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock&&appearance().snow) selected=false;
        return selected && volume(state) && !ignored(state);
    }
    public static boolean logs(BlockState state) { return state.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_log"); }
    public static boolean volume(BlockState state) { BakeData data=BAKE.get();return (data==null?fullStates:data.full).contains(state); }
    public static boolean leaves(BlockState state) { return state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock; }
    public static int renderGroup(BlockState state) { return leaves(state)?1:state.isSolidRender()?0:2; }
    public static boolean renderVolume(BlockState state,boolean foliage) {
        return renderVolume(state,foliage?1:0);
    }
    public static boolean renderVolume(BlockState state,int group) {
        if(state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock&&appearance().snow) return false;
        if(!volume(state)) return false;
        int own=renderGroup(state);
        return own==0||own==group;
    }

    public static boolean saveRounding(double value) { return save(value,null,null,null,null); }
    public static boolean saveSettings(double value,boolean stable) { return save(value,null,stable,null,null); }
    public static boolean saveSettings(double value,boolean stable,Appearance choices,boolean all) { return save(value,null,stable,choices,all); }
    public static synchronized boolean saveFilters(Filters filters) { return save(rounding,filters,null,null,null); }
    private static synchronized boolean save(double value,Filters filters,Boolean stable,Appearance choices,Boolean all) {
        double next=Double.isFinite(value)?Math.clamp(value,0.0,1.0):0.25;
        Path file=FabricLoader.getInstance().getConfigDir().resolve("smoothterrain.properties");
        Path temporary=null;
        try {
            Properties properties=new Properties();
            if(Files.exists(file)) {
                try(Reader reader=Files.newBufferedReader(file)) { properties.load(reader); }
            }
            properties.setProperty("rounding",Double.toString(next));
            properties.setProperty("config_version","2");
            properties.setProperty("mode",(all==null?allBlocks:all)?"all":"terrain");
            Appearance nextAppearance=choices==null?appearance:choices;
            properties.setProperty("smooth_leaves",Boolean.toString(nextAppearance.leaves));
            properties.setProperty("smooth_logs",Boolean.toString(nextAppearance.logs));
            properties.setProperty("smooth_snow",Boolean.toString(nextAppearance.snow));
            properties.setProperty("smooth_shores",Boolean.toString(nextAppearance.shores));
            properties.setProperty("better_grass",Boolean.toString(nextAppearance.grass));
            properties.setProperty("blocks",String.join(",",new TreeSet<>(blockIds)));
            if(stable!=null) properties.setProperty("safeMovement",Boolean.toString(stable));
            FilterState prepared=filters==null?null:compile(filters);
            if(filters!=null) {
                properties.setProperty("ignored_blocks",String.join(",",filters.ignored_blocks));
                properties.setProperty("ignored_tags",String.join(",",filters.ignored_tags));
                properties.setProperty("ignore_redstone",Boolean.toString(filters.redstone));
                properties.setProperty("ignore_containers",Boolean.toString(filters.containers));
                properties.setProperty("ignore_structures",Boolean.toString(filters.structures));
            }
            Files.createDirectories(file.getParent());
            temporary=Files.createTempFile(file.getParent(),"smoothterrain-",".tmp");
            try(Writer writer=Files.newBufferedWriter(temporary)) {
                properties.store(writer,"Smooth Terrain: rounding can be changed through Mod Menu.");
            }
            try {
                Files.move(temporary,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch(java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            rounding=next;
            appearance=nextAppearance;
            if(all!=null) allBlocks=all;
            if(stable!=null) safeMovement=stable;
            if(prepared!=null) filterState=prepared;
            // Appearance/all-block changes also alter collision anchors. Publish
            // a new bake revision even when the blacklist itself is unchanged.
            FILTER_REVISION.incrementAndGet();
            TerrainCollision.REVISION.incrementAndGet();
            return true;
        } catch(IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("smoothterrain").warn("Could not save settings",e);
            return false;
        } finally {
            if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) {}
        }
    }
}
