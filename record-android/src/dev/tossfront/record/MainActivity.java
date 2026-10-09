package dev.tossfront.record;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public final class MainActivity extends Activity implements RecordPanel.Host {
    private RecordPanel panel;
    @Override public void onCreate(Bundle state){super.onCreate(state);panel=new RecordPanel(this,this,false);setContentView(panel);SystemBars.apply(getWindow(),new Appearance(this));if(getIntent().getBooleanExtra("ask_audio",false))audioPermission();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("ask_audio",false))audioPermission();}
    @Override protected void onResume(){super.onResume();if(panel!=null)panel.preferencesChanged();SessionRepository.get(this).connect();SystemBars.apply(getWindow(),new Appearance(this));}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)SystemBars.apply(getWindow(),new Appearance(this));}
    public void appearanceChanged(){SystemBars.apply(getWindow(),new Appearance(this));}
    public void openSource(){SessionRepository.get(this).openWeb(this,false);}
    public void openAudio(){Intent intent=getPackageManager().getLaunchIntentForPackage("dev.tossfront.audio");if(intent!=null)startActivity(intent);}
    public void audioPermission(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);}
    public void connectionSettings(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    public void showRecord(){if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}startForegroundService(new Intent(this,RecordOverlay.class));SessionRepository.get(this).openWeb(this,true);}
    public void close(){panel.dismiss(()->startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));}
}
