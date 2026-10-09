package dev.tossfront.record;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public final class MainActivity extends Activity implements RecordPanel.Host {
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(RecordPanel.PAPER);getWindow().setNavigationBarColor(RecordPanel.PAPER);getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);setContentView(new RecordPanel(this,this,false));}
    @Override protected void onResume(){super.onResume();SessionRepository.get(this).connect();}
    public void openSource(String source){SessionRepository.get(this).openSource(this,source);}
    public void connectionSettings(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
    public void showRecord(){if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}startForegroundService(new Intent(this,RecordOverlay.class));SessionRepository repository=SessionRepository.get(this);repository.openSource(this,repository.packageName());}
    public void close(){startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));}
}
