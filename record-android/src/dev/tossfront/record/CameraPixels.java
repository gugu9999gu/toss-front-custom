package dev.tossfront.record;

/** Stride-aware YUV conversion with portrait rotation/mirroring in one reusable buffer. */
final class CameraPixels {
    static int width(int w,int h,int rotation){return rotation==90||rotation==270?h:w;}
    static int height(int w,int h,int rotation){return rotation==90||rotation==270?w:h;}
    static void convert(int w,int h,byte[] y,int ys,int yp,byte[] u,int us,int up,byte[] v,int vs,int vp,int rotation,boolean mirror,byte[] out){
        convert(w,h,y,ys,yp,u,us,up,v,vs,vp,rotation,mirror,1,out);
    }
    static void convert(int sourceWidth,int sourceHeight,byte[] y,int ys,int yp,byte[] u,int us,int up,byte[] v,int vs,int vp,int rotation,boolean mirror,int step,byte[] out){
        if((step!=1&&step!=2)||sourceWidth%step!=0||sourceHeight%step!=0)throw new IllegalArgumentException("Invalid camera sampling size");
        int w=sourceWidth/step,h=sourceHeight/step,ow=width(w,h,rotation);
        if(out.length!=w*h*4)throw new IllegalArgumentException("Invalid camera output buffer");
        for(int row=0;row<h;row++)for(int col=0;col<w;col++){
            int sourceRow=row*step,sourceCol=col*step,at=sourceRow*ys+sourceCol*yp;
            int luminance=y[at]&255;
            if(step==2)luminance=(luminance+(y[at+yp]&255)+(y[at+ys]&255)+(y[at+ys+yp]&255)+2)/4;
            int yy=Math.max(0,luminance-16)*298;
            int uu=(u[sourceRow/2*us+sourceCol/2*up]&255)-128,vv=(v[sourceRow/2*vs+sourceCol/2*vp]&255)-128;
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
