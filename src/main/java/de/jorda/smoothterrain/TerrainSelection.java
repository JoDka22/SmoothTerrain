package de.jorda.smoothterrain;

import java.util.*;
import java.lang.ref.WeakReference;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public final class TerrainSelection {
    private record Edge(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V direction) {
        Edge(SurfaceMesh.V a,SurfaceMesh.V b){this(a,b,b.minus(a).unit());}
    }
    private static BlockPos cachedPos;
    private static WeakReference<Object> cachedWorld=new WeakReference<>(null);
    private static long cachedRevision=-1;
    private static long cachedConfigRevision=-1;
    private static double cachedRounding=-1;
    private static List<Edge> outline=List.of();

    public static boolean submit(PoseStack pose,SubmitNodeCollector collector,RenderType type,BlockPos pos,int color,float width) {
        var world=Minecraft.getInstance().level;
        if(world==null||!TerrainConfig.enabled||TerrainConfig.rounding==0||!TerrainConfig.smooth(world.getBlockState(pos))) return false;
        List<Edge> snapshot=outline(world,pos);
        collector.submitCustomGeometry(pose,type,(matrix,vertices)->{
            for(Edge edge:snapshot) {
                var direction=edge.direction;
                for(int i=0;i<2;i++) {
                    var p=i==0?edge.a:edge.b;
                    vertices.addVertex(matrix,(float)(p.x()-pos.getX()),(float)(p.y()-pos.getY()),(float)(p.z()-pos.getZ()))
                        .setColor(color).setNormal(matrix,(float)direction.x(),(float)direction.y(),(float)direction.z()).setLineWidth(width);
                }
            }
        });
        return true;
    }
    static List<Edge> outline(BlockGetter world,BlockPos pos) {
        try(var scope=TerrainConfig.openBake()) {return outlineScoped(world,pos);}
    }
    private static List<Edge> outlineScoped(BlockGetter world,BlockPos pos) {
        long revision=TerrainCollision.localRevision(world,pos),configRevision=TerrainConfig.bakeFilterRevision();
        if(!pos.equals(cachedPos)||world!=cachedWorld.get()||revision!=cachedRevision
            ||configRevision!=cachedConfigRevision||cachedRounding!=TerrainConfig.bakeRounding()) {
            Map<Edge,Integer> edges=new LinkedHashMap<>();
            BlockPos.MutableBlockPos sample=new BlockPos.MutableBlockPos();
            int group=TerrainConfig.renderGroup(world.getBlockState(pos));
            new SurfaceMesh((x,y,z)->TerrainConfig.renderVolume(world.getBlockState(sample.set(x,y,z)),group),TerrainConfig.bakeRounding(),(x,y,z)->TerrainConfig.renderAnchor(world.getBlockState(sample.set(x,y,z)),group),group!=1)
                .generateBox(pos.getX()-1,pos.getY()-1,pos.getZ()-1,2,2,2,
                    source->source.x()==pos.getX()&&source.y()==pos.getY()&&source.z()==pos.getZ(),q->{
                    var pts=q.points();for(int i=0;i<4;i++) {
                        var a=pts[i].position();var b=pts[(i+1)%4].position();
                        if(compare(a,b)>0) { var swap=a;a=b;b=swap; }
                        edges.merge(new Edge(a,b),1,Integer::sum);
                    }
                });
            outline=edges.entrySet().stream().filter(e->e.getValue()==1).map(Map.Entry::getKey).toList();
            // A detached single block has a closed surface without boundary edges.
            if(outline.isEmpty()) outline=List.copyOf(edges.keySet());
            cachedPos=pos.immutable();cachedWorld=new WeakReference<>(world);cachedRevision=revision;
            cachedConfigRevision=configRevision;cachedRounding=TerrainConfig.bakeRounding();
        }
        return outline;
    }
    private static int compare(SurfaceMesh.V a,SurfaceMesh.V b) {
        int x=Double.compare(a.x(),b.x());if(x!=0)return x;
        int y=Double.compare(a.y(),b.y());return y!=0?y:Double.compare(a.z(),b.z());
    }
}
