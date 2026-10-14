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
    }
}
