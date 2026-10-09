package dev.tossfront.record;
import java.util.*;
public final class HandMotionTrackerTest {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static HandGestureCore.Hand hand(float x,float y,boolean pinch,long at){return new HandGestureCore.Hand(RecordGestureTest.hand(x,y,pinch).xy,at);}
    public static void main(String[] args){
        HandMotionTracker t=new HandMotionTracker();t.acceptModel(Collections.singletonList(hand(.4f,.3f,true,1000)),1000,1200);
        check(t.update(1220).size()==1,"Inference delay does not discard a newly received hand");
        check(Math.abs(t.update(1250).get(0).pinchX-.4)<.001,"Polling does not invent a movement between model observations");
        t.acceptModel(Collections.singletonList(hand(.46f,.34f,true,1300)),1300,1400);
        List<HandGestureCore.Hand> moved=t.update(1450);
        check(Math.abs(moved.get(0).pinchX-.46)<.001&&Math.abs(moved.get(0).pinchY-.34)<.001,"Confirmed model movement is retained exactly for gesture decisions");
        check(moved.get(0).observedAt==1300,"Displayed poses retain the real model source time");
        t.acceptModel(Collections.singletonList(hand(.85f,.1f,true,1500)),1500,1600);
        check(Math.abs(t.update(1620).get(0).pinchX-.46)<.001,"A single implausible landmark jump is quarantined");
        t.acceptModel(Collections.singletonList(hand(.48f,.35f,true,1650)),1650,1700);
        check(Math.abs(t.update(1750).get(0).pinchX-.48)<.001,"Returning to the confirmed trajectory clears a false jump");
        t.acceptModel(Collections.singletonList(hand(.85f,.1f,true,1800)),1800,1850);
        t.acceptModel(Collections.singletonList(hand(.86f,.11f,true,1900)),1900,1950);
        check(Math.abs(t.update(2000).get(0).pinchX-.86)<.001,"Two consistent new model observations can reacquire a genuinely moved hand");
        t.acceptModel(Collections.singletonList(hand(.86f,.11f,false,2050)),2050,2100);
        check(!t.update(2150).get(0).pinching,"A confirmed open finger pose is never replaced with the old pinch shape");
        t.clear();t.acceptModel(Arrays.asList(hand(.3f,.4f,true,3000),hand(.7f,.4f,true,3000)),3000,3100);
        t.acceptModel(Arrays.asList(hand(.7f,.42f,true,3200),hand(.3f,.42f,true,3200)),3200,3300);
        check(t.update(3350).get(0).pinchX<.5&&t.update(3350).get(1).pinchX>.5,"Two hands keep their identities when the model list order changes");
        t.acceptModel(Collections.singletonList(hand(.3f,.44f,true,3400)),3400,3500);
        check(t.update(3550).size()==2,"A briefly omitted second hand remains available for bounded recovery");
        check(t.update(3750).size()==1,"A missing second hand expires independently");
        check(t.update(3950).isEmpty(),"Model-only tracking cannot freeze an unseen hand indefinitely");
        t.acceptModel(Collections.singletonList(hand(.4f,.3f,true,4000)),4000,4500);
        check(t.update(4750).isEmpty(),"An old source frame cannot stay alive just because inference arrived late");
        float[] invalid=new float[42];Arrays.fill(invalid,.5f);t.acceptModel(Collections.singletonList(new HandGestureCore.Hand(invalid,5000)),5000,5000);
        check(t.update(5050).isEmpty(),"Collapsed model coordinates cannot create a hand");
        t.acceptModel(Collections.singletonList(hand(.4f,.3f,true,5100)),5100,5150);t.clear();check(t.update(5200).isEmpty(),"Camera stop clears all model observations");
        System.out.println(checks+" model-only hand association checks passed");
    }
}
