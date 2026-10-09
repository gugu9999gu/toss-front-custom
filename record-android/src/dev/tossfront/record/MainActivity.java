package dev.tossfront.record;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public final class MainActivity extends Activity implements RecordPanel.Host {
    private RecordPanel panel;private boolean openingOverlay;
    @Override public void onCreate(Bundle state){super.onCreate(state);panel=new RecordPanel(this,this,false);setContentView(panel);SystemBars.apply(getWindow(),new Appearance(this));if(getIntent().getBooleanExtra("ask_audio",false))audioPermission();if(getIntent().getBooleanExtra("ask_camera",false))cameraPermission();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("ask_audio",false))audioPermission();if(intent.getBooleanExtra("ask_camera",false))cameraPermission();}
    @Override protected void onResume(){super.onResume();if(panel!=null){panel.cameraVisible(!openingOverlay&&!RecordOverlay.showing());panel.preferencesChanged();}SessionRepository.get(this).connect();SystemBars.apply(getWindow(),new Appearance(this));}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)SystemBars.apply(getWindow(),new Appearance(this));}
    public void appearanceChanged(){SystemBars.apply(getWindow(),new Appearance(this));}
    public void openSource(){SessionRepository.get(this).openWeb(this,false);}
    public void openAudio(){Intent intent=getPackageManager().getLaunchIntentForPackage("dev.tossfront.audio");if(intent!=null)startActivity(intent);}
    public void audioPermission(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);}
    public void cameraPermission(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},11);else cameraReady();}
    private void cameraReady(){panel.preferencesChanged();if(getIntent().getBooleanExtra("ask_camera",false)){getIntent().removeExtra("ask_camera");showRecord();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==11){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)cameraReady();else{Appearance a=new Appearance(this);a.handCamera=false;a.save();panel.preferencesChanged();}}}
    @Override protected void onPause(){if(panel!=null)panel.cameraVisible(false);openingOverlay=false;super.onPause();}
    public void connectionSettings(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    public void showRecord(){if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}openingOverlay=true;panel.cameraVisible(false);startForegroundService(new Intent(this,RecordOverlay.class));SessionRepository.get(this).openWeb(this,true);}
    public void close(){panel.dismiss(()->startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));}
}
