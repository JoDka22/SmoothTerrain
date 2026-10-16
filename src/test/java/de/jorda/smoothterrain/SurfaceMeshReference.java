package de.jorda.smoothterrain;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Surface Nets followed by shared Catmull-Clark subdivision. All keys use world
 * coordinates, including the halo, so neighboring sections construct identical seams.
 * This is an independent implementation; it does not deform vanilla block models. */
final class SurfaceMeshReference {
    @FunctionalInterface public interface Field { boolean solid(int x, int y, int z); }
    public record V(double x, double y, double z) {
        public V plus(V b) { return new V(x+b.x,y+b.y,z+b.z); }
        public V minus(V b) { return new V(x-b.x,y-b.y,z-b.z); }
        public V times(double s) { return new V(x*s,y*s,z*s); }
        public V cross(V b) { return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public double dot(V b) { return x*b.x+y*b.y+z*b.z; }
        public V unit() { double n=Math.sqrt(dot(this)); return n<1e-12 ? new V(0,1,0) : times(1/n); }
        static V zero() { return new V(0,0,0); }
    }
    public record C(int x,int y,int z) {
        C shift(int axis,int amount) { return new C(x+(axis==0?amount:0),y+(axis==1?amount:0),z+(axis==2?amount:0)); }
    }
    public record Point(V position,V normal,double u,double v) {}
    public record Quad(Point a,Point b,Point c,Point d,C source,int axis,int sign) {
        public Point point(int i) { return switch(i) { case 0->a;case 1->b;case 2->c;case 3->d;default->throw new IndexOutOfBoundsException(i); }; }
        public Point[] points() { return new Point[]{a,b,c,d}; }
        public C outside() { return source.shift(axis,sign); }
    }
    private record F(C start,int axis) {}
    private record E(C low,int axis) {}
    private record Raw(F key,C[] cells,V center,V normal,C source,int sign) {}
    private record Vertex(V position,V normal) {}
    // Section-local arrays avoid hash lookups and repeated world reads. The five
    // block halo covers subdivision neighbors; a fallback keeps arbitrary calls safe.
    private static final class Samples {
        final Field field;
        int x,y,z,width;
        byte[] values;
        final Map<C,Boolean> fallback=new HashMap<>();
        Samples(Field field) { this.field=field; }
        void begin(int x,int y,int z,int size) {
            this.x=x-5;this.y=y-5;this.z=z-5;this.width=size+10;
            values=new byte[width*width*width];fallback.clear();
        }
        boolean get(int x,int y,int z) {
            int dx=x-this.x,dy=y-this.y,dz=z-this.z;
            if(values==null||dx<0||dy<0||dz<0||dx>=width||dy>=width||dz>=width)
                return fallback.computeIfAbsent(new C(x,y,z),p->field.solid(p.x,p.y,p.z));
            int index=(dz*width+dy)*width+dx;
            byte value=values[index];
            if(value==0) { value=(byte)(field.solid(x,y,z)?2:1);values[index]=value; }
            return value==2;
        }
    }
    private final Samples samples;
    private final Samples fixedSamples;
    private final Map<C,Boolean> pinned=new HashMap<>();
    private static final double[][] UV={{0,0},{1,0},{1,1},{0,1}};
    private final double rounding;
    private final Map<C,V> rawVertices=new HashMap<>();
    private final Map<F,Raw> faces=new HashMap<>();
    private final Set<F> emptyFaces=new HashSet<>();
    private final Map<C,List<Raw>> incidentFaces=new HashMap<>();
    private final Map<C,Vertex> vertices=new HashMap<>();
    private final Map<E,Vertex> edges=new HashMap<>();

