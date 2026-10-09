package dev.tossfront.record;

import java.util.*;

/** Ephemeral contact strokes. No images, storage, logging, or Android dependency. */
final class PinchTrail {
    static final int MAX_POINTS=1024,MAX_STROKES=8;
    static final long FADE_MS=3000,MISSING_MS=400;
    static final class Stroke {
        final float[] xs=new float[MAX_POINTS],ys=new float[MAX_POINTS];
        int count;long seen,ended=-1;
        boolean active(){return ended<0;}
        void add(float x,float y,long now){
            seen=now;if(count>0&&Math.hypot(x-xs[count-1],y-ys[count-1])<.0025)return;
            if(count==MAX_POINTS){for(int i=1;i<MAX_POINTS/2;i++){xs[i]=xs[i*2];ys[i]=ys[i*2];}count=MAX_POINTS/2;}
            xs[count]=x;ys[count++]=y;
        }
        float alpha(long now){if(active())return 1;float t=Math.max(0,Math.min(1,1-(now-ended)/(float)FADE_MS));return t*t*(3-2*t);}
    }
    final ArrayList<Stroke> strokes=new ArrayList<>();
    private final Stroke[] live=new Stroke[2];private long last;
    void clear(){strokes.clear();Arrays.fill(live,null);last=0;}
    private void finish(int index,long now){if(live[index]!=null){live[index].ended=now;live[index]=null;}}
    boolean advance(long now){
        if(last>now){clear();return false;}last=now;
        for(int i=0;i<2;i++)if(live[i]!=null&&now-live[i].seen>MISSING_MS)finish(i,live[i].seen+MISSING_MS);
        for(Iterator<Stroke> it=strokes.iterator();it.hasNext();){Stroke s=it.next();if(!s.active()&&now-s.ended>=FADE_MS)it.remove();}
        return !strokes.isEmpty();
    }
    void update(List<HandGestureCore.Hand> hands,long now,boolean enabled){
        if(!enabled){clear();return;}advance(now);boolean[] used=new boolean[2];
        for(int n=0;n<Math.min(2,hands.size());n++){
            HandGestureCore.Hand hand=hands.get(n);if(hand.pinchRatio>.78f)continue;
            int slot=-1;double distance=.22;
            for(int i=0;i<2;i++)if(!used[i]&&live[i]!=null){Stroke s=live[i];double d=Math.hypot(hand.pinchX-s.xs[s.count-1],hand.pinchY-s.ys[s.count-1]);if(d<distance){distance=d;slot=i;}}
            if(slot<0&&hand.pinching){
                for(int i=0;i<2;i++)if(!used[i]&&live[i]==null){slot=i;break;}
                if(slot<0)for(int i=0;i<2;i++)if(!used[i]){slot=i;finish(i,now);break;}
                if(slot>=0){Stroke s=new Stroke();live[slot]=s;strokes.add(s);while(strokes.size()>MAX_STROKES)strokes.remove(0);}
            }
            if(slot>=0){used[slot]=true;live[slot].add(hand.pinchX,hand.pinchY,now);}
        }
        for(int i=0;i<2;i++)if(live[i]!=null&&!used[i]){
            Stroke s=live[i];for(HandGestureCore.Hand hand:hands)if(hand.pinchRatio>.78f&&Math.hypot(hand.pinchX-s.xs[s.count-1],hand.pinchY-s.ys[s.count-1])<.25){finish(i,now);break;}
        }
    }
    boolean fading(){for(Stroke s:strokes)if(!s.active())return true;return false;}
}
