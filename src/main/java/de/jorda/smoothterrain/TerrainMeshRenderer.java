package de.jorda.smoothterrain;

import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainMeshRenderer {
    private record Template(BakedQuad quad,FaceUV uv) {}
    private record Layer(Template template,VertexConsumer buffer,int tint,int light,BlockState state) {}
    private static final class FaceLayers { SurfaceMesh.C source; int axis,sign; List<Layer> layers=List.of(); }
    private static final class Bounds {
        int minX=16,minY=16,minZ=16,maxX=-1,maxY=-1,maxZ=-1;
        void include(int x,int y,int z) {
            minX=Math.min(minX,Math.max(-1,x-1));minY=Math.min(minY,Math.max(-1,y-1));minZ=Math.min(minZ,Math.max(-1,z-1));
            maxX=Math.max(maxX,Math.min(15,x));maxY=Math.max(maxY,Math.min(15,y));maxZ=Math.max(maxZ,Math.min(15,z));
        }
        boolean present() { return maxX>=0; }
    }
    private static FaceUV mapping(BakedQuad quad,int axis,int sign) {
        FaceUV result=new FaceUV();
        for(int i=0;i<4;i++) {
            var p=quad.position(i);long packed=quad.packedUV(i);
            result.set(FaceUV.corner(p.x(),p.y(),p.z(),axis,sign),UVPair.unpackU(packed),UVPair.unpackV(packed));
        }
        return result;
    }
    public static void render(SectionPos section,BlockAndTintGetter world,BlockStateModelSet models,
            BlockColors colors,boolean cutoutLeaves,Function<ChunkSectionLayer,? extends VertexConsumer> buffers) {
        try(var scope=TerrainConfig.openBake()) {
            try { renderScoped(section,world,models,colors,cutoutLeaves,buffers); }
            catch(RuntimeException|Error failure) { TerrainPlantOffsets.end();throw failure; }
        }
    }
    private static void renderScoped(SectionPos section,BlockAndTintGetter world,BlockStateModelSet models,
            BlockColors colors,boolean cutoutLeaves,Function<ChunkSectionLayer,? extends VertexConsumer> buffers) {
        if(!TerrainConfig.bakeEnabled()||TerrainConfig.bakeRounding()==0) return;
        BlockPos origin=section.origin();
        ShaderContexts shaders=ShaderContexts.capture(origin);
        SectionStates states=new SectionStates(world,origin);
        boolean enclosed=true;
        Bounds[] groups={new Bounds(),new Bounds(),new Bounds()};
        Bounds snowBounds=new Bounds();
        TerrainPlantOffsets.start();
        TerrainPlantOffsets.States sampler=states::get;
        // Classify a halo for culling, but assign faces to their solid source
        // block. Otherwise an empty adjacent section can swallow boundary faces.
        for(int z=-1;z<=16;z++) for(int y=-1;y<=16;y++) for(int x=-1;x<=16;x++) {
            var state=states.get(origin.getX()+x,origin.getY()+y,origin.getZ()+z);
            if(enclosed&&(!state.isSolidRender()||!TerrainConfig.volume(state))) enclosed=false;
            if(x<0||y<0||z<0) continue;
            if(x<16&&y<16&&z<16) TerrainPlantOffsets.collect(origin.getX()+x,origin.getY()+y,origin.getZ()+z,state,sampler);
            if(x<16&&y==0&&z<16&&state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock&&TerrainPlantOffsets.replacesSnow(new BlockPos(origin.getX()+x,origin.getY(),origin.getZ()+z))) snowBounds.include(x,-1,z);
            if(x<16&&y<16&&z<16&&TerrainConfig.smooth(state)) groups[TerrainConfig.renderGroup(state)].include(x,y,z);
        }
        // An opaque section plus a full one-block halo has no exposed surface.
        // Keep vanilla visibility culling and skip the subdivision entirely.
        if(enclosed) { TerrainPlantOffsets.enclose();return; }
        Map<SurfaceMesh.C,List<BlockStateModelPart>> partsCache=new HashMap<>();
        Map<BakedQuad,Template> templateCache=new IdentityHashMap<>();
        BlockModelLighter lighter=new BlockModelLighter();
        var fluidModels=net.minecraft.client.Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        RandomSource random=RandomSource.create(0);
        FaceLayers current=new FaceLayers();
        WorldUV.Sink emit=(face,projection,tileA,tileB)->{
            if(!face.source().equals(current.source)||face.axis()!=current.axis||face.sign()!=current.sign) {
                current.source=face.source();current.axis=face.axis();current.sign=face.sign();
                var src=face.source();BlockPos pos=new BlockPos(src.x(),src.y(),src.z());
                BlockState state=states.get(src.x(),src.y(),src.z());
                if(!TerrainConfig.smooth(state)&&!(TerrainConfig.appearance().snow()&&state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock)) { current.layers=List.of();return; }
                if(state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock) state=net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
                final BlockState textureState=state;
                Direction direction=switch(face.axis()) {
                    case 0 -> face.sign()>0?Direction.EAST:Direction.WEST;
                    case 1 -> face.sign()>0?Direction.UP:Direction.DOWN;
                    default -> face.sign()>0?Direction.SOUTH:Direction.NORTH;
                };
                List<BlockStateModelPart> parts=partsCache.computeIfAbsent(src,ignored->{
                    List<BlockStateModelPart> result=new ArrayList<>();random.setSeed(textureState.getSeed(pos));
                    models.get(textureState).collectParts(random,result);return result;
                });
                var outside=face.outside();BlockPos lightPos=new BlockPos(outside.x(),outside.y(),outside.z());
                // Snow skirt positions follow a curved surface, so the original
                // neighbouring cube may contain the nominal side sample.
                if(states.get(src.x(),src.y(),src.z()).getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock&&world.getBlockState(lightPos).isSolidRender()) lightPos=pos.above();
                int light=lighter.getLightCoords(world.getBlockState(lightPos),world,lightPos);
                List<Layer> result=new ArrayList<>();
                for(var part:parts) {
                    boolean grassSide=TerrainConfig.appearance().grass()&&state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)&&face.axis()!=1
                        &&!state.getValue(net.minecraft.world.level.block.GrassBlock.SNOWY);
                    Direction textureDirection=grassSide?Direction.UP:direction;
                    List<BakedQuad> quads=new ArrayList<>(part.getQuads(textureDirection));
                    for(var quad:part.getQuads(null)) if(quad.direction()==textureDirection) quads.add(quad);
                    for(var quad:quads) {
                        var material=quad.materialInfo();
                        Template template=templateCache.computeIfAbsent(quad,q->new Template(q,mapping(q,grassSide?1:face.axis(),grassSide?1:face.sign())));
                        var tintSource=material.tintIndex()<0?null:colors.getTintSource(state,material.tintIndex());
                        // Biome tints are RGB, not opacity. Texture alpha remains
                        // in the original sprite; terrain vertex colors stay opaque.
                        int tint=tintSource==null?0xffffffff:ARGB.opaque(tintSource.colorInWorld(state,world,pos));
                        int blockLight=Math.max(light&0xffff,material.lightEmission()<<4);
                        ChunkSectionLayer layer=TerrainConfig.leaves(state)&&!cutoutLeaves?ChunkSectionLayer.SOLID:material.layer();
                        result.add(new Layer(template,buffers.apply(layer),tint,(light&0xffff0000)|blockLight,states.get(src.x(),src.y(),src.z())));
                    }
                }
                current.layers=result;
            }
            for(Layer layer:current.layers) {
                try {
                shaders.begin(layer.buffer,layer.state,face.source(),false);
                for(int i=0;i<4;i++) {
                var point=face.point(i);var p=point.position();var n=point.normal();
                float shade=(float)(0.72+0.2*n.y()+0.08*n.x()-0.04*n.z());
                int color=ARGB.scaleRGB(layer.tint,Math.clamp(shade,0.45f,1f));
                layer.buffer.addVertex((float)(p.x()-origin.getX()),(float)(p.y()-origin.getY()),(float)(p.z()-origin.getZ()),
                    color,layer.template.uv.u(WorldUV.s(p,projection,face.sign(),tileA,tileB),WorldUV.t(p,projection,face.sign(),tileA,tileB)),
                    layer.template.uv.v(WorldUV.s(p,projection,face.sign(),tileA,tileB),WorldUV.t(p,projection,face.sign(),tileA,tileB)),0,layer.light,
                    (float)n.x(),(float)n.y(),(float)n.z());
                }
                } finally {shaders.end(layer.buffer);}
            }
        };
        // Separate opaque, leaf and other transparent surfaces. Each transparent
        // group sees opaque volumes but cannot erase terrain behind its texture.
        for(int group=0;group<groups.length;group++) if(groups[group].present()) {
            int currentGroup=group;
            bake(states,origin,group,groups[group],face->{
                if(currentGroup==0) TerrainPlantOffsets.accept(face);
                WorldUV.emit(face,emit);
                emitSnow(face,states,emit);
                if(currentGroup!=1&&TerrainConfig.appearance().shores()) {
                    var src=face.source();SurfaceMesh.C wet=face.axis()!=1?face.outside():null;
                    if(face.axis()==1&&face.sign()==1) for(Direction direction:Direction.Plane.HORIZONTAL) {
                        var p=new SurfaceMesh.C(src.x()+direction.getStepX(),src.y(),src.z()+direction.getStepZ());
                        if(states.get(p.x(),p.y(),p.z()).getFluidState().isSource()) {wet=p;break;}
                    }
                    if(wet!=null) {
                        var o=wet;var state=states.get(o.x(),o.y(),o.z());var fluid=state.getFluidState();
                        if(fluid.isSource()&&states.get(o.x(),o.y()+1,o.z()).getFluidState().isEmpty()) {
                            BlockPos pos=new BlockPos(o.x(),o.y(),o.z());double height=o.y()+fluid.getHeight(world,pos)-0.001;
                            var model=fluidModels.get(fluid);
                            var sprite=model.stillMaterial().sprite();var buffer=buffers.apply(model.layer());
                            int tint=ARGB.scaleRGB(model.tintSource()==null?0xffffffff:ARGB.opaque(model.tintSource().colorInWorld(state,world,pos)),world.cardinalLighting().up());
                            int sampledLight=lighter.getLightCoords(state,world,pos.above());
                            int light=(sampledLight&0xffff0000)|Math.max(sampledLight&0xffff,state.getLightEmission()<<4);
                            Consumer<SurfaceMesh.Quad> water=q->WorldUV.emit(q,(tile,axis,a,b)->{
                                try {
                                shaders.begin(buffer,fluid.createLegacyBlock(),o,true);
                                for(int i=0;i<4;i++) {var p=tile.point(i).position();buffer.addVertex((float)(p.x()-origin.getX()),(float)(p.y()-origin.getY()),(float)(p.z()-origin.getZ()),
                                    tint,sprite.getU((float)WorldUV.s(p,axis,1,a,b)),sprite.getV((float)WorldUV.t(p,axis,1,a,b)),0,light,0,1,0);}
                                } finally {shaders.end(buffer);}
                            });
                            if(face.axis()==1) TerrainCovers.submergedTop(face,height,water);else TerrainCovers.shore(face,height,water,
                                side->states.get(src.x(),src.y(),src.z()+side).getFluidState().isSource());
                        }
                    }
                }
            });
        }
        if(snowBounds.present()) {
            for(int group=0;group<3;group++) {
            int snowGroup=group;
            new SurfaceMesh((x,y,z)->states.volume(x,y,z,snowGroup),TerrainConfig.bakeRounding(),(x,y,z)->states.anchor(x,y,z,snowGroup))
                .generateBox(origin.getX()+snowBounds.minX,origin.getY()+snowBounds.minY,origin.getZ()+snowBounds.minZ,
                    snowBounds.maxX-snowBounds.minX+1,snowBounds.maxY-snowBounds.minY+1,snowBounds.maxZ-snowBounds.minZ+1,2,
                    src->TerrainConfig.renderGroup(states.get(src.x(),src.y(),src.z()))==snowGroup&&TerrainPlantOffsets.replacesSnow(new BlockPos(src.x(),src.y()+1,src.z())),q->emitSnow(q,states,emit));
            }
        }
        TerrainPlantOffsets.finish(sampler);
    }
    private static void emitSnow(SurfaceMesh.Quad q,SectionStates states,WorldUV.Sink emit) {
        if(q.axis()!=1||q.sign()!=1) return;
        var src=q.source();var snowState=states.get(src.x(),src.y()+1,src.z());
        if(!(snowState.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock)||!TerrainPlantOffsets.replacesSnow(new BlockPos(src.x(),src.y()+1,src.z()))) return;
        double h=snowHeight(snowState);
        TerrainCovers.snow(q,h,cover->WorldUV.emit(cover,emit),side->snowHeight(states.get(src.x()+(side==2?-1:side==3?1:0),src.y()+1,src.z()+(side==0?-1:side==1?1:0))));
    }
    private static double snowHeight(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock?state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)/8.0:0;
    }
    private static void bake(SectionStates states,BlockPos origin,int group,Bounds bounds,Consumer<SurfaceMesh.Quad> emit) {
        new SurfaceMesh((x,y,z)->states.volume(x,y,z,group),TerrainConfig.bakeRounding(),
            (x,y,z)->states.anchor(x,y,z,group))
            .generateBox(origin.getX()+bounds.minX,origin.getY()+bounds.minY,origin.getZ()+bounds.minZ,
                bounds.maxX-bounds.minX+1,bounds.maxY-bounds.minY+1,bounds.maxZ-bounds.minZ+1,
                source->{
                    if(source.x()<origin.getX()||source.x()>=origin.getX()+16||source.y()<origin.getY()||source.y()>=origin.getY()+16||source.z()<origin.getZ()||source.z()>=origin.getZ()+16) return false;
                    var state=states.get(source.x(),source.y(),source.z());return TerrainConfig.smooth(state)&&TerrainConfig.renderGroup(state)==group;
                },emit);
    }
    // Both passes share state reads; no BlockPos allocation per sample.
    private static final class SectionStates {
        final BlockAndTintGetter world;
        final int x,y,z;
        final BlockState[] values=new BlockState[26*26*26];
        // Immutable BlockState classifications are reusable only within this bake's
        // config/tag snapshot. No global cache or retained world reference.
        final Map<BlockState,Classification> classifications=new IdentityHashMap<>();
        private record Classification(int volumes,int anchors) {}
        final BlockPos.MutableBlockPos scratch=new BlockPos.MutableBlockPos();
        SectionStates(BlockAndTintGetter world,BlockPos origin) {
            this.world=world;x=origin.getX()-5;y=origin.getY()-5;z=origin.getZ()-5;
        }
        private Classification classify(BlockState state) {
            return classifications.computeIfAbsent(state,key->{
                int volumes=0,anchors=0;
                for(int group=0;group<3;group++) {
                    if(TerrainConfig.renderVolume(key,group))volumes|=1<<group;
                    if(TerrainConfig.renderAnchor(key,group))anchors|=1<<group;
                }
                return new Classification(volumes,anchors);
            });
        }
        boolean volume(int x,int y,int z,int group) {return (classify(get(x,y,z)).volumes&(1<<group))!=0;}
        boolean anchor(int x,int y,int z,int group) {return (classify(get(x,y,z)).anchors&(1<<group))!=0;}
        BlockState get(int x,int y,int z) {
            int dx=x-this.x,dy=y-this.y,dz=z-this.z;
            if(dx<0||dy<0||dz<0||dx>=26||dy>=26||dz>=26) return world.getBlockState(scratch.set(x,y,z));
            int index=(dz*26+dy)*26+dx;
            BlockState state=values[index];
            if(state==null) values[index]=state=world.getBlockState(scratch.set(x,y,z));
            return state;
        }
    }
}
