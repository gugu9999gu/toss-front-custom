package dev.tossfront.record;

/** One genuine content end produces at most one queue transition; ads never do. */
final class PlaybackGate {
    private String id="";
    private boolean played,latched;
    private long contentStarted;
    boolean update(String value,boolean playing,boolean ended,boolean ad,long now){if(!LibraryCore.valid(value))return false;if(!value.equals(id)){id=value;played=latched=false;contentStarted=0;}if(ad){contentStarted=0;return false;}if(playing){if(contentStarted==0)contentStarted=now;played=true;latched=false;return false;}if(ended&&played&&!latched){latched=true;played=false;return true;}if(!ended){played=false;contentStarted=0;}return false;}
    boolean recordable(long now){return contentStarted>0&&now-contentStarted>=2500;}
    void reset(){id="";played=latched=false;contentStarted=0;}
}
