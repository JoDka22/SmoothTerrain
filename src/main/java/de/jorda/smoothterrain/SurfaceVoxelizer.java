package de.jorda.smoothterrain;

import java.util.List;

/** Conservative surface coverage for collision cells. Centre rays alone can
 * miss a steep triangle crossing the side of an otherwise empty cell. */
public final class SurfaceVoxelizer {
    @FunctionalInterface public interface Cell {void fill(int x,int y,int z);}
    private SurfaceVoxelizer() {}
    public static void cover(List<SurfaceMesh.Quad> mesh,int px,int py,int pz,int width,int height,Cell cells) {
        var origin=new SurfaceMesh.V(px,py,pz);
        for(var q:mesh)for(int t=0;t<2;t++) {
            var a=q.a().position().minus(origin);var b=q.point(t==0?1:2).position().minus(origin);
            var c=q.point(t==0?2:3).position().minus(origin);
            int lx=Math.max(0,(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))*width));
            int hx=Math.min(width-1,(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))*width)-1);
            int ly=Math.max(0,(int)Math.floor(Math.min(a.y(),Math.min(b.y(),c.y()))*height));
            int hy=Math.min(height-1,(int)Math.ceil(Math.max(a.y(),Math.max(b.y(),c.y()))*height)-1);
            int lz=Math.max(0,(int)Math.floor(Math.min(a.z(),Math.min(b.z(),c.z()))*width));
            int hz=Math.min(width-1,(int)Math.ceil(Math.max(a.z(),Math.max(b.z(),c.z()))*width)-1);
            for(int z=lz;z<=hz;z++)for(int y=ly;y<=hy;y++)for(int x=lx;x<=hx;x++)
                if(intersects(a,b,c,x/(double)width,y/(double)height,z/(double)width,
                    (x+1)/(double)width,(y+1)/(double)height,(z+1)/(double)width))cells.fill(x,y,z);
        }
    }
    /** Triangle/open-box SAT. Mere contact with a cell boundary adds no layer;
     * the existing volume classification already owns the solid side. */
    public static boolean intersects(SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,
            double x0,double y0,double z0,double x1,double y1,double z1) {
        if(Math.min(a.x(),Math.min(b.x(),c.x()))>=x1-1e-11||Math.max(a.x(),Math.max(b.x(),c.x()))<=x0+1e-11
            ||Math.min(a.y(),Math.min(b.y(),c.y()))>=y1-1e-11||Math.max(a.y(),Math.max(b.y(),c.y()))<=y0+1e-11
            ||Math.min(a.z(),Math.min(b.z(),c.z()))>=z1-1e-11||Math.max(a.z(),Math.max(b.z(),c.z()))<=z0+1e-11)return false;
        var centre=new SurfaceMesh.V((x0+x1)/2,(y0+y1)/2,(z0+z1)/2);
        a=a.minus(centre);b=b.minus(centre);c=c.minus(centre);
        double hx=(x1-x0)/2,hy=(y1-y0)/2,hz=(z1-z0)/2;
        var ab=b.minus(a);var ac=c.minus(a);var normal=ab.cross(ac);
        if(normal.dot(normal)<1e-24)return false;
        if(!axis(normal.x(),normal.y(),normal.z(),a,b,c,hx,hy,hz))return false;
        return edge(ab,a,b,c,hx,hy,hz)&&edge(c.minus(b),a,b,c,hx,hy,hz)&&edge(a.minus(c),a,b,c,hx,hy,hz);
    }
    private static boolean edge(SurfaceMesh.V e,SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,double hx,double hy,double hz) {
        return axis(0,e.z(),-e.y(),a,b,c,hx,hy,hz)&&axis(-e.z(),0,e.x(),a,b,c,hx,hy,hz)
            &&axis(e.y(),-e.x(),0,a,b,c,hx,hy,hz);
    }
    private static boolean axis(double x,double y,double z,SurfaceMesh.V a,SurfaceMesh.V b,SurfaceMesh.V c,double hx,double hy,double hz) {
        double length=x*x+y*y+z*z;if(length<1e-24)return true;
        double pa=x*a.x()+y*a.y()+z*a.z(),pb=x*b.x()+y*b.y()+z*b.z(),pc=x*c.x()+y*c.y()+z*c.z();
        double radius=hx*Math.abs(x)+hy*Math.abs(y)+hz*Math.abs(z),epsilon=1e-11*Math.sqrt(length);
        return Math.min(pa,Math.min(pb,pc))<radius-epsilon&&Math.max(pa,Math.max(pb,pc))>-radius+epsilon;
    }
}