    public SurfaceMeshReference(Field field) { this(field,1.0); }
    public SurfaceMeshReference(Field field,double rounding) {
        this(field,rounding,null);
    }
    public SurfaceMeshReference(Field field,double rounding,Field fixed) {
        this.samples=new Samples(field);this.fixedSamples=fixed==null?null:new Samples(fixed);
        this.rounding=Double.isFinite(rounding)?Math.clamp(rounding,0.0,1.0):0.25;
    }
    private V blend(V original,V rounded) { return original.plus(rounded.minus(original).times(rounding)); }
    private boolean solid(C p) { return samples.get(p.x,p.y,p.z); }

    private static int indexOf(C[] cells,C cell) {
        for(int i=0;i<cells.length;i++) if(cells[i].equals(cell)) return i;
        return -1;
    }
    private boolean pinned(C cell) {
        if(fixedSamples==null) return false;
        return pinned.computeIfAbsent(cell,k->{
            for(int bits=0;bits<8;bits++) if(fixedSamples.get(k.x+(bits&1),k.y+((bits>>1)&1),k.z+((bits>>2)&1))) return true;
            return false;
        });
    }
    private V rawVertex(C cell) {
        return rawVertices.computeIfAbsent(cell,k->{
            if(pinned(k)) return new V(k.x+1,k.y+1,k.z+1);
            V sum=V.zero(); int count=0;
            for(int bits=0;bits<8;bits++) {
                C a=new C(k.x+(bits&1),k.y+((bits>>1)&1),k.z+((bits>>2)&1));
                for(int axis=0;axis<3;axis++) {
                    if((bits&(1<<axis))!=0) continue;
                    if(solid(a)==solid(a.shift(axis,1))) continue;
                    sum=sum.plus(new V(a.x+0.5+(axis==0?0.5:0),a.y+0.5+(axis==1?0.5:0),a.z+0.5+(axis==2?0.5:0)));
                    count++;
                }
            }
            V corner=new V(k.x+1,k.y+1,k.z+1);
            return count==0?corner:blend(corner,sum.times(1.0/count));
        });
    }

    private Raw face(F key) {
        if(emptyFaces.contains(key)) return null;
        Raw cached=faces.get(key); if(cached!=null) return cached;
        C p=key.start; int axis=key.axis;
        boolean inside=solid(p);
        if(inside==solid(p.shift(axis,1))) { emptyFaces.add(key); return null; }
        int u=(axis+1)%3,v=(axis+2)%3;
        C c0=p.shift(u,-1).shift(v,-1);
        C[] cells={c0,c0.shift(u,1),c0.shift(u,1).shift(v,1),c0.shift(v,1)};
        if(!inside) { C swap=cells[1];cells[1]=cells[3];cells[3]=swap; }
        V p0=rawVertex(cells[0]),p1=rawVertex(cells[1]),p2=rawVertex(cells[2]),p3=rawVertex(cells[3]);
        V center=p0.plus(p1).plus(p2).plus(p3).times(0.25);
        V normal=p1.minus(p0).cross(p2.minus(p0)).unit();
        Raw result=new Raw(key,cells,center,normal,inside?p:p.shift(axis,1),inside?1:-1);
        faces.put(key,result); return result;
    }

    private List<Raw> around(C cell) {
        return incidentFaces.computeIfAbsent(cell,k->{
            List<Raw> result=new ArrayList<>(6);
            for(int axis=0;axis<3;axis++) {
                int u=(axis+1)%3,v=(axis+2)%3;
                for(int a=0;a<2;a++) for(int b=0;b<2;b++) {
                    Raw f=face(new F(k.shift(u,a).shift(v,b),axis));
                    if(f!=null) result.add(f);
                }
            }
            return result;
        });
    }

