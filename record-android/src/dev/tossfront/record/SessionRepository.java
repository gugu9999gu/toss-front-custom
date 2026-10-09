package dev.tossfront.record;

import android.content.*;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** YouTube metadata lives only in memory. No network, database, or arbitrary media-key broadcast. */
public final class SessionRepository {
    public static final String WEB = "local.tossfront.youtubeweb";
    public interface Observer { void changed(); }
    private static SessionRepository instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final Set<Observer> observers = new HashSet<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::choose;
    private MediaController controller;
    private boolean registered, access;
    private MediaMetadata metadata;
    private PlaybackState playback;
    private final RecordLibrary library;
    private final PlaybackGate gate=new PlaybackGate();
    private final HistoryQueue historyQueue=new HistoryQueue();
    private String observedId="",historyTarget="";
    private boolean historyNavigating;
    private String pendingId="";
    private long navigationAt;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { metadata = value; track(); notifyObservers(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { playback = value; track(); notifyObservers(); }
        @Override public void onSessionDestroyed() { detach(); connect(); }
        @Override public void onSessionEvent(String event,Bundle extras){if("frontrecord.content_ended".equals(event)&&extras!=null){String id=extras.getString("video_id");if(id!=null&&id.equals(videoId())&&gate.update(id,false,true,advertisement(),SystemClock.elapsedRealtime()))handleEnd(id);}}
    };

    private SessionRepository(Context context) {
        this.context = context.getApplicationContext();
        manager = (MediaSessionManager)this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this.context, SessionListener.class);
        library=RecordLibrary.get(this.context);
    }
    public static synchronized SessionRepository get(Context context) {
        if (instance == null) instance = new SessionRepository(context);
        return instance;
    }
    public void observe(Observer observer) { observers.add(observer); connect(); observer.changed(); }
    public void remove(Observer observer) { observers.remove(observer); }
    private void notifyObservers() { for (Observer observer : new ArrayList<>(observers)) observer.changed(); }
    public void connect() {
        try {
            List<MediaController> active = manager.getActiveSessions(listener);
            access = true;
            if (!registered) { manager.addOnActiveSessionsChangedListener(sessions, listener, handler); registered = true; }
            choose(active);
        } catch (SecurityException e) { access = false; detach(); notifyObservers(); }
    }
    public void disconnected() { access = false; detach(); notifyObservers(); }
    private int score(MediaController candidate) {
        if (!candidate.getPackageName().equals(WEB)) return -1;
        PlaybackState state = candidate.getPlaybackState();
        int value = 1;
        if (state != null) {
            if (state.getState() == PlaybackState.STATE_PLAYING) value += 100;
            else if (state.getState() == PlaybackState.STATE_BUFFERING || state.getState() == PlaybackState.STATE_CONNECTING) value += 50;
            else if (state.getState() == PlaybackState.STATE_PAUSED) value += 20;
        }
        return value;
    }
    private void choose(List<MediaController> controllers) {
        MediaController chosen = null; int best = -1;
        if (controllers != null) for (MediaController candidate : controllers) {
            int value = score(candidate); if (value > best) { best = value; chosen = candidate; }
        }
        if (chosen == null) { detach(); notifyObservers(); return; }
        if (controller == null || !controller.getSessionToken().equals(chosen.getSessionToken())) {
            detach(); controller = chosen; controller.registerCallback(callback, handler);
        }
        metadata = controller.getMetadata(); playback = controller.getPlaybackState(); track(); notifyObservers();
    }
    private void detach() {
        if (controller != null) controller.unregisterCallback(callback);
        controller = null; metadata = null; playback = null;
    }
    public boolean hasAccess() { return access; }
    public boolean connected() { return controller != null; }
    public String source() { return "YouTube 웹"; }
    public String title() {
        if (metadata == null) return connected() ? "재생 정보를 불러오는 중" : "재생 중인 곡이 없어요";
        String title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (title == null || title.trim().isEmpty()) title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return title == null || title.trim().isEmpty() ? "제목 정보가 없는 영상" : title;
    }
    public String artist() {
        if (metadata == null) return "YouTube 웹에서 영상을 재생해 주세요.";
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.trim().isEmpty()) artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return artist == null || artist.trim().isEmpty() ? source() : artist;
    }
    public Bitmap artwork() {
        if (metadata == null) return null;
        Bitmap image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (image == null) image = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (image == null) image = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return image;
    }
    public boolean playing() { return playback != null && playback.getState() == PlaybackState.STATE_PLAYING; }
    public boolean buffering() { return playback != null && (playback.getState() == PlaybackState.STATE_BUFFERING || playback.getState() == PlaybackState.STATE_CONNECTING); }
    public boolean error() { return playback != null && playback.getState() == PlaybackState.STATE_ERROR; }
    public boolean paused() { return playback != null && playback.getState() == PlaybackState.STATE_PAUSED; }
    String videoId(){return metadata==null?"":metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);}
    boolean advertisement(){return metadata!=null&&metadata.getLong("frontrecord.is_ad")==1;}
    private void track(){String id=videoId();if(!LibraryCore.valid(id))return;long now=SystemClock.elapsedRealtime();if(!pendingId.isEmpty()&&now-navigationAt>8000){pendingId="";gate.reset();}if(now-navigationAt<1500||!pendingId.isEmpty()&&!pendingId.equals(id))return;if(pendingId.equals(id)&&playing()&&!advertisement())pendingId="";
        if(!id.equals(observedId)){if(!id.equals(historyTarget)){historyQueue.clear();historyNavigating=false;}observedId=id;historyTarget="";}
        boolean ended=gate.update(id,playing(),playback!=null&&playback.getState()==PlaybackState.STATE_STOPPED,advertisement(),now);
        if(playing()&&!advertisement()&&gate.recordable(now)&&!"YouTube".equals(title()))library.played(id,title(),duration());
        if(ended)handleEnd(id);
    }
    private void handleEnd(String id){if(PlaybackTimer.get(context).trackEnded()){pause();notifyObservers();return;}if(library.loop==1){navigationAt=SystemClock.elapsedRealtime();gate.reset();custom("frontrecord.repeat_one",null);}else if(library.loop==2){String next=library.neighbor(id,1);if(next!=null)playVideo(next);}}
    static String formatTimer(long ms){long seconds=(Math.max(0,ms)+999)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    public long duration() { return metadata == null ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)); }
    public long position() {
        if (playback == null) return 0;
        long value = Math.max(0, playback.getPosition());
        if (playing() && playback.getLastPositionUpdateTime() > 0) value += (long)((SystemClock.elapsedRealtime() - playback.getLastPositionUpdateTime()) * playback.getPlaybackSpeed());
        return duration() > 0 ? Math.min(duration(), value) : value;
    }
    public boolean supports(long action) { return controller != null && playback != null && (playback.getActions() & action) != 0; }
    public void toggle() {
        if (controller == null) return;
        if (playing() && supports(PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) controller.getTransportControls().pause();
        else if (!playing() && supports(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) controller.getTransportControls().play();
    }
    public void pause(){if(controller!=null&&supports(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))controller.getTransportControls().pause();}
    public void seek(long position) { if (supports(PlaybackState.ACTION_SEEK_TO)) controller.getTransportControls().seekTo(Math.max(0, position)); }
    public void next(){navigate(1);}
    public void previous(){navigate(-1);}
    private long skipAction(int direction){return direction>0?PlaybackState.ACTION_SKIP_TO_NEXT:PlaybackState.ACTION_SKIP_TO_PREVIOUS;}
    private String skipTarget(int direction){return library.loop==2?library.neighbor(videoId(),direction):historyQueue.neighbor(library.entries(false),videoId(),direction);}
    boolean canSkip(int direction){if(!connected()||advertisement()||!pendingId.isEmpty())return false;if(library.loop!=2&&supports(skipAction(direction)))return true;String id=skipTarget(direction);return id!=null&&!id.equals(videoId());}
    private void navigate(int direction){if(!canSkip(direction))return;String id=skipTarget(direction);boolean queued=historyNavigating&&id!=null&&!id.equals(videoId());if(library.loop!=2&&!queued&&supports(skipAction(direction))){historyQueue.clear();historyTarget="";historyNavigating=false;if(direction>0)controller.getTransportControls().skipToNext();else controller.getTransportControls().skipToPrevious();}else playVideo(id,library.loop!=2);}
    void playVideo(String id){playVideo(id,false);}
    private void playVideo(String id,boolean keepHistoryQueue){if(!LibraryCore.valid(id))return;if(!keepHistoryQueue)historyQueue.clear();historyNavigating=keepHistoryQueue;historyTarget=keepHistoryQueue?id:"";pendingId=id;navigationAt=SystemClock.elapsedRealtime();gate.reset();if(controller!=null){Bundle value=new Bundle();value.putString("video_id",id);custom("frontrecord.play_id",value);}else{Intent intent=webIntent(true);if(intent!=null)context.startActivity(intent.putExtra("play_video_id",id));}}
    private void custom(String action,Bundle data){if(controller!=null)controller.getTransportControls().sendCustomAction(action,data);}
    int loopMode(){return library.loop;}
    void cycleLoop(){int next=library.loop+1;if(next==2&&library.core.favorites.isEmpty())next=0;if(next>2)next=0;library.loop(next);notifyObservers();}
    void loopFavorites(){if(library.core.favorites.isEmpty())return;library.loop(2);String id=videoId();if(!library.favorite(id))playVideo(library.core.favorites.get(0).id);notifyObservers();}
    void libraryChanged(){notifyObservers();}
    public void openWeb(Context from,boolean recordActive) {
        // Legacy source preferences are unused. Only the web companion can be launched.
        Intent intent=webIntent(recordActive);if(intent!=null)from.startActivity(intent);
    }
    private Intent webIntent(boolean active){Intent intent=context.getPackageManager().getLaunchIntentForPackage(WEB);if(intent==null)return null;Appearance a=new Appearance(context);return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("record_ui_active",active).putExtra("record_ui_full",active&&a.immersive).putExtra("record_ui_bg",a.bg()).putExtra("record_ui_dark",a.dark()).putExtra("auto_skip_ads",a.autoSkip);}
}
