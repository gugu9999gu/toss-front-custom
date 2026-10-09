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
    private String videoId="",lastTitle="",lastArtist="";
    private long lastDuration;
    private Bitmap artwork;
    private final Runnable poll=new Runnable(){public void run(){if(destroyed)return;readPlayer();main.postDelayed(this,800);}};
    private static final String STATE_JS="(function(){try{const u=new URL(location.href);const v=document.querySelector('video');const id=u.searchParams.get('v')||(u.pathname.match(/^\\/shorts\\/([\\w-]+)/)||[])[1]||'';const watch=(u.pathname==='/watch'||u.pathname.startsWith('/shorts/'));if(!watch||!v)return JSON.stringify({active:false});const m=navigator.mediaSession&&navigator.mediaSession.metadata;const t=document.querySelector('ytm-slim-video-metadata-renderer h1,ytm-watch-metadata h1,h1');const a=document.querySelector('ytm-slim-owner-renderer .slim-owner-channel-name,ytm-slim-owner-renderer a');return JSON.stringify({active:true,id:id,title:(m&&m.title)||(t&&t.textContent)||document.title.replace(/\\s*-\\s*YouTube$/,''),artist:(m&&m.artist)||(a&&a.textContent)||'YouTube 웹',duration:Number.isFinite(v.duration)?v.duration:0,position:Number.isFinite(v.currentTime)?v.currentTime:0,paused:v.paused,ended:v.ended,ready:v.readyState,speed:v.playbackRate,error:!!v.error});}catch(e){return JSON.stringify({active:false});}})()";

    @Override public void onCreate(Bundle state){super.onCreate(state);
        container=new FrameLayout(this);browser=new WebView(this);container.addView(browser,new FrameLayout.LayoutParams(-1,-1));setContentView(container);
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(false);
        browser.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"https".equals(request.getUrl().getScheme());}});
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreenVideo!=null){callback.onCustomViewHidden();return;}fullScreenVideo=view;fullScreenCallback=callback;previousSystemUi=getWindow().getDecorView().getSystemUiVisibility();browser.setVisibility(View.GONE);container.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
            @Override public void onHideCustomView(){exitFullScreen();}
        });
        session=new MediaSession(this,"YouTubeWebRecord");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){control("v.play().catch(function(){})");}
            @Override public void onPause(){control("v.pause()");}
            @Override public void onSeekTo(long position){control("if(Number.isFinite(v.duration))v.currentTime=Math.max(0,Math.min(v.duration,"+(position/1000.0)+"))");}
        },main);
        String launch=getIntent().getStringExtra("url");
        if(validLaunchUrl(launch))browser.loadUrl(launch);
        else if(state==null||browser.restoreState(state)==null)browser.loadUrl("https://m.youtube.com/");
        main.post(poll);
    }
    private boolean validLaunchUrl(String url){if(url==null)return false;Uri uri=Uri.parse(url);return "https".equals(uri.getScheme())&&("m.youtube.com".equals(uri.getHost())||"www.youtube.com".equals(uri.getHost()));}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String url=intent.getStringExtra("url");if(validLaunchUrl(url))browser.loadUrl(url);}
    private boolean youtubePage(){String url=browser.getUrl();if(url==null)return false;Uri uri=Uri.parse(url);return "https".equals(uri.getScheme())&&("m.youtube.com".equals(uri.getHost())||"www.youtube.com".equals(uri.getHost()));}
    private void control(String command){if(!youtubePage())return;browser.evaluateJavascript("(function(){const v=document.querySelector('video');if(v){"+command+";}return true;})()",r->readPlayer());}
    private void readPlayer(){
        if(!youtubePage()){session.setActive(false);return;}
        browser.evaluateJavascript(STATE_JS,raw->{if(destroyed)return;
            try{Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))return;JSONObject value=new JSONObject((String)decoded);
                if(!value.optBoolean("active")){session.setActive(false);return;}
                String id=value.optString("id"),title=value.optString("title").trim(),artist=value.optString("artist").trim();
                long duration=Math.max(0,(long)(value.optDouble("duration")*1000));
                if(!id.equals(videoId)){videoId=id;artwork=null;lastTitle="";if(id.matches("[A-Za-z0-9_-]{11}"))loadArtwork(id);}
                if(!title.equals(lastTitle)||!artist.equals(lastArtist)||duration!=lastDuration){lastTitle=title;lastArtist=artist;lastDuration=duration;publishMetadata();}
                int playback=value.optBoolean("error")?PlaybackState.STATE_ERROR:value.optBoolean("ended")?PlaybackState.STATE_STOPPED:value.optBoolean("paused")?PlaybackState.STATE_PAUSED:value.optInt("ready")<3?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PLAYING;
                long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE;
                if(duration>0)actions|=PlaybackState.ACTION_SEEK_TO;
                session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playback,Math.max(0,(long)(value.optDouble("position")*1000)),(float)value.optDouble("speed",1),SystemClock.elapsedRealtime()).build());session.setActive(true);
            }catch(JSONException ignored){/* A page without usable media metadata remains unconnected. */}
        });
    }
    private void publishMetadata(){MediaMetadata.Builder builder=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,lastTitle).putString(MediaMetadata.METADATA_KEY_ARTIST,lastArtist).putLong(MediaMetadata.METADATA_KEY_DURATION,lastDuration);if(artwork!=null)builder.putBitmap(MediaMetadata.METADATA_KEY_ART,artwork);session.setMetadata(builder.build());}
    private void loadArtwork(String id){artworkWorker.execute(()->{
        Bitmap image=null;HttpsURLConnection connection=null;
        try{
            // Fixed first-party thumbnail host and validated ID; page-provided URLs are never fetched.
            connection=(HttpsURLConnection)new URL("https://i.ytimg.com/vi/"+id+"/hqdefault.jpg").openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            if(connection.getResponseCode()==200){try(InputStream input=connection.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int size;while((size=input.read(buffer))!=-1){if(data.size()+size>1048576)throw new IOException("Image too large");data.write(buffer,0,size);}byte[] bytes=data.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}}
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        final Bitmap result=image;main.post(()->{if(!destroyed&&id.equals(videoId)&&result!=null){artwork=result;publishMetadata();}});
    });}
    private void exitFullScreen(){if(fullScreenVideo==null)return;container.removeView(fullScreenVideo);fullScreenVideo=null;browser.setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(previousSystemUi);if(fullScreenCallback!=null)fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}
    @Override public void onBackPressed(){if(fullScreenVideo!=null)exitFullScreen();else if(browser.canGoBack())browser.goBack();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){browser.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacks(poll);artworkWorker.shutdownNow();session.setActive(false);session.release();exitFullScreen();container.removeView(browser);browser.destroy();super.onDestroy();}
}
