package dev.tossfront.record;
import java.util.*;
public final class RecordGestureTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static HandGestureCore.Hand hand(float x,float y,boolean pinch){return handGap(x,y,pinch?.024f:.20f);}
    static HandGestureCore.Hand handGap(float x,float y,float gap){
        float[] xy=new float[42];for(int i=0;i<21;i++){xy[i*2]=x;xy[i*2+1]=y+.1f;}
        xy[0]=x;xy[1]=y+.24f;xy[10]=x-.08f;xy[11]=y+.12f;xy[34]=x+.08f;xy[35]=y+.12f;
        xy[8]=x-gap*.5f;xy[9]=y;xy[16]=x+gap*.5f;xy[17]=y;return new HandGestureCore.Hand(xy);
    }
    static HandGestureCore.Output frame(HandGestureCore c,long t,float x,float y){return c.update(Collections.singletonList(hand(x,y,true)),t,true);}
    static HandGestureCore.Output pair(HandGestureCore c,long t,float a,float b){return c.update(Arrays.asList(hand(.3f,a,true),hand(.7f,b,true)),t,true);}
    public static void main(String[] args){
        check(hand(.5f,.5f,true).pinching,"Touching thumb and index make a pinch");
        check(!hand(.5f,.5f,false).pinching,"Separated fingertips cannot arm commands");
        HandGestureCore up=new HandGestureCore();frame(up,1000,.5f,.6f);check(frame(up,1200,.5f,.54f).volumeDelta==0,"One-hand pinch must settle before commands");
        check(frame(up,1400,.5f,.53f).volumeDelta==1,"Upward pinch raises volume");
        check(frame(up,1650,.5f,.47f).volumeDelta>0,"Continuing upward motion changes volume by distance");
        check(frame(up,1900,.5f,.56f).volumeDelta<0,"Reversing vertical direction lowers volume");
        HandGestureCore down=new HandGestureCore();frame(down,2000,.5f,.4f);check(frame(down,2400,.5f,.48f).volumeDelta==-1,"Downward pinch lowers volume");
        HandGestureCore next=new HandGestureCore();frame(next,1000,.4f,.5f);HandGestureCore.Output right=frame(next,1400,.55f,.5f);
        check(right.navigation==1&&right.volumeDelta==0,"Right movement selects next without changing volume");
        check(frame(next,1800,.65f,.5f).navigation==0,"Holding a pinch cannot repeatedly skip songs");
        next.update(Collections.singletonList(hand(.65f,.5f,false)),2000,true);frame(next,2200,.4f,.5f);
        check(frame(next,2600,.55f,.5f).navigation==1,"Opening and pinching again rearms navigation");
        HandGestureCore prev=new HandGestureCore();frame(prev,1000,.6f,.5f);check(frame(prev,1400,.45f,.5f).navigation==-1,"Left movement selects previous");
        HandGestureCore jitter=new HandGestureCore();int events=0;
        for(int i=0;i<20;i++){HandGestureCore.Output o=frame(jitter,1000+i*200,.5f+(i%2==0?.009f:-.009f),.5f+(i%3==0?.01f:-.01f));events+=Math.abs(o.volumeDelta)+Math.abs(o.navigation)+Math.abs(o.playback);}
        check(events==0,"Tracking jitter cannot change playback or volume");
        HandGestureCore diagonal=new HandGestureCore();frame(diagonal,1000,.4f,.4f);HandGestureCore.Output diag=frame(diagonal,1400,.54f,.54f);
        check(diag.navigation==0&&diag.volumeDelta==0,"Ambiguous diagonal movement does not choose two actions");
        HandGestureCore play=new HandGestureCore();pair(play,1000,.6f,.6f);HandGestureCore.Output raised=pair(play,1400,.49f,.49f);
        check(raised.playback==1&&raised.volumeDelta==0&&raised.navigation==0,"Both pinches raised explicitly request play");
        check(pair(play,1600,.45f,.45f).playback==0,"Held two-hand gesture never toggles or repeats playback");
        HandGestureCore pause=new HandGestureCore();pair(pause,1000,.4f,.4f);check(pair(pause,1400,.51f,.51f).playback==-1,"Both pinches lowered explicitly request pause");
        HandGestureCore uneven=new HandGestureCore();pair(uneven,1000,.6f,.6f);check(pair(uneven,1400,.49f,.59f).playback==0,"One moving hand cannot become a two-hand play command");
        HandGestureCore opposite=new HandGestureCore();pair(opposite,1000,.5f,.5f);check(pair(opposite,1400,.39f,.61f).playback==0,"Opposite vertical directions never change playback");
        HandGestureCore lost=new HandGestureCore();pair(lost,1000,.5f,.5f);HandGestureCore.Output remaining=frame(lost,1400,.3f,.6f);
        check(remaining.volumeDelta==0&&remaining.navigation==0&&remaining.playback==0,"Losing a paired hand cannot produce a single-hand command");
        HandGestureCore slow=new HandGestureCore();frame(slow,1000,.4f,.5f);frame(slow,1600,.43f,.5f);frame(slow,2200,.46f,.5f);frame(slow,2800,.49f,.5f);
        check(frame(slow,3400,.54f,.5f).navigation==1,"Slow sparse updates still recognize distance-based navigation");
        HandGestureCore gap=new HandGestureCore();frame(gap,1000,.4f,.5f);check(frame(gap,2000,.54f,.5f).navigation==0,"Long tracking gaps cannot join unrelated motion");
        HandGestureCore jump=new HandGestureCore();frame(jump,1000,.3f,.5f);check(frame(jump,1400,.75f,.5f).navigation==0,"Implausible position jumps cancel the gesture");
        HandGestureCore modal=new HandGestureCore();modal.update(Collections.singletonList(hand(.4f,.5f,true)),1000,false);
        HandGestureCore.Output disabled=modal.update(Collections.singletonList(hand(.55f,.5f,true)),1400,false);
        check(disabled.navigation==0&&disabled.volumeDelta==0&&disabled.playback==0,"Settings block every playback gesture");
        HandGestureCore hysteresis=new HandGestureCore();frame(hysteresis,1000,.5f,.5f);
        HandGestureCore.Output noisy=hysteresis.update(Collections.singletonList(handGap(.5f,.42f,.10f)),1400,true);
        check(noisy.volumeDelta==1,"A slightly noisy fingertip gap does not break a latched pinch");
        HandGestureCore swap=new HandGestureCore();pair(swap,1000,.6f,.6f);
        HandGestureCore.Output swapped=swap.update(Arrays.asList(hand(.7f,.49f,true),hand(.3f,.49f,true)),1400,true);
        check(swapped.playback==1,"Landmark list reordering does not reverse two-hand motion");
        HandGestureCore priority=new HandGestureCore();frame(priority,1000,.3f,.6f);pair(priority,1200,.6f,.6f);
        check(pair(priority,1600,.49f,.49f).playback==1,"A second pinch takes priority over unfinished one-hand control");
        HandGestureCore.Output visual=new HandGestureCore().update(Arrays.asList(hand(.3f,.4f,true),hand(.7f,.6f,true)),1000,false);
        check(visual.hands==2&&Math.abs(visual.xs[0]-.3f)<.001&&Math.abs(visual.xs[1]-.7f)<.001,"Each pinch contact is retained independently for local visualization");
        GestureInfluence field=new GestureInfluence();field.update(visual);field.displace(field.xs[0]+.06f,field.ys[0],1,1);
        check(field.dx>0&&Math.abs(field.dy)<.001,"Nearby particles move away from the pinch contact");
        field.displace(.98f,.02f,1,1);check(field.dx==0&&field.dy==0,"Distant waveform regions remain unchanged");
        field.displace(field.xs[0],field.ys[0],1,1);check(Float.isFinite(field.dx)&&Float.isFinite(field.dy),"Exact contact produces bounded finite displacement");
        field.clear();field.displace(.3f,.4f,1,1);check(field.dx==0&&field.dy==0&&!field.active,"Disabling tracking removes the local field");
        System.out.println(checks+" pinch gesture and native hand-field checks passed");
    }
}
