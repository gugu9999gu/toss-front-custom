package dev.tossfront.record;

/** Playback intent follows an actual gesture volume change, never a slider change. */
final class GestureVolume {
    static int playback(int gestureDelta,int current,int target){
        if(gestureDelta<0&&target==0)return -1;
        if(gestureDelta>0&&target>current&&target>0)return 1;
        return 0;
    }
}
