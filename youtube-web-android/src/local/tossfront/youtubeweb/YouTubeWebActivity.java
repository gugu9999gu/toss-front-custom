package local.tossfront.youtubeweb;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Official mobile website with a local Android media-session adapter. No audio/video extraction. */
public final class YouTubeWebActivity extends Activity {
    private FrameLayout container;
    private WebView browser;
    private View fullScreenVideo;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private int previousSystemUi;
    private MediaSession session;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService artworkWorker=Executors.newSingleThreadExecutor();
    private boolean destroyed;
    private boolean recordUi,recordFull,recordDark;
    private boolean autoSkip=true,lastAd;
    private int recordBg,originalStatus,originalNavigation;
    private String videoId="",lastTitle="",lastArtist="";
    private String requestedAudioId="";
    private int unmuteAttempts;
    private long lastDuration;
    private String lastEnd="";
    private Bitmap artwork;
    private final Runnable poll=new Runnable(){public void run(){if(destroyed)return;readPlayer();main.postDelayed(this,800);}};

    @Override public void onCreate(Bundle state){super.onCreate(state);originalStatus=getWindow().getStatusBarColor();originalNavigation=getWindow().getNavigationBarColor();readRecordUi(getIntent());
        container=new FrameLayout(this);browser=new WebView(this);container.addView(browser,new FrameLayout.LayoutParams(-1,-1));setContentView(container);
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(false);
        browser.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"https".equals(request.getUrl().getScheme());}});
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreenVideo!=null){callback.onCustomViewHidden();return;}fullScreenVideo=view;fullScreenCallback=callback;previousSystemUi=getWindow().getDecorView().getSystemUiVisibility();browser.setVisibility(View.GONE);container.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
            @Override public void onHideCustomView(){exitFullScreen();}
        });
        session=new MediaSession(this,"YouTubeWebRecord");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){control("v.muted=false;if(v.volume===0)v.volume=1;v.play().catch(function(){})");if(youtubePage())browser.evaluateJavascript(WebProbe.unmute(),null);}
            @Override public void onPause(){control("v.pause()");}
            @Override public void onSeekTo(long position){seek(position);}
            @Override public void onSkipToNext(){navigate(true);}
            @Override public void onSkipToPrevious(){navigate(false);}
            @Override public void onCustomAction(String action,Bundle extras){if("frontrecord.play_id".equals(action)&&extras!=null){String id=extras.getString("video_id");if(validVideoId(id)){requestedAudioId=id;unmuteAttempts=5;browser.loadUrl("https://m.youtube.com/watch?v="+id);}}else if("frontrecord.repeat_one".equals(action)&&!lastAd)control("v.currentTime=0;v.play().catch(function(){})");}
        },main);
        String launch=getIntent().getStringExtra("play_video_id");
        if(validVideoId(launch)){requestedAudioId=launch;unmuteAttempts=5;browser.loadUrl("https://m.youtube.com/watch?v="+launch);}
        else if(state==null||browser.restoreState(state)==null)browser.loadUrl("https://m.youtube.com/");
        applyRecordUi();main.post(poll);
    }
    private boolean validVideoId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{11}");}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readRecordUi(intent);applyRecordUi();String id=intent.getStringExtra("play_video_id");if(validVideoId(id)){requestedAudioId=id;unmuteAttempts=5;browser.loadUrl("https://m.youtube.com/watch?v="+id);}}
    private void readRecordUi(Intent intent){recordUi=intent.getBooleanExtra("record_ui_active",false);recordFull=intent.getBooleanExtra("record_ui_full",false);recordDark=intent.getBooleanExtra("record_ui_dark",false);recordBg=intent.getIntExtra("record_ui_bg",android.graphics.Color.BLACK);autoSkip=intent.getBooleanExtra("auto_skip_ads",getPreferences(0).getBoolean("auto_skip_ads",true));getPreferences(0).edit().putBoolean("auto_skip_ads",autoSkip).apply();}
    private void applyRecordUi(){if(fullScreenVideo!=null)return;getWindow().setStatusBarColor(recordUi?recordBg:originalStatus);getWindow().setNavigationBarColor(recordUi?recordBg:originalNavigation);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(!(recordUi&&recordFull));WindowInsetsController control=getWindow().getInsetsController();if(control!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;control.setSystemBarsAppearance(recordUi&&!recordDark?light:0,light);control.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(recordUi&&recordFull)control.hide(WindowInsets.Type.systemBars());else control.show(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(recordUi&&recordFull?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION:0);
    }
    @Override protected void onResume(){super.onResume();applyRecordUi();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)applyRecordUi();}
    private boolean youtubePage(){String url=browser.getUrl();if(url==null)return false;Uri uri=Uri.parse(url);return "https".equals(uri.getScheme())&&("m.youtube.com".equals(uri.getHost())||"www.youtube.com".equals(uri.getHost()));}
    private void control(String command){if(!youtubePage())return;browser.evaluateJavascript("(function(){const v=document.querySelector('video');if(v){"+command+";}return true;})()",r->readPlayer());}
    private void seek(long position){if(!youtubePage()||lastAd)return;browser.evaluateJavascript(WebProbe.seek(position),raw->{try{Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))return;JSONObject value=new JSONObject((String)decoded);Bundle result=new Bundle();result.putLong("request_ms",position);result.putBoolean("ok",value.optBoolean("ok"));result.putLong("target_ms",(long)(value.optDouble("target")*1000));result.putBoolean("clamped",value.optBoolean("clamped"));session.sendSessionEvent("frontrecord.seek_result",result);android.util.Log.i("FrontRecordWeb",value.optBoolean("ok")?"seek_requested_"+value.optString("method"):"seek_unavailable");readPlayer();}catch(JSONException ignored){}});}
    private void navigate(boolean next){if(youtubePage())browser.evaluateJavascript(WebProbe.navigate(next),r->readPlayer());}
    private void readPlayer(){
        if(!youtubePage()){session.setActive(false);return;}
        browser.evaluateJavascript(WebProbe.state(autoSkip),raw->{if(destroyed)return;
            try{Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))return;JSONObject value=new JSONObject((String)decoded);
                if(!value.optBoolean("active")){session.setActive(false);return;}
                String ended=value.optString("endedId");long endedAt=value.optLong("endedAt");String endKey=ended+":"+endedAt;if(ended.matches("[A-Za-z0-9_-]{11}")&&endedAt>0&&!endKey.equals(lastEnd)){lastEnd=endKey;Bundle event=new Bundle();event.putString("video_id",ended);session.sendSessionEvent("frontrecord.content_ended",event);}
                String id=value.optString("id"),title=value.optString("title").trim(),artist=value.optString("artist").trim();
                if(id.equals(requestedAudioId)&&value.optInt("ready")>=3&&unmuteAttempts>0){final String expected=id;if(--unmuteAttempts==0)requestedAudioId="";browser.evaluateJavascript(WebProbe.unmute(),clicked->{if("true".equals(clicked)&&expected.equals(requestedAudioId))requestedAudioId="";});}
                long duration=Math.max(0,(long)(value.optDouble("duration")*1000));
                if(!id.equals(videoId)){videoId=id;artwork=null;lastTitle="";if(id.matches("[A-Za-z0-9_-]{11}"))loadArtwork(id);}
                boolean ad=value.optBoolean("ad");if(!title.equals(lastTitle)||!artist.equals(lastArtist)||duration!=lastDuration||lastAd!=ad){lastTitle=title;lastArtist=artist;lastDuration=duration;lastAd=ad;publishMetadata();}
                int playback=value.optBoolean("error")?PlaybackState.STATE_ERROR:value.optBoolean("ended")?PlaybackState.STATE_STOPPED:value.optBoolean("paused")?PlaybackState.STATE_PAUSED:value.optInt("ready")<3?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PLAYING;
                long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE;
                if(duration>0)actions|=PlaybackState.ACTION_SEEK_TO;
                if(!ad&&value.optBoolean("next"))actions|=PlaybackState.ACTION_SKIP_TO_NEXT;if(!ad&&value.optBoolean("previous"))actions|=PlaybackState.ACTION_SKIP_TO_PREVIOUS;
                session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playback,Math.max(0,(long)(value.optDouble("position")*1000)),(float)value.optDouble("speed",1),SystemClock.elapsedRealtime()).build());session.setActive(true);
            }catch(JSONException ignored){/* A page without usable media metadata remains unconnected. */}
        });
    }
    private void publishMetadata(){MediaMetadata.Builder builder=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,lastTitle).putString(MediaMetadata.METADATA_KEY_ARTIST,lastArtist).putString(MediaMetadata.METADATA_KEY_MEDIA_ID,videoId).putLong("frontrecord.is_ad",lastAd?1:0).putLong(MediaMetadata.METADATA_KEY_DURATION,lastDuration);if(artwork!=null)builder.putBitmap(MediaMetadata.METADATA_KEY_ART,artwork);session.setMetadata(builder.build());}
    private void loadArtwork(String id){artworkWorker.execute(()->{
        Bitmap image=null;HttpsURLConnection connection=null;
        try{
            // Fixed first-party thumbnail host and validated ID; page-provided URLs are never fetched.
            connection=(HttpsURLConnection)new URL("https://i.ytimg.com/vi/"+id+"/hqdefault.jpg").openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            if(connection.getResponseCode()==200){try(InputStream input=connection.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int size;while((size=input.read(buffer))!=-1){if(data.size()+size>1048576)throw new IOException("Image too large");data.write(buffer,0,size);}byte[] bytes=data.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}}
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        final Bitmap result=image;main.post(()->{if(!destroyed&&id.equals(videoId)&&result!=null){artwork=result;publishMetadata();}});
    });}
    private void exitFullScreen(){if(fullScreenVideo==null)return;container.removeView(fullScreenVideo);fullScreenVideo=null;browser.setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(previousSystemUi);applyRecordUi();if(fullScreenCallback!=null)fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}
    @Override public void onBackPressed(){if(fullScreenVideo!=null)exitFullScreen();else if(browser.canGoBack())browser.goBack();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){browser.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacks(poll);artworkWorker.shutdownNow();session.setActive(false);session.release();exitFullScreen();container.removeView(browser);browser.destroy();super.onDestroy();}
}