    private Vertex vertex(C cell) {
        return vertices.computeIfAbsent(cell,k->{
            V p=rawVertex(k),f=V.zero(),r=V.zero(),normal=V.zero();
            List<Raw> incident=around(k);
            if(incident.isEmpty()) return new Vertex(p,new V(0,1,0));
            Set<C> neighbors=new HashSet<>();
            for(Raw face:incident) {
                f=f.plus(face.center);normal=normal.plus(face.normal);
                int at=indexOf(face.cells,k);
                neighbors.add(face.cells[(at+1)%4]);neighbors.add(face.cells[(at+3)%4]);
            }
            for(C neighbor:neighbors) r=r.plus(p.plus(rawVertex(neighbor)).times(0.5));
            int n=neighbors.size();
            V smoothed=f.times(1.0/incident.size()).plus(r.times(2.0/n)).plus(p.times(n-3)).times(1.0/n);
            return new Vertex(pinned(k)?p:blend(p,smoothed),normal.unit());
        });
    }

    private Vertex edge(C a,C b) {
        int axis=a.x!=b.x?0:a.y!=b.y?1:2;
        C low=(axis==0?a.x<b.x:axis==1?a.y<b.y:a.z<b.z)?a:b;
        return edges.computeIfAbsent(new E(low,axis),key->{
            V sum=rawVertex(a).plus(rawVertex(b)),normal=V.zero(); int count=2;
            for(Raw f:around(a)) {
                if(indexOf(f.cells,b)<0) continue;
                sum=sum.plus(f.center);normal=normal.plus(f.normal);count++;
            }
            V midpoint=rawVertex(a).plus(rawVertex(b)).times(0.5);
            return new Vertex(pinned(a)&&pinned(b)?midpoint:blend(midpoint,sum.times(1.0/count)),normal.unit());
        });
    }

    public void generate(int x,int y,int z,int size,Consumer<Quad> consumer) {
        generateBox(x,y,z,size,size,size,source->true,consumer);
    }
    public void generateBox(int x,int y,int z,int width,int height,int depth,Predicate<C> sources,Consumer<Quad> consumer) {
        generateBox(x,y,z,width,height,depth,7,sources,consumer);
    }
    public void generateBox(int x,int y,int z,int width,int height,int depth,int axisMask,Predicate<C> sources,Consumer<Quad> consumer) {
        int size=Math.max(width,Math.max(height,depth));
        if(width<=0||height<=0||depth<=0) return;
        // All geometry caches belong to the current field snapshot, not the object.
        pinned.clear();rawVertices.clear();faces.clear();emptyFaces.clear();incidentFaces.clear();vertices.clear();edges.clear();
        samples.begin(x,y,z,size);
        if(fixedSamples!=null) fixedSamples.begin(x,y,z,size);
        for(int dz=0;dz<depth;dz++) for(int dy=0;dy<height;dy++) for(int dx=0;dx<width;dx++) {
            int px=x+dx,py=y+dy,pz=z+dz;
            boolean solid=samples.get(px,py,pz);
            for(int axis=0;axis<3;axis++) {
                if((axisMask&(1<<axis))==0) continue;
                // Most pairs are interior or air. Avoid allocating/cache-inserting
                // cell/face keys for these pairs before subdivision even starts.
                if(solid==samples.get(px+(axis==0?1:0),py+(axis==1?1:0),pz+(axis==2?1:0))) continue;
                Raw f=face(new F(new C(px,py,pz),axis));
                if(f==null||!sources.test(f.source)) continue;
                double[][] uv=UV;
                for(int i=0;i<4;i++) {
                    int next=(i+1)%4,previous=(i+3)%4;
                    Vertex p=vertex(f.cells[i]),e1=edge(f.cells[i],f.cells[next]),e0=edge(f.cells[previous],f.cells[i]);
                    Point a=new Point(p.position,p.normal,uv[i][0],uv[i][1]);
                    Point b=new Point(e1.position,e1.normal,(uv[i][0]+uv[next][0])/2,(uv[i][1]+uv[next][1])/2);
                    Point c=new Point(f.center,f.normal,0.5,0.5);
                    Point d=new Point(e0.position,e0.normal,(uv[i][0]+uv[previous][0])/2,(uv[i][1]+uv[previous][1])/2);
                    consumer.accept(new Quad(a,b,c,d,f.source,axis,f.sign));
                }
            }
        }
    }
}
