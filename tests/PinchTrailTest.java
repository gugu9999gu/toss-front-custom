package dev.tossfront.record;
import java.util.*;
public final class PinchTrailTest {
    static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static List<HandGestureCore.Hand> one(float x,float y,boolean pinch){return Collections.singletonList(RecordGestureTest.hand(x,y,pinch));}
    public static void main(String[] args){
        PinchTrail trail=new PinchTrail();trail.update(one(.3f,.5f,false),1000,true);
        check(trail.strokes.isEmpty(),"Open fingertips cannot start a trail");
        trail.update(one(.3f,.5f,true),1100,true);PinchTrail.Stroke first=trail.strokes.get(0);
        check(first.count==1&&Math.abs(first.xs[0]-.3f)<.0001,"Trail begins at the thumb-index contact");
        trail.update(one(.35f,.45f,true),1200,true);trail.update(one(.4f,.4f,true),1300,true);
        check(first.count==3&&first.xs[0]==.3f&&first.xs[2]==.4f,"Movement retains the entire contact path");
        trail.update(one(.4f,.4f,true),1400,true);check(first.count==3,"Stationary hands do not allocate duplicate samples");
        trail.update(Arrays.asList(RecordGestureTest.hand(.4f,.4f,true),RecordGestureTest.hand(.75f,.5f,true)),1500,true);
        check(trail.strokes.size()==2,"Both hands draw separate contact trails");
        PinchTrail.Stroke second=trail.strokes.get(1);
        trail.update(Arrays.asList(RecordGestureTest.hand(.77f,.48f,true),RecordGestureTest.hand(.42f,.38f,true)),1600,true);
        check(trail.strokes.size()==2&&Math.abs(first.xs[first.count-1]-.42)<.0001&&Math.abs(second.xs[second.count-1]-.77)<.0001,"Model hand reordering never joins left and right strokes");
        trail.update(Arrays.asList(RecordGestureTest.hand(.42f,.38f,false),RecordGestureTest.hand(.77f,.48f,false)),1700,true);
        check(!first.active()&&!second.active()&&first.alpha(1700)==1,"Releasing fingers leaves visible finished strokes");
        check(Math.abs(first.alpha(3200)-.5f)<.0001,"Afterimages fade smoothly over three seconds");
        trail.advance(4700);check(trail.strokes.isEmpty(),"Expired paths and coordinates are removed from memory");
        trail.update(one(.4f,.5f,true),5000,true);trail.update(Collections.emptyList(),5200,true);trail.update(one(.45f,.5f,true),5300,true);
        check(trail.strokes.size()==1&&trail.strokes.get(0).count==2,"Brief tracking omissions preserve path continuity");
        trail.advance(5801);check(!trail.strokes.get(0).active(),"Long tracking loss finishes the path");
        trail.update(one(.5f,.5f,true),5900,true);check(trail.strokes.size()==2,"Reacquisition after a long loss starts a new stroke");
        trail.update(one(.8f,.5f,true),6000,true);check(trail.strokes.get(trail.strokes.size()-1).count==1,"Large coordinate jumps cannot draw a line across the screen");
        trail.update(one(.8f,.5f,true),6100,false);check(trail.strokes.isEmpty(),"Disabling trails clears retained coordinates immediately");
        for(int i=0;i<20;i++){long t=7000+i*10;trail.update(one(.4f,.5f,true),t,true);trail.update(one(.4f,.5f,false),t+1,true);}
        check(trail.strokes.size()<=PinchTrail.MAX_STROKES,"Repeated gestures have bounded stroke storage");
        trail.clear();for(int i=0;i<3000;i++)trail.update(one(.4f+.1f*(float)Math.sin(i*.3),.5f,true),10000+i,true);
        check(trail.strokes.size()==1&&trail.strokes.get(0).count<=PinchTrail.MAX_POINTS&&trail.strokes.get(0).xs[0]==.4f,"Long strokes simplify samples while preserving their start and memory bound");
        trail.advance(9000);check(trail.strokes.isEmpty(),"Clock reset cannot retain stale strokes");
        System.out.println(checks+" ephemeral pinch trail checks passed");
    }
}
