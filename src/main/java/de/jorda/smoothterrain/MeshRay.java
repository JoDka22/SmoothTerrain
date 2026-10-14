package de.jorda.smoothterrain;

import java.util.*;

/** Vertical ray intersections used for both collision rasterization and geometry checks. */
public final class MeshRay {
    public record Hit(double y,boolean exiting) {}
    public record Interval(double low,double high) {}
    public static double height(SurfaceMesh.Quad q,double x,double z) {
        double first=height(q.a().position(),q.b().position(),q.c().position(),x,z);
        double second=height(q.a().position(),q.c().position(),q.d().position(),x,z);
        return Double.isNaN(first)?second:Double.isNaN(second)?first:Math.max(first,second);
    }
    private static double height(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,double x,double z) {
        double denominator=(b.z()-c.z())*(a.x()-c.x())+(c.x()-b.x())*(a.z()-c.z());
        if(Math.abs(denominator)<1e-12) return Double.NaN;
        double u=((b.z()-c.z())*(x-c.x())+(c.x()-b.x())*(z-c.z()))/denominator;
        double v=((c.z()-a.z())*(x-c.x())+(a.x()-c.x())*(z-c.z()))/denominator;
        double w=1-u-v;
        return u<-1e-8||v<-1e-8||w<-1e-8?Double.NaN:u*a.y()+v*b.y()+w*c.y();
    }
    public static List<Interval> solidIntervals(List<SurfaceMesh.Quad> mesh,double x,double z,
            double low,double high,boolean solidAtBottom) {
        List<Hit> hits=new ArrayList<>();
        for(var q:mesh) {
            triangle(q.a().position(),q.b().position(),q.c().position(),x,z,hits);
            triangle(q.a().position(),q.c().position(),q.d().position(),x,z,hits);
        }
        return intervals(hits,low,high,solidAtBottom);
    }
    private static List<Interval> intervals(List<Hit> hits,double low,double high,boolean solidAtBottom) {
        hits.sort(Comparator.comparingDouble(Hit::y));
        List<Interval> result=new ArrayList<>();
        boolean inside=hits.isEmpty()?solidAtBottom:hits.getFirst().exiting;
        double start=low,previous=-Double.MAX_VALUE;
        for(Hit hit:hits) {
            if(Math.abs(hit.y-previous)<1e-6) continue;
            previous=hit.y;
            if(hit.exiting) {
                if(inside&&hit.y>low&&start<high) result.add(new Interval(Math.max(start,low),Math.min(hit.y,high)));
                inside=false;
            } else { inside=true;start=hit.y; }
        }
        if(inside&&start<high) result.add(new Interval(Math.max(start,low),high));
        result.removeIf(i->i.high<=i.low+1e-6);
        return result;
    }
    /** Bake-local ray buckets; projected coefficients are calculated once per triangle. */
    public static final class Columns {
        private record Triangle(double cx,double cz,double a,double b,double c,double d,double denominator,
                                double ay,double by,double cy,boolean exiting) {
            void intersect(double x,double z,List<Hit> hits) {
                double u=(a*(x-cx)+b*(z-cz))/denominator;
                double v=(c*(x-cx)+d*(z-cz))/denominator;
                double w=1-u-v;
                if(u<-1e-8||v<-1e-8||w<-1e-8)return;
                hits.add(new Hit(u*ay+v*by+w*cy,exiting));
            }
        }
        private final List<Triangle>[] buckets;
        private final int resolution;
        private final double originX,originZ;
        @SuppressWarnings("unchecked")
        public Columns(List<SurfaceMesh.Quad> mesh,double x,double z,int resolution) {
            if(resolution<=0)throw new IllegalArgumentException("Positive column resolution required");
            this.resolution=resolution;originX=x;originZ=z;
            buckets=(List<Triangle>[])new List<?>[resolution*resolution];
            for(var q:mesh) {
                add(q.a().position(),q.b().position(),q.c().position());
                add(q.a().position(),q.c().position(),q.d().position());
            }
        }
        private void add(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c) {
            double denominator=(b.z()-c.z())*(a.x()-c.x())+(c.x()-b.x())*(a.z()-c.z());
            if(Math.abs(denominator)<1e-12)return;
            double minX=Math.min(a.x(),Math.min(b.x(),c.x()))-1e-8,maxX=Math.max(a.x(),Math.max(b.x(),c.x()))+1e-8;
            double minZ=Math.min(a.z(),Math.min(b.z(),c.z()))-1e-8,maxZ=Math.max(a.z(),Math.max(b.z(),c.z()))+1e-8;
            int loX=Math.clamp((int)Math.ceil((minX-originX)*resolution-.5),0,resolution);
            int hiX=Math.clamp((int)Math.floor((maxX-originX)*resolution-.5),-1,resolution-1);
            int loZ=Math.clamp((int)Math.ceil((minZ-originZ)*resolution-.5),0,resolution);
            int hiZ=Math.clamp((int)Math.floor((maxZ-originZ)*resolution-.5),-1,resolution-1);
            if(loX>hiX||loZ>hiZ)return;
            boolean exiting=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z())>0;
            var triangle=new Triangle(c.x(),c.z(),b.z()-c.z(),c.x()-b.x(),c.z()-a.z(),a.x()-c.x(),denominator,a.y(),b.y(),c.y(),exiting);
            for(int z=loZ;z<=hiZ;z++)for(int x=loX;x<=hiX;x++) {
                int index=z*resolution+x;
                if(buckets[index]==null)buckets[index]=new ArrayList<>();
                buckets[index].add(triangle);
            }
        }
        public List<Interval> intervals(int x,int z,double low,double high,boolean solidAtBottom) {
            if(x<0||z<0||x>=resolution||z>=resolution)throw new IndexOutOfBoundsException();
            var hits=new ArrayList<Hit>();var bucket=buckets[z*resolution+x];
            if(bucket!=null)for(var triangle:bucket)
                triangle.intersect(originX+(x+.5)/resolution,originZ+(z+.5)/resolution,hits);
            return MeshRay.intervals(hits,low,high,solidAtBottom);
        }
    }
    private static void triangle(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,double x,double z,List<Hit> hits) {
        if(x<Math.min(a.x(),Math.min(b.x(),c.x()))-1e-8||x>Math.max(a.x(),Math.max(b.x(),c.x()))+1e-8
            ||z<Math.min(a.z(),Math.min(b.z(),c.z()))-1e-8||z>Math.max(a.z(),Math.max(b.z(),c.z()))+1e-8) return;
        double denominator=(b.z()-c.z())*(a.x()-c.x())+(c.x()-b.x())*(a.z()-c.z());
        if(Math.abs(denominator)<1e-12) return;
        double u=((b.z()-c.z())*(x-c.x())+(c.x()-b.x())*(z-c.z()))/denominator;
        double v=((c.z()-a.z())*(x-c.x())+(a.x()-c.x())*(z-c.z()))/denominator;
        double w=1-u-v;
        if(u<-1e-8||v<-1e-8||w<-1e-8) return;
        double normalY=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
        hits.add(new Hit(u*a.y()+v*b.y()+w*c.y(),normalY>0));
    }
}
