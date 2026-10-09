package dev.tossfront.record;
import java.util.*;
public final class HandMotionTrackerTest {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static byte[] image(int x,int y){byte[] a=new byte[10000];Arrays.fill(a,(byte)13);for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++)a[(y+dy)*100+x+dx]=(byte)(50+Math.floorMod(dx*37+dy*19+dx*dy*11,190));return a;}
    static HandGestureCore.Hand hand(){return RecordGestureTest.hand(.5f,.6f,.4f,.3f,false);}
    public static void main(String[] args){
        HandMotionTracker t=new HandMotionTracker();t.seed(Collections.singletonList(hand()),image(40,30),100,100,1000);
        List<HandGestureCore.Hand> moved=t.update(image(46,34),100,100,1100);
        check(moved.size()==1&&Math.abs(moved.get(0).xy[16]-.46)<.001&&Math.abs(moved.get(0).xy[17]-.34)<.001,"Inter-frame image movement relocates the index tip");
        check(moved.get(0).indexOnly,"Optical translation preserves the last recognized hand pose");
        moved=t.update(image(52,38),100,100,1200);check(Math.abs(moved.get(0).xy[16]-.52)<.001,"Tracking continues from the latest matched frame");
        moved=t.update(image(52,38),100,100,1300);check(Math.abs(moved.get(0).xy[16]-.52)<.001,"Stationary frames do not drift to repeated texture");
        check(t.update(image(52,38),100,100,1700).isEmpty(),"Optical flow never keeps an unconfirmed pose alive indefinitely");
        byte[] flat=new byte[10000];t.seed(Collections.singletonList(hand()),flat,100,100,2000);moved=t.update(flat,100,100,2100);check(moved.get(0).xy[16]==.4f,"Textureless images cannot invent movement");
        t.seed(Collections.emptyList(),image(40,30),100,100,2200);check(t.update(image(46,34),100,100,2300).isEmpty(),"A model result without hands clears optical tracks");
        t.seed(Collections.singletonList(hand()),image(40,30),100,100,2400);check(t.update(new byte[5000],50,100,2500).isEmpty(),"Changing image dimensions invalidates old tracks");
        System.out.println(checks+" inter-frame hand motion checks passed");
    }
}
