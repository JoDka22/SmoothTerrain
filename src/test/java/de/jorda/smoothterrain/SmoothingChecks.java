package de.jorda.smoothterrain;
import java.util.*;
public final class SmoothingChecks {
    private static void verifyRayTangencies() {
        var random=new Random(201001);
        var tangent=List.of(new MeshRay.Hit(1.5,false),new MeshRay.Hit(1.5,true),new MeshRay.Hit(1.5,false),new MeshRay.Hit(1.5,true));
        for(int trial=0;trial<200;trial++) {
            var hits=new ArrayList<>(tangent);Collections.shuffle(hits,random);
            check(MeshRay.intervals(hits,2,4,true).isEmpty(),"Tangent ridge created a phantom pillar above its mesh");
            hits=new ArrayList<>(tangent);hits.add(new MeshRay.Hit(1,false));hits.add(new MeshRay.Hit(2,true));Collections.shuffle(hits,random);
            check(MeshRay.intervals(hits,0,4,false).equals(List.of(new MeshRay.Interval(1,2))),"Internal tangent changed solid interval");
            hits=new ArrayList<>(List.of(new MeshRay.Hit(1,false),new MeshRay.Hit(1,false),new MeshRay.Hit(2,true),new MeshRay.Hit(2,true)));Collections.shuffle(hits,random);
            check(MeshRay.intervals(hits,0,4,false).equals(List.of(new MeshRay.Interval(1,2))),"Coplanar duplicate changed solid interval");
        }
        System.out.println("PASS: 600 deterministic ray-tangency/order checks; no phantom solid above pillar ridges.");
    }
    private static void verifyResourceOwnership() {
        int[] closed={0};
        var failure=new IllegalArgumentException("Injected mesh failure");
        try(var resources=new RenderResources()) {
            resources.track((AutoCloseable)()->closed[0]++);
            resources.track((AutoCloseable)()->{closed[0]++;throw new IllegalStateException("Injected close failure");});
            resources.track((AutoCloseable)()->closed[0]++);
            throw failure;
        } catch(IllegalArgumentException caught) {
            check(caught==failure&&caught.getSuppressed().length==1,"Cleanup lost the original mesh exception");
        }
        check(closed[0]==3,"A failed close skipped remaining mesh cleanup");
        try(var resources=new RenderResources()) { resources.track((AutoCloseable)()->closed[0]++);resources.commit(); }
        check(closed[0]==3,"Successful compiler handoff prematurely closed caller-owned meshes");
        for(boolean commit:new boolean[]{false,true}) {
            var resources=new RenderResources();
            if(commit)resources.commit();else resources.close();
            int[] lateClosed={0};
            try {
                resources.track((AutoCloseable)()->lateClosed[0]++);
                throw new AssertionError("Inactive render ledger accepted a late mesh");
            } catch(IllegalStateException expected) {
                check(lateClosed[0]==1,"Rejected late mesh was not released immediately");
            } finally {resources.close();}
            check(lateClosed[0]==1,"Late mesh was released twice");
            try {resources.commit();throw new AssertionError("Closed render ledger accepted ownership handoff");}
            catch(IllegalStateException expected) {}
        }
    }
    private static void check(boolean c,String message) { if(!c) throw new AssertionError(message); }
    private static double area(SurfaceMesh.Quad q) {
        var a=q.b().position().minus(q.a().position()).cross(q.c().position().minus(q.a().position()));
        var b=q.c().position().minus(q.a().position()).cross(q.d().position().minus(q.a().position()));
        return (Math.sqrt(a.dot(a))+Math.sqrt(b.dot(b)))/2;
    }
    private static void verifyWorldUV() {
        MeshEquivalenceChecks.verify();
        UVEquivalenceChecks.verify();
        SurfaceMesh.Field hill=(x,y,z)->y<Math.floorMod(x*x+z*z,5)-2;
        for(double rounding:new double[]{0.25,0.8,1}) {
            var mesh=generate(hill,-4,-4,-4,8,rounding);
            double expected=mesh.stream().mapToDouble(SmoothingChecks::area).sum();double[] actual={0};
            for(var face:mesh) WorldUV.emit(face,(q,axis,a,b)->{
                actual[0]+=area(q);check(q.source().equals(face.source()),"UV clipping changed material ownership");
                for(int i=0;i<4;i++) {
                    var p=q.point(i).position();double s=WorldUV.s(p,axis,q.sign(),a,b),t=WorldUV.t(p,axis,q.sign(),a,b);
                    check(Double.isFinite(s)&&Double.isFinite(t)&&s>=0&&s<=1&&t>=0&&t<=1,"Projected UV escaped the atlas tile");
                }
            });
            check(Math.abs(actual[0]-expected)<1e-7,"World-space tiling changed the physical triangle surface");
        }
        var n=new SurfaceMesh.V(0,1,0);
        var q=new SurfaceMesh.Quad(new SurfaceMesh.Point(new SurfaceMesh.V(0.2,0,0.2),n,0,0),new SurfaceMesh.Point(new SurfaceMesh.V(0.2,0,0.6),n,1,0),new SurfaceMesh.Point(new SurfaceMesh.V(0.6,0,0.6),n,1,1),new SurfaceMesh.Point(new SurfaceMesh.V(0.6,0,0.2),n,0,1),new SurfaceMesh.C(0,0,0),1,1);
        WorldUV.emit(q,(face,axis,a,b)->check(Math.abs(WorldUV.s(face.b().position(),axis,1,a,b)-WorldUV.s(face.a().position(),axis,1,a,b)-0.4)<1e-9,"Shrunk geometry still stretches a full texture tile"));
        System.out.println("PASS: world-space UV density, negative coordinates, bounded atlas sampling and unchanged clipped triangle surfaces.");
    }
    private static List<SurfaceMesh.Quad> generate(SurfaceMesh.Field field,int x,int y,int z,int size) {
        return generate(field,x,y,z,size,1.0);
    }
    private static List<SurfaceMesh.Quad> generate(SurfaceMesh.Field field,int x,int y,int z,int size,double rounding) {
        List<SurfaceMesh.Quad> result=new ArrayList<>();new SurfaceMesh(field,rounding).generate(x,y,z,size,result::add);return result;
    }
    private static String point(SurfaceMesh.V p) { return String.format(Locale.ROOT,"%.8f/%.8f/%.8f",p.x(),p.y(),p.z()); }
    private static Map<String,Integer> edges(List<SurfaceMesh.Quad> mesh) {
        Map<String,Integer> edges=new HashMap<>();
        for(var q:mesh) { var pts=q.points();for(int i=0;i<4;i++) {
            String a=point(pts[i].position()),b=point(pts[(i+1)%4].position());
            String key=a.compareTo(b)<0?a+"|"+b:b+"|"+a;edges.merge(key,1,Integer::sum);
        }}return edges;
    }
    public static void main(String[] args) {
        verifyRayTangencies();
        verifyDirectionalPassages();
        verifySnowMaterials();
        verifyPassagesAndVerticalWalls();
        verifySeededVerticalVolumes();
        verifySurfaceCoverage();
        verifyRayColumns();
        verifyCovers();
        verifyWorldUV();
        verifyResourceOwnership();
        SurfaceMesh.Field cube=(x,y,z)->x==0&&y==0&&z==0;
        var rounded=generate(cube,-2,-2,-2,5);
        check(rounded.size()==24,"A single block must emit 24 subdivided patches, not six cube faces");
        boolean[] present={true},pin={false};
        var reusable=new SurfaceMesh((x,y,z)->present[0]&&cube.solid(x,y,z),0.25,(x,y,z)->pin[0]&&cube.solid(x,y,z));
        List<SurfaceMesh.Quad> reused=new ArrayList<>();
        reusable.generate(-2,-2,-2,5,reused::add);
        check(reused.size()==24,"Reusable mesh did not generate the first snapshot");
        present[0]=false;reused.clear();reusable.generate(-2,-2,-2,5,reused::add);
        check(reused.isEmpty(),"Reusable mesh retained faces after the block was removed");
        present[0]=true;pin[0]=true;reused.clear();reusable.generate(-2,-2,-2,5,reused::add);
        var pinnedRay=MeshRay.solidIntervals(reused,0.5,0.5,0,1,false);
        check(pinnedRay.size()==1&&pinnedRay.getFirst().low()==0&&pinnedRay.getFirst().high()==1,"Reusable mesh retained outdated pinning/vertices");
        Set<String> positions=new HashSet<>();
        for(var q:rounded) for(var p:q.points()) {
            positions.add(point(p.position()));
            check(p.normal().dot(p.position().minus(new SurfaceMesh.V(0.5,0.5,0.5)))>0,"Normals face inward");
        }
        check(positions.size()==26,"Subdivision must share the same 26 vertices");
        check(edges(rounded).values().stream().allMatch(n->n==2),"Rounded surface has a hole");
        var center=MeshRay.solidIntervals(rounded,0.5,0.5,0,1,false);
        check(center.size()==1&&center.getFirst().low()>0&&center.getFirst().high()<1,"Collision is still full-sized");
        check(MeshRay.solidIntervals(rounded,0.01,0.01,0,1,false).isEmpty(),"Empty cube corners must not collide");
        var flat=generate((x,y,z)->y<0,-2,-2,-2,5);
        for(var q:flat) for(var p:q.points()) check(Math.abs(p.position().y())<1e-8,"Flat ground moved");
        var ray=MeshRay.solidIntervals(flat,0.2,0.3,-1,1,true);
        check(ray.size()==1&&Math.abs(ray.getFirst().high())<1e-8,"Flat collision must stop at ground level");
        SurfaceMesh.Field hill=(x,y,z)->Math.pow(x-3.5,2)+Math.pow(y-3.5,2)+Math.pow(z-3.5,2)<16;
        List<SurfaceMesh.Quad> tiled=new ArrayList<>();
        for(int x=0;x<8;x+=4) for(int y=0;y<8;y+=4) for(int z=0;z<8;z+=4) tiled.addAll(generate(hill,x,y,z,4));
        check(edges(tiled).equals(edges(generate(hill,0,0,0,8))),"Independent section seams differ");
        // A fixed cube next to a rounded cube must retain its exact faces, while
        // the combined mesh remains watertight, including on a section boundary.
        SurfaceMesh.Field pair=(x,y,z)->(x==0||x==1)&&y==0&&z==0;
        SurfaceMesh.Field fixed=(x,y,z)->x==0&&y==0&&z==0;
        List<SurfaceMesh.Quad> anchored=new ArrayList<>();
        new SurfaceMesh(pair,1,fixed).generate(-2,-2,-2,5,anchored::add);
        check(edges(anchored).values().stream().allMatch(n->n==2),"Blacklist transition has a hole");
        for(var q:anchored) if(q.source().x()==0) for(var p:q.points()) {
            double coordinate=q.axis()==0?p.position().x():q.axis()==1?p.position().y():p.position().z();
            check(coordinate==(q.sign()>0?1:0),"Ignored cube face was deformed");
        }
        List<SurfaceMesh.Quad> split=new ArrayList<>();
        new SurfaceMesh(pair,1,fixed).generate(-2,-2,-2,3,split::add);
        new SurfaceMesh(pair,1,fixed).generate(1,-2,-2,3,split::add);
        check(edges(split).equals(edges(anchored)),"Blacklist transition differs across section seam");
        Map<SurfaceMesh.C,Integer> fixedQueries=new HashMap<>();
        List<SurfaceMesh.Quad> memoized=new ArrayList<>();
        new SurfaceMesh(pair,1,(x,y,z)->{
            fixedQueries.merge(new SurfaceMesh.C(x,y,z),1,Integer::sum);
            return fixed.solid(x,y,z);
        }).generate(-2,-2,-2,5,memoized::add);
        check(fixedQueries.values().stream().allMatch(n->n==1),"Blacklist repeatedly queried the same block during a bake");
        check(edges(memoized).equals(edges(anchored)),"Blacklist query cache changed geometry");
        SurfaceMesh.Field leafPatch=(x,y,z)->y<0||(x==3&&y==1&&z==2);
        var leafSource=new SurfaceMesh.C(3,1,2);
        List<SurfaceMesh.Quad> boundedLeaves=new ArrayList<>();
        new SurfaceMesh(leafPatch,0.25).generateBox(2,0,1,2,2,2,leafSource::equals,boundedLeaves::add);
        var fullLeaves=generate(leafPatch,0,-2,0,8,0.25).stream().filter(q->q.source().equals(leafSource)).toList();
        check(!boundedLeaves.isEmpty()&&edges(boundedLeaves).equals(edges(fullLeaves)),"Bounded foliage bake changed the leaf geometry or halo seams");
        var mild=generate(cube,-2,-2,-2,5,0.25);
        var mildRay=MeshRay.solidIntervals(mild,0.5,0.5,0,1,false);
        check(mildRay.getFirst().high()-mildRay.getFirst().low()>0.8,"Default rounding shrinks a single block too much");
        check(edges(mild).values().stream().allMatch(n->n==2),"Mild surface has a hole");
        var zero=generate(cube,-2,-2,-2,5,0);
        var zeroRay=MeshRay.solidIntervals(zero,0.5,0.5,0,1,false);
        check(zeroRay.getFirst().low()==0&&zeroRay.getFirst().high()==1,"Zero rounding must preserve block bounds");
        // Slopes and overhangs can bend a normal towards an occupied neighbor. Lighting
        // must instead sample the empty endpoint of the original sign-changing edge.
        SurfaceMesh.Field cliff=(x,y,z)-> y<0 || (x<0 && y<5) || (x==2 && y==2 && z==0);
        for(var q:generate(cliff,-4,-2,-4,9,0.25)) {
            var out=q.outside();var src=q.source();
            check(cliff.solid(src.x(),src.y(),src.z()),"Face source is not solid");
            check(!cliff.solid(out.x(),out.y(),out.z()),"Lighting was sampled inside the cliff");
        }
        tiled.clear();
        for(int x=0;x<8;x+=4) for(int y=0;y<8;y+=4) for(int z=0;z<8;z+=4) tiled.addAll(generate(hill,x,y,z,4,0.25));
        check(edges(tiled).equals(edges(generate(hill,0,0,0,8,0.25))),"Mild section seams differ");
        for(int axis=0;axis<3;axis++) for(int sign:new int[]{-1,1}) {
            FaceUV mapping=new FaceUV();
            for(int a=0;a<2;a++) for(int b=0;b<2;b++) {
                double[] p=new double[3];p[axis]=sign>0?1:0;p[(axis+1)%3]=a;p[(axis+2)%3]=b;
                int index=FaceUV.corner(p[0],p[1],p[2],axis,sign);
                mapping.set(index,(float)(p[(axis+1)%3]*0.3+0.2),(float)(1-p[1]));
            }
            for(double s:new double[]{0,0.25,0.5,1}) for(double t:new double[]{0,0.75,1}) {
                double a=sign>0?s:t,b=sign>0?t:s;
                double y=axis==0?a:axis==1?(sign>0?1:0):b;
                check(Math.abs(mapping.v(s,t)-(1-y))<1e-6,"Grass side texture rotated away from vertical on axis "+axis+" sign "+sign);
                check(Math.abs(mapping.u(s,t)-(a*0.3+0.2))<1e-6,"Original cropped/rotated texture coordinates were lost");
            }
        }
        System.out.println("PASS: watertight meshes, collision rays, section seams, mild rounding, exposed light samples and original UV orientation on all six sides.");
    }
    private static void verifyDirectionalPassages() {
        for(double amount:new double[]{.25,.5,.8,1}) {
            SurfaceMesh.Field shaft=(x,y,z)->Math.abs(x)<=3&&Math.abs(z)<=3&&y>=-3&&y<=3&&!(x==0&&z==0);
            var mesh=new ArrayList<SurfaceMesh.Quad>();new SurfaceMesh(shaft,amount,null,true).generateBox(-5,-5,-5,11,11,11,s->true,mesh::add);
            long roundedRim=mesh.stream().flatMap(q->Arrays.stream(q.points())).map(SurfaceMesh.Point::position)
                .filter(p->(p.x()==0||p.x()==1)&&(p.z()==0||p.z()==1)&&p.y()>3&&p.y()<4).count();
            check(roundedRim>0,"Shaft clearance unnecessarily freezes tangential rounding at "+amount);
            SurfaceMesh.Field bottom=(x,y,z)->Math.abs(x)<=3&&Math.abs(z)<=3&&y>=-2&&y<=9&&(y<=0||y==9||x!=0||z!=0);
            var closed=new ArrayList<SurfaceMesh.Quad>();new SurfaceMesh(bottom,amount,null,true).generateBox(-5,-4,-5,11,16,11,s->true,closed::add);
            for(double x=.2;x<.81;x+=.05)for(double z=.2;z<.81;z+=.05)
                check(MeshRay.solidIntervals(closed,x,z,1.000001,8.999999,false).isEmpty(),"Narrow shaft floor or ceiling protrudes into its clearance at "+amount);
        }
        System.out.println("PASS: passage width remains protected while shaft rims can round tangentially at 25/50/80/100 percent.");
    }
    private static void verifySnowMaterials() {
        int painted=0,unpainted=0;
        for(double amount:new double[]{0,.25,.5,.8,1}) {
            var faces=generate((x,y,z)->x==0&&y==0&&z==0,-2,-2,-2,5,amount);
            for(var q:faces)if(q.axis()!=1) {
                var out=new ArrayList<SurfaceMesh.Quad>();TerrainCovers.snowySide(q,out::add);
                check(Math.abs(out.stream().mapToDouble(SmoothingChecks::area).sum()-area(q))<1e-9,"Snow material replacement changes terrain area");
                for(var r:out) {
                    boolean up=r.b().position().minus(r.a().position()).cross(r.c().position().minus(r.a().position())).y()>1e-12;
                    boolean snowy=r.source().y()==q.source().y()+1;
                    check(up==snowy,"Snow was applied to vertical/downward geometry or omitted from an upward slope");
                    for(var p:r.points())check(Arrays.asList(q.points()).contains(p),"Snow material moved a vertex");
                    if(snowy)painted++;else unpainted++;
                }
            }
        }
        check(painted>0&&unpainted>0,"Snow material fixture misses slopes or cliffs");
        System.out.println("PASS: snow slope materials preserve original triangle geometry; vertical walls and undersides remain unchanged.");
    }
    private static void verifyPassagesAndVerticalWalls() {
        int checks=0;
        for(double rounding:new double[]{0,.25,.5,.8,1}) {
            for(int shift:new int[]{-17,0,15})for(int mode=0;mode<3;mode++) {
                final int kind=mode;
                SurfaceMesh.Field field=(x,y,z)->x>=shift-3&&x<=shift+3&&y>=-3&&y<=8&&z>=-3&&z<=3
                    && !(kind==0?x==shift&&z==0:kind==1?z==0&&y>=0&&y<=1:x<shift&&z<0);
                var full=new ArrayList<SurfaceMesh.Quad>();
                new SurfaceMesh(field,rounding,null,true).generateBox(shift-5,-5,-5,11,16,11,s->true,full::add);
                check(edges(full).values().stream().allMatch(n->n==2),"Protected passage mesh has a hole");
                var tiled=new ArrayList<SurfaceMesh.Quad>();
                new SurfaceMesh(field,rounding,null,true).generateBox(shift-5,-5,-5,5,16,11,s->true,tiled::add);
                new SurfaceMesh(field,rounding,null,true).generateBox(shift,-5,-5,6,16,11,s->true,tiled::add);
                check(edges(tiled).equals(edges(full)),"Passage protection differs across independently baked sections");
                if(mode<2) {
                    for(int a=0;a<12;a++)for(int b=0;b<12;b++) {
                        double x=shift+.2+a*.05,z=.2+b*.05;
                        double low=mode==0?0:.01,high=mode==0?5:1.99;
                        var intervals=MeshRay.solidIntervals(full,x,z,low,high,false);
                        check(intervals.isEmpty(),"Passage mesh contracted clearance at "+rounding+" mode="+mode);
                        checks++;
                    }
                }
                var patch=new ArrayList<SurfaceMesh.Quad>();
                new SurfaceMesh(field,rounding,null,true).generateBox(shift-2,1,-2,3,5,3,s->true,patch::add);
                var nearest=new MeshRay.NearestSurface(patch);
                Random random=new Random(200316+mode);
                for(int sample=0;sample<180;sample++) {
                    double x=shift-1+random.nextDouble(),z=-1+random.nextDouble(),y=3+random.nextDouble();
                    // Complete mesh ray is the independent closed-volume oracle.
                    var exact=MeshRay.solidIntervals(full,x,z,-5,11,false);
                    boolean expected=exact.stream().anyMatch(i->y>i.low()+1e-7&&y<i.high()-1e-7);
                    // Test nearest sign even when this particular direction has
                    // crossings; full mesh is closed well outside the samples.
                    boolean actual=nearest.inside(x,y,z,field.solid((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)));
                    check(actual==expected,"Nearest-surface sign disagrees with closed mesh at "+x+","+y+","+z+" round="+rounding+" mode="+mode);
                    checks++;
                }
            }
        }
        System.out.println("PASS: "+checks+" passage-clearance and nearest vertical-wall sign checks across five rounding levels and negative/chunk-boundary coordinates.");
    }
    private static void verifySeededVerticalVolumes() {
        int checked=0;Random samples=new Random(200017);
        for(int seed=0;seed<16;seed++)for(double rounding:new double[]{0,.25,.5,.8,1}) {
            Random cells=new Random(260016+seed);boolean[][] mask=new boolean[5][5];
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)mask[x][z]=cells.nextBoolean();
            SurfaceMesh.Field field=(x,y,z)->x>=-2&&x<=2&&z>=-2&&z<=2&&y>=-3&&y<=8&&mask[x+2][z+2];
            var full=new ArrayList<SurfaceMesh.Quad>();
            new SurfaceMesh(field,rounding,null,true).generateBox(-4,-5,-4,9,16,9,s->true,full::add);
            for(int px=-1;px<=1;px++)for(int pz=-1;pz<=1;pz++) {
                var patch=new ArrayList<SurfaceMesh.Quad>();
                new SurfaceMesh(field,rounding,null,true).generateBox(px-1,1,pz-1,3,5,3,s->true,patch::add);
                var nearest=new MeshRay.NearestSurface(patch);
                for(int trial=0;trial<12;trial++) {
                    double x=px+.001+samples.nextDouble()*.998,z=pz+.001+samples.nextDouble()*.998,y=3.1+samples.nextDouble()*.8;
                    var exact=MeshRay.solidIntervals(full,x,z,-5,11,false);
                    boolean expected=exact.stream().anyMatch(i->y>i.low()&&y<i.high());
                    boolean actual=nearest.inside(x,y,z,field.solid(px,3,pz));
                    check(expected==actual,"Seeded vertical volume classification failed: seed="+seed+" rounding="+rounding+" at "+x+","+y+","+z);
                    checked++;
                }
            }
        }
        System.out.println("PASS: "+checked+" deterministic vertical-volume samples agree with complete closed meshes (16 terrain seeds, five rounding levels).");
    }
    private static List<SurfaceMesh.V> clipSurface(List<SurfaceMesh.V> input,int axis,double boundary,boolean greater) {
        if(input.isEmpty())return input;var result=new ArrayList<SurfaceMesh.V>();var a=input.getLast();
        for(var b:input) {
            double av=axis==0?a.x():axis==1?a.y():a.z(),bv=axis==0?b.x():axis==1?b.y():b.z();
            boolean ai=greater?av>boundary:av<boundary,bi=greater?bv>boundary:bv<boundary;
            if(ai!=bi)result.add(a.plus(b.minus(a).times((boundary-av)/(bv-av))));
            if(bi)result.add(b);a=b;
        }return result;
    }
    private static void verifySurfaceCoverage() {
        Random random=new Random(200018);
        for(int sample=0;sample<5000;sample++) {
            var points=new ArrayList<SurfaceMesh.V>();
            for(int i=0;i<3;i++)points.add(new SurfaceMesh.V(random.nextDouble()*2-.5,random.nextDouble()*2-.5,random.nextDouble()*2-.5));
            List<SurfaceMesh.V> clipped=points;
            for(int axis=0;axis<3;axis++){clipped=clipSurface(clipped,axis,1e-9,true);clipped=clipSurface(clipped,axis,1-1e-9,false);}
            boolean expected=false;
            for(int i=1;i<clipped.size()-1;i++) {
                var cross=clipped.get(i).minus(clipped.getFirst()).cross(clipped.get(i+1).minus(clipped.getFirst()));
                expected|=cross.dot(cross)>1e-18;
            }
            check(SurfaceVoxelizer.intersects(points.get(0),points.get(1),points.get(2),0,0,0,1,1,1)==expected,"Triangle/cell SAT differs from independent polygon clipping");
        }
        var a=new SurfaceMesh.V(0,0,0);var b=new SurfaceMesh.V(1,0,0);var c=new SurfaceMesh.V(0,0,1);
        check(!SurfaceVoxelizer.intersects(a,b,c,0,0,0,1,1,1),"Flat floor acquired a spurious collision layer");
        check(!SurfaceVoxelizer.intersects(a,b,a,0,0,0,1,1,1),"Degenerate triangle acquired collision volume");
        for(double amount:new double[]{.25,.5,.8,1}) {
            var mesh=generate((x,y,z)->x==0&&y==0&&z==0,-2,-2,-2,5,amount);
            boolean[][][] covered=new boolean[8][32][8];
            SurfaceVoxelizer.cover(mesh,0,0,0,8,32,(x,y,z)->covered[x][y][z]=true);
            for(var q:mesh)for(int tri=0;tri<2;tri++)for(int point=0;point<30;point++) {
                double u=random.nextDouble(),v=random.nextDouble()*(1-u);
                var p=q.a().position().times(1-u-v).plus(q.point(tri==0?1:2).position().times(u)).plus(q.point(tri==0?2:3).position().times(v));
                double sx=p.x()*8,sy=p.y()*32,sz=p.z()*8;
                if(Math.abs(sx-Math.rint(sx))<1e-8||Math.abs(sy-Math.rint(sy))<1e-8||Math.abs(sz-Math.rint(sz))<1e-8)continue;
                check(covered[(int)sx][(int)sy][(int)sz],"Conservative raster omitted a surface sample");
            }
        }
        System.out.println("PASS: 5000 triangle/cell SAT comparisons against independent polygon clipping; conservative surface coverage, flat-floor contacts and degenerate triangles verified.");
    }
    private static void verifyRayColumns() {
        for(double rounding:new double[]{0,.25,.8,1}) {
            SurfaceMesh.Field field=(x,y,z)->y<Math.floorMod(x*13+z*7,5)-2||x==0&&z==0&&y<4;
            var mesh=generate(field,-4,-4,-4,8,rounding);
            for(int px=-2;px<=1;px++)for(int pz=-2;pz<=1;pz++) {
                var columns=new MeshRay.Columns(mesh,px,pz,8);
                for(int z=0;z<8;z++)for(int x=0;x<8;x++) {
                    var expected=MeshRay.solidIntervals(mesh,px+(x+.5)/8,pz+(z+.5)/8,-2,3,true);
                    var actual=columns.intervals(x,z,-2,3,true);
                    check(expected.equals(actual),"Prepared ray column changed exact collision intervals");
                }
            }
        }
        System.out.println("PASS: 4096 prepared collision columns match brute-force intervals exactly, including negative coordinates and overhangs.");
    }
    private static void verifyCovers() {
        for(double rounding:new double[]{0,0.25,0.5,0.8,1}) {
            for(SurfaceMesh.Field bank:List.<SurfaceMesh.Field>of((x,y,z)->y<0||(x<0&&y<3),(x,y,z)->y<0||(x==0&&y==0)||(x==1&&y==1&&z<1))) {
                var bankFaces=generate(bank,-3,-2,-3,6,rounding);var caps=new ArrayList<SurfaceMesh.Quad>();
                for(var q:bankFaces) {double height=q.source().y()+8.0/9;TerrainCovers.shore(q,height,caps::add,side->true);TerrainCovers.submergedTop(q,height,caps::add);}
                for(var cap:caps)for(var point:cap.points()) {
                    var p=point.position();var src=cap.source();
                    check(p.x()>=src.x()-1e-9&&p.x()<=src.x()+1+1e-9&&p.z()>=src.z()-1e-9&&p.z()<=src.z()+1+1e-9,"Water join overlaps a neighbouring original fluid cell");
                }
            }
            List<SurfaceMesh.Quad> faces=new ArrayList<>();
            new SurfaceMesh((x,y,z)->x==0&&y==0&&z==0,rounding).generate(-2,-2,-2,5,faces::add);
            List<SurfaceMesh.Quad> shore=new ArrayList<>(),snow=new ArrayList<>();
            for(var q:faces) {
                if(q.axis()==0&&q.sign()==1)TerrainCovers.shore(q,8.0/9,shore::add);
                if(q.axis()==1&&q.sign()==1)TerrainCovers.snow(q,0.125,snow::add,side->0);
            }
            check(!snow.isEmpty()&&(rounding==0||!shore.isEmpty()),"Missing snow or shore cover at "+rounding);
            if(rounding==0) check(shore.isEmpty(),"Original shoreline got overlapping cap geometry");
            for(var q:shore) for(int i=0;i<4;i++) {
                var p=q.point(i).position();check(Double.isFinite(p.x()+p.y()+p.z())&&Math.abs(p.y()-8.0/9)<1e-9,"Non-finite or tilted water contact");
            }
            if(rounding>0) check(shore.stream().mapToDouble(SmoothingChecks::area).sum()>0,"Missing water contact bridge");
            double topArea=snow.stream().filter(q->q.axis()==1).mapToDouble(SmoothingChecks::area).sum();
            double original=faces.stream().filter(q->q.axis()==1&&q.sign()==1).mapToDouble(SmoothingChecks::area).sum();
            check(Math.abs(topArea-original)<1e-8,"Snow changed supporting triangle area");
            if(rounding>0) {
                List<SurfaceMesh.Quad> water=new ArrayList<>();
                for(var q:faces) {TerrainCovers.shore(q,8.0/9,water::add,side->true);TerrainCovers.submergedTop(q,8.0/9,water::add);}
                for(double x=0.025;x<1;x+=0.05)for(double z=0.025;z<1;z+=0.05) {
                    final double px=x,pz=z;
                    boolean land=faces.stream().anyMatch(q->q.axis()==1&&q.sign()==1&&MeshRay.height(q,px,pz)>=8.0/9-1e-8);
                    boolean wet=water.stream().anyMatch(q->Double.isFinite(MeshRay.height(q,px,pz)));
                    check(land||wet,"Uncovered liquid/terrain corner at "+rounding+" "+x+","+z);
                }
            }
        }
        System.out.println("PASS: shoreline contact footprints and draped snow surfaces at 0/25/50/80/100 percent rounding.");
    }
}
