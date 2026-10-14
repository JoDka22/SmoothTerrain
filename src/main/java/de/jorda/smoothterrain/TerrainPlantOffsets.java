package de.jorda.smoothterrain;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Plant translations scoped to a single chunk bake, shared by vanilla and Indigo. */
public final class TerrainPlantOffsets {
    @FunctionalInterface interface States { BlockState get(int x,int y,int z); }
    private static final class Plant {
        final BlockPos root;
        final double x,z;
        double height=Double.NaN;
        Plant(BlockPos root,BlockState state) {
            this.root=root;
            var offset=state.getOffset(root.above());
            x=root.getX()+0.5+offset.x;z=root.getZ()+0.5+offset.z;
        }
    }
    private static final class Bake {
        boolean enclosed;
        final Map<SurfaceMesh.C,Plant> roots=new HashMap<>();
        final Map<Long,Plant> plants=new HashMap<>();
        final Set<Long> snow=new HashSet<>();
    }
    private static final ThreadLocal<Bake> ACTIVE=new ThreadLocal<>();
    public static void begin() { ACTIVE.remove(); }
    public static void end() { ACTIVE.remove(); }
    public static boolean enclosed() { Bake bake=ACTIVE.get();return bake!=null&&bake.enclosed; }
    public static boolean replacesSnow(BlockPos pos) { Bake bake=ACTIVE.get();return bake!=null&&bake.snow.contains(pos.asLong()); }
    static void enclose() { Bake bake=new Bake();bake.enclosed=true;ACTIVE.set(bake); }
    static void start() { ACTIVE.set(new Bake()); }
    static void prepare(SectionPos section,States states) {
        start();
        BlockPos origin=section.origin();
        for(int z=0;z<16;z++) for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            BlockState state=states.get(origin.getX()+x,origin.getY()+y,origin.getZ()+z);
            collect(origin.getX()+x,origin.getY()+y,origin.getZ()+z,state,states);
        }
    }
    static void collect(int x,int y,int z,BlockState state,States states) {
            if(state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock&&TerrainConfig.appearance().snow()&&!TerrainConfig.ignored(state)
                &&TerrainConfig.smooth(states.get(x,y-1,z))) { Bake bake=ACTIVE.get();if(bake!=null) bake.snow.add(BlockPos.asLong(x,y,z));return; }
            if(!(state.getBlock() instanceof VegetationBlock)||!state.getFluidState().isEmpty()||TerrainConfig.ignored(state)) return;
            Bake bake=ACTIVE.get();
            if(bake==null) return;
            BlockPos pos=new BlockPos(x,y,z);
            BlockPos bottom=state.getBlock() instanceof DoublePlantBlock&&state.getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
            BlockPos root=bottom.below();
            BlockState ground=states.get(root.getX(),root.getY(),root.getZ());
            if(!TerrainConfig.smooth(ground)||TerrainConfig.renderGroup(ground)!=0) return;
            var key=new SurfaceMesh.C(root.getX(),root.getY(),root.getZ());
            Plant plant=bake.roots.computeIfAbsent(key,k->new Plant(root,states.get(bottom.getX(),bottom.getY(),bottom.getZ())));
            bake.plants.put(pos.asLong(),plant);
    }
    static void accept(SurfaceMesh.Quad face) {
        Bake bake=ACTIVE.get();
        if(bake==null||bake.roots.isEmpty()||face.axis()!=1||face.sign()<0) return;
        Plant plant=bake.roots.get(face.source());
        if(plant==null) return;
        double height=MeshRay.height(face,plant.x,plant.z);
        if(Double.isFinite(height)&&height>=plant.root.getY()+0.5&&height<=plant.root.getY()+1.5)
            plant.height=Double.isNaN(plant.height)?height:Math.max(height,plant.height);
    }
    static void finish(States states) {
        Bake bake=ACTIVE.get();
        if(bake==null) return;
        // All missing roots share one halo bake, rather than rebuilding the same
        // supporting row once per plant along a section boundary.
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        for(Plant plant:bake.roots.values()) if(Double.isNaN(plant.height)) {
            var p=plant.root;
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
        }
        if(maxX==Integer.MIN_VALUE) return;
        new SurfaceMesh((x,y,z)->TerrainConfig.renderVolume(states.get(x,y,z),0),TerrainConfig.bakeRounding(),
            (x,y,z)->TerrainConfig.renderAnchor(states.get(x,y,z),0))
            .generateBox(minX-1,minY-1,minZ-1,maxX-minX+2,maxY-minY+2,maxZ-minZ+2,2,bake.roots::containsKey,TerrainPlantOffsets::accept);
    }
    public static double offset(BlockPos pos) {
        Bake bake=ACTIVE.get();
        Plant plant=bake==null?null:bake.plants.get(pos.asLong());
        return plant==null||Double.isNaN(plant.height)?0:plant.height-plant.root.getY()-1;
    }
}
