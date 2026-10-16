package de.jorda.smoothterrain;

import java.util.ArrayList;

/** Frozen 1.0.2 reference checks all generated positions, normals and UVs. */
final class MeshEquivalenceChecks {
    static void verify() {
        for(int seed=0;seed<12;seed++)for(double rounding:new double[]{0,.25,.5,.8,1}) {
            final int key=seed;
            SurfaceMesh.Field field=(x,y,z)->Math.floorMod(x*73856093^y*19349663^z*83492791^key,7)<3;
            SurfaceMesh.Field anchor=(x,y,z)->Math.floorMod(x+2*y+3*z+key,17)==0;
            var expected=new ArrayList<String>();var actual=new ArrayList<String>();
            new SurfaceMeshReference(field::solid,rounding,anchor::solid).generate(-3,-2,-4,5,q->expected.add(q.toString()));
            new SurfaceMesh(field,rounding,anchor).generate(-3,-2,-4,5,q->actual.add(q.toString()));
            if(!expected.equals(actual))throw new AssertionError("Mesh differs from 1.0.2: seed="+seed+" rounding="+rounding);
        }
        System.out.println("PASS: raw mesher output exactly matches frozen 1.0.2 across 60 random anchored fields/rounding cases.");
        for(int seed=0;seed<3;seed++)for(double rounding:new double[]{0,.25,.5,.8,1}) {
            final int key=seed;
            SurfaceMesh.Field field=(x,y,z)->Math.floorMod(x*73856093^y*19349663^z*83492791^key,7)<3;
            SurfaceMesh.Field anchor=(x,y,z)->Math.floorMod(x+2*y+3*z+key,17)==0;
            var reference=new SurfaceMeshReference(field::solid,rounding,anchor::solid);
            var mesh=new SurfaceMesh(field,rounding,anchor);
            for(int[] box:new int[][]{{3,5,3,7},{17,2,17,2},{2,17,17,7},{17,17,2,7}}) {
                var expected=new ArrayList<String>();var actual=new ArrayList<String>();
                reference.generateBox(-17,-3,-9,box[0],box[1],box[2],box[3],source->true,q->expected.add(q.toString()));
                mesh.generateBox(-17,-3,-9,box[0],box[1],box[2],box[3],source->true,q->actual.add(q.toString()));
                if(!expected.equals(actual))throw new AssertionError("Rectangular/reused mesh differs: seed="+seed+" rounding="+rounding+" size="+java.util.Arrays.toString(box));
            }
        }
        System.out.println("PASS: 60 rectangular/reused collision and thin-section mesh cases match the frozen reference exactly, including negative coordinates and all rounding levels.");
    }
}
