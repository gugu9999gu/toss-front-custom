package dev.tossfront.record;

import java.util.*;

/** Temporal gestures from mirrored portrait hand landmarks. No camera or Android dependency. */
final class HandGestureCore {
    static final class Hand {
        final float[] xy;
        final float x,y,span,open;
        final boolean indexOnly;
        Hand(float[] values){
            xy=values.clone();float cx=0,cy=0;for(int i:new int[]{0,5,9,13,17}){cx+=xy[i*2];cy+=xy[i*2+1];}
            x=cx/5;y=cy/5;span=Math.max(.025f,distance(5,17));
            int extended=0;for(int i:new int[]{8,12,16,20})if(extended(i))extended++;
            // One partly raised adjacent finger is tolerated when the index is extended;
            // an open palm still cannot become a pointing command.
            open=extended/4f;indexOnly=extended(8)&&extended<=2;
        }
        float distance(int a,int b){return (float)Math.hypot(xy[a*2]-xy[b*2],xy[a*2+1]-xy[b*2+1]);}
        boolean extended(int tip){return distance(tip,0)>distance(tip-2,0)*1.23f&&distance(tip,tip-3)>span*.45f;}
        boolean upright(){return indexOnly&&xy[17]<xy[13]-span*.25f;}
    }
    static final class Output {
        int volumeDelta;boolean toggle,next;float x=.5f,y=.5f,strength=1,spread=1;int hands;
    }
    private static final class Point {final float x,y;final long at;Point(float x,float y,long at){this.x=x;this.y=y;this.at=at;}}
    private final ArrayList<Point> circle=new ArrayList<>();
    private long last,volumeAt=-10000,toggleAt=-10000,nextAt=-10000,clapArmedAt,firstFlick,flickAt;
    private float previousGap,previousX,previousY,palmX,palmY,flickStartX,flickStartY,flickPeak;
    private boolean clapArmed,hadPoint,strokeReady=true,returning;
    void reset(){last=0;circle.clear();clapArmed=hadPoint=returning=false;strokeReady=true;firstFlick=0;}
    Output update(List<Hand> hands,long now,boolean commands){
        Output out=new Output();out.hands=hands.size();
        if(last>0&&(now<=last||now-last>800))reset();
        float dt=last==0?0:Math.min(.5f,(now-last)/1000f);last=now;
        if(hands.isEmpty()){circle.clear();hadPoint=false;firstFlick=0;clapArmed=false;return out;}
        float cx=0,cy=0,open=0;for(Hand h:hands){cx+=h.x;cy+=h.y;open+=h.open;}
        out.x=cx/hands.size();out.y=cy/hands.size();out.strength=.7f+open/hands.size()*1.4f;
        Hand pointer=null;int pointers=0;for(Hand candidate:hands)if(candidate.indexOnly){pointer=candidate;pointers++;}
        if(hands.size()==2){
            Hand a=hands.get(0),b=hands.get(1);float gap=(float)Math.hypot(a.x-b.x,a.y-b.y),scale=(a.span+b.span)/2;
            out.spread=bound(.7f+gap*1.1f,.7f,1.55f);
            boolean facing=pointers==0&&Math.abs(a.y-b.y)<scale*1.8f&&a.open>=.25f&&b.open>=.25f;
            if(pointers>0)clapArmed=false;
            if(facing&&gap>Math.max(.18f,scale*2.4f)){clapArmed=true;clapArmedAt=now;}
            float speed=dt>0?(previousGap-gap)/dt:0;
            if(commands&&clapArmed&&facing&&now-clapArmedAt<1600&&gap<scale*2.0f&&speed>.3f&&now-toggleAt>1300){out.toggle=true;toggleAt=now;clapArmed=false;}
            if(now-clapArmedAt>1600)clapArmed=false;
            previousGap=gap;if(pointers!=1){circle.clear();hadPoint=false;firstFlick=0;return out;}
        }
        clapArmed=false;
        Hand h=pointers==1?pointer:hands.get(0);float x=h.xy[16],y=h.xy[17];
        if(!h.indexOnly||!commands){circle.clear();hadPoint=false;firstFlick=0;return out;}
        if(hadPoint&&(Math.hypot(h.x-palmX,h.y-palmY)>.22||Math.hypot(x-previousX,y-previousY)>.3)){circle.clear();hadPoint=false;firstFlick=0;}
        // Require a closed two-dimensional path; a pair of lateral taps cannot change volume.
        circle.add(new Point(x,y,now));
        while(circle.size()>80||circle.size()>1&&now-circle.get(0).at>5000)circle.remove(0);
        float minX=1,maxX=0,minY=1,maxY=0;
        for(Point p:circle){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
        float width=maxX-minX,height=maxY-minY;
        boolean circularMotion=width>.055f&&height>.055f;
        if(circle.size()>=8&&now-volumeAt>700){
            if(width>.065f&&height>.065f&&width/height>.5f&&width/height<2){
                float mx=(minX+maxX)/2,my=(minY+maxY)/2;double turn=0,total=0,prev=0;
                for(int i=0;i<circle.size();i++){Point p=circle.get(i);double angle=Math.atan2(p.y-my,p.x-mx);if(i>0){double d=angle-prev;while(d>Math.PI)d-=Math.PI*2;while(d< -Math.PI)d+=Math.PI*2;turn+=d;total+=Math.abs(d);}prev=angle;}
                Point first=circle.get(0),end=circle.get(circle.size()-1);
                if(Math.abs(turn)>5.0&&Math.abs(turn)>total*.72&&Math.hypot(first.x-end.x,first.y-end.y)<Math.max(width,height)*.45){out.volumeDelta=turn>0?2:-2;volumeAt=now;circle.clear();firstFlick=0;hadPoint=false;}
            }
        }
        // Two brisk rightward strokes with a leftward recoil and upright index in between.
        if(!h.upright()||circularMotion){firstFlick=0;strokeReady=true;returning=false;}
        else if(out.volumeDelta==0){
            if(!hadPoint){flickStartX=x;flickStartY=y;flickAt=now;strokeReady=true;}
            if(firstFlick>0&&now-firstFlick>1100){firstFlick=0;strokeReady=true;flickStartX=x;flickStartY=y;flickAt=now;}
            if(returning&&x<flickPeak-h.span*.4f){returning=false;strokeReady=true;flickStartX=x;flickStartY=y;flickAt=now;}
            if(strokeReady){
                if(now-flickAt>650||Math.abs(y-flickStartY)>h.span*.6f){flickStartX=x;flickStartY=y;flickAt=now;}
                if(x-flickStartX>Math.max(.055f,h.span*.65f)&&now-flickAt>=60&&now-flickAt<=650&&Math.abs(y-flickStartY)<h.span*.6f){
                    if(firstFlick>0&&now-firstFlick>=160&&now-firstFlick<1100&&now-nextAt>1500){out.next=true;nextAt=now;firstFlick=0;}
                    else firstFlick=now;
                    strokeReady=false;returning=true;flickPeak=x;
                }
            }
        }
        previousX=x;previousY=y;palmX=h.x;palmY=h.y;hadPoint=true;
        return out;
    }
    static float bound(float v,float lo,float hi){return Math.max(lo,Math.min(hi,Float.isFinite(v)?v:lo));}
}
