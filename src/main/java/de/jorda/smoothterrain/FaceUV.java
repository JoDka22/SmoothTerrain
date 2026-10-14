package de.jorda.smoothterrain;

/** Bilinear interpolation of the original model's four texture coordinates. */
public final class FaceUV {
    private final float[] u=new float[4],v=new float[4];
    public static int corner(double x,double y,double z,int axis,int sign) {
        double a=axis==0?y:axis==1?z:x,b=axis==0?z:axis==1?x:y;
        boolean first=a>0.5,second=b>0.5;
        if(sign<0) { boolean swap=first;first=second;second=swap; }
        return second?(first?2:3):(first?1:0);
    }
    public void set(int corner,float u,float v) { this.u[corner]=u;this.v[corner]=v; }
    public float u(double s,double t) { return interpolate(u,s,t); }
    public float v(double s,double t) { return interpolate(v,s,t); }
    private static float interpolate(float[] values,double s,double t) {
        return (float)((1-t)*((1-s)*values[0]+s*values[1])+t*((1-s)*values[3]+s*values[2]));
    }
}
