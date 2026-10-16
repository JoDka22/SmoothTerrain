package de.jorda.smoothterrain;

import java.util.Arrays;
import java.util.ArrayList;
import java.lang.management.ManagementFactory;

public final class PerformanceChecks {
    public static void main(String[] args) {
        if(Arrays.asList(args).contains("rectangular")){benchmarkRectangular();return;}
        SurfaceMesh.Field field=(x,y,z)->y<4+(Math.floorMod(x,9)/3)+(Math.floorMod(z,11)/4)
            || (x%7==0&&z%7==0&&y<13);
        long[] times=new long[100];long[] faces={0};
        for(int pass=-50;pass<times.length;pass++) {
            long start=System.nanoTime();faces[0]=0;
            new SurfaceMesh(field,0.25).generate(0,0,0,16,q->faces[0]++);
            if(pass>=0) times[pass]=System.nanoTime()-start;
        }
        Arrays.sort(times);
        System.out.printf("SECTION_BENCH: median %.3f ms; %d patches; 50 warmup + 100 measured sections.%n",times[times.length/2]/1e6,faces[0]);
        long[] fixedQueries={0};
        for(int pass=-50;pass<times.length;pass++) {
            long start=System.nanoTime();faces[0]=0;fixedQueries[0]=0;
            new SurfaceMesh(field,0.25,(x,y,z)->{
                fixedQueries[0]++;
                return field.solid(x,y,z)&&Math.floorMod(x,13)==0&&Math.floorMod(z,13)==0;
            }).generate(0,0,0,16,q->faces[0]++);
            if(pass>=0) times[pass]=System.nanoTime()-start;
        }
        Arrays.sort(times);
        System.out.printf("FILTERED_SECTION_BENCH: median %.3f ms; %d patches; %d blacklist field queries; same section, 50 warmup + 100 measured sections.%n",times[times.length/2]/1e6,faces[0],fixedQueries[0]);
        benchmarkUV();
    }

    private static void benchmarkRectangular() {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        SurfaceMesh.Field field=(x,y,z)->y<1+Math.floorMod(x+z,3);
        SurfaceMesh.Field anchor=(x,y,z)->Math.floorMod(x+2*y+3*z,17)==0;
        for(int[] box:new int[][]{{3,5,3,7},{17,2,17,2},{2,17,17,7},{17,17,2,7}}) {
            long[] times=new long[40],bytes=new long[40],faces={0},hash={0};
            for(int pass=-20;pass<40;pass++) {
                faces[0]=hash[0]=0;
                long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                new SurfaceMesh(field,.8,anchor).generateBox(-4,0,-4,box[0],box[1],box[2],box[3],source->true,q->{
                    faces[0]++;
                    hash[0]=hash[0]*31+Double.doubleToLongBits(q.a().position().x());
                    hash[0]=hash[0]*31+Double.doubleToLongBits(q.a().position().y());
                    hash[0]=hash[0]*31+Double.doubleToLongBits(q.a().position().z());
                });
                if(pass>=0){times[pass]=System.nanoTime()-start;bytes[pass]=bean.getThreadAllocatedBytes(thread)-before;}
            }
            if(faces[0]==0)throw new AssertionError("Rectangular benchmark emitted no surfaces");
            Arrays.sort(times);Arrays.sort(bytes);
            System.out.printf(java.util.Locale.ROOT,"RECTANGULAR_BENCH: size=%dx%dx%d mask=%d median_ms=%.4f allocated_bytes=%d patches=%d hash=%016x warmup=20 samples=40%n",box[0],box[1],box[2],box[3],times[20]/1e6,bytes[20],faces[0],hash[0]);
        }
    }
    private static void benchmarkUV() {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        // Actual subdivided terrain: terraces, isolated pillars and a cave opening,
        // including negative coordinates and higher rounding. Generation is excluded.
        var input=new ArrayList<SurfaceMesh.Quad>();
        SurfaceMesh.Field field=(x,y,z)->y<6+Math.floorMod(x,9)/3+Math.floorMod(z,11)/4
            && !(x>3&&x<11&&z>4&&z<12&&y>2&&y<6)
            || Math.floorMod(x,7)==0&&Math.floorMod(z,7)==0&&y<14;
        for(double rounding:new double[]{.25,.8,1})
            new SurfaceMesh(field,rounding).generate(-8,0,-8,16,input::add);
        long[] times=new long[100],allocations=new long[100];
        long[] count={0},hash={0};
        WorldUV.Sink sink=(q,axis,a,b)->{
            count[0]++;hash[0]=hash[0]*31+axis;hash[0]=hash[0]*31+a;hash[0]=hash[0]*31+b;
            for(int i=0;i<4;i++) {
                var p=q.point(i);
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.position().x());
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.position().y());
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.position().z());
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.normal().x());
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.normal().y());
                hash[0]=hash[0]*31+Double.doubleToLongBits(p.normal().z());
            }
        };
        for(int pass=-50;pass<times.length;pass++) {
            count[0]=0;hash[0]=0;
            long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(var q:input)WorldUV.emit(q,sink);
            if(pass>=0){times[pass]=System.nanoTime()-start;allocations[pass]=bean.getThreadAllocatedBytes(thread)-before;}
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf("UV_BENCH: median %.3f ms; %.3f MiB allocated; %d input patches; %d output patches; geometry hash %016x; 50 warmup + 100 measured batches.%n",
            times[50]/1e6,allocations[50]/1048576.0,input.size(),count[0],hash[0]);
    }
}
