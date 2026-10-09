package dev.tossfront.record;
import java.util.*;

/** Short-lived optical motion between landmark results; never invents a hand pose. */
final class HandMotionTracker {
    private static final int PATCH=4;
    private static final class Track {
        HandGestureCore.Hand hand;byte[] image;long poseAt,frameAt;
        Track(HandGestureCore.Hand h,byte[] frame,long at){hand=h;image=frame;poseAt=frameAt=at;}
    }
    private final ArrayList<Track> tracks=new ArrayList<>();private int width,height;
    void clear(){tracks.clear();}
    void seed(List<HandGestureCore.Hand> hands,byte[] image,int w,int h,long at){clear();width=w;height=h;for(HandGestureCore.Hand hand:hands)tracks.add(new Track(hand,image,at));}
    List<HandGestureCore.Hand> update(byte[] image,int w,int h,long at){
        ArrayList<HandGestureCore.Hand> result=new ArrayList<>();if(w!=width||h!=height){clear();return result;}
        byte[] retained=null;
        for(Iterator<Track> it=tracks.iterator();it.hasNext();){Track t=it.next();if(at-t.poseAt>650||at<t.frameAt){it.remove();continue;}
            int x=Math.round(t.hand.xy[16]*w),y=Math.round(t.hand.xy[17]*h),radius=at-t.frameAt>130?48:20;
            int[] next=match(t.image,image,w,h,x,y,radius);
            if(next!=null){
                float dx=(next[0]-x)/(float)w,dy=(next[1]-y)/(float)h;float[] xy=t.hand.xy.clone();
                for(int i=0;i<21;i++){xy[i*2]=HandGestureCore.bound(xy[i*2]+dx,0,1);xy[i*2+1]=HandGestureCore.bound(xy[i*2+1]+dy,0,1);}
                t.hand=new HandGestureCore.Hand(xy);if(retained==null)retained=image.clone();t.image=retained;t.frameAt=at;
            }
            result.add(t.hand);
        }
        return result;
    }
    private static int[] match(byte[] a,byte[] b,int w,int h,int x,int y,int radius){
        if(x<PATCH||x>=w-PATCH||y<PATCH||y>=h-PATCH)return null;
        int min=255,max=0;for(int yy=-PATCH;yy<=PATCH;yy++)for(int xx=-PATCH;xx<=PATCH;xx++){int v=a[(y+yy)*w+x+xx]&255;min=Math.min(min,v);max=Math.max(max,v);}
        if(max-min<24)return null;
        int best=Integer.MAX_VALUE,bx=x,by=y;
        for(int dy=-radius;dy<=radius;dy+=2)for(int dx=-radius;dx<=radius;dx+=2){int nx=x+dx,ny=y+dy;if(nx<PATCH||nx>=w-PATCH||ny<PATCH||ny>=h-PATCH)continue;int score=score(a,b,w,x,y,nx,ny,best)+2*(Math.abs(dx)+Math.abs(dy));if(score<best){best=score;bx=nx;by=ny;}}
        int cx=bx,cy=by;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=cx+dx,ny=cy+dy;if(nx<PATCH||nx>=w-PATCH||ny<PATCH||ny>=h-PATCH)continue;int score=score(a,b,w,x,y,nx,ny,best)+2*(Math.abs(nx-x)+Math.abs(ny-y));if(score<best){best=score;bx=nx;by=ny;}}
        return best<=81*18?new int[]{bx,by}:null;
    }
    private static int score(byte[] a,byte[] b,int w,int x,int y,int nx,int ny,int bound){int sum=0;for(int yy=-PATCH;yy<=PATCH;yy++){for(int xx=-PATCH;xx<=PATCH;xx++)sum+=Math.abs((a[(y+yy)*w+x+xx]&255)-(b[(ny+yy)*w+nx+xx]&255));if(sum>=bound)return sum;}return sum;}
}
