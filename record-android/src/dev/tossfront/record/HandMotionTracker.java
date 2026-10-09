package dev.tossfront.record;
import java.util.*;

/** Associate model-confirmed poses only. Image patches never decide hand movement. */
final class HandMotionTracker {
    private static final class Track {
        HandGestureCore.Hand hand,candidate;long received,observed,candidateAt;
        Track(HandGestureCore.Hand h,long at,long now){hand=h;observed=at;received=now;}
    }
    private final ArrayList<Track> tracks=new ArrayList<>();private long last;
    void clear(){tracks.clear();last=0;}
    void acceptModel(List<HandGestureCore.Hand> hands,long at,long now){
        if(now<last)clear();last=now;expire(now);boolean[] used=new boolean[2];
        ArrayList<HandGestureCore.Hand> sorted=new ArrayList<>(hands);sorted.sort(Comparator.comparingDouble(h->h.x));
        for(HandGestureCore.Hand h:sorted){
            if(!valid(h))continue;int nearest=-1;double distance=Double.MAX_VALUE;
            for(int i=0;i<tracks.size();i++)if(!used[i]){Track t=tracks.get(i);double d=Math.hypot(t.hand.x-h.x,t.hand.y-h.y);if(d<distance){distance=d;nearest=i;}}
            // A clearly separated second hand gets a separate slot, not the existing hand's trajectory.
            if(nearest<0||distance>.23&&tracks.size()<2&&sorted.size()>tracks.size()){
                if(tracks.size()<2){used[tracks.size()]=true;tracks.add(new Track(h,at,now));}continue;
            }
            Track t=tracks.get(nearest);used[nearest]=true;if(at<=t.observed)continue;
            double jump=Math.hypot(t.hand.pinchX-h.pinchX,t.hand.pinchY-h.pinchY);
            if(distance>.2||jump>.22){
                if(t.candidate==null||at<=t.candidateAt||at-t.candidateAt>400||Math.hypot(t.candidate.x-h.x,t.candidate.y-h.y)>.07){t.candidate=h;t.candidateAt=at;continue;}
            }
            t.hand=h;t.observed=at;t.received=now;t.candidate=null;t.candidateAt=0;
        }
    }
    List<HandGestureCore.Hand> update(long now){
        if(now<last)clear();last=now;expire(now);ArrayList<HandGestureCore.Hand> result=new ArrayList<>();for(Track t:tracks)result.add(t.hand);return result;
    }
    private void expire(long now){for(Iterator<Track> it=tracks.iterator();it.hasNext();){Track t=it.next();if(now<t.received||now-t.received>400||now-t.observed>700)it.remove();}}
    private static boolean valid(HandGestureCore.Hand h){
        float minX=1,maxX=0,minY=1,maxY=0;for(int i=0;i<21;i++){float x=h.xy[i*2],y=h.xy[i*2+1];if(!Float.isFinite(x)||!Float.isFinite(y))return false;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}return Math.max(maxX-minX,maxY-minY)>.055f;
    }
}
