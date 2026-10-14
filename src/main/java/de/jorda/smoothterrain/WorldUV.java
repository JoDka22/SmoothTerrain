package de.jorda.smoothterrain;

import java.util.ArrayList;
import java.util.List;

/** One texture tile per world block. Clip at tile borders before sampling the atlas. */
public final class WorldUV {
    @FunctionalInterface public interface Sink {void accept(SurfaceMesh.Quad quad,int axis,int tileA,int tileB);}
    private static double component(SurfaceMesh.V p,int axis) {return axis==0?p.x():axis==1?p.y():p.z();}
    public static double s(SurfaceMesh.V p,int axis,int sign,int a,int b) {
        return Math.clamp(sign>0?component(p,(axis+1)%3)-a:component(p,(axis+2)%3)-b,0,1);
    }
    public static double t(SurfaceMesh.V p,int axis,int sign,int a,int b) {
        return Math.clamp(sign>0?component(p,(axis+2)%3)-b:component(p,(axis+1)%3)-a,0,1);
    }
    public static void emit(SurfaceMesh.Quad face,Sink sink) {
        var p0=face.a().position();var p1=face.b().position();var p2=face.c().position();var p3=face.d().position();
        double x1=p1.x()-p0.x(),y1=p1.y()-p0.y(),z1=p1.z()-p0.z();
        double x2=p2.x()-p0.x(),y2=p2.y()-p0.y(),z2=p2.z()-p0.z();
        double x3=p3.x()-p0.x(),y3=p3.y()-p0.y(),z3=p3.z()-p0.z();
        double nx=Math.abs(y1*z2-z1*y2+y2*z3-z2*y3),ny=Math.abs(z1*x2-x1*z2+z2*x3-x2*z3),nz=Math.abs(x1*y2-y1*x2+x2*y3-y2*x3);
        int axis=face.axis();
        double max=axis==0?nx:axis==1?ny:nz;
        if(nx>max+1e-9) {axis=0;max=nx;}if(ny>max+1e-9) {axis=1;max=ny;}if(nz>max+1e-9) axis=2;
        int u=(axis+1)%3,v=(axis+2)%3;
        double minA=Double.POSITIVE_INFINITY,maxA=Double.NEGATIVE_INFINITY,minB=minA,maxB=maxA;
        for(int i=0;i<4;i++) {
            var p=face.point(i).position();double a=component(p,u),b=component(p,v);
            minA=Math.min(minA,a);maxA=Math.max(maxA,a);minB=Math.min(minB,b);maxB=Math.max(maxB,b);
        }
        int loA=(int)Math.floor(minA+1e-9),hiA=(int)Math.floor(maxA-1e-9);
        int loB=(int)Math.floor(minB+1e-9),hiB=(int)Math.floor(maxB-1e-9);
        if(hiA<loA||hiB<loB) return; // Zero projected area.
        if(loA==hiA&&loB==hiB) {sink.accept(face,axis,loA,loB);return;}
        // Preserve the renderer's original triangle diagonal and physical surface.
        for(int triangle=0;triangle<2;triangle++) {
            var input=triangle==0?List.of(face.a(),face.b(),face.c()):List.of(face.a(),face.c(),face.d());
            for(int a=loA;a<=hiA;a++) for(int b=loB;b<=hiB;b++) {
                List<SurfaceMesh.Point> polygon=clip(input,u,a,true);
                polygon=clip(polygon,u,a+1,false);polygon=clip(polygon,v,b,true);polygon=clip(polygon,v,b+1,false);
                if(polygon.size()<3) continue;
                for(int i=1;i<polygon.size()-1;i++) {
                    var p=polygon.get(0);var q=polygon.get(i);var r=polygon.get(i+1);
                    var cross=q.position().minus(p.position()).cross(r.position().minus(p.position()));
                    if(cross.dot(cross)<1e-20) continue;
                    var fourth=r;
                    if(i+2<polygon.size()) {
                        var next=polygon.get(i+2);
                        var nextCross=r.position().minus(p.position()).cross(next.position().minus(p.position()));
                        if(nextCross.dot(nextCross)>=1e-20) {fourth=next;i++;}
                    }
                    // Two adjacent fan triangles share the exact original diagonal.
                    // One quad needs four vertices instead of two padded triangles (8).
                    sink.accept(new SurfaceMesh.Quad(p,q,r,fourth,face.source(),face.axis(),face.sign()),axis,a,b);
                }
            }
        }
    }
    private static List<SurfaceMesh.Point> clip(List<SurfaceMesh.Point> input,int axis,double boundary,boolean above) {
        if(input.isEmpty()) return input;
        // Allocate only at the first crossing. Entirely accepted polygons borrow
        // the input; entirely rejected polygons need no temporary list either.
        List<SurfaceMesh.Point> out=null;
        var previous=input.getLast();double old=component(previous.position(),axis)-boundary;
        boolean inside=above?old>=0:old<=0;
        for(int i=0;i<input.size();i++) {
            var point=input.get(i);
            double distance=component(point.position(),axis)-boundary;
            boolean current=above?distance>=0:distance<=0;
            if(inside!=current) {
                if(out==null) {
                    out=new ArrayList<>(input.size()+1);
                    if(inside)for(int j=0;j<i;j++)out.add(input.get(j));
                }
                double weight=old/(old-distance);
                out.add(new SurfaceMesh.Point(previous.position().plus(point.position().minus(previous.position()).times(weight)),
                    previous.normal().plus(point.normal().minus(previous.normal()).times(weight)).unit(),0,0));
            }
            if(current&&out!=null) out.add(point);
            previous=point;old=distance;inside=current;
        }
        return out!=null?out:inside?input:List.of();
    }
}
