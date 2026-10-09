package dev.tossfront.record;

import java.util.*;

/** Distance-based pinch gestures tolerate sparse landmark samples; no camera/Android dependency. */
final class HandGestureCore {
    static final class Hand {
        final float[] xy;
        final float x,y,span,open,pinchX,pinchY,pinchRatio;
        final boolean pinching;
        final long observedAt;
        Hand(float[] values){this(values,-1);}
        Hand(float[] values,long observedAt){
            this.observedAt=observedAt;
            xy=values.clone();float cx=0,cy=0;for(int i:new int[]{0,5,9,13,17}){cx+=xy[i*2];cy+=xy[i*2+1];}
            x=cx/5;y=cy/5;span=Math.max(.025f,Math.max(distance(5,17),distance(0,9)*.9f));
            int extended=0;for(int tip:new int[]{8,12,16,20})if(distance(tip,0)>distance(tip-2,0)*1.23f)extended++;
            open=extended/4f;pinchX=(xy[8]+xy[16])*.5f;pinchY=(xy[9]+xy[17])*.5f;
            pinchRatio=distance(4,8)/span;pinching=pinchRatio<=.5f;
        }
        float distance(int a,int b){return (float)Math.hypot(xy[a*2]-xy[b*2],xy[a*2+1]-xy[b*2+1]);}
    }
    static final class Output {
        int volumeDelta,navigation,playback,hands,pinches,edge;boolean ready,release,recovering;
        final float[] xs=new float[2],ys=new float[2],powers=new float[2];
    }
    private long last,started,missingAt,pairedMissingAt;
    private int mode,axis;
    private boolean blocked;
    private int edgeZone;private long edgeSince,lastPinchAt,releaseAt;
    private final float[] anchorX=new float[2],anchorY=new float[2],previousX=new float[2],previousY=new float[2];
    void reset(){last=missingAt=0;cancel();blocked=false;}
    private void cancel(){mode=axis=edgeZone=0;started=pairedMissingAt=edgeSince=lastPinchAt=releaseAt=0;}
    private boolean edgeRelease(List<Hand> hands,Output out,long now){
        if(mode!=1||blocked||edgeZone==0||edgeSince==0||now-edgeSince<120||now-started<320||now-lastPinchAt>500||hands.size()!=1){releaseAt=0;return false;}
        Hand h=hands.get(0);long observed=h.observedAt<0?now:h.observedAt;
        if(h.pinchRatio<=.78f||now-observed>350||Math.hypot(h.pinchX-previousX[0],h.pinchY-previousY[0])>.23){releaseAt=0;return false;}
        // Repeated optical frames of the same open pose cannot confirm a release.
        if(releaseAt==0){releaseAt=observed;out.edge=edgeZone;return true;}
        if(observed-releaseAt>=60){out.navigation=edgeZone;cancel();blocked=false;return true;}
        out.edge=edgeZone;return true;
    }
    private void awaitPair(Output out,long now){
        if(pairedMissingAt==0)pairedMissingAt=now;
        if(now-pairedMissingAt<=450)out.recovering=true;
        else{cancel();blocked=true;missingAt=now;out.release=true;}
    }
    private boolean held(Hand h){
        if(h.pinching)return true;
        if(mode==0||h.pinchRatio>.78f)return false;
        for(int i=0;i<mode;i++)if(Math.hypot(h.pinchX-previousX[i],h.pinchY-previousY[i])<.23)return true;
        return false;
    }
    Output update(List<Hand> hands,long now,boolean commands){
        if(last>0&&(now<=last||now-last>800))reset();last=now;
        Output out=new Output();out.hands=Math.min(2,hands.size());
        ArrayList<Hand> pinches=new ArrayList<>(2);
        for(int i=0;i<out.hands;i++){
            Hand h=hands.get(i);out.xs[i]=h.pinchRatio<=.78f?h.pinchX:h.x;out.ys[i]=h.pinchRatio<=.78f?h.pinchY:h.y;out.powers[i]=.8f+h.open*.6f;
            if(held(h))pinches.add(h);
        }
        if(out.hands==2&&out.xs[0]>out.xs[1]){for(float[] values:new float[][]{out.xs,out.ys,out.powers}){float value=values[0];values[0]=values[1];values[1]=value;}}
        pinches.sort(Comparator.comparingDouble(h->h.pinchX));out.pinches=pinches.size();
        if(!commands){cancel();blocked=false;return out;}
        for(Hand h:hands)if(h.observedAt>=0&&now-h.observedAt>550){out.recovering=true;return out;}
        if(edgeRelease(hands,out,now))return out;
        if(pinches.isEmpty()){
            if(mode==2&&hands.isEmpty()){awaitPair(out,now);return out;}
            // A confirmed open hand releases the latch; a brief model dropout does not rearm it.
            if(!hands.isEmpty()){cancel();blocked=false;missingAt=0;}
            else{if(missingAt==0)missingAt=now;if(now-missingAt>600){cancel();blocked=false;}}
            return out;
        }
        if(missingAt!=0){cancel();missingAt=0;}
        if(mode==2&&pairedMissingAt!=0&&now-pairedMissingAt>450){cancel();blocked=true;}
        if(blocked){out.release=true;return out;}
        int count=pinches.size();
        // Losing one of two pinches cannot suddenly turn playback control into volume/skip.
        if(mode==2&&count==1){
            if(out.hands==2){blocked=true;out.release=true;}
            else awaitPair(out,now);
            return out;
        }
        if(mode==2&&count==2)pairedMissingAt=0;
        if(mode!=count){
            mode=count;axis=edgeZone=0;edgeSince=releaseAt=0;started=now;
            for(int i=0;i<count;i++){Hand h=pinches.get(i);anchorX[i]=previousX[i]=h.pinchX;anchorY[i]=previousY[i]=h.pinchY;}
            return out;
        }
        for(int i=0;i<count;i++){
            Hand h=pinches.get(i);
            if(Math.hypot(h.pinchX-previousX[i],h.pinchY-previousY[i])>.23){cancel();return out;}
            previousX[i]=h.pinchX;previousY[i]=h.pinchY;
        }
        out.ready=now-started>=(count==2?180:320);
        if(!out.ready)return out;
        if(count==2){
            float dy0=previousY[0]-anchorY[0],dy1=previousY[1]-anchorY[1];
            boolean vertical=Math.abs(previousX[0]-anchorX[0])<.13f&&Math.abs(previousX[1]-anchorX[1])<.13f;
            if(vertical&&dy0<-.08f&&dy1<-.08f){out.playback=1;blocked=true;out.release=true;}
            else if(vertical&&dy0>.08f&&dy1>.08f){out.playback=-1;blocked=true;out.release=true;}
            return out;
        }
        float dx=previousX[0]-anchorX[0],dy=previousY[0]-anchorY[0];
        lastPinchAt=now;releaseAt=0;
        int zone=previousX[0]<=.16f?-1:previousX[0]>=.84f?1:0;
        if(zone!=edgeZone){edgeZone=zone;edgeSince=zone==0?0:now;}
        if(zone!=0&&now-edgeSince>=120)out.edge=zone;
        if(axis==0){
            if(Math.abs(dy)>.04f&&Math.abs(dy)>Math.abs(dx)*1.35f)axis=2;
            else if(Math.abs(dx)>.07f&&Math.abs(dx)>Math.abs(dy)*1.35f)axis=1;
        }
        if(axis==2&&zone==0){
            int steps=(int)(-dy/.045f);out.volumeDelta=Math.max(-2,Math.min(2,steps));
            if(out.volumeDelta!=0)anchorY[0]-=out.volumeDelta*.045f;
        }
        return out;
    }
    static float bound(float v,float lo,float hi){return Math.max(lo,Math.min(hi,Float.isFinite(v)?v:lo));}
}
