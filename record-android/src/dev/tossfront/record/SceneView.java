package dev.tossfront.record;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.webkit.*;
import org.json.*;
import java.io.ByteArrayInputStream;
import java.util.*;

/** Bundled Three.js scenes. No network, JS bridge, page-provided URLs or media metadata. */
final class SceneView extends WebView {
    private final AudioSpectrum audio;private Appearance appearance;
    private boolean playing,attached,destroyed,scheduled,ready,ownsAudio,reported,lowReported,highReported,cameraReported;private long began;
    private String status="3D 장면을 준비하는 중";private int areaTop,areaHeight=400;
    private final Runnable frame=new Runnable(){public void run(){scheduled=false;if(!active())return;if(playing){ownsAudio=true;audio.start();}else releaseAudio();send();if(playing||!ready&&SystemClock.uptimeMillis()-began<12000)schedule();}};
    SceneView(Context c,AudioSpectrum audio){super(c);this.audio=audio;setBackgroundColor(Color.TRANSPARENT);setLayerType(View.LAYER_TYPE_HARDWARE,null);setFocusable(false);setFocusableInTouchMode(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);WebSettings s=getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);
        setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){Uri u=request.getUrl();String file=u.getPath();try{if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&file!=null&&file.matches("/visual/[A-Za-z0-9_.-]+")){String name=file.substring(8);String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":"text/plain";Map<String,String> headers=new HashMap<>();headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'none'; media-src 'none'; object-src 'none'");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getContext().getAssets().open("visual/"+name));}}catch(java.io.IOException ignored){}return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}@Override public void onPageFinished(WebView view,String url){send();schedule();}});
        setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage message){return true;}});began=SystemClock.uptimeMillis();loadUrl("https://appassets.androidplatform.net/visual/index.html");
    }
    void applyAppearance(Appearance a){appearance=a;setBackgroundColor(a.bg());if(getParent() instanceof android.view.View)((android.view.View)getParent()).setBackgroundColor(a.bg());send();if(active())schedule();}
    void setOpenArea(int top,int height){areaTop=top;areaHeight=height;send();}
    void gestureChanged(){if(!playing)send();}
    private void releaseAudio(){if(ownsAudio){audio.stop();ownsAudio=false;}}
    void update(boolean value){playing=value;if(active()){onResume();send();schedule();}else{releaseAudio();send();removeCallbacks(frame);scheduled=false;onPause();}}
    private boolean active(){return !destroyed&&attached&&isShown()&&getWindowVisibility()==VISIBLE&&appearance!=null&&appearance.mode>=7;}
    private void schedule(){if(!scheduled&&!destroyed&&active()){scheduled=true;postDelayed(frame,50);}}
    private void send(){if(destroyed||appearance==null)return;try{JSONObject state=new JSONObject();state.put("mode",appearance.mode-7);state.put("filter",appearance.visualFilter);state.put("cameraMotion",appearance.cameraMotion);state.put("retro",appearance.theme==4);GestureInfluence hand=audio.gesture;JSONObject gesture=new JSONObject();gesture.put("active",hand.active);gesture.put("x",hand.x);gesture.put("y",hand.y);gesture.put("strength",hand.strength);gesture.put("spread",hand.spread);state.put("gesture",gesture);state.put("playing",playing&&active());state.put("signal",audio.data.peak>.01f&&audio.connected());state.put("reduced",!appearance.motion());state.put("gain",appearance.gain());state.put("background",String.format(Locale.ROOT,"#%06X",appearance.bg()&0xffffff));JSONArray colors=new JSONArray();for(int color:appearance.visualColors())colors.put(String.format(Locale.ROOT,"#%06X",color&0xffffff));state.put("colors",colors);JSONArray bands=new JSONArray();for(int i=0;i<16;i++){float sum=0;for(int j=0;j<4;j++)sum+=audio.data.bands[i*4+j];bands.put(sum/4);}state.put("bands",bands);state.put("rms",ResponseMath.rms(audio.data.waveform));state.put("peak",audio.data.peak);state.put("lowVocal",audio.data.lowVocal);state.put("highVocal",audio.data.highVocal);JSONArray wave=new JSONArray();for(int i=0;i<32;i++)wave.put(audio.data.waveform[i*4]);state.put("wave",wave);state.put("focus",(areaTop+areaHeight*.5f)/Math.max(1,getHeight()));state.put("span",areaHeight/(float)Math.max(1,getHeight()));evaluateJavascript("window.FrontScene?window.FrontScene.update("+state.toString()+"):null",raw->{try{if(raw!=null&&!"null".equals(raw)){JSONObject result=new JSONObject(raw);ready=true;if(!lowReported&&result.optInt("lowOnsets")>0){lowReported=true;android.util.Log.i("FrontRecordScene","audio_low_side_motion_detected");}if(!highReported&&result.optInt("highOnsets")>0){highReported=true;android.util.Log.i("FrontRecordScene","audio_high_side_motion_detected");}if(!cameraReported&&result.optInt("cameraMoves")>0){cameraReported=true;android.util.Log.i("FrontRecordScene","audio_camera_motion_detected");}if(!reported){reported=true;android.util.Log.i("FrontRecordScene",result.optBoolean("webgl")?"bundled_webgl_ready":"bundled_webgl_unavailable");}status=result.optBoolean("webgl")?"":"3D 그래픽을 사용할 수 없어요 · 다른 시각화를 선택해 주세요";}}catch(JSONException ignored){}});}catch(JSONException ignored){}}
    String status(){return status;}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){return false;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;update(playing);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(audio!=null)update(playing);}
    @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(audio!=null)update(playing);}
    @Override protected void onDetachedFromWindow(){attached=false;releaseAudio();send();removeCallbacks(frame);scheduled=false;onPause();destroyed=true;stopLoading();destroy();super.onDetachedFromWindow();}
}
