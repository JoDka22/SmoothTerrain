package de.jorda.smoothterrain;

import java.util.function.Consumer;

/** Heap-only transition geometry; liquids themselves retain their vanilla mesh. */
final class TerrainCovers {
    /** Snow on an upward-bent side is a material replacement, not a coplanar
     * overlay. Keep both original triangles, including their normals and UVs.
     * Vertical cliffs and undersides retain their original block material. */
    static void snowySide(SurfaceMesh.Quad q,Consumer<SurfaceMesh.Quad> sink) {
        if(q.axis()==1) {sink.accept(q);return;}
        boolean first=upward(q.a(),q.b(),q.c()),second=upward(q.a(),q.c(),q.d());
        if(first==second) {sink.accept(first?snowMaterial(q):q);return;}
        var a=new SurfaceMesh.Quad(q.a(),q.b(),q.c(),q.c(),q.source(),q.axis(),q.sign());
        var b=new SurfaceMesh.Quad(q.a(),q.c(),q.d(),q.d(),q.source(),q.axis(),q.sign());
        sink.accept(first?snowMaterial(a):a);sink.accept(second?snowMaterial(b):b);
    }
    private static boolean upward(SurfaceMesh.Point a,SurfaceMesh.Point b,SurfaceMesh.Point c) {
        return b.position().minus(a.position()).cross(c.position().minus(a.position())).y()>1e-12;
    }
    private static SurfaceMesh.Quad snowMaterial(SurfaceMesh.Quad q) {
        var s=q.source();return new SurfaceMesh.Quad(q.a(),q.b(),q.c(),q.d(),new SurfaceMesh.C(s.x(),s.y()+1,s.z()),q.axis(),q.sign());
    }
    private static SurfaceMesh.Point point(SurfaceMesh.Point p,SurfaceMesh.V v) { return new SurfaceMesh.Point(v,p.normal(),p.u(),p.v()); }
    static void snow(SurfaceMesh.Quad q,double height,Consumer<SurfaceMesh.Quad> sink,java.util.function.IntToDoubleFunction neighbor) {
        if(q.axis()!=1||q.sign()!=1) return;
        var src=q.source();var snow=new SurfaceMesh.C(src.x(),src.y()+1,src.z());
        SurfaceMesh.Point[] raised=new SurfaceMesh.Point[4];
        for(int i=0;i<4;i++) raised[i]=point(q.point(i),q.point(i).position().plus(new SurfaceMesh.V(0,height,0)));
        sink.accept(new SurfaceMesh.Quad(raised[0],raised[1],raised[2],raised[3],snow,1,1));
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;var a=q.point(i);var b=q.point(j);
            int side=a.u()==0&&b.u()==0?0:a.u()==1&&b.u()==1?1:a.v()==0&&b.v()==0?2:a.v()==1&&b.v()==1?3:-1;
            if(side<0) continue;
            double lower=neighbor.applyAsDouble(side);if(lower>=height) continue;
            var lowA=point(a,a.position().plus(new SurfaceMesh.V(0,lower,0)));var lowB=point(b,b.position().plus(new SurfaceMesh.V(0,lower,0)));
            int axis=side<2?2:0,sign=side%2==0?-1:1;
            sink.accept(new SurfaceMesh.Quad(raised[j],raised[i],lowA,lowB,snow,axis,sign));
        }
    }
    static void shore(SurfaceMesh.Quad q,double fluidHeight,Consumer<SurfaceMesh.Quad> sink) {
        shore(q,fluidHeight,sink,side->false);
    }
    static void shore(SurfaceMesh.Quad q,double fluidHeight,Consumer<SurfaceMesh.Quad> sink,java.util.function.IntPredicate wetCorner) {
        if(q.axis()==1) return;
        var src=q.source();
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;var a=q.point(i);var b=q.point(j);
            double ay=q.axis()==0?(q.sign()>0?a.u():a.v()):(q.sign()>0?a.v():a.u());
            double by=q.axis()==0?(q.sign()>0?b.u():b.v()):(q.sign()>0?b.v():b.u());
            if(ay!=1||by!=1) continue;
            var p=a.position();var r=b.position();double boundary=(q.axis()==0?src.x():src.z())+(q.sign()>0?1:0);
            var pa=new SurfaceMesh.V(q.axis()==0?boundary:p.x(),fluidHeight,q.axis()==2?boundary:p.z());
            var pb=new SurfaceMesh.V(q.axis()==0?boundary:r.x(),fluidHeight,q.axis()==2?boundary:r.z());
            flat(pa,pb,new SurfaceMesh.V(r.x(),fluidHeight,r.z()),new SurfaceMesh.V(p.x(),fluidHeight,p.z()),q,sink);
            if(q.axis()==0) for(var corner:java.util.List.of(a,b)) {
                double v=q.sign()>0?corner.v():corner.u();
                if(v!=0&&v!=1||!wetCorner.test(v==0?-1:1)) continue;
                var target=corner.position();double z=src.z()+v;
                flat(new SurfaceMesh.V(boundary,fluidHeight,z),new SurfaceMesh.V(boundary,fluidHeight,target.z()),
                    new SurfaceMesh.V(target.x(),fluidHeight,target.z()),new SurfaceMesh.V(target.x(),fluidHeight,z),q,sink);
            }
        }
    }
    static void submergedTop(SurfaceMesh.Quad q,double height,Consumer<SurfaceMesh.Quad> sink) {
        if(q.axis()!=1||q.sign()!=1) return;
        for(int tri=0;tri<2;tri++) {
            var input=tri==0?java.util.List.of(q.a().position(),q.b().position(),q.c().position()):java.util.List.of(q.a().position(),q.c().position(),q.d().position());
            var out=new java.util.ArrayList<SurfaceMesh.V>(4);var previous=input.getLast();
            for(var p:input) {
                if((previous.y()<=height)!=(p.y()<=height)) out.add(previous.plus(p.minus(previous).times((height-previous.y())/(p.y()-previous.y()))));
                if(p.y()<=height) out.add(p);previous=p;
            }
            for(int i=1;i+1<out.size();i++) {
                var a=out.get(0);var b=out.get(i);var c=out.get(i+1);
                flat(new SurfaceMesh.V(a.x(),height,a.z()),new SurfaceMesh.V(b.x(),height,b.z()),new SurfaceMesh.V(c.x(),height,c.z()),new SurfaceMesh.V(c.x(),height,c.z()),q,sink);
            }
        }
    }
    private static void flat(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,SurfaceMesh.V d,SurfaceMesh.Quad q,Consumer<SurfaceMesh.Quad> sink) {
        var src=q.source();
        if(inside(a,src)&&inside(b,src)&&inside(c,src)&&inside(d,src)) {flatRaw(a,b,c,d,q,sink);return;}
        // The original fluid already covers neighbouring wet cells. Emit only
        // in the former solid cell, preventing coplanar translucent overlap.
        var polygon=java.util.List.of(a,b,c,d);
        polygon=clip(polygon,0,src.x(),true);polygon=clip(polygon,0,src.x()+1,false);
        polygon=clip(polygon,2,src.z(),true);polygon=clip(polygon,2,src.z()+1,false);
        for(int i=1;i+1<polygon.size();i++)flatRaw(polygon.getFirst(),polygon.get(i),polygon.get(i+1),polygon.get(i+1),q,sink);
    }
    private static boolean inside(SurfaceMesh.V p,SurfaceMesh.C src) {
        return p.x()>=src.x()&&p.x()<=src.x()+1&&p.z()>=src.z()&&p.z()<=src.z()+1;
    }
    private static java.util.List<SurfaceMesh.V> clip(java.util.List<SurfaceMesh.V> input,int axis,double boundary,boolean above) {
        if(input.isEmpty())return input;
        var output=new java.util.ArrayList<SurfaceMesh.V>();var previous=input.getLast();
        double old=(axis==0?previous.x():previous.z())-boundary;
        for(var point:input) {
            double value=(axis==0?point.x():point.z())-boundary;
            boolean before=above?old>=0:old<=0,after=above?value>=0:value<=0;
            if(before!=after)output.add(previous.plus(point.minus(previous).times(old/(old-value))));
            if(after)output.add(point);
            previous=point;old=value;
        }
        return output;
    }
    private static void flatRaw(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,SurfaceMesh.V d,SurfaceMesh.Quad q,Consumer<SurfaceMesh.Quad> sink) {
        double cross=b.minus(a).cross(c.minus(a)).y();if(Math.abs(cross)<1e-12) return;
        if(cross<0) {var swap=b;b=d;d=swap;}
        var normal=new SurfaceMesh.V(0,1,0);
        sink.accept(new SurfaceMesh.Quad(new SurfaceMesh.Point(a,normal,0,0),new SurfaceMesh.Point(b,normal,0,0),new SurfaceMesh.Point(c,normal,0,0),new SurfaceMesh.Point(d,normal,0,0),q.source(),1,1));
    }
}
