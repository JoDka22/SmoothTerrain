package de.jorda.smoothterrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Compare every emitted vertex and tile to the frozen release implementation. */
public final class UVEquivalenceChecks {
    private record Output(SurfaceMesh.Point p,SurfaceMesh.Point q,SurfaceMesh.Point r,SurfaceMesh.C source,int faceAxis,int sign,int axis,int a,int b) {}
    static void verify() {
        var random=new Random(0x102);
        for(int sample=0;sample<12000;sample++) {
            int axis=sample%3,sign=sample%2==0?1:-1;
            var points=new SurfaceMesh.Point[4];
            double x=random.nextInt(20)-10,y=random.nextInt(20)-10,z=random.nextInt(20)-10;
            for(int i=0;i<4;i++) {
                // Includes exact tile boundaries, nearly-boundary thin triangles,
                // negative coordinates, nonplanar faces and interpolated normals.
                double dx=offset(random,sample),dy=offset(random,sample),dz=offset(random,sample);
                var n=new SurfaceMesh.V(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).unit();
                points[i]=new SurfaceMesh.Point(new SurfaceMesh.V(x+dx,y+dy,z+dz),n,i%2,i/2);
            }
            var face=new SurfaceMesh.Quad(points[0],points[1],points[2],points[3],new SurfaceMesh.C((int)x,(int)y,(int)z),axis,sign);
            compare(face);
        }
        SurfaceMesh.Field hill=(x,y,z)->y<5+Math.floorMod(x,7)/2+Math.floorMod(z,9)/3;
        for(double rounding:new double[]{0,.25,.5,.8,1})
            new SurfaceMesh(hill,rounding).generate(-8,0,-8,16,UVEquivalenceChecks::compare);
        System.out.println("PASS: visible UV triangle output exactly matches frozen 1.0.1 for 12000 boundary/random faces and terrain at 0/25/50/80/100 percent.");
    }
    private static double offset(Random random,int sample) {
        return switch(sample%4) {
            case 0->random.nextInt(4)-1;
            case 1->(random.nextInt(3)-1)*1e-10;
            default->random.nextDouble()*3-1;
        };
    }
    private static void compare(SurfaceMesh.Quad face) {
        List<Output> expected=new ArrayList<>(),actual=new ArrayList<>();
        WorldUVReference.emit(face,(q,axis,a,b)->triangles(expected,q,axis,a,b));
        WorldUV.emit(face,(q,axis,a,b)->triangles(actual,q,axis,a,b));
        if(!expected.equals(actual))throw new AssertionError("UV optimization changed emitted geometry/tile order: "+face);
    }
    private static void triangles(List<Output> out,SurfaceMesh.Quad quad,int axis,int a,int b) {
        for(int i=1;i<3;i++) {
            var p=quad.a();var q=quad.point(i);var r=quad.point(i+1);
            var cross=q.position().minus(p.position()).cross(r.position().minus(p.position()));
            if(cross.dot(cross)<1e-20)continue;
            out.add(new Output(p,q,r,quad.source(),quad.axis(),quad.sign(),axis,a,b));
        }
    }
}
