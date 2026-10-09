package dev.tossfront.record;
import java.util.*;

public final class HandDisplayTest {
    static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        HandDisplay display=new HandDisplay();HandGestureCore.Hand left=RecordGestureTest.hand(.3f,.4f,true),right=RecordGestureTest.hand(.7f,.4f,true);
        display.update(Arrays.asList(left,right),1000);check(display.hands.size()==2&&display.alphas[0]==1,"Both observed hands are fully visible");
        display.update(Arrays.asList(right,left),1100);check(Math.abs(display.hands.get(0).pinchX-.3)<.001,"Model list reordering keeps stable display slots");
        display.update(Collections.singletonList(right),1200);check(display.hands.size()==2&&display.alphas[0]==1,"A briefly omitted hand does not blink off");
        display.update(Collections.singletonList(right),1350);check(display.alphas[0]>0&&display.alphas[0]<1,"An omitted hand fades instead of staying fully visible");
        HandGestureCore.Output out=new HandGestureCore.Output();display.output(out);
        check(out.hands==2&&out.playback==0&&out.navigation==0&&out.volumeDelta==0,"Display persistence cannot create a playback command");
        check(out.powers[0]<out.powers[1],"The missing hand's local waveform influence fades with its display");
        display.update(Collections.singletonList(right),1550);check(display.hands.size()==1&&Math.abs(display.hands.get(0).pinchX-.7)<.001,"Expired missing hands are discarded independently");
        display.update(Collections.emptyList(),2000);check(display.hands.isEmpty(),"All display coordinates expire after tracking stops");
        display.update(Collections.singletonList(left),2200);display.clear();check(display.hands.isEmpty()&&!display.fading(),"Camera disable clears display persistence immediately");
        display.update(Collections.singletonList(left),2400);display.update(Collections.emptyList(),2300);check(display.hands.isEmpty(),"Clock reset cannot retain old display geometry");
        HandGestureCore core=new HandGestureCore();core.update(Collections.singletonList(new HandGestureCore.Hand(left.xy,3000)),3000,true);
        HandGestureCore.Output stale=core.update(Collections.singletonList(new HandGestureCore.Hand(RecordGestureTest.hand(.3f,.2f,true).xy,3000)),3600,true);
        check(stale.recovering&&stale.volumeDelta==0&&stale.playback==0&&stale.navigation==0,"Optical-only stale poses may be shown but cannot issue new commands");
        display.clear();display.update(Collections.singletonList(left),4000);display.update(Collections.singletonList(RecordGestureTest.hand(.4f,.4f,true)),4020);
        float before=display.hands.get(0).pinchX;display.advance(4036);float after=display.hands.get(0).pinchX;
        check(after>before&&after<.4f,"Display motion advances smoothly between model updates");
        check(display.moving(),"Unfinished display interpolation schedules another animation frame");
        display.advance(4500);check(display.hands.isEmpty()&&!display.moving(),"Display animation stops when observations expire");
        System.out.println(checks+" hand display persistence and stale command checks passed");
    }
}
