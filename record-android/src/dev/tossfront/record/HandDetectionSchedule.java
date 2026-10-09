package dev.tossfront.record;

/** Keep one-hand landmark tracking warm; periodically discover a second hand. */
final class HandDetectionSchedule {
    private long lastSearch=-1,lastPair=-1,last;
    void reset(){lastSearch=lastPair=-1;last=0;}
    boolean search(long now){
        if(now<last)reset();last=now;
        return lastSearch<0||now-lastSearch>=600||lastPair>=0&&now-lastPair<=900;
    }
    void result(boolean search,int hands,long at){if(search)lastSearch=at;if(hands>=2)lastPair=at;}
}
