package dev.tossfront.record;

import java.util.*;

/** Display interpolation and brief persistence cannot create playback commands. */
final class HandDisplay {
    private static final class Slot {HandGestureCore.Hand hand,target;long seen,drawn;}
    private final Slot[] slots={new Slot(),new Slot()};private long last;
    final ArrayList<HandGestureCore.Hand> hands=new ArrayList<>();final float[] alphas=new float[2];
    void clear(){for(Slot s:slots){s.hand=s.target=null;s.seen=s.drawn=0;}hands.clear();Arrays.fill(alphas,0);last=0;}
    void update(List<HandGestureCore.Hand> observed,long now){
        if(now<last)clear();expire(now);boolean[] used=new boolean[2];
        for(HandGestureCore.Hand h:observed){
            int nearest=-1;double distance=.23;
            for(int i=0;i<2;i++)if(!used[i]&&slots[i].target!=null){HandGestureCore.Hand old=slots[i].target;double d=Math.hypot(old.x-h.x,old.y-h.y);if(d<distance){distance=d;nearest=i;}}
            if(nearest<0)for(int i=0;i<2;i++)if(!used[i]&&slots[i].hand==null){nearest=i;break;}
            if(nearest<0)for(int i=0;i<2;i++)if(!used[i]){nearest=i;break;}
            if(nearest>=0){Slot s=slots[nearest];if(s.hand==null||Math.hypot(s.target.x-h.x,s.target.y-h.y)>=.23){s.hand=h;s.drawn=now;}s.target=h;s.seen=now;used[nearest]=true;}
        }
        advance(now);
    }
    void advance(long now){
        if(now<last){clear();return;}last=now;expire(now);hands.clear();Arrays.fill(alphas,0);
        for(Slot s:slots)if(s.hand!=null){
            if(s.hand!=s.target&&now>s.drawn){
                float blend=(float)(1-Math.exp(-Math.min(64,now-s.drawn)/110.0)),max=0;float[] xy=s.hand.xy.clone();
                for(int i=0;i<xy.length;i++){float delta=s.target.xy[i]-xy[i];max=Math.max(max,Math.abs(delta));xy[i]+=delta*blend;}
                s.hand=max<.0005f?s.target:new HandGestureCore.Hand(xy,s.target.observedAt);
            }
            s.drawn=now;long age=now-s.seen;float alpha=age<=160?1:Math.max(0,1-(age-160)/260f);alphas[hands.size()]=alpha;hands.add(s.hand);
        }
    }
    private void expire(long now){for(Slot s:slots)if(s.hand!=null&&now-s.seen>=420)s.hand=s.target=null;}
    void output(HandGestureCore.Output out){
        out.hands=hands.size();for(int i=0;i<out.hands;i++){HandGestureCore.Hand h=hands.get(i);out.xs[i]=h.pinchRatio<=.78f?h.pinchX:h.x;out.ys[i]=h.pinchRatio<=.78f?h.pinchY:h.y;out.powers[i]=(.8f+h.open*.6f)*alphas[i];}
    }
    boolean moving(){for(Slot s:slots)if(s.hand!=null&&s.target!=s.hand)for(int i=0;i<42;i++)if(Math.abs(s.hand.xy[i]-s.target.xy[i])>=.0005f)return true;return false;}
    boolean fading(){for(Slot s:slots)if(s.hand!=null&&s.seen<last)return true;return false;}
}
