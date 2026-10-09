package dev.tossfront.record;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;

public final class RecordOverlay extends Service implements RecordPanel.Host {
    private android.widget.FrameLayout sceneLayer;private WindowManager windows;private RecordPanel panel;private boolean full,autoSkip;private int background;
    @Override public void onCreate(){super.onCreate();NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("record","레코드 화면",NotificationManager.IMPORTANCE_LOW));PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecordOverlay.class).setAction("close"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Notification notification=new Notification.Builder(this,"record").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("재생 화면 표시 중").setContentText("알림을 눌러 재생 화면을 닫을 수 있습니다.").setContentIntent(stop).setOngoing(true).addAction(new Notification.Action.Builder(null,"닫기",stop).build()).build();startForeground(1,notification);}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null&&"close".equals(intent.getAction())){close();return START_NOT_STICKY;}if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}if(panel==null){windows=(WindowManager)getSystemService(WINDOW_SERVICE);sceneLayer=new android.widget.FrameLayout(this);sceneLayer.setBackgroundColor(new Appearance(this).bg());windows.addView(sceneLayer,window(true));panel=new RecordPanel(this,this,true,sceneLayer);WindowManager.LayoutParams p=window(false);windows.addView(panel,p);Appearance a=new Appearance(this);full=a.immersive;background=a.bg();autoSkip=a.autoSkip;SessionRepository.get(this).openWeb(this,true);}return START_NOT_STICKY;}
    private WindowManager.LayoutParams window(boolean scene){WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,scene?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP;if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;}
    public void appearanceChanged(){Appearance a=new Appearance(this);if(full!=a.immersive||background!=a.bg()||autoSkip!=a.autoSkip){full=a.immersive;background=a.bg();autoSkip=a.autoSkip;SessionRepository.get(this).openWeb(this,true);}}
    public void openSource(){panel.dismiss(()->{SessionRepository.get(this).openWeb(this,false);stopSelf();});}
    public void openAudio(){panel.dismiss(()->{SessionRepository.get(this).openWeb(this,false);Intent intent=getPackageManager().getLaunchIntentForPackage("dev.tossfront.audio");if(intent!=null)startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));stopSelf();});}
    public void audioPermission(){if(panel!=null)panel.dismiss(()->{startActivity(new Intent(this,MainActivity.class).putExtra("ask_audio",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));stopSelf();});}
    public void showRecord(){}
    public void connectionSettings(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public void close(){if(panel!=null)panel.dismiss(()->{SessionRepository.get(this).openWeb(this,false);stopSelf();});else stopSelf();}
    @Override public void onDestroy(){if(panel!=null){windows.removeView(panel);panel=null;}if(sceneLayer!=null){windows.removeView(sceneLayer);sceneLayer=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
