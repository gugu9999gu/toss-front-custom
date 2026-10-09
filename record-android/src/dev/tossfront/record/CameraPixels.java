package dev.tossfront.record;

/** Stride-aware YUV conversion with portrait rotation/mirroring in one reusable buffer. */
final class CameraPixels {
    static int width(int w,int h,int rotation){return rotation==90||rotation==270?h:w;}
    static int height(int w,int h,int rotation){return rotation==90||rotation==270?w:h;}
    static void convert(int w,int h,byte[] y,int ys,int yp,byte[] u,int us,int up,byte[] v,int vs,int vp,int rotation,boolean mirror,byte[] out){
        int ow=width(w,h,rotation);
        for(int row=0;row<h;row++)for(int col=0;col<w;col++){
            int yy=Math.max(0,(y[row*ys+col*yp]&255)-16)*298;
            int uu=(u[row/2*us+col/2*up]&255)-128,vv=(v[row/2*vs+col/2*vp]&255)-128;
            int dx=col,dy=row;
            if(rotation==90){dx=h-1-row;dy=col;}else if(rotation==180){dx=w-1-col;dy=h-1-row;}else if(rotation==270){dx=row;dy=w-1-col;}
            if(mirror)dx=ow-1-dx;
            int offset=(dy*ow+dx)*4;
            out[offset]=(byte)channel((yy+409*vv+128)>>8);
            out[offset+1]=(byte)channel((yy-100*uu-208*vv+128)>>8);
            out[offset+2]=(byte)channel((yy+516*uu+128)>>8);out[offset+3]=(byte)255;
        }
    }
    private static int channel(int value){return Math.max(0,Math.min(255,value));}
}
