package dev.tossfront.record;
import java.util.*;
public final class RecordGestureTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static HandGestureCore.Hand hand(float cx,float cy,float tx,float ty,boolean open){
        float[] xy=new float[42];for(int i=0;i<21;i++){xy[i*2]=cx;xy[i*2+1]=cy;}
        xy[0]=cx;xy[1]=cy+.2f;xy[10]=cx-.08f;xy[11]=cy;xy[34]=cx+.08f;xy[35]=cy;
        for(int tip:new int[]{8,12,16,20}){xy[(tip-3)*2]=cx+(tip-12)*.007f;xy[(tip-3)*2+1]=cy;xy[(tip-2)*2]=cx;xy[(tip-2)*2+1]=cy-.1f;xy[tip*2]=cx;xy[tip*2+1]=open?cy-.3f:cy+.03f;}
        xy[16]=tx;xy[17]=ty;return new HandGestureCore.Hand(xy);
    }
    static HandGestureCore.Output frame(HandGestureCore core,long t,float tx,float ty){return core.update(Collections.singletonList(hand(.5f,.6f,tx,ty,false)),t,true);}
    public static void main(String[] args){
        check(hand(.5f,.6f,.5f,.25f,false).indexOnly,"Only extended index is recognized");
        check(!hand(.5f,.6f,.5f,.25f,true).indexOnly,"Open palm is never a pointing command");
        for(int direction:new int[]{1,-1}){
            HandGestureCore core=new HandGestureCore();int delta=0,next=0;
            for(int i=0;i<=20;i++){double a=direction*i*Math.PI*2/20;HandGestureCore.Output o=frame(core,10000+i*100,.5f+(float)Math.cos(a)*.09f,.25f+(float)Math.sin(a)*.09f);delta+=o.volumeDelta;next+=o.next?1:0;}
            check(delta==direction*2,"A closed index circle yields one bounded volume action");
            check(next==0,"Circular movement never becomes a next-track command");
        }
        HandGestureCore point=new HandGestureCore();int commands=0;
        for(int i=0;i<20;i++)commands+=frame(point,1000+i*100,.5f,.25f).volumeDelta;
        check(commands==0,"Stationary index never changes volume");
        HandGestureCore flick=new HandGestureCore();frame(flick,1000,.45f,.25f);
        check(!frame(flick,1150,.57f,.25f).next,"One right flick is insufficient");
        frame(flick,1300,.45f,.25f);
        check(frame(flick,1500,.57f,.25f).next,"Two right flicks with recoil trigger next");
        frame(flick,1600,.45f,.25f);check(!frame(flick,1800,.57f,.25f).next,"Next track is debounced");
        HandGestureCore clap=new HandGestureCore();
        clap.update(Arrays.asList(hand(.25f,.6f,.25f,.3f,true),hand(.75f,.6f,.75f,.3f,true)),2000,true);
        check(clap.update(Arrays.asList(hand(.46f,.6f,.46f,.3f,true),hand(.54f,.6f,.54f,.3f,true)),2200,true).toggle,"Approaching open hands toggle playback once");
        check(!clap.update(Arrays.asList(hand(.46f,.6f,.46f,.3f,true),hand(.54f,.6f,.54f,.3f,true)),2300,true).toggle,"Held contact cannot repeatedly toggle");
        HandGestureCore modal=new HandGestureCore();modal.update(Arrays.asList(hand(.25f,.6f,.25f,.3f,true),hand(.75f,.6f,.75f,.3f,true)),3000,false);
        check(!modal.update(Arrays.asList(hand(.43f,.6f,.43f,.3f,true),hand(.57f,.6f,.57f,.3f,true)),3200,false).toggle,"Settings disable playback commands");
        HandGestureCore lost=new HandGestureCore();frame(lost,1000,.45f,.25f);frame(lost,1150,.57f,.25f);lost.update(Collections.emptyList(),1250,true);check(!frame(lost,1400,.57f,.25f).next,"Lost tracking cancels a partial gesture");
        HandGestureCore.Output empty=lost.update(Collections.emptyList(),1500,true);check(empty.strength==1&&empty.spread==1&&empty.hands==0,"No hand restores neutral influence");
        HandGestureCore.Output spread=lost.update(Arrays.asList(hand(.2f,.6f,.2f,.3f,true),hand(.8f,.6f,.8f,.3f,true)),1700,false);check(spread.spread>1&&spread.strength>1,"Open separated hands expand and amplify visualization");
        HandGestureCore slowCircle=new HandGestureCore();int slowDelta=0,slowNext=0;
        for(int i=0;i<=10;i++){double a=i*Math.PI*2/10;HandGestureCore.Output o=frame(slowCircle,20000+i*400,.5f+(float)Math.cos(a)*.09f,.25f+(float)Math.sin(a)*.09f);slowDelta+=o.volumeDelta;slowNext+=o.next?1:0;}
        check(slowDelta==2,"A low-frame-rate four-second circle still changes volume once");
        check(slowNext==0,"Low-frame-rate circles never become lateral taps");
        HandGestureCore slowFlick=new HandGestureCore();frame(slowFlick,1000,.45f,.25f);frame(slowFlick,1500,.57f,.25f);frame(slowFlick,1650,.45f,.25f);
        check(frame(slowFlick,2000,.57f,.25f).next,"Two measured right strokes tolerate slower camera updates");
        HandGestureCore separate=new HandGestureCore();List<HandGestureCore.Hand> separated=Arrays.asList(hand(.25f,.6f,.25f,.3f,true),hand(.75f,.6f,.75f,.3f,true));
        separate.update(separated,3000,true);check(!separate.update(separated,3200,true).toggle,"Stationary separated hands never become a clap");
        HandGestureCore twoHands=new HandGestureCore();int twoDelta=0;boolean falseClap=false;
        for(int i=0;i<=20;i++){double angle=i*Math.PI*2/20;HandGestureCore.Output o=twoHands.update(Arrays.asList(hand(.5f,.6f,.5f+(float)Math.cos(angle)*.09f,.25f+(float)Math.sin(angle)*.09f,false),hand(.2f,.6f,.2f,.63f,false)),30000+i*100,true);twoDelta+=o.volumeDelta;falseClap|=o.toggle;}
        check(twoDelta==2,"An idle second hand cannot block an index-circle command");
        check(!falseClap,"Pointing with a second hand present cannot become a clap");
        System.out.println(checks+" temporal hand gesture checks passed");
    }
}
