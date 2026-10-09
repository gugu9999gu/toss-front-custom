package dev.tossfront.record;

import java.util.*;

/** Distance-based pinch gestures tolerate sparse landmark samples; no camera/Android dependency. */
final class HandGestureCore {
    static final class Hand {
        final float[] xy;
        final float x,y,span,open,pinchX,pinchY,pinchRatio;
        final boolean pinching;
        Hand(float[] values){
            xy=values.clone();float cx=0,cy=0;for(int i:new int[]{0,5,9,13,17}){cx+=xy[i*2];cy+=xy[i*2+1];}
            x=cx/5;y=cy/5;span=Math.max(.025f,distance(5,17));
            int extended=0;for(int tip:new int[]{8,12,16,20})if(distance(tip,0)>distance(tip-2,0)*1.23f)extended++;
            open=extended/4f;pinchX=(xy[8]+xy[16])*.5f;pinchY=(xy[9]+xy[17])*.5f;
            pinchRatio=distance(4,8)/span;pinching=pinchRatio<=.5f;
        }
        float distance(int a,int b){return (float)Math.hypot(xy[a*2]-xy[b*2],xy[a*2+1]-xy[b*2+1]);}
    }
    static final class Output {
        int volumeDelta,navigation,playback,hands,pinches;boolean ready,release;
        final float[] xs=new float[2],ys=new float[2],powers=new float[2];
    }
    private long last,started,missingAt;
    private int mode,axis;
    private boolean blocked;
    private final float[] anchorX=new float[2],anchorY=new float[2],previousX=new float[2],previousY=new float[2];
    void reset(){last=started=missingAt=0;mode=axis=0;blocked=false;}
    private void cancel(){mode=axis=0;started=0;}
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
        pinches.sort(Comparator.comparingDouble(h->h.pinchX));out.pinches=pinches.size();
        if(!commands){cancel();blocked=false;return out;}
        if(pinches.isEmpty()){
            // A confirmed open hand releases the latch; a brief model dropout does not rearm it.
            if(!hands.isEmpty()){cancel();blocked=false;missingAt=0;}
            else{if(missingAt==0)missingAt=now;if(now-missingAt>600){cancel();blocked=false;}}
            return out;
        }
        if(missingAt!=0){cancel();missingAt=0;}
        if(blocked){out.release=true;return out;}
        int count=pinches.size();
        // Losing one of two pinches cannot suddenly turn playback control into volume/skip.
        if(mode==2&&count==1){blocked=true;out.release=true;return out;}
        if(mode!=count){
            mode=count;axis=0;started=now;
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
        if(axis==0){
            if(Math.abs(dy)>.04f&&Math.abs(dy)>Math.abs(dx)*1.35f)axis=2;
            else if(Math.abs(dx)>.07f&&Math.abs(dx)>Math.abs(dy)*1.35f)axis=1;
        }
        if(axis==2){
            int steps=(int)(-dy/.045f);out.volumeDelta=Math.max(-2,Math.min(2,steps));
            if(out.volumeDelta!=0)anchorY[0]-=out.volumeDelta*.045f;
        }else if(axis==1&&Math.abs(dx)>.11f){out.navigation=dx>0?1:-1;blocked=true;out.release=true;}
        return out;
    }
    static float bound(float v,float lo,float hi){return Math.max(lo,Math.min(hi,Float.isFinite(v)?v:lo));}
}
