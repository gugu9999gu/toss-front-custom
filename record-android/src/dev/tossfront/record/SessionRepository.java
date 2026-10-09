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
    public static final String OFFICIAL = "com.google.android.youtube", WEB = "local.tossfront.youtubeweb";
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
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { metadata = value; notifyObservers(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { playback = value; notifyObservers(); }
        @Override public void onSessionDestroyed() { detach(); connect(); }
    };

    private SessionRepository(Context context) {
        this.context = context.getApplicationContext();
        manager = (MediaSessionManager)this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(this.context, SessionListener.class);
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
        if (!candidate.getPackageName().equals(OFFICIAL) && !candidate.getPackageName().equals(WEB)) return -1;
        PlaybackState state = candidate.getPlaybackState();
        int value = candidate.getPackageName().equals(OFFICIAL) ? 2 : 1;
        if (candidate.getPackageName().equals(preferredSource())) value += 6;
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
        metadata = controller.getMetadata(); playback = controller.getPlaybackState(); notifyObservers();
    }
    private void detach() {
        if (controller != null) controller.unregisterCallback(callback);
        controller = null; metadata = null; playback = null;
    }
    public boolean hasAccess() { return access; }
    public boolean connected() { return controller != null; }
    public String source() { return controller != null && controller.getPackageName().equals(WEB) ? "YouTube 웹" : "YouTube"; }
    public String packageName() { return controller == null ? preferredSource() : controller.getPackageName(); }
    public String preferredSource() { return context.getSharedPreferences("record", 0).getString("source", WEB); }
    public void rememberSource(String source) {
        if (OFFICIAL.equals(source) || WEB.equals(source)) context.getSharedPreferences("record", 0).edit().putString("source", source).apply();
    }
    public String title() {
        if (metadata == null) return connected() ? "재생 정보를 불러오는 중" : "YouTube 재생을 기다리는 중";
        String title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (title == null || title.trim().isEmpty()) title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return title == null || title.trim().isEmpty() ? "제목 정보가 없는 영상" : title;
    }
    public String artist() {
        if (metadata == null) return "YouTube에서 영상을 재생해 주세요.";
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
    public void seek(long position) { if (supports(PlaybackState.ACTION_SEEK_TO)) controller.getTransportControls().seekTo(Math.max(0, position)); }
    public void next() { if (supports(PlaybackState.ACTION_SKIP_TO_NEXT)) controller.getTransportControls().skipToNext(); }
    public void previous() { if (supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) controller.getTransportControls().skipToPrevious(); }
    public void openSource(Context from, String source) {
        rememberSource(source);
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(source);
        if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); from.startActivity(intent); }
    }
}
