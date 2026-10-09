package dev.tossfront.record;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;

public final class RecordOverlay extends Service implements RecordPanel.Host {
    private WindowManager windows;private RecordPanel panel;
    @Override public void onCreate(){super.onCreate();NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("record", "레코드 화면", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecordOverlay.class).setAction("close"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"record").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("레코드 화면 표시 중").setContentText("YouTube 위의 레코드 화면을 닫을 수 있습니다.").setOngoing(true).addAction(new Notification.Action.Builder(null,"닫기",stop).build()).build();startForeground(1,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null&&"close".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}if(panel==null){windows=(WindowManager)getSystemService(WINDOW_SERVICE);panel=new RecordPanel(this,this,true);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP;windows.addView(panel,params);}return START_NOT_STICKY;}
    public void openSource(String source){stopSelf();SessionRepository.get(this).openSource(this,source);}
    public void showRecord(){}
    public void connectionSettings(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public void close(){stopSelf();}
    @Override public void onDestroy(){if(panel!=null){windows.removeView(panel);panel=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
